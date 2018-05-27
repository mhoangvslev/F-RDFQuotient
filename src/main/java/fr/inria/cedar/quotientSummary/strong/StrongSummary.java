package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class StrongSummary extends StrongOrTypedStrongSummary {
	private static final Logger LOGGER = Logger.getLogger(StrongSummary.class.getName());

	public StrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = STRONG_SUMMARY_PREFIX;
		this.isTypeFirst = false;
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn) {
		long start = System.currentTimeMillis();
		try {
			conn.setAutoCommit(false);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true;
			avoidCollisionsWhenAssigningSummaryNodes(conn);
		}
		String getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
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
						dataTriplesSummarizedSoFar++;
						if (checkConsistency) {
							consistencyChecks();
						}
						//drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		dataTriplesSummarizationTime = System.currentTimeMillis() - start;
		LOGGER.info("Summarized " + dataTriplesSummarizedSoFar + " data triples in " + dataTriplesSummarizationTime + " ms");

		start = System.currentTimeMillis();
		String getTypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						this.handleTypeTripleAfterData(t);
						triplesSummarizedSoFar++;
						typeTriplesSummarizedSoFar++;
						storeSpecialNodesRepresentation(t, false);
						if (checkConsistency) {
							consistencyChecks();
						}
						//drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		typeTriplesSummarizationTime = System.currentTimeMillis() - start;
		LOGGER.info("Summarized " + typeTriplesSummarizedSoFar + " type triples in " + typeTriplesSummarizationTime + " ms");

		allTriplesSummarizationTime = dataTriplesSummarizationTime + typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " overall triples in " + allTriplesSummarizationTime + " ms");
	}

	public void handleDataTriple(Triple t) {
		// 8 cases: (RS, US) x (RP, UP) x (RO, UO)
		Long sourceCliqueS = n2sc.get(t.s);
		Long targetCliqueS = n2tc.get(t.s);
		Long sourceCliqueO = n2sc.get(t.o);
		Long targetCliqueO = n2tc.get(t.o);

		Long sourceCliqueP = p2sc.get(t.p);
		Long targetCliqueP = p2tc.get(t.p);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		char caseNumber = decode(repS, repO, sourceCliqueP);

		//LOGGER.debug("\n" + t.toString() + " " + RDF2SQLEncoding.decode(t) + " case: " + this.caseName(caseNumber)); 
		switch (caseNumber) {
			case RS_RP_RO: {
				handleDataTriple_RS_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_RP_UO: {
				handleDataTriple_RS_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RP_RO: {
				handleDataTriple_US_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_RP_UO: {
				handleDataTriple_US_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_UP_RO: {
				handleDataTriple_RS_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case RS_UP_UO: {
				handleDataTriple_RS_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_UP_RO: {
				handleDataTriple_US_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			case US_UP_UO: {
				handleDataTriple_US_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			}
			default:
				throw new IllegalStateException("Unknown case " + caseNumber);
		}
		cacheTriple(t);
		//display(); 
	}

	/** This implementation should be shared by Weak and Strong
	 *
	 * @param t
	 */
	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		//LOGGER.debug("\nType triple " + t.toString());
		Long repS = rep.get(t.s);
		if (repS != null) {
			//LOGGER.debug("Source " + t.s + " already represented");
			edgesWithProv.addTriple(repS, t.p, t.o);
		}
		else {
			//LOGGER.debug("Source " + t.s + " has no data properties");
			if (!typeOnlyNodeAlreadySeen) {
				//LOGGER.debug("Creating representative for type-only node"); 
				this.typeOnlyNodeID = getNextSummaryNode();
				typeOnlyNodeAlreadySeen = true;
			}
			edgesWithProv.addTriple(typeOnlyNodeID, t.p, t.o);
			rep.put(t.s, typeOnlyNodeID);
			rep.put(t.o, t.o);
		}
	}

	private char decode(Long repS, Long repO, Long sourceCliqueP) {
		if (repS != null) // US, RS
			// US, RS, UO
			if (repO != null) // RO
				if (sourceCliqueP != null)
					return RS_RP_RO;
				else
					return RS_UP_RO;
			else // NO
				if (sourceCliqueP != null)
					return RS_RP_UO;
				else
					return RS_UP_UO;
		else // US, NS
			if (repO != null) // RO
				if (sourceCliqueP != null) // RP
					return US_RP_RO;
				else
					return US_UP_RO;
			else // NO
				if (sourceCliqueP != null) // RP
					return US_RP_UO;
				else
					return US_UP_UO;
	}
}
