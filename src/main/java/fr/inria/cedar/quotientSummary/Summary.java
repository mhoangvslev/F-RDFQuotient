package fr.inria.cedar.quotientSummary;

import java.io.FileReader;
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

import fr.inria.cedar.quotientSummary.datastructures.EdgesWithProvenanceCounts;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.export.DOTAuxiliary;
import fr.inria.cedar.quotientSummary.export.SummaryExport;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

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

	// The following three attribute serve to identify and store the class sets for RDF resources
	protected Long2LongSet cs; // for each class set ID, a class set
	protected Long2Long n2cs; // for each data node, its class set ID. This is also the rep function for typed nodes
	protected HashMap<TreeSet<Long>, Long> cs2csID; // for each set of types known so far, the ID of that set

	protected Traverser traverser;

	// these serve to represent the nodes that may have types but no data property
	protected long typeOnlyNodeID;
	protected boolean typeOnlyNodeAlreadySeen;

	protected long maxSummaryNode;
	protected Properties properties;
	protected SummarizationProperties summProperties;
	protected LoadingProperties loadingProperties; 
	protected static String SUMMARY_CONFIG_FILE = "conf/summarization.properties";
	protected static String LOADING_CONFIG_FILE = "conf/dataLoading.properties";

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

	public long triplesSummarizedSoFar = 0;
	protected long typeTriplesSummarizedSoFar = 0;
	protected long nonTypeTriplesSummarizedSoFar = 0;

	// statistics
	protected long schemaNodesCollectionTime;
	protected long summaryEdgesSavingTime;
	protected long representationFunctionSavingTime;
	protected long classSetCreationTime;
	protected long typeTriplesSummarizationTime;
	protected long nonTypeTriplesSummarizationTime;
	protected long allTriplesSummarizationTime;
	// for each summary node, the number of graph nodes it represents
	protected HashMap<Long, Long> summaryNodeStatistics;
	// for each summary edge, the number of graph edge it represents
	protected HashMap<Triple, Long> summaryEdgeStatistics;

	// helper class for multicolor printing to DOT
	protected DOTAuxiliary dax; 
	
	// exporter utility
	protected SummaryExport exporter; 
	
	public Summary() {
		LOGGER.setLevel(Level.INFO);
		summaryTablePrefix = ROOT_SUMMARY_PREFIX;
		typeOnlyNodeAlreadySeen = false;
		sn = new HashSet<>();
		rep = new Long2Long();
		edgesWithProv = new EdgesWithProvenanceCounts();

		summaryNodeStatistics = new HashMap<>();
		summaryEdgeStatistics = new HashMap<>();
		properties = new Properties();
		// initialize properties with default values from code
		properties.putAll((new LoadingProperties()).prop);
		properties.putAll((new SummarizationProperties()).prop); 
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		}
		catch(IOException e){
			LOGGER.info("Was not able to load configuration file " + SUMMARY_CONFIG_FILE);
		}
		try{
			checkConsistency = properties.getProperty("consistencyChecks").toLowerCase().equals("true");
		} catch (Exception e) {
			throw new IllegalStateException("Unable to determine if consistency checks are needed");
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
				long s = rs.getLong(1);
				long p = rs.getLong(2);
				long o = rs.getLong(3);
				edgesWithProv.addTriple(s, p, o);
			}
		}
		LOGGER.info("Summary read from Postgres");
	}

	public void setSummaryConfigFile(String fileName) {
		SUMMARY_CONFIG_FILE = fileName;
	}

	/**
	 * Fills in the exporter object
	 */
	protected void ensureExporter(){
		if (exporter == null){
			exporter = new SummaryExport(this, properties, dax, 
					dictionaryTableName, triplesFileName, encodedTriplesTableName); 
		}
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
		long typeCode = RDF2SQLEncoding.getTypeCode();

		String getTriplesString = "select distinct s from " + encodedTriplesTableName
			+ " where p = " + subClassCode
			+ " or p = " + subPropertyCode
			+ " or p = " + domainCode
			+ " or p = " + rangeCode
			+ ";";
		try {
			try (Statement getTriples = conn.createStatement()) {
				getTriples.setFetchSize(10000);
				try (ResultSet rs = getTriples.executeQuery(getTriplesString)) {
					while (rs.next()) {
						long s = rs.getLong(1);
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
			+ " or p = " + rangeCode
			+ " or p = " + typeCode
			+ ";";
		try {
			try (Statement getTriples = conn.createStatement()) {
				getTriples.setFetchSize(10000);
				try (ResultSet rs = getTriples.executeQuery(getTriplesString)) {
					while (rs.next()) {
						long o = rs.getLong(1);
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
		for (long node : this.rep.getKeys()) {
			//sb.append(node).append("=>").append(rep.get(node)).append(" ");
			sb.append(node).append(" (").append(RDF2SQLEncoding.dictionaryDecode(node)).append(") => ").append(rep.get(node)).append("\n");
		}
		return sb.toString();
	}

	protected long getNextSummaryNode() {
		long node = this.maxSummaryNode;
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

	public void gatherStatistics() {
		gatherNodeStatistics();
		gatherEdgeStatistics();
	}

	/**
	 * write in summaryNodeStatistics the number of 
	 * data nodes each summary node represents
	 */
	protected void gatherNodeStatistics() {
		for (Long l: rep.getKeys()){
			Long sn = rep.get(l);
			Long existingSnCount = summaryNodeStatistics.get(sn);
			if (existingSnCount == null){
				existingSnCount = 1L; 
			}
			else{
				existingSnCount = (existingSnCount + 1L);
			}
			summaryNodeStatistics.put(sn, existingSnCount); 
		}
	}

	/**
	 * May 24, 2018: these statistics should be picked directly from the edgesWithCounter.
	 */
	protected void gatherEdgeStatistics() {
		long totalRepresentedEdges = 0; 
		for (Triple t: this.edgesWithProv.getSummaryEdges()){
			long represents = edgesWithProv.getCounter(t.s,t.p, t.o); 
			//System.out.println("Summary edge " + t.toString() + " represented: " + 
			//		represents); 
			summaryEdgeStatistics.put(t, represents); 
			totalRepresentedEdges += represents; 
		}
		//System.out.println("Total number of represented edges: " + totalRepresentedEdges);
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	public void summarizeFromPostgres(Connection conn) {
		if (isTypeFirst) {
			if (isTwoPass) {
				traverser = new TypeFirstTwoPassTraverser(this, conn);
			}
			else {
				traverser = new TypeFirstTraverser(this, conn);
			}
		}
		else {
			if (isTwoPass) {
				traverser = new DataFirstTwoPassTraverser(this, conn);
			}
			else {
				traverser = new DataFirstTraverser(this, conn);
			}
		}
		traverser.traverseAllTriples();
	}

	protected void handleDataTriple(Triple t) {
		throw new IllegalStateException("Not implemented at this level");
	}

	/**
	 * This implementation should be shared by Weak and Strong
	 *
	 * @param t
	 */
	protected void representTypeTripleAfterData(Triple t) {
		Long repS = rep.get(t.s);
		if (repS != null) {
			edgesWithProv.addTriple(repS, t.p, t.o);
		}
		else {
			if (!typeOnlyNodeAlreadySeen) {
				typeOnlyNodeID = getNextSummaryNode();
				typeOnlyNodeAlreadySeen = true;
			}
			edgesWithProv.addTriple(typeOnlyNodeID, t.p, t.o);
			rep.put(t.s, typeOnlyNodeID);
		}
		// o already represented in collectSchemaNodes
	}

	protected void handleTypeTripleBeforeData(Triple t) {
		if (sn.contains(t.s)) { // schemaNode rdf:type classNode, represent right away
			edgesWithProv.addTriple(t.s, t.p, t.o);
			// s, o already represented in collectSchemaNodes
		}
		else {
			Long classSetIDOfThisNode = n2cs.get(t.s);
			TreeSet<Long> classSetOfThisNode = null; 
			if (classSetIDOfThisNode != null){
				classSetOfThisNode = cs.get(classSetIDOfThisNode); 
			}
			if (classSetOfThisNode == null) { // this is the first time we encounter the node: create a class set with exactly this type
				classSetOfThisNode = new TreeSet<>();
				classSetOfThisNode.add(t.o);
				Long thisNodeClassSetID = cs2csID.get(classSetOfThisNode); 
				// comparison between sets uses equals and compares the structures of the sets
				if (thisNodeClassSetID == null) {
					// this class set was not already known so we create it
					thisNodeClassSetID = getNextSummaryNode();
					cs.put(thisNodeClassSetID, classSetOfThisNode); // installs the new class set
					cs2csID.put(classSetOfThisNode, thisNodeClassSetID); // installs the new class set
				}
				// whether or not newClassSetID was known:
				n2cs.put(t.s, thisNodeClassSetID); // erases/replaces previously known class set ID
			}
			else if (!classSetOfThisNode.contains(t.o)) { // we already had some types for t.s but not this one so we need to add new type
				// n is moving from classSetOfThisNode to newClassSetOfThisNode.
				// TODO Check if classSetOfThisNode is deserted and if yes, maybe remove it.
				// (We can also keep it there to reuse it later...)
				TreeSet<Long> newClassSetOfThisNode = new TreeSet<>();
				newClassSetOfThisNode.addAll(classSetOfThisNode);
				newClassSetOfThisNode.add(t.o);
				Long newClassSetID = cs2csID.get(newClassSetOfThisNode);
				if (newClassSetID == null) {
					newClassSetID = getNextSummaryNode();
					cs.put(newClassSetID, newClassSetOfThisNode);
					cs2csID.put(newClassSetOfThisNode, newClassSetID);
				}
				n2cs.put(t.s, newClassSetID);
			}
			//else {
			// do nothing
			//}
		}
	}
	
	/**
	 * This method adds the type triples in the summary, based on the structures previously filled in while traversing those triples.
	 * It is called only once and will output all the type triples of the summary.
	 */
	protected void representTypeTriplesBeforeData() {
		//LOGGER.debug("POST HANDLE TYPE TRIPLES");
		for (long node: n2cs.getKeys()) {
			long thisClassSetID = n2cs.get(node);
			TreeSet<Long> thisClassSet = cs.get(thisClassSetID); // the class set IS the representative
			for (long thisClass: thisClassSet) {
				edgesWithProv.addTriple(thisClassSetID, RDF2SQLEncoding.getTypeCode(), thisClass);
				rep.put(node, thisClassSetID);
				// o already represenated in collectSchemaNodes
			}
		}
	}

	protected void classifyDataTriple(Triple t) {
		throw new IllegalStateException("Not implemented at this level");
	}

	protected void classificationPostProcessing() {
		throw new IllegalStateException("Not implemented at this level");
	}

	protected void representDataTriple(Triple t) {
		throw new IllegalStateException("Not implemented at this level");
	}

	protected void consistencyChecks() {
		throw new IllegalStateException("Not implemented at this level");
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
	public void saveSummaryInPostgres(Connection conn, boolean partialResult, String summarizationInput) {
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
		else if (summarizationInput.equals("")) {
			newTableName = "sav_" + timestamp + "_" + newTableName + "_" + getSummaryURIPrefix();
		}
		else {
			newTableName = "sav_" + timestamp + "_" + newTableName + "_" + summarizationInput + "_" + getSummaryURIPrefix();
		}
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
					for (long origNode : origNodes) {
						long sumNode = rep.get(origNode);
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
			stmt.executeUpdate("insert into saved_summary_table_names values ('dictionary', '" + dictionaryTableName + "');" );
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

	public void drawSummaryAndGraph(Connection conn, String suffix) {
		SummaryExport exporter = new SummaryExport(this, properties, dax, 
				dictionaryTableName, triplesFileName, encodedTriplesTableName); 
		String summaryDotFileName = exporter.getDotFileName(suffix);
		exporter.writeSummaryToDotFile(conn, summaryDotFileName);
		String graphDotFileName = exporter.getRDFDotFileName(suffix);
		exporter.writeRDFGraphToDotFile(conn, graphDotFileName);
	}

	public void writeToFileAndDraw() {
		SummaryExport exporter = new SummaryExport(this, properties, dax, 
				dictionaryTableName, triplesFileName, encodedTriplesTableName); 
		exporter.writeEncodedSummaryToFile(exporter.getNTSummaryFileName(""));
		exporter.writeEncodedSummaryToDotFile(exporter.getDotFileName(""));
	}
	

	public void display() {
		System.out.println("SUMMARY " + this.getClass().getName());
		edgesWithProv.display();
		System.out.println("REPRESENTATION: " + rep.toString()); 
		System.out.println("=======");
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
		stats.put("summaryType", getSummaryURIPrefix());

		stats.put("summaryEdgesSavingTime", Long.toString(summaryEdgesSavingTime));
		stats.put("representationFunctionSavingTime", Long.toString(representationFunctionSavingTime));

		stats.put("schemaNodesCollectionTime", Long.toString(schemaNodesCollectionTime));
		stats.put("classSetCreationTime", Long.toString(classSetCreationTime));
		stats.put("typeTriplesSummarizationTime", Long.toString(typeTriplesSummarizationTime));
		stats.put("nonTypeTriplesSummarizationTime", Long.toString(nonTypeTriplesSummarizationTime));
		stats.put("allTriplesSummarizationTime", Long.toString(allTriplesSummarizationTime));

		stats.put("inputGraphSize", Long.toString(triplesSummarizedSoFar));
		stats.put("inputGraphTypeTriples", Long.toString(typeTriplesSummarizedSoFar));
		stats.put("inputGraphNonTypeTriples", Long.toString(nonTypeTriplesSummarizedSoFar));
		stats.put("outputGraphSize", Integer.toString(edgesWithProv.getSummaryEdges().size()));

		stats.put("inputGraphNumberOfNodes", Long.toString(rep.numberOfKeys()));
		stats.put("outputGraphNumberOfNodes", Long.toString(rep.numberOfDistinctValues()));

		return stats;
	}

	protected void displayClique(HashSet<Long> clique) {
		System.out.println(showCliqueAsString(clique));
	}

	protected String showCliqueAsString(HashSet<Long> clique) {
		StringBuffer sb = new StringBuffer();
		sb.append("[");
		for (long l : clique)
			sb.append(l).append("(").append(RDF2SQLEncoding.dictionaryDecode(l)).append(") ");
		sb.append("]");
		return new String(sb); 
	}

	protected HashMap<Long, HashSet<Long>> getEdgesFrom(long s){
		return this.edgesWithProv.get(s);
	}

	protected HashMap<Long, HashSet<Long>> getEdgesTo(long o){
		HashMap<Long, HashSet<Long>> res = new HashMap<>();
		for (long s: edgesWithProv.keySet()){
			for (long p: edgesWithProv.get(s).keySet()){
				// if there is an edge s--p-->o
				if (edgesWithProv.get(s).get(p).contains(o)) {
					HashSet<Long> onP = res.get(p);
					if (onP == null){ // the first edge labeled p which goes into o 
						onP = new HashSet<>();
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

	public HashMap<Long, Long> getSummaryNodeStatistics(){
		return this.summaryNodeStatistics; 
	}
	public HashMap<Triple, Long> getSummaryEdgeStatistics(){
		return this.summaryEdgeStatistics; 
	}
	public String getEncodedTriplesTableName() {
		return this.encodedTriplesTableName; 
	}

	public String getDictionaryTableName() {
		return this.dictionaryTableName; 
	}

	public boolean isTypeFirst() {
		return this.isTypeFirst; 
	}

	public long getRepresentative(long l) {
		return rep.get(l); 
	}

	public HashSet<Long> getSchemaNodes() {
		return sn; 
	}

	public void writeDecodedSummaryToNTFile(Connection conn, String summarizationTechnique) {
		ensureExporter();
		exporter.writeDecodedSummaryToNTFile(conn, summarizationTechnique);
	}

	public String getNTSummaryFileName(String summarizationTechnique) {
		ensureExporter();
		return exporter.getNTSummaryFileName(summarizationTechnique);
	}

	public Long getRepresentedNodeNumber(Long s) {
		return summaryNodeStatistics.get(s);
	}
	public Long getRepresentedTripleNumber(Triple t){
		return summaryEdgeStatistics.get(t);
	}
}
