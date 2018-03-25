package fr.inria.cedar.quotientSummary.strong;

import java.sql.Connection;

import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Triple;

public class StrongSummary extends StrongOrTypedStrongSummary {

	/**
	 * This must be used to read a summary from Postgres. It is based on the core summary population method of the root summary class,
	 * then we just steal its edges.
	 * @param conn
	 */
	public  StrongSummary (Connection conn) {
		Summary s = Summary.readSummaryFromPostgres(conn);
		this.edges = s.getEdgesAsInternallyStored(); 
	}

	public StrongSummary() {
		super(); 
		this.summaryTablePrefix = "s_"; 
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
