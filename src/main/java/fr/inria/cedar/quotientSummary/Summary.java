package fr.inria.cedar.quotientSummary;

import fr.inria.cedar.quotientSummary.datastructures.EdgesWithProvenanceCounts;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.DOTAuxiliary;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.io.BufferedReader;
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
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Summary {
	private static final Logger LOGGER = Logger.getLogger(Summary.class.getName());
	private static final SimpleDateFormat SD_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");

	protected Long2Long rep; // representative function for untyped nodes
	protected boolean typeTriplesExist = false;
	// data, schema and type triples:
	//   for each subject
	//     for each property
	//       the set of objects such that (subject, property, object) is in the summary
	protected EdgesWithProvenanceCounts edgesWithProv;
	// for each summary node, the number of graph nodes it represents
	protected HashMap<Long, Long> summaryNodeStatistics;
	// for each summary edge, the number of graph edge it represents
	protected HashMap<Triple, Long> summaryEdgeStatistics;
	// these serve to represent the nodes that may have types but no data property
	protected long typeOnlyNodeID;
	protected boolean typeOnlyNodeAlreadySeen;
	protected Triple lastReadTriple;
	protected long maxSummaryNode;
	protected Properties properties;
	protected static String SUMMARY_CONFIG_FILE = "conf/summarization.properties";
	// repTablePrefix must be instantiated with a specific string for each summary type, so that each summary is saved as separated Postgres tables
	protected String summaryTablePrefix;
	protected static String ROOT_SUMMARY_PREFIX = "";
	protected static String WEAK_SUMMARY_PREFIX = "w_";
	protected static String STRONG_SUMMARY_PREFIX = "s_";
	protected static String TYPED_WEAK_SUMMARY_PREFIX = "tw_";
	protected static String TYPED_STRONG_SUMMARY_PREFIX = "ts_";
	protected static String TWO_PASS_STRONG_SUMMARY_PREFIX = "ts2_";
	protected static String TWO_PASS_TYPED_STRONG_SUMMARY_PREFIX = "ts2_";
	
	protected boolean checkConsistency = false;
	protected long triplesSummarizedSoFar = 0;
	protected DOTAuxiliary dax;
	// statistics
	protected long summaryEdgesSavingTime;
	protected long representationFunctionSavingTime;
	protected long classSetCreationTime;
	protected long typeTriplesSummarizationTime;
	protected long dataTriplesSummarizationTime;
	protected long allTriplesSummarizationTime;

	public Summary() {
		LOGGER.setLevel(Level.INFO);
		rep = new Long2Long();
		edgesWithProv = new EdgesWithProvenanceCounts();
		summaryNodeStatistics = new HashMap<>();
		summaryEdgeStatistics = new HashMap<>();
		typeOnlyNodeAlreadySeen = false;
		properties = new Properties();
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
			checkConsistency = properties.getProperty("consistencyChecks").toLowerCase().equals("true");
		} catch (IOException e) {
			throw new IllegalStateException("Unable to initialize summary properties");
		}
		summaryTablePrefix = ROOT_SUMMARY_PREFIX;
		dax = new DOTAuxiliary();
	}

	/**
	 * Reads an integer-encoded triple out of a string (a line)
	 *
	 * @param spo
	 *
	 * @return
	 */
	protected Triple readTriple(String spo) {
		int spacePos = spo.indexOf(' ');
		LOGGER.debug("String position " + spacePos + " out of: " + spo.length());
		Long s = new Long(spo.substring(0, spacePos));
		spo = spo.substring(spacePos + 1, spo.length());
		LOGGER.debug("Read s: " + s + " spo is: #" + spo + "#");

		spacePos = spo.indexOf(' ');
		LOGGER.debug("String position " + spacePos + " out of: " + spo.length());
		Long p = new Long(spo.substring(0, spacePos));
		LOGGER.debug("Read p: "+ p + " spo is: =" + spo + "=");
		spo = spo.substring(spacePos + 1, spo.length());

		Long o = new Long(spo);
		LOGGER.debug("Read o: " + o);
		lastReadTriple = new Triple(s, p, o);
		return lastReadTriple;
	}

	
//	protected void checkTypeIsObject() {
//		for (Long s : edges.keySet())
//			for (Long p : edges.get(s).keySet())
//				for (Long o : edges.get(s).get(p))
//					if (o == RDF2SQLEncoding.getTypeCode())
//						throw new Error("Found type in object position for " + s + " " + RDF2SQLEncoding.dictionaryDecode(s) + " and " + RDF2SQLEncoding.dictionaryDecode(p));
//	}

	// we need to be sure that integers which we invent to represent nodes
	// will not collide with the codes already given to classes and properties
	// (which, in this implementation, for simplicity, are preserved).
	protected void avoidCollisionsWhenAssigningSummaryNodes(Connection conn) {
		long maxClassOrPropertyCode = 0;
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();

		String jumpRepString = ("select max(o) from tmp_encoded t1 where p=" + typeConstantCode);
		try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
			while (rs.next()) {
				maxClassOrPropertyCode = rs.getLong(1);
				break;
			}
		}
		catch (SQLException ex1) {
			jumpRepString = ("select max(o) from tmp_encoded_saturated t1 where p=" + typeConstantCode);
			try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
				while (rs.next()) {
					long rs_val = rs.getLong(1);
					if (maxClassOrPropertyCode < rs_val)
						maxClassOrPropertyCode = rs_val;
					break;
				}
			}
			catch (SQLException ex2) {
			}
		}

		jumpRepString = ("select max(o) from tmp_encoded_summarized t1 where p=" + typeConstantCode);
		try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
			while (rs.next()) {
				long rs_val = rs.getLong(1);
				if (maxClassOrPropertyCode < rs_val)
					maxClassOrPropertyCode = rs_val;
				break;
			}
		}
		catch (SQLException ex1) {
			jumpRepString = ("select max(o) from tmp_encoded_summarized_saturated t1 where p=" + typeConstantCode);
			try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
				while (rs.next()) {
					long rs_val = rs.getLong(1);
					if (maxClassOrPropertyCode < rs_val)
						maxClassOrPropertyCode = rs_val;
					break;
				}
			}
			catch (SQLException ex2) {
			}
		}

		this.jumpSummaryNodeCount(maxClassOrPropertyCode + 1);
	}

	protected void showRepInBuffer(StringBuffer sb) {
		for (Long node : this.rep.getKeys()) {
			//sb.append(node).append("=>").append(rep.get(node)).append(" ");
			sb.append(node + " (" + RDF2SQLEncoding.dictionaryDecode(node)).append(") => ").append(rep.get(node)).append("\n");
		}
	}

	protected void showRep() {
		StringBuffer sb = new StringBuffer();
		showRepInBuffer(sb);
		System.out.println(sb.toString());
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
	 * Computes node statistics through a GROUP-BY query. Should be called after
	 * the summary is completely computed and stored in Postgres.
	 */
	private void gatherNodeStatistics() {
		try {
			try (
				Statement nodeStatisticQuery = RDF2SQLEncoding.getConnection().createStatement();
				ResultSet rs = nodeStatisticQuery.executeQuery("select summarynode, count(*) from " + this.getSummaryTablePrefix() + "encoded_rep group by summarynode;")
				// TODO: needs to be modified to account new naming convention
			) {
				while (rs.next()) {
					Long summaryNode = rs.getLong(1);
					Long numberOfReprGraphNodes = rs.getLong(2);
					this.summaryNodeStatistics.put(summaryNode, numberOfReprGraphNodes);
				}
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not compute node representation statistics from Postgres: " + e.toString());
		}
	}

	/**
	 * Computes edge statistics through a GROUP-BY query. Should be called after
	 * the summary is completely computed and stored in Postgres.
	 */
	private void gatherEdgeStatistics() {
		try {
			try (
				Statement edgeStatisticQuery = RDF2SQLEncoding.getConnection().createStatement();
				ResultSet rs = edgeStatisticQuery.executeQuery(
					"select es.s as summary_source, es.p as summary_prop, es.o as summary_target, count(*) " + "from "
					+ summaryTablePrefix + "encoded_rep rep1, " + summaryTablePrefix
					+ "encoded_rep rep2, encoded_triples t, " + summaryTablePrefix + "encoded_summary es " // TODO: needs to be modified to account new naming convention
					+ "where rep1.graphnode = t.s and rep2.graphnode=t.o and es.s = rep1.summarynode and es.o = rep2.summarynode and es.p = t.p "
					+ "group by es.s, es.p, es.o;")
					// + "order by es.s, es.p, es.o;");
			) {
				while (rs.next()) {
					Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
					this.summaryEdgeStatistics.put(t, rs.getLong(4));
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not compute edge representation statistics from Postgres: " + e.toString());
		}
	}

	protected void handleTypeTripleAfterData(Triple t) {
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
	 * @param summarizationTechnique
	 * @param dictionaryTableName
	 * @return
	 */
	public String saveSummaryInPostgres(Connection conn, Boolean partialResult, String summarizationTechnique, String dictionaryTableName) {
		String newTableName = "_encoded_";
		String timestamp = SD_FORMAT.format(new Timestamp(System.currentTimeMillis()));
		if (partialResult)
			newTableName = "tmp" + newTableName + "summarized";
		else
			newTableName = "sav_" + timestamp + newTableName + summaryTablePrefix + summarizationTechnique;
		String newSummaryTableNameRep = newTableName + "_rep";
		String newSummaryTableNameSum = newTableName + "_sum";
		String newDictionaryTableName = "sav_" + timestamp + "_" + dictionaryTableName;

		LOGGER.info("Saving " + this.getClass().getName() + " in Postgres in tables " + newSummaryTableNameRep + " and " + newSummaryTableNameSum);

		Statement stmt;
		try {
			stmt = conn.createStatement();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not create the statement: " + e.toString());
		}

		// change the dictionary table's name by prepending the timestamp
		try {
			conn.setAutoCommit(false);
			stmt.execute("alter table " + dictionaryTableName + " rename to " + newDictionaryTableName + ";");
			conn.commit();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not insert summary triples in " + newSummaryTableNameRep + ": " + e.toString());
		}
		
		if (!partialResult) {
			// save representation function
			try {
				conn.setAutoCommit(false);
				long start = System.currentTimeMillis();
				// create the table (it may have existed)
				if (!existsTable(conn, newSummaryTableNameRep)) {
					stmt.execute("create table " + newSummaryTableNameRep + "(graphNode int not null, summaryNode int not null);");
					LOGGER.info("Table " + newSummaryTableNameRep + "created");
				}
				else
					LOGGER.info("Did not create " + newSummaryTableNameRep + " table as it was already there");
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
			conn.setAutoCommit(false);
			long start = System.currentTimeMillis();
			// create the table (it may have existed)
			if (!existsTable(conn, newSummaryTableNameSum)) {
				stmt.execute("create table " + newSummaryTableNameSum + "(s int not null, p int not null, o int not null);");
				LOGGER.info("Table " + newSummaryTableNameSum + " created");
			}
			else
				LOGGER.info("Did not create " + newSummaryTableNameSum + " table as it was already there");
			// empty it (even if the creation failed, e.g. because the table was already there)
			stmt.executeUpdate("delete from " + newSummaryTableNameSum + ";");

			// now insert all the summary edges:
			String insertIntoSummary = "insert into " + newSummaryTableNameSum + " values(?, ?, ?);";
			try (PreparedStatement insertInSummary = conn.prepareStatement(insertIntoSummary)) {
				ArrayList<Triple> edges = edgesWithProv.getSummaryEdges();
				for (Triple t : edges) {
					LOGGER.debug("Saving in Postgres edge: " + t.toString());
					insertInSummary.setLong(1, t.s);
					insertInSummary.setLong(2, t.p);
					insertInSummary.setLong(3, t.o);
					insertInSummary.executeUpdate();
				}
				// if (!hasIndex(conn, "encoded_summary"))
				//	stmt.executeUpdate("create index indSummaryS on encoded_summary(s);");
				conn.commit();
				summaryEdgesSavingTime = System.currentTimeMillis() - start;
				LOGGER.info("Summary edges saved in " + summaryEdgesSavingTime + " ms");
				LOGGER.info("Summary saved in Postgres");
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not insert summary triples in " + newSummaryTableNameSum + ": " + e.toString());
		}

		return newDictionaryTableName;
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

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres. It prints the
	 * summary to the standard output and also saves it in a separate .nt file
	 *
	 * @param conn
	 * @param graphFileName
	 * @param dictionaryTableName
	 */
	public void writeDecodedSummaryToNTFile(Connection conn, String graphFileName, String dictionaryTableName) {
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);

		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");

		String summaryNTFileName = getNTSummaryFileName(graphFileName);

		//LOGGER.info("Decoding summary and writing it in .nt format to " + summaryNTFileName);

		boolean gatherStatistics = properties.getProperty("gatherStatistics").toLowerCase().equals("true");
		if (gatherStatistics)
			this.gatherStatistics();
		ArrayList<Triple> summEdges = edgesWithProv.getSummaryEdges();
		try {
			// write summary triples:
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryNTFileName)))) {
				// write summary triples:
				for (Triple t : summEdges) {
					LOGGER.debug("Summary triple: " + t.toString() );
					String subject = "", property = "", object = "";
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
					LOGGER.debug(subject + " " + property + " " + object);
					bw.write(subject + " " + property + " " + object + " .\n");
				}
				if (gatherStatistics) {
					// write node cardinality statistics:
					for (Long node : this.summaryNodeStatistics.keySet()) {
						Long numberOfRepresentedGraphNodes = this.summaryNodeStatistics.get(node);
						String subject = getSummaryNodeURI(URIprefix, node);
						String property = properties.getProperty("summaryNodeSupportURI");
						String object = ("\"" + numberOfRepresentedGraphNodes + "\"");
						LOGGER.debug(subject + " " + property + " " + object);
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

	private String getCoreGraphFileName(String graphFileName) {
		int lastDotPosition = Math.max(0, graphFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, graphFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + graphFileName);
		return graphFileName.substring(lastSlashPosition + 1, lastDotPosition + 3);
	}

	private String getNTSummaryFileName(String graphFileName) {
		String coregraphFileName = getCoreGraphFileName(graphFileName);
		String summaryNTFileName = graphFileName.replaceFirst(coregraphFileName, (this.summaryTablePrefix + coregraphFileName));
		return summaryNTFileName;
	}

	/**
	 * This is the inventor of summary node URIs.
	 *
	 * @param uriPrefix
	 * @param n
	 *
	 * @return
	 */
	private String getSummaryNodeURI(String uriPrefix, long n) {
		return ("<" + uriPrefix + this.getSummaryURIPrefix() + n + ">");
	}

	public void drawSummaryAndGraph(Connection conn, String fullGraphFileName, String triplesTableName, String dictionaryTableName) {
		this.drawSummaryAndGraph(conn, fullGraphFileName, triplesTableName, dictionaryTableName, "");
	}

	protected void drawSummaryAndGraph(Connection conn, String fullGraphFileName, String triplesTableName, String dictionaryTableName, String suffix) {
		String summaryDotFileName = getDotFileName(fullGraphFileName, suffix);
		writeSummaryToDotFile(conn, summaryDotFileName, dictionaryTableName);
		String graphDotFileName = getRDFDotFileName(fullGraphFileName, suffix);
		writeRDFGraphToDotFile(conn, graphDotFileName, triplesTableName);
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres
	 *
	 * @param conn
	 * @param dotFileName
	 * @param dictionaryTableName
	 */
	protected void writeSummaryToDotFile(Connection conn, String dotFileName, String dictionaryTableName) {
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		dax.resetColors();
		Properties properties = new Properties();
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read config file: " + e.toString());
		}
		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");
		LOGGER.debug("writeSummaryToDotFile:");

		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)))) {
				bw.write("digraph g{\n");

				ArrayList<Triple> summEdges = edgesWithProv.getSummaryEdges();
				for (Triple t : summEdges) {
					String subject, property, object, subjectInDot, propertyInDot, objectInDot;
					// in all cases, edge labels are preserved:
					property = getShortURIForDot(RDF2SQLEncoding.dictionaryDecode(t.p));
					propertyInDot = property.replaceAll("\"", "");

					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						subject = this.getSummaryNodeURI(URIprefix, t.s);
						subjectInDot = getShortURIForDot(subject).replaceAll("\"", "");
						if (dax.unknownSummaryNode(t.s))
							bw.write("\"" + subjectInDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(t.s) + "];\n");
						object = this.getSummaryNodeURI(URIprefix, t.o);
						objectInDot = getShortURIForDot(object).replaceAll("\"", "");
						if (dax.unknownSummaryNode(t.o))
							bw.write("\"" + objectInDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(t.o) + "];\n");
					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						subject = getShortURIForDot(RDF2SQLEncoding.dictionaryDecode(t.s));
						subjectInDot = subject.replaceAll("\"", "");
						property = getShortURIForDot(RDF2SQLEncoding.dictionaryDecode(t.p));
						propertyInDot = property.replaceAll("\"", "");
						object = getShortURIForDot(RDF2SQLEncoding.dictionaryDecode(t.o));
						objectInDot = object.replaceAll("\"", "");
						if (dax.unknownSummaryNode(t.s))
							bw.write("\"" + subjectInDot + "\" [fontcolor=white, style = filled, color=black];\n");
						if (dax.unknownSummaryNode(t.o))
							bw.write("\"" + objectInDot + "\" [fontcolor=white, style = filled, color=black];\n");
					} else { // type
						subject = this.getSummaryNodeURI(URIprefix, t.s);
						subjectInDot = subject.replaceAll("\"", "");
						object = getShortURIForDot(RDF2SQLEncoding.dictionaryDecode(t.o));
						objectInDot = object.replaceAll("\"", "");
						property = "rdf:type";
						if (dax.unknownSummaryNode(t.s))
							bw.write("\"" + subjectInDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(t.s) + "];\n");
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

	/**
	 * Given a path to an .nt RDF data file, computes a file name by inserting
	 * the prefix encoding the summary type before the main file name, and
	 * replacing the trailing .nt with .dot
	 *
	 * It also inserts the suffix with a "-" before the ".".
	 *
	 * @param fullGraphFileName
	 * @param suffix
	 *
	 * @return
	 */
	private String getDotFileName(String fullGraphFileName, String suffix) {
		String coreGraphFileName = getCoreGraphFileName(fullGraphFileName);
		String dotFileName = fullGraphFileName.replaceFirst(coreGraphFileName, (this.summaryTablePrefix + coreGraphFileName));
		dotFileName = dotFileName.substring(0, dotFileName.length() - 3) + suffix + ".dot"; // replace .nt with .dot
		return dotFileName;
	}

	/**
	 * Given a path to an .nt RDF data file, computes a file name by replacing
	 * the trailing .nt with .dot.
	 *
	 * It also adds the suffix just before the "."
	 *
	 * @param fullGraphFileName
	 * @param suffix
	 *
	 * @return
	 */
	private String getRDFDotFileName(String fullGraphFileName, String suffix) {
		return (fullGraphFileName.substring(0, fullGraphFileName.length() - 3)) + suffix + ".dot";
	}

	/**
	 * URIs can be too long, thus they may need to be shortened in a .dot file.
	 *
	 * @param URI
	 *
	 * @return
	 */
	private String getShortURIForDot(String URI) {
		int maxNodeLabelLength = Integer.parseInt(properties.getProperty("maxNodeLabelLength"));
		if (URI.length() < maxNodeLabelLength)
			return URI;
		else
			return "..." + URI.substring(URI.length() - (maxNodeLabelLength - 4), URI.length());
	}

	public void writeRDFGraphToDotFile(Connection conn, String dotFileName, String triplesTableName) {
		Properties properties = new Properties();
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read config file");
		}

		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)))) {
				bw.write("digraph g{\n");
				long triplesToDraw = Math.min(25, triplesSummarizedSoFar);
				LOGGER.debug("Writing " + triplesToDraw + " RDF graph triples to DOT");
				long triplesDrawn;
				try (ResultSet rs = getGraphTriplesCursor1ForDotDrawing(conn, triplesToDraw, triplesTableName)) {
					triplesDrawn = drawTriples(rs, bw);
				}
				if (triplesDrawn < triplesToDraw){
					try (ResultSet rs2 = getGraphTriplesCursor2ForDotDrawing(conn, (triplesToDraw-triplesDrawn), triplesTableName)) {
						drawTriples(rs2, bw);
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

				String object = rs.getString(3);
				Long o = RDF2SQLEncoding.dictionaryEncode(object);
				Long oRep = rep.get(o);

				String property = rs.getString(2);
				Long p = RDF2SQLEncoding.dictionaryEncode(property);

				LOGGER.debug("Triple! (" + subject + " " + property + " " + object + ")");
				LOGGER.debug("Represented by: " + sRep + " " + p + " " + oRep);
				writeGraphTripleToDotFile(bw, s, p, o, subject, property, object, sRep, oRep);
				triplesDrawnInDot++;

			}
		}
		catch (SQLException e){
			throw new IllegalStateException("Could not get triple from cursor " + e.toString());
		}
		return triplesDrawnInDot; 
	}

	protected ResultSet getGraphTriplesCursor1ForDotDrawing(Connection conn, long limit, String triplesTableName) {
		throw new IllegalStateException("Not supposed to be called at this level"); 
	}
	protected ResultSet getGraphTriplesCursor2ForDotDrawing(Connection conn, long limit, String triplesTableName) {
		throw new IllegalStateException("Not supposed to be called at this level"); 
	}

	private void writeGraphTripleToDotFile(BufferedWriter bw, Long s, Long p, Long o, String subject, String property, String object, Long sRep, Long oRep) {
		String subjectForDot = getShortURIForDot(subject).replaceAll("\"", "");
		String objectForDot = getShortURIForDot(object).replaceAll("\"", "");
		String propertyForDot = getShortURIForDot(property).replaceAll("\"", "");

		try {
			if (RDF2SQLEncoding.isDataProperty(p)) {
				//if (dax.unknownRDFNode(s))
				//	LOGGER.debug("Data-S " + s + " (" + subject + ") represented by  " + sRep + " colored " + dax.getSummaryNodeColor(sRep));
				bw.write("\"" + subjectForDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(sRep) + "];\n");
				//if (dax.unknownRDFNode(o))
				//	LOGGER.debug("Data-O " + o + " (" + object + ") represented by " + oRep + " colored " + dax.getSummaryNodeColor(oRep));
				bw.write("\"" + objectForDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(oRep) + "];\n");
			}
			else if (RDF2SQLEncoding.isSchemaProperty(p)) {
				propertyForDot = getShortURIForDot(property).replaceAll("\"", "");
				//if (dax.unknownRDFNode(s))
				//	LOGGER.debug("SCH1 " + s + " (" + subject + ") represented by  " + sRep);
				bw.write("\"" + subjectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
				//if (dax.unknownRDFNode(o))
				//	LOGGER.debug("SCH2 " + s + " (" + subject + ") represented by  " + sRep);
				bw.write("\"" + objectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
			}
			else { // type
				LOGGER.debug("Type triple");
				//if (dax.unknownRDFNode(s))
				//	LOGGER.debug("TYP1 " + s + " (" + subject + ") represented by  " + sRep);
				if (dax == null){
					throw new IllegalStateException("Null dax");
				}
				if (sRep == null){
					throw new IllegalStateException("Null sRep");
				}
				bw.write("\"" + subjectForDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(sRep) + "];\n");

				//if (dax.unknownRDFNode(o))
				//	LOGGER.debug("TYP2 " + o + " (" + object + ") represented by  " + oRep);
				bw.write("\"" + objectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
			}
			bw.write("\"" + subjectForDot + "\"" + " -> \"" + objectForDot + "\" [label=\"" + propertyForDot + "\"];\n");
			LOGGER.debug("Written a line in dot");
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
	}

	public void display(String fullGraphFileName) {
		writeEncodedSummaryToFile(getNTSummaryFileName(fullGraphFileName));
		writeEncodedSummaryToDotFile(getDotFileName(fullGraphFileName, ""));
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

	private void writeEncodedTripleToFile(BufferedWriter bw) throws IOException {
		for (Triple t : edgesWithProv.getSummaryEdges())
			bw.write(t.toString() + "\n");
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (Triple t : edgesWithProv.getSummaryEdges()) {
			sb.append(t.toString());
			sb.append(" ");
		}
		return new String(sb);
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

	public Summary(Connection conn) throws SQLException {
		LOGGER.info("Trying to read summary from Postgres");
		RDF2SQLEncoding.setUp(conn, "dictionary");
		LOGGER.debug("Set up special URIs from dictionary");
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

	public void summarizeFromPostgres(Connection conn, String[] args) {
		throw new IllegalStateException("This method is not defined for " + this.getClass().getName());
	}

	protected final String getSummaryTriplesSQLQuery() {
		return "select *  from " + getSummaryTablePrefix() + "encoded_summary"; // TODO: needs to be modified to account new naming convention
	}

	public final String getEncodedRepSQLQuery() {
		return "select summarynode from " + getSummaryTablePrefix() + "encoded_rep where graphnode=?";  // TODO: needs to be modified to account new naming convention
	}

	public static Summary readSummaryFromPostgres(Connection conn) {
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

	public HashMap<String, Long> getRunStatistics() {
		HashMap<String, Long> stats = new HashMap<>();

		stats.put("summaryEdgesSavingTime", summaryEdgesSavingTime);
		stats.put("representationFunctionSavingTime", representationFunctionSavingTime);

		stats.put("classSetCreationTime", classSetCreationTime);
		stats.put("typeTriplesSummarizationTime", typeTriplesSummarizationTime);
		stats.put("dataTriplesSummarizationTime", dataTriplesSummarizationTime);
		stats.put("allTriplesSummarizationTime", allTriplesSummarizationTime);

		stats.put("inputGraphSize", triplesSummarizedSoFar);
		stats.put("outputGraphSize", new Long(edgesWithProv.getSummaryEdges().size()));

		return stats;
	}

	protected void showClique(TreeSet<Long> clique) {
		LOGGER.info(showCliqueAsString(clique));
	}
	protected String showCliqueAsString(TreeSet<Long> clique) {
		StringBuffer sb = new StringBuffer();
		sb.append("[");
		for (Long l : clique)
			sb.append(l).append("(" + RDF2SQLEncoding.dictionaryDecode(l) + ") ");
		sb.append("]");
		return new String(sb); 
	}
	
	protected HashMap<Long, TreeSet<Long>> getEdgesFrom(Long s){
		return this.edgesWithProv.get(s); 
	}
	protected HashMap<Long, TreeSet<Long>> getEdgesTo(Long o){
		HashMap<Long, TreeSet<Long>> res = new HashMap<Long, TreeSet<Long>>();
		for (Long s: edgesWithProv.keySet()){
			for (Long p: edgesWithProv.get(s).keySet()){
				// if there is an edge s--p-->o
				if (edgesWithProv.get(s).get(p).equals(o)) {
					TreeSet<Long> onP = res.get(p);
					if (onP == null){ // the first edge labeled p which goes into o 
						onP = new TreeSet<Long>();
						res.put(p, onP);
					}
					onP.add(s); // add s on p in the result
				}
			}
		}
		return res;  
	}
	protected void addIncomingEdges(Long node, Long2LongSet newEdges){
		//System.out.println("SUMMARY ADD INCOMING EDGES INTO " + node);
		for (Long p: newEdges.keys()){
			//System.out.println("SUMMARY ADD INCOMING EDGES: incoming property: " + p);
			for (Long s: newEdges.get(p)){
				//System.out.println("SUMMARY ADDDING " + s + "--" + p + "-->" + node); 
				edgesWithProv.addTriple(s, p, node);
			}
		}
	}
	protected void removeIncomingEdges(Long node, Long2LongSet removedEdges){
		for (Long p: removedEdges.keys()){
			for (Long s: removedEdges.get(p)){
				edgesWithProv.removeTriple(s, p, node); 
			}
		}
	}
	protected void addOutgoingEdges(Long node, Long2LongSet newEdges){
		for (Long p: newEdges.keys()){
			for (Long o: newEdges.get(p)){
				edgesWithProv.addTriple(node, p, o);
			}
		}
	}
	protected void removeOutgoingEdges(Long node, Long2LongSet removedEdges){
		for (Long p: removedEdges.keys()){
			for (Long o: removedEdges.get(p)){
				edgesWithProv.removeTriple(node, p, o); 
			}
		}
	}
	public String getEdgesToString(){
		StringBuffer sb = new StringBuffer();
		for (Triple t: edgesWithProv.getSummaryEdges()){
			sb.append(t.toString() + " ");
		}
		return new String(sb); 
	}
	
}
