package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class StrongSummary extends StrongOrTypedStrongSummary {
	public StrongSummary() {
		super();
		this.summaryTablePrefix = STRONG_SUMMARY_PREFIX;
	}

	/**
	 * This must be used to read a S summary from Postgres.
	 *
	 * @param conn
	 */
	public StrongSummary(Connection conn) {
		this.conn = conn; 
		this.summaryTablePrefix = STRONG_SUMMARY_PREFIX;
		Debugger.log("Reading Strong summary from Postgres, setting up special URIs from the dictionary");
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
				this.addTriple(s, p, o);
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to read Strong summary from Postgres: " + e.toString());
		}
		System.out.println("Read Strong summary from Postgres");
	}


	/**
	 * this must be called after the constructor as the summary needs to ask more queries
	 * for patching itself up during summarization.
	 * 
	 * @param conn
	 */
	public void setConn(Connection conn){
		this.conn = conn; 
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 * @param args
	 */
	@Override
	public void summarizeFromPostgres(Connection conn, String[] args) {
		this.setConn(conn);
		long start = System.currentTimeMillis();

		String triplesFileName = args[0];
		String triplesTableName = args[1];
		String encodedTriplesTableName = args[2];
		String dictionaryTableName = args[3];

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
						|| (t.p == RDF2SQLEncoding.getRangeCode()))
							addTriple(t.s, t.p, t.o);
						else
							handleDataTriple(t);
						triplesSummarizedSoFar++;
						//this.drawSummaryAndGraph(conn, triplesFileName, triplesTableName, dictionaryTableName, "-after-" + triplesSummarizedSoFar + "-"+ t.s + "-" + t.p + "-" + t.o);
						//display();
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
						//System.out.println("#### Type triple " + t.toString());
						this.handleTypeTripleAfterData(t);
						triplesSummarizedSoFar++;
						this.drawSummaryAndGraph(conn, triplesFileName, triplesTableName, dictionaryTableName, "-after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
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
		this.display(triplesFileName);
	}

	public void handleDataTriple(Triple t) {
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

		System.out.println("\n" + t.toString() + " " + RDF2SQLEncoding.decode(t) + " case: " + this.caseName(caseNumber)); 
		switch (caseNumber) {
			case US_RS_UO_RO_RP: {
				handleDataTriple_US_RS_UO_RO_RP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_UO_NO_RP: {
				handleDataTriple_US_RS_UO_NO_RP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_UO_RO_RP: {
				handleDataTriple_US_NS_UO_RO_RP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_UO_NO_RP: {
				handleDataTriple_US_NS_UO_NO_RP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_UO_RO_NP: {
				handleDataTriple_US_RS_UO_RO_NP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RS_UO_NO_NP: {
				handleDataTriple_US_RS_UO_NO_NP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_UO_RO_NP: {
				handleDataTriple_US_NS_UO_RO_NP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_NS_UO_NO_NP: {
				handleDataTriple_US_NS_UO_NO_NP(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			default:
				throw new IllegalStateException("Unknown case " + caseNumber);
		}
		cacheTriple(t) ;
		//this.display();
	}


	/** This implementation should be shared by Weak and Strong
	 *
	 * @param t
	 */
	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		//System.out.println("\nType triple " + t.toString());
		Long repS = rep.get(t.s);
		if (repS != null) {
			//System.out.println("Source " + t.s + " already represented");
			addTriple(repS, t.p, t.o);
		}
		else {
			//System.out.println("Source " + t.s + " has no data properties");
			if (!typeOnlyNodeAlreadySeen) {
				//System.out.println("Creating representative for type-only node"); 
				this.typeOnlyNodeID = getNextSummaryNode();
				typeOnlyNodeAlreadySeen = true;
			}
			addTriple(typeOnlyNodeID, t.p, t.o);
			rep.put(t.s, typeOnlyNodeID);
		}
		this.numberOfTypeTriplesRead++;
	}

	private char decode(Long repS, Long repO, Long sourceCliqueP) {
		if (repS != null) // US, RS
			// US, RS, UO
			if (repO != null) // RO
				if (sourceCliqueP != null)
					return US_RS_UO_RO_RP;
				else
					return US_RS_UO_RO_NP;
			else // NO
				if (sourceCliqueP != null)
					return US_RS_UO_NO_RP;
				else
					return US_RS_UO_NO_NP;
		else // US, NS
			if (repO != null) // RO
				if (sourceCliqueP != null) // RP
					return US_NS_UO_RO_RP;
				else
					return US_NS_UO_RO_NP;
			else // NO
				if (sourceCliqueP != null) // RP
					return US_NS_UO_NO_RP;
				else
					return US_NS_UO_NO_NP;
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
			return conn.createStatement().executeQuery("select * from " + triplesTableName + " where p <> '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' limit " + triplesToDraw);
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
		try{
			return conn.createStatement().executeQuery("select * from " + triplesTableName + " where p='<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' limit " + triplesToDraw);
		}
		catch(SQLException e){
			throw new IllegalStateException("Could not get a cursor on the graph triples for drawing"); 
		}
	}

	public void display() {
		System.out.println("===STRONG SUMMARY\nSource cliques: " + sc.toString());
		System.out.println("Target cliques: " + tc.toString());
		System.out.println("Data nodes to source cliques: " + n2sc.display());
		System.out.println("Data nodes to target cliques: " + n2tc.display());
		System.out.println("Property to source cliques: " + p2sc.display());
		System.out.println("Property to target cliques: " + p2tc.display());
		System.out.println("Untyped summary nodes: " + untypedSummaryNodes.toString());
		System.out.println("Representation function: ");
		showRep();
		System.out.println("Summary: ");
		for (Triple t: this.getSummaryEdges())
			t.display();
		roundTripConsistencyCheck();
		System.out.println("===");
	}
}
