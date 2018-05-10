package fr.inria.cedar.quotientSummary.weak;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import fr.inria.cedar.quotientSummary.util.Substitutions;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

public class TypedWeakSummary extends WeakOrTypedWeakSummary {
	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set
	Long2Long n2cs; // for each node, its class set ID. This is also the rep function for typed nodes
	Long2LongSet n2c; // for each node, the set of types we know so far for this node
	HashMap<TreeSet<Long>, Long> cs2csID; // for each set of types known so far, the ID of that set

	protected final static char TRS_RO = 9;
	protected final static char TRS_TRO = 10;
	protected final static char TRS_UO = 11;

	protected final static char TRS_RP_TRO = 12;
	protected final static char TRS_RP_RO = 13;
	protected final static char TRS_RP_UO = 14;

	protected final static char TRS_UP_TRO = 15; 
	protected final static char TRS_UP_RO = 16; 
	protected final static char TRS_UP_UO = 17; 

	protected final static char RS_RP_TRO = 18;
	protected final static char RS_UP_TRO = 19; 

	protected final static char US_UP_TRO = 20; 
	protected final static char US_RP_TRO = 21; 


	public TypedWeakSummary() {
		super();
		cs = new Long2LongSet();
		n2cs = new Long2Long();	
		n2c = new Long2LongSet();
		cs2csID = new HashMap<TreeSet<Long>, Long>();
		this.summaryTablePrefix = TYPED_WEAK_SUMMARY_PREFIX;
	}

	/**
	 * This must be used to read a TW summary from Postgres.
	 *
	 * @param conn
	 */
	public TypedWeakSummary(Connection conn) {
		this.summaryTablePrefix = TYPED_WEAK_SUMMARY_PREFIX;
		Debugger.log("Reading TypedWeak summary from Postgres, setting up special URIs from the dictionary");
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
			throw new IllegalStateException("Unable to read TypedWeak summary from Postgres " + e.getStackTrace());
		}
		System.out.println("Read TypedWeak summary from Postgres");
	}

	/**
	 * @param typeTriplesFile
	 * @param dataTriplesFile
	 */
	@Override
	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile) {
		long start = System.currentTimeMillis();
		try {
			// First file: type triples
			try (BufferedReader br = new BufferedReader(new FileReader(new File(typeTriplesFile)))) {
				while (br.ready()) {
					String spo = br.readLine();
					Triple t = readTriple(spo);
					//t.display();
					handleTypeTripleAfterData(t);
					//System.out.println();
				}
			}
			//System.out.println("=== After weak type triple summarization of " + typeTriplesFile + ": =================================== ");
			//display();
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not exploit file " + typeTriplesFile);
		}
		// this is the one who actually puts type triples in the summary
		postHandleTypeTriples();

		// Second file: data triples
		try (BufferedReader br = new BufferedReader(new FileReader(new File(dataTriplesFile)))) {
			while (br.ready()) {
				String spo = br.readLine();
				Triple t = readTriple(spo);
				//System.out.println("\n");
				//t.display();
				handleDataTriple(t);
				//display();
				//System.out.println();
			}
		}
		//System.out.println("=== After weak data triple summarization of "+ dataTriplesFile + ": ==================================");
		//display();

		catch (IOException e) {
			throw new IllegalStateException("Unable to open file " + dataTriplesFile + " or " + typeTriplesFile + ": " + e.toString());
		}
		allTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized in " + allTriplesSummarizationTime + " ms");
		display(dataTriplesFile); // this prints out and makes a DOT file
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 * @param args
	 */
	@Override
	public void summarizeFromPostgres(Connection conn, String[] args) {
		//Debugger.setFlag(true);
		long start = System.currentTimeMillis();
		String tableName = args[0];
		String dataTriplesFileName = args[1];
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn);
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true;
			avoidCollisionsWhenAssigningSummaryNodes(conn);
		}
		//System.out.println("TypedWeak: Looking for type triples"); 
		triplesSummarizedSoFar = 0;
		String getTypedTriplesString = ("select *  from " + tableName + " where p =" + typeConstantCode);
		try {
			Statement getTypedTriples = conn.createStatement();
			getTypedTriples.setFetchSize(1000);
			ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString);
			while (rs.next()) {
				Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
				System.out.println("### Type triple " + t.toString());
				this.handleTypeTripleBeforeData(t);
				triplesSummarizedSoFar++;
			}
			rs.close();
			getTypedTriples.close();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		classSetCreationTime = System.currentTimeMillis() - start;
		System.out.println("Class sets created in " + classSetCreationTime + " ms");

		start = System.currentTimeMillis();
		this.postHandleTypeTriples();
		long typeTripleCount = triplesSummarizedSoFar;
		typeTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + numberOfTypeTriplesRead + " type triples in " + typeTriplesSummarizationTime + " ms");

		start = System.currentTimeMillis();

		//this.drawSummaryAndGraph(conn, dataTriplesFileName, ("_" + triplesSummarizedSoFar));

		// now all the non-type triples
		String getUntypedTriplesString = ("select *  from " + tableName + " where p <> " + typeConstantCode);
		try {
			conn.setAutoCommit(false);
			Statement getUntypedTriples = conn.createStatement();
			getUntypedTriples.setFetchSize(10000);
			ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString);
			while (rs.next()) {
				Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
				//System.out.println("#### Data triple " + t.toString());
				if ((t.p == RDF2SQLEncoding.getSubClassCode())
						|| (t.p == RDF2SQLEncoding.getSubPropertyCode())
						|| (t.p == RDF2SQLEncoding.getDomainCode())
						|| (t.p == RDF2SQLEncoding.getRangeCode()))
					addTriple(t.s, t.p, t.o);
				else
					handleDataTriple(t);
				triplesSummarizedSoFar++;
				System.out.println("Triples summarized so far: " + triplesSummarizedSoFar);
				//this.drawSummaryAndGraph(conn, dataTriplesFileName, ("_" + triplesSummarizedSoFar));

			}
			rs.close();
			getUntypedTriples.close();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		dataTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + numberOfDataTriplesRead + " data triples in " + dataTriplesSummarizationTime + " ms");

		allTriplesSummarizationTime = classSetCreationTime + typeTriplesSummarizationTime + dataTriplesSummarizationTime;
		System.out.println("Summarized " + triplesSummarizedSoFar + " triples overall in " + allTriplesSummarizationTime + " ms");
		this.display(dataTriplesFileName);
	}

	protected void handleDataTriple(Triple t) {
		//Debugger.log("### Data triple: " + t.toString());
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p);
		// can't do this because of triples where one node is typed and the other is not; such nodes have a source but not a target, or the opposite.
		//if ((pSource == null && pTarget != null) || (pSource != null && pTarget == null))
		//	throw new Error("Source represented and target not represented, or the opposite");
		boolean pRepresented = ( (pSource != null) || (pTarget != null)); 
		boolean sRepresented = (repS != null);
		boolean sTyped = ((n2cs.get(t.s) != null));
		boolean oRepresented = (repO != null);
		boolean oTyped = ((n2cs.get(t.o) != null));

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sTyped,
				pRepresented, oRepresented, oTyped);
		System.out.println("Case: " + this.caseName(caseNumber));
		switch (caseNumber) {
		case TRS_UP_TRO: // six cases for TRS
			handleDataTriple_TRS_UP_TRO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_UP_RO:
			handleDataTriple_TRS_UP_RO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_UP_UO:
			handleDataTriple_TRS_UP_UO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_RP_TRO:
			handleDataTriple_TRS_RP_TRO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_RP_UO:
			handleDataTriple_TRS_RP_UO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_RP_RO:
			handleDataTriple_TRS_RP_RO(t, repS, repO, pSource, pTarget);
			break;
			// six cases for RS: 
		case RS_UP_UO:
			handleDataTriple_RS_UP_UO(t);
			break;
		case RS_UP_RO:
			handleDataTriple_RS_UP_RO(t);
			break;
		case RS_UP_TRO:
			handleDataTriple_RS_UP_TRO(t, repS, repO, pSource, pTarget);
			break;
		case RS_RP_UO:
			handleDataTriple_RS_RP_UO(t);
			break;
		case RS_RP_RO:
			handleDataTriple_RS_RP_RO(t);
			break;
		case RS_RP_TRO:
			handleDataTriple_RS_RP_TRO(t, repS, repO, pSource, pTarget);
			break;
			// six cases for US:  
		case US_UP_UO: 
			handleDataTriple_US_UP_UO(t);
			break;
		case US_UP_RO:
			handleDataTriple_US_UP_RO(t);
			break;
		case US_UP_TRO:
			handleDataTriple_US_UP_TRO(t, repS, repO, pSource, pTarget);
			break;
		case US_RP_UO: 
			handleDataTriple_US_RP_UO(t);
			break;
		case US_RP_RO:
			handleDataTriple_US_RP_RO(t);
			break;
		case US_RP_TRO:
			handleDataTriple_US_RP_TRO(t, repS, repO, pSource, pTarget);
			break;


			//		case TRS_RO:
			//			handleDataTriple_TRS_RO(t);
			//			break;
			//		case TRS_TRO:
			//			handleDataTriple_TRS_TRO(t);
			//			break;
			//		case TRS_UO:
			//			handleDataTriple_TRS_UO(t);
			//			break;

		default:
			throw new IllegalStateException("This case should not be encountered here");
		}

		//Debugger.log("After processing triple " + t.toString() + ", we have:\n" + this.toString()); 
		//safetyCheck(); 
	}

	/**
	 * In this case we need to: represent the subject by the property source if it exists, otherwise, create a new node and also register it as the source of p; 
	 * add a p edge between this and the typed object, if not already there
	 */
	private void handleDataTriple_US_RP_TRO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		Long sourceP = ps.get(t.p);
		if (sourceP != null){
			rep.put(t.s, sourceP);
		}
		else{
			sourceP = this.getNextSummaryNode();
			rep.put(t.s, sourceP);
			ps.put(t.p, sourceP); 
		}
		this.addTripleAndCheck(sourceP, t.p, repO); 
	}

	/**
	 * In this case we need to: create the source of p; we don't create a target for it.
	 * We represent s by the source of p, and add an edge from that to repO. 
	 */
	private void handleDataTriple_US_UP_TRO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		Long sourceP = this.getNextSummaryNode();
		ps.put(t.p, sourceP);
		rep.put(t.s, sourceP);
		this.addTripleAndCheck(sourceP, t.p, repO);
	}

	/**
	 * In this case we may have to fuse things between repS and the source of P
	 * RepO remains unchanged. 
	 */
	private void handleDataTriple_RS_RP_TRO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		Long addedTripleSource = sourceP;
		Long addedTripleTarget = repO;

		if (sourceP != null){
			Substitutions subs = new Substitutions(sourceP, repS);
			//System.out.println("Substitutions: " + subs.toString());

			// update added triple source, if needed
			Long possibleNewAddedTripleSource = subs.get(addedTripleSource);
			if (possibleNewAddedTripleSource != null)
				addedTripleSource = possibleNewAddedTripleSource;

			// apply replacements, if any
			applySubstitutions(subs, t.p);
			// try to add the resulting triple
		}
		else{
			sourceP = repS; 
			addedTripleSource = repS; 
			ps.put(t.p, sourceP); 
		}
		this.addTripleAndCheck(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to: use repS as the source of P; we don't know a target for p.
	 * We add the edge. 
	 */
	private void handleDataTriple_RS_UP_TRO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = repO;
		ps.put(t.p, repS);
		this.addTripleAndCheck(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to possibly fuse the target of p with repO.
	 * The source of p (if it exists)  remains unchanged.
	 */
	private void handleDataTriple_TRS_RP_RO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = repO;

		if (targetP != null){
			Substitutions subs = new Substitutions(repS, repS, targetP, repO);
			//System.out.println("Substitutions: " + subs.toString());

			// update added triple  target, if needed
			Long possibleNewAddedTripleTarget = subs.get(addedTripleTarget);
			if (possibleNewAddedTripleTarget != null)
				addedTripleTarget = possibleNewAddedTripleTarget;
			// apply replacements, if any
			applySubstitutions(subs, t.p);
			// try to add the resulting triple
		}
		else{
			targetP = repO;
			pt.put(t.p, targetP); 
		}
		this.addTripleAndCheck(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to represent o by the target of p, and add the edge from repS to that node.
	 * The source of p (if it exists) is not affected.
	 * The target of p, if it did not exist, may become the representative of o.  
	 */
	private void handleDataTriple_TRS_RP_UO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = targetP;

		if (targetP == null){
			targetP = this.getNextSummaryNode();
			pt.put(t.p, targetP); 
			addedTripleTarget = targetP; 
		}
		rep.put(t.o, targetP);

		// try to add the resulting triple
		this.addTripleAndCheck(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to add a p triple (if not already there) between repS and repO. 
	 * The source of p (if it exists) is not affected.
	 * The target of p (if it exists) is not affected. 
	 */
	private void handleDataTriple_TRS_RP_TRO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		this.addTripleAndCheck(repS, t.p, repO);
	}

	/**
	 * In this case we need to create the target of p and represent o by it.
	 * We do not create a source of p.  
	 */
	private void handleDataTriple_TRS_UP_UO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		Long targetP = this.getNextSummaryNode();
		pt.put(t.p, targetP);
		rep.put(t.o, targetP);
		this.addTripleAndCheck(repS, t.p,targetP);
	}

	/**
	 * In this case we need to use repO as the target of p, and do nothing about p's source.
	 */
	private void handleDataTriple_TRS_UP_RO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		pt.put(t.p, repO);
		this.addTripleAndCheck(repS, t.p, repO);
	}

	/**
	 * In this case we just add the triple; we do not modify its source nor its target
	 */
	private void handleDataTriple_TRS_UP_TRO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		this.addTripleAndCheck(repS, t.p, repO);
	}

	String caseName(char c) { 
		switch (c) {
		case TRS_UP_TRO: 
			return "TRS_UP_TRO"; 
		case TRS_UP_RO:
			return "TRS_UP_RO"; 
		case TRS_UP_UO:
			return "TRS_UP_UO"; 
		case TRS_RP_TRO:
			return "TRS_RP_TRO"; 
		case TRS_RP_UO:
			return "TRS_RP_UO"; 
		case TRS_RP_RO:
			return "TRS_RP_RO"; 
		case RS_UP_UO:
			return "RS_UP_UO"; 
		case RS_UP_RO:
			return "RS_UP_RO"; 
		case RS_UP_TRO:
			return "RS_UP_TRO"; 
		case RS_RP_UO:
			return "RS_RP_UO"; 
		case RS_RP_RO:
			return "RS_RP_RO"; 
		case RS_RP_TRO: 
			return "RS_RP_TRO";  
		case US_UP_UO: 
			return "US_UP_UO";
		case US_UP_RO:
			return "US_UP_RO";
		case US_UP_TRO:
			return "US_UP_TRO";
		case US_RP_UO:
			return "US_RP_UO";
		case US_RP_RO:
			return "US_RP_RO";
		case TRS_RO:
			return("TRS_RO");
		case TRS_TRO:
			return("TRS_TRO");
		case TRS_UO:
			return("TRS_UO");
		}
		throw new IllegalStateException("Unrecognized case " + c);
	}	


	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean sTyped,
			boolean pRepresented, boolean oRepresented, boolean oTyped) {
		if (sRepresented){
			if (sTyped) {// in this case, the edge will not change the source of p, but it may change its target
				if (pRepresented){
					if (oRepresented){
						if (oTyped){
							return TRS_RP_TRO; 
						}
						return TRS_RP_RO; 
					}
					else{ // o unrepresented => o untyped
						return TRS_RP_UO; 
					}
				}
				else{ // s represented, typed, p unrepresented 
					if (oRepresented){
						if (oTyped){
							return TRS_UP_TRO; 
						}
						else{ // o unrepresented => untyped
							return TRS_UP_RO; 
						}
					}
					else{ // s represented, typed, p unrepresented, o unrepresented => untyped
						return TRS_UP_UO; 
					}
				}
			}
			else{ // s represented, untyped
				if (pRepresented){
					if (oRepresented){
						if (oTyped){
							return RS_RP_TRO; 
						}
						return RS_RP_RO; 
					}
					else{ // o unrepresented => o untyped
						return RS_RP_UO; 
					}
				}
				else{ // s represented, typed, p unrepresented 
					if (oRepresented){
						if (oTyped){
							return RS_UP_TRO; 
						}
						else{ // o unrepresented => untyped
							return RS_UP_RO; 
						}
					}
					else{ // s represented, typed, p unrepresented, o unrepresented => untyped
						return RS_UP_UO; 
					}
				}
			}
		}
		else{ // s unrepresented => untyped
			if (pRepresented){
				if (oRepresented){
					if (oTyped){
						return US_RP_TRO; 
					}
					return US_RP_RO; 
				}
				else{ // o unrepresented => o untyped
					return US_RP_UO; 
				}
			}
			else{ // s represented, typed, p unrepresented 
				if (oRepresented){
					if (oTyped){
						return US_UP_TRO; 
					}
					else{ // o unrepresented => untyped
						return US_UP_RO; 
					}
				}
				else{ // s represented, typed, p unrepresented, o unrepresented => untyped
					return US_UP_UO; 
				}
			}
		}
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

	@Override
	protected void consistencyChecks() {
		for (Long s: edges.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edges.get(s);
			if (triplesOfThisSubject == null)
				throw new IllegalStateException("No triples whose subject is " + s);
			for (Long p: triplesOfThisSubject.keySet()) {
				TreeSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if ((objectsOfThisSandP.size() > 1) && RDF2SQLEncoding.isDataProperty(p)
						&& (n2cs.get(s) == null)) // only check for untyped nodes 
					throw new IllegalStateException("Subject " + s + " has more than one edge with label " + p);
				for (Long o: objectsOfThisSandP) {
					if (!this.ps.get(p).equals(s))
						throw new IllegalStateException("Source of " + p + " is not " + s + " but " + this.ps.get(p));
					if (pt.get(p) == null)
						throw new IllegalStateException("No target for " + p);
					if (!this.pt.get(p).equals(o))
						throw new IllegalStateException("Target of " + p + " is not " + o + " but " + this.pt.get(p));
				}
			}
		}
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
