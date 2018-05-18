package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
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
		numberOfDataTriplesRead = 0;
		numberOfTypeTriplesRead = 0;
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX;
		isTypeFirst = true;
	}

	/**
	 * this must be called after the constructor as the summary needs to ask more queries
	 * for patching itself up during summarization.
	 * 
	 * @param conn
	 */
	public void setConn(Connection conn) {
		this.conn = conn; 
	}

	/**
	 * This must be used to read a TRS summary from Postgres.
	 *
	 * @param conn
	 */
	public TypedStrongSummary(Connection conn) {
		this.conn = conn; 
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX;
		LOGGER.info("Reading TypedStrong summary from Postgres, setting up special URIs from the dictionary");
		RDF2SQLEncoding.setUp(conn, "dictionary");
		String getSummaryTriples = getSummaryTriplesSQLQuery();
		try {
			Statement getTriples = conn.createStatement();
			// LOGGER.debug("Created statement");
			ResultSet rs = getTriples.executeQuery(getSummaryTriples);
			// LOGGER.debug("Asking for summary triples")
			while (rs.next()) {
				Long s = rs.getLong(1);
				Long p = rs.getLong(2);
				Long o = rs.getLong(3);
				edgesWithProv.addTriple(s, p, o);
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to read Typed Strong summary from Postgres: " + e.toString());
		}
		System.out.println("Read Typed Strong summary from Postgres");
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn) {
		this.setConn(conn);
		long start = System.currentTimeMillis();

		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true;
			avoidCollisionsWhenAssigningSummaryNodes(conn);
		}
		triplesSummarizedSoFar = 0;
		String getTypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						this.handleTypeTripleBeforeData(t);
						triplesSummarizedSoFar++;
						this.numberOfTypeTriplesRead++;
						storeSpecialNodesRepresentation(t, false);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		classSetCreationTime = System.currentTimeMillis() - start;
		System.out.println("Class sets created in " + classSetCreationTime + " ms");

		start = System.currentTimeMillis();
		this.postHandleTypeTriples();
		typeTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + numberOfTypeTriplesRead + " type triples in " + typeTriplesSummarizationTime + " ms");

		start = System.currentTimeMillis();

		//this.drawSummaryAndGraph(conn, "_" + triplesSummarizedSoFar);
		//this.writeToFileAndDraw();
		// now all the non-type triples
		String getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			conn.setAutoCommit(false);
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						//LOGGER.debug("#### Data triple " + t.toString());
						if ((t.p == RDF2SQLEncoding.getSubClassCode())
						|| (t.p == RDF2SQLEncoding.getSubPropertyCode())
						|| (t.p == RDF2SQLEncoding.getDomainCode())
						|| (t.p == RDF2SQLEncoding.getRangeCode())) {
							edgesWithProv.addTriple(t.s, t.p, t.o);
							storeSpecialNodesRepresentation(t, true);
						}
						else
							handleDataTriple(t);
						triplesSummarizedSoFar++;
						this.numberOfDataTriplesRead++;
						//this.drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
						//this.roundTripConsistencyCheck(); 
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		dataTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + numberOfDataTriplesRead + " data triples in " + dataTriplesSummarizationTime + " ms");

		allTriplesSummarizationTime = classSetCreationTime + typeTriplesSummarizationTime + dataTriplesSummarizationTime;
		System.out.println("Summarized " + triplesSummarizedSoFar + " triples overall in " + allTriplesSummarizationTime + " ms");
		//this.writeToFileAndDraw(dataTriplesFileName);
	}

	private char decode(Long classSetS, Long repS, Long classSetO, Long repO, Long sourceCliqueP) {
		if (classSetS != null) // TRS (also represented)
			if (classSetO != null) // TRO (also represented)
				return TRS_TRO;
			else // UO
				if (repO != null) // RO
					if (sourceCliqueP != null) // RP
						return TRS_RP_RO;
					else // NP
						return TRS_UP_RO;
				else // NO
					if (sourceCliqueP != null) // RP
						return TRS_RP_UO;
					else
						return TRS_UP_UO;
		else // US
			if (repS != null) // US, RS
				if (classSetO != null) // TRO (also represented)
					if (sourceCliqueP != null)
						return RS_RP_TRO;
					else
						return RS_UP_TRO;
				else // US, RS, UO
					if (repO != null) // RO
						if (sourceCliqueP != null)
							return RS_RP_RO;
						else
							return RS_UP_RO;
					else // NO
						if (sourceCliqueP != null)
							return RS_RP_UO;
						else
							return RS_UP_UO;
			else // US, NS
				if (classSetO != null) // TRO, also represented
					if (sourceCliqueP != null)
						return US_RP_TRO;
					else
						return US_UP_TRO;
				else // UO
					if (repO != null) // RO
						if (sourceCliqueP != null) // RP
							return US_RP_RO;
						else
							return US_UP_RO;
					else // NO
						if (sourceCliqueP != null) // RP
							return US_RP_UO;
						else
							return US_UP_UO;
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
		showRep();
		System.out.println("Cs to cs ID: ");
		showClassSets();
		System.out.println("Summary edges: ");
		edgesWithProv.display();
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
	private void showClassSets() {
		for (TreeSet<Long> cs: cs2csID.keySet()){
			StringBuilder thisCSBuffer = new StringBuilder();
			thisCSBuffer.append(showLongSet(cs));
			thisCSBuffer.append("-->");
			thisCSBuffer.append(cs2csID.get(cs));
			System.out.println(thisCSBuffer.toString());
		}
	}

	public void showRepThroughCliques() {
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
	public void handleTypeTripleBeforeData(Triple t) {
		//System.out.println("@@@ Type triple: " + t.toString()); 

		TreeSet<Long> classSetOfThisNode = n2c.get(t.s); 
		if (classSetOfThisNode == null){ // this is the first time we encounter the node: create a class set with exactly this type
			Long newClassSetID = this.getNextSummaryNode(); 
			classSetOfThisNode = new TreeSet<>();
			classSetOfThisNode.add(t.o); 
			n2c.put(t.s, classSetOfThisNode);
			cs.put(newClassSetID, classSetOfThisNode);
			cs2csID.put(classSetOfThisNode, newClassSetID);
			n2cs.put(t.s, newClassSetID);
		}
		else{ // we already had some types for t.s
			if (classSetOfThisNode.contains(t.o)){
				// do nothing
			}
			else {
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
		//display(); 
	}

	/**
	 * This method adds the type triples in the summary, based on the structures previously filled in while traversing those triples.
	 * It is called only once and will output all the type triples of the summary.
	 */
	public void postHandleTypeTriples() {
		//System.out.println("POST HANDLE TYPE TRIPLES");
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

		//TODO comment this out to improve performance when debugging is finished
		//checkSymmetry(sourceCliqueS, targetCliqueS, sourceCliqueO, targetCliqueO, sourceCliqueP, targetCliqueP); 
		char caseNumber = decode(classSetS, repS, classSetO, repO, sourceCliqueP);

		//LOGGER.debug("Case " + this.caseName(caseNumber));
		switch (caseNumber) {
			case TRS_TRO: {
				handleDataTriple_TRS_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case TRS_RP_RO: {
				handleDataTriple_TRS_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case TRS_RP_UO: {
				handleDataTriple_TRS_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_RP_TRO: {
				handleDataTriple_RS_RP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_RP_RO: {
				handleDataTriple_RS_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_RP_UO: {
				handleDataTriple_RS_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RP_TRO: {
				handleDataTriple_US_RP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RP_RO: {
				handleDataTriple_US_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RP_UO: {
				handleDataTriple_US_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case TRS_UP_RO: {
				handleDataTriple_TRS_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case TRS_UP_UO: {
				handleDataTriple_TRS_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_UP_TRO: {
				handleDataTriple_RS_UP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_UP_RO: {
				handleDataTriple_RS_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_UP_UO: {
				handleDataTriple_RS_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_UP_TRO: {
				handleDataTriple_US_UP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_UP_RO: {
				handleDataTriple_US_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_UP_UO: {
				handleDataTriple_US_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			default:
				throw new IllegalStateException("Unknown case;");
		}
		//this.writeToFileAndDraw();
	}

	protected void handleDataTriple_TRS_TRO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		edgesWithProv.addTriple(repS, t.p, repO);
	}

	// untyped, represented subject
	// represented property
	// typed, represented object
	protected void handleDataTriple_RS_RP_TRO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, targetCliqueS);
		Long newRepO = repO;

		boolean replaceForS = true;
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			if (rep.getInverse(repS).size() > 1) {
				replaceForS = false;
			}
		}

		if (replaceForS) {
			fuseCliqueInto(sourceCliqueS, newSourceCliqueS, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSourceCliqueS, SOURCE);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)) {
				ReplacementSpecification repsS = new ReplacementSpecification(newSourceCliqueS, targetCliqueS, repS, newRepS);
				nodeReps.add(repsS);
			}
		}

		for (ReplacementSpecification reps: nodeReps) {
			untypedSummaryNodes.applyTargetedReplacement(reps);
		}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE);
		}

		if (!replaceForS) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repS, newRepS, t.s, TARGET), repS, newRepS, TARGET);
		}

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);

		n2sc.put(t.s, newSourceCliqueS);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject
	// represented property
	// untyped, represented object
	// we need to unify the target clique of O with the target clique of P
	protected void handleDataTriple_TRS_RP_RO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTargetCliqueO);

		boolean replaceForO = true;
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
			}
		}

		if (replaceForO) {
			fuseCliqueInto(targetCliqueO, newTargetCliqueO, TARGET);
			fuseCliqueInto(targetCliqueP, newTargetCliqueO, TARGET);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForO) {
			if (!newRepO.equals(repO)) {
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTargetCliqueO, repO, newRepO);
				nodeReps.add(repsO);
			}
		}

		for (ReplacementSpecification reps: nodeReps) {
			untypedSummaryNodes.applyTargetedReplacement(reps);
		}

		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET);
		}

		if (!replaceForO) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repO, newRepO, t.o, SOURCE), repO, newRepO, SOURCE);
		}

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.o, newRepO);

		n2tc.put(t.o, newTargetCliqueO);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, represented subject: it has a source clique, which needs to gain p
	// unknown property
	// typed, represented object which won't change
	protected void handleDataTriple_RS_UP_TRO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, targetCliqueS);
		Long newRepO = repO;

		boolean replaceForS = true;
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			if (rep.getInverse(repS).size() > 1) {
				replaceForS = false;
			}
		}

		if (replaceForS) {
			fuseCliqueInto(sourceCliqueS, newSourceCliqueS, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSourceCliqueS, SOURCE);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)) {
				ReplacementSpecification repsS = new ReplacementSpecification(newSourceCliqueS, targetCliqueS, repS, newRepS);
				nodeReps.add(repsS);
			}
		}

		for (ReplacementSpecification reps: nodeReps) {
			untypedSummaryNodes.applyTargetedReplacement(reps);
		}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE);
		}

		if (!replaceForS) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repS, newRepS, t.s, TARGET), repS, newRepS, TARGET);
		}

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);

		n2sc.put(t.s, newSourceCliqueS);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject won't change
	// unknown property
	// untyped, represented object, with a target clique which needs to change as p was unknown
	protected void handleDataTriple_TRS_UP_RO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTargetCliqueO);

		boolean replaceForO = true;
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
			}
		}

		if (replaceForO) {
			fuseCliqueInto(targetCliqueO, newTargetCliqueO, TARGET);
			fuseCliqueInto(targetCliqueP, newTargetCliqueO, TARGET);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForO) {
			if (!newRepO.equals(repO)) {
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTargetCliqueO, repO, newRepO);
				nodeReps.add(repsO);
			}
		}

		for (ReplacementSpecification reps: nodeReps) {
			untypedSummaryNodes.applyTargetedReplacement(reps);
		}

		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET);
		}

		if (!replaceForO) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repO, newRepO, t.o, SOURCE), repO, newRepO, SOURCE);
		}

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.o, newRepO);

		n2tc.put(t.o, newTargetCliqueO);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// known property
	// typed, represented object
	protected void handleDataTriple_US_RP_TRO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long newSourceCliqueS = sourceCliqueP;

		Long repS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID());
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.s, newRepS);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.s, getEmptyTargetCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject
	// known property
	// untyped, unrepresented object
	protected void handleDataTriple_TRS_RP_UO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long newTargetCliqueO = targetCliqueP;

		Long repS = rep.get(t.s);
		Long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.o, newRepO);

		n2tc.put(t.o, newTargetCliqueO);
		n2sc.put(t.o, getEmptySourceCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// unknown property
	// typed (thus represented) object
	protected void handleDataTriple_US_UP_TRO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = sourceCliqueP;

		Long repS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID());
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.s, newRepS);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.s, getEmptyTargetCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject which won't change
	// unknown property: both its cliques need to be created
	// untyped, unrepresented object
	protected void handleDataTriple_TRS_UP_UO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newTargetCliqueO = targetCliqueP;

		Long repS = rep.get(t.s);
		Long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.o, newRepO);

		n2tc.put(t.o, newTargetCliqueO);
		n2sc.put(t.o, getEmptySourceCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}
}
