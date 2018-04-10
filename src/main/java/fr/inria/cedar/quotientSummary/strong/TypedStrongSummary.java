package fr.inria.cedar.quotientSummary.strong;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongList;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

public class TypedStrongSummary extends StrongOrTypedStrongSummary {
	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set	
	Long2Long n2cs; // for each node, its class set ID. This is also the rep function for typed nodes
	Long2LongSet c2cs; // class to enclosing class sets


	HashMap<Long, HashMap<Long, Long>> untypedSummaryNodes; // source clique --> target clique --> summary node

	private long TYPE; // the number to be used for the type property
	private boolean firstType;
	private char TARGET=1;
	private char SOURCE=0;

	// case classification
	// T: typed, U: untyped (apply to S and O)
	// R: already represented, N: not already represented (apply to S, P, O)
	private final char TS_TO = 0; 
	private final char TS_UO_RO_RP = 1;
	private final char TS_UO_NO_RP = 2;
	private final char US_RS_TO_RP = 3;
	private final char US_RS_UO_RO_RP = 4;
	private final char US_RS_UO_NO_RP = 5; 
	private final char US_NS_TO_RP = 6;
	private final char US_NS_UO_RO_RP = 7;
	private final char US_NS_UO_NO_RP = 8;
	private final char TS_UO_RO_NP = 10;
	private final char TS_UO_NO_NP = 11;
	private final char US_RS_TO_NP = 12;
	private final char US_RS_UO_RO_NP = 13;
	private final char US_RS_UO_NO_NP = 14; 
	private final char US_NS_TO_NP = 15;
	private final char US_NS_UO_RO_NP = 16;
	private final char US_NS_UO_NO_NP = 17;


	// a subject that is typed has been represented before the data triples are traversed.
	// such a subject representative should never be merged with the source of a data property
	// nor should it involve the source and target of the data property
	protected final static char TRS_RO = 9;
	protected final static char TRS_TRO = 10;
	protected final static char TRS_UO = 11;

	public TypedStrongSummary(){
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
		firstType=true;
		numberOfDataTriplesRead=0;
		numberOfTypeTriplesRead=0; 
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX; 
	}

	/**
	 * This must be used to read a summary from Postgres. It is based on the core summary population method of the root summary class,
	 * then we just steal its edges.
	 * @param conn
	 */
	public  TypedStrongSummary (Connection conn) {
		Debugger.log("Reading TypedStrong summary from Postgres, setting up special URIs from the dictionary");
		RDF2SQLEncoding.setUp(conn); 
		String getSummaryTriples = getSQLQueryForSummaryTriples();
		try{
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
		catch(SQLException e) {
			throw new IllegalStateException("Unable to read Typed Strong summary from Postgres " + e.getStackTrace()); 
		}
		System.out.println("Read Typed Strong summary from Postgres"); 
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 * @param conn
	 * @param args
	 * @throws SQLException
	 * @throws IOException
	 */
	public void summarizeFromRDBMS(Connection conn, String[] args) {
		//Debugger.setFlag(true);
		long start = System.currentTimeMillis(); 
		String dataTriplesFileName = args[0];
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn); 
		long typeConstantCode = RDF2SQLEncoding.getTypeCode(); 
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true; 
		}
		//System.out.println("TypedWeak: Looking for type triples"); 
		String getTypedTriplesString = ("select *  from encoded_triples where p=" + typeConstantCode); 
		try {
			Statement getTypedTriples = conn.createStatement(); 
			getTypedTriples.setFetchSize(1000);
			ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString);
			while (rs.next()){		
				Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3)); 
				//System.out.println("### Type triple " + t.toString());
				this.handleTypeTripleBeforeData(t);
				globalTripleCount ++; 
			}
			rs.close();
			getTypedTriples.close();
		}
		catch(SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString()); 
		}
		System.out.println("Class sets created in " + (System.currentTimeMillis() - start) + " ms.");
		this.postHandleTypeTriples();
		long typeTripleCount = globalTripleCount; 
		System.out.println("Summarized " + typeTripleCount + " type triples in " + (System.currentTimeMillis() - start)  + " ms."); 

		this.display();
		
		// now all the non-type triples
		String getUntypedTriplesString = ("select *  from encoded_triples where p <> " + typeConstantCode); 
		try{
			conn.setAutoCommit(false);
			Statement getUntypedTriples = conn.createStatement(); 
			getUntypedTriples.setFetchSize(10000);
			ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString);
			globalTripleCount = 0;
			while (rs.next()){
				Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3)); 
				//Debugger.log("#### Triple " + t.toString());
				if ((t.p == RDF2SQLEncoding.getSubClassCode()) ||
						(t.p == RDF2SQLEncoding.getSubPropertyCode()) ||
						(t.p == RDF2SQLEncoding.getDomainCode()) ||
						(t.p == RDF2SQLEncoding.getRangeCode())) {
					copySchemaTriple(t.s, t.p, t.o); 
				}
				else{
					handleDataTriple(t); 
				}
				//Files.write(Paths.get("output.txt"), (globalTripleCount + ": " + new String(s + " " + p + " " + o + "\n")).getBytes(), StandardOpenOption.APPEND); 
				globalTripleCount++; 
				//if ((globalTripleCount % 1000 == 0)) {//|| (globalTripleCount > 28800)) {
				//	System.out.println(globalTripleCount + " triples");
				//}
			}
			rs.close();
			getUntypedTriples.close();
		}
		catch(SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString()); 
		}

		System.out.println("Summarized " + globalTripleCount + " triples in " + (System.currentTimeMillis() - start)  + " ms."); 
		this.display(dataTriplesFileName);
	}


	String caseName(char c){
		switch(c){
		case TS_TO: { return "TS_TO"; }
		case TS_UO_RO_RP: { return "TS_UO_RO_RP"; }
		case TS_UO_NO_RP: { return "TS_UO_NO_RP"; }
		case US_RS_TO_RP: { return "US_RS_TO_RP"; }
		case US_RS_UO_RO_RP: { return "US_RS_UO_RO_RP"; }
		case US_RS_UO_NO_RP: { return "US_RS_UO_NO_RP"; }
		case US_NS_TO_RP: { return "US_NS_TO_RP"; }
		case US_NS_UO_RO_RP: { return "US_NS_UO_RO_RP"; }
		case US_NS_UO_NO_RP: { return "US_NS_UO_NO_RP"; }
		case TS_UO_RO_NP: { return "TS_UO_RO_NP"; }
		case TS_UO_NO_NP: { return "TS_UO_NO_NP"; }
		case US_RS_TO_NP: { return "US_RS_TO_NP"; }
		case US_RS_UO_RO_NP: { return "US_RS_UO_RO_NP"; }
		case US_RS_UO_NO_NP: { return "US_RS_UO_NO_NP"; }
		case US_NS_TO_NP: { return "US_NS_TO_NP"; }
		case US_NS_UO_RO_NP: { return "US_NS_UO_RO_NP"; }
		case US_NS_UO_NO_NP: { return "US_NS_UO_NO_NP"; }
		}
		throw new IllegalStateException("Unrecognized case " + c); 
	}

	private char decode(Long classSetS, Long classSetO, Long sourceCliqueS, Long sourceCliqueO, Long sourceCliqueP){
		if (classSetS != null){ // TS
			if (classSetO != null){ // TS, TO
				return TS_TO; 
			}
			//TS, UO
			if (sourceCliqueO != null){// TS, UO, RO
				if (sourceCliqueP != null){// TS, UO, RO, RP
					return TS_UO_RO_RP; 
				}
				// TS, UO, RO, NP
				return TS_UO_RO_NP; 
			}
			// TS, UO, NO
			if (sourceCliqueP != null){ // TS, UO, NO, RP
				return TS_UO_NO_RP;
			}
			// TS, UO, NO, NP
			return TS_UO_NO_NP; 
		}
		// US
		if (sourceCliqueS != null){// US, RS
			if (classSetO != null){ // US, RS, TO
				if (sourceCliqueP != null){// US, RS, TO, RP
					return US_RS_TO_RP;
				}
				// US, RS, TO, NP
				return US_RS_TO_NP; 
			}
			// US, RS, UO
			if (sourceCliqueO != null){ //US, RS, UO, RO
				if (sourceCliqueP != null){ //US, RS, UO, RO, RP
					return US_RS_UO_RO_RP; 
				}
				//US, RS, UO, RO, NP
				return US_RS_UO_RO_NP; 
			}
			// US, RS, UO, NO
			if (sourceCliqueP != null){ // US, RS, UO, NO, RP
				return US_RS_UO_NO_RP;
			}
			// US, RS, UO, NO, NP 
			return US_RS_UO_NO_NP; 
		}
		if (classSetO != null){ // US, NS, TO
			if (sourceCliqueP != null){// US, NS, TO, RP
				return US_NS_TO_RP;
			}
			// US, NS, TO, NP
			return US_NS_TO_NP; 
		}
		// US, NS, UO
		if (sourceCliqueO != null){ //US, NS, UO, RO
			if (sourceCliqueP != null){ //US, NS, UO, RO, RP
				return US_NS_UO_RO_RP; 
			}
			//US, NS, UO, RO, NP
			return US_NS_UO_RO_NP; 
		}
		// US, NS, UO, NO
		if (sourceCliqueP != null){ // US, NS, UO, NO, RP
			return US_NS_UO_NO_RP;
		}
		// US, NS, UO, NO, NP 
		return US_NS_UO_NO_NP; 
	}


	/**
	 * Paranoid method for safety check. Throws an error is something is not coherent across the 
	 * data structures.
	 */
	public void cliqueSafetyCheck(){
		if (n2sc.getNodes().size() != n2tc.getNodes().size()){
			throw new IllegalStateException("n2sc has " + n2sc.getNodes().size() + " while n2tc has " + 
					n2tc.getNodes().size() + " entries"); 
		}
		if (n2sc.getNodes().size() != rep.getNodes().size()){
			throw new IllegalStateException("n2sc has " + n2sc.getNodes().size() + " while rep has " + 
					rep.getNodes().size() + " entries"); 
		}
		if (rep.getNodes().size() != n2tc.getNodes().size()){
			throw new IllegalStateException("rep has " + rep.getNodes().size() + " while n2tc has " + 
					n2tc.getNodes().size() + " entries"); 
		}
		if (p2sc.getNodes().size() != p2tc.getNodes().size()){
			display();
			throw new IllegalStateException("After " + this.numberOfDataTriplesRead + " data triples, " + 
					p2sc.getNodes().size() + " properties have source cliques while " +
					p2tc.getNodes().size() + " properties have target cliques "); 
		}
		// there is no reason why numbers of source cliques should be equal to numbers of target cliques
		//
		// The number of source and target clique in untypedSummaryNodes may be less than those in p2tc, p2sc, n2tc, n2sc.
		// This is because typed nodes may be source or target of a data property and in this case, a source (target) clique is created for the data property, but is not associated to any node,
		// as typed nodes do not have source/target cliques. 		
	}

	private long countDistinctTargetCliquesInCliqueToNodesMap() {
		TreeSet<Long> uniqueTCs = new TreeSet<>();
		for (Long sourceClique: untypedSummaryNodes.keySet()){
			HashMap<Long, Long> map = untypedSummaryNodes.get(sourceClique); 
			for (Long targetClique: map.keySet()){
				if (targetClique == this.emptyTCCount){
					continue;  // not counting the empty tc because it does not appear in p2tc
				}
				if (!uniqueTCs.contains(targetClique)){
					uniqueTCs.add(targetClique); 
				}
			}
		}
		return uniqueTCs.size(); 
	}

	private long countDistinctSourceCliquesInCliqueToNodesMap() {
		return this.untypedSummaryNodes.keySet().size(); // this does not count the empty sc
	}



	public void display(){
		System.out.println("TYPED STRONG SUMMARY\nClass to class set IDs:");
		c2cs.display();
		System.out.println("Class set IDs to class sets:");
		cs.display();
		System.out.println("Nodes to class set IDs");
		n2cs.display();
		System.out.println("Source cliques:");
		sc.display();
		System.out.println("Target cliques:");
		tc.display();
		System.out.println("Nodes to source cliques");
		n2sc.display();
		System.out.println("Nodes to target cliques");
		n2tc.display();
		System.out.println("Property to source cliques: ");
		p2sc.display();
		System.out.println("Property to target cliques: ");
		p2tc.display();
		System.out.println("Representation function for untyped nodes: ");
		showRep();
		System.out.println("Summary: ");
		for (Triple t: this.getSummaryEdges()){
			t.display();
		}
	}

	public void showRepThroughCliques(){
		StringBuffer sb = new StringBuffer();
		sb.append("n2sc: ");
		for (Long node: n2sc.getNodes()){
			if (node == null){
				throw new IllegalStateException("Null node"); 
			}
			Long thisNodeSC = n2sc.get(node);
			if (thisNodeSC == null){
				throw new IllegalStateException("Null source clique");
			}
			Long thisNodeTC = n2tc.get(node);
			if (thisNodeTC == null){
				throw new IllegalStateException("Null target clique for " + node); 
			}
			Long summaryNode = untypedSummaryNodes.get(thisNodeSC).get(thisNodeTC);
			if(summaryNode == null){
				throw new IllegalStateException("Null summary node");
			}
			sb.append(node + "->" + summaryNode+ " ");
		}
		System.out.println(sb); 	
	}

	public void handleTypeTripleBeforeData(Triple t){
		System.out.println("@@@ Type triple: " + t.toString()); 
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
				TreeSet<Long> newClassSetOfS = new TreeSet<Long>();
				newClassSetOfS.addAll(thisSubjectClassSet);
				newClassSetOfS.add(t.o); 
				//Either the union of the class plus t.o already existed:
				long existingClassSetID = classSetID(newClassSetOfS, t.o); 
				if (existingClassSetID>= 0) {
					// then we need to connect t.s to that
					n2cs.put(t.s, existingClassSetID);
					//Debugger.log("Attached " + t.s + " to the existing class set " + existingClassSetID);
				}
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
		display();
		this.numberOfTypeTriplesRead ++; 
	}

	/**
	 * Tries to see if the given class set has already been encountered.
	 * For efficiency, the method also gets @givenClass, so that it can look
	 * only in the class sets that include it. 
	 * @param givenClassSet
	 * @param givenClass
	 * @return the ID of the class set if it was already known, otherwise -1
	 */
	private long classSetID(TreeSet<Long> givenClassSet, long givenClass) {
		TreeSet<Long> possibleSets = c2cs.get(givenClass);
		if (possibleSets != null) {
			for (long possibleSetNo: possibleSets) {
				TreeSet<Long> possibleSet = cs.get(possibleSetNo); 
				if (possibleSet.equals(givenClassSet)) {
					return possibleSetNo; 
				}
			}
		}
		return -1;
	}

	public void postHandleTypeTriples() {
		for (Long node: this.n2cs.getNodes()) {
			for (Long thisClass: this.cs.get(this.n2cs.get(node))) {
				//System.out.println("Adding triple " + thisClass + " type " + RDF2SQLEncoding.dictionaryDecode(thisClass));
				this.addTriple(rep.get(node), RDF2SQLEncoding.getTypeCode(), thisClass);
				//checkTypeIsObject(); 
				globalTripleCount ++; 
			}
		}
	}
	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName()); 
	}

	/**
	 * Updates sc, n2sc, untypedSummaryNodes, summary (edges) 
	 * @param sourceCliqueOld
	 * @param sourceCliqueNew
	 */
	private void fuseSourceCliques(Long sourceCliqueOld, Long sourceCliqueNew){
		Debugger.log("FuseSourceCliques:  " + sourceCliqueOld + " becomes " + sourceCliqueNew);
		//display();
		// add properties of target clique of o, to those of the target clique of p
		ArrayList<Long> realSCNew = this.sc.get(sourceCliqueNew);
		ArrayList<Long> realSCOld = this.sc.get(sourceCliqueOld);

		if (realSCOld != null){
			for (Long l: realSCOld){
				realSCNew.add(l); 
			}
		}
		this.sc.remove(sourceCliqueOld);
		// update n2sc to inform all the nodes mapped to sourceCliqueO, to map now to sourceCliqueP
		this.n2sc.replaceValue(sourceCliqueOld, sourceCliqueNew);
		replaceCliqueInSummary(sourceCliqueOld, sourceCliqueNew);

		this.p2sc.replaceValue(sourceCliqueOld, sourceCliqueNew);
		Debugger.log("After the fusion: ");
		//display();
	}

	/**
	 * Updates tc, n2tc, p2tc, summary (edges)
	 * @param sourceCliqueOld
	 * @param sourceCliqueNew
	 */
	private void fuseTargetCliques(Long targetCliqueOld, Long targetCliqueNew){
		Debugger.log("FuseTargetCliques: " + targetCliqueOld + " into " + targetCliqueNew);
		if (targetCliqueNew == this.emptyTCCount){
			throw new Error("Should not use the empty target clique in a place where we had something else"); 
		}
		// Do not display here as this requires rep to be fully filled and rep cannot be filled for new nodes before the fusion. So some nodes may be missing.
		//display();
		// then make targetCliqueO the same as targetCliqueP (keep smaller)
		// add properties of target clique of o, to those of the target clique of p
		ArrayList<Long> realTCNew = this.tc.get(targetCliqueNew);
		ArrayList<Long> realTCOld = this.tc.get(targetCliqueOld);

		if (realTCNew == null){
			this.tc.display();
			throw new Error("After reading " + this.numberOfDataTriplesRead + 
					" data triples, there is no clique on the new target clique " + targetCliqueNew); 

		}

		if (realTCOld != null){
			for (Long l: realTCOld){
				realTCNew.add(l); 
			}
		}
		this.tc.remove(targetCliqueOld);
		// update n2tc to inform all the nodes mapped to targetCliqueO, to map now to targetCliqueP
		this.n2tc.replaceValue(targetCliqueOld, targetCliqueNew);
		replaceCliqueInSummary(targetCliqueOld, targetCliqueNew);
		this.p2tc.replaceValue(targetCliqueOld, targetCliqueNew);
		//Debugger.log("After the fusion: ");
		//display();
	}

	public void handleDataTriple(Triple t){
		System.out.println("@@@ handleDataTriple " + t.toString());
		// 18 cases: (TS, USR, USN) x (TO, UOR, UON) x (PR, PN)  also multiplied by: which cliques are empty and their consequences on fusion
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);

		Long sourceCliqueS = n2sc.get(t.s);
		Long targetCliqueS = n2tc.get(t.s);
		Long sourceCliqueO = n2sc.get(t.o);
		Long targetCliqueO = n2tc.get(t.o);

		Long sourceCliqueP = p2sc.get(t.p);
		Long targetCliqueP = p2tc.get(t.p);

		checkSymmetry(sourceCliqueS, targetCliqueS, sourceCliqueO, targetCliqueO, sourceCliqueP, targetCliqueP); 

		char caseNumber = decode(classSetS, classSetO, sourceCliqueS, sourceCliqueO, sourceCliqueP); 

		System.out.println("Case " + this.caseName(caseNumber));
		switch(caseNumber){
		case TS_TO: {          handleDataTriple_TS_TO(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case TS_UO_RO_RP: {    handleDataTriple_TS_UO_RO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case TS_UO_NO_RP: {    handleDataTriple_TS_UO_NO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_TO_RP: {    handleDataTriple_US_RS_TO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_UO_RO_RP: { handleDataTriple_US_RS_UO_RO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_UO_NO_RP: { handleDataTriple_US_RS_UO_NO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_TO_RP: {    handleDataTriple_US_NS_TO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_UO_RO_RP: { handleDataTriple_US_NS_UO_RO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_UO_NO_RP: { handleDataTriple_US_NS_UO_NO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case TS_UO_RO_NP: {    handleDataTriple_TS_UO_RO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case TS_UO_NO_NP: {    handleDataTriple_TS_UO_NO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_TO_NP: {    handleDataTriple_US_RS_TO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_UO_RO_NP: { handleDataTriple_US_RS_UO_RO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_UO_NO_NP: { handleDataTriple_US_RS_UO_NO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_TO_NP: {    handleDataTriple_US_NS_TO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_UO_RO_NP: { handleDataTriple_US_NS_UO_RO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_UO_NO_NP: { handleDataTriple_US_NS_UO_NO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		default: throw new IllegalStateException("Unknown case;"); 
		}
		this.display();
	}
	// untyped, non represented subject
	// untyped, non represented object
	// unknown property
	private void handleDataTriple_US_NS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		long psc = makeAndAddNewSourceClique(t.p); 
		long ptc = makeAndAddNewTargetClique(t.p); 
		p2sc.put(t.p, psc); 
		p2tc.put(t.p, ptc);
		long emptyTargetCliqueID = getEmptyTargetCliqueID();
		long repS = getOrCreateSummaryNode(psc, emptyTargetCliqueID);
		rep.put(t.s, repS);
		long emptySourceCliqueID = getEmptySourceCliqueID();
		long repO = getOrCreateSummaryNode(ptc, emptySourceCliqueID);
		rep.put(t.o, repO); 
	}

	// untyped, unrepresented subject
	// untyped, represented object (this has appeared in data triples before)
	// unknown property
	private void handleDataTriple_US_NS_UO_RO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// create p's source clique
		long psc = makeAndAddNewSourceClique(t.p); 
		p2sc.put(t.p, psc); 
		// represent s by the source clique of P and the empty target clique:
		long emptyTargetCliqueID = getEmptyTargetCliqueID();
		long repS = getOrCreateSummaryNode(psc, emptyTargetCliqueID);
		rep.put(t.s, repS);
		// o is already represented but its target clique did not include p (given that p was not known)
		// we need to add p to this target clique
		// then adjust t.o's representation
		addPropertyToTargetClique(t.p, targetCliqueO); 
		p2tc.put(t.p, targetCliqueO);
		this.addTriple(repS, t.p, rep.get(t.o));
		
	}
	
	// untyped, unrepresented subject
	// typed (thus represented) object
	// unknown property
	private void handleDataTriple_US_NS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// create p's source clique
		long psc = makeAndAddNewSourceClique(t.p); 
		p2sc.put(t.p, psc); 
		// represent s by the source clique of P and the empty target clique:
		long emptyTargetCliqueID = getEmptyTargetCliqueID();
		long repS = getOrCreateSummaryNode(psc, emptyTargetCliqueID);
		rep.put(t.s, repS);
		// the target clique of p needs to be created and initialized with p alone
		// because now that we have seen p, we cannot give it just a source clique
		long ptc = makeAndAddNewSourceClique(t.p);
		p2tc.put(t.p, ptc);
		this.addTriple(repS, t.p, rep.get(t.o)); 
	}

	// untyped subject, already represented: thus, it has a source clique that p must join
	// untyped object, not represented: we assign it target clique {p} and empty source clique
	// unknown property
	private void handleDataTriple_US_RS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// add p to the source clique of t.s
		long ssc = n2sc.get(t.s); 
		addPropertyToSourceClique(t.p, ssc); 
		p2sc.put(t.p, ssc); 
		long ptc = makeAndAddNewSourceClique(t.p); 
		p2tc.put(t.p, ptc);
		long repO = getOrCreateSummaryNode(ssc, ptc);
		rep.put(t.o, repO);
		n2tc.put(t.o, ptc);
		this.addTriple(rep.get(t.s), t.p, repO);
	}

	// untyped, represented subject: it has a source clique, which needs to gain p
	// untyped, represented object: it has a target clique, which needs to gain p
	// unknown property: it should be bound to these modified cliques
	private void handleDataTriple_US_RS_UO_RO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		addPropertyToSourceClique(t.p, sourceCliqueS);
		addPropertyToTargetClique(t.p, targetCliqueO);
		p2sc.put(t.p, sourceCliqueS);
		p2tc.put(t.p, targetCliqueO);
		// neither the representatives nor the source, target cliques of t.s and t.o change
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));
	}

	// untyped, represented subject: it has a source clique, which needs to gain p
	// typed, represented object which won't change
	// unknown property
	private void handleDataTriple_US_RS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		long ssc = n2sc.get(t.s); 
		addPropertyToSourceClique(t.p, ssc); 
		p2sc.put(t.p, ssc);
		long ptc = makeAndAddNewSourceClique(t.p); 
		p2tc.put(t.p, ptc);
		// no representatives will be changed; the cliques of the source node don't change either
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));
	}

	// typed, represented subject which won't change
	// untyped, unrepresented object
	// unknown property: both its cliques need to be created
	private void handleDataTriple_TS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		long psc = makeAndAddNewSourceClique(t.p);
		long ptc = makeAndAddNewTargetClique(t.p); 
		p2sc.put(t.p, psc);
		p2tc.put(t.p, ptc);
		// cliques of t.o: 
		n2tc.put(t.o, ptc);
		n2sc.put(t.o, getEmptySourceCliqueID());
		// represent t.o:
		long repO = getOrCreateSummaryNode(ptc, getEmptySourceCliqueID()); 
		rep.put(t.o, repO);
		// add triple:
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));
		
	}

	// typed, represented subject won't change
	// untyped, represented object, with a source clique which needs to change as p was unknown
	// unknown property
	private void handleDataTriple_TS_UO_RO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// p gets a new source clique as it was unknown, and its (typed) subject doesn't impact psc 
		long psc = makeAndAddNewSourceClique(t.p); 
		p2sc.put(t.p, psc);
		// adding p to o's target clique
		ArrayList<Long> tco = tc.get(targetCliqueO);
		tco.add(t.p); 
		p2tc.put(t.p, targetCliqueO);
		// node representatives do not change:
		// add triple: 
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));
	}

	/**
	 * Helper method which represents an (untyped) unrepresented subject 
	 * based on its known property p
	 * @param triple 
	 * @param sourceCliqueP
	 * @param targetCliqueP
	 */
	private void helper_US_NS_RP(Triple t, Long sourceCliqueP, Long targetCliqueP) {
		// represent t.s as empty target clique + source clique of p
		Long emptyTargetCliqueS = getEmptyTargetCliqueID(); 
		Long repS = getOrCreateSummaryNode(sourceCliqueP, emptyTargetCliqueS);
		rep.put(t.s, repS);
		n2tc.put(t.s, emptyTargetCliqueS);
		n2sc.put(t.s, sourceCliqueP);
	}
	
	// untyped, unrepresented subject
	// untyped, unrepresented object
	// known property
	private void handleDataTriple_US_NS_UO_NO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		helper_US_NS_RP(t, sourceCliqueP, targetCliqueP);
		// represent t.o as target clique of p + empty source clique
		Long emptySourceCliqueO = getEmptySourceCliqueID();
		Long repO = getOrCreateSummaryNode(emptySourceCliqueO, targetCliqueP); 
		rep.put(t.o, repO);
		n2tc.put(t.o, targetCliqueP);
		n2sc.put(t.o, emptySourceCliqueO);
		// add triple:
		this.addTriple(rep.get(t.s), t.p, repO);
	}

	// untyped, unrepresented subject
	// untyped, represented object
	// known property
	private void handleDataTriple_US_NS_UO_RO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		helper_US_NS_RP(t, sourceCliqueP, targetCliqueP); 
		// see what to do with t.o
		Long newRepO = rep.get(t.o); 
		if (targetCliqueO != targetCliqueP) {
			// this call updates n2tc, tc, existing summary edges
			Long fusedTargetPO = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET); 
			newRepO = getOrCreateSummaryNode(sourceCliqueO, fusedTargetPO); 
			n2tc.put(newRepO, fusedTargetPO); 
			n2sc.put(newRepO, sourceCliqueO);
			rep.put(t.o, newRepO);
		}
		else { // p is already known to be in the target clique of o (o and p have the same tc) 
			// no need to change the representative of o
		}
		// adding triple: 
		this.addTriple(rep.get(t.s), t.p, newRepO); 
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

	private void helper_UO_NO_RP(Triple t, Long sourceCliqueP, Long targetCliqueP) {
		// represent t.o as empty source clique + target clique of p
		Long emptySourceCliqueO = getEmptySourceCliqueID(); 
		Long repO = getOrCreateSummaryNode(emptySourceCliqueO, targetCliqueP);
		rep.put(t.o, repO);
		n2tc.put(t.o, targetCliqueP);
		n2sc.put(t.o, emptySourceCliqueO);
	}
	
	// untyped, represented subject
	// untyped, unrepresented object
	// known property
	private void handleDataTriple_US_RS_UO_NO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		helper_UO_NO_RP(t, sourceCliqueP, targetCliqueP); 
		// see what we do with t.s:
		Long newRepS = rep.get(t.s); 
		if (sourceCliqueS != sourceCliqueP) { // the source clique of P was not that of S
			Long fusedCliqueS = fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
			newRepS = getOrCreateSummaryNode(fusedCliqueS, targetCliqueS); 
			rep.put(t.s, newRepS);
			n2tc.put(newRepS, targetCliqueS);
			n2sc.put(newRepS, fusedCliqueS);
		}
		else { // no need to do anything, the source clique of S is already that of p 
		}
		// adding triple:
		this.addTriple(newRepS, t.p, rep.get(t.o));
	}

	// toughest case: 
	// untyped, represented object
	// untyped, represented subject
	// represented property 
	private void handleDataTriple_US_RS_UO_RO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long newSCs = sourceCliqueS;
		Long newTCo = targetCliqueO; 
		Long newRepS = repS;
		Long newRepO = repO; 
		boolean sRepChanged = false; 
		boolean oRepChanged = false; 
		if (sourceCliqueS != sourceCliqueP) {
			sRepChanged = true; 
			newSCs = fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
		}
		if (targetCliqueO != targetCliqueP) {
			oRepChanged = true; 
			newTCo = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET); 
		}
		if (sRepChanged) {
			newRepS = getOrCreateSummaryNode(newSCs, targetCliqueS);
			n2tc.put(newRepS, targetCliqueS);
			n2sc.put(newRepS, newSCs);
			rep.put(t.s, newRepS);
		}
		if (oRepChanged) {
			newRepO = getOrCreateSummaryNode(sourceCliqueO, newTCo); 
			n2tc.put(newRepO, newTCo);
			n2sc.put(newRepO, sourceCliqueO);
			rep.put(t.o, newRepO);
		}
		// adding triple:
		this.addTriple(newRepS, t.p, newRepO);
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
			Long fusedSCs = this.fuseCliquesIntoCreatedFirst(sourceCliqueS,  sourceCliqueP, SOURCE); 
			repS = getOrCreateSummaryNode(fusedSCs, sourceCliqueP);
			n2tc.put(repS, targetCliqueS); 
			n2sc.put(repS,  fusedSCs);
			rep.put(t.s,  repS);
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
	private void handleDataTriple_TS_UO_RO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long newTCo = targetCliqueO; 
		Long newRepO = repO; 
		if (targetCliqueO != targetCliqueP) {
			newTCo = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET); 
			newRepO = getOrCreateSummaryNode(sourceCliqueO, newTCo); 
			n2tc.put(newRepO, newTCo);
			n2sc.put(newRepO, sourceCliqueO);
			rep.put(t.o, newRepO);
		}
		// adding triple:
		this.addTriple(repS, t.p, newRepO); 
	}

	private void handleDataTriple_TS_TO(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// add the edge to the summary
		this.addTriple(classSetS, t.p, classSetO);
	}

	private void checkSymmetry(Long sourceCliqueS, Long targetCliqueS, Long sourceCliqueO, Long targetCliqueO,
			Long sourceCliqueP, Long targetCliqueP) {
		if ( (sourceCliqueS == null && targetCliqueS != null) || (sourceCliqueS != null && targetCliqueS == null)){
			throw new Error("Subject has only one of the two cliques"); 
		}
		if ( (sourceCliqueO == null && targetCliqueO != null) || (sourceCliqueO != null && targetCliqueO == null)){
			throw new Error("Object has only one of the two cliques"); 
		}
		if ( (sourceCliqueP == null && targetCliqueP != null) || (sourceCliqueP != null && targetCliqueP == null)){
			throw new Error("Property has only one of the two cliques"); 
		}
	}

	/**
	 * Creates a new (untyped) summary node and inserts it into untypedSummaryNodes
	 * @param sourceClique
	 * @param targetClique
	 * @return
	 */
	private Long getOrCreateSummaryNode(Long sourceClique, Long targetClique) {
		assert(sourceClique != null & targetClique != null); 
		HashMap<Long, Long> targetCliquesForThisSourceClique = this.untypedSummaryNodes.get(sourceClique); 
		if (targetCliquesForThisSourceClique == null){
			targetCliquesForThisSourceClique = new HashMap<>();
			this.untypedSummaryNodes.put(sourceClique, targetCliquesForThisSourceClique); 
		}
		Long node = targetCliquesForThisSourceClique.get(targetClique);
		if (node == null){
			Debugger.log("Created " + this.maxSummaryNode + " for source clique " + sourceClique + " and target clique " + targetClique); 
			node = getNextSummaryNode(); // from the parent method
			this.untypedSummaryNodes.get(sourceClique).put(targetClique, node);
		}
		return node; 
	}

	private Long getEmptySourceCliqueID() {
		Long res; 
		if (this.emptySCCount == Long.MAX_VALUE){ // the empty source clique has not been created yet
			ArrayList<Long> emptySC = new ArrayList<>();
			res = minCliqueID; // we invent a new source clique
			Debugger.log("ooooo> Initialized the empty source clique at: " + res);
			this.emptySCCount = minCliqueID; 
			// add this to sc
			sc.put(minCliqueID, emptySC);
			minCliqueID--;
		}
		else{ // the empty source clique has already been created, just copy it 
			res = this.emptySCCount; 
		}	
		return res; 
	}

	private Long getEmptyTargetCliqueID() {
		Long res; 
		if (this.emptyTCCount == Long.MAX_VALUE){ // the empty source clique has not been created yet
			ArrayList<Long> emptyTC = new ArrayList<>();
			res = minCliqueID; // we invent a new source clique
			Debugger.log("ooooo> Initialized the empty target clique at: " + res);
			this.emptyTCCount = minCliqueID; 
			// add this to tc
			tc.put(minCliqueID, emptyTC);
			minCliqueID--;
		}
		else{ // the empty source clique has already been created, just copy it 
			res = this.emptyTCCount; 
		}	
		return res; 
	}

	/**
	 * Clique IDs are negative. So, the higher value is the one created first. We will keep the higher value and replace the 
	 * lower value with this higher value.
	 * Updates sc, tc, n2tc, p2sc, p2tc, summary (edges)
	 */
	private Long fuseCliquesIntoCreatedFirst(Long c1, Long c2, char code) {
		if (code == SOURCE){
			if (c2 == this.emptySCCount){
				throw new Error("Do not replace with empty source clique!"); 
			}
			if (c1 > c2){
				// this method treats its first parameter as "old" and the second as "new" 
				fuseSourceCliques(c2, c1);
				return c1; 
			}
			else{
				if (c2 > c1){
					fuseSourceCliques(c1, c2);
					return c2; 
				}
			}
		}
		else{
			if (code == TARGET){
				if (c2 == this.emptyTCCount){
					throw new Error("Do not replace with empty target clique!"); 
				}
				if (c1 > c2){
					// this method treats its first parameter as "old" and the second as "new" 
					fuseTargetCliques(c2, c1);
					return c1; 
				}
				else{
					if (c2 > c1){
						fuseTargetCliques(c1, c2);
						return c2; 
					}
				}
			}
			else{
				throw new Error("Unknown code!"); 
			}
		}
		return c1; 
	}

	/**
	 * Creates a new source clique with just p; updates sc
	 * @param p
	 * @return
	 */
	private Long makeAndAddNewSourceClique(Long p){
		Long res = new Long(this.minCliqueID);
		ArrayList<Long> actualSourceClique = new ArrayList<>(); 
		actualSourceClique.add(p);
		sc.put(res, actualSourceClique);
		p2sc.put(p,  res);
		Debugger.log("Added the new source clique for: " + res + " with property " + p);
		minCliqueID --;
		return res; 
	}
	/**
	 * Adds p to the source clique indicated by sourceCliqueID; updates p2sc and sc
	 * @param p
	 * @param sourceCliqueID
	 */
	private void addPropertyToSourceClique(Long p, Long sourceCliqueID) {
		p2sc.put(p, sourceCliqueID);
		if (!(sc.get(sourceCliqueID).contains(p))){
			sc.get(sourceCliqueID).add(p);
		}		
	}

	/**
	 * Initializes a target clique for property p
	 * Also records the association between p and this target clique in p2c and tc
	 * @param p
	 * @return
	 */
	private Long makeAndAddNewTargetClique(Long p){		
		Long targetCliqueID = new Long(this.minCliqueID);
		ArrayList<Long> actualTargetClique = new ArrayList<>(); 
		actualTargetClique.add(p);
		tc.put(targetCliqueID, actualTargetClique);
		p2tc.put(p,  targetCliqueID);
		Debugger.log("Added the new target clique for: " + targetCliqueID + " with property " + p);
		minCliqueID --;
		return targetCliqueID; 
	}

	/**
	 * Adds p to the target clique indicated by targetCliqueID
	 * @param p
	 * @param targetCliqueID
	 */
	private void addPropertyToTargetClique(Long p, Long targetCliqueID) {
		p2tc.put(p, targetCliqueID);
		if (!(tc.get(targetCliqueID).contains(p))){
			tc.get(targetCliqueID).add(p);
		}		
	}


	/**
	 * Replaces a clique ID with another cliqueID in the summary
	 * 
	 * Modifies untypedSummaryNodes and the summary edges
	 * 
	 * For simplicity (and in a somehow violent manner), it replaces either a source clique or a target clique
	 * Thus it is important that oldCliqueID is not allowed to match both a source clique and a target clique, 
	 * because we usually only want to replace one.
	 * @param oldCliqueID
	 * @param newCliqueID
	 */
	private void replaceCliqueInSummary(Long oldCliqueID, Long newCliqueID) {		
		// replace in rep:
		// first, replace in second-level hash, if it occurs as a target clique:
		if (oldCliqueID > newCliqueID){
			throw new Error("Wrong replacement");
		}
		Debugger.log("Trying to replace " + oldCliqueID + " with " + newCliqueID + " in summary");
		for (Long l: this.untypedSummaryNodes.keySet()){
			HashMap<Long, Long> tcToNodes = untypedSummaryNodes.get(l);
			Long nodeOldTC = tcToNodes.get(oldCliqueID);
			Long nodeNewTC = tcToNodes.get(newCliqueID);
			// replace old with new; remove entry for old:
			if ((nodeOldTC != null) && (nodeNewTC!= null)){
				replaceInSummary(nodeOldTC, nodeNewTC); 
				tcToNodes.remove(oldCliqueID);
			}
			// if there was nothing there, nothing to do 
		}
		// then, replace in first-level hash: 
		HashMap<Long, Long> tcToNodesForOldSC = untypedSummaryNodes.get(oldCliqueID);
		HashMap<Long, Long> tcToNodesForNewSC = untypedSummaryNodes.get(newCliqueID);
		if (tcToNodesForOldSC != null){
			if (tcToNodesForNewSC == null){
				tcToNodesForNewSC = new HashMap<>();
				untypedSummaryNodes.put(newCliqueID,  tcToNodesForNewSC);
			}
			for (Long thisOldTC: tcToNodesForOldSC.keySet()){
				Long thisOldNode = tcToNodesForOldSC.get(thisOldTC); // this is not null
				Long thisNewNode = tcToNodesForNewSC.get(thisOldTC);
				if (thisNewNode == null){
					tcToNodesForNewSC.put(thisOldTC, thisOldNode); // no summary node replacement here
				}
				else{
					replaceInSummary(thisOldNode, thisNewNode); // if there was a summary node on the old and new clique with the same TC, use the new clique node
				}
			}
			untypedSummaryNodes.remove(oldCliqueID); // after the loop not to interfere with the cursor
		}
		else{ // oldSC was not a source clique, nothing left to do
		}
	}

}
