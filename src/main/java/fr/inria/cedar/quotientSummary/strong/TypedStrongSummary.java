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
import java.util.HashMap;
import java.util.TreeSet;

public class TypedStrongSummary extends StrongOrTypedStrongSummary {
	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set
	Long2Long n2cs; // for each node, its class set ID. This is also the rep function for typed nodes
	Long2LongSet n2c; // for each node, the set of types we know so far for this node
	HashMap<TreeSet<Long>, Long> cs2csID; // for each set of types known so far, the ID of that set
	
	public TypedStrongSummary() {
		super();
		cs = new Long2LongSet();
		n2sc = new Long2Long();
		rep = new Long2Long();
		n2cs = new Long2Long();
		
		n2c = new Long2LongSet();
		cs2csID = new HashMap<TreeSet<Long>, Long>();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE;
		numberOfDataTriplesRead = 0;
		numberOfTypeTriplesRead = 0;
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX;
	}
	
	/**
	 * this must be called after the constructor as the summary needs to ask more queries
	 * for patching itself up during summarization.
	 * 
	 * @param conn
	 */
	public void setConn(Connection conn){
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
		RDF2SQLEncoding.setUp(conn);
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
				this.addTriple(s, p, o);
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to read Typed Strong summary from Postgres " + e.getStackTrace());
		}
		System.out.println("Read Typed Strong summary from Postgres");
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 * @param args
	 */
	@Override
	public void summarizeFromRDBMS(Connection conn, String[] args) {
		this.setConn(conn);
		//Debugger.setFlag(true);
		long start = System.currentTimeMillis();
		String dataTriplesFileName = args[0];
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn);
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true;
			avoidCollisionsWhenAssigningSummaryNodes(conn);
		}

		//System.out.println("TypedStrong: Looking for type triples");
		triplesSummarizedSoFar = 0;
		String getTypedTriplesString = ("select *  from encoded_triples where p=" + typeConstantCode);
		try {
			Statement getTypedTriples = conn.createStatement();
			getTypedTriples.setFetchSize(1000);
			ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString);
			while (rs.next()) {
				Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
				//Debugger.log("### Type triple " + t.toString());
				this.handleTypeTripleBeforeData(t);
				triplesSummarizedSoFar++;
				this.numberOfTypeTriplesRead++;
			}
			rs.close();
			getTypedTriples.close();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		classSetCreationTime = System.currentTimeMillis() - start;
		System.out.println("Class sets created in " + classSetCreationTime + " ms.");

		start = System.currentTimeMillis();
		this.postHandleTypeTriples();
		typeTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + numberOfTypeTriplesRead + " type triples in " + typeTriplesSummarizationTime + " ms.");

		start = System.currentTimeMillis();

		//this.drawSummaryAndGraph(conn, dataTriplesFileName, ("_" + triplesSummarizedSoFar));
		//this.display();
		// now all the non-type triples
		String getUntypedTriplesString = ("select *  from encoded_triples where p <> " + typeConstantCode);
		try {
			conn.setAutoCommit(false);
			Statement getUntypedTriples = conn.createStatement();
			getUntypedTriples.setFetchSize(10000);
			ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString);
			while (rs.next()) {
				Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
				//Debugger.log("#### Data triple " + t.toString());
				if ((t.p == RDF2SQLEncoding.getSubClassCode())
					|| (t.p == RDF2SQLEncoding.getSubPropertyCode())
					|| (t.p == RDF2SQLEncoding.getDomainCode())
					|| (t.p == RDF2SQLEncoding.getRangeCode()))
					addTriple(t.s, t.p, t.o);
				else
					handleDataTriple(t);
				triplesSummarizedSoFar++;
				this.numberOfDataTriplesRead++;
				//this.drawSummaryAndGraph(conn, dataTriplesFileName, ("_" + triplesSummarizedSoFar));

			}
			rs.close();
			getUntypedTriples.close();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		dataTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + numberOfDataTriplesRead + " data triples in " + dataTriplesSummarizationTime + " ms.");

		allTriplesSummarizationTime = classSetCreationTime + typeTriplesSummarizationTime + dataTriplesSummarizationTime;
		System.out.println("Summarized " + triplesSummarizedSoFar + " triples overall in " + allTriplesSummarizationTime + " ms.");
		this.display(dataTriplesFileName);
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

	/**
	 * Paranoid method for safety check. Throws an error is something is not coherent across the
	 * data structures.
	 */
	public void cliqueSafetyCheck() {
		if (n2sc.getKeys().size() != n2tc.getKeys().size())
			throw new IllegalStateException("n2sc has " + n2sc.getKeys().size() + " while n2tc has "
											+ n2tc.getKeys().size() + " entries");
		if (n2sc.getKeys().size() != rep.getKeys().size())
			throw new IllegalStateException("n2sc has " + n2sc.getKeys().size() + " while rep has "
											+ rep.getKeys().size() + " entries");
		if (rep.getKeys().size() != n2tc.getKeys().size())
			throw new IllegalStateException("rep has " + rep.getKeys().size() + " while n2tc has "
											+ n2tc.getKeys().size() + " entries");
		if (p2sc.getKeys().size() != p2tc.getKeys().size()) {
			display();
			throw new IllegalStateException("After " + numberOfDataTriplesRead + " data triples, "
											+ p2sc.getKeys().size() + " properties have source cliques while "
											+ p2tc.getKeys().size() + " properties have target cliques ");
		}
		// there is no reason why numbers of source cliques should be equal to numbers of target cliques
		//
		// The number of source and target clique in untypedSummaryNodes may be less than those in p2tc, p2sc, n2tc, n2sc.
		// This is because typed nodes may be source or target of a data property and in this case, a source (target) clique is created for the data property, but is not associated to any node,
		// as typed nodes do not have source/target cliques.
	}

	private long countDistinctTargetCliquesInCliqueToNodesMap() {
		TreeSet<Long> uniqueTCs = new TreeSet<>();
		for (Long sourceClique: untypedSummaryNodes.keySet()) {
			HashMap<Long, Long> map = untypedSummaryNodes.get(sourceClique);
			for (Long targetClique: map.keySet()) {
				if (targetClique == this.emptyTCCount)
					continue; // not counting the empty tc because it does not appear in p2tc
				if (!uniqueTCs.contains(targetClique))
					uniqueTCs.add(targetClique);
			}
		}
		return uniqueTCs.size();
	}

	private long countDistinctSourceCliquesInCliqueToNodesMap() {
		return this.untypedSummaryNodes.keySet().size(); // this does not count the empty sc
	}

	public void display() {
		System.out.println("TYPED STRONG SUMMARY\nClass set IDs to class sets: " + cs.display());
		System.out.println("Nodes to class set IDs: " + n2cs.display());
		System.out.println("Source cliques: " + sc.display());
		System.out.println("Target cliques: " + tc.display());
		System.out.println("Nodes to source cliques: " + n2sc.display());
		System.out.println("Nodes to target cliques: " + n2tc.display());
		System.out.println("Property to source cliques: " + p2sc.display());
		System.out.println("Property to target cliques: " + p2tc.display());
		System.out.println("Representation function: ");
		showRep();
		System.out.println("Cs to cs ID: ");
		showClassSets();
		System.out.println("Summary: ");
		for (Triple t: this.getSummaryEdges())
			t.display();
	}

	private String showLongSet(TreeSet<Long> s){
		StringBuffer sb = new StringBuffer();
		sb.append("{");
		for (Long e: s){
			sb.append(e + " ");
		}
		sb.append("}");
		return new String(sb);
	}
	private void showClassSets() {
		for (TreeSet<Long> cs: cs2csID.keySet()){
			StringBuffer thisCSBuffer = new StringBuffer();
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
			classSetOfThisNode = new TreeSet<Long>();
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
			else{				
				TreeSet<Long> newClassSetOfThisNode = new TreeSet<Long>();
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
				this.addTriple(thisClassSetID, RDF2SQLEncoding.getTypeCode(), thisClass);
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
		//this.display();
	}

	// untyped, unrepresented subject
	// typed (thus represented) object
	// unknown property
	private void handleDataTriple_US_NS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// create p's source clique
		long psc = makeAndAddNewSourceClique(t.p);
		// represent s by the source clique of p and the empty target clique:
		long emptyTargetCliqueID = getEmptyTargetCliqueID();
		long repS = getOrCreateSummaryNode(psc, emptyTargetCliqueID);
		rep.put(t.s, repS);
		n2sc.put(t.s, psc);
		n2tc.put(t.s, emptyTargetCliqueID);
		// the target clique of p needs to be created and initialized with p alone
		// because now that we have seen p, we cannot give it just a source clique
		makeAndAddNewSourceClique(t.p);
		this.addTriple(repS, t.p, rep.get(t.o));
	}

	// untyped, represented subject: it has a source clique, which needs to gain p
	// typed, represented object which won't change
	// unknown property
	private void handleDataTriple_US_RS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long ssc = n2sc.get(t.s);
		Long repS = rep.get(t.s); 
		Long newRepS = repS; 
		Long newSourceClique = addPropertyToSourceClique(t.p, ssc);//TODO check correctness: what if s had an empty source clique and it needs to split?
		if (!ssc.equals(newSourceClique)){
			newRepS = this.replaceAndMaybeSplitUntypedSummaryNodes(t.s, newSourceClique, SOURCE); 
			if (!repS.equals(newRepS)){
				//this.changeRepresentationOfInto(t.s, newRepS);
				rep.put(t.s, newRepS);
			}
		}
		n2sc.put(t.s, newSourceClique); // this line should stay after the call to Split...
		makeAndAddNewSourceClique(t.p);
		// no representatives will be changed; the cliques of the source node don't change either
		this.addTriple(newRepS, t.p, rep.get(t.o));
	}

	// typed, represented subject which won't change
	// untyped, unrepresented object
	// unknown property: both its cliques need to be created
	private void handleDataTriple_TS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// p has no source clique so far, as we only saw it with a typed source.
		long ptc = makeAndAddNewTargetClique(t.p);
		// cliques of t.o: 
		n2tc.put(t.o, ptc);
		n2sc.put(t.o, getEmptySourceCliqueID());
		// represent t.o for the first time: 
		long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), ptc);
		rep.put(t.o, repO);
		// add triple:
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));

	}

	// typed, represented subject won't change
	// untyped, represented object, with a target clique which needs to change as p was unknown
	// unknown property
	private void handleDataTriple_TS_UO_RO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// p gets a new source clique as it was unknown, and its (typed) subject doesn't impact psc 
		makeAndAddNewSourceClique(t.p);
		// adding p to o's target clique
		Long newTargetCliqueO = addPropertyToTargetClique(t.p, targetCliqueO);
		Long repO = rep.get(t.o); 
		if ((targetCliqueO.equals(this.getEmptyTargetCliqueID())) && (!targetCliqueO.equals(newTargetCliqueO))){
			repO = this.replaceAndMaybeSplitUntypedSummaryNodes(t.o, newTargetCliqueO, TARGET); 
		}
		p2tc.put(t.p, newTargetCliqueO); // this should stay after the call to Split...
		// node representatives do not change:
		// add triple: 
		this.addTriple(rep.get(t.s), t.p, repO);
	}

	// untyped, unrepresented subject
	// typed, represented object
	// known property
	private void handleDataTriple_US_NS_TO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		helper_US_NS_RP(t, sourceCliqueP, targetCliqueP);
		// the source clique of p does not change because this subject has no other properties so far
		// the target clique of p does not change because this object is typed
		// adding triple:
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));
	}

	// untyped, represented subject
	// typed, represented object
	// represented property
	private void handleDataTriple_US_RS_TO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// the target clique of p does not change because o is typed
		// the representative of o does not change because o is typed
		// the representative of s may have to change if the source clique of s did not contain p
		Long repS = rep.get(t.s);
		Long newRepS = repS; 
		if (sourceCliqueS != sourceCliqueP) {
			Long fusedSCs = this.fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
			newRepS = getOrCreateSummaryNode(fusedSCs, sourceCliqueP);
			if (!repS.equals(newRepS)){
				//this.changeRepresentationOfInto(t.s, newRepS);
				rep.put(t.s, newRepS);
			}
			n2tc.put(t.s, targetCliqueS);
			n2sc.put(t.s, fusedSCs);
		}
		else {
			// nothing
		}
		// adding triple:
		this.addTriple(newRepS, t.p, rep.get(t.o));
	}

	// typed, represented subject
	// untyped, unrepresented object
	// known property
	private void handleDataTriple_TS_UO_NO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// represent o based on p: 
		this.helper_UO_NO_RP(t, sourceCliqueP, targetCliqueP);
		// the cliques of p and o will remain unchanged because o is typed and s was unknown
		// (thus only has p as far as we know)
		// add triple:
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));

	}

	// typed, represented subject
	// untyped, represented object
	// represented property
	// we need to unify the target clique of O with the target clique of P
	private void handleDataTriple_TS_UO_RO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long newTCo = targetCliqueO;
		Long newRepO = repO;
		if (targetCliqueO != targetCliqueP) {
			//Debugger.log("Fusing target clique O: " + targetCliqueO + " with target clique P: " + targetCliqueP);
			//this.showClique(tc.get(targetCliqueO));
			//this.showClique(tc.get(targetCliqueP));
			//Debugger.log("Empty target clique is: " + this.getEmptyTargetCliqueID());
			newTCo = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET);
			newRepO = getOrCreateSummaryNode(sourceCliqueO, newTCo);
			if (!repO.equals(newRepO)){
				//this.changeRepresentationOfInto(t.o, newRepO);
				rep.put(t.o, newRepO);
			}
			n2tc.put(newRepO, newTCo);
			n2sc.put(newRepO, sourceCliqueO);
		}
		// adding triple:
		if (repS == null)
			throw new IllegalStateException("repS");
		if (rep.get(t.o) == null)
			throw new IllegalStateException("repO");
		this.addTriple(repS, t.p, newRepO);
	}

	private void handleDataTriple_TS_TO(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
										Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// add the edge to the summary
		this.addTriple(classSetS, t.p, classSetO);
	}

	private void checkSymmetry(Long sourceCliqueS, Long targetCliqueS, Long sourceCliqueO, Long targetCliqueO,
							   Long sourceCliqueP, Long targetCliqueP) {
		if ((sourceCliqueS == null && targetCliqueS != null) || (sourceCliqueS != null && targetCliqueS == null))
			throw new Error("Subject has only one of the two cliques");
		if ((sourceCliqueO == null && targetCliqueO != null) || (sourceCliqueO != null && targetCliqueO == null))
			throw new Error("Object has only one of the two cliques");
		if ((sourceCliqueP == null && targetCliqueP != null) || (sourceCliqueP != null && targetCliqueP == null))
			throw new Error("Property has only one of the two cliques");
	}
	
	/**
	 * This is used only when drawing the graph using Dot. 
	 * Different summaries need to traverse their triples in different orders, thus the two cursors which differ between the typed and untyped summaries.
	 * Returns the first cursor, over the type triples
	 * @param conn
	 * @return
	 */
	protected ResultSet getGraphTriplesCursor1ForDotDrawing(Connection conn, long triplesToDraw) {
		try{
			String query = ("select * from triples where p='<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' limit " + triplesToDraw);
			Debugger.log("get cursor 1: " + query);
			return conn.createStatement().executeQuery("select * from triples where p='<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' limit " + triplesToDraw);
		}
		catch(SQLException e){
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing"); 
		}
	}
	/**
	 * This is used only when drawing the graph using Dot. 
	 * Different summaries need to traverse their triples in different orders, thus the two cursors which differ between the typed and untyped summaries.
	 * Returns the second cursor, over the non-type triples.
	 * @param conn
	 * @return
	 */
	protected ResultSet getGraphTriplesCursor2ForDotDrawing(Connection conn, long triplesToDraw) {
		try{
			return conn.createStatement().executeQuery("select * from triples where p<>'<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' limit " + triplesToDraw);
		}
		catch(SQLException e){
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing"); 
		}
	}

	
}
