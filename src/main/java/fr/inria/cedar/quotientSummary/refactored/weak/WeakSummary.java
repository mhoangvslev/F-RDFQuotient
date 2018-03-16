package fr.inria.cedar.quotientSummary.refactored.weak;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.refactored.Summary;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

public class WeakSummary extends WeakOrTypedWeakSummary {

	/**
	 * This must be used to read a summary from Postgres. It is based on the core summary population method of the root summary class,
	 * then we just steal its edges.
	 * @param conn
	 */
	public  WeakSummary (Connection conn) {
		Summary s = Summary.readSummaryFromPostgres(conn);
		this.edges = s.getEdgesAsInternallyStored(); 
	}
	
	public WeakSummary() {
		super(); 
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
				handleTypeTripleAfterData(t);
				//System.out.println();
			}
		}
		//System.out.println("=== After weak type triple summarization of " + typeTriplesFile + ": =================================== ");
		//display();
		}
		catch(IOException e) {
			throw new IllegalStateException("Unable to open file " + dataTriplesFile + " or " + typeTriplesFile + ": " + e.toString()); 
		}
		long stop = System.currentTimeMillis();
		System.out.println("Weak summarization took: "+ (stop - start));
		display(dataTriplesFile); // this prints out and makes a DOT file
	}
	

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 * @param conn
	 * @param args
	 * @throws SQLException
	 * @throws IOException
	 */
	public void summarizeFromRDBMS(Connection conn, String[] args) {
		long start = System.currentTimeMillis(); 
		String dataTriplesFileName = args[0];
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn); 
		long typeConstantCode = RDF2SQLEncoding.getTypeCode(); 
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true; 
		}
		String getUntypedTriplesString = ("select *  from encoded_triples where p <> " + typeConstantCode); 
		try{
			conn.setAutoCommit(false);
			Statement getUntypedTriples = conn.createStatement(); 
			getUntypedTriples.setFetchSize(10000);
			ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString);
			globalTripleCount = 0;
			while (rs.next()){
				Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3)); 
				Debugger.log("#### Triple " + t.toString());
				handleDataTriple(t); 
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
		long afterDataTriples = System.currentTimeMillis(); 
		System.out.println("Summarized " + globalTripleCount + " data triples in " + (afterDataTriples - start) + " ms.");
 
		String getTypedTriplesString = ("select *  from encoded_triples where p=" + typeConstantCode); 
		try {
			Statement getTypedTriples = conn.createStatement(); 
			getTypedTriples.setFetchSize(1000);
			ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString);
			while (rs.next()){		
				Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3)); 
				//Debugger.log("#### Type triple " + t.toString());
				this.handleTypeTripleAfterData(t);
				globalTripleCount ++; 
			}
			rs.close();
			getTypedTriples.close();
		}
		catch(SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString()); 
		}
		System.out.println("Summarized " + globalTripleCount + " triples in " + (System.currentTimeMillis() - start)  + " ms."); 
		this.display(dataTriplesFileName);
	}
	

	/** This implementation should be shared by Weak and Strong
	 * 
	 * @param t
	 */
	protected void handleTypeTripleAfterData(Triple t) {
		Long repS = rep.get(t.s);
		if (repS != null){
			addTriple(repS, t.p, t.o);
		}
		else{
			if (!typeOnlyNodeAlreadySeen){
				this.typeOnlyNodeID = getNextSummaryNode();
				typeOnlyNodeAlreadySeen=true;
			}
			addTriple(typeOnlyNodeID, t.p, t.o); 
		}
		this.numberOfTypeTriplesRead++;
	}


}
