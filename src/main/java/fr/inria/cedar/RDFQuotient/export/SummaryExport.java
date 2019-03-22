//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

/**
 * This class comprises code to save the summary and a limited size of the input graph, in DOT format,
 * in order to draw them.
 * It also has code for saving the summary in .nt files.
 */
package fr.inria.cedar.RDFQuotient.export;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.controller.Interface;
import fr.inria.cedar.RDFQuotient.datastructures.Long2LongSet;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import fr.inria.cedar.RDFQuotient.util.PostgresIdentifier;
import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import org.apache.log4j.Logger;

public class SummaryExport {
	private static final Logger LOGGER = Logger.getLogger(SummaryExport.class.getName());

	Summary summary;
	private Properties summarizationProperties;
	private String dictionaryTableName;
	private String triplesFileName;
	private String summaryTablePrefix;
	private String encodedTriplesTableName;

	private boolean gatherStatistics = false;
	private boolean drawOfTypeClassEdges = false; // whether or not to draw edges of the form C rdf:type rdfs:Class
	private boolean drawGraphLabel = false; // when drawing with entities, we may include a label of the graph, or not

	// helper class for multicolor printing to DOT
	private DOTAuxiliary dax;

	private static PreparedStatement stmtSplitLeavesCount;

	private HashMap<Long, String> newNodeLabels; // we will plot the names of summary nodes shorter
	// and more intelligible
	private long lastGivenLabel;

	// one size fits all attribute for drawing
	private double arrowsize = 0.8;
	private String schemaNodeLineSuffix = "\" [penwidth=2, fontsize=12, fillcolor=white, fontcolor=black];\n";
	private int maxDotLinesPrinted = 1000;

	public SummaryExport(Summary s, Properties summarizationProperties, String dictionaryTableName,
			String triplesFileName, String encodedTriplesTableName){
		this.summary = s;
		this.summarizationProperties = summarizationProperties;
		this.dictionaryTableName = dictionaryTableName;
		this.triplesFileName = triplesFileName;
		this.summaryTablePrefix = s.getSummaryTablePrefix();
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.newNodeLabels = new HashMap<>();
		lastGivenLabel = 0;
		// by default statistics are not used
		this.gatherStatistics = false;
		this.drawGraphLabel = false;
		try {
			gatherStatistics = summarizationProperties.getProperty("summary.gather_representation_counts").toLowerCase().equals("true");
			dax = new DOTAuxiliary(summarizationProperties.getProperty("drawing.color_scheme"));
			drawGraphLabel = summarizationProperties.getProperty("drawing.title").toLowerCase().equals("true");
		}
		catch(Exception e){
			LOGGER.error(e);
		}
	}

	//============= Saving in NT format ====

	protected void writeEncodedTripleToFile(BufferedWriter bw) throws IOException {
		for (Triple t : summary.getSummaryEdges())
			bw.write(t.toString() + "\n");
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

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres. It saves the
	 * summary in an .nt file
	 *
	 * @param conn
	 * @return
	 */
	public String writeDecodedSummaryToNTFile(Connection conn) {
		HashSet<Long> sn = summary.getSchemaNodes();
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);

		String URIprefix = summarizationProperties.getProperty("drawing.summary_node_URI_prefix");

		String summaryNTFileName = getNTSummaryFileName();

		LOGGER.info("Decoding summary and writing it in .nt format to " + summaryNTFileName);

		ArrayList<Triple> summEdges = summary.getSummaryEdges();
		// if we generalize types, we will output the type edges of the summary not from the summary
		// (where they are not all present, as the node only has edges to the types in its most general set)
		// but from the actual type set stored in the summary.
		//
		// This means the type summary edges will be output "for each typed summary node", which is not
		// "for a summary triple".
		// Therefore, we need to remember which typed summary node has already had its type edges output, and
		// which has not.
		HashSet<Long> typedSummaryNodesCovered = new HashSet<>();

		try {
			// write summary triples:
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryNTFileName)))) {
				// write summary triples:
				for (Triple t : summEdges) {
					boolean isTypeTriple = false;
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
					} else { // type triple
						if (sn.contains(t.s)) {
							subject = RDF2SQLEncoding.dictionaryDecode(t.s);
						}
						else {
							subject = getSummaryNodeURI(URIprefix, t.s);
						}
						property = RDF2SQLEncoding.dictionaryDecode(t.p);

						if (summary.generalizeTypes() && (!sn.contains(t.s))) { // handle all the type triples of this node
							// (unless already done, and unless it is a schema node)
							object = null; // initialization to escape static analysis paranoia
							isTypeTriple = true;
							if (typedSummaryNodesCovered.contains(t.s)) { // this typed summary node already exported
								// do nothing
							}
							else {
								// write the type triples from the actual type map, not from the summary
								HashMap<Long, Long> actualTypesOfThisNode = summary.getActualTypesWithStatistics(t.s);
								if (actualTypesOfThisNode == null) {
									//LOGGER.info("Summary type triple: " + RDF2SQLEncoding.decode(t).toString());
									//LOGGER.info("For typed summary node " + t.s + " there are no actual types");
								}
								else{
									for (Long actualType: actualTypesOfThisNode.keySet()) {
										object = RDF2SQLEncoding.dictionaryDecode(actualType);
										bw.write(subject + " " + property + " " + object + " .\n");
									}
								}
								typedSummaryNodesCovered.add(t.s);
							}
						}
						else {
							object = RDF2SQLEncoding.dictionaryDecode(t.o);
						}
					}
					//LOGGER.debug(subject + " " + property + " " + object);
					// if EITHER we are not generalizing types (which means the summary triples are not correct)
					// OR this is not a type triple (non-type triples are always correct)
					if (!(summary.generalizeTypes()) || (!isTypeTriple)) {
						// if we group on generalized types and this is a type triple, we should not
						// output it, because it goes to one of the most general types, and not
						// necessarily to an actual type that occurred in the data.
						bw.write(subject + " " + property + " " + object + " .\n");
					}
				}

				if (gatherStatistics) {
					HashMap<Long, Long> summaryNodeStats = summary.getSummaryNodeStatistics();
					// write node cardinality statistics:
					for (long node : summaryNodeStats.keySet()) {
						long numberOfRepresentedGraphNodes = summaryNodeStats.get(node);
						String subject = getSummaryNodeURI(URIprefix, node);
						String property = summarizationProperties.getProperty("drawing.summary_node_support_URI_prefix");
						String object = ("\"" + numberOfRepresentedGraphNodes + "\"");
						//LOGGER.debug(subject + " " + property + " " + object);
						bw.write(subject + " <" + property + "> " + object + " .\n");
					}
					HashMap<Triple, Long> summaryEdgeStats = summary.getSummaryEdgeStatistics();
					// write edge cardinality statistics:
					int reifiedEdgeNumber = 0;
					for (Triple ts : summaryEdgeStats.keySet()) {
						// TODO: fix this too so that the edge cardinalities refer to actual edges (not the case now)
						long numberOfRepresentedEdges = summaryEdgeStats.get(ts);
						String reifEdgeURI = getSummaryNodeURI(summarizationProperties.getProperty("drawing.reified_summary_edge_URI_prefix"),
							reifiedEdgeNumber);
						bw.write(reifEdgeURI + " <" + summarizationProperties.getProperty("drawing.reified_edge_subject_URI_prefix") + "> "
							+ getSummaryNodeURI(URIprefix, ts.s) + " .\n");
						bw.write(reifEdgeURI + " <" + summarizationProperties.getProperty("drawing.reified_edge_property_URI_prefix") + "> "
							+ RDF2SQLEncoding.dictionaryDecode(ts.p) + " .\n");
						bw.write(reifEdgeURI + " <" + summarizationProperties.getProperty("drawing.reified_edge_object_URI_prefix") + "> "
							+ getSummaryNodeURI(URIprefix, ts.o) + " .\n");
						bw.write(reifEdgeURI + " <" + summarizationProperties.getProperty("drawing.summary_edge_support_URI_prefix") + "> \""
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
		return summaryNTFileName;
	}

	//============= Saving in DOT format ====

	private void drawWithDOT(String summaryDOTFilename, String summaryPNGFilename) {
		String pathToDOT = summarizationProperties.getProperty("drawing.dot_installation");
		boolean removeDOTFile = summarizationProperties.getProperty("drawing.remove_dot_file").equals("true");
		try {
			Process p = Runtime.getRuntime().exec(new String[] {pathToDOT, "-Tpng", summaryDOTFilename, "-o", summaryPNGFilename});
			try {
				p.waitFor();
			}
			catch (InterruptedException ex) {
				LOGGER.error(ex);
			}
			LOGGER.info("Summary drawn to PNG file " + summaryPNGFilename);
			if (removeDOTFile) {
				File file = new File(summaryDOTFilename);
				if(file.delete()) {
					LOGGER.info(summaryDOTFilename + " file deleted successfully");
				}
				else {
					LOGGER.error("Failed to delete " + summaryDOTFilename + " file");
				}
			}
		}
		catch (IOException e) {
			LOGGER.error("Could not turn .dot file into .png (check the drawing.dot_installation value in summarization.properties) " + e);
		}
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres
	 *
	 * @param conn
	 * @param summaryDOTFileName
	 * @param summaryPNGFileName
	 */
	public void writeSummaryToDOTFile(Connection conn, String summaryDOTFileName, String summaryPNGFileName) {
		dax = new DOTAuxiliary(summarizationProperties.getProperty("drawing.color_scheme"));
		HashSet<Long> sn = summary.getSchemaNodes();
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		dax.resetColors();
		String URIprefix = summarizationProperties.getProperty("drawing.summary_node_URI_prefix");

		int dotLinesPrinted = 0;

		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryDOTFileName)))) {
				bw.write("digraph g{\n node[color=black, style=filled];\n");

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
					bw.write("\"" + subjectInDot + "\"" + " -> \"" + objectInDot + "\" [arrowsize=" + arrowsize +", arrowhead=vee, fontsize=12, label=\"" + propertyInDot);
					if (gatherStatistics){
						bw.write(" (" + summary.getRepresentedTripleNumber(t) + ")");
					}
					bw.write("\"];\n");
					dotLinesPrinted++;
				}
				if (drawGraphLabel) {
					bw.write("fontsize=12; label=\"" + summary.getClass().getSimpleName() +
					(summary.generalizeTypes()?" (generalize types) ":"") +
							" of " +
					triplesFileName + " (" +
					summary.triplesSummarizedSoFar + " triples)\"\n");
					bw.write("labelloc=top; labeljust=center;\n");
				}
				bw.write("}\n");
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
		LOGGER.info("Summary written to DOT file " + summaryDOTFileName);

		drawWithDOT(summaryDOTFileName, summaryPNGFileName);
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
		if (stmtSplitLeavesCount == null) {
			String representationTableName = summary.getRepresentationTableName();
			String getSplitLeafRepCountQuery = "select count(distinct et.o) from "
				+ PostgresIdentifier.escapedQuotedId(encodedTriplesTableName) + " et, "
				+ PostgresIdentifier.escapedQuotedId(representationTableName) + " reps, "
				+ PostgresIdentifier.escapedQuotedId(representationTableName)
				+ " repo where reps.summarynode=? and reps.graphnode=et.s and "
				+ " et.p=? and repo.summarynode=? and repo.graphnode=et.o";
			try{
				stmtSplitLeavesCount =  RDF2SQLEncoding.getConnection().prepareStatement(getSplitLeafRepCountQuery);
			}
			catch(SQLException e) {
				e.printStackTrace();
				throw new IllegalStateException("Unable to prepare statement for cardinality computation");
			}
		}
		try {
			stmtSplitLeavesCount.setLong(1, t.s);
			stmtSplitLeavesCount.setLong(2, t.p);
			stmtSplitLeavesCount.setLong(3, t.o);
			ResultSet rs = stmtSplitLeavesCount.executeQuery();
			while (rs.next()) {
				Long n = rs.getLong(1);
				//LOGGER.info("Represented by leaf " + t.toString() + ": " + n);
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
	 * @param summaryDOTFileName
	 * @param summaryPNGFileName
	 */
	public void writeSummaryToDOTFileSplitLeaves(Connection conn, String summaryDOTFileName, String summaryPNGFileName) {
		dax = new DOTAuxiliary(summarizationProperties.getProperty("drawing.color_scheme"));
		// first, determine who is a leaf
		HashSet<Long> leaves = new HashSet<>(); // tentative leaf nodes (until discovered to be subjects)
		HashSet<Long> notLeaves = new HashSet<>(); // certain non-leaf nodes (subjects)
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
		//LOGGER.debug("writeSummaryToDOTFileSplitLeaves:");

		HashMap<Long, Integer> leafCounter  = new HashMap<>();
		int dotLinesPrinted = 0;
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryDOTFileName)))) {
				bw.write("digraph g{\nsplines=polyline;\n node[shape=box, color=black, style=filled];\n");

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
					bw.write("\"" + subjectInDot + "\"" + " -> \"" + objectInDot + "\" [arrowsize=" + arrowsize +", weight=1, arrowhead=vee, fontsize=12, penwidth=" + penWidth +
							", label=\"" + propertyInDot);
					if (gatherStatistics){
						bw.write(" (" + summary.getRepresentedTripleNumber(t) + ")");
						//System.out.println("Writing " + subjectInDot + " -> " + objectInDot + "[weight=1, penwidth=" + penWidth +
						//		" label=" + propertyInDot + " (" + summary.getRepresentedTripleNumber(t) + ")");

					}
					bw.write("\"];\n");
					dotLinesPrinted++;
				}
				if (drawGraphLabel) {
					bw.write("fontsize=12; label=\"" + summary.getClass().getSimpleName() +
					(summary.generalizeTypes()?" (generalize types) ":"") +
							" (split leaves) of " +
					triplesFileName + " (" +
					summary.triplesSummarizedSoFar + " triples)\"\n");
					bw.write("labelloc=top; labeljust=center;\n");
				}
				bw.write("}\n");
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
		LOGGER.info("Summary written to DOT file " + summaryDOTFileName);

		drawWithDOT(summaryDOTFileName, summaryPNGFileName);
	}

	/**
	 * This decodes the summary (replaces property codes with the original URIs
	 * or strings) based on a dictionary table in Postgres and draws it by making
	 * a different node for every leaf
	 *
	 * @param conn
	 * @param summaryDOTFileName
	 * @param summaryPNGFileName
	 */
	public void writeSummaryToDOTFileSplitAndFoldLeaves(Connection conn, String summaryDOTFileName, String summaryPNGFileName) {
		dax = new DOTAuxiliary(summarizationProperties.getProperty("drawing.color_scheme"));
		// first, determine who is a leaf
		HashSet<Long> leaves = new HashSet<>(); // tentative leaf nodes (until discovered to be subjects)
		HashSet<Long> notLeaves = new HashSet<>(); // certain non-leaf nodes (subjects)

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
		//LOGGER.debug("writeSummaryToDOTFileSplitLeaves:");

		HashMap<Long, EntitySummaryNode> entities = new HashMap<>();
		long entityEdgeCount = 0;

		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryDOTFileName)))) {
				bw.write("digraph g{\nsplines=polyline;\n nodesep=0.15;\n ranksep=0.2;\n node[shape=box, color=black, style=filled];\n");

				ArrayList<Triple> summEdges = summary.getSummaryEdges();

				//first pass: build the entities, label all the nodes, print schema triples
				for (Triple t : summEdges) {
					//LOGGER.debug("First pass over " + t.toString());
					String subject, property, object, subjectInDot, propertyInDot, objectInDot;
					// in all cases, edge labels are preserved:
					property = RDF2SQLEncoding.dictionaryDecode(t.p);
					//object = RDF2SQLEncoding.dictionaryDecode(t.o); Don't do this: the object may be a new node
					// thus it may lack a code in the dictionary
					//LOGGER.debug("Trying to write " + t.p + " edge, decoded into: |" + property + "|");
					propertyInDot = getVeryShortForDot(property.replaceAll("\"", ""));
					//LOGGER.info("Property: " + property);
					if (RDF2SQLEncoding.isDataProperty(t.p)) { // data
						//LOGGER.info("Data triple, property: " + propertyInDot);
						subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn);
						if (!sn.contains(t.s)) { // the subject is a data node
							//LOGGER.info("Data subject: " + t.s);
							if (!leaves.contains(t.s)) {// the subject is not a leaf, thus it is an entity
								//LOGGER.info("Not leaf");
								EntitySummaryNode esn = entities.get(t.s);
								if (esn == null) { // the entity did not exist yet --> create it
									esn = new EntitySummaryNode(t.s, summary.getRepresentedNodeNumber(t.s), subjectInDot, this);
									entities.put(t.s, esn);
								}
								// if the object is a leaf, it needs to be wrapped in this entity:
								if (leaves.contains(t.o)) {
									esn.addLeafChild(t.p, t.o, summary.getRepresentedTripleNumber(t),
											getRepresentedByThisLeaf(t));
								}
								// we cannot write to DOT yet because the record of t.s is not complete
							}
							else {
								// if the subject is a leaf, do nothing (it will be taken care of by the parent)
							}
						}

					} else if (RDF2SQLEncoding.isSchemaProperty(t.p)) { // schema
						// nothing
					} else { // type triples
						if (!sn.contains(t.s)) { // if the subject is not a schema node itself, create an entity
							subjectInDot = getVeryShortLabelForSummaryDataSubject(t.s, sn);
							EntitySummaryNode esn = entities.get(t.s);
							if (esn == null) { // the entity did not exist yet --> create it
								//LOGGER.info("Creating new entity for subject " + t.s + " of type " + object);
								esn = new EntitySummaryNode(t.s, summary.getRepresentedNodeNumber(t.s), subjectInDot, this);
								entities.put(t.s, esn);
							}
							esn.addType(t.o, summary.getRepresentedTripleNumber(t));
						}
					}
				}
				// now we print

				//LOGGER.info("PRINTING");
				long dotLinesPrinted = 0;
				for (Triple t: summary.getSummaryEdges()) {
					if (dotLinesPrinted == this.maxDotLinesPrinted) {
						LOGGER.info("Cut summary split DOT drawing to " + maxDotLinesPrinted);
						break;
					}
					//LOGGER.info("Second pass over " + t.toString());
					if (RDF2SQLEncoding.isDataProperty(t.p) || (RDF2SQLEncoding.getTypeCode() == t.p)) { // type or data triple
						if (!sn.contains(t.s)) { // data subject
							//LOGGER.info(t.s + " is a data node");
							EntitySummaryNode esn = entities.get(t.s);
							if (esn == null) {
								throw new IllegalStateException("No entity for: " + t.s);
							}
							if (dax.unknownSummaryNode(t.s)){ // print the subject in all cases
								if (summary.generalizeTypes()) {
									esn.setActualTypes(summary.getActualTypesWithStatistics(t.s));
								}
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
										arrowsize + ", fontsize=12, arrowhead=vee, " +
										(summary.isGeneric(t.p)?" fontname=\"times italic\", ":"") +
										"label=\"" + propertyInDot);
								entityEdgeCount ++;
								if (gatherStatistics){
									bw.write(" (" + summary.getRepresentedTripleNumber(t) + ")");
									//LOGGER.debug("Writing " + subjectInDot + " -> " + objectInDot + "[weight=1, penwidth=" + penWidth +
									//		" label=" + propertyInDot + " (" + summary.getRepresentedTripleNumber(t) + ")");

								}
								bw.write("\"]\n");
								dotLinesPrinted++;
							}
						}
					}
				}
				if (drawGraphLabel) {
						bw.write("fontsize=12; label=\"" + summary.getClass().getSimpleName() +
						(summary.generalizeTypes()?" (generalize types) ":"") +
								" of " +
						triplesFileName + " (" +
						summary.triplesSummarizedSoFar + " triples): " +
						entities.size() + " nodes, " + entityEdgeCount + " edges\"\n");
						bw.write("labelloc=top; labeljust=center;\n");
				}
				bw.write("}\n");
				bw.close();
			}
			LOGGER.info(entities.size() + " entity nodes, " + entityEdgeCount + " entity edges");
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
		LOGGER.info("Summary written to DOT file " + summaryDOTFileName);

		drawWithDOT(summaryDOTFileName, summaryPNGFileName);
	}

	private void writeNodeToDot(BufferedWriter bw, long node, String label) {
		try{
			String nColor = dax.getSummaryNodeColor(node);
			bw.write("\"" + label);
			bw.write("\" [fontsize=12, color=black, style=filled, fillcolor=" +
					nColor +
					(dax.isDarkColor(nColor)?", fontcolor=white":"")
					+ "];\n");
		}
		catch (IOException e) {
			LOGGER.error("Could not turn .dot file into .png (check the pathToDot value in summarization.properties) " + e);
		}
	}

	public void writeEncodedSummaryToDOTFile(String summaryDOTFileName, String summaryPNGFileName) {
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryDOTFileName)))) {
				bw.write("digraph g{\nsplines=polyline;");
				for (Triple t : summary.getSummaryEdges())
					bw.write(t.s + " -> " + t.o + " [arrowsize=" + arrowsize + ", arrowhead=vee, label=\"" + t.p + "\"];\n");
				if (drawGraphLabel) {
					bw.write("fontsize=12; label=\"" + summary.getClass().getSimpleName() +
					(summary.generalizeTypes()?" (generalize types) ":"") +
							" of " +
					triplesFileName + " (" +
					summary.triplesSummarizedSoFar + " triples)\"\n");
					bw.write("labelloc=top; labeljust=center;\n");
				}
				bw.write("}\n");
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not write encoded summary to dot file: " + summaryDOTFileName + ". Is the path correct?");
		}

		drawWithDOT(summaryDOTFileName, summaryPNGFileName);
	}

	/**
	 * Takes triples from a cursor and prints them in DOT format into a buffered writer.
	 * @param conn
	 * @param rs
	 * @param bw
	 * @param triplesToDraw
	 * @param triplesDrawn
	 * @param isTypeFirst
	 * @return the number of triples drawn. This is needed to control how many triples (if any) we need to print from the second group of triples.
	 */
	protected long drawTriples(Connection conn, ResultSet rs, BufferedWriter bw, long triplesToDraw, long triplesDrawn, boolean isTypeFirst) {
		long currentPosition = triplesDrawn;
		long triplesDrawnInDot = 0;
		ResultSet userRS = null;
		String userSubject = null;
		String userProperty = null;
		String userObject = null;
		Long userAddedAfter = null;
		String subject;
		String property;
		String object;

		if (userEncodedTriplesTableExists(conn)) {
			userRS = getUserTriplesCursorForDotDrawing(conn);
		}
		try {
			do {
				if (currentPosition == triplesToDraw) {
					break;
				}
				// check user triple
				if (userRS != null) {
					if (userAddedAfter == null) {
						if (userRS.next()) {
							userSubject = userRS.getString(1);
							userProperty = userRS.getString(2);
							userObject = userRS.getString(3);
							userAddedAfter = userRS.getLong(4);
						}
					}
					if (userAddedAfter != null && userAddedAfter == currentPosition) {
						subject = userSubject;
						property = userProperty;
						object = userObject;
						userSubject = null;
						userProperty = null;
						userObject = null;
						userAddedAfter = null;
					}
					else {
						if (rs.next()) {
							subject = rs.getString(1);
							property = rs.getString(2);
							object = rs.getString(3);
						}
						else {
							break;
						}
					}
				}
				else {
					if (rs.next()) {
						subject = rs.getString(1);
						property = rs.getString(2);
						object = rs.getString(3);
					}
					else {
						break;
					}
				}

				triplesDrawnInDot++;
				currentPosition++;

				long p = RDF2SQLEncoding.dictionaryEncode(property);
				if (summary.genericPropertiesIgnoredInCliques.contains(p)) {
					continue;
				}

				long s = RDF2SQLEncoding.dictionaryEncode(subject);
				long sRep = s;
				if (!isTypeFirst) {
					sRep = summary.getRepresentative(s);
				}
				//LOGGER.debug("DrawTriples: Encoded " + subject + " into " + s + " whose representative is: "  + sRep);

				long o = RDF2SQLEncoding.dictionaryEncode(object);
				long oRep = summary.getRepresentative(o);

				//LOGGER.debug("DrawTriples: Encoded " + object + " into " + o + " whose representative is: " + oRep);
				//LOGGER.debug("DRAW Triple! (" + subject + " " + property + " " + object + ")");
				//LOGGER.debug("DRAW Represented by: " + sRep + " " + p + " " + oRep);
				writeGraphTripleToDOTFile(bw, s, p, o, subject, property, object, sRep, oRep);
			}
			while (true);
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Could not get triple from cursor " + ex.toString());
		}
		return triplesDrawnInDot;
	}

	public void writeRDFGraphToDOTFile(Connection conn, String summaryDOTFileName, String summaryPNGFileName) {
		try {
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(summaryDOTFileName)))) {
				bw.write("digraph g{\n");
				long triplesToDraw = Math.min(100, summary.triplesSummarizedSoFar);
				long triplesDrawn = 0;
				if (summary.isTypeFirst()) {
					try (ResultSet rs = getTypeTriplesCursorForDotDrawing(conn, triplesToDraw)) {
						triplesDrawn = drawTriples(conn, rs, bw, triplesToDraw, triplesDrawn, true);
					}
					if (triplesDrawn < triplesToDraw){
						try (ResultSet rs2 = getNonTypeTriplesCursorForDotDrawing(conn, triplesToDraw-triplesDrawn)) {
							drawTriples(conn, rs2, bw, triplesToDraw, triplesDrawn, false);
						}
					}
				}
				else {
					try (ResultSet rs = getNonTypeTriplesCursorForDotDrawing(conn, triplesToDraw)) {
						triplesDrawn = drawTriples(conn, rs, bw, triplesToDraw, triplesDrawn, false);
					}
					if (triplesDrawn < triplesToDraw){
						try (ResultSet rs2 = getTypeTriplesCursorForDotDrawing(conn, triplesToDraw-triplesDrawn)) {
							drawTriples(conn, rs2, bw, triplesToDraw, triplesDrawn, false);
						}
					}
				}
				if (drawGraphLabel) {
					bw.write("fontsize=12; label=\"RDF graph " +
					triplesFileName + " (" +
					summary.triplesSummarizedSoFar + " triples)\"\n");
					bw.write("labelloc=top; labeljust=center;\n");
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
		LOGGER.info("Graph written to DOT file " + summaryDOTFileName);

		drawWithDOT(summaryDOTFileName, summaryPNGFileName);
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
			String stepByStep = summarizationProperties.getProperty("summary.step_by_step");
			Boolean sbs = stepByStep.equals("false");
			String query = "select d1.value, d2.value, d3.value from (select row_number() over () as id, s, p, o from "
				+ encodedTriplesTableName + ") t join " + dictionaryTableName
				+ " d1 on t.s = d1.key join " + dictionaryTableName
				+ " d2 on t.p = d2.key join " + dictionaryTableName
				+ " d3 on t.o = d3.key where d2.value <> '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>'"
				+ (summarizationProperties.getProperty("database.deterministic_ordering").equals("false") ? " order by id" : " order by d1.key, d2.key, d3.key")
				+ " limit " + triplesToDraw;
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
				+ " d3 on t.o = d3.key where d2.value = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>'"
				+ (summarizationProperties.getProperty("database.deterministic_ordering").equals("false") ? "" : " order by d1.value, d2.value, d3.value")
				+ " limit " + triplesToDraw;
			return conn.createStatement().executeQuery(query);
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing");
		}
	}

	protected boolean userEncodedTriplesTableExists(Connection conn) {
		try {
			DatabaseMetaData dbm = conn.getMetaData();
			ResultSet rs = dbm.getTables(null, "public", "user_encoded_triples", null);
			return rs.next();
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing");
		}
	}

	protected ResultSet getUserTriplesCursorForDotDrawing(Connection conn) {
		try {
			String query = "select d1.value, d2.value, d3.value, added_after from "
				+ " user_encoded_triples t join " + dictionaryTableName
				+ " d1 on t.s = d1.key join " + dictionaryTableName
				+ " d2 on t.p = d2.key join " + dictionaryTableName
				+ " d3 on t.o = d3.key";
			return conn.createStatement().executeQuery(query);
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing");
		}
	}

	protected void writeGraphTripleToDOTFile(BufferedWriter bw, long s, long p, long o, String subject, String property, String object, long sRep, long oRep) {
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
			bw.write("\"" + subjectForDot + "\"" + " -> \"" + objectForDot + "\" [arrowsize=" + arrowsize + ", arrowhead=vee, label=\"" + propertyForDot + "\"];\n");
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open the DOT file to for the summary: " + e.toString());
		}
	}

	//=========== below this line auxiliary getters

	/**
	 * Computes a summary filename by:
	 * 1) adding prefix for DOT filename after last slash
	 * 2) adding information about saturation after dataset filename
	 * 3) adding summary short name just before the file extension
	 *
	 * @return
	 */
	public String getNTSummaryFileName() {
		String filePathWithoutExtension = Interface.trimExtension(triplesFileName, false);
		String NTFilenamePrefix = summarizationProperties.getProperty("summary.nt_file_prefix");
		int lastSlashPostion = filePathWithoutExtension.lastIndexOf("/");
		String newPath = filePathWithoutExtension.substring(0, lastSlashPostion + 1) + NTFilenamePrefix;
		String filename = filePathWithoutExtension.substring(lastSlashPostion + 1);
		filePathWithoutExtension = newPath + filename;
		if (NTFilenamePrefix.contains("/")) {
			lastSlashPostion = newPath.lastIndexOf("/");
			newPath = newPath.substring(0, lastSlashPostion);
			File f = new File(newPath);
			f.mkdirs();
		}

		String saturated = summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true") ? "_sat" : "";

		return filePathWithoutExtension + saturated + "_" + summaryTablePrefix + ".nt";
	}

	/**
	 * Computes a DOT filename by:
	 * 1) adding prefix for DOT filename after last slash
	 * 2) adding information about saturation after dataset filename
	 * 3) adding summary short name
	 * 4) adding drawing style information according to shortSummaryName
	 * 5) adding the suffix just before the file extension
	 * 6) replacing .nt extension in dataset filename with .dot
	 *
	 * As a side effect, creates directory(-ies) according to the added prefix.
	 * @param shortSummaryName
	 * @param suffix
	 *
	 * @return
	 */
	public String getDOTFileName(Boolean shortSummaryName, String suffix) {
		String filePathWithoutExtension = Interface.trimExtension(triplesFileName, false);
		String DOTFilenamePrefix = summarizationProperties.getProperty("drawing.dot_file_prefix");
		int lastSlashPostion = filePathWithoutExtension.lastIndexOf("/");
		String newPath = filePathWithoutExtension.substring(0, lastSlashPostion + 1) + DOTFilenamePrefix;
		String filename = filePathWithoutExtension.substring(lastSlashPostion + 1);
		filePathWithoutExtension = newPath + filename;
		if (DOTFilenamePrefix.contains("/")) {
			lastSlashPostion = newPath.lastIndexOf("/");
			newPath = newPath.substring(0, lastSlashPostion);
			File f = new File(newPath);
			f.mkdirs();
		}

		String saturated = summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true") ? "_sat" : "";
		String drawingStyle = summarizationProperties.getProperty("drawing.style");

		if (shortSummaryName) {
			return filePathWithoutExtension + saturated + "_" + summaryTablePrefix + "_" + drawingStyle + suffix + ".dot";
		}
		return filePathWithoutExtension + saturated + "_" + drawingStyle + suffix + ".dot";
	}

	/**
	 * Computes a DOT filename by:
	 * 1) adding prefix for DOT filename after last slash
	 * 2) adding information about saturation after dataset filename
	 * 3) adding summary short name
	 * 4) adding drawing style information
	 * 5) replacing .nt extension in dataset filename with .dot
	 *
	 * As a side effect, creates directory(-ies) according to the added prefix.
	 * @return
	 */
	public String getDOTFileName() {
		return getDOTFileName(true, "");
	}

	/**
	 * Computes a PNG filename by:
	 * 1) adding prefix for PNG filename after last slash
	 * 2) adding information about saturation after dataset filename
	 * 3) adding summary short name
	 * 4) adding drawing style information according to shortSummaryName
	 * 5) adding the suffix just before the file extension
	 * 6) replacing .nt extension in dataset filename with .dot
	 *
	 * As a side effect, creates directory(-ies) according to the added prefix.
	 *
	 * @param shortSummaryName
	 * @param suffix
	 *
	 * @return
	 */
	public String getPNGFileName(Boolean shortSummaryName, String suffix) {
		String filePathWithoutExtension = Interface.trimExtension(triplesFileName, false);
		String PNGFilenamePrefix = summarizationProperties.getProperty("drawing.png_file_prefix");
		int lastSlashPostion = filePathWithoutExtension.lastIndexOf("/");
		String newPath = filePathWithoutExtension.substring(0, lastSlashPostion + 1) + PNGFilenamePrefix;
		String filename = filePathWithoutExtension.substring(lastSlashPostion + 1);
		filePathWithoutExtension = newPath + filename;
		if (PNGFilenamePrefix.contains("/")) {
			lastSlashPostion = newPath.lastIndexOf("/");
			newPath = newPath.substring(0, lastSlashPostion);
			File f = new File(newPath);
			f.mkdirs();
		}

		String saturated = summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true") ? "_sat" : "";
		String drawingStyle = summarizationProperties.getProperty("drawing.style");

		if (shortSummaryName) {
			return filePathWithoutExtension + saturated + "_" + summaryTablePrefix + "_" + drawingStyle + suffix + ".png";
		}
		return filePathWithoutExtension + saturated + "_" + drawingStyle + suffix + ".png";
	}

	/**
	 * Computes a PNG filename by:
	 * 1) adding prefix for PNG filename after last slash
	 * 2) adding information about saturation after dataset filename
	 * 3) adding summary short name
	 * 4) adding drawing style information
	 * 5) replacing .nt extension in dataset filename with .dot
	 *
	 * As a side effect, creates directory(-ies) according to the added prefix.
	 *
	 * @return
	 */
	public String getPNGFileName() {
		return getPNGFileName(true, "");
	}

	/**
	 * URIs can be too long, thus they may need to be shortened in a .dot file.
	 *
	 * @param URI
	 *
	 * @return
	 */
	protected String getShortURIForDot(String URI) {
		int maxNodeLabelLength = Integer.parseInt(summarizationProperties.getProperty("drawing.max_node_label_length"));
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
		int suffixLength = Integer.parseInt(summarizationProperties.getProperty("drawing.max_node_label_length"));
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
		return ("<" + uriPrefix + summaryTablePrefix + n + ">");
	}
}
