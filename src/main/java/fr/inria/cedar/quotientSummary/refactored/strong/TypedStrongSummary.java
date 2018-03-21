package fr.inria.cedar.quotientSummary.refactored.strong;

import java.sql.Connection;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.refactored.Summary;

public class TypedStrongSummary extends StrongOrTypedStrongSummary {
	
	/**
	 * This must be used to read a summary from Postgres. It is based on the core summary population method of the root summary class,
	 * then we just steal its edges.
	 * @param conn
	 */
	public  TypedStrongSummary (Connection conn) {
		Summary s = Summary.readSummaryFromPostgres(conn);
		this.edges = s.getEdgesAsInternallyStored(); 
	}
	
	public TypedStrongSummary() {
		super();
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX; 
	}

	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName()); 
	}
}
