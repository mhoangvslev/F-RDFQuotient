package fr.inria.cedar.quotientSummary;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
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

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

public class Summary {
	protected Long2Long rep; // representative function for untyped nodes

	protected boolean typeTriplesExist = false; 

	// for each subject
	//     for each property
	//         the set of objects such that (subject, property, object) is in the summary
	protected HashMap<Long, HashMap<Long, ArrayList<Long>>> edges; 
	// for each summary node, the number of graph nodes it represents
	protected HashMap<Long, Long> summaryNodeStatistics; 
	// for each summary edge, the number of graph edge it represents
	protected HashMap<Triple, Long> summaryEdgeStatistics; 

	protected boolean typeOnlyNodeAlreadySeen;
	protected long typeOnlyNodeID;

	protected Triple lastReadTriple; 

	protected long maxSummaryNode; 
	protected Properties properties; 
	protected static String SUMMARY_CONFIG_FILE="conf/summarization.properties"; 

	// repTablePrefix must be instantiated with a specific string for each summary type,
	// so that each summary is saved as separated Postgres tables
	protected String summaryTablePrefix; 

	protected static String ROOT_SUMMARY_PREFIX="";
	protected static String WEAK_SUMMARY_PREFIX="w_";
	protected static String STRONG_SUMMARY_PREFIX="s_";
	protected static String TYPED_WEAK_SUMMARY_PREFIX="tw_";
	protected static String TYPED_STRONG_SUMMARY_PREFIX="ts_";

	protected boolean checkConsistency = false; 
	
	public Summary(){
		rep = new Long2Long();
		edges = new HashMap<Long, HashMap<Long, ArrayList<Long>>>();
		summaryNodeStatistics = new HashMap<Long, Long>(); 
		summaryEdgeStatistics = new HashMap<Triple, Long>(); 
		typeOnlyNodeAlreadySeen = false;
		properties = new Properties();
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
			checkConsistency = properties.getProperty("consistencyChecks").toLowerCase().equals("true"); 
		} catch (IOException e) {
			throw new IllegalStateException("Unable to initialize summary properties"); 
		}
		this.summaryTablePrefix = ROOT_SUMMARY_PREFIX; 
	}
	/**
	 * Reads an integer-encoded triple out of a string (a line)
	 * @param spo
	 * @return
	 */
	protected  Triple readTriple(String spo){
		int spacePos = spo.indexOf(' '); 
		//System.out.println("String position " + spacePos + " out of: " + spo.length());
		Long s = new Long(spo.substring(0, spacePos)); 
		spo = spo.substring(spacePos+1, spo.length());
		//System.out.println("Read s: " + s + " spo is: #" + spo + "#");

		spacePos = spo.indexOf(' '); 
		//System.out.println("String position " + spacePos + " out of: " + spo.length());
		Long p = new Long(spo.substring(0, spacePos)); 
		//System.out.println("Read p: "+ p + " spo is: =" + spo + "=");
		spo = spo.substring(spacePos+1, spo.length()); 

		Long o = new Long(spo);
		//System.out.println("Read o: " + o);
		lastReadTriple = new Triple(s, p, o); 
		return lastReadTriple; 
	}

	/**
	 * Adds an integer-encoded triple to the summary 
	 * @param s
	 * @param p
	 * @param o
	 */
	protected void addTriple(Long s, Long p, Long o) {
		Triple t = new Triple(s, p, o);
		//Debugger.log("XX Trying to add triple " + t.toString());

		HashMap<Long, ArrayList<Long>> triplesOfThisSubject = edges.get(s);
		if (triplesOfThisSubject == null){ // no edges yet for this subject; otherwise, s has already some edges
			triplesOfThisSubject = new HashMap<>();
			edges.put(s, triplesOfThisSubject);
			//Debugger.log("XX Created triple map for subject " +s); 
		}
		ArrayList<Long> objectsOfThisSubjectAndProperty = triplesOfThisSubject.get(p); 
		if (objectsOfThisSubjectAndProperty == null){ // no edges yet for this subject and property; otherwise, s has already some p edges
			objectsOfThisSubjectAndProperty = new ArrayList<>();
			triplesOfThisSubject.put(t.p, objectsOfThisSubjectAndProperty); 
			//Debugger.log("XX Created array list for subject " + s + " and property " + p);
		}
		if (!objectsOfThisSubjectAndProperty.contains(t.o)){ // otherwise, s p o is already there
			//Debugger.log("XX " + o + " was not a known value for " + p + " of " + s + " in " + this.toString()); 
			objectsOfThisSubjectAndProperty.add(o); 
		}
	}

	protected void checkTypeIsObject() {
		for (Long s: edges.keySet()) {
			for (Long p: edges.get(s).keySet()) {
				for (Long o: edges.get(s).get(p)) {
					if (o == RDF2SQLEncoding.getTypeCode()) {
						throw new Error("Found type in object position for " + s +
								" " + RDF2SQLEncoding.dictionaryDecode(s) + 
								" and " +
								RDF2SQLEncoding.dictionaryDecode(p)); 
					}
				}
			}
		}
	}

	protected boolean isDataProperty(Long p) {
		long n = RDF2SQLEncoding.getTypeCode(); 
		if (n != -1) {
			if (p.equals(n)) {
				return false; 
			}
		}
		n = RDF2SQLEncoding.getSubClassCode(); 
		if (n != -1) {
			if (p.equals(n)) {
				return false; 
			}
		}
		n = RDF2SQLEncoding.getSubPropertyCode(); 
		if (n != -1) {
			if (p.equals(n)) {
				return false; 
			}
		}
		n = RDF2SQLEncoding.getDomainCode(); 
		if (n != -1) {
			if (p.equals(n)) {
				return false; 
			}
		}
		n = RDF2SQLEncoding.getRangeCode(); 
		if (n != -1) {
			if (p.equals(n)) {
				return false; 
			}
		}
		//Debugger.log(p + " isDataProperty, here are the standard URI codes: " + typeConstantCode + " " + subClassCode + " " + subPropertyCode + 
		//		" " + domainCode + " " + rangeCode);
		return true; 
	}

	protected void showRepInBuffer(StringBuffer sb) {
		sb.append("|| rep:  ");
		for (Long node: this.rep.getNodes()){
			sb.append(node + "=>" + rep.get(node) + " ");
			assert(rep.get(node) != null); 
		}	
	}
	protected void showRep() {
		StringBuffer sb = new StringBuffer();
		showRepInBuffer(sb); 
		System.out.println(sb.toString());
	}
	protected Long getNextSummaryNode(){
		Long node = new Long(this.maxSummaryNode);
		this.maxSummaryNode++;
		return node; 
	}

	// replaces in summary edges, not in rep
	protected void replaceInSummary(Long oldNode, Long newNode) {
		if (newNode == null){
			throw new Error("Null new node");
		}
		if (oldNode.equals(newNode)){
			throw new Error("Won't replace equal nodes"); 
		}
		Debugger.log("REPLACING " + oldNode + " with " + newNode + " in: ");
		Debugger.log(this.toString());
		// replace oldNode wherever it existed as an object: 
		for (long s: edges.keySet()){

			HashMap<Long, ArrayList<Long>> triplesOfThisSubject = edges.get(s); 

			for (long propOfThisSubject: triplesOfThisSubject.keySet()){

				ArrayList<Long> objectsForThisSubjectAndProperty = triplesOfThisSubject.get(propOfThisSubject); 
				ArrayList<Long> newObjectsForThisSubjectAndProperty = new ArrayList<>(); 
				boolean arrayChanged = false; 
				for (long o: objectsForThisSubjectAndProperty){
					if (o == oldNode.longValue()){
						if (!newObjectsForThisSubjectAndProperty.contains(newNode)){
							newObjectsForThisSubjectAndProperty.add(newNode);
						}
						arrayChanged = true; 
					}
					else{
						newObjectsForThisSubjectAndProperty.add(o);
					}
				}

				if (arrayChanged){
					triplesOfThisSubject.replace(propOfThisSubject, newObjectsForThisSubjectAndProperty); // replace is not a structural modification of the map, thus no
					// concurrent modification exception
				}
			}
		}
		// above we have replaced old with new wherever it appeared *** as an object *** 

		// now let's also do it for the subject:
		Debugger.log("After replacement as an object, we have: ");
		Debugger.log(this.toString());
		Debugger.log("Now replacing as subject");

		HashMap<Long, ArrayList<Long>> oldNodeIsSubject = edges.get(oldNode);
		if (oldNodeIsSubject != null){ // in some edges, oldNode was subject
			Debugger.log("Removing edges whose subject is " + oldNode);
			edges.remove(oldNode); // detach this entry from edges (but keep them in oldNodeIsSubject for now)

			HashMap<Long, ArrayList<Long>> newNodeIsSubject = edges.get(newNode);
			if (newNodeIsSubject == null){ // the new node was not previously a subject of some edges
				Debugger.log("Adding on the new node " + newNode + " the triples of old node " + oldNode);
				edges.put(newNode, oldNodeIsSubject);  // we're done
			}
			else{ // there were already edges whose subject was the new node
				if (oldNodeIsSubject != null){ // in this case we need to fuse the two maps so that each edge appears only once
					// we will do this by copying those oldNodeIsSubject triples which were not already on the new node, into the properties of the new node
					Debugger.log("There were edges both on old " + oldNode + " and on new " + newNode); 

					for (Long oldNodeProperty: oldNodeIsSubject.keySet()){ // iterate over the properties of the old node 
						ArrayList<Long> oldNodeObjectsForThisProperty = oldNodeIsSubject.get(oldNodeProperty); 
						ArrayList<Long> newNodeObjectsForThisProperty = newNodeIsSubject.get(oldNodeProperty); 
						if (newNodeObjectsForThisProperty == null){ // the new node did not have this one
							Debugger.log(newNode + " did not have edges labeled " + oldNodeProperty + ", he is taking them from " + oldNode);
							newNodeObjectsForThisProperty = new ArrayList<>();
							newNodeIsSubject.put(oldNodeProperty, newNodeObjectsForThisProperty);
						}
						// whether the new node did or did not have triples labeled oldNodeProperty, try to give him the triples labeled oldNodeProperty of the old node: 
						for (Long objectOfOldNode: oldNodeObjectsForThisProperty){
							if (!newNodeObjectsForThisProperty.contains(objectOfOldNode)){
								Debugger.log(newNode + " takes property " + oldNodeProperty + " with value "+ objectOfOldNode + " from " + oldNode); 
								newNodeObjectsForThisProperty.add(objectOfOldNode); 
							}
							else{
								Debugger.log(newNode + " already had property " + oldNodeProperty + " with value "+ objectOfOldNode); 
							}
						}
					}
				}
			}
		}
		else{ // there was no edge with oldNode as a subject, no subject replacement to do
		}
	}

	protected void gatherStatistics() {
		gatherNodeStatistics(); 
		gatherEdgeStatistics(); 
	}

	/**
	 * Computes node statistics through a GROUP-BY query. Should be called after the summary is completely computed and stored in Postgres.
	 */
	private void gatherNodeStatistics() {
		try{
			Statement nodeStatisticQuery = RDF2SQLEncoding.getConnection().createStatement(); 
			ResultSet rs = nodeStatisticQuery.executeQuery("select summarynode, count(*) from " + 
					this.getSummaryTablePrefix() + "encoded_rep group by summarynode;"); 
			while (rs.next()) {
				Long summaryNode = rs.getLong(1);
				Long numberOfReprGraphNodes = rs.getLong(2); 
				this.summaryNodeStatistics.put(summaryNode, numberOfReprGraphNodes); 
			}
			rs.close(); 
			nodeStatisticQuery.close();
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not compute node representation statistics from Postgres " + e.toString()); 
		}
	}
	/**
	 * Computes edge statistics through a GROUP-BY query. Should be called after the summary is completely computed and stored in Postgres. 
	 */
	private void gatherEdgeStatistics() {
		try {
			Statement edgeStatisticQuery = RDF2SQLEncoding.getConnection().createStatement(); 
			ResultSet rs = edgeStatisticQuery.executeQuery(
					"select es.s as summary_source, es.p as summary_prop, es.o as summary_target, count(*) " +
							"from " + 
							this.summaryTablePrefix + "encoded_rep rep1, " +
							this.summaryTablePrefix + "encoded_rep rep2, encoded_triples t, " +
							this.summaryTablePrefix + "encoded_summary es " +							
							"where rep1.graphnode = t.s and rep2.graphnode=t.o and es.s = rep1.summarynode and es.o = rep2.summarynode and es.p = t.p " +
					"group by es.s, es.p, es.o\n"); // + 	"order by es.s, es.p, es.o;");  
			while (rs.next()) {
				Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3)); 
				this.summaryEdgeStatistics.put(t,  rs.getLong(4)); 
			}
			rs.close();
			edgeStatisticQuery.close();
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not compute edge representation statistics from Postgres"); 
		}
	}

	// inserts a schema triple directly in edges, with no fusion or other replacements
	protected void copySchemaTriple(Long s, Long p, Long o) {
		HashMap<Long, ArrayList<Long>> schemasForThisS = edges.get(s);
		if (schemasForThisS == null) {
			schemasForThisS = new HashMap<Long, ArrayList<Long>> (); 
			edges.put(s, schemasForThisS); 
		}
		ArrayList<Long> objectsForThisSAndP = schemasForThisS.get(p);
		if (objectsForThisSAndP == null) {
			objectsForThisSAndP = new ArrayList<Long>();
		}
		if (!objectsForThisSAndP.contains(o)){
			objectsForThisSAndP.add(o); 
		}
	}


	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("Not implemented at this level"); 
	}

	/**
	 * Saves a summary as two Postgres tables: one is rep (the representation function)
	 * the other one is the set of summary edges, encoded as integers.
	 * The property and class URIs here are encoded exactly like the input.
	 * The subject and objects in the summary edges are just "new integer codes".
	 * 
	 * @param conn
	 * @param rdfFileName
	 * @throws SQLException
	 */
	public void saveSummaryInPostgres(Connection conn, String rdfFileName) {
		System.out.println("Saving " + this.getClass().getName() + " in Postgres...");
		Statement stmt; 
		try {
			long start = System.currentTimeMillis(); 
			conn.setAutoCommit(false);
			stmt = conn.createStatement();
			// create the table (it may have existed)
			if (!existsTable(conn, this.summaryTablePrefix + "encoded_rep")) {			
				stmt.execute("create table " + this.summaryTablePrefix + "encoded_rep(graphNode int not null, summaryNode int not null); ");
			} else {
				Debugger.log("Did not create " + this.summaryTablePrefix + "encoded_rep table as it was already there");
			}
			// empty it (even if the creation failed, e.g. because the table was already there)
			stmt.executeUpdate("delete from " + this.summaryTablePrefix + "encoded_rep; "); 

			// now insert all the rep entries:
			String insertIntoRep = "insert into " + this.summaryTablePrefix + "encoded_rep values(?, ?);"; 
			PreparedStatement insertInRep = conn.prepareStatement(insertIntoRep); 
			Set<Long> origNodes = this.rep.getNodes(); 
			for (Long origNode: origNodes) {
				Long sumNode = this.rep.get(origNode); 
				insertInRep.setLong(1, origNode);
				insertInRep.setLong(2, sumNode);
				insertInRep.executeUpdate(); 
			}
			//		if (!hasIndex(conn, "encoded_rep")) {
			//			stmt.executeUpdate("create index indRepS on encoded_rep(graphNode); ");
			//		} This gives some erros in the JDBC driver, perhaps it is not implemented properly.
			conn.commit();
			System.out.println("Saved representation function in " + (System.currentTimeMillis() - start) + " ms.");
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not insert summary triples in " + 
					this.summaryTablePrefix + "encoded_rep " + e.toString()); 
		}

		try {
			conn.setAutoCommit(false);
			long start = System.currentTimeMillis(); 
			if (!existsTable(conn, this.summaryTablePrefix + "encoded_summary")) {			
				stmt.execute("create table " + this.summaryTablePrefix + "encoded_summary(s int not null, p int not null, o int not null); ");
			}
			// empty it (even if the creation failed, e.g. because the table was already there)
			stmt.executeUpdate("delete from " + this.summaryTablePrefix +  "encoded_summary; "); 

			// now insert all the summary edges:
			String insertIntoSummary = "insert into " + this.summaryTablePrefix + "encoded_summary values(?, ?, ?);"; 
			try (PreparedStatement insertInSummary= conn.prepareStatement(insertIntoSummary)) {
				ArrayList<Triple> edges = this.getSummaryEdges(); 
				for (Triple t: edges) {
					insertInSummary.setLong(1, t.s);
					insertInSummary.setLong(2, t.p);
					insertInSummary.setLong(3, t.o);
					insertInSummary.executeUpdate(); 
				}
				//			if (!hasIndex(conn, "encoded_summary")) {
				//				stmt.executeUpdate("create index indSummaryS on encoded_summary(s); ");
				//			}
				conn.commit();
				insertInSummary.close();
				System.out.println("Summary edges saved in " + (System.currentTimeMillis() - start) + " ms."); 
				System.out.println("Summary saved in Postgres.");
			}
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not insert summary triples in " 
					+ this.summaryTablePrefix + "encoded_summary " + e.toString()); 
		}
	}

	static protected boolean existsTable(Connection conn, String tableName) {
		try {	
			ResultSet res = conn.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"}) ;
			return res.next();  
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not find out if table " + tableName + " exists " + e.toString()); 
		}
	}
	static protected boolean hasIndex(Connection conn, String tableName) {
		try{
			DatabaseMetaData meta = conn.getMetaData();
			ResultSet res = meta.getIndexInfo(null, null, tableName, true, true); 
			return res.next();  
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not find out if an index exists on " + tableName + e.toString()); 
		}
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs or strings) 
	 * based on a dictionary table in Postgres. It prints the summary to the standard output
	 * and also saves it in a separate .nt file
	 * @param con
	 * @throws SQLException
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public void writeDecodedSummaryToNTFile(Connection con, String rdfFileName) {

		String URIprefix = properties.getProperty("prefixURIForSummaryNodes"); 

		String summaryNTFileName = getNTSummaryFileName(rdfFileName); 

		System.out.println("Decoding summary and writing it in .nt format in " + summaryNTFileName + "..."); 

		boolean gatherStatistics = properties.getProperty("gatherStatistics").toLowerCase().equals("true");
		if (gatherStatistics) {
			this.gatherStatistics();
		}
		ArrayList<Triple> summEdges = this.getSummaryEdges(); 
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter (new File(summaryNTFileName))); 
			// write summary triples: 
			for (Triple t: summEdges){
				String subject = getSummaryNodeURI(URIprefix, t.s); 
				String property = RDF2SQLEncoding.dictionaryDecode(t.p);
				String object; 
				if (isDataProperty(t.p)) { // if data property, invent/retrieve an URI for the object
					object =  getSummaryNodeURI(URIprefix, t.o);
				}
				else { // otherwise, the object is a class or property: use the original URI
					object =  RDF2SQLEncoding.dictionaryDecode(t.o); 
				}   
				Debugger.log(subject + " " + property + " " + object);
				bw.write(subject + " " + property + " " + object + " . \n");
			}
			if (gatherStatistics) {
				// write node cardinality statistics: 
				for (Long node: this.summaryNodeStatistics.keySet()) {
					Long numberOfRepresentedGraphNodes = this.summaryNodeStatistics.get(node); 
					String subject = getSummaryNodeURI(URIprefix, node); 
					String property = properties.getProperty("summaryNodeSupportURI");
					String object = ("\"" + numberOfRepresentedGraphNodes + "\""); 
					Debugger.log(subject + " " + property + " " + object);
					bw.write(subject + " <" + property + "> " + object + " . \n");
				}
				// write edge cardinality statistics: 
				int reifiedEdgeNumber = 0; 
				for (Triple ts: this.summaryEdgeStatistics.keySet()) {
					Long numberOfRepresentedEdges = this.summaryEdgeStatistics.get(ts); 
					String reifEdgeURI = getSummaryNodeURI(properties.getProperty("reifiedSummaryEdgeURIPrefix"), reifiedEdgeNumber);
					bw.write(reifEdgeURI + " <" + properties.getProperty("reifiedEdgeHasSubject") + "> " + getSummaryNodeURI(URIprefix, ts.s) + " . \n");
					bw.write(reifEdgeURI + " <" + properties.getProperty("reifiedEdgeHasProperty") + "> <" + RDF2SQLEncoding.dictionaryDecode(ts.p) + "> . \n") ;
					bw.write(reifEdgeURI + " <" + properties.getProperty("reifiedEdgeHasObject") + "> " + getSummaryNodeURI(URIprefix, ts.o) + " . \n");
					bw.write(reifEdgeURI + " <" + properties.getProperty("summaryEdgeSupportURI") + "> \"" + numberOfRepresentedEdges + "\" . \n"); 
					reifiedEdgeNumber ++; 
				}
			}
			bw.close(); 
		}
		catch(IOException e) {
			throw new IllegalStateException("Could not save the decoded summary in .nt file"); 
		}
		System.out.println("Summary decoded and saved in .nt format");
	}

	private String getCoreRDFFileName(String rdfFileName) {
		int lastDotPosition = Math.max(0, rdfFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, rdfFileName.lastIndexOf("/")); 
		if (lastDotPosition - lastSlashPosition < 1) {
			throw new IllegalStateException("Was not able to extract a core component of the file name " 
					+ rdfFileName); 
		}
		return rdfFileName.substring(lastSlashPosition+1,  lastDotPosition); 
	}
	private String getNTSummaryFileName(String rdfFileName) {
		String coreRDFFileName = getCoreRDFFileName(rdfFileName);
		String summaryNTFileName = rdfFileName.replaceFirst(coreRDFFileName, 
				(this.summaryTablePrefix+coreRDFFileName)); 
		return summaryNTFileName; 
	}

	/**
	 * This is the inventor of summary node URIs.
	 * 
	 * @param uriPrefix
	 * @param n
	 * @return
	 */
	private String getSummaryNodeURI(String uriPrefix, long n) {
		return ("<" + uriPrefix + n + ">");
	}
	/**
	 * This decodes the summary (replaces property codes with the original URIs or strings) 
	 * based on a dictionary table in Postgres
	 * @param con
	 * @throws SQLException
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public void writeSummaryToDotFile(Connection con, String fullRDFFileName) {
		String dotFileName = getDotFileName(fullRDFFileName); 

		Properties properties = new Properties();	
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read config file"); 
		}
		String URIprefix = properties.getProperty("prefixURIForSummaryNodes"); 
		//Debugger.log("writeSummaryToDotFile:");

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter (new File(dotFileName))); 
			bw.write("digraph g{\n");

			ArrayList<Triple> summEdges = this.getSummaryEdges(); 
			for (Triple t: summEdges){
				String subject = URIprefix + t.s; 
				String object =  URIprefix + t.o; 
				String property = getShortURIForDot(RDF2SQLEncoding.dictionaryDecode(t.p)); 
				if (t.p == RDF2SQLEncoding.getTypeCode()) {
					// if this is a type triple, decode the object, too: concretely, this changes the object string
					object = getShortURIForDot(RDF2SQLEncoding.dictionaryDecode(t.o)); 
					property = "rdf:type"; 
					bw.write("\"" + object.replaceAll("\"", "") + "\" [style = filled, color=darkseagreen];\n");  
					bw.write("\"" + subject.replaceAll("\"", "") + "\"" + " -> \""+ 
							object.replaceAll("\"", "") + 
							"\" [color=darkseagreen, label=\"" +  property.replaceAll("\"", "")+ "\"];\n");
				}
				else{// in all cases, print the edge: 
					//System.out.println(subject + " " + property + " " + object);
					bw.write("\"" + getShortURIForDot(subject).replaceAll("\"", "") + "\"" + " -> \""+ 
							getShortURIForDot(object).replaceAll("\"", "") + 
							"\" [label=\"" +  getShortURIForDot(property).replaceAll("\"", "")+ "\"];\n");
				}
			}
			bw.write("}\n"); 
			bw.close(); 
		}
		catch(IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString()); 
		}
		System.out.println("Summary written to DOT file " + dotFileName + "."); 
			
		String pathToDot = properties.getProperty("pathToDot"); 
		try {
			String pngFileName = dotFileName.substring(0, dotFileName.length() - 4) + ".png"; 
			Runtime.getRuntime().exec(pathToDot + " -Tpng " + dotFileName + " -o " + 
					pngFileName);
			System.out.println("Summary drawn to PNG file " + pngFileName + "."); 
		} catch (IOException e) {
			System.out.println("Could not turn .dot file into .png (check the pathToDot value in summarization.properties)" + e.toString());
		} 
	}

	/** 
	 * Given a path to an .nt RDF data file, computes a file name by inserting the
	 * prefix encoding the summary type before the main file name, and replacing 
	 * the trailing .nt with .dot
	 *  
	 * @param fullRDFFileName
	 * @return
	 */
	private String getDotFileName(String fullRDFFileName) {
		String coreRDFFileName = getCoreRDFFileName(fullRDFFileName); 
		String dotFileName = fullRDFFileName.replaceFirst(coreRDFFileName, 
				(this.summaryTablePrefix+coreRDFFileName));
		dotFileName = dotFileName.substring(0, dotFileName.length() - 3) + ".dot"; // replace .nt with .dot
		return dotFileName; 
	}

	/** 
	 * Given a path to an .nt RDF data file, computes a file name by  replacing 
	 * the trailing .nt with .dot
	 *  
	 * @param fullRDFFileName
	 * @return
	 */
	private String getRDFDotFileName(String fullRDFFileName) {
		return (fullRDFFileName.substring(0, fullRDFFileName.length() - 3)) + ".dot"; 
	}
	/**
	 * URIs can be too long, thus they may need to be shortened in a .dot file.
	 * @param URI
	 * @return
	 */
	private String getShortURIForDot(String URI) {
		int maxNodeLabelLength = Integer.parseInt(properties.getProperty("maxNodeLabelLength")); 
		if (URI.length() < maxNodeLabelLength) {
			return URI; 
		}
		else {
			return "..." + URI.substring(URI.length() - (maxNodeLabelLength-4), URI.length()); 
		}	
	}

	public void writeRDFGraphToDotFile(Connection con, String fullRDFFileName) {
		String dotFileName = getRDFDotFileName(fullRDFFileName); 

		Properties properties = new Properties();	
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read config file"); 
		}

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter (new File(dotFileName))); 
			bw.write("digraph g{\n");
			ResultSet rs = con.createStatement().executeQuery("select * from triples limit 25"); 
			while (rs.next()) {
				String subject = rs.getString(1); 
				String object =  rs.getString(3); 
				String property = rs.getString(2);
				if (property.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")){ 
					// if this is a type triple, decode the object, too: concretely, this changes the object string
					object = getShortURIForDot(object); 
					property = "rdf:type"; 
					bw.write("\"" + object.replaceAll("\"", "") + "\" [style = filled, color=darkseagreen];\n");  
					bw.write("\"" + subject.replaceAll("\"", "") + "\"" + " -> \""+ 
							object.replaceAll("\"", "") + 
							"\" [color=darkseagreen, label=\"" +  property.replaceAll("\"", "")+ "\"];\n");
				}
				else{// in all cases, print the edge: 
					//System.out.println(subject + " " + property + " " + object);
					bw.write("\"" + getShortURIForDot(subject).replaceAll("\"", "") + "\"" + " -> \""+ 
							getShortURIForDot(object).replaceAll("\"", "") + 
							"\" [label=\"" +  getShortURIForDot(property).replaceAll("\"", "")+ "\"];\n");
				}
			}
			bw.write("}\n"); 
			bw.close(); 
		}
		catch(IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString()); 
		} catch (SQLException e) {
			throw new IllegalStateException("Unable to read and plot RDF triples: " + e.toString()); 

		}
		System.out.println("RDF graph written to DOT file " + dotFileName + "."); 
			
		String pathToDot = properties.getProperty("pathToDot"); 
		try {
			String pngFileName = dotFileName.substring(0, dotFileName.length() - 4) + ".png"; 
			Runtime.getRuntime().exec(pathToDot + " -Tpng " + dotFileName + " -o " + 
					pngFileName);
			System.out.println("RDF graph drawn to PNG file " + pngFileName + "."); 
		} catch (IOException e) {
			System.out.println("Could not turn .dot file into .png (check the pathToDot value in summarization.properties)" + e.toString());
		} 
	}
	
	public ArrayList<Triple> getSummaryEdges() {
		ArrayList<Triple> res = new ArrayList<>();
		for (Long s: edges.keySet()){
			HashMap<Long, ArrayList<Long>> triplesOfThisSubject = edges.get(s); 
			if (triplesOfThisSubject == null){
				throw new Error("No triples whose subject is " + s); 
			}
			for (Long p: triplesOfThisSubject.keySet()){
				ArrayList<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				for (Long o: objectsOfThisSandP){
					Triple t = new Triple(s, p, o);
					res.add(t);
				}
			}
		}
		return res; 
	}

	public void display(String fullRDFFileName){
		writeEncodedSummaryToFile(getNTSummaryFileName(fullRDFFileName));
		writeEncodedSummaryToDotFile(getDotFileName(fullRDFFileName));  
	}

	public void writeEncodedSummaryToFile(String fileName){
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter (new File(fileName))); 
			this.writeEncodedTripleToFile(bw);
			bw.close();
		}
		catch(IOException e) {
			throw new IllegalStateException("Could not write encoded summary to file: " + fileName + ". Is the path correct?"); 
		}
	}


	private void writeEncodedTripleToFile(BufferedWriter bw) throws IOException{
		for (Triple t: getSummaryEdges()){
			bw.write(t.toString() + "\n"); 
		}
	}

	@Override
	public String toString(){
		StringBuffer sb = new StringBuffer();
		for (Triple t: getSummaryEdges()){
			sb.append(t.toString());
			sb.append("\n");
		}
		return new String(sb); 
	}

	public void writeEncodedSummaryToDotFile(String dotFile) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter (new File(dotFile))); 

			bw.write("digraph g{\n");
			for (Triple t: getSummaryEdges()){
				bw.write(t.s + " -> "+ t.o + " [label=\"" + t.p + "\"];\n");
			}
			bw.write("}\n"); 
			bw.close(); 
		}
		catch(IOException e) {
			throw new IllegalStateException("Could not write encoded summary to dot file: " + dotFile + ". Is the path correct?"); 
		}
	}

	/** Reads summary triples from an .nt file 
	 *  TODO the method is currently insufficient as in the summary that has been read, the codes of special properties are not known.
	 *  Either fix by starting the serialization in a file with the five magic constants, or don't use for now.
	 *  Instead, use readSummaryFromPostgres (below).
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static Summary readSummaryFromFile(String[] args) throws IOException {
		Summary sum = new Summary(); 
		String summaryTripleFileName = args[0]; 
		System.out.println("Trying to read an encoded summary from file:" + summaryTripleFileName);
		try (BufferedReader br = new BufferedReader(new FileReader(new File(summaryTripleFileName)))) {
			while (br.ready()){
				String spo = br.readLine().replaceAll("<", "").replaceAll(">", ""); 
				Triple t = sum.readTriple(spo);
				sum.addTriple(t.s, t.p, t.o);
			}
		}
		return sum; 
	}

	public  Summary (Connection conn) throws SQLException {
		//Debugger.log("Trying to read summary from Postgres");
		RDF2SQLEncoding.setUp(conn); 
		//Debugger.log("Set up special URIs from dictionary"); 
		String getSummaryTriples = ("select *  from " + this.summaryTablePrefix + "encoded_summary"); 
		try(Statement getTriples = conn.createStatement(); 
				// Debugger.log("Created statement");
				ResultSet rs = getTriples.executeQuery(getSummaryTriples)
						// Debugger.log("Asking for summary triples")
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

	public static Summary readSummaryFromPostgres(Connection conn) {
		Summary sum = new Summary(); 
		Debugger.log("Trying to read summary from Postgres");
		RDF2SQLEncoding.setUp(conn); 
		Debugger.log("Set up special URIs from dictionary"); 
		String getSummaryTriples = ("select *  from encoded_summary"); 
		try{
			Statement getTriples = conn.createStatement(); 
			// Debugger.log("Created statement");
			ResultSet rs = getTriples.executeQuery(getSummaryTriples); 
			// Debugger.log("Asking for summary triples")
			while (rs.next()) {
				Long s = rs.getLong(1);
				Long p = rs.getLong(2); 
				Long o = rs.getLong(3);
				sum.addTriple(s, p, o);
			}
		}
		catch(SQLException e) {
			throw new IllegalStateException("Unable to read summary from Postgres"); 
		}
		System.out.println("Read summary from Postgres"); 
		return sum; 
	}

	/**
	 * This method is needed by specialization classes when they are read from Postgres.
	 * They need to 
	 * @return
	 */
	public HashMap<Long, HashMap<Long, ArrayList<Long>>> getEdgesAsInternallyStored() {
		return this.edges; 
	}

	public String getSummaryTablePrefix() {
		return this.summaryTablePrefix; 
	}

}
