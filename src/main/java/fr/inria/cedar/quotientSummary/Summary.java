package fr.inria.cedar.quotientSummary;

import fr.inria.cedar.commons.miscellaneous.Debugger;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public class Summary {
	protected Long2Long rep; // representative function for untyped nodes
	protected boolean typeTriplesExist = false;
	// data, schema and type triples:
	// for each subject
	// for each property
	// the set of objects such that (subject, property, object) is in the
	// summary
	protected HashMap<Long, HashMap<Long, TreeSet<Long>>> edges;
	// for each summary node, the number of graph nodes it represents
	protected HashMap<Long, Long> summaryNodeStatistics;
	// for each summary edge, the number of graph edge it represents
	protected HashMap<Triple, Long> summaryEdgeStatistics;
	// these serve to represent the nodes that may have types but no
	// data property
	protected long typeOnlyNodeID;
	protected boolean typeOnlyNodeAlreadySeen;
	protected Triple lastReadTriple;
	protected long maxSummaryNode;
	protected Properties properties;
	protected static String SUMMARY_CONFIG_FILE = "conf/summarization.properties";
	// repTablePrefix must be instantiated with a specific string for each
	// summary type,
	// so that each summary is saved as separated Postgres tables
	protected String summaryTablePrefix;
	protected static String ROOT_SUMMARY_PREFIX = "";
	protected static String WEAK_SUMMARY_PREFIX = "w_";
	protected static String STRONG_SUMMARY_PREFIX = "s_";
	protected static String TYPED_WEAK_SUMMARY_PREFIX = "tw_";
	protected static String TYPED_STRONG_SUMMARY_PREFIX = "ts_";
	protected boolean checkConsistency = false;
	protected long triplesSummarizedSoFar = 0;
	protected DOTAuxiliary dax;
	// stats
	protected long summaryEdgesSavingTime;
	protected long representationFunctionSavingTime;
	protected long classSetCreationTime;
	protected long typeTriplesSummarizationTime;
	protected long dataTriplesSummarizationTime;
	protected long allTriplesSummarizationTime;

	public Summary() {
		rep = new Long2Long();
		edges = new HashMap<>();
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
		this.summaryTablePrefix = ROOT_SUMMARY_PREFIX;
		this.dax = new DOTAuxiliary();
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
		// System.out.println("String position " + spacePos + " out of: " +
		// spo.length());
		Long s = new Long(spo.substring(0, spacePos));
		spo = spo.substring(spacePos + 1, spo.length());
		// System.out.println("Read s: " + s + " spo is: #" + spo + "#");

		spacePos = spo.indexOf(' ');
		// System.out.println("String position " + spacePos + " out of: " +
		// spo.length());
		Long p = new Long(spo.substring(0, spacePos));
		// System.out.println("Read p: "+ p + " spo is: =" + spo + "=");
		spo = spo.substring(spacePos + 1, spo.length());

		Long o = new Long(spo);
		// System.out.println("Read o: " + o);
		lastReadTriple = new Triple(s, p, o);
		return lastReadTriple;
	}

	/**
	 * Adds an integer-encoded triple to the summary
	 *
	 * @param s
	 * @param p
	 * @param o
	 */
	protected void addTriple(Long s, Long p, Long o) {
		Triple t = new Triple(s, p, o);
		// Debugger.log("XX Trying to add triple " + t.toString());

		HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edges.get(s);
		if (triplesOfThisSubject == null) { // no edges yet for this subject;
			// otherwise, s has already some
			// edges
			triplesOfThisSubject = new HashMap<>();
			edges.put(s, triplesOfThisSubject);
			// Debugger.log("XX Created triple map for subject " +s);
		}
		TreeSet<Long> objectsOfThisSubjectAndProperty = triplesOfThisSubject.get(p);
		if (objectsOfThisSubjectAndProperty == null) { // no edges yet for this
			// subject and property;
			// otherwise, s has
			// already some p edges
			objectsOfThisSubjectAndProperty = new TreeSet<>();
			triplesOfThisSubject.put(t.p, objectsOfThisSubjectAndProperty);
		}
		if (!objectsOfThisSubjectAndProperty.contains(t.o)) // otherwise, s p o
			objectsOfThisSubjectAndProperty.add(o);
	}

	protected void checkTypeIsObject() {
		for (Long s : edges.keySet())
			for (Long p : edges.get(s).keySet())
				for (Long o : edges.get(s).get(p))
					if (o == RDF2SQLEncoding.getTypeCode())
						throw new Error("Found type in object position for " + s + " "
								+ RDF2SQLEncoding.dictionaryDecode(s) + " and " + RDF2SQLEncoding.dictionaryDecode(p));
	}

	// we need to be sure that integers which we invent to represent nodes
	// will not collide with the codes already given to classes and properties
	// (which, in this implementation, for simplicity, are preserved).
	protected void avoidCollisionsWhenAssigningSummaryNodes(Connection conn) {
		long maxClassOrPropertyCode = 0;
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		try {
			String jumpRepString = ("select max(o) from encoded_triples t1 where p=" + typeConstantCode);
			ResultSet rs = conn.createStatement().executeQuery(jumpRepString);
			while (rs.next()) {
				maxClassOrPropertyCode = rs.getLong(1);
				break;
			}
			rs.close();

		} catch (SQLException e) {
			throw new IllegalStateException(
					"Unable to determine the highest dictionary code for a type " + e.toString());
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

	// replaces in summary edges, not in rep
	protected void replaceNodeInSummaryEdges(Long oldNode, Long newNode) {
	//System.out.println("   SUMMARY.REPLACE IN EDGES NODE " + oldNode + " WITH " + newNode);
		//Debugger.log(this.toString());
		// replace oldNode wherever it existed as an object:
		for (long s : edges.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edges.get(s);
			for (long propOfThisSubject : triplesOfThisSubject.keySet()) {

				TreeSet<Long> objectsForThisSubjectAndProperty = triplesOfThisSubject.get(propOfThisSubject);
				TreeSet<Long> newObjectsForThisSubjectAndProperty = new TreeSet<Long>();
				boolean arrayChanged = false;
				for (long o : objectsForThisSubjectAndProperty)
					if (o == oldNode) {
						if (!newObjectsForThisSubjectAndProperty.contains(newNode))
							newObjectsForThisSubjectAndProperty.add(newNode);
						arrayChanged = true;
					} else
						newObjectsForThisSubjectAndProperty.add(o);

				if (arrayChanged)
					triplesOfThisSubject.replace(propOfThisSubject, newObjectsForThisSubjectAndProperty); // replace
				// is not a structural modification of the map, thus no concurrent modification exception
			}
		}
		// above we have replaced old with new wherever it appeared *** as an
		// object ***

		// now let's also do it for the subject:
		//System.out.println("   SUMMARY.REPLACE IN EDGES: After replacement as an object, we have: ");
		//System.out.println("   " + this.toString());
		//System.out.println("   SUMMARY.REPLACE IN EDGES: Now replacing as subject");

		HashMap<Long, TreeSet<Long>> oldNodeIsSubject = edges.get(oldNode);
		if (oldNodeIsSubject != null) { // in some edges, oldNode was subject
			//System.out.println("   SUMMARY.REPLACE IN EDGES: Removing edges whose subject is " + oldNode);
			edges.remove(oldNode); // detach this entry from edges (but keep
			// them in oldNodeIsSubject for now)

			HashMap<Long, TreeSet<Long>> newNodeIsSubject = edges.get(newNode);
			if (newNodeIsSubject == null) { // the new node was not previously a
				// subject of some edges
				//System.out.println("   SUMMARY.REPLACE IN EDGES: Adding on the new node " + newNode + " the triples of old node " + oldNode);
				edges.put(newNode, oldNodeIsSubject); // we're done
			} else // there were already edges whose subject was the new node
				if (oldNodeIsSubject != null) { // in this case we need to fuse the
					// two maps so that each edge
					// appears only once
					// we will do this by copying those oldNodeIsSubject triples
					// which were not already on the new node, into the properties
					// of the new node
					//System.out.println("   SUMMARY.REPLACE IN EDGES: There were edges both on old " + oldNode + " and on new " + newNode);

					for (Long oldNodeProperty : oldNodeIsSubject.keySet()) { // iterate
						// over the properties of the old node
						TreeSet<Long> oldNodeObjectsForThisProperty = oldNodeIsSubject.get(oldNodeProperty);
						TreeSet<Long> newNodeObjectsForThisProperty = newNodeIsSubject.get(oldNodeProperty);
						if (newNodeObjectsForThisProperty == null) { // the new node
							// did not have this one
							//System.out.println("   SUMMARY.REPLACE IN EDGES: " + newNode + " did not have edges labeled " + oldNodeProperty
							//		+ ", he is taking them from " + oldNode);
							newNodeObjectsForThisProperty = new TreeSet<Long>();
							newNodeIsSubject.put(oldNodeProperty, newNodeObjectsForThisProperty);
						}
						// whether the new node did or did not have triples labeled
						// oldNodeProperty, try to give him the triples labeled
						// oldNodeProperty of the old node:
						for (Long objectOfOldNode : oldNodeObjectsForThisProperty)
							if (!newNodeObjectsForThisProperty.contains(objectOfOldNode)) {
								//System.out.println("   SUMMARY.REPLACE IN EDGES: " +newNode + " takes property " + oldNodeProperty + " with value "
								//		+ objectOfOldNode + " from " + oldNode);
								newNodeObjectsForThisProperty.add(objectOfOldNode);
							} 
							else {
								//System.out.println("   SUMMARY.REPLACE IN EDGES: " +newNode + " already had property " + oldNodeProperty + " with value "	+ objectOfOldNode);
							}
					}
				}
		} else { // there was no edge with oldNode as a subject, no subject
			// replacement to do
		}
		//System.out.println("   SUMMARY.REPLACE IN EDGES ends");
		
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
			Statement nodeStatisticQuery = RDF2SQLEncoding.getConnection().createStatement();
			ResultSet rs = nodeStatisticQuery.executeQuery("select summarynode, count(*) from "
					+ this.getSummaryTablePrefix() + "encoded_rep group by summarynode;");
			while (rs.next()) {
				Long summaryNode = rs.getLong(1);
				Long numberOfReprGraphNodes = rs.getLong(2);
				this.summaryNodeStatistics.put(summaryNode, numberOfReprGraphNodes);
			}
			rs.close();
			nodeStatisticQuery.close();
		} catch (SQLException e) {
			throw new IllegalStateException(
					"Could not compute node representation statistics from Postgres " + e.toString());
		}
	}

	/**
	 * Computes edge statistics through a GROUP-BY query. Should be called after
	 * the summary is completely computed and stored in Postgres.
	 */
	private void gatherEdgeStatistics() {
		try {
			Statement edgeStatisticQuery = RDF2SQLEncoding.getConnection().createStatement();
			ResultSet rs = edgeStatisticQuery.executeQuery(
					"select es.s as summary_source, es.p as summary_prop, es.o as summary_target, count(*) " + "from "
							+ this.summaryTablePrefix + "encoded_rep rep1, " + this.summaryTablePrefix
							+ "encoded_rep rep2, encoded_triples t, " + this.summaryTablePrefix + "encoded_summary es "
							+ "where rep1.graphnode = t.s and rep2.graphnode=t.o and es.s = rep1.summarynode and es.o = rep2.summarynode and es.p = t.p "
							+ "group by es.s, es.p, es.o\n"); // + "order by
			// es.s, es.p,
			// es.o;");
			while (rs.next()) {
				Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
				this.summaryEdgeStatistics.put(t, rs.getLong(4));
			}
			rs.close();
			edgeStatisticQuery.close();
		} catch (SQLException e) {
			throw new IllegalStateException("Could not compute edge representation statistics from Postgres");
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
	 * @param rdfFileName
	 */
	public void saveSummaryInPostgres(Connection conn, String rdfFileName) {
		System.out.println("Saving " + this.getClass().getName() + " in Postgres");
		Statement stmt;
		try {
			long start = System.currentTimeMillis();
			conn.setAutoCommit(false);
			stmt = conn.createStatement();
			// create the table (it may have existed)
			if (!existsTable(conn, this.summaryTablePrefix + "encoded_rep"))
				stmt.execute("create table " + this.summaryTablePrefix
						+ "encoded_rep(graphNode int not null, summaryNode int not null); ");
			else
				Debugger.log("Did not create " + this.summaryTablePrefix + "encoded_rep table as it was already there");
			// empty it (even if the creation failed, e.g. because the table was
			// already there)
			stmt.executeUpdate("delete from " + this.summaryTablePrefix + "encoded_rep; ");

			// now insert all the rep entries:
			String insertIntoRep = "insert into " + this.summaryTablePrefix + "encoded_rep values(?, ?);";
			PreparedStatement insertInRep = conn.prepareStatement(insertIntoRep);
			Set<Long> origNodes = this.rep.getKeys();
			for (Long origNode : origNodes) {
				Long sumNode = this.rep.get(origNode);
				insertInRep.setLong(1, origNode);
				insertInRep.setLong(2, sumNode);
				insertInRep.executeUpdate();
			}
			// if (!hasIndex(conn, "encoded_rep")) {
			// stmt.executeUpdate("create index indRepS on
			// encoded_rep(graphNode); ");
			// } This gives some erros in the JDBC driver, perhaps it is not
			// implemented properly.
			conn.commit();
			representationFunctionSavingTime = System.currentTimeMillis() - start;
			System.out.println("Saved representation function in " + representationFunctionSavingTime + " ms");
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not insert summary triples in "
											+ this.summaryTablePrefix + "encoded_rep " + e.toString());
		}

		try {
			conn.setAutoCommit(false);
			long start = System.currentTimeMillis();
			if (!existsTable(conn, this.summaryTablePrefix + "encoded_summary"))
				stmt.execute("create table " + this.summaryTablePrefix
						+ "encoded_summary(s int not null, p int not null, o int not null); ");
			// empty it (even if the creation failed, e.g. because the table was
			// already there)
			stmt.executeUpdate("delete from " + this.summaryTablePrefix + "encoded_summary; ");

			// now insert all the summary edges:
			String insertIntoSummary = "insert into " + this.summaryTablePrefix + "encoded_summary values(?, ?, ?);";
			try (PreparedStatement insertInSummary = conn.prepareStatement(insertIntoSummary)) {
				ArrayList<Triple> edges = this.getSummaryEdges();
				for (Triple t : edges) {
					//System.out.println("Saving in Postgres edge: " + t.toString());
					insertInSummary.setLong(1, t.s);
					insertInSummary.setLong(2, t.p);
					insertInSummary.setLong(3, t.o);
					insertInSummary.executeUpdate();
				}
				// if (!hasIndex(conn, "encoded_summary")) {
				// stmt.executeUpdate("create index indSummaryS on
				// encoded_summary(s); ");
				// }
				conn.commit();
				insertInSummary.close();
				summaryEdgesSavingTime = System.currentTimeMillis() - start;
				System.out.println("Summary edges saved in " + summaryEdgesSavingTime + " ms");
				System.out.println("Summary saved in Postgres");
			}
		} catch (SQLException e) {
			throw new IllegalStateException("Could not insert summary triples in " + this.summaryTablePrefix
					+ "encoded_summary " + e.toString());
		}
	}

	static protected boolean existsTable(Connection conn, String tableName) {
		try {
			ResultSet res = conn.getMetaData().getTables(null, null, tableName, new String[] { "TABLE" });
			return res.next();
		} catch (SQLException e) {
			throw new IllegalStateException("Could not find out if table " + tableName + " exists " + e.toString());
		}
	}

	static protected boolean hasIndex(Connection conn, String tableName) {
		try {
			DatabaseMetaData meta = conn.getMetaData();
			ResultSet res = meta.getIndexInfo(null, null, tableName, true, true);
			return res.next();
		} catch (SQLException e) {
			throw new IllegalStateException("Could not find out if an index exists on " + tableName + e.toString());
		}
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres. It prints the
	 * summary to the standard output and also saves it in a separate .nt file
	 *
	 * @param con
	 * @param rdfFileName
	 */
	public void writeDecodedSummaryToNTFile(Connection con, String rdfFileName) {

		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");

		String summaryNTFileName = getNTSummaryFileName(rdfFileName);

		//System.out.println("Decoding summary and writing it in .nt format in " + summaryNTFileName);

		boolean gatherStatistics = properties.getProperty("gatherStatistics").toLowerCase().equals("true");
		if (gatherStatistics)
			this.gatherStatistics();
		ArrayList<Triple> summEdges = this.getSummaryEdges();
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryNTFileName)));
			// write summary triples:
			for (Triple t : summEdges) {
				// System.out.println("Summary triple: " + t.toString() );
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
				Debugger.log(subject + " " + property + " " + object);
				bw.write(subject + " " + property + " " + object + " .\n");
			}
			if (gatherStatistics) {
				// write node cardinality statistics:
				for (Long node : this.summaryNodeStatistics.keySet()) {
					Long numberOfRepresentedGraphNodes = this.summaryNodeStatistics.get(node);
					String subject = getSummaryNodeURI(URIprefix, node);
					String property = properties.getProperty("summaryNodeSupportURI");
					String object = ("\"" + numberOfRepresentedGraphNodes + "\"");
					Debugger.log(subject + " " + property + " " + object);
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
			bw.close();
		} catch (IOException e) {
			throw new IllegalStateException("Could not save the decoded summary in .nt file");
		}
		System.out.println("Summary decoded and saved in .nt format");
	}

	private String getCoreRDFFileName(String rdfFileName) {
		int lastDotPosition = Math.max(0, rdfFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, rdfFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + rdfFileName);
		return rdfFileName.substring(lastSlashPosition + 1, lastDotPosition + 3);
	}

	private String getNTSummaryFileName(String rdfFileName) {
		String coreRDFFileName = getCoreRDFFileName(rdfFileName);
		String summaryNTFileName = rdfFileName.replaceFirst(coreRDFFileName,
				(this.summaryTablePrefix + coreRDFFileName));
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

	public void drawSummaryAndGraph(Connection con, String fullRDFFileName) {
		this.drawSummaryAndGraph(con, fullRDFFileName, "");
	}

	protected void drawSummaryAndGraph(Connection con, String fullRDFFileName, String suffix) {
		String summaryDotFileName = getDotFileName(fullRDFFileName, suffix);
		writeSummaryToDotFile(con, summaryDotFileName);
		String graphDotFileName = getRDFDotFileName(fullRDFFileName, suffix);
		writeRDFGraphToDotFile(con, graphDotFileName);
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres
	 *
	 * @param con
	 * @param dotFileName
	 */
	protected void writeSummaryToDotFile(Connection con, String dotFileName) {
		dax.resetColors();
		Properties properties = new Properties();
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read config file");
		}
		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");
		// Debugger.log("writeSummaryToDotFile:");

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)));
			bw.write("digraph g{\n");

			ArrayList<Triple> summEdges = this.getSummaryEdges();
			for (Triple t : summEdges) {
				String subject, property, object, subjectInDot, propertyInDot, objectInDot;
				// in all cases, edge labels are preserved:
				property = getShortURIForDot(RDF2SQLEncoding.dictionaryDecode(t.p));
				propertyInDot = property.replaceAll("\"", "");

				if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
					subject = this.getSummaryNodeURI(URIprefix, t.s);
					subjectInDot = getShortURIForDot(subject).replaceAll("\"", "");
					if (dax.unknownSummaryNode(t.s))
						bw.write("\"" + subjectInDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(t.s)
						+ "];\n");
					object = this.getSummaryNodeURI(URIprefix, t.o);
					objectInDot = getShortURIForDot(object).replaceAll("\"", "");
					if (dax.unknownSummaryNode(t.o))
						bw.write("\"" + objectInDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(t.o)
						+ "];\n");
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
						bw.write("\"" + subjectInDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(t.s)
						+ "];\n");
					bw.write("\"" + objectInDot + "\" [fontcolor=white, style = filled, color=black];\n");
				}
				// write the triple in all cases:
				bw.write("\"" + subjectInDot + "\"" + " -> \"" + objectInDot + "\" [label=\"" + propertyInDot
						+ "\"];\n");

			}
			bw.write("}\n");
			bw.close();
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
		System.out.println("Summary written to DOT file " + dotFileName);

		String pathToDot = properties.getProperty("pathToDot");
		try {
			String pngFileName = dotFileName.substring(0, dotFileName.length() - 4) + ".png";
			Runtime.getRuntime().exec(pathToDot + " -Tpng " + dotFileName + " -o " + pngFileName);
			System.out.println("Summary drawn to PNG file " + pngFileName);
		} catch (IOException e) {
			System.out.println(
					"Could not turn .dot file into .png (check the pathToDot value in summarization.properties)"
							+ e.toString());
		}
	}

	/**
	 * Given a path to an .nt RDF data file, computes a file name by inserting
	 * the prefix encoding the summary type before the main file name, and
	 * replacing the trailing .nt with .dot
	 *
	 * It also inserts the suffix with a "-" before the ".".
	 *
	 * @param fullRDFFileName
	 * @param suffix
	 *
	 * @return
	 */
	private String getDotFileName(String fullRDFFileName, String suffix) {
		String coreRDFFileName = getCoreRDFFileName(fullRDFFileName);
		String dotFileName = fullRDFFileName.replaceFirst(coreRDFFileName, (this.summaryTablePrefix + coreRDFFileName));
		dotFileName = dotFileName.substring(0, dotFileName.length() - 3) + suffix + ".dot"; // replace
		// .nt
		// with
		// .dot
		return dotFileName;
	}

	/**
	 * Given a path to an .nt RDF data file, computes a file name by replacing
	 * the trailing .nt with .dot.
	 *
	 * It also adds the suffix just before the "."
	 *
	 * @param fullRDFFileName
	 * @param suffix
	 *
	 * @return
	 */
	private String getRDFDotFileName(String fullRDFFileName, String suffix) {
		return (fullRDFFileName.substring(0, fullRDFFileName.length() - 3)) + suffix + ".dot";
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

	public void writeRDFGraphToDotFile(Connection con, String dotFileName) {
		Properties properties = new Properties();
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read config file");
		}

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)));
			bw.write("digraph g{\n");
			long triplesToDraw = Math.min(25, triplesSummarizedSoFar);
			//System.out.println("Writing " + triplesToDraw + " RDF graph triples to DOT");
			ResultSet rs = getGraphTriplesCursor1ForDotDrawing(con, triplesToDraw);
			long triplesDrawn = drawTriples(rs, bw); 
			//Debugger.log("Drawn " + triplesDrawn);
			rs.close();
			if (triplesDrawn < triplesToDraw){
				ResultSet rs2 = getGraphTriplesCursor2ForDotDrawing(con, (triplesToDraw-triplesDrawn));
				//Debugger.log("Got 2nd cursor");
				drawTriples(rs2, bw);
				rs2.close();
			}
			bw.write("}\n");
			//System.out.println("Finished writing file");
			bw.close();
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		} catch (SQLException e) {
			throw new IllegalStateException("Unable to read and plot RDF triples: " + e.toString());

		}
		System.out.println("RDF graph written to DOT file " + dotFileName);

		String pathToDot = properties.getProperty("pathToDot");
		try {
			String pngFileName = dotFileName.substring(0, dotFileName.length() - 4) + ".png";
			Runtime.getRuntime().exec(pathToDot + " -Tpng " + dotFileName + " -o " + pngFileName);
			System.out.println("RDF graph drawn to PNG file " + pngFileName);
		} catch (IOException e) {
			System.out.println(
					"Could not turn .dot file into .png (check the pathToDot value in summarization.properties)"
							+ e.toString());
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
		try{
			while (rs.next()) {
				String subject = rs.getString(1);
				Long s = RDF2SQLEncoding.dictionaryEncode(subject);
				Long sRep = rep.get(s);

				String object = rs.getString(3);
				Long o = RDF2SQLEncoding.dictionaryEncode(object);
				Long oRep = rep.get(o);

				String property = rs.getString(2);
				Long p = RDF2SQLEncoding.dictionaryEncode(property);

				//System.out.println("Triple! (" + subject + " " + property + " " + object + ")");
				//System.out.println("Represented by: " + sRep + " " + p + " " + oRep);
				writeGraphTripleToDotFile(bw, s, p, o, subject, property, object, sRep, oRep);
				triplesDrawnInDot++;

			}
		}
		catch (SQLException e){
			throw new IllegalStateException("Could not get triple from cursor " + e.toString());
		}
		return triplesDrawnInDot; 
	}

	protected ResultSet getGraphTriplesCursor1ForDotDrawing(Connection conn, long limit) {
		throw new IllegalStateException("Not supposed to be called at this level"); 
	}
	protected ResultSet getGraphTriplesCursor2ForDotDrawing(Connection conn, long limit) {
		throw new IllegalStateException("Not supposed to be called at this level"); 
	}

	private void writeGraphTripleToDotFile(BufferedWriter bw, Long s, Long p, Long o, String subject, String property,
			String object, Long sRep, Long oRep) {
		String subjectForDot = getShortURIForDot(subject).replaceAll("\"", "");
		String objectForDot = getShortURIForDot(object).replaceAll("\"", "");
		String propertyForDot = getShortURIForDot(property).replaceAll("\"", "");

		try {
			if (RDF2SQLEncoding.isDataProperty(p)) {
				if (dax.unknownRDFNode(s))
					//System.out.println("Data-S " + s + " (" + subject + ") represented by  " + sRep + " colored "
					//		+ dax.getSummaryNodeColor(sRep));
				bw.write("\"" + subjectForDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(sRep) + "];\n");
				//if (dax.unknownRDFNode(o))
					//System.out.println("Data-O " + o + " (" + object + ")");
				//System.out.println("represented by " + oRep + " colored " + dax.getSummaryNodeColor(oRep));
				bw.write("\"" + objectForDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(oRep) + "];\n");
			} else if (RDF2SQLEncoding.isSchemaProperty(p)) {
				propertyForDot = getShortURIForDot(property).replaceAll("\"", "");
				//if (dax.unknownRDFNode(s))
					//System.out.println("SCH1 " + s + " (" + subject + ") represented by  " + sRep);
				bw.write("\"" + subjectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
				//if (dax.unknownRDFNode(o))
				//	System.out.println("SCH2 " + s + " (" + subject + ") represented by  " + sRep);
				bw.write("\"" + objectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
			} else { // type
				//Debugger.log("Type triple");
				//if (dax.unknownRDFNode(s))
				//	System.out.println("TYP1 " + s + " (" + subject + ") represented by  " + sRep);
				if (dax == null){
					throw new IllegalStateException("Null dax");
				}
				if (sRep == null){
					throw new IllegalStateException("Null sRep");
				}
				bw.write("\"" + subjectForDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(sRep) + "];\n");

				//if (dax.unknownRDFNode(o))
				//	System.out.println("TYP2 " + o + " (" + object + ") represented by  " + oRep);
				bw.write("\"" + objectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
			}
			bw.write(
					"\"" + subjectForDot + "\"" + " -> \"" + objectForDot + "\" [label=\"" + propertyForDot + "\"];\n");
			//System.out.println("Written a line in dot");
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
	}

	public ArrayList<Triple> getSummaryEdges() {
		ArrayList<Triple> res = new ArrayList<>();
		for (Long s : edges.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edges.get(s);
			for (Long p : triplesOfThisSubject.keySet()) {
				TreeSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				for (Long o : objectsOfThisSandP) {
					Triple t = new Triple(s, p, o);
					res.add(t);
				}
			}
		}
		return res;
	}

	public void display(String fullRDFFileName) {
		writeEncodedSummaryToFile(getNTSummaryFileName(fullRDFFileName));
		writeEncodedSummaryToDotFile(getDotFileName(fullRDFFileName, ""));
	}

	public void writeEncodedSummaryToFile(String fileName) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(fileName)));
			this.writeEncodedTripleToFile(bw);
			bw.close();
		} catch (IOException e) {
			throw new IllegalStateException(
					"Could not write encoded summary to file: " + fileName + ". Is the path correct?");
		}
	}

	private void writeEncodedTripleToFile(BufferedWriter bw) throws IOException {
		for (Triple t : getSummaryEdges())
			bw.write(t.toString() + "\n");
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (Triple t : getSummaryEdges()) {
			sb.append(t.toString());
			sb.append(" ");
		}
		return new String(sb);
	}

	public void writeEncodedSummaryToDotFile(String dotFile) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFile)));

			bw.write("digraph g{\n");
			for (Triple t : getSummaryEdges())
				bw.write(t.s + " -> " + t.o + " [label=\"" + t.p + "\"];\n");
			bw.write("}\n");
			bw.close();
		} catch (IOException e) {
			throw new IllegalStateException(
					"Could not write encoded summary to dot file: " + dotFile + ". Is the path correct?");
		}
	}

//	/**
//	 * Reads summary triples from an .nt file TODO the method is currently
//	 * insufficient as in the summary that has been read, the codes of special
//	 * properties are not known. Either fix by starting the serialization in a
//	 * file with the five magic constants, or don't use for now. Instead, use
//	 * readSummaryFromPostgres (below).
//	 *
//	 * @param args
//	 *
//	 * @return
//	 *
//	 * @throws IOException
//	 */
//	public static Summary readSummaryFromFile(String[] args) throws IOException {
//		Summary sum = new Summary();
//		String summaryTripleFileName = args[0];
//		//System.out.println("Trying to read an encoded summary from file:" + summaryTripleFileName);
//		try (BufferedReader br = new BufferedReader(new FileReader(new File(summaryTripleFileName)))) {
//			while (br.ready()) {
//				String spo = br.readLine().replaceAll("<", "").replaceAll(">", "");
//				Triple t = sum.readTriple(spo);
//				sum.addTriple(t.s, t.p, t.o);
//			}
//		}
//		System.out.println("Read encoded summary from file:" + summaryTripleFileName);
//		
//		return sum;
//	}

	public Summary(Connection conn) throws SQLException {
		// Debugger.log("Trying to read summary from Postgres");
		RDF2SQLEncoding.setUp(conn);
		// Debugger.log("Set up special URIs from dictionary");
		String getSummaryTriples = ("select *  from " + this.summaryTablePrefix + "encoded_summary");
		try (Statement getTriples = conn.createStatement();
				// Debugger.log("Created statement");
				ResultSet rs = getTriples.executeQuery(getSummaryTriples) // Debugger.log("Asking
						// for
						// summary
						// triples")
				) {
			while (rs.next()) {
				Long s = rs.getLong(1);
				Long p = rs.getLong(2);
				Long o = rs.getLong(3);
				this.addTriple(s, p, o);
			}
		}
		System.out.println("Read summary from Postgres");
	}

	public void summarizeFromRDBMS(Connection conn, String[] args) {
		throw new IllegalStateException("This method is not defined for " + this.getClass().getName());
	}

	protected String getSummaryTriplesSQLQuery() {
		return ("select *  from " + getSummaryTablePrefix() + "encoded_summary");
	}

	public String getEncodedRepSQLQuery() {
		return ("select summarynode from " + getSummaryTablePrefix() + "encoded_rep where graphnode=?");
	}

	public static Summary readSummaryFromPostgres(Connection conn) {
		Summary sum = new Summary();
		Debugger.log("Trying to read summary from Postgres");
		RDF2SQLEncoding.setUp(conn);
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
		stats.put("outputGraphSize", new Long(getSummaryEdges().size()));

		return stats;
	}
	protected void showClique(TreeSet<Long> clique) {
		System.out.println(showCliqueAsString(clique));
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
		return this.edges.get(s); 
	}
	protected HashMap<Long, TreeSet<Long>> getEdgesTo(Long o){
		HashMap<Long, TreeSet<Long>> res = new HashMap<Long, TreeSet<Long>>();
		for (Long s: edges.keySet()){
			for (Long p: edges.get(s).keySet()){
				// if there is an edge s--p-->o
				if (edges.get(s).get(p).equals(o)) {
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
				this.addTriple(s, p, node);
			}
		}
	}
	protected void removeIncomingEdges(Long node, Long2LongSet removedEdges){
		for (Long p: removedEdges.keys()){
			for (Long s: removedEdges.get(p)){
				this.removeTriple(s, p, node); 
			}
		}
	}
	protected void addOutgoingEdges(Long node, Long2LongSet newEdges){
		for (Long p: newEdges.keys()){
			for (Long o: newEdges.get(p)){
				this.addTriple(node, p, o);
			}
		}
	}
	protected void removeOutgoingEdges(Long node, Long2LongSet removedEdges){
		for (Long p: removedEdges.keys()){
			for (Long o: removedEdges.get(p)){
				this.removeTriple(node, p, o); 
			}
		}
	}
	protected void removeTriple(Long s, Long p, Long o){
		HashMap<Long, TreeSet<Long>> edgesOfS = edges.get(s); 
		if (edgesOfS != null){
			TreeSet<Long> pEdgesOfS = edgesOfS.get(p);
			if (pEdgesOfS != null){
				pEdgesOfS.remove(o); 
			}
		}
	}
	public String getEdgesToString(){
		StringBuffer sb = new StringBuffer();
		for (Triple t: this.getSummaryEdges()){
			sb.append(t.toString() + " ");
		}
		return new String(sb); 
	}
	
}
