package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TypedStrongSummary extends StrongOrTypedStrongSummary {
	private static final Logger LOGGER = Logger.getLogger(TypedStrongSummary.class.getName());

	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set
	Long2Long n2cs; // for each node, its class set ID. This is also the rep function for typed nodes
	Long2LongSet n2c; // for each node, the set of types we know so far for this node
	HashMap<TreeSet<Long>, Long> cs2csID; // for each set of types known so far, the ID of that set

	protected final static char TRS_RP_RO = 21;
	protected final static char TRS_RP_UO = 22;

	protected final static char TRS_UP_RO = 25;
	protected final static char TRS_UP_UO = 26;

	protected final static char RS_RP_TRO = 28;
	protected final static char RS_UP_TRO = 29;

	protected final static char US_UP_TRO = 31;
	protected final static char US_RP_TRO = 32;

	public TypedStrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		cs = new Long2LongSet();
		n2sc = new Long2Long();
		rep = new Long2Long();
		n2cs = new Long2Long();

		n2c = new Long2LongSet();
		cs2csID = new HashMap<>();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE;
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX;
		isTypeFirst = true;
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn) {
		long avoidCollisionsTimeStart;
		long avoidCollisionsTime = 0;
		long start = System.currentTimeMillis();
		try {
			conn.setAutoCommit(false);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		if (typeConstantCode != -1) {
			avoidCollisionsTimeStart = System.currentTimeMillis();
			avoidCollisionsWhenAssigningSummaryNodes(conn);
			avoidCollisionsTime += System.currentTimeMillis() - avoidCollisionsTimeStart;
		}
		String getTypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						this.handleTypeTripleBeforeData(t);
						rep.put(t.o, t.o);
						triplesSummarizedSoFar++;
						typeTriplesSummarizedSoFar++;
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		classSetCreationTime = System.currentTimeMillis() - start - avoidCollisionsTime;
		LOGGER.info("Class sets created in " + classSetCreationTime + " ms");

		start = System.currentTimeMillis();
		this.representTypeTriples();
		if (checkConsistency) {
			consistencyChecks();
		}
		typeTriplesSummarizationTime = System.currentTimeMillis() - start;
		LOGGER.info("Summarized " + typeTriplesSummarizedSoFar + " type triples in " + typeTriplesSummarizationTime + " ms");

		start = System.currentTimeMillis();
		collectSchemaNodes(conn);
		// now all the non-type triples
		String getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if ((t.p == RDF2SQLEncoding.getSubClassCode())
						|| (t.p == RDF2SQLEncoding.getSubPropertyCode())
						|| (t.p == RDF2SQLEncoding.getDomainCode())
						|| (t.p == RDF2SQLEncoding.getRangeCode())) {
							edgesWithProv.addTriple(t.s, t.p, t.o);
							rep.put(t.s, t.s);
							rep.put(t.o, t.o);
						}
						else
							handleDataTriple(t);
						triplesSummarizedSoFar++;
						dataTriplesSummarizedSoFar++;
						if (checkConsistency) {
							consistencyChecks();
						}
						//this.drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		dataTriplesSummarizationTime = System.currentTimeMillis() - start;
		LOGGER.info("Summarized " + dataTriplesSummarizedSoFar + " data triples in " + dataTriplesSummarizationTime + " ms");

		allTriplesSummarizationTime = classSetCreationTime + typeTriplesSummarizationTime + dataTriplesSummarizationTime;
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " triples overall in " + allTriplesSummarizationTime + " ms");
	}

	@Override
	public void handleTypeTripleBeforeData(Triple t) {
		//LOGGER.debug("@@@ Type triple: " + t.toString()); 

		TreeSet<Long> classSetOfThisNode = n2c.get(t.s); 
		if (classSetOfThisNode == null){ // this is the first time we encounter the node: create a class set with exactly this type
			classSetOfThisNode = new TreeSet<>();
			classSetOfThisNode.add(t.o); 
			Long thisNodeClassSetID = cs2csID.get(classSetOfThisNode);
			if (thisNodeClassSetID == null){
				thisNodeClassSetID = this.getNextSummaryNode();
				cs.put(thisNodeClassSetID, classSetOfThisNode);
				cs2csID.put(classSetOfThisNode, thisNodeClassSetID);
			}
			n2c.put(t.s, classSetOfThisNode);
			n2cs.put(t.s, thisNodeClassSetID);
		}
		else{ // we already had some types for t.s
			if (classSetOfThisNode.contains(t.o)){
				// do nothing
			}
			else {
				// n is moving from classSetOfThisNode to newClassSetOfThisNode.
				// TODO Check if classSetOfThisNode is deserted and if yes, maybe remove it.
				// (We can also keep it there to reuse it later...)
				TreeSet<Long> newClassSetOfThisNode = new TreeSet<>();
				newClassSetOfThisNode.addAll(classSetOfThisNode);
				newClassSetOfThisNode.add(t.o); 

				Long newClassSetID = cs2csID.get(newClassSetOfThisNode);
				if (newClassSetID == null){
					// this class set was not already known. We create it.
					newClassSetID = this.getNextSummaryNode();
					cs.put(newClassSetID, newClassSetOfThisNode); // installs the new class set
					cs2csID.put(newClassSetOfThisNode, newClassSetID); // installs the new class set
				}
				// whether or not newClassSetID was known:
				n2cs.put(t.s, newClassSetID); // erases/replaces previously known class set ID
				n2c.put(t.s, newClassSetOfThisNode); // erases/replaces previously known class set
			}
		}
	}

	/**
	 * This method adds the type triples in the summary, based on the structures previously filled in while traversing those triples.
	 * It is called only once and will output all the type triples of the summary.
	 */
	public void representTypeTriples() {
		//LOGGER.debug("POST HANDLE TYPE TRIPLES");
		for (Long node: this.n2cs.getKeys()){
			Long thisClassSetID = this.n2cs.get(node);
			TreeSet<Long> thisClassSet = this.cs.get(thisClassSetID);
			for (Long thisClass: thisClassSet){
				edgesWithProv.addTriple(thisClassSetID, RDF2SQLEncoding.getTypeCode(), thisClass);
				rep.put(node, thisClassSetID);
			}
		}
	}

	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean sTyped, boolean sSchemaNode, boolean pRepresented, boolean oRepresented, boolean oTyped, boolean oSchemaNode) {
		if((sTyped || sSchemaNode) && (oTyped || oSchemaNode)) {
			return SELF_SELF;
		}
		if (sTyped) {
			if (pRepresented) {
				if (oRepresented) {
					return TRS_RP_RO;
				}
				else {
					return TRS_RP_UO;
				}
			}
			else {
				if (oRepresented) {
					return TRS_UP_RO;
				}
				else {
					return TRS_UP_UO;
				}
			}
		}
		else if (sSchemaNode) {
			if (pRepresented) {
				if (oRepresented) {
					return SN_RP_RO;
				}
				else {
					return SN_RP_UO;
				}
			}
			else {
				if (oRepresented) {
					return SN_UP_RO;
				}
				else {
					return SN_UP_UO;
				}
			}
		}
		else if (sRepresented) {
			if (pRepresented) {
				if (oTyped) {
					return RS_RP_TRO;
				}
				else if (oSchemaNode) {
					return RS_RP_SN;
				}
				else if (oRepresented) {
					return RS_RP_RO;
				}
				else {
					return RS_RP_UO;
				}
			}
			else {
				if (oTyped) {
					return RS_UP_TRO;
				}
				else if (oSchemaNode) {
					return RS_UP_SN;
				}
				else if (oRepresented) {
					return RS_UP_RO;
				}
				else {
					return RS_UP_UO;
				}
			}
		}
		else {
			if (pRepresented) {
				if (oTyped) {
					return US_RP_TRO;
				}
				else if (oSchemaNode) {
					return US_RP_SN;
				}
				else if (oRepresented) {
					return US_RP_RO;
				}
				else {
					return US_RP_UO;
				}
			}
			else {
				if (oTyped) {
					return US_UP_TRO;
				}
				else if (oSchemaNode) {
					return US_UP_SN;
				}
				else if (oRepresented) {
					return US_UP_RO;
				}
				else {
					return US_UP_UO;
				}
			}
		}
	}

	public void handleDataTriple(Triple t) {
		// 18 cases: (TRS, RS, US) x (RP, UP) x (TRO, RO, UO) also multiplied by: which cliques are empty and their consequences on fusion
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);

		Long sourceCliqueS = n2sc.get(t.s);
		Long targetCliqueS = n2tc.get(t.s);
		Long sourceCliqueO = n2sc.get(t.o);
		Long targetCliqueO = n2tc.get(t.o);

		Long sourceCliqueP = p2sc.get(t.p);
		Long targetCliqueP = p2tc.get(t.p);

		Long repO = rep.get(t.o);
		Long repS = rep.get(t.s);

		boolean pRepresented = (sourceCliqueP != null);
		boolean sRepresented = (repS != null);
		boolean sTyped = (classSetS != null);
		boolean sSchemaNode = sn.contains(t.s);
		boolean oRepresented = (repO != null);
		boolean oTyped = (classSetO != null);
		boolean oSchemaNode = sn.contains(t.o);

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sSchemaNode, sTyped, pRepresented, oRepresented, oTyped, oSchemaNode);
		//LOGGER.debug("Case " + this.showCaseName(caseNumber));
		switch (caseNumber) {
			case SELF_SELF:
				handleDataTriple_SELF_SELF(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case SN_RP_RO:
			case TRS_RP_RO:
				handleDataTriple_TRS_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case SN_RP_UO:
			case TRS_RP_UO:
				handleDataTriple_TRS_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_RP_SN:
			case RS_RP_TRO:
				handleDataTriple_RS_RP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_RP_RO:
				handleDataTriple_RS_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_RP_UO:
				handleDataTriple_RS_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_RP_SN:
			case US_RP_TRO:
				handleDataTriple_US_RP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_RP_RO:
				handleDataTriple_US_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_RP_UO:
				handleDataTriple_US_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case SN_UP_RO:
			case TRS_UP_RO:
				handleDataTriple_TRS_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case SN_UP_UO:
			case TRS_UP_UO:
				handleDataTriple_TRS_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_UP_SN:
			case RS_UP_TRO:
				handleDataTriple_RS_UP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_UP_RO:
				handleDataTriple_RS_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_UP_UO:
				handleDataTriple_RS_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_UP_SN:
			case US_UP_TRO:
				handleDataTriple_US_UP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_UP_RO:
				handleDataTriple_US_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_UP_UO:
				handleDataTriple_US_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			default:
				throw new IllegalStateException("Unknown case;");
		}
		cacheTriple(t);
	}

	// -->
	// Debug methods
	// -->

	@Override
	protected String showCaseName(char caseNumber) { 
		switch (caseNumber) {
			case SELF_SELF:
				return "SELF_SELF";
			case SN_RP_RO:
				return "SN_RP_RO";
			case SN_RP_UO:
				return "SN_RP_UO";
			case SN_UP_RO:
				return "SN_UP_RO";
			case SN_UP_UO:
				return "SN_UP_UO";
			case RS_RP_SN:
				return "RS_RP_SN";
			case RS_RP_RO:
				return "RS_RP_RO";
			case RS_RP_UO:
				return "RS_RP_UO";
			case RS_UP_SN:
				return "RS_UP_SN";
			case RS_UP_RO:
				return "RS_UP_RO";
			case RS_UP_UO:
				return "RS_UP_UO";
			case US_RP_SN:
				return "US_RP_SN";
			case US_RP_RO:
				return "US_RP_RO";
			case US_RP_UO:
				return "US_RP_UO";
			case US_UP_SN:
				return "US_UP_SN";
			case US_UP_RO:
				return "US_UP_RO";
			case US_UP_UO:
				return "US_UP_UO";
			case TRS_RP_RO:
				return "TRS_RP_RO";
			case TRS_RP_UO:
				return "TRS_RP_UO";
			case TRS_UP_RO:
				return "TRS_UP_RO";
			case TRS_UP_UO:
				return "TRS_UP_UO";
			case RS_RP_TRO:
				return "RS_RP_TRO";
			case RS_UP_TRO:
				return "RS_UP_TRO";
			case US_UP_TRO:
				return "US_UP_TRO";
			case US_RP_TRO:
				return "US_RP_TRO";
			default:
				throw new IllegalStateException("Unrecognized case " + caseNumber);
		}
	}

	private String showLongSet(TreeSet<Long> s){
		StringBuffer sb = new StringBuffer();
		sb.append("{");
		for (Long e: s){
			sb.append(e).append(" ");
		}
		sb.append("}");
		return new String(sb);
	}

	private void displayClassSets() {
		for (TreeSet<Long> classSets: cs2csID.keySet()){
			StringBuilder thisCSBuffer = new StringBuilder();
			thisCSBuffer.append(showLongSet(classSets));
			thisCSBuffer.append("-->");
			thisCSBuffer.append(cs2csID.get(classSets));
			System.out.println(thisCSBuffer.toString());
		}
	}

	public void displayRepThroughCliques() {
		StringBuffer sb = new StringBuffer();
		sb.append("n2sc: ");
		for (Long node: n2sc.getKeys()) {
			if (node == null)
				throw new IllegalStateException("Null node");
			Long thisNodeSC = n2sc.get(node);
			if (thisNodeSC == null)
				throw new IllegalStateException("Null source clique");
			System.out.println("n2sc: " + node + "->" + thisNodeSC);
			Long thisNodeTC = n2tc.get(node);
			if (thisNodeTC == null)
				throw new IllegalStateException("Null target clique for " + node);
			if (untypedSummaryNodes == null)
				throw new IllegalStateException("Untyped summary nodes");
			if (untypedSummaryNodes.get(thisNodeSC) == null)
				throw new IllegalStateException("Unknown source clique " + thisNodeSC);
			Long summaryNode = untypedSummaryNodes.get(thisNodeSC).get(thisNodeTC);
			if (summaryNode == null)
				throw new IllegalStateException("Null summary node");
			sb.append(node).append("->").append(summaryNode).append(" ");
		}
		System.out.println(sb);
	}

	@Override
	public void display() {
		System.out.println("TYPED STRONG SUMMARY\nClass set IDs to class sets: " + cs.toString());
		System.out.println("Nodes to class set IDs: " + n2cs.toString());
		System.out.println("Source cliques: " + sc.toString());
		System.out.println("Target cliques: " + tc.toString());
		System.out.println("Nodes to source cliques: " + n2sc.toString());
		System.out.println("Nodes to target cliques: " + n2tc.toString());
		System.out.println("Property to source cliques: " + p2sc.toString());
		System.out.println("Property to target cliques: " + p2tc.toString());
		System.out.println("Representation function: ");
		System.out.println(showRep());
		System.out.println("Cs to cs ID: ");
		displayClassSets();
		System.out.println("Summary edges: ");
		edgesWithProv.display();
	}

}
