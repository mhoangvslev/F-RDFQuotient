package fr.inria.cedar.quotientSummary.strong;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

public class StrongSummary extends StrongOrTypedStrongSummary {

	/**
	 * This must be used to read a S summary from Postgres. 
	 * @param conn
	 */
	public  StrongSummary (Connection conn) {

		this.summaryTablePrefix = STRONG_SUMMARY_PREFIX; 
		Debugger.log("Reading Strong summary from Postgres, setting up special URIs from the dictionary");
		RDF2SQLEncoding.setUp(conn); 
		String getSummaryTriples = getSummaryTriplesSQLQuery();
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
			throw new IllegalStateException("Unable to read Strong summary from Postgres " + e.getStackTrace()); 
		}
		System.out.println("Read Strong summary from Postgres"); 
	}

	public StrongSummary() {
		super(); 
		this.summaryTablePrefix = STRONG_SUMMARY_PREFIX; 
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
	}

}
