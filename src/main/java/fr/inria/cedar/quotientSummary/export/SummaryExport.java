/**
 * This class comprises code to save the summary and a limited size of the input graph, in DOT format, 
 * in order to draw them. 
 * It also has code for saving the summary in .nt files. 
 */
package fr.inria.cedar.quotientSummary.export;

import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.PostgresIdentifier;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import org.apache.log4j.Logger;

public class SummaryExport {
	Properties properties; 
	private static final Logger LOGGER = Logger.getLogger(SummaryExport.class.getName());
	Summary summary; 
	String dictionaryTableName; 
	String triplesFileName; 
	String summaryTablePrefix; 
	String encodedTriplesTableName; 
	
	boolean gatherStatistics; 
	boolean drawOfTypeClassEdges = false; // whether or not to draw edges of the form C rdf:type rdfs:Class
	boolean drawGraphLabel = false; // when drawing with entities, we may include a label of the graph, or not
	
	DOTAuxiliary dax;
	
	private static PreparedStatement stmtSplitLeavesCount;
	
	private HashMap<Long, String> newNodeLabels; // we will plot the names of summary nodes shorter
	// and more intelligible 
	private long lastGivenLabel; 
	
	// one size fits all attribute for drawing
	double arrowsize=2.0; 	
	String schemaNodeLineSuffix = "\" [penwidth=2, fontsize=40, fillcolor=white, fontcolor=black];\n"; 
	int maxDotLinesPrinted = 1000; 
	
	
	public SummaryExport(Summary s, Properties properties, DOTAuxiliary dax, String dictionaryTableName,
			String triplesFileName, String encodedTriplesTableName){
		this.summary = s;
		this.properties = properties; 
		this.dax = dax; 
		this.dictionaryTableName = dictionaryTableName; 
		this.triplesFileName = triplesFileName;
		this.summaryTablePrefix = s.getSummaryTablePrefix();
		this.encodedTriplesTableName = encodedTriplesTableName; 
		this.newNodeLabels = new HashMap<Long, String>(); 
		lastGivenLabel = 0; 
		// by default statistics are not used
		this.gatherStatistics = false; 
		this.drawGraphLabel = false; 
		try{
			this.gatherStatistics = properties.getProperty("gatherStatistics").toLowerCase().equals("true");
			this.drawGraphLabel = properties.getProperty("drawGraphLabel").toLowerCase().equals("true"); 
		}
		catch(Exception e){
			LOGGER.info("Could not determine if I should output summarization statistics. Will not do it.");
		}
		if (gatherStatistics){
			if (summary.getSummaryNodeStatistics().isEmpty()){
				summary.gatherNodeStatistics();
			}
			if (summary.getSummaryEdgeStatistics().isEmpty()){
				summary.gatherEdgeStatistics();
			}
		}
		String representationTableName = summary.getRepresentationTableName(); 
		String getSplitLeafRepCountQuery = "select count(distinct et.o) from " + PostgresIdentifier.escapedQuotedId(encodedTriplesTableName) + " et, " +
				PostgresIdentifier.escapedQuotedId(representationTableName) + " reps, " + PostgresIdentifier.escapedQuotedId(representationTableName) + 
				" repo where reps.summarynode=? and reps.graphnode=et.s and " + 
				" et.p=? and repo.summarynode=? and repo.graphnode=et.o"; 
		try{
			stmtSplitLeavesCount =  RDF2SQLEncoding.getConnection().prepareStatement(getSplitLeafRepCountQuery); 
		}
		catch(SQLException e) {
			e.printStackTrace();
			throw new IllegalStateException("Unable to prepare statement for cardinality computation");
		}
	}
	
	//============= Saving in NT format ====

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres. It saves the
	 * summary in an .nt file
	 *
	 * @param conn
	 * @param summarizationTechnique
	 */
	public void writeDecodedSummaryToNTFile(Connection conn, String summarizationTechnique) {
		HashSet<Long> sn = summary.getSchemaNodes(); 
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);

		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");

		String summaryNTFileName = getNTSummaryFileName(summarizationTechnique);

		LOGGER.info("Decoding summary and writing it in .nt format to " + summaryNTFileName);

		ArrayList<Triple> summEdges = summary.getSummaryEdges();
		try {
			// write summary triples:
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryNTFileName)))) {
				// write summary triples:
				for (Triple t : summEdges) {
					//LOGGER.debug("Summary triple: " + t.toString() );
					String subject, property, object;
					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						if (sn.contains(t.s)) {
							subject = RDF2SQLEncoding.dictionaryDecode(t.s);
						}
						else {
							subject = getSummaryNodeURI(URIprefix, t.s);
						}
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						if (sn.contains(t.o)) {
							object = RDF2SQLEncoding.dictionaryDecode(t.o);
						}
						else {
							object = getSummaryNodeURI(URIprefix, t.o);
						}
					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						subject = RDF2SQLEncoding.dictionaryDecode(t.s);
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						object = RDF2SQLEncoding.dictionaryDecode(t.o);
					} else { // type
						if (sn.contains(t.s)) {
							subject = RDF2SQLEncoding.dictionaryDecode(t.s);
						}
						else {
							subject = getSummaryNodeURI(URIprefix, t.s);
						}
						property = RDF2SQLEncoding.dictionaryDecode(t.p);
						object = RDF2SQLEncoding.dictionaryDecode(t.o);
					}
					//LOGGER.debug(subject + " " + property + " " + object);
					bw.write(subject + " " + property + " " + object + " .\n");
				}
				if (gatherStatistics) {
					HashMap<Long, Long> summaryNodeStats = summary.getSummaryNodeStatistics(); 
					// write node cardinality statistics:
					for (long node : summaryNodeStats.keySet()) {
						long numberOfRepresentedGraphNodes = summaryNodeStats.get(node);
						String subject = getSummaryNodeURI(URIprefix, node);
						String property = properties.getProperty("summaryNodeSupportURI");
						String object = ("\"" + numberOfRepresentedGraphNodes + "\"");
						//LOGGER.debug(subject + " " + property + " " + object);
						bw.write(subject + " <" + property + "> " + object + " .\n");
					}
					HashMap<Triple, Long> summaryEdgeStats = summary.getSummaryEdgeStatistics(); 
					// write edge cardinality statistics:
					int reifiedEdgeNumber = 0;
					for (Triple ts : summaryEdgeStats.keySet()) {
						long numberOfRepresentedEdges = summaryEdgeStats.get(ts);
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
		for (Triple t : summary.getSummaryEdges())
			bw.write(t.toString() + "\n");
	}

	//============= Saving in DOT format ====
	
	public void writeEncodedSummaryToDotFile() {
		String dotFile = getNTSummaryFileName(""); 
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFile)))) {
				bw.write("digraph g{\nratio=0.66;\n");
				for (Triple t : summary.getSummaryEdges())
					bw.write(t.s + " -> " + t.o + " [label=\"" + t.p + "\"];\n");
				bw.write("}\n");
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not write encoded summary to dot file: " + dotFile + ". Is the path correct?");
		}
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres
	 *
	 * @param conn
	 * @param dotFileName
	 */
	public void writeSummaryToDotFile(Connection conn, String dotFileName) {
		HashSet<Long> sn = summary.getSchemaNodes(); 
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		dax.resetColors();
		String URIprefix = properties.getProperty("prefixURIForSummaryNodes");
		//LOGGER.info("writeSummaryToDotFile:");
	
		int dotLinesPrinted = 0; 
		
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)))) {
				bw.write("digraph g{\nratio=0.66;\n node[shape=box, color=black, style=filled];\n");
				
				ArrayList<Triple> summEdges = summary.getSummaryEdges();
				for (Triple t : summEdges) {
					if (dotLinesPrinted == this.maxDotLinesPrinted) {
						LOGGER.info("Cut DOT printing at " + maxDotLinesPrinted);
						break; // skips the rest of the drawing -- this would be too large
					}
					
					String subject, property, object, subjectInDot, propertyInDot, objectInDot;
					// in all cases, edge labels are preserved:
					property = RDF2SQLEncoding.dictionaryDecode(t.p);
					propertyInDot = getVeryShortForDot(property.replaceAll("\"", ""));
					//System.out.println("Property: " + property);
					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn); 
						if (sn.contains(t.s)) {// The subject is a schema node -- this can happen
							if (dax.unknownSchemaNode(t.s)){
								bw.write("\"" + subjectInDot + schemaNodeLineSuffix);
								dotLinesPrinted++;
							}
						}
						else { // the subject is a data node
							if (dax.unknownSummaryNode(t.s)){
								writeNodeToDot(bw, t.s, subjectInDot); 
								dotLinesPrinted++;
							}
						}
						objectInDot = getVeryShortLabelForSummaryDataSubject(t.o, sn); 
						if (sn.contains(t.o)) {// The subject is a schema node -- this can happen
							if (dax.unknownSchemaNode(t.o)){
								bw.write("\"" + objectInDot + schemaNodeLineSuffix);
								dotLinesPrinted++;
							}
						}
						else { // the object is a data node
							if (dax.unknownSummaryNode(t.o)){
								writeNodeToDot(bw, t.o, objectInDot); 
								dotLinesPrinted++;
							}
						}
						
					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						//System.out.println("Schema triple\n");
						subject = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.s));
						//subject = RDF2SQLEncoding.dictionaryDecode(t.s); 
						subjectInDot = subject.replaceAll("\"", "");
						if (gatherStatistics){
							subjectInDot = subjectInDot + " (" + summary.getRepresentedNodeNumber(t.s) + ")"; 
						}
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
						//object = RDF2SQLEncoding.dictionaryDecode(t.o);
						objectInDot = object.replaceAll("\"", "");
						if (gatherStatistics){
							objectInDot = objectInDot + " (" + summary.getRepresentedNodeNumber(t.o) + ")"; 
						}
						if (dax.unknownSchemaNode(t.s)){
							bw.write("\"" + subjectInDot + schemaNodeLineSuffix);
							dotLinesPrinted++;
						}
						if (dax.unknownSchemaNode(t.o)){
							bw.write("\"" + objectInDot + schemaNodeLineSuffix);
							dotLinesPrinted++;
						}
					} else { // type triples 
						//System.out.println("Type triple\n");
						subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn); 
						if (sn.contains(t.s)) {// The subject is a schema node -- this can happen
							if (dax.unknownSchemaNode(t.s)){
								bw.write("\"" + subjectInDot + schemaNodeLineSuffix);
								dotLinesPrinted++;
							}
						}
						else { // the subject is a data node
							if (dax.unknownSummaryNode(t.s)){
								writeNodeToDot(bw, t.s, subjectInDot); 
								dotLinesPrinted++;
							}
						}
						object = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.o));
						//object = RDF2SQLEncoding.dictionaryDecode(t.o); 
						objectInDot = object.replaceAll("\"", "");
						if (gatherStatistics){
							objectInDot = objectInDot + " (" + summary.getRepresentedNodeNumber(t.o) + ")"; 
						}
						propertyInDot = "rdf:type";
						if (sn.contains(t.s)){
							if (dax.unknownSchemaNode(t.s)){
								bw.write("\"" + subjectInDot + schemaNodeLineSuffix);
								dotLinesPrinted++;
							}
						}
						else{
							if (dax.unknownSummaryNode(t.s)){
								writeNodeToDot(bw, t.s, subjectInDot);
								dotLinesPrinted++;
							}
						}
						if (sn.contains(t.o)){
							if (dax.unknownSchemaNode(t.o)){
								bw.write("\"" + objectInDot + schemaNodeLineSuffix);
								dotLinesPrinted++;
							}
						}
						else {
							throw new IllegalStateException("Type not part of the schema nodes: "); 
						}
					}
					// write the triple in all cases:
					//System.out.println("Writing " + subjectInDot + " -> " + objectInDot);
					bw.write("\"" + subjectInDot + "\"" + " -> \"" + objectInDot + "\" [arrowsize=" + arrowsize +", penwidth=2, fontsize=40, label=\"" + propertyInDot);
					if (gatherStatistics){
						bw.write(" (" + summary.getRepresentedTripleNumber(t) + ")"); 
					}
					bw.write("\"];\n");
					dotLinesPrinted++;
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
	 * Computes the new URI of a summary node appearing in a data triple.
	 * @param s the long-encoded subject or object
	 * @param URIprefix the prefix to use for the URIs
	 * @param sn the set of schema nodes (in which we must check if a schema node happens to also participate in a data node)
	 * @return an URI of the summary node 
	 */
	String getSubjectOrObjectURIforSummaryDataNode(Long s, String URIprefix, HashSet<Long> sn) {
		String subject, subjectInDot; 
		if (sn.contains(s)) {
			subject = RDF2SQLEncoding.dictionaryDecode(s);
		}
		else {
			subject = getSummaryNodeURI(URIprefix, s);
		}
		//subjectInDot = getVeryShortForDot(subject).replaceAll("\"", "");
		subjectInDot = subject.replaceAll("\"", "");
		if (gatherStatistics){
			subjectInDot = subjectInDot + " (" + summary.getRepresentedNodeNumber(s) + ")"; 
		}
		return subjectInDot; 
	}
	
	/**
	 * Similar to getSubjectOrObjectURIforSummaryDataNode but only in the case of objects, which may happen to be leave,
	 * if we want to split the drawing of leaves into many distinct nodes, 
	 * this computes URIs that have an "inserted suffix" to distinguish between several instances of the same thing
	 * @param s the long-encoded subject or object
	 * @param URIprefix the prefix to use for the URIs
	 * @param sn the set of schema nodes (in which we must check if a schema node happens to also participate in a data node)
	 * @return an URI of the summary node 
	 * @param suffix the integer distinguishing between several copies of the same leaf
	 * @return an URI for the summary (data, leaf) node
	 */
	String getObjectURIforSummaryDataNodeWithCountSuffix(Triple t, String URIprefix, HashSet<Long> sn, Integer suffix) {
		String object, objectInDot; 
		if (sn.contains(t.o)) {
			object = RDF2SQLEncoding.dictionaryDecode(t.o);
		}
		else {
			object = getSummaryNodeURI(URIprefix, t.o);
		}
		objectInDot = getVeryShortForDot(object).replaceAll("\"", "");
		objectInDot = object.replaceAll("\"", ""); 
		objectInDot = objectInDot.replaceAll(">", ("-" + suffix + ">")); 
		if (gatherStatistics){
			// in this case, the leaf representation count must be computed through an SQL query, 
			// because the nodes represented by the mother leaf are now split across many representatives
			objectInDot = objectInDot + " (" + getRepresentedByThisLeaf(t) + ")"; 
		}
		return objectInDot; 
	}
	
	String getVeryShortLabelforSummaryDataObjectWithCountSuffix(Triple t, HashSet<Long> sn, Integer suffix) {
		String object, objectInDot; 
		if (sn.contains(t.o)) {
			object = RDF2SQLEncoding.dictionaryDecode(t.o);
		}
		else {
			String existing = this.newNodeLabels.get(t.o);
			if (existing != null) {
				object=existing; 
			}
			else {
				String label = makeNewLabel(); 
				this.newNodeLabels.put(t.o,  label); 
				object = label; 
			}
		}
		objectInDot = object +  "." + suffix; 
		if (gatherStatistics){
			// in this case, the leaf representation count must be computed through an SQL query, 
			// because the nodes represented by the mother leaf are now split across many representatives
			objectInDot = objectInDot + " (" + getRepresentedByThisLeaf(t) + ")"; 
		}
		return objectInDot; 
	}
	String getVeryShortLabelForSummaryDataSubject(Long s, HashSet<Long> sn) {
		String subject, subjectInDot; 
		if (sn.contains(s)) {
			subject = RDF2SQLEncoding.dictionaryDecode(s);
		}
		else {
			String existing = this.newNodeLabels.get(s);
			if (existing != null) {
				subject=existing; 
			}
			else {
				String label = makeNewLabel(); 
				this.newNodeLabels.put(s,  label); 
				subject = label; 
			}
			//subject = getSummaryURIPrefix() + s;
		}
		subjectInDot = subject.replaceAll("\"", "");
		if (gatherStatistics){
			subjectInDot = subjectInDot + " (" + summary.getRepresentedNodeNumber(s) + ")"; 
		}
		return subjectInDot; 
	}
	/**
	 * Creates 
	 * @return
	 */
	private String makeNewLabel() {
		lastGivenLabel++;
		return ("N" + lastGivenLabel); 
	}
	
	/**
	 * Computes the number of nodes represented by t.o which are target of an edge labeled t.p
	 * which comes from a node represented by t.s
	 * @param t a summary triple
	 * @return the number of nodes represented by t.o as above
	 */
	private Long getRepresentedByThisLeaf(Triple t) {
		try {
			stmtSplitLeavesCount.setLong(1, t.s);
			stmtSplitLeavesCount.setLong(2, t.p);
			stmtSplitLeavesCount.setLong(3, t.o);
			ResultSet rs = stmtSplitLeavesCount.executeQuery();
			while (rs.next()) {
				Long n = rs.getLong(1); 
				return n; 
			}
		}
		catch(SQLException e) {
			e.printStackTrace();
			throw new IllegalStateException("Unable to get the representation counts"); 
		}
		return 0L; 
	}
	
	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres and draws it by making
	 * a different node for every leaf
	 *
	 * @param conn
	 * @param dotFileName
	 */
	public void writeSummaryToDotFileSplitLeaves(Connection conn, String dotFileName) {
		// first, determine who is a leaf
		HashSet<Long> leaves = new HashSet<Long>(); // tentative leaf nodes (until discovered to be subjects)
		HashSet<Long> notLeaves = new HashSet<Long>(); // certain non-leaf nodes (subjects)
		for (Triple t: this.summary.getSummaryEdges()) {
			notLeaves.add(t.s); // for sure s is not a leaf
			//LOGGER.info(t.s + " surely not a leaf"); 
			if (leaves.contains(t.s)){ // if someone thought it was a leaf, fix this
				leaves.remove(t.s); 
			}
			if (!(notLeaves.contains(t.o))){ // unless there was already evidence o is not a leaf, we assume it a leaf
				leaves.add(t.o); 
				//LOGGER.info(t.o + " is a leaf");
			}
		}
		summary.numberOfLeaves = leaves.size();
		// now we know who the leaves are, we just have to draw all this		
		HashSet<Long> sn = summary.getSchemaNodes(); 
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		dax.resetColors();
		//LOGGER.debug("writeSummaryToDotFileSplitLeaves:");
		
		HashMap<Long, Integer> leafCounter  = new HashMap<Long, Integer>(); 
		int dotLinesPrinted = 0; 
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)))) {
				bw.write("digraph g{\nratio=0.66;\n node[shape=box, color=black, style=filled];\n");

				int penWidth=2; 
				ArrayList<Triple> summEdges = summary.getSummaryEdges();
				for (Triple t : summEdges) {
					String property, subjectInDot, propertyInDot, objectInDot;
					if (dotLinesPrinted == this.maxDotLinesPrinted) {
						LOGGER.info("Stopped split lines DOT drawing after " + this.maxDotLinesPrinted + " lines");
						break; 
					}
					// in all cases, edge labels are preserved:
					property = RDF2SQLEncoding.dictionaryDecode(t.p);
					propertyInDot = getVeryShortForDot(property.replaceAll("\"", ""));
					//propertyInDot = property.replaceAll("\"", "");
					//System.out.println("Property: " + property);
					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						//LOGGER.info("Data triple" + t.toString() + " property: " + propertyInDot);
						subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn); 
						if (sn.contains(t.s)) {// The subject is a schema node -- this can happen
							subjectInDot = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.s)); 
							if (dax.unknownSchemaNode(t.s)){
								bw.write("\"" + subjectInDot + schemaNodeLineSuffix); 
								dotLinesPrinted++;
							}
						}
						else { // the subject is a data node
							if (dax.unknownSummaryNode(t.s)){
								writeNodeToDot(bw, t.s, subjectInDot); 
								dotLinesPrinted++;
							}
						}
						if (leaves.contains(t.o)) { // if o is a leaf, print a new node for this occurrence			
							//LOGGER.info("Object is leaf!");
							Integer printedLeafCounter = leafCounter.get(t.o); // try to find what number to attach to it
							if (printedLeafCounter == null) {
								printedLeafCounter = 1;
								leafCounter.put(t.o, 1);
							}
							else {
								printedLeafCounter += 1; 
								leafCounter.put(t.o, printedLeafCounter); 
							}
							objectInDot = getVeryShortLabelforSummaryDataObjectWithCountSuffix(t, sn, printedLeafCounter); 
							//LOGGER.info("Writing leaf " + objectInDot + " at occurrence: " + printedLeafCounter);
							writeNodeToDot(bw, t.o, objectInDot); 
							dotLinesPrinted++;
						}
						else { // if o was not a leaf, print as before (iff we had not printed it already)
							//LOGGER.info("Object not a leaf!");
							objectInDot = getVeryShortLabelForSummaryDataSubject(t.o, sn); 
							if (dax.unknownSummaryNode(t.o)){
								objectInDot = getVeryShortLabelForSummaryDataSubject(t.o, sn); 
								writeNodeToDot(bw, t.o, objectInDot); 
								dotLinesPrinted++;
							}
						}
					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						//System.out.println("Schema triple" + RDF2SQLEncoding.decode(t).toString());
						subjectInDot = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.s)); 
						
						if (gatherStatistics){
							subjectInDot = subjectInDot + " (" + summary.getRepresentedNodeNumber(t.s) + ")"; 
						}
						objectInDot = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.o)); 
						if (gatherStatistics){
							objectInDot = objectInDot + " (" + summary.getRepresentedNodeNumber(t.o) + ")"; 
						}
						if (dax.unknownSchemaNode(t.s)){
							bw.write("\"" + subjectInDot + schemaNodeLineSuffix);
						}
						if (dax.unknownSchemaNode(t.o)){
							bw.write("\"" + objectInDot + schemaNodeLineSuffix);
						}
					} else { // type triples 
						if (!this.drawOfTypeClassEdges) { // if this was false
							if (t.o == RDF2SQLEncoding.getClassCode()) { // if this is an edge "C type Class", do not draw it
								continue; 
							}
						}
						subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn); 
						//object
						objectInDot = getVeryShortForDot(RDF2SQLEncoding.dictionaryDecode(t.o)); 
						//System.out.println("Type triple: " + subjectInDot + " " + propertyInDot + " " + objectInDot); 
						propertyInDot = "rdf:type"; 
						if (gatherStatistics){
							objectInDot = objectInDot + " (" + summary.getRepresentedNodeNumber(t.o) + ")"; 
							//System.out.println(object + " represents " + summary.getRepresentedNodeNumber(t.o));
						}
						if (sn.contains(t.s)){// subject is schema node
							//System.out.println("Subject is schema node");
							if (dax.unknownSchemaNode(t.s)){
								bw.write("\"" + subjectInDot + schemaNodeLineSuffix);
								dotLinesPrinted++;
							}
						}
						else{
							//System.out.println("Subject is not schema node");
							if (dax.unknownSummaryNode(t.s)){
								writeNodeToDot(bw, t.s, subjectInDot);
								dotLinesPrinted++;
							}
						}
						if (sn.contains(t.o)){ // object is schema node
							//System.out.println("Object is schema node");
							if (dax.unknownSchemaNode(t.o)){
								bw.write("\"" + objectInDot + schemaNodeLineSuffix);
								dotLinesPrinted++;
							}
						}
						else{ // object is not schema node yet this is a type triple?...
							//System.out.println("Object is not schema node");
							throw new IllegalStateException("The target of a type triple should be a schema node"); 
						}
					}
					// write the triple in all cases:
					bw.write("\"" + subjectInDot + "\"" + " -> \"" + objectInDot + "\" [arrowsize=" + arrowsize +", weight=1, fontsize=40, penwidth=" + penWidth +
							", label=\"" + propertyInDot); 
					if (gatherStatistics){
						bw.write(" (" + summary.getRepresentedTripleNumber(t) + ")"); 
						//System.out.println("Writing " + subjectInDot + " -> " + objectInDot + "[weight=1, penwidth=" + penWidth +
						//		" label=" + propertyInDot + " (" + summary.getRepresentedTripleNumber(t) + ")"); 
						
					}
					bw.write("\"];\n");
					dotLinesPrinted++;
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
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres and draws it by making
	 * a different node for every leaf
	 *
	 * @param conn
	 * @param dotFileName
	 */
	public void writeSummaryToDotFileSplitAndFoldLeaves(Connection conn, String dotFileName) {
		// first, determine who is a leaf
		HashSet<Long> leaves = new HashSet<Long>(); // tentative leaf nodes (until discovered to be subjects)
		HashSet<Long> notLeaves = new HashSet<Long>(); // certain non-leaf nodes (subjects)
		for (Triple t: this.summary.getSummaryEdges()) {
			notLeaves.add(t.s); // for sure s is not a leaf
			//LOGGER.info(t.s + " surely not a leaf"); 
			if (leaves.contains(t.s)){ // if someone thought it was a leaf, fix this
				leaves.remove(t.s); 
			}
			if (!(notLeaves.contains(t.o))){ // unless there was already evidence o is not a leaf, we assume it a leaf
				leaves.add(t.o); 
				//LOGGER.info(t.o + " is a leaf");
			}
		}
		summary.numberOfLeaves = leaves.size();
		Long2LongSet children = new Long2LongSet(); // for each parent of a leaf node, all its leaf children
		for (Triple t: this.summary.getSummaryEdges()) {
			if (leaves.contains(t.o)) {
				children.add(t.s, t.o);
			}
		}
		
		// prepare the drawing		
		HashSet<Long> sn = summary.getSchemaNodes(); 
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		dax.resetColors();
		//LOGGER.debug("writeSummaryToDotFileSplitLeaves:");
			
		HashMap<Long, EntitySummaryNode> entities = new HashMap<Long, EntitySummaryNode>();
		long entityEdgeCount = 0; 
		
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)))) {
				bw.write("digraph g{\nratio=0.66;\n node[shape=box, color=black, style=filled];\n");
				
				ArrayList<Triple> summEdges = summary.getSummaryEdges();
				
				//first pass: build the entities, label all the nodes, print schema triples
				HashMap<Long, Integer> leafCounter = new HashMap<Long, Integer>(); 
				for (Triple t : summEdges) {
					String subject, property, object, subjectInDot, propertyInDot, objectInDot;
					int penWidth = 1; 
					// in all cases, edge labels are preserved:
					property = RDF2SQLEncoding.dictionaryDecode(t.p);
					propertyInDot = getVeryShortForDot(property.replaceAll("\"", ""));
					//LOGGER.info("Property: " + property);
					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						//LOGGER.info("Data triple, property: " + propertyInDot);
						subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn); 
						if (sn.contains(t.s)) {// The subject is a schema node -- this can happen
							if (dax.unknownSchemaNode(t.s)){
								// this method has side effect, so leave it like this
							}
						}
						else { // the subject is a data node
							if (!leaves.contains(t.s)) {// the subject is not a leaf, thus it is an entity
								EntitySummaryNode esn = entities.get(t.s); 
								if (esn == null) { // the entity did not exist yet --> create it
									esn = new EntitySummaryNode(t.s, summary.getRepresentedNodeNumber(t.s), subjectInDot, this);
									entities.put(t.s, esn); 
								}
								// if the object is a leaf, it needs to be wrapped in this entity: 
								if (leaves.contains(t.o)) {
									Integer counterForThisSplitLeaf = leafCounter.get(t.o); // try to find what number to attach to it
									if (counterForThisSplitLeaf == null) {
										counterForThisSplitLeaf = 1;
										leafCounter.put(t.o, 1);
									}
									else {
										counterForThisSplitLeaf += 1; 
										leafCounter.put(t.o, counterForThisSplitLeaf); 
									}
									objectInDot = getVeryShortLabelforSummaryDataObjectWithCountSuffix(t, sn, counterForThisSplitLeaf); 
									
									esn.addLeafChild(t.p, t.o, summary.getRepresentedTripleNumber(t), getRepresentedByThisLeaf(t));
								}								
								// we cannot write to DOT yet because the record of t.s is not complete
							}
							else {
								// if the subject is a leaf, do nothing (it will be taken care of by the parent)
							}
						}
			
					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						//System.out.println("Schema triple" + RDF2SQLEncoding.decode(t).toString());
						subject = RDF2SQLEncoding.dictionaryDecode(t.s); 
						subjectInDot = subject.replaceAll("\"", "");
						if (gatherStatistics){
							subjectInDot = subjectInDot + " (" + summary.getRepresentedNodeNumber(t.s) + ")"; 
						}
						object = RDF2SQLEncoding.dictionaryDecode(t.o);
						objectInDot = object.replaceAll("\"", "");
						if (gatherStatistics){
							objectInDot = objectInDot + " (" + summary.getRepresentedNodeNumber(t.o) + ")"; 
						}
						if (dax.unknownSchemaNode(t.s)){
						}
						if (dax.unknownSchemaNode(t.o)){
						}
						if (gatherStatistics){
							
						}
					} else { // type triples 
						if (!this.drawOfTypeClassEdges) { // if this was false
							if (t.o == RDF2SQLEncoding.getClassCode()) { // if this is an edge "C type Class", do not draw it
								continue; 
							}
						}
						subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn); 
						EntitySummaryNode esn = entities.get(t.s); 
						if (esn == null) { // the entity did not exist yet --> create it
							esn = new EntitySummaryNode(t.s, summary.getRepresentedNodeNumber(t.s), subjectInDot, this);
							entities.put(t.s, esn); 
						}
						esn.addType(t.o);
					}
				}
				// now we print
				
				long dotLinesPrinted = 0; 
				for (Triple t: summary.getSummaryEdges()) {
					if (dotLinesPrinted == this.maxDotLinesPrinted) {
						LOGGER.info("Cut summary split DOT drawing to " + maxDotLinesPrinted);
						break; 
					}
					if (RDF2SQLEncoding.isDataProperty(t.p) || (RDF2SQLEncoding.getTypeCode() == t.p)) { // type or data triple
						if (!sn.contains(t.s)) { // data subject
							EntitySummaryNode esn = entities.get(t.s); 
							if (esn == null) {
								throw new IllegalStateException("No entity for: " + t.s);
							}
							if (dax.unknownSummaryNode(t.s)){ // print the subject in all cases
								esn.addNodeDescriptionTo(bw, dax);
								dotLinesPrinted++;
							}
							if (t.p != RDF2SQLEncoding.getTypeCode() && (!leaves.contains(t.o))) { // print data edge (not type edge)
								// if the object is not a leaf
								String subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn); 
								String objectInDot = getVeryShortLabelForSummaryDataSubject(t.o, sn); 
								String property = RDF2SQLEncoding.dictionaryDecode(t.p);
								String propertyInDot = getVeryShortForDot(property.replaceAll("\"", ""));
								bw.write("\"" + subjectInDot + "\"" + " -> \"" + objectInDot + "\" [weight=1, arrowsize=" + 
										arrowsize + ", fontsize=20, label=\"" + propertyInDot); 
								entityEdgeCount ++; 
								if (gatherStatistics){
									bw.write(" (" + summary.getRepresentedTripleNumber(t) + ")"); 
									//System.out.println("Writing " + subjectInDot + " -> " + objectInDot + "[weight=1, penwidth=" + penWidth +
									//		" label=" + propertyInDot + " (" + summary.getRepresentedTripleNumber(t) + ")"); 
									
								}
								bw.write("\"]\n"); 
								dotLinesPrinted++;
							}
						}
					}
				}
				if (drawGraphLabel) {
						bw.write("fontsize=20; label=\"" + summary.getClass().getSimpleName() + " of " +
						triplesFileName + " (" + 
						summary.triplesSummarizedSoFar + " triples): " +
						entities.size() + " nodes, " + entityEdgeCount + " edges\"\n"); 
				}
				bw.write("labelloc=top; labeljust=center;\n"); 			
				bw.write("}\n");
				bw.close();
			}
			LOGGER.info(entities.size() + " entity nodes, " + entityEdgeCount + " entity edges");
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

	
	
	private void writeNodeToDot(BufferedWriter bw, long node, String label) {
		try{
			String nColor = dax.getSummaryNodeColor(node);
			bw.write("\"" + label);
			bw.write("\" [fontsize=40, color=black, style=filled, fillcolor=" + // Fontsize=40, shape=box added on Sept 26
					nColor +
					(dax.isDarkColor(nColor)?", fontcolor=white":"")
					+ "];\n");
		}
		catch (IOException e) {
			LOGGER.error("Could not turn .dot file into .png (check the pathToDot value in summarization.properties)" + e.toString());
		}
	}
		
	
	public void writeEncodedSummaryToDotFile(String dotFile) {
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFile)))) {
				bw.write("digraph g{\nratio=0.66;\n");
				for (Triple t : summary.getSummaryEdges())
					bw.write(t.s + " -> " + t.o + " [arrowsize=" + arrowsize + ", label=\"" + t.p + "\"];\n");
				bw.write("}\n");
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not write encoded summary to dot file: " + dotFile + ". Is the path correct?");
		}
	}




	
	public void writeRDFGraphToDotFile(Connection conn, String dotFileName) {
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dotFileName)))) {
				bw.write("digraph g{\nratio=0.66;\n");
				long triplesToDraw = Math.min(100, summary.triplesSummarizedSoFar);
				//LOGGER.debug("Writing " + triplesToDraw + " RDF graph triples to DOT");
				long triplesDrawn;
				if (summary.isTypeFirst()) {
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
				long s = RDF2SQLEncoding.dictionaryEncode(subject);
				long sRep = summary.getRepresentative(s);
				//LOGGER.debug("DrawTriples: Encoded " + subject + " into " + s + " whose representative is: "  + sRep);

				String object = rs.getString(3);
				long o = RDF2SQLEncoding.dictionaryEncode(object);
				long oRep = summary.getRepresentative(o);

				//LOGGER.debug("DrawTriples: Encoded " + object + " into " + o + " whose representative is: " + oRep);
				String property = rs.getString(2);
				long p = RDF2SQLEncoding.dictionaryEncode(property);
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

	protected void writeGraphTripleToDotFile(BufferedWriter bw, long s, long p, long o, String subject, String property, String object, long sRep, long oRep) {
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
				if (p == RDF2SQLEncoding.getSubClassCode()) {
					propertyForDot = "rdfs:subClass"; 
				}
				if (p == RDF2SQLEncoding.getSubPropertyCode()) {
					propertyForDot = "rdfs:subProperty"; 
				}
				if (p == RDF2SQLEncoding.getDomainCode()) {
					propertyForDot = "rdfs:domain"; 
				}
				if (p == RDF2SQLEncoding.getRangeCode()) {
					propertyForDot = "rdfs:range"; 
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
				bw.write("\"" + subjectForDot + "\" [style = filled, color=" + dax.getSummaryNodeColor(sRep) + "];\n");

				//LOGGER.debug("TYP2 " + o + " (" + object + ") represented by  " + oRep);
				bw.write("\"" + objectForDot + "\" [fontcolor=white, style = filled, color=black];\n");
			}
			bw.write("\"" + subjectForDot + "\"" + " -> \"" + objectForDot + "\" [arrowsize=" + arrowsize + ", label=\"" + propertyForDot + "\"];\n");
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
	}

	//=========== below this line auxiliary getters

	public String getNTSummaryFileName(String summarizationTechnique) {
		int lastDotPosition = Math.max(0, triplesFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, triplesFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + triplesFileName);
		if (summarizationTechnique.equals("")) {
			return triplesFileName.substring(0, lastDotPosition) + "_" + getSummaryURIPrefix() + ".nt";
		}
		if (summarizationTechnique.equals("sat")) {
			return triplesFileName.substring(0, lastDotPosition) + "_sat_" + getSummaryURIPrefix() + ".nt";
		}
		return triplesFileName.substring(0, lastDotPosition) + "_" + summaryTablePrefix + summarizationTechnique + ".nt";
	}
	

	public String getSummaryURIPrefix() {
		if (summaryTablePrefix.length() < 2)
			throw new IllegalStateException("The method should not be called on an instance of the root Summary type");
		return summaryTablePrefix.substring(0, summaryTablePrefix.length() - 1);
	}
//	/**
//	 * Given a path to an .nt RDF data file, computes a file name by inserting
//	 * the prefix encoding the summary type before the main file name, and
//	 * replacing the trailing .nt with .dot
//	 */
//	private String extractShortFileName() {
//		int lastDotPosition = Math.max(0, triplesFileName.lastIndexOf("."));
//		int lastSlashPosition = Math.max(0, triplesFileName.lastIndexOf("/"));
//		if (lastDotPosition - lastSlashPosition < 1)
//			throw new IllegalStateException("Was not able to extract a core component of the file name " + triplesFileName);
//		return triplesFileName.substring(0, lastDotPosition); 
//		
//	}
	/**
	 * Takes the short file name and inserts the suffix before the ".".
	 *
	 * @param suffix
	 *
	 * @return
	 */
	public String getDotFileName(String suffix) {
		int lastDotPosition = Math.max(0, triplesFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, triplesFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + triplesFileName);
		return triplesFileName.substring(0, lastDotPosition) + "_" + summaryTablePrefix + suffix + "_plain.dot"; // replace .nt with .dot
	}
	/**
	 * Takes the short file name and inserts the suffix before the ".".
	 *
	 * @param suffix
	 *
	 * @return
	 */
	public String getDotFileNameSplitLeaves(String suffix) {
		int lastDotPosition = Math.max(0, triplesFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, triplesFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + triplesFileName);
		return triplesFileName.substring(0, lastDotPosition) + "_" + summaryTablePrefix + suffix + "_split.dot"; // replace .nt with .dot
	}
	/**
	 * Takes the short file name and inserts the suffix before the ".".
	 *
	 * @param suffix
	 *
	 * @return
	 */
	public String getDotFileNameFoldLeaves(String suffix) {
		int lastDotPosition = Math.max(0, triplesFileName.lastIndexOf("."));
		int lastSlashPosition = Math.max(0, triplesFileName.lastIndexOf("/"));
		if (lastDotPosition - lastSlashPosition < 1)
			throw new IllegalStateException("Was not able to extract a core component of the file name " + triplesFileName);
		return triplesFileName.substring(0, lastDotPosition) + "_" + summaryTablePrefix + suffix + "_fold.dot"; // replace .nt with .dot
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
	public String getRDFDotFileName(String suffix) {
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
		int suffixLength = Integer.parseInt(properties.getProperty("maxNodeLabelLength"));
		if (s.length() <= suffixLength){
			return s; 
		}
		else{
			return  s.substring(s.length() - suffixLength, s.length());
		}
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
}
