package fr.inria.cedar.quotientSummary;

import fr.inria.cedar.quotientSummary.datastructures.EdgesWithProvenanceCounts;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.traversers.DataFirstTraverser;
import fr.inria.cedar.quotientSummary.traversers.DataFirstTwoPassTraverser;
import fr.inria.cedar.quotientSummary.traversers.Traverser;
import fr.inria.cedar.quotientSummary.traversers.TypeFirstTraverser;
import fr.inria.cedar.quotientSummary.traversers.TypeFirstTwoPassTraverser;
import fr.inria.cedar.quotientSummary.util.DOTAuxiliary;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Summary {
	private static final Logger LOGGER = Logger.getLogger(Summary.class.getName());
	protected static final SimpleDateFormat SD_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");

	protected Long2Long rep; // representation function
	protected HashSet<Long> sn; // schema nodes
	// data, schema and type triples:
	//   for each subject
	//     for each property
	//       the set of objects such that (subject, property, object) is in the summary
	protected EdgesWithProvenanceCounts edgesWithProv;

	protected Traverser traverser;

	// these serve to represent the nodes that may have types but no data property
	protected long typeOnlyNodeID;
	protected boolean typeOnlyNodeAlreadySeen;

	protected Triple lastReadTriple;
	protected long maxSummaryNode;
	protected Properties properties;
	protected static String SUMMARY_CONFIG_FILE = "conf/summarization.properties";

	// repTablePrefix must be instantiated with a specific string for each summary type, so that each summary is saved as separated Postgres tables
	protected String summaryTablePrefix;
	protected boolean isTypeFirst = false;
	protected boolean isTwoPass = false;
	protected static String ROOT_SUMMARY_PREFIX = "";
	protected static String WEAK_SUMMARY_PREFIX = "w_";
	protected static String STRONG_SUMMARY_PREFIX = "s_";
	protected static String TYPED_WEAK_SUMMARY_PREFIX = "tw_";
	protected static String TYPED_STRONG_SUMMARY_PREFIX = "ts_";
	protected static String TWO_PASS_WEAK_SUMMARY_PREFIX = "2pw_";
	protected static String TWO_PASS_WEAK_SUMMARY_WITH_UNION_FIND_PREFIX = "2pwuf_";
	protected static String TWO_PASS_STRONG_SUMMARY_PREFIX = "2ps_";
	protected static String TWO_PASS_TYPED_WEAK_SUMMARY_PREFIX = "2ptw_";
	protected static String TWO_PASS_TYPED_STRONG_SUMMARY_PREFIX = "2pts_";
	protected static String ONEFB_SUMMARY_PREFIX = "1fb_"; 

	protected String triplesFileName = "";
	protected String triplesTableName = "";
	protected String encodedTriplesTableName = "";
	protected String dictionaryTableName = "";
	protected String repTableName = "";
	protected String edgeTableName = "";
	protected boolean checkConsistency = false;

	protected long triplesSummarizedSoFar = 0;
	protected long typeTriplesSummarizedSoFar = 0;
	protected long dataTriplesSummarizedSoFar = 0;

	protected DOTAuxiliary dax;

	// statistics
	protected long summaryEdgesSavingTime;
	protected long representationFunctionSavingTime;
	protected long classSetCreationTime;
	protected long typeTriplesSummarizationTime;
	protected long dataTriplesSummarizationTime;
	protected long allTriplesSummarizationTime;
	// TODO possibly rewrite code gathering these
	// for each summary node, the number of graph nodes it represents
	protected HashMap<Long, Long> summaryNodeStatistics;
	// for each summary edge, the number of graph edge it represents
	protected HashMap<Triple, Long> summaryEdgeStatistics;

	public Summary() {
		LOGGER.setLevel(Level.INFO);
		summaryTablePrefix = ROOT_SUMMARY_PREFIX;
		typeOnlyNodeAlreadySeen = false;
		sn = new HashSet<>();
		rep = new Long2Long();
		edgesWithProv = new EdgesWithProvenanceCounts();

		//summaryNodeStatistics = new HashMap<>();
		//summaryEdgeStatistics = new HashMap<>();
		properties = new Properties();
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
			checkConsistency = properties.getProperty("consistencyChecks").toLowerCase().equals("true");
		} catch (IOException e) {
			throw new IllegalStateException("Unable to initialize summary properties");
		}
		dax = new DOTAuxiliary();
	}

	public Summary(Connection conn) throws SQLException {
		this.rep = new Long2Long();
		this.edgesWithProv = new EdgesWithProvenanceCounts();
		try {
			conn.setAutoCommit(false);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
		LOGGER.info("Trying to read summary from Postgres");
		Statement stmt = conn.createStatement();
		try{
			ResultSet rs = stmt.executeQuery("select name from saved_summary_table_names where role='dictionary';");
			if (rs.next()){
				this.dictionaryTableName = rs.getString(1);
			}
			else{
				throw new IllegalStateException("Could not learn the name of the dictionary table"); 
			}
			rs = stmt.executeQuery("select name from saved_summary_table_names where role='representation';");
			if (rs.next()){
				this.repTableName = rs.getString(1);
			}
			else{
				throw new IllegalStateException("Could not learn the name of the representation table"); 
			}
			rs = stmt.executeQuery("select name from saved_summary_table_names where role='edges';");
			if (rs.next()){
				this.edgeTableName = rs.getString(1);
			}
			else{
				throw new IllegalStateException("Could not learn the name of the edge table"); 
			}
			rs = stmt.executeQuery("select name from saved_summary_table_names where role='encoded_triples';");
			if (rs.next()){
				this.encodedTriplesTableName = rs.getString(1);
			}
			else{
				throw new IllegalStateException("Could not learn the name of the encoded triples table"); 
			}
			rs.close();
		}
		catch(SQLException e){
			stmt.close();
			conn.close();
			throw new IllegalStateException("Could not read summary from Postgres " + e.toString()); 
		}
		RDF2SQLEncoding.setUp(conn, this.dictionaryTableName);
		//LOGGER.debug("Set up special URIs from dictionary");
		String getSummaryTriples = getSummaryTriplesSQLQuery();
		try (
				Statement getTriples = conn.createStatement();
				ResultSet rs = getTriples.executeQuery(getSummaryTriples);
		) {
			while (rs.next()) {
				Long s = rs.getLong(1);
				Long p = rs.getLong(2);
				Long o = rs.getLong(3);
				edgesWithProv.addTriple(s, p, o);
			}
		}
		LOGGER.info("Summary read from Postgres");
	}

	// we need to be sure that integers which we invent to represent nodes
	// will not collide with the codes already given to classes and properties
	// (which, in this implementation, for simplicity, are preserved).
	protected void avoidCollisionsWhenAssigningSummaryNodes(Connection conn) {
		long maxClassOrPropertyCode = 0;
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();

		if (typeConstantCode != -1){
			maxClassOrPropertyCode = this.maxO(conn, typeConstantCode); 
		}
		long subClassCode = RDF2SQLEncoding.getSubClassCode();
		if (subClassCode != -1){
			maxClassOrPropertyCode = Math.max(maxClassOrPropertyCode, this.maxSPO(conn, subClassCode));
		}
		long domainCode = RDF2SQLEncoding.getDomainCode();
		if (domainCode != -1){
			maxClassOrPropertyCode = Math.max(maxClassOrPropertyCode, this.maxSPO(conn, domainCode));
		}
		long rangeCode = RDF2SQLEncoding.getRangeCode();
		if (rangeCode != -1){
			maxClassOrPropertyCode = Math.max(maxClassOrPropertyCode, this.maxSPO(conn, domainCode));
		}
		this.jumpSummaryNodeCount(maxClassOrPropertyCode + 1);
	}

	protected long maxSPO(Connection conn, long property){
		long maxS = maxS(conn, property);
		long maxP = maxO(conn, property);
		long maxO = maxO(conn, property);
		return Math.max(maxS, Math.max(maxP, maxO));
	}

	protected long maxS(Connection conn, long property){
		String jumpRepString = ("select max(s) from " + encodedTriplesTableName + " t1 where p = " + property);
		try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
			while (rs.next()) {
				return rs.getLong(1); 
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to determine the highest subject code" + e.toString());
		}
		return -1; 
	}

	protected long maxP(Connection conn, long property){
		String jumpRepString = ("select max(p) from " + encodedTriplesTableName + " t1 where p = " + property);
		try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
			while (rs.next()) {
				return rs.getLong(1); 
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to determine the highest property code " + e.toString());
		}
		return -1; 
	}

	protected long maxO(Connection conn, long property){
		String jumpRepString = ("select max(o) from " + encodedTriplesTableName + " t1 where p = " + property);
		try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
			while (rs.next()) {
				return rs.getLong(1); 
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to determine the highest object code " + e.toString());
		}
		return -1; 
	}

	protected void collectSchemaNodes(Connection conn) {
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		long subClassCode = RDF2SQLEncoding.getSubClassCode();
		long subPropertyCode = RDF2SQLEncoding.getSubPropertyCode();
		long domainCode = RDF2SQLEncoding.getDomainCode();
		long rangeCode = RDF2SQLEncoding.getRangeCode();

		String getTriplesString = "select distinct s from " + encodedTriplesTableName
			+ " where p = " + subClassCode
			+ " or p = " + subPropertyCode + ";";
		try {
			try (Statement getTriples = conn.createStatement()) {
				getTriples.setFetchSize(10000);
				try (ResultSet rs = getTriples.executeQuery(getTriplesString)) {
					while (rs.next()) {
						long s = rs.getInt(1);
						sn.add(s);
						rep.put(s, s);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while collecting schema nodes " + e.toString());
		}

		getTriplesString = "select distinct o from " + encodedTriplesTableName
			+ " where p = " + subClassCode
			+ " or p = " + subPropertyCode
			+ " or p = " + domainCode
			+ " or p = " + rangeCode + ";";
		try {
			try (Statement getTriples = conn.createStatement()) {
				getTriples.setFetchSize(10000);
				try (ResultSet rs = getTriples.executeQuery(getTriplesString)) {
					while (rs.next()) {
						long o = rs.getInt(1);
						sn.add(o);
						rep.put(o, o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while collecting schema nodes " + e.toString());
		}
	}

	protected String showRep() {
		StringBuilder sb = new StringBuilder();
		for (Long node : this.rep.getKeys()) {
			//sb.append(node).append("=>").append(rep.get(node)).append(" ");
			sb.append(node).append(" (").append(RDF2SQLEncoding.dictionaryDecode(node)).append(") => ").append(rep.get(node)).append("\n");
		}
		return sb.toString();
	}

	protected Long getNextSummaryNode() {
		Long node = this.maxSummaryNode;
		this.maxSummaryNode++;
		return node;
	}

	/**
	 * This method is needed in order to avoid collisions between IDs assigned
	 * for class sets, and IDs assigned based on property cliques.
	 *
	 * @param n
	 */
	protected void jumpSummaryNodeCount(long n) {
		this.maxSummaryNode += n;
	}

	protected void gatherStatistics() {
		gatherNodeStatistics();
		gatherEdgeStatistics();
	}

	/**
	 * May 24, 2018: these should be taken directly from rep
	 */
	protected void gatherNodeStatistics() {
		// TODO
	}

	/**
	 * May 24, 2018: these statistics should be picked directly from the edgesWithCounter.
	 */
	protected void gatherEdgeStatistics() {
		//TODO
	}

	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("Not implemented at this level");
	}

	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("Not implemented at this level");
	}

	public void summarizeFromPostgres(Connection conn) {
		if (isTypeFirst) {
			if (isTwoPass) {
				traverser = new DataFirstTwoPassTraverser(conn);
			}
			else {
				traverser = new DataFirstTraverser(conn);
			}
		}
		else {
			if (isTwoPass) {
				traverser = new TypeFirstTwoPassTraverser(conn);
			}
			else {
				traverser = new TypeFirstTraverser(conn);
			}
		}
		traverser.traverseAllTriples();
	}

	/**
	 * Saves a summary as two Postgres tables: one is rep (the representation
	 * function) the other one is the set of summary edges, encoded as integers.
	 * The property and class URIs here are encoded exactly like the input. The
	 * subject and objects in the summary edges are just "new integer codes".
	 *
	 * @param conn
	 * @param partialResult
	 * @param summarizationInput
	 */
	public void saveSummaryInPostgres(Connection conn, Boolean partialResult, String summarizationInput) {
		try {
			conn.setAutoCommit(false);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
		String newTableName = encodedTriplesTableName;
		String timestamp = SD_FORMAT.format(new Timestamp(System.currentTimeMillis()));
		if (partialResult)
			newTableName = newTableName + "_sum";
		else
			newTableName = "sav_" + timestamp + "_" + newTableName + "_" + summarizationInput + "_" + getSummaryURIPrefix();
		String newSummaryTableNameRep = newTableName + "_rep";
		String newSummaryTableNameEdges = newTableName + "_edges";

		LOGGER.info("Saving " + this.getClass().getName() + " in Postgres in tables " + newSummaryTableNameRep + " and " + newSummaryTableNameEdges);

		Statement stmt;
		try {
			stmt = conn.createStatement();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not create the statement: " + e.toString());
		}

		if (!partialResult) {
			// save representation function
			try {
				long start = System.currentTimeMillis();
				// create the table (it may have existed)
				if (!existsTable(conn, newSummaryTableNameRep)) {
					stmt.execute("create table " + newSummaryTableNameRep + "(graphNode int not null, summaryNode int not null);");
					//LOGGER.debug("Table " + newSummaryTableNameRep + " created");
				}
				else {
					//LOGGER.debug("Did not create " + newSummaryTableNameRep + " table as it was already there");
				}
				// empty it (even if the creation failed, e.g. because the table was already there)
				stmt.executeUpdate("delete from " + newSummaryTableNameRep + ";");

				// now insert all the rep entries:
				String insertIntoRep = "insert into " + newSummaryTableNameRep + " values(?, ?);";
				try (PreparedStatement insertInRep = conn.prepareStatement(insertIntoRep)) {
					Set<Long> origNodes = rep.getKeys();
					for (Long origNode : origNodes) {
						Long sumNode = rep.get(origNode);
						insertInRep.setLong(1, origNode);
						insertInRep.setLong(2, sumNode);
						insertInRep.executeUpdate();
					}
				}
				// if (!hasIndex(conn, "encoded_rep"))
				//	stmt.executeUpdate("create index indRepS on encoded_rep(graphNode);");
				// This gives some erros in the JDBC driver, perhaps it is not implemented properly.
				conn.commit();
				representationFunctionSavingTime = System.currentTimeMillis() - start;
				LOGGER.info("Representation function saved in " + representationFunctionSavingTime + " ms");
			}
			catch (SQLException e) {
				throw new IllegalStateException("Could not insert summary triples in " + newSummaryTableNameRep + ": " + e.toString());
			}
		}

		// save summary
		try {
			long start = System.currentTimeMillis();
			// create the table (it may have existed)
			if (!existsTable(conn, newSummaryTableNameEdges)) {
				stmt.execute("create table " + newSummaryTableNameEdges + "(s int not null, p int not null, o int not null);");
				//LOGGER.debug("Table " + newSummaryTableNameEdges + " created");
			}
			else {
				//LOGGER.debug("Did not create " + newSummaryTableNameEdges + " table as it was already there");
			}
			// empty it (even if the creation failed, e.g. because the table was already there)
			stmt.executeUpdate("delete from " + newSummaryTableNameEdges + ";");
			conn.commit();

			// now insert all the summary edges:
			String insertIntoSummary = "insert into " + newSummaryTableNameEdges + " values(?, ?, ?);";
			try (PreparedStatement insertInSummary = conn.prepareStatement(insertIntoSummary)) {
				ArrayList<Triple> edges = edgesWithProv.getSummaryEdges();
				for (Triple t : edges) {
					//LOGGER.debug("Saving in Postgres edge: " + t.toString());
					insertInSummary.setLong(1, t.s);
					insertInSummary.setLong(2, t.p);
					insertInSummary.setLong(3, t.o);
					insertInSummary.executeUpdate();
				}
				// if (!hasIndex(conn, "encoded_summary"))
				//	stmt.executeUpdate("create index indSummaryS on encoded_summary(s);");
				conn.commit();
				summaryEdgesSavingTime = System.currentTimeMillis() - start;
				LOGGER.info("Saved " + edges.size() + " summary edges in " + summaryEdgesSavingTime + " ms");
				LOGGER.info("Summary saved in Postgres");
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not insert summary triples in " + newSummaryTableNameEdges + ": " + e.toString());
		}

		// saving the table names in Postgres: 
		try {
			stmt.executeUpdate("create table if not exists saved_summary_table_names(role varchar, name varchar);");
			stmt.executeUpdate("insert into saved_summary_table_names values ('edges', '" + newSummaryTableNameEdges + "');" );
			stmt.executeUpdate("insert into saved_summary_table_names values ('representation', '" + newSummaryTableNameRep + "');");
			stmt.executeUpdate("insert into saved_summary_table_names values ('encoded_triples', '" + encodedTriplesTableName + "');");
			conn.commit();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not create table_names: " + e.toString());
		}
	}

	static protected boolean existsTable(Connection conn, String tableName) {
		try {
			ResultSet res = conn.getMetaData().getTables(null, null, tableName, new String[] { "TABLE" });
			return res.next();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not find out if table " + tableName + " exists: " + e.toString());
		}
	}

	static protected boolean hasIndex(Connection conn, String tableName) {
		try {
			DatabaseMetaData meta = conn.getMetaData();
			ResultSet res = meta.getIndexInfo(null, null, tableName, true, true);
			return res.next();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not find out if an index exists on " + tableName + ": " + e.toString());
		}
	}

	public void writeDecodedSummaryToNTFile(Connection conn) {
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);

		String summaryNTFileName = getNTSummaryFileName();
		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");

		LOGGER.info("Decoding summary and writing it in .nt format to " + summaryNTFileName);

		ArrayList<Triple> summEdges = edgesWithProv.getSummaryEdges();
		try {
			// write summary triples:
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryNTFileName)))) {
				// write summary triples:
				for (Triple t : summEdges) {
					//LOGGER.debug("Summary triple: " + t.toString() );
					String subject, property, object;
					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						subject = getSummaryNodeURI(URIprefix, t.s);
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						object = getSummaryNodeURI(URIprefix, t.o);
					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						subject = RDF2SQLEncoding.dictionaryDecode(t.s);
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						object = RDF2SQLEncoding.dictionaryDecode(t.o);
					} else { // type
						subject = getSummaryNodeURI(URIprefix, t.s);
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						object = RDF2SQLEncoding.dictionaryDecode(t.o);
					}
					//LOGGER.debug(subject + " " + property + " " + object);
					bw.write(subject + " " + property + " " + object + " .\n");
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not save the decoded summary in .nt file: " + e.toString());
		}
		LOGGER.info("Summary decoded and saved in .nt format");
	}

	public String getNTSummaryFileName() {
		int lastDotPosition = Math.max(0, triplesFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, triplesFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + triplesFileName);
		return triplesFileName.substring(0, lastDotPosition) + "_" + getSummaryURIPrefix() + ".nt";
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres. It prints the
	 * summary to the standard output and also saves it in a separate .nt file
	 *
	 * @param conn
	 * @param summarizationTechnique
	 */
	public void writeDecodedSummaryToNTFile(Connection conn, String summarizationTechnique) {
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);

		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");

		String summaryNTFileName = getNTSummaryFileName(summarizationTechnique);

		LOGGER.info("Decoding summary and writing it in .nt format to " + summaryNTFileName);

		boolean gatherStatistics = properties.getProperty("gatherStatistics").toLowerCase().equals("true");
		if (gatherStatistics)
			this.gatherStatistics();
		ArrayList<Triple> summEdges = edgesWithProv.getSummaryEdges();
		try {
			// write summary triples:
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryNTFileName)))) {
				// write summary triples:
				for (Triple t : summEdges) {
					//LOGGER.debug("Summary triple: " + t.toString() );
					String subject, property, object;
					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						subject = getSummaryNodeURI(URIprefix, t.s);
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						object = getSummaryNodeURI(URIprefix, t.o);
					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						subject = RDF2SQLEncoding.dictionaryDecode(t.s);
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						object = RDF2SQLEncoding.dictionaryDecode(t.o);
					} else { // type
						subject = getSummaryNodeURI(URIprefix, t.s);
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						object = RDF2SQLEncoding.dictionaryDecode(t.o);
					}
					//LOGGER.debug(subject + " " + property + " " + object);
					bw.write(subject + " " + property + " " + object + " .\n");
				}
				if (gatherStatistics) {
					// write node cardinality statistics:
					for (Long node : this.summaryNodeStatistics.keySet()) {
						Long numberOfRepresentedGraphNodes = this.summaryNodeStatistics.get(node);
						String subject = getSummaryNodeURI(URIprefix, node);
						String property = properties.getProperty("summaryNodeSupportURI");
						String object = ("\"" + numberOfRepresentedGraphNodes + "\"");
						//LOGGER.debug(subject + " " + property + " " + object);
						bw.write(subject + " <" + property + "> " + object + " .\n");
					}
					// write edge cardinality statistics:
					int reifiedEdgeNumber = 0;
					for (Triple ts : this.summaryEdgeStatistics.keySet()) {
						Long numberOfRepresentedEdges = this.summaryEdgeStatistics.get(ts);
						String reifEdgeURI = getSummaryNodeURI(properties.getProperty("reifiedSummaryEdgeURIPrefix"),
							reifiedEdgeNumber);
						bw.write(reifEdgeURI + " <" + properties.getProperty("reifiedEdgeHasSubject") + "> "
							+ getSummaryNodeURI(URIprefix, ts.s) + " .\n");
						bw.write(reifEdgeURI + " <" + properties.getProperty("reifiedEdgeHasProperty") + "> <"
							+ RDF2SQLEncoding.dictionaryDecode(ts.p) + "> .\n");
						bw.write(reifEdgeURI + " <" + properties.getProperty("reifiedEdgeHasObject") + "> "
							+ getSummaryNodeURI(URIprefix, ts.o) + " .\n");
						bw.write(reifEdgeURI + " <" + properties.getProperty("summaryEdgeSupportURI") + "> \""
							+ numberOfRepresentedEdges + "\" .\n");
						reifiedEdgeNumber++;
					}
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not save the decoded summary in .nt file: " + e.toString());
		}
		LOGGER.info("Summary decoded and saved in .nt format");
	}

	protected String getNTSummaryFileName(String summarizationTechnique) {
		int lastDotPosition = Math.max(0, triplesFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, triplesFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + triplesFileName);
		return triplesFileName.substring(0, lastDotPosition) + "_" + summaryTablePrefix + summarizationTechnique + ".nt";
	}

	/**
	 * This is the inventor of summary node URIs.
	 *
	 * @param uriPrefix
	 * @param n
	 *
	 * @return
	 */
	protected String getSummaryNodeURI(String uriPrefix, long n) {
		return ("<" + uriPrefix + this.getSummaryURIPrefix() + n + ">");
	}

	public void drawSummaryAndGraph(Connection conn, String suffix) {
		String summaryDotFileName = getDotFileName(suffix);
		writeSummaryToDotFile(conn, summaryDotFileName);
		String graphDotFileName = getRDFDotFileName(suffix);
		writeRDFGraphToDotFile(conn, graphDotFileName);
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres
	 *
	 * @param conn
	 * @param dotFileName
	 */
	protected void writeSummaryToDotFile(Connection conn, String dotFileName) {
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		dax.resetColors();
		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");
		//LOGGER.debug("writeSummaryToDotFile:");

		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)))) {
				bw.write("digraph g{\n");

				ArrayList<Triple> summEdges = edgesWithProv.getSummaryEdges();
				for (Triple t : summEdges) {
					String subject, property, object, subjectInDot, propertyInDot, objectInDot;
					// in all cases, edge labels are preserved:
					property = RDF2SQLEncoding.dictionaryDecode(t.p);
					propertyInDot = getVeryShortForDot(property.replaceAll("\"", ""));

					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						subject = this.getSummaryNodeURI(URIprefix, t.s);
						subjectInDot = getVeryShortForDot(subject).replaceAll("\"", "");
						if (dax.unknownSummaryNode(t.s)){
							writeNodeToDot(bw, t.s, subjectInDot); 
						}
						object = this.getSummaryNodeURI(URIprefix, t.o);
						objectInDot = getVeryShortForDot(object).replaceAll("\"", "");
						if (dax.unknownSummaryNode(t.o)){
							writeNodeToDot(bw, t.o, objectInDot); 
						}
					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						subject = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.s));
						subjectInDot = subject.replaceAll("\"", "");
						if (t.p == RDF2SQLEncoding.getSubClassCode()){
							propertyInDot = "subClass";
						}
						if (t.p == RDF2SQLEncoding.getSubPropertyCode()){
							propertyInDot = "subProperty";
						}
						if (t.p == RDF2SQLEncoding.getDomainCode()){
							propertyInDot = "domain";
						}
						if (t.p == RDF2SQLEncoding.getRangeCode()){
							propertyInDot = "range";
						}
						object = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.o));
						objectInDot = object.replaceAll("\"", "");
						bw.write("\"" + subjectInDot + "\" [fontcolor=white, style = filled, color=black];\n");
						bw.write("\"" + objectInDot + "\" [fontcolor=white, style = filled, color=black];\n");
					} else { // type
						subject = this.getSummaryNodeURI(URIprefix, t.s);
						subjectInDot = getVeryShortForDot(subject).replaceAll("\"", "");
						object = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.o));
						objectInDot = object.replaceAll("\"", "");
						propertyInDot = "rdf:type";
						if (dax.unknownSummaryNode(t.s)){
							this.writeNodeToDot(bw, t.s, subjectInDot);
						}
						bw.write("\"" + objectInDot + "\" [fontcolor=white, style = filled, color=black];\n");
					}
					// write the triple in all cases:
					bw.write("\"" + subjectInDot + "\"" + " -> \"" + objectInDot + "\" [label=\"" + propertyInDot + "\"];\n");
				}
				bw.write("}\n");
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
		LOGGER.info("Summary written to DOT file " + dotFileName);

		String pathToDot = properties.getProperty("pathToDot");
		try {
			String pngFileName = dotFileName.substring(0, dotFileName.length() - 4) + ".png";
			Runtime.getRuntime().exec(pathToDot + " -Tpng " + dotFileName + " -o " + pngFileName);
			LOGGER.info("Summary drawn to PNG file " + pngFileName);
		}
		catch (IOException e) {
			LOGGER.error("Could not turn .dot file into .png (check the pathToDot value in summarization.properties)" + e.toString());
		}
	}

	private void writeNodeToDot(BufferedWriter bw, Long node, String label) {
	try{
		String nColor = dax.getSummaryNodeColor(node);
		bw.write("\"" + label + "\" [style = filled, color=" + 
				nColor +
				(dax.isDarkColor(nColor)?", fontcolor=white ":"")
				+ "];\n");
	}
	catch (IOException e) {
		LOGGER.error("Could not turn .dot file into .png (check the pathToDot value in summarization.properties)" + e.toString());
	}
}

	/**
	 * Given a path to an .nt RDF data file, computes a file name by inserting
	 * the prefix encoding the summary type before the main file name, and
	 * replacing the trailing .nt with .dot
	 *
	 * It also inserts the suffix before the ".".
	 *
	 * @param suffix
	 *
	 * @return
	 */
	protected String getDotFileName(String suffix) {
		int lastDotPosition = Math.max(0, triplesFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, triplesFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + triplesFileName);
		return triplesFileName.substring(0, lastDotPosition) + "_" + summaryTablePrefix + suffix + ".dot"; // replace .nt with .dot
	}

	/**
	 * Given a path to an .nt RDF data file, computes a file name by replacing
	 * the trailing .nt with .dot.
	 *
	 * It also adds the suffix just before the "."
	 *
	 * @param suffix
	 *
	 * @return
	 */
	protected String getRDFDotFileName(String suffix) {
		return (triplesFileName.substring(0, triplesFileName.length() - 3)) + "_" + suffix + ".dot";
	}

	/**
	 * URIs can be too long, thus they may need to be shortened in a .dot file.
	 *
	 * @param URI
	 *
	 * @return
	 */
	protected String getShortURIForDot(String URI) {
		int maxNodeLabelLength = Integer.parseInt(properties.getProperty("maxNodeLabelLength"));
		if (URI.length() < maxNodeLabelLength)
			return URI;
		else
			return "..." + URI.substring(URI.length() - (maxNodeLabelLength - 4), URI.length());
	}

	protected String getVeryShortForDot(String URIorLiteral){
		boolean URI = false;
		if (URIorLiteral.charAt(0) == '<' && (URIorLiteral.charAt(URIorLiteral.length() - 1)) == '>'){
			URI = true; 
		}
		if (!URI){
			return dotSuffixOfStringsAndURIs(URIorLiteral); 
		}
		else{
			int closing = URIorLiteral.length() - 1;
			int lastSlash = URIorLiteral.lastIndexOf('/');
			if (lastSlash == -1){
				return dotSuffixOfStringsAndURIs(URIorLiteral);
			}
			else{
				return URIorLiteral.substring(lastSlash+1, closing); 
			}
		}
	}
	protected String dotSuffixOfStringsAndURIs(String s){
		int suffixLength = new Integer(properties.getProperty("maxNodeLabelLength"));
		if (s.length() <= suffixLength){
			return s; 
		}
		else{
			return  s.substring(s.length() - suffixLength, s.length());
		}
	}

	public void writeRDFGraphToDotFile(Connection conn, String dotFileName) {
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)))) {
				bw.write("digraph g{\n");
				long triplesToDraw = Math.min(25, triplesSummarizedSoFar);
				//LOGGER.debug("Writing " + triplesToDraw + " RDF graph triples to DOT");
				long triplesDrawn;
				if (this.isTypeFirst) {
					try (ResultSet rs = getTypeTriplesCursorForDotDrawing(conn, triplesToDraw)) {
						triplesDrawn = drawTriples(rs, bw);
					}
					if (triplesDrawn < triplesToDraw){
						try (ResultSet rs2 = getNonTypeTriplesCursorForDotDrawing(conn, triplesToDraw-triplesDrawn)) {
							drawTriples(rs2, bw);
						}
					}
				}
				else {
					try (ResultSet rs = getNonTypeTriplesCursorForDotDrawing(conn, triplesToDraw)) {
						triplesDrawn = drawTriples(rs, bw);
					}
					if (triplesDrawn < triplesToDraw){
						try (ResultSet rs2 = getTypeTriplesCursorForDotDrawing(conn, triplesToDraw-triplesDrawn)) {
							drawTriples(rs2, bw);
						}
					}
				}
				bw.write("}\n");
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to read and plot RDF triples: " + e.toString());

		}
		LOGGER.info("Graph written to DOT file " + dotFileName);

		String pathToDot = properties.getProperty("pathToDot");
		try {
			String pngFileName = dotFileName.substring(0, dotFileName.length() - 4) + ".png";
			Runtime.getRuntime().exec(pathToDot + " -Tpng " + dotFileName + " -o " + pngFileName);
			LOGGER.info("Graph drawn to PNG file " + pngFileName);
		}
		catch (IOException e) {
			LOGGER.error("Could not turn .dot file into .png (check the pathToDot value in summarization.properties)" + e.toString());
		}
	}

	/**
	 * Takes triples from a cursor and prints them in DOT format into a buffered writer.
	 * @param rs
	 * @param bw
	 * @return the number of triples drawn. This is needed to control how many triples (if any) we need to print from the second group of triples. 
	 */
	protected long drawTriples(ResultSet rs, BufferedWriter bw){
		long triplesDrawnInDot = 0; 
		try {
			while (rs.next()) {
				String subject = rs.getString(1);
				Long s = RDF2SQLEncoding.dictionaryEncode(subject);
				Long sRep = rep.get(s);
				//LOGGER.debug("DrawTriples: Encoded " + subject + " into " + s + " whose representative is: "  + sRep);

				String object = rs.getString(3);
				Long o = RDF2SQLEncoding.dictionaryEncode(object);
				Long oRep = rep.get(o);

				//LOGGER.debug("DrawTriples: Encoded " + object + " into " + o + " whose representative is: " + oRep);
				String property = rs.getString(2);
				Long p = RDF2SQLEncoding.dictionaryEncode(property);
				//LOGGER.debug("DRAW Triple! (" + subject + " " + property + " " + object + ")");
				//LOGGER.debug("DRAW Represented by: " + sRep + " " + p + " " + oRep);
				writeGraphTripleToDotFile(bw, s, p, o, subject, property, object, sRep, oRep);
				triplesDrawnInDot++;
			}
		}
		catch (SQLException e){
			throw new IllegalStateException("Could not get triple from cursor " + e.toString());
		}
		return triplesDrawnInDot; 
	}

	/**
	 * This is used only when drawing the graph using Dot. 
	 * Different summaries need to traverse their triples in different orders, thus the two cursors which differ between the typed and untyped summaries.
	 * Returns the first cursor, over the non-type triples
	 * @param conn
	 * @param triplesToDraw
	 * @return
	 */
	protected ResultSet getNonTypeTriplesCursorForDotDrawing(Connection conn, long triplesToDraw) {
		try {
			String query = "select d1.value, d2.value, d3.value from (select row_number() over () as id, s, p, o from "
				+ encodedTriplesTableName + ") t join " + dictionaryTableName
				+ " d1 on t.s = d1.key join " + dictionaryTableName
				+ " d2 on t.p = d2.key join " + dictionaryTableName
				+ " d3 on t.o = d3.key where d2.value <> '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' order by id limit " + triplesToDraw;
			return conn.createStatement().executeQuery(query);
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing"); 
		}
	}
	/**
	 * This is used only when drawing the graph using Dot. 
	 * Different summaries need to traverse their triples in different orders, thus the two cursors which differ between the typed and untyped summaries.
	 * Returns the second cursor, over the type triples.
	 * @param conn
	 * @param triplesToDraw
	 * @return
	 */
	protected ResultSet getTypeTriplesCursorForDotDrawing(Connection conn, long triplesToDraw) {
		try {
			String query = "select d1.value, d2.value, d3.value from "
				+ encodedTriplesTableName + " t join " + dictionaryTableName
				+ " d1 on t.s = d1.key join " + dictionaryTableName
				+ " d2 on t.p = d2.key join " + dictionaryTableName
				+ " d3 on t.o = d3.key where d2.value = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' limit " + triplesToDraw;
			return conn.createStatement().executeQuery(query);
		}
		catch(SQLException e){
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing"); 
		}
	}

	protected void writeGraphTripleToDotFile(BufferedWriter bw, Long s, Long p, Long o, String subject, String property, String object, Long sRep, Long oRep) {
		//LOGGER.debug("WRITE GRAPH TRIPLE TO DOT s: " + s + " p: " + p + " o: " + o + " subject: "  + subject + " property " + property + " object " + object + " sRep: " + sRep + " oRep: " + oRep); 
		String subjectForDot = getVeryShortForDot(subject).replaceAll("\"", "");
		String objectForDot = getVeryShortForDot(object).replaceAll("\"", "");
		String propertyForDot = getVeryShortForDot(property).replaceAll("\"", "");

		try {
			if (RDF2SQLEncoding.isDataProperty(p)) {
				//LOGGER.debug("Data-S " + s + " (" + subject + ") represented by  " + sRep);
				//LOGGER.debug(" colored " + 	dax.getSummaryNodeColor(sRep));
				String sColor =  dax.getSummaryNodeColor(sRep); 
				String oColor =  dax.getSummaryNodeColor(oRep); 
				bw.write("\"" + subjectForDot + "\" [style = filled, color=" + sColor + 
						(dax.isDarkColor(sColor)?", fontcolor=white ":"")+ 
						"];\n");
				//LOGGER.debug("Data-O " + o + " (" + object + ") represented by " + oRep + " colored " + dax.getSummaryNodeColor(oRep));
				bw.write("\"" + objectForDot + "\" [style = filled, color=" + oColor + 
						(dax.isDarkColor(oColor)?", fontcolor=white ":"")+ 
						"];\n");
			}
			else if (RDF2SQLEncoding.isSchemaProperty(p)) {
				//LOGGER.debug("SCHEMA TRIPLE"); 
				if (p.equals(RDF2SQLEncoding.getSubClassCode())){
					propertyForDot = "subClass"; 
				}
				if (p.equals(RDF2SQLEncoding.getSubPropertyCode())){
					propertyForDot = "subProperty"; 
				}
				if (p.equals(RDF2SQLEncoding.getDomainCode())){
					propertyForDot = "domain"; 
				}
				if (p.equals(RDF2SQLEncoding.getRangeCode())){
					propertyForDot = "range"; 
				}
				//LOGGER.debug("SCH1 " + s + " (" + subject + ") represented by  " + sRep + " written alone as " + subjectForDot); 
				bw.write("\"" + subjectForDot + "\" [fontcolor=white, style = filled, color=black];\n");

				//LOGGER.debug("SCH2 " + o + " (" + object + ") represented by  " + oRep + " written alone as  "+ objectForDot);
				bw.write("\"" + objectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
			}
			else { // type
				//LOGGER.debug("TYPE TRIPLE"); 
				propertyForDot = "rdf:type";
				//LOGGER.debug("TYP1 " + s + " (" + subject + ") represented by  " + sRep);
				if (dax == null) {
					throw new IllegalStateException("Null dax");
				}
				if (sRep == null) {
					throw new IllegalStateException("Null sRep");
				}
				bw.write("\"" + subjectForDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(sRep) + "];\n");

				//LOGGER.debug("TYP2 " + o + " (" + object + ") represented by  " + oRep);
				bw.write("\"" + objectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
			}
			bw.write("\"" + subjectForDot + "\"" + " -> \"" + objectForDot + "\" [label=\"" + propertyForDot + "\"];\n");
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
	}

	public void writeToFileAndDraw() {
		writeEncodedSummaryToFile(getNTSummaryFileName(""));
		writeEncodedSummaryToDotFile(getDotFileName(""));
	}

	public void writeEncodedSummaryToFile(String fileName) {
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fileName)))) {
				this.writeEncodedTripleToFile(bw);
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not write encoded summary to file: " + fileName + ". Is the path correct?");
		}
	}

	protected void writeEncodedTripleToFile(BufferedWriter bw) throws IOException {
		for (Triple t : edgesWithProv.getSummaryEdges())
			bw.write(t.toString() + "\n");
	}

	public void display() {
		System.out.println("SUMMARY " + this.getClass().getName());
		edgesWithProv.display();
		System.out.println("REPRESENTATION: " + rep.toString()); 
		System.out.println("=======");
	}

	public void writeEncodedSummaryToDotFile(String dotFile) {
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFile)))) {
				bw.write("digraph g{\n");
				for (Triple t : edgesWithProv.getSummaryEdges())
					bw.write(t.s + " -> " + t.o + " [label=\"" + t.p + "\"];\n");
				bw.write("}\n");
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not write encoded summary to dot file: " + dotFile + ". Is the path correct?");
		}
	}

	protected final String getSummaryTriplesSQLQuery() {
		return "select * from " + this.edgeTableName + ";"; 
	}

	public final String getEncodedRepSQLQuery() {
		return "select summarynode from " + this.repTableName + " where graphnode=?;"; 
	}

	public static Summary readSummaryFromPostgres(Connection conn) {
		try {
			conn.setAutoCommit(false);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
		Summary sum = new Summary();
		LOGGER.info("Trying to read summary from Postgres");
		RDF2SQLEncoding.setUp(conn, "dictionary");
		return sum;
	}

	public String getSummaryTablePrefix() {
		return this.summaryTablePrefix;
	}

	public String getSummaryURIPrefix() {
		if (this.summaryTablePrefix.length() < 2)
			throw new IllegalStateException("The method should not be called on an instance of the root Summary type");
		return this.summaryTablePrefix.substring(0, this.summaryTablePrefix.length() - 1);
	}

	public HashMap<String, String> getRunStatistics() {
		HashMap<String, String> stats = new HashMap<>();

		stats.put("inputFileName", triplesFileName);

		stats.put("summaryEdgesSavingTime", Long.toString(summaryEdgesSavingTime));
		stats.put("representationFunctionSavingTime", Long.toString(representationFunctionSavingTime));

		stats.put("classSetCreationTime", Long.toString(classSetCreationTime));
		stats.put("typeTriplesSummarizationTime", Long.toString(typeTriplesSummarizationTime));
		stats.put("dataTriplesSummarizationTime", Long.toString(dataTriplesSummarizationTime));
		stats.put("allTriplesSummarizationTime", Long.toString(allTriplesSummarizationTime));

		stats.put("inputGraphSize", Long.toString(triplesSummarizedSoFar));
		stats.put("outputGraphSize", Integer.toString(edgesWithProv.getSummaryEdges().size()));

		stats.put("inputGraphNumberOfNodes", Long.toString(rep.numberOfKeys()));
		stats.put("outputGraphNumberOfNodes", Long.toString(rep.numberOfDistinctValues()));

		return stats;
	}

	protected void displayClique(TreeSet<Long> clique) {
		System.out.println(showCliqueAsString(clique));
	}

	protected String showCliqueAsString(TreeSet<Long> clique) {
		StringBuffer sb = new StringBuffer();
		sb.append("[");
		for (Long l : clique)
			sb.append(l).append("(").append(RDF2SQLEncoding.dictionaryDecode(l)).append(") ");
		sb.append("]");
		return new String(sb); 
	}

	protected HashMap<Long, TreeSet<Long>> getEdgesFrom(Long s){
		return this.edgesWithProv.get(s); 
	}

	protected HashMap<Long, TreeSet<Long>> getEdgesTo(Long o){
		HashMap<Long, TreeSet<Long>> res = new HashMap<>();
		for (Long s: edgesWithProv.keySet()){
			for (Long p: edgesWithProv.get(s).keySet()){
				// if there is an edge s--p-->o
				if (edgesWithProv.get(s).get(p).contains(o)) {
					TreeSet<Long> onP = res.get(p);
					if (onP == null){ // the first edge labeled p which goes into o 
						onP = new TreeSet<>();
						res.put(p, onP);
					}
					onP.add(s); // add s on p in the result
				}
			}
		}
		return res;  
	}

	public String getEdgesToString(){
		StringBuffer sb = new StringBuffer();
		for (Triple t: edgesWithProv.getSummaryEdges()){
			sb.append(t.toString()).append(" ");
		}
		return new String(sb); 
	}

	public ArrayList<Triple> getSummaryEdges() {
		return edgesWithProv.getSummaryEdges(); 
	}

	public String getEncodedTriplesTableName() {
		return this.encodedTriplesTableName; 
	}

	public String getDictionaryTableName() {
		return this.dictionaryTableName; 
	}
}
