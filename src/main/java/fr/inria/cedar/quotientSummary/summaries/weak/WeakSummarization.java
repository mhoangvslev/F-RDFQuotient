package fr.inria.cedar.quotientSummary.summaries.weak;

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
import java.util.HashMap;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

/**
 * Weak and typed weak summarization
 * 
 * @author ioanamanolescu
 *
 */
public class WeakSummarization extends fr.inria.cedar.quotientSummary.summaries.Summarization  {
	HashMap<Long, Long> ps; // for each property, the property source
	HashMap<Long, Long> pt; // for each property, the property source	
		
	long minSummaryNode; 

	private final static char US_UP_UO = 1;
	private final static char US_UP_RO = 2;
	private final static char US_RP_UO = 3;
	private final static char US_RP_RO = 4;
	private final static char RS_UP_UO = 5;
	private final static char RS_UP_RO = 6;
	private final static char RS_RP_UO = 7;
	private final static char RS_RP_RO = 8;

	// for debugging
	long globalTripleCount; 
	
	public WeakSummarization(){
		super(); 
		ps = new HashMap<>(); 
		pt = new HashMap<>(); 
		
		numberOfDataTriplesRead=0;
		numberOfTypeTriplesRead=0; 
		minSummaryNode = -1; 

	}
	
	@Override
	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile, String method) throws FileNotFoundException, IOException{
		summarizeFromTripleFiles(typeTriplesFile, dataTriplesFile); 
	}
	
	/**
	 * @param typeTriplesFile
	 * @param dataTriplesFile
	 * @param method
	 * @throws FileNotFoundException 
	 * @throws IOException 
	 */
	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile) throws FileNotFoundException, IOException{
		long start = System.currentTimeMillis(); 
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

		// First file: type triples
		try (BufferedReader br = new BufferedReader(new FileReader(new File(typeTriplesFile)))) {
			while (br.ready()){
				String spo = br.readLine();
				Triple t = readTriple(spo);
				//t.display();
				handleTypeTriplesAfterData(t);
				//System.out.println();
			}
		}
		//System.out.println("=== After weak type triple summarization of " + typeTriplesFile + ": =================================== ");
		//display();

		long stop = System.currentTimeMillis();
		System.out.println("Weak summarization took: "+ (stop - start));
		display(dataTriplesFile); // this prints out and makes a DOT file
	}

	private void handleDataTriple(Triple t) {
		//if (globalTripleCount >= 28835) {
		//	Debugger.turnOn();
		//	Debugger.log("Before processing " +t.toString());
		//	Debugger.log(this.toString());
		//}
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p);
		if ((pSource == null && pTarget != null) ||(pSource != null && pTarget == null)){
			throw new Error("Source represented and target not represented, or the opposite"); 
		}
		boolean pRepresented = (pSource == null ? false: true); 
		boolean sRepresented = (repS == null ? false: true); 
		boolean oRepresented = (repO == null? false: true); 

		char caseNumber = decode(sRepresented, pRepresented, oRepresented); 
		switch(caseNumber){
		case US_UP_UO: handleDataTriple_US_UP_UO(t); break; 
		case US_UP_RO: handleDataTriple_US_UP_RO(t); break; 
		case US_RP_UO: handleDataTriple_US_RP_UO(t); break; 
		case US_RP_RO: handleDataTriple_US_RP_RO(t); break; 
		case RS_UP_UO: handleDataTriple_RS_UP_UO(t); break; 
		case RS_UP_RO: handleDataTriple_RS_UP_RO(t); break; 
		case RS_RP_UO: handleDataTriple_RS_RP_UO(t); break; 
		case RS_RP_RO: handleDataTriple_RS_RP_RO(t); break; 
		}
		
		Debugger.log("After processing triple " + t.toString() + ", we have:\n" + this.toString()); 
		safetyCheck(); 
	}
	
	private void replaceAll(Long oldNode, Long newNode, Long forProperty){
		Debugger.log("WEAK REPLACE-ALL " + oldNode + " with " + newNode + " for property " + forProperty + " in: ");
		Debugger.log(this.toString());
		replaceInSummary(oldNode, newNode);
		//Debugger.log("Representation was: "); 
		//showRep(); 
		rep.replaceValue(oldNode, newNode); 
		// now we need to replace oldNode with newNode in the property source and target. It does not suffice to do it for one property.
		if (ps.containsValue(oldNode)) {
			for (Long prop: ps.keySet()) {
				if (ps.get(prop).equals(oldNode)){
					ps.replace(prop, newNode); 
					Debugger.log("Now source of " + prop + " is " + ps.get(forProperty));
				}
			}
		}
		if (pt.containsValue(oldNode)) {
			for (Long prop: pt.keySet()) {
				if (pt.get(prop).equals(oldNode)){
					pt.replace(prop, newNode); 
					Debugger.log("Now target of " + prop + " is " + ps.get(forProperty));
				}
			}
		}
	}

	private void handleDataTriple_RS_RP_RO(Triple t) {
		Debugger.log("============ RS_RP_RO " + t.toString());
		// everything has been represented. In this case we must:
		// - fuse the subject of p with the representative of s (keep the smallest)
		// - fuse the object of p with the representative of s (keep the smallest)
		Long targetP = pt.get(t.p); 
		Long sourceP = ps.get(t.p);
		Long repS = rep.get(t.s); 
		Long repO = rep.get(t.o); 
		
		if (sourceP < repS){
			replaceAll(repS, sourceP, t.p);
			if (targetP < repO){
				replaceAll(repO, targetP, t.p);
				this.addTripleAndCheck(sourceP, t.p, targetP);
			}
			else{//repO <= targetP
				if (targetP > repO){ // replace if not equal
					replaceAll(targetP, repO, t.p);
				}	
				this.addTripleAndCheck(sourceP, t.p, repO);
			}
		}
		else{// sourceP >= repS
			if (sourceP > repS){
				replaceAll(sourceP, repS, t.p);
			}
			if (targetP < repO){
				replaceAll(repO, targetP, t.p); 
				this.addTripleAndCheck(repS, t.p, targetP);
			}
			else{ // repO <= targetP
				if (targetP > repO){ // replace if not equal
					replaceAll(targetP, repO, t.p); 
				}
				// add this triple in any case
				this.addTripleAndCheck(repS, t.p, repO);
			}
		}

	}

	private void handleDataTriple_RS_RP_UO(Triple t) {
		Debugger.log("================== RS_RP_UO on " + t.toString() + " starts on");
		Debugger.log(this.toString()); 
		safetyCheck(); 
		
		// the subject and property have been represented, not the object. In this case we must:
		// - represent the object by the target of the property 
		// - fuse the source of p with the representative of s. By convention, we will keep the *** smaller *** one. 
		Long targetP = pt.get(t.p); 
		rep.put(t.o, targetP);
		
		Long sourceP = ps.get(t.p);
		long repS = rep.get(t.s); 
		Debugger.log("RS_RP_UO 1. repS: " + repS + " sourceP: " + sourceP + " we should keep the smaller"); 
		Debugger.log("RS_RP_UO 2. targetP: " + targetP);
		if (repS < sourceP){ // we keep repS, replace sourceP with repS all over
			Debugger.log("RS_RP_UO 3. Replacing " + sourceP + " with " + repS); 
			replaceAll(sourceP, repS, t.p); 
			Debugger.log("RS_RP_UO 4. After replacement but before triple addition (1)\n" + this.toString());
			addTripleAndCheck(repS, t.p, targetP); 
			Debugger.log("RS_RP_UO 5. After replacement and triple addition (1)\n" + this.toString());
		}
		else{ 
			if (repS > sourceP ) { // we keep sourceP, replace repS with sourceP all over
				Debugger.log("RS_RP_UO 6. Replacing " + repS + " with " + sourceP); 
				replaceAll(repS, sourceP, t.p);
				Debugger.log("RS_RP_UO 7. After replacement but before triple addition (2)\n" + this.toString());
			}
			// add the edge in any case
			addTripleAndCheck(sourceP, t.p, targetP); 
			Debugger.log("RS_RP_UO 8. After replacement and triple addition (2)\n" + this.toString());
			safetyCheck();
		}
	}

	private void handleDataTriple_RS_UP_RO(Triple t) {
		Debugger.log("RS_UP_RO");
		// the subject and object have been represented, not the property
		// represent the property by the subject and object codes
		Long sourceP = rep.get(t.s);
		Long targetP = rep.get(t.o); 
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP);
		addTripleAndCheck(sourceP, t.p, targetP); 
	}


	private void handleDataTriple_RS_UP_UO(Triple t) {
		Debugger.log("RS_UP_UO");
		// the subject has been represented, not the object nor the property
		// we need to create the property target, represent o by this
		Long sourceP = rep.get(t.s); 
		Long targetP = this.getNextSummaryNode(); 
		rep.put(t.o, targetP); 
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP); 
		addTripleAndCheck(sourceP, t.p, targetP); 
	}

	private void handleDataTriple_US_RP_RO(Triple t) {
		Debugger.log("US_RP_RO");
		// the property and the object have been seen, not the subject. In this case we must:

		// - represent the subject by the source of the property	
		Long sourceP = ps.get(t.p); 
		rep.put(t.s, sourceP); 

		// - fuse the target of p with the representative of o. By convention we will keep the *** smaller *** one. 
		Long targetP = pt.get(t.p); 
		Long repO = rep.get(t.o); 
		if (repO > targetP){ // we keep targetP, we need to replace repO  with targetP, all over the summary
			replaceAll(repO, targetP, t.p); 
			addTripleAndCheck(sourceP, t.p, targetP); 
		}
		else{ 
			if (repO < targetP){
				// we keep repO, we need to replace targetP with repO all over in the summary
				replaceAll(targetP, repO, t.p); 
			}
			// add the edge in any case
			addTripleAndCheck(sourceP, t.p, repO); 
			// and we represent o by repO: nothing needed, it was already the case
		}
	}

	private void handleDataTriple_US_RP_UO(Triple t) {
		Debugger.log("US_RP_UO");
		// the property has been seen so far, not the subject nor the object
		// in this case we need to represent s by the source of p and o by the target of p
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p); 
		rep.put(t.s, pSource);
		rep.put(t.o, pTarget); 
		addTripleAndCheck(pSource, t.p, pTarget); 
	}

	private void handleDataTriple_US_UP_RO(Triple t) {
		Debugger.log("US_UP_RO");
		// only the object has been seen so far: it must have been seen as the target of *another* property.  
		// We need to: mark the target of p as the target of that property: 
		Long pTarget = rep.get(t.o); 
		pt.put(t.p, pTarget); 
		
		// create source for p; represent the subject by that source; 
		Long pSource = this.getNextSummaryNode(); 
		ps.put(t.p, pSource);
		rep.put(t.s, pSource);
		addTripleAndCheck(pSource, t.p, pTarget); 
	}

	private void handleDataTriple_US_UP_UO(Triple t) {
		Debugger.log("US_UP_UO");
		// nothing has been seen so far
		Long pSource = this.getNextSummaryNode(); 
		Long pTarget = this.getNextSummaryNode(); 
		ps.put(t.p, pSource);
		pt.put(t.p, pTarget);
		rep.put(t.s, pSource);
		rep.put(t.o, pTarget);
		addTripleAndCheck(pSource, t.p, pTarget); 
	}

	private void addTripleAndCheck(Long s, long p, Long o) {
		addTriple(s, p, o); 
		safetyCheck(); //This may have been called too early
	}

	private char decode(boolean sRepresented, boolean pRepresented, boolean oRepresented) {
		if (sRepresented){
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

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 * @param conn
	 * @param args
	 * @throws SQLException
	 * @throws IOException
	 */
	public void summarizeFromRDBMS(Connection conn, String[] args) throws SQLException, IOException {
		String dataTriplesFileName = args[0];
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn); 
		long typeConstantCode = RDF2SQLEncoding.getTypeCode(); 
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true; 
		}
		String getUntypedTriplesString = ("select *  from encoded_triples where p <> " + typeConstantCode); 
		try(Statement getUntypedTriples = conn.createStatement(); 
			ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
			globalTripleCount = 0;
			while (rs.next()){
				long s = -1; 
				long p = -1; 
				long o = -1; 
			
				try{
					s = rs.getInt(1);	
				}
				catch(Exception e){
					e.printStackTrace(); 
				}
				
				try{
					p = rs.getInt(2);	
				}
				catch(Exception e){
					e.printStackTrace(); 
				}
				
				try{
					o = rs.getInt(3);	
				}
				catch(Exception e){
					e.printStackTrace(); 
				}
				Triple t = new Triple(s, p, o); 
				try{
					Debugger.log("#### Triple " + t.toString());
					handleDataTriple(t); 
				}
				catch(Exception e){
					e.printStackTrace(); 
				}
				//Files.write(Paths.get("output.txt"), (globalTripleCount + ": " + new String(s + " " + p + " " + o + "\n")).getBytes(), StandardOpenOption.APPEND); 
				globalTripleCount++; 
				//if ((globalTripleCount % 1000 == 0)) {//|| (globalTripleCount > 28800)) {
				//	System.out.println(globalTripleCount + " triples");
				//}
			}
			System.out.println("Summarized " + globalTripleCount + " triples.");
		}

		String getTypedTriplesString = ("select *  from encoded_triples where p=" + typeConstantCode); 
		try(Statement getTypedTriples = conn.createStatement(); 
			ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
			while (rs.next()){	
				long s = rs.getInt(1);	
				long p = rs.getInt(2);	
				long o = rs.getInt(3);	
				Triple t = new Triple(s, p, o); 
				try{
					Debugger.log("#### Type triple " + t.toString());
					this.handleTypeTriplesAfterData(t);
				}
				catch(Exception e){
					e.printStackTrace(); 
				}
			}
		}
		this.display(dataTriplesFileName);
	}
	void safetyCheck(){
		for (Long s: edges.keySet()){
			HashMap<Long, ArrayList<Long>> triplesOfThisSubject = edges.get(s); 
			if (triplesOfThisSubject == null){
				throw new Error("No triples whose subject is " + s); 
			}
			for (Long p: triplesOfThisSubject.keySet()){
				ArrayList<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if ((objectsOfThisSandP.size() > 1) && isDataProperty(p)){
					throw new Error("Subject " + s + " has more than one edge with label " + p); 
				}
				for (Long o: objectsOfThisSandP){
					if (!this.ps.get(p).equals(s)){
						throw new Error("Source of " + p + " is not " + s + " but " + this.ps.get(p)); 
					}
					if (pt.get(p) == null) {
						throw new Error("No target for " + p); 
					}
					if (!this.pt.get(p).equals(o)){
						throw new Error("Target of " + p + " is not " + o + " but " + this.pt.get(p)); 
					}
				}
			}
		}
	}

	@Override
	public ArrayList<Triple> getSummaryEdges() {
		ArrayList<Triple> res = new ArrayList<>();
		for (Long s: edges.keySet()){
			HashMap<Long, ArrayList<Long>> triplesOfThisSubject = edges.get(s); 
			if (triplesOfThisSubject == null){
				throw new Error("No triples whose subject is " + s); 
			}
			for (Long p: triplesOfThisSubject.keySet()){
				ArrayList<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if (isDataProperty(p)) {
					if (objectsOfThisSandP.size() > 1){
						throw new Error("Subject " + s + " has more than one edge with label " + p); //TODO this holds just for the weak.
					}
				}
				for (Long o: objectsOfThisSandP){
					Triple t = new Triple(s, p, o);
					res.add(t);
				}
			}
		}
		return res; 
	}

	@Override
	public String toString(){
		StringBuffer sb = new StringBuffer();
		for (Triple t: getSummaryEdges()){
			sb.append(t.toString());
			sb.append("\n");
		}
		//sb.append("rep:\n");
		//this.showRepInBuffer(sb);
//		sb.append("\nProperty sources:\n");
//		for (Long l: ps.keySet()) {
//			sb.append(l + ": " + ps.get(l));
//			sb.append(" "); 
//		}
//		sb.append("\nProperty targets:\n");
//		for (Long l: pt.keySet()) {
//			sb.append(l + ": " + pt.get(l));
//			sb.append(" "); 
//		}
		return new String(sb); 
	}

	/** Reads an encoded weak summary from an .nt file 
	 *  TODO the method is currently insufficient as in the summary that has been read, the codes of special properties are not known.
	 *  Either fix by starting the serialization in a file with the five magic constants, or don't use for now.
	 *  Instead, use readSummaryFromPostgres (below).
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static WeakSummarization readSummaryFromFile(String[] args) throws IOException {
		WeakSummarization ws = new WeakSummarization(); 
		String summaryTripleFileName = args[0]; 
		System.out.println("Trying to read an encoded summary from file:" + summaryTripleFileName);
		try (BufferedReader br = new BufferedReader(new FileReader(new File(summaryTripleFileName)))) {
			while (br.ready()){
				String spo = br.readLine().replaceAll("<", "").replaceAll(">", ""); 
				Triple t = ws.readTriple(spo);
				ws.addTriple(t.s, t.p, t.o);
			}
		}
		return ws; 
	}

	public static WeakSummarization readSummaryFromPostgres(Connection conn) throws SQLException {
		Debugger.log("Trying to read summary from Postgres");
		WeakSummarization ws = new WeakSummarization(); 
		RDF2SQLEncoding.setUp(conn); 
		Debugger.log("Set up special URIs from dictionary"); 
		String getSummaryTriples = ("select *  from encoded_summary"); 
		try(Statement getTriples = conn.createStatement(); 
			// Debugger.log("Created statement");
			ResultSet rs = getTriples.executeQuery(getSummaryTriples)
			// Debugger.log("Asking for summary triples")
			) {
			while (rs.next()) {
				Long s = rs.getLong(1);
				Long p = rs.getLong(2); 
				Long o = rs.getLong(3);
				ws.addTriple(s, p, o);
			}
		}
		System.out.println("Read weak summary from Postgres"); 
		return ws; 
	}
	
}
