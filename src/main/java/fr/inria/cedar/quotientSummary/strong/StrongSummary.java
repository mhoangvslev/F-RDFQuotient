package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class StrongSummary extends StrongOrTypedStrongSummary {
	/**
	 * This must be used to read a S summary from Postgres.
	 *
	 * @param conn
	 */
	public StrongSummary(Connection conn) {

		this.summaryTablePrefix = STRONG_SUMMARY_PREFIX;
		Debugger.log("Reading Strong summary from Postgres, setting up special URIs from the dictionary");
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
			throw new IllegalStateException("Unable to read Strong summary from Postgres " + e.getStackTrace());
		}
		System.out.println("Read Strong summary from Postgres");
	}

	public StrongSummary() {
		super();
		this.summaryTablePrefix = STRONG_SUMMARY_PREFIX;
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
		System.out.println(" dataTriplesFileName "+dataTriplesFileName);
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn); 
		long typeConstantCode = RDF2SQLEncoding.getTypeCode(); 
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true; 
			avoidCollisionsWhenAssigningSummaryNodes(conn); 
		}
		this.triplesSummarizedSoFar = 0; 
		String getUntypedTriplesString = ("select *  from encoded_triples where p <> " + typeConstantCode); 
		try{
			conn.setAutoCommit(false);
			Statement getUntypedTriples = conn.createStatement(); 
			getUntypedTriples.setFetchSize(10000);
			ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString);
			while (rs.next()){
				Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3)); 
				if ((t.p == RDF2SQLEncoding.getSubClassCode()) ||
						(t.p == RDF2SQLEncoding.getSubPropertyCode()) ||
						(t.p == RDF2SQLEncoding.getDomainCode()) ||
						(t.p == RDF2SQLEncoding.getRangeCode())) {
					//System.out.println("#### Schema triple " + t.toString());
					addTriple(t.s, t.p, t.o); 
				}
				else{
					//System.out.println("#### Data triple " + t.toString());
					handleDataTriple(t); 
				}
				//System.out.println("Summary has become: " + this.toString());
				triplesSummarizedSoFar ++; 
				//this.drawSummaryAndGraph(conn, dataTriplesFileName, ("-after-" + 
				//triplesSummarizedSoFar + "-"+ t.s + "-" + t.p + "-" + t.o));
				//Files.write(Paths.get("output.txt"), (globalTripleCount + ": " + new String(s + " " + p + " " + o + "\n")).getBytes(), StandardOpenOption.APPEND); 
				//if ((globalTripleCount % 1000 == 0)) {//|| (globalTripleCount > 28800)) {
				//	System.out.println(globalTripleCount + " triples");
				//}
				//System.out.println("Summary now has " + getSummaryEdges().size() + " triples");
			}
			rs.close();
			getUntypedTriples.close();
		}
		catch(SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString()); 
		}
		long afterDataTriples = System.currentTimeMillis(); 
		System.out.println("Summarized " + triplesSummarizedSoFar + " data triples in " + (afterDataTriples - start) + " ms.");

		String getTypedTriplesString = ("select *  from encoded_triples where p=" + typeConstantCode); 
		try {
			Statement getTypedTriples = conn.createStatement(); 
			getTypedTriples.setFetchSize(1000);
			ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString);
			while (rs.next()){		
				Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3)); 
				//System.out.println("#### Type triple " + t.toString());
				this.handleTypeTripleAfterData(t);
				triplesSummarizedSoFar ++; 
				//this.drawSummaryAndGraph(conn, dataTriplesFileName, ("-after-" + t.s + "-" + t.p + "-" + t.o));
				//System.out.println("Summary now has " + getSummaryEdges().size() + " triples");

			}
			rs.close();
			getTypedTriples.close();
		}
		catch(SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString()); 
		}
		System.out.println("Summarized " + triplesSummarizedSoFar + " triples in " + (System.currentTimeMillis() - start)  + " ms."); 
		this.display(dataTriplesFileName);
	}

	public void handleDataTriple(Triple t){
		// 8 cases: (US_RS, US_NS) x (UO_RO, UO_NO) x (RP, NP) 
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

		char caseNumber = decode(repS, repO, sourceCliqueP); 

		//Debugger.log("Case " + this.caseName(caseNumber));
		switch(caseNumber){
		case US_RS_UO_RO_RP: { handleDataTriple_US_RS_UO_RO_RP(t,  sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_UO_NO_RP: { handleDataTriple_US_RS_UO_NO_RP(t,  sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_UO_RO_RP: { handleDataTriple_US_NS_UO_RO_RP(t,  sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_UO_NO_RP: { handleDataTriple_US_NS_UO_NO_RP(t,  sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_UO_RO_NP: { handleDataTriple_US_RS_UO_RO_NP(t,  sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_RS_UO_NO_NP: { handleDataTriple_US_RS_UO_NO_NP(t,  sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_UO_RO_NP: { handleDataTriple_US_NS_UO_RO_NP(t,  sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		case US_NS_UO_NO_NP: { handleDataTriple_US_NS_UO_NO_NP(t,  sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); break; }
		default: throw new IllegalStateException("Unknown case " + caseNumber); 
		}
		//this.display();
	}

	/** This implementation should be shared by Weak and Strong
	 *
	 * @param t
	 */
	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		Long repS = rep.get(t.s);
		if (repS != null)
			addTriple(repS, t.p, t.o);
		else {
			if (!typeOnlyNodeAlreadySeen) {
				this.typeOnlyNodeID = getNextSummaryNode();
				typeOnlyNodeAlreadySeen = true;
			}
			addTriple(typeOnlyNodeID, t.p, t.o);
		}
	}

	private char decode(Long repS,  Long repO, Long sourceCliqueP) {
		if (repS != null) {//US, RS
			// US, RS, UO
			if (repO != null) { //RO
				if (sourceCliqueP != null) {
					return US_RS_UO_RO_RP; 
				}
				else {
					return US_RS_UO_RO_NP; 
				}
			}
			else { // NO
				if (sourceCliqueP != null) {
					return US_RS_UO_NO_RP; 
				}
				else {
					return US_RS_UO_NO_NP; 
				}
			}
		}
		else { //US, NS
			if (repO != null) { // RO
				if (sourceCliqueP != null) { // RP
					return US_NS_UO_RO_RP; 
				}
				else {
					return US_NS_UO_RO_NP;
				}
			}
			else { // NO
				if (sourceCliqueP != null) { // RP
					return US_NS_UO_NO_RP; 
				}
				else {
					return US_NS_UO_NO_NP; 
				}
			}
		}
	}


}
