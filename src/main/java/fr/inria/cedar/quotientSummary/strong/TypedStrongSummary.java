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
	Long2LongSet c2cs; // class to enclosing class sets
	// case classification
	// T: typed, U: untyped (apply to S and O)
	// R: already represented, N: not already represented (apply to S, P, O)
	protected final char TS_TO = 0;
	protected final char TS_UO_RO_RP = 1;
	protected final char TS_UO_NO_RP = 2;
	protected final char US_RS_TO_RP = 3;
	protected final char US_NS_TO_RP = 6;
	protected final char TS_UO_RO_NP = 9;
	protected final char TS_UO_NO_NP = 10;
	protected final char US_RS_TO_NP = 11;
	protected final char US_NS_TO_NP = 14;

	public TypedStrongSummary() {
		super();
		cs = new Long2LongSet();
		n2sc = new Long2Long();
		c2cs = new Long2LongSet();
		rep = new Long2Long();
		n2cs = new Long2Long();
		untypedSummaryNodes = new HashMap<>();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE;
		numberOfDataTriplesRead = 0;
		numberOfTypeTriplesRead = 0;
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX;
	}

	/**
	 * This must be used to read a TS summary from Postgres.
	 *
	 * @param conn
	 */
	public TypedStrongSummary(Connection conn) {
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

		//System.out.println("TypedWeak: Looking for type triples"); 
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
		long endOfClassSetCreation = System.currentTimeMillis();
		System.out.println("Class sets created in " + (endOfClassSetCreation - start) + " ms.");
		this.postHandleTypeTriples();
		long startData = System.currentTimeMillis();
		System.out.println("Summarized " + this.numberOfTypeTriplesRead + " type triples in " + (startData - start) + " ms.");

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
				//Files.write(Paths.get("output.txt"), (globalTripleCount + ": " + new String(s + " " + p + " " + o + "\n")).getBytes(), StandardOpenOption.APPEND); 
				triplesSummarizedSoFar++;
				this.numberOfDataTriplesRead++;
				//if ((globalTripleCount % 1000 == 0)) {//|| (globalTripleCount > 28800)) {
				//	System.out.println(globalTripleCount + " triples");
				//}
			}
			rs.close();
			getUntypedTriples.close();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		System.out.println("Summarized " + this.numberOfDataTriplesRead + " data triples in " + (System.currentTimeMillis() - startData) + " ms.");
		System.out.println("Summarized " + this.triplesSummarizedSoFar + " triples overall in " + (System.currentTimeMillis() - start) + " ms.");
		this.display(dataTriplesFileName);
	}

	String caseName(char c) { // 17 cases
		switch (c) {
			case TS_TO: {
				return "TS_TO";
			}
			case TS_UO_RO_RP: {
				return "TS_UO_RO_RP";
			}
			case TS_UO_NO_RP: {
				return "TS_UO_NO_RP";
			}
			case US_RS_TO_RP: {
				return "US_RS_TO_RP";
			}
			case US_RS_UO_RO_RP: {
				return "US_RS_UO_RO_RP";
			}
			case US_RS_UO_NO_RP: {
				return "US_RS_UO_NO_RP";
			}
			case US_NS_TO_RP: {
				return "US_NS_TO_RP";
			}
			case US_NS_UO_RO_RP: {
				return "US_NS_UO_RO_RP";
			}
			case US_NS_UO_NO_RP: {
				return "US_NS_UO_NO_RP";
			}
			case TS_UO_RO_NP: {
				return "TS_UO_RO_NP";
			}
			case TS_UO_NO_NP: {
				return "TS_UO_NO_NP";
			}
			case US_RS_TO_NP: {
				return "US_RS_TO_NP";
			}
			case US_RS_UO_RO_NP: {
				return "US_RS_UO_RO_NP";
			}
			case US_RS_UO_NO_NP: {
				return "US_RS_UO_NO_NP";
			}
			case US_NS_TO_NP: {
				return "US_NS_TO_NP";
			}
			case US_NS_UO_RO_NP: {
				return "US_NS_UO_RO_NP";
			}
			case US_NS_UO_NO_NP: {
				return "US_NS_UO_NO_NP";
			}
		}
		throw new IllegalStateException("Unrecognized case " + c);
	}

	private char decode(Long classSetS, Long repS, Long classSetO, Long repO, Long sourceCliqueP) {
		if (classSetS != null) //TS (also represented)
			if (classSetO != null) // TO (also represented)
				return TS_TO;
			else//UO
				if (repO != null)//RO
					if (sourceCliqueP != null)//RP
						return TS_UO_RO_RP;
					else // NP
						return TS_UO_RO_NP;
				else // NO
					if (sourceCliqueP != null)//RP
						return TS_UO_NO_RP;
					else
						return TS_UO_NO_NP;
		else // US
			if (repS != null)//US, RS
				if (classSetO != null) // TO (also represented)
					if (sourceCliqueP != null)
						return US_RS_TO_RP;
					else
						return US_RS_TO_NP;
				else // US, RS, UO
					if (repO != null) //RO
						if (sourceCliqueP != null)
							return US_RS_UO_RO_RP;
						else
							return US_RS_UO_RO_NP;
					else // NO
						if (sourceCliqueP != null)
							return US_RS_UO_NO_RP;
						else
							return US_RS_UO_NO_NP;
			else //US, NS
				if (classSetO != null)// TO, also represented
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
		if (n2sc.getNodes().size() != n2tc.getNodes().size())
			throw new IllegalStateException("n2sc has " + n2sc.getNodes().size() + " while n2tc has "
											+ n2tc.getNodes().size() + " entries");
		if (n2sc.getNodes().size() != rep.getNodes().size())
			throw new IllegalStateException("n2sc has " + n2sc.getNodes().size() + " while rep has "
											+ rep.getNodes().size() + " entries");
		if (rep.getNodes().size() != n2tc.getNodes().size())
			throw new IllegalStateException("rep has " + rep.getNodes().size() + " while n2tc has "
											+ n2tc.getNodes().size() + " entries");
		if (p2sc.getNodes().size() != p2tc.getNodes().size()) {
			display();
			throw new IllegalStateException("After " + this.numberOfDataTriplesRead + " data triples, "
											+ p2sc.getNodes().size() + " properties have source cliques while "
											+ p2tc.getNodes().size() + " properties have target cliques ");
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
		System.out.println("TYPED STRONG SUMMARY\nClass to class set IDs:");
		c2cs.display();
		System.out.println("Class set IDs to class sets: " + cs.display());
		System.out.println("Nodes to class set IDs: " + n2cs.display());
		System.out.println("Source cliques: " + sc.display());
		System.out.println("Target cliques: " + tc.display());
		System.out.println("Nodes to source cliques: " + n2sc.display());
		System.out.println("Nodes to target cliques: " + n2tc.display());
		System.out.println("Property to source cliques: " + p2sc.display());
		System.out.println("Property to target cliques: " + p2tc.display());
		System.out.println("Representation function: ");
		showRep();
		System.out.println("Summary: ");
		for (Triple t: this.getSummaryEdges())
			t.display();
	}

	public void showRepThroughCliques() {
		StringBuffer sb = new StringBuffer();
		sb.append("n2sc: ");
		for (Long node: n2sc.getNodes()) {
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
		Long prevClassSetOfS = this.n2cs.get(t.s);
		TreeSet<Long> thisSubjectClassSet;
		if (prevClassSetOfS == null) { // this subject was untyped so far
			prevClassSetOfS = getNextSummaryNode();
			n2cs.put(t.s, prevClassSetOfS);
			thisSubjectClassSet = new TreeSet<>();
			thisSubjectClassSet.add(t.o);
			cs.put(prevClassSetOfS, thisSubjectClassSet);
			c2cs.add(t.o, prevClassSetOfS);
		}
		else { // the subject was typed, then cs should also know about it
			thisSubjectClassSet = cs.get(prevClassSetOfS);
			if (thisSubjectClassSet.contains(t.o)) {
				// do nothing -- we knew s was of type o
				//Debugger.log("Already knew " + t.s + " was of type " + t.o);
			}
			else {
				// the class set of s needs to change get also o
				TreeSet<Long> newClassSetOfS = new TreeSet<>();
				newClassSetOfS.addAll(thisSubjectClassSet);
				newClassSetOfS.add(t.o);
				//Either the union of the class plus t.o already existed:
				long existingClassSetID = classSetID(newClassSetOfS, t.o);
				if (existingClassSetID >= 0)
					// then we need to connect t.s to that
					n2cs.put(t.s, existingClassSetID); //Debugger.log("Attached " + t.s + " to the existing class set " + existingClassSetID);
				else {
					//we need to create a new class set, move t.s to that class set, 
					// detach t.s from its previous class set
					long newClassSetID = getNextSummaryNode();
					cs.put(newClassSetID, newClassSetOfS);
					n2cs.put(t.s, newClassSetID);
					c2cs.add(t.o, newClassSetID);
					//Debugger.log("Attached " + t.s + " to the newly created class set " + newClassSetID);
				}
			}
		}
		// store the representation of t.s:
		rep.put(t.s, n2cs.get(t.s));
		//Debugger.log(t.s + " represented by " + n2cs.get(t.s));
		//display();
		this.numberOfTypeTriplesRead++;
	}

	/**
	 * Tries to see if the given class set has already been encountered.
	 * For efficiency, the method also gets @givenClass, so that it can look
	 * only in the class sets that include it.
	 *
	 * @param givenClassSet
	 * @param givenClass
	 *
	 * @return the ID of the class set if it was already known, otherwise -1
	 */
	private long classSetID(TreeSet<Long> givenClassSet, long givenClass) {
		TreeSet<Long> possibleSets = c2cs.get(givenClass);
		if (possibleSets != null)
			for (long possibleSetNo: possibleSets) {
				TreeSet<Long> possibleSet = cs.get(possibleSetNo);
				if (possibleSet.equals(givenClassSet))
					return possibleSetNo;
			}
		return -1;
	}

	public void postHandleTypeTriples() {
		for (Long node: this.n2cs.getNodes())
			for (Long thisClass: this.cs.get(this.n2cs.get(node)))
				//System.out.println("Adding triple " + thisClass + " type " + RDF2SQLEncoding.dictionaryDecode(thisClass));
				this.addTriple(rep.get(node), RDF2SQLEncoding.getTypeCode(), thisClass); //checkTypeIsObject(); 
	}

	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	public void handleDataTriple(Triple t) {
		// 18 cases: (TS, USR, USN) x (TO, UOR, UON) x (PR, PN)  also multiplied by: which cliques are empty and their consequences on fusion
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
		long ssc = n2sc.get(t.s);
		Long newSourceClique = addPropertyToSourceClique(t.p, ssc);
		n2sc.put(t.s, newSourceClique);
		makeAndAddNewSourceClique(t.p);
		// no representatives will be changed; the cliques of the source node don't change either
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));
	}

	// typed, represented subject which won't change
	// untyped, unrepresented object
	// unknown property: both its cliques need to be created
	private void handleDataTriple_TS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
											  Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		long psc = makeAndAddNewSourceClique(t.p);//TODO check this -- bug? 
		long ptc = makeAndAddNewTargetClique(t.p);
		// cliques of t.o: 
		n2tc.put(t.o, ptc);
		n2sc.put(t.o, getEmptySourceCliqueID());
		// represent t.o:
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
		Long resultingTargetClique = addPropertyToTargetClique(t.p, targetCliqueO);
		p2tc.put(t.p, resultingTargetClique);
		// node representatives do not change:
		// add triple: 
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));
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
		if (sourceCliqueS != sourceCliqueP) {
			Long fusedSCs = this.fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
			repS = getOrCreateSummaryNode(fusedSCs, sourceCliqueP);
			n2tc.put(repS, targetCliqueS);
			n2sc.put(repS, fusedSCs);
			rep.put(t.s, repS);
		}
		else { // nothing 			
		}
		// adding triple:
		this.addTriple(repS, t.p, rep.get(t.o));
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
			this.showClique(tc.get(targetCliqueO));
			this.showClique(tc.get(targetCliqueP));
			//Debugger.log("Empty target clique is: " + this.getEmptyTargetCliqueID());
			newTCo = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET);
			newRepO = getOrCreateSummaryNode(sourceCliqueO, newTCo);
			if (newRepO == null)
				throw new IllegalStateException("repO");
			n2tc.put(newRepO, newTCo);
			n2sc.put(newRepO, sourceCliqueO);
			rep.put(t.o, newRepO);
		}
		// adding triple:
		if (repS == null)
			throw new IllegalStateException("repS");
		if (rep.get(t.o) == null)
			throw new IllegalStateException("repO");
		this.addTriple(repS, t.p, rep.get(t.o));
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
}
