package fr.inria.cedar.quotientSummary.weak;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.TreeSet;

public class WeakSummary extends WeakOrTypedWeakSummary {
	public WeakSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = WEAK_SUMMARY_PREFIX;
		typeOnlyNodeID = -1;
	}

	/**
	 * This must be used to read a W summary from Postgres.
	 * TODO make sure that any call to a summarization method made on a summary read from Postgres
	 * handles that error appropriately (explaining that this object does no longer do such things)
	 * @param conn
	 */
	public WeakSummary(Connection conn) {
		this.summaryTablePrefix = WEAK_SUMMARY_PREFIX;
		Debugger.log("Reading Weak summary from Postgres, setting up special URIs from the dictionary");
		RDF2SQLEncoding.setUp(conn, "dictionary");
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
				edgesWithProv.addTriple(s, p, o);
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to read Weak summary from Postgres " + getSummaryTriples
											+ " " + e.toString());
		}
		System.out.println("Read Weak summary from Postgres");
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn) {
		long start = System.currentTimeMillis();

		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true;
			avoidCollisionsWhenAssigningSummaryNodes(conn);
		}
		triplesSummarizedSoFar = 0;
		String getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			conn.setAutoCommit(false);
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						if ((t.p == RDF2SQLEncoding.getSubClassCode())
						|| (t.p == RDF2SQLEncoding.getSubPropertyCode())
						|| (t.p == RDF2SQLEncoding.getDomainCode())
						|| (t.p == RDF2SQLEncoding.getRangeCode())) {
							edgesWithProv.addTriple(t.s, t.p, t.o);
							storeSpecialNodesRepresentation(t, true);
						}
						else
							handleDataTriple(t);
						triplesSummarizedSoFar++;
						//display(); 
						//this.drawSummaryAndGraph(conn, "-after-" + triplesSummarizedSoFar + "-"+ t.s + "-" + t.p + "-" + t.o);
						if (this.checkConsistency)
							consistencyChecks();
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		dataTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + triplesSummarizedSoFar + " data triples in " + dataTriplesSummarizationTime + " ms");

		String getTypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p =" + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						this.handleTypeTripleAfterData(t);
						triplesSummarizedSoFar++;
						storeSpecialNodesRepresentation(t, false);
						//this.drawSummaryAndGraph(conn, "-after-" + t.s + "-" + t.p + "-" + t.o);
						//System.out.println("Summary now has " + getSummaryEdges().size() + " triples");
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		allTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + triplesSummarizedSoFar + " triples in " + allTriplesSummarizationTime + " ms");
		if (checkConsistency){
			consistencyChecks();
		}
		//this.display(dataTriplesFileName);
	}

	protected void handleDataTriple(Triple t) {
		//System.out.println("\n### Read data triple: " + t.toString());
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p);
		if ((pSource == null && pTarget != null) || (pSource != null && pTarget == null))
			throw new IllegalStateException("Source represented and target not represented, or the opposite");
		boolean pRepresented = (pSource != null);
		boolean sRepresented = (repS != null);
		boolean oRepresented = (repO != null);

		char caseNumber = identifyTripleSummarizationCase(sRepresented, pRepresented, oRepresented);
		//System.out.println(showCaseNumber(caseNumber));
		switch (caseNumber) {
			case US_UP_UO:
				handleDataTriple_US_UP_UO(t);
				break;
			case US_UP_RO:
				handleDataTriple_US_UP_RO(t);
				break;
			case US_RP_UO:
				handleDataTriple_US_RP_UO(t);
				break;
			case US_RP_RO:
				handleDataTriple_US_RP_RO(t);
				break;
			case RS_UP_UO:
				handleDataTriple_RS_UP_UO(t);
				break;
			case RS_UP_RO:
				handleDataTriple_RS_UP_RO(t);
				break;
			case RS_RP_UO:
				handleDataTriple_RS_RP_UO(t);
				break;
			case RS_RP_RO:
				handleDataTriple_RS_RP_RO(t);
				break;
			default:
				throw new IllegalStateException("This case should not be encountered here");
		}

		//System.out.println("After processing triple " + t.toString() + ", we have:\n" + this.toString()); 
		//safetyCheck(); 
	}

	private String showCaseNumber(char caseNumber) {
		switch (caseNumber) {
			case US_UP_UO:
				return "US_UP_UO";
			case US_UP_RO:
				return "US_UP_RO";
			case US_RP_UO:
				return "US_RP_UO";
			case US_RP_RO:
				return "US_RP_RO";
			case RS_UP_UO:
				return "RS_UP_UO";
			case RS_UP_RO:
				return "RS_UP_RO";
			case RS_RP_UO:
				return "RS_RP_UO";
			case RS_RP_RO:
				return "RS_RP_RO";
			default:
				throw new IllegalStateException("Unrecognized case " + caseNumber);
		}
	}

	/** This implementation should be shared by Weak and Strong
	 *
	 * @param t
	 */
	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		Long repS = rep.get(t.s);
		if (repS != null)
			edgesWithProv.addTriple(repS, t.p, t.o);
		else {
			if (!typeOnlyNodeAlreadySeen) {
				typeOnlyNodeID = getNextSummaryNode();
				typeOnlyNodeAlreadySeen = true;
			}
			edgesWithProv.addTriple(typeOnlyNodeID, t.p, t.o);
			rep.put(t.s, typeOnlyNodeID);
		}
		this.numberOfTypeTriplesRead++;
	}

	@Override
	protected void consistencyChecks() {
		for (Long s: edgesWithProv.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edgesWithProv.get(s);
			if (triplesOfThisSubject == null)
				throw new IllegalStateException("No triples whose subject is " + s);
			for (Long p: triplesOfThisSubject.keySet()) {
				TreeSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if ((objectsOfThisSandP.size() > 1) && RDF2SQLEncoding.isDataProperty(p))
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
	 * Returns the first cursor, over the non-type triples
	 * @param conn
	 * @param triplesToDraw
	 * @param triplesTableName
	 * @return
	 */
	@Override
	protected ResultSet getGraphTriplesCursor1ForDotDrawing(Connection conn, long triplesToDraw, String triplesTableName) {
		try {
			//System.out.println("WEAK 1st CURSOR: triplesTableName is: " + triplesTableName);
			return conn.createStatement().executeQuery("select * from " + triplesTableName + " where p<>'<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' limit " + triplesToDraw);
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing"); 
		}
	}
	/**
	 * This is used only when drawing the graph using Dot. 
	 * Different summaries need to traverse their triples in different orders, thus the two cursors which differ between the typed and untyped summaries.
	 * Returns the second cursor, over the type triples.
	 * @param conn
	 * @param triplesToDraw
	 * @param triplesTableName
	 * @return
	 */
	@Override
	protected ResultSet getGraphTriplesCursor2ForDotDrawing(Connection conn, long triplesToDraw, String triplesTableName) {
		try {
			return conn.createStatement().executeQuery("select * from " + triplesTableName + " where p='<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' limit " + triplesToDraw);
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing"); 
		}
	}
}
