package fr.inria.cedar.quotientSummary.oldsummaries;

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

public class Summarization {
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

	protected long maxSummaryNode; 

	protected boolean typeOnlyNodeAlreadySeen;
	protected long typeOnlyNodeID;

	protected Triple lastReadTriple; 
	protected long numberOfDataTriplesRead; 
	protected long numberOfTypeTriplesRead; 

	protected Properties properties; 
	protected static String SUMMARY_CONFIG_FILE="conf/summarization.properties"; 

	public Summarization(){
		rep = new Long2Long();
		edges = new HashMap<Long, HashMap<Long, ArrayList<Long>>>();
		summaryNodeStatistics = new HashMap<Long, Long>(); 
		summaryEdgeStatistics = new HashMap<Triple, Long>(); 
		typeOnlyNodeAlreadySeen = false;
		properties = new Properties();
		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to initialize summary properties"); 
		}
		Debugger.turnOff();
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
		Debugger.log(sb.toString());
	}
	protected Long getNextSummaryNode(){
		Long node = new Long(this.maxSummaryNode);
		this.maxSummaryNode++;
		return node; 
	}

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
			ResultSet rs = nodeStatisticQuery.executeQuery("select summarynode, count(*) from encoded_rep group by summarynode;"); 
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
							"from encoded_rep rep1, encoded_rep rep2, encoded_triples t, encoded_summary es " +
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


	/** This implementation should be shared by Weak and Strong
	 * 
	 * @param t
	 */
	protected void handleTypeTriplesAfterData(Triple t) {
		Long repS = rep.get(t.s);
		if (repS != null){
			addTriple(repS, t.p, t.o);
		}
		else{
			if (!typeOnlyNodeAlreadySeen){
				this.typeOnlyNodeID = getNextSummaryNode();
				typeOnlyNodeAlreadySeen=true;
			}
			addTriple(typeOnlyNodeID, t.p, t.o); 
		}
		this.numberOfTypeTriplesRead++;
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
			if (!existsTable(conn, "encoded_rep")) {			
				stmt.execute("create table encoded_rep(graphNode int not null, summaryNode int not null); ");
			} else {
				Debugger.log("Did not created encoded_rep table as it was already there");
			}
			// empty it (even if the creation failed, e.g. because the table was already there)
			stmt.executeUpdate("delete from encoded_rep; "); 

			// now insert all the rep entries:
			String insertIntoRep = "insert into encoded_rep values(?, ?);"; 
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
			throw new IllegalStateException("Could not insert summary triples in encoded_rep " + e.toString()); 
		}

		try {
			conn.setAutoCommit(false);
			long start = System.currentTimeMillis(); 
			if (!existsTable(conn, "encoded_summary")) {			
				stmt.execute("create table encoded_summary(s int not null, p int not null, o int not null); ");
			}
			// empty it (even if the creation failed, e.g. because the table was already there)
			stmt.executeUpdate("delete from encoded_summary; "); 

			// now insert all the summary edges:
			String insertIntoSummary = "insert into encoded_summary values(?, ?, ?);"; 
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
			throw new IllegalStateException("Could not insert summary triples in encoded_summary " + e.toString()); 
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
		ArrayList<Triple> summEdges = this.getSummaryEdges(); 
		String summaryNTFileName = "";
		if (rdfFileName.lastIndexOf(".nt") > 0) {
			summaryNTFileName = rdfFileName.substring(0, rdfFileName.lastIndexOf(".nt")) + 
					"-sum.nt"; 
		}
		else {
			summaryNTFileName = rdfFileName + "-sum.nt"; 
		}
		System.out.println("Decoding summary and saving it in .nt format in " + summaryNTFileName + "..."); 
		this.gatherStatistics();
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter (new File(summaryNTFileName))); 
			// write summary triples: 
			for (Triple t: summEdges){
				String subject = getSummaryNodeURI(URIprefix, t.s); 
				String object =  getSummaryNodeURI(URIprefix, t.p);  
				String property = RDF2SQLEncoding.dictionaryDecode(t.p); 
				Debugger.log(subject + " " + property + " " + object);
				bw.write(subject + " <" + property + "> " + object + " . \n");
			}
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
			bw.close(); 
		}
		catch(IOException e) {
			throw new IllegalStateException("Could not save the decoded summary in .nt file"); 
		}
		System.out.println("Summary decoded and saved in .nt format");
	}


	private String getSummaryNodeURI(String uriPrefix, long n) {
		return ("<" + uriPrefix + n + ">");
	}
	/**
	 * This decodes the summary (replaces property codes with the original URIs or strings) based on a dictionary table in Postgres
	 * @param con
	 * @throws SQLException
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	public void writeSummaryToDotFile(Connection con, String dotFile) {
		Properties properties = new Properties();

		try {
			properties.load(new FileReader(SUMMARY_CONFIG_FILE));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read config file"); 
		}
		String URIprefix = properties.getProperty("prefixURIForSummaryNodes"); 
		//Debugger.log("writeSummaryToDotFile:");

		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter (new File(dotFile))); 
			bw.write("digraph g{\n");

			ArrayList<Triple> summEdges = this.getSummaryEdges(); 
			for (Triple t: summEdges){
				String subject = URIprefix + t.s;  
				String object =  URIprefix + t.o; 
				String property = RDF2SQLEncoding.dictionaryDecode(t.p); 
				if (t.p == RDF2SQLEncoding.getTypeCode()) {
					// if this is a type triple, decode the object, too: concretely, this changes the object string
					object = RDF2SQLEncoding.dictionaryDecode(t.o); 
					bw.write("\"" + object.replaceAll("\"", "") + "\" [style = filled, color=darkseagreen];\n");  
					bw.write("\"" + subject.replaceAll("\"", "") + "\"" + " -> \""+ 
							object.replaceAll("\"", "") + 
							"\" [color=darkseagreen, label=\"" +  property.replaceAll("\"", "")+ "\"];\n");
				}
				else{// in all cases, print the edge: 
					//System.out.println(subject + " " + property + " " + object);
					bw.write("\"" + subject.replaceAll("\"", "") + "\"" + " -> \""+ 
							object.replaceAll("\"", "") + 
							"\" [label=\"" +  property.replaceAll("\"", "")+ "\"];\n");
				}
			}
			bw.write("}\n"); 
			bw.close(); 
		}
		catch(IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString()); 
		}
		System.out.println("Summary written to DOT file " + dotFile + "."); 
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

	public void display(String dataTriplesFile){
		writeEncodedSummaryToFile(dataTriplesFile + "-sum.nt");
		writeEncodedSummaryToDotFile(dataTriplesFile +  ".dot");
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
}
