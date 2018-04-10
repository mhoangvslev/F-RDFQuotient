package fr.inria.cedar.quotientSummary.weak;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.TreeSet;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.util.HashMap; 

public class TypedWeakSummary extends WeakOrTypedWeakSummary {


	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set	
	Long2Long n2cs; // for each node, its class set ID. This is also the rep function for typed nodes
	Long2LongSet c2cs; // class to enclosing class sets

	// a subject that is typed has been represented before the data triples are traversed.
	// such a subject representative should never be merged with the source of a data property
	// nor should it involve the source and target of the data property
	protected final static char TRS_RO = 9;
	protected final static char TRS_TRO = 10;
	protected final static char TRS_UO = 11;

	public TypedWeakSummary() {
		super(); 
		cs = new Long2LongSet(); 
		n2cs = new Long2Long(); 
		c2cs = new Long2LongSet(); 
		this.summaryTablePrefix = TYPED_WEAK_SUMMARY_PREFIX; 
	}

	/**
	 * This must be used to read a TW summary from Postgres. 
	 * @param conn
	 */
	public  TypedWeakSummary (Connection conn) {
		Debugger.log("Reading TypedWeak summary from Postgres, setting up special URIs from the dictionary");
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
			throw new IllegalStateException("Unable to read TypedWeak summary from Postgres " + e.getStackTrace()); 
		}
		System.out.println("Read TypedWeak summary from Postgres"); 
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

	protected void handleDataTriple(Triple t) {
		//Debugger.log("### Data triple: " + t.toString());
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p);
		if ((pSource == null && pTarget != null) ||(pSource != null && pTarget == null)){
			throw new Error("Source represented and target not represented, or the opposite"); 
		}
		boolean pRepresented = (pSource == null ? false: true); 
		boolean sRepresented = (repS == null ? false: true); 
		boolean sTyped = ((n2cs.get(t.s) == null)? false:true); 
		boolean oRepresented = (repO == null? false: true); 
		boolean oTyped = ((n2cs.get(t.o) == null)? false:true); 

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sTyped, 
				pRepresented, oRepresented, oTyped); 
		switch(caseNumber){
		case US_UP_UO: handleDataTriple_US_UP_UO(t); break; 
		case US_UP_RO: handleDataTriple_US_UP_RO(t); break; 
		case US_RP_UO: handleDataTriple_US_RP_UO(t); break; 
		case US_RP_RO: handleDataTriple_US_RP_RO(t); break; 
		case RS_UP_UO: handleDataTriple_RS_UP_UO(t); break; 
		case RS_UP_RO: handleDataTriple_RS_UP_RO(t); break; 
		case RS_RP_UO: handleDataTriple_RS_RP_UO(t); break; 
		case RS_RP_RO: handleDataTriple_RS_RP_RO(t); break; 
		case TRS_RO: handleDataTriple_TRS_RO(t); break; 
		case TRS_TRO: handleDataTriple_TRS_TRO(t); break; 
		case TRS_UO: handleDataTriple_TRS_UO(t); break; 
		default: throw new IllegalStateException("This case should not be encountered here"); 
		}

		//Debugger.log("After processing triple " + t.toString() + ", we have:\n" + this.toString()); 
		//safetyCheck(); 
	}

	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean sTyped,
			boolean pRepresented, boolean oRepresented, boolean oTyped) {
		if (sRepresented){
			if (sTyped) { // in this case, the edge will not change the source or target of p
				if (oRepresented) {
					if (oTyped) {
						return TRS_TRO;
					}
					else {
						return TRS_RO; 
					}
				}
				else {
					return TRS_UO; 
				}
			}
			else { // s represented, s not typed
				if (pRepresented){
					if (oRepresented){
						return RS_RP_RO; 
					}
					return RS_RP_UO; 
				}
				if (oRepresented){
					return RS_UP_RO;
				}
				return RS_UP_UO; 
			}
		}
		if (pRepresented){
			if (oRepresented){
				return US_RP_RO; 
			}
			return US_RP_UO; 
		}
		if (oRepresented){
			return US_UP_RO; 
		}
		return US_UP_UO; 
	}



	protected void handleDataTriple_TRS_RO(Triple t) {
		//Debugger.log("============ TRS_RO " + t.toString());
		// everything has been represented and the subject is typed. In this case we must:
		// - reuse the subject no matter what; it is represented for its types. 
		// - reuse the object
		Long source = rep.get(t.s); 
		Long target = rep.get(t.o); 
		this.addTripleAndCheck(source, t.p, target);
	}

	protected void handleDataTriple_TRS_TRO(Triple t) {
		Long source = rep.get(t.s); 
		Long target = rep.get(t.o); 
		addTripleAndCheck(source, t.p, target); 
		//Debugger.log("TRS_RP_UO 2. After replacement and triple addition (1)\n" + this.toString());
	}

	protected void handleDataTriple_TRS_UO(Triple t) {
		//Debugger.log("TRS_UO");
		// the subject is typed and  represented.
		// The object has not been represented, nor the property. 
		Long source = rep.get(t.s); 
		// we take the next number but we may not use it in the end
		Long target = this.getNextSummaryNode(); 
		boolean addToSummary = true; 
		// we need to figure out if the representative of o needs creation or not.
		// we don't create it if this subject already had property p defined on it.
		HashMap<Long, ArrayList<Long>> edgesOfS = edges.get(source);
		if(edgesOfS != null) {
			//Debugger.log(t.s + " had edges");
			ArrayList<Long> pValuesForS = edgesOfS.get(t.p);
			if (pValuesForS != null) { // in this case, target is overwritten with the existing node
				//Debugger.log(t.s + " had edges for " + t.p);
				target = pValuesForS.get(0); 
				//Debugger.log("Reusing target " + target);
				addToSummary = false; 
			}
		}
		rep.put(t.o, target); 
		// no writing in PS nor TS
		if (addToSummary) {
			//Debugger.log("Added triple " + source + " "+ t.p + " " + target); 
			addTripleAndCheck(source, t.p, target); 
		}
	}


	/**
	 * @param typeTriplesFile
	 * @param dataTriplesFile
	 * @param method
	 * @throws FileNotFoundException 
	 * @throws IOException 
	 */
	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile) {
		long start = System.currentTimeMillis(); 
		try {

			// First file: type triples
			try (BufferedReader br = new BufferedReader(new FileReader(new File(typeTriplesFile)))) {
				while (br.ready()){
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
		catch(IOException e) {
			throw new IllegalStateException("Could not exploit file " + typeTriplesFile); 
		}
		// this is the one who actually puts type triples in the summary	
		postHandleTypeTriples();

		//  Second file: data triples	
		try (BufferedReader br = new BufferedReader(new FileReader(new File(dataTriplesFile)))) {
			while (br.ready()){
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

		catch(IOException e) {
			throw new IllegalStateException("Unable to open file " + dataTriplesFile + " or " + typeTriplesFile + ": " + e.toString()); 
		}
		long stop = System.currentTimeMillis();
		System.out.println("Typed weak summarization took: "+ (stop - start));
		display(dataTriplesFile); // this prints out and makes a DOT file
	}

	public void handleTypeTripleBeforeData(Triple t){
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
		//display();
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
	protected void consistencyChecks(){
		for (Long s: edges.keySet()){
			HashMap<Long, ArrayList<Long>> triplesOfThisSubject = edges.get(s); 
			if (triplesOfThisSubject == null){
				throw new IllegalStateException("No triples whose subject is " + s); 
			}
			for (Long p: triplesOfThisSubject.keySet()){
				ArrayList<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if ((objectsOfThisSandP.size() > 1) && isDataProperty(p)
						&& (n2cs.get(s) == null)) { // only check for untyped nodes 
					throw new IllegalStateException("Subject " + s + " has more than one edge with label " + p); 
				}
				for (Long o: objectsOfThisSandP){
					if (!this.ps.get(p).equals(s)){
						throw new IllegalStateException("Source of " + p + " is not " + s + " but " + this.ps.get(p)); 
					}
					if (pt.get(p) == null) {
						throw new IllegalStateException("No target for " + p); 
					}
					if (!this.pt.get(p).equals(o)){
						throw new IllegalStateException("Target of " + p + " is not " + o + " but " + this.pt.get(p)); 
					}
				}
			}
		}
	}
}
