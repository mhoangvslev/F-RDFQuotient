package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.commons.miscellaneous.Debugger;
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

public class TypedStrongSummary extends StrongOrTypedStrongSummary {
	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set
	Long2Long n2cs; // for each node, its class set ID. This is also the rep function for typed nodes
	Long2LongSet n2c; // for each node, the set of types we know so far for this node
	HashMap<TreeSet<Long>, Long> cs2csID; // for each set of types known so far, the ID of that set

	public TypedStrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
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
	 * This must be used to read a TS summary from Postgres.
	 *
	 * @param conn
	 */
	public TypedStrongSummary(Connection conn) {
		this.conn = conn; 
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX;
		Debugger.log("Reading TypedStrong summary from Postgres, setting up special URIs from the dictionary");
		RDF2SQLEncoding.setUp(conn, "dictionary");
		String getSummaryTriples = getSummaryTriplesSQLQuery();
		try {
			Statement getTriples = conn.createStatement();
			// Debugger.log("Created statement");
			ResultSet rs = getTriples.executeQuery(getSummaryTriples);
			// Debugger.log("Asking for summary triples")
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
		String getTypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p =" + typeConstantCode);
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
						//Debugger.log("#### Data triple " + t.toString());
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
		if (classSetS != null) // TS (also represented)
			if (classSetO != null) // TO (also represented)
				return TS_TO;
			else // UO
				if (repO != null) // RO
					if (sourceCliqueP != null) // RP
						return TS_UO_RO_RP;
					else // NP
						return TS_UO_RO_NP;
				else // NO
					if (sourceCliqueP != null) // RP
						return TS_UO_NO_RP;
					else
						return TS_UO_NO_NP;
		else // US
			if (repS != null) // US, RS
				if (classSetO != null) // TO (also represented)
					if (sourceCliqueP != null)
						return US_RS_TO_RP;
					else
						return US_RS_TO_NP;
				else // US, RS, UO
					if (repO != null) // RO
						if (sourceCliqueP != null)
							return US_RS_UO_RO_RP;
						else
							return US_RS_UO_RO_NP;
					else // NO
						if (sourceCliqueP != null)
							return US_RS_UO_NO_RP;
						else
							return US_RS_UO_NO_NP;
			else // US, NS
				if (classSetO != null) // TO, also represented
					if (sourceCliqueP != null)
						return US_NS_TO_RP;
					else
						return US_NS_TO_NP;
				else // UO
					if (repO != null) // RO
						if (sourceCliqueP != null) // RP
							return US_NS_UO_RO_RP;
						else
							return US_NS_UO_RO_NP;
					else // NO
						if (sourceCliqueP != null) // RP
							return US_NS_UO_NO_RP;
						else
							return US_NS_UO_NO_NP;
	}

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
		// 18 cases: (TS, USR, USN) x (TO, UOR, UON) x (PR, PN) also multiplied by: which cliques are empty and their consequences on fusion
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

		//Debugger.log("Case " + this.caseName(caseNumber));
		switch (caseNumber) {
			case TS_TO: {
				handleDataTriple_TS_TO(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case TS_UO_RO_RP: {
				handleDataTriple_TS_UO_RO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case TS_UO_NO_RP: {
				handleDataTriple_TS_UO_NO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_TO_RP: {
				handleDataTriple_US_RS_TO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_UO_RO_RP: {
				handleDataTriple_US_RS_UO_RO_RP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_UO_NO_RP: {
				handleDataTriple_US_RS_UO_NO_RP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_TO_RP: {
				handleDataTriple_US_NS_TO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_UO_RO_RP: {
				handleDataTriple_US_NS_UO_RO_RP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_UO_NO_RP: {
				handleDataTriple_US_NS_UO_NO_RP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case TS_UO_RO_NP: {
				handleDataTriple_TS_UO_RO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case TS_UO_NO_NP: {
				handleDataTriple_TS_UO_NO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_TO_NP: {
				handleDataTriple_US_RS_TO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_UO_RO_NP: {
				handleDataTriple_US_RS_UO_RO_NP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_UO_NO_NP: {
				handleDataTriple_US_RS_UO_NO_NP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_TO_NP: {
				handleDataTriple_US_NS_TO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_UO_RO_NP: {
				handleDataTriple_US_NS_UO_RO_NP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_UO_NO_NP: {
				handleDataTriple_US_NS_UO_NO_NP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			default:
				throw new IllegalStateException("Unknown case;");
		}
		//this.writeToFileAndDraw();
	}

	// untyped, unrepresented subject
	// typed (thus represented) object
	// unknown property
	// copy-then-edit from StrongOrTypedStrong2.US_NS_UO_RO_NP
	private void handleDataTriple_US_NS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		
		Long scp = this.makeAndAddNewSourceClique(t.p);
		Long tcp = this.makeAndAddNewTargetClique(t.p);

		Long repS = this.getOrCreateSummaryNode(scp, this.getEmptyTargetCliqueID()); 
		Long repO = rep.get(t.o);

		// determine future cliques
		Long newSCs = scp; 
		// o does not get a target clique, because it is typed
		
		// determine future representatives: we create them but do nothing else so far
		Long newRepS = repS;
		// o does not get a target clique because of this triple, because o is typed
		
		boolean replaceForO = false; // o will not be replaced because it is represented according to its types  
		
		// no clique to modify or fuse because we had not seen S nor P before
		// no node replacement 
		// no split 
		
		// now modifying rep:
		rep.put(t.s, newRepS);

		// now fixing s and o's cliques
		n2sc.put(t.s, scp);
		n2tc.put(t.s, this.getEmptyTargetCliqueID()); 
		
		// adding the triple:
		edgesWithProv.addTriple(newRepS, t.p, repO);

	}

	// untyped, represented subject: it has a source clique, which needs to gain p
	// typed, represented object which won't change
	// unknown property
	private void handleDataTriple_US_RS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// sourceCliqueP is null, targetCliqueP is null
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long scp = this.makeAndAddNewSourceClique(t.p);
		Long tcp = this.makeAndAddNewTargetClique(t.p); 
		// determine future cliques
		Long newSCs = cliqueFusionResult(sourceCliqueS, scp, SOURCE);
		// no target clique for o which is typed
		
		// determine future representatives: we create them but do nothing else so far
		Long newRepS = getOrCreateSummaryNode(newSCs, targetCliqueS); 
		Long newRepO = repO; // o is typed

		boolean replaceForS = true; 
		boolean replaceForO = false;  
		if (sourceCliqueS.equals(this.getEmptySourceCliqueID())){ // due to the current triple, newSCS for sure is not empty. 
			if (rep.getInverse(repS).size()  > 1){ // other nodes were (and still are) on the empty scs and targetCliqueS.
				// In this case, we should not replace repS with newRepS, but only represent s by newRepS -- and keep repS! 
				// Also, we should not replace sourceCliqueS with newSC, but create newSCs and keep sourceCliqueS!
				replaceForS = false; 
			}
		}
		
		// really modify cliques (and do nothing else)
		if (replaceForS){
			fuseCliqueInto(sourceCliqueS, newSCs, SOURCE);
			fuseCliqueInto(scp, newSCs, SOURCE);
		}
		else{ }// if we are not replacing but splitting, scs was empty, the new clique of S is that of P, no clique creation is needed
		
		// compute node replacements:
		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)){
				nodeReps.add(new ReplacementSpecification(newSCs, targetCliqueS, repS, newRepS)); 
			}
		}
		// no replacement for/around repO
		
		for (ReplacementSpecification reps: nodeReps){
			untypedSummaryNodes.applyTargetedReplacement(reps);
		}
		// now compute and then apply the clique replacements in untyped, where they were still not applied
		// compute sourceCliqueReplacements and apply them: 
		if (replaceForS){ // compute: 
			computeAndApplyCliqueReplacements(sourceCliqueS, scp, newSCs, SOURCE); 
		} // else, nothing to do because newRepS is correctly inserted in untypedNodes, on its cliques
				
		// now modify summary edges
		for (ReplacementSpecification reps: nodeReps){
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode()); 
		}

		// now patching summary edges if needed
		if (!replaceForS){
			updateEdgesAfterSplit(distributeSummaryEdgesThroughCounts(repS, newRepS, t.s, TARGET), repS, newRepS, TARGET); 
		}
		// no patching/splitting for o, because it's typed

		// now modifying rep:
		rep.put(t.s, newRepS);

		// updating cliques of nodes: 
		n2sc.put(t.s, newSCs);
		
		// adding the triple:
		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject which won't change
	// untyped, unrepresented object
	// unknown property: both its cliques need to be created
	// edit-then-copy from StrongOrTypedStrongSummary2.RS_NO_NP
	private void handleDataTriple_TS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// p has no source clique so far, as we only saw it with a typed source.
		Long repS = rep.get(t.s);
		//System.out.println("US_RS_UO_NO_NP The subject " + t.s + " was represented by " + repS); 
		//System.out.println("US_RS_UO_NO_NP Upon starting, n2sc is: " + n2sc.writeToFileAndDraw()); 
		
		// p gets both cliques
		Long scp = this.makeAndAddNewSourceClique(t.p);
		Long tcp = this.makeAndAddNewTargetClique(t.p);
		
		Long repO = this.getOrCreateSummaryNode(this.getEmptySourceCliqueID(), tcp); 
		
		// determine future cliques
		Long newTCo = tcp; 
		// determine future representatives: we create them but do nothing else so far
		Long newRepS = repS; 
		Long newRepO = repO; 
		
		// we don't replace nor split for S because it's typed boolean replaceForS = false; 
		// no split for O because it was unknown (thus it did not have an empty target clique)
		// no replacement for O because it was unknown
		
		// no cliques to modify: S was typed and O was unknown
		// no node to replace: S was typed and O was unknown
		// no edges to modify
		// no patching for S 

		// now modifying rep:
		rep.put(t.o, newRepO);
		
		// now fixing o's cliques
		// no clique for S, because it's typed
		n2sc.put(t.o, this.getEmptySourceCliqueID());
		n2tc.put(t.o, newTCo);

		// adding the triple:
		edgesWithProv.addTriple(newRepS, t.p, newRepO);
		
	}

	// typed, represented subject won't change
	// untyped, represented object, with a target clique which needs to change as p was unknown
	// unknown property
	// copy-then-edit from RS_RO_NP
	private void handleDataTriple_TS_UO_RO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// p gets a new source clique as it was unknown, and its (typed) subject doesn't impact psc 
		// sourceCliqueP is null, targetCliqueP is null
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long scp = this.makeAndAddNewSourceClique(t.p);
		Long tcp = this.makeAndAddNewTargetClique(t.p); 
		// determine future cliques
		// S does not get a new source clique
		Long newTCo = cliqueFusionResult(targetCliqueO, tcp, TARGET); 
		
		// determine future representatives: we create them but do nothing else so far
		Long newRepS = repS; 
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTCo); 
		
		// no replace and no split for S
		boolean replaceForO = true; 
		if (targetCliqueO.equals(this.getEmptyTargetCliqueID())){
			if (rep.getInverse(repO).size()>1){
				replaceForO = false; 
			}
		}

		// really modify cliques (and do nothing else)
		if (replaceForO){
			fuseCliqueInto(targetCliqueO, newTCo, TARGET);
			fuseCliqueInto(tcp, newTCo, TARGET);
		}// otherwise do nothing

		
		// compute node replacements:
		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForO){
			if (!newRepO.equals(repO)){
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTCo, repO, newRepO); 
				repsO.checkForConflicts(nodeReps); 
				nodeReps.add(repsO); 
			}
		}
		// now we replace just the nodes in untyped (not the cliques yet), because the nodes are at the lowest (value) level
		for (ReplacementSpecification reps: nodeReps){
			untypedSummaryNodes.applyTargetedReplacement(reps);
		}
		// now compute and then apply the clique replacements in untyped, where they were still not applied
		if (replaceForO){ 
			computeAndApplyCliqueReplacements(targetCliqueO, tcp, newTCo, TARGET); 
		}
		// now modify summary edges
		for (ReplacementSpecification reps: nodeReps){
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode()); 
		}

		// now patching summary edges if needed
		if (!replaceForO){
			updateEdgesAfterSplit(distributeSummaryEdgesThroughCounts(repO, newRepO, t.o, SOURCE), repO, newRepO, SOURCE); 
		}

		// now modifying rep:
		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO); 
		
		// now fixing o's cliques
		n2tc.put(t.o, newTCo);
		
		// adding the triple:
		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// typed, represented object
	// known property
	// copy-then-edit from US_RO_RP
	private void handleDataTriple_US_NS_TO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long repS = this.getOrCreateSummaryNode(sourceCliqueP, this.getEmptyTargetCliqueID()); 
		Long repO = rep.get(t.o);

		// determine future cliques
		Long newSCs = sourceCliqueP; 
		// O does not get a target clique
		
		// determine future representatives: we create them but do nothing else so far
		Long newRepS = repS;
		
		// no replace nor split for S as it was unknown
		// no replace nor split for O as it was typed 

		// no clique modifications
		// no node replacement
		// now modify summary edges
		// no patching 
		// now modifying rep:
		rep.put(t.s, newRepS);
		// now fixing s' cliques
		n2sc.put(t.s, newSCs);
		n2tc.put(t.s, this.getEmptyTargetCliqueID()); 
		
		// adding the triple:
		edgesWithProv.addTriple(newRepS, t.p, repO);
	}

	// untyped, represented subject
	// typed, represented object
	// represented propert
	// copy-then-edit from RS_RO_RP
	private void handleDataTriple_US_RS_TO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// the target clique of p does not change because o is typed
		// the representative of o does not change because o is typed
		// the representative of s may have to change if the source clique of s did not contain p
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		// determine future cliques
		Long newSCs = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);
		
		// determine future representatives: we create them but do nothing else so far
		Long newRepS = getOrCreateSummaryNode(newSCs, targetCliqueS); 
		
		boolean replaceForS = true; 
		// no replace nor split for O 
		if (sourceCliqueS.equals(this.getEmptySourceCliqueID())){ // due to the current triple, newSCS for sure is not empty. 
			if (rep.getInverse(repS).size()  > 1){ // other nodes were (and still are) on the empty scs and targetCliqueS.
				// In this case, we should not replace repS with newRepS, but only represent s by newRepS -- and keep repS! 
				// Also, we should not replace sourceCliqueS with newSC, but create newSCs and keep sourceCliqueS!
				replaceForS = false; 
			}
		}
		// really modify cliques (and do nothing else)
		if (replaceForS){
			fuseCliqueInto(sourceCliqueS, newSCs, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSCs, SOURCE);
		}
		else{ }// if we are not replacing but splitting, scs was empty, the new clique of S is that of P, no clique creation is needed
		
		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)){
				nodeReps.add(new ReplacementSpecification(newSCs, targetCliqueS, repS, newRepS)); 
			}
		}

		for (ReplacementSpecification reps: nodeReps){
			untypedSummaryNodes.applyTargetedReplacement(reps);
		}
		// if repS and/or repO did not need to be replaced (becase scs and/or tco were empty), there is nothing to do at this stage,
		// compute sourceCliqueReplacements and apply them: 
		if (replaceForS){ // compute: 
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSCs, SOURCE); 
		} // else, nothing to do because newRepS is correctly inserted in untypedNodes, on its cliques
		
		// now modify summary edges
		for (ReplacementSpecification reps: nodeReps){
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode()); 
		}

		// now patching summary edges if needed
		if (!replaceForS){
			updateEdgesAfterSplit(distributeSummaryEdgesThroughCounts(repS, newRepS, t.s, TARGET), repS, newRepS, TARGET); 
		}
		
		// now modifying rep:
		rep.put(t.s, newRepS);
		// now fixing s and o's cliques
		n2sc.put(t.s, newSCs);

		// adding the triple:
		edgesWithProv.addTriple(newRepS, t.p, repO);
	}

	// typed, represented subject
	// untyped, unrepresented object
	// known property
	// copy-then-edit from RS_NO_RP
	private void handleDataTriple_TS_UO_NO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		
		Long repS = rep.get(t.s);
		Long repO = this.getOrCreateSummaryNode(this.getEmptySourceCliqueID(), targetCliqueP); 

		// determine future cliques
		// S does not get a source clique because it's typed 
		Long newTCo = targetCliqueP; 

		// no replace nor split for S (typed)
		// no replace nor split for O (new) 
		// no clique modifications
		// no edge modifications 
		// no node replacement
		// no edge modification 
		// now modify summary edges
		// now modifying rep:
		rep.put(t.o, repO); 

		// now fixing s and o's cliques
		n2sc.put(t.o, this.getEmptySourceCliqueID()); 
		n2tc.put(t.o, newTCo);

		// adding the triple:
		edgesWithProv.addTriple(repS, t.p, repO);
	}
	

	// typed, represented subject
	// untyped, represented object
	// represented property
	// we need to unify the target clique of O with the target clique of P
	// copy-then-edit from RS_RO_RP
	private void handleDataTriple_TS_UO_RO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		// determine future cliques
		Long newTCo = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET); 

		// determine future representatives: we create them but do nothing else so far
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTCo); 

		boolean replaceForO = true; 
		if (targetCliqueO.equals(this.getEmptyTargetCliqueID())){
			if (rep.getInverse(repO).size()>1){
				replaceForO = false; 
			}
		}

		// really modify cliques (and do nothing else)
		if (replaceForO){
			fuseCliqueInto(targetCliqueO, newTCo, TARGET);
			fuseCliqueInto(targetCliqueP, newTCo, TARGET);
		}// otherwise do nothing
		
		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForO){
			if (!newRepO.equals(repO)){
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTCo, repO, newRepO); 
				repsO.checkForConflicts(nodeReps); 
				nodeReps.add(repsO); 
			}
		}
		
		for (ReplacementSpecification reps: nodeReps){
			untypedSummaryNodes.applyTargetedReplacement(reps);
		}
		// if repS and/or repO did not need to be replaced (becase scs and/or tco were empty), there is nothing to do at this stage,
		// because newRepS resp. newRepO are already well inserted in untyped, on their respective cliques
		
		// now compute and then apply the clique replacements in untyped, where they were still not applied
		if (replaceForO){ 
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTCo, TARGET); 
		}

		// now modify summary edges
		// apply nodeReplacements in all cases, because it only contains replacements that should be made;
		// e.g., if replaceForS is false but replaceForO is true, it contains those node replacements that are needed because of O, and
		// will replace nothing wrongly around s
		for (ReplacementSpecification reps: nodeReps){
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode()); 
		}

		// now patching summary edges if needed
		if (!replaceForO){
			updateEdgesAfterSplit(distributeSummaryEdgesThroughCounts(repO, newRepO, t.o, SOURCE), repO, newRepO, SOURCE); 
		}

		// now modifying rep:
		rep.put(t.o, newRepO); 

		// now fixing s and o's cliques
		n2tc.put(t.o, newTCo);

		// adding the triple:
		edgesWithProv.addTriple(repS, t.p, newRepO);
	}

	private void handleDataTriple_TS_TO(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
										Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// add the edge to the summary
		edgesWithProv.addTriple(classSetS, t.p, classSetO);
	}
}
