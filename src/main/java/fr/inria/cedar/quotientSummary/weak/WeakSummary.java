package fr.inria.cedar.quotientSummary.weak;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class WeakSummary extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(WeakSummary.class.getName());

	public WeakSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = WEAK_SUMMARY_PREFIX;
		this.isTypeFirst = false;
		this.typeOnlyNodeID = -1;
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn) {
		long avoidCollisionsTimeStart;
		long avoidCollisionsTime = 0;
		long start = System.currentTimeMillis();
		collectSchemaNodes(conn);
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
			avoidCollisionsTimeStart = System.currentTimeMillis();
			avoidCollisionsWhenAssigningSummaryNodes(conn);
			avoidCollisionsTime += System.currentTimeMillis() - avoidCollisionsTimeStart;
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
							rep.put(t.s, t.s);
							rep.put(t.o, t.o);
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
		dataTriplesSummarizationTime = System.currentTimeMillis() - start - avoidCollisionsTime;
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
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " triples overall in " + allTriplesSummarizationTime + " ms");
	}

	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean sSchemaNode, boolean pRepresented, boolean oRepresented, boolean oSchemaNode) {
		if (sSchemaNode) {
			if (oSchemaNode) {
				return SELF_SELF;
			}
			if (pRepresented) {
				if (oRepresented) {
					return SN_RP_RO;
				}
				else {
					return SN_RP_UO;
				}
			}
			else {
				if (oRepresented) {
					return SN_UP_RO;
				}
				else {
					return SN_UP_UO;
				}
			}
		}
		else if (sRepresented) {
			if (pRepresented) {
				if (oSchemaNode) {
					return RS_RP_SN;
				}
				else if (oRepresented) {
					return RS_RP_RO;
				}
				else {
					return RS_RP_UO;
				}
			}
			else {
				if (oSchemaNode) {
					return RS_UP_SN;
				}
				else if (oRepresented) {
					return RS_UP_RO;
				}
				else {
					return RS_UP_UO;
				}
			}
		}
		else {
			if (pRepresented) {
				if (oSchemaNode) {
					return US_RP_SN;
				}
				else if (oRepresented) {
					return US_RP_RO;
				}
				else {
					return US_RP_UO;
				}
			}
			else {
				if (oSchemaNode) {
					return US_UP_SN;
				}
				else if (oRepresented) {
					return US_UP_RO;
				}
				else {
					return US_UP_UO;
				}
			}
		}
	}

	protected void handleDataTriple(Triple t) {
		//LOGGER.debug("\n### Read data triple: " + t.toString());
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p);
		//if ((pSource == null && pTarget != null) || (pSource != null && pTarget == null))
		//	throw new IllegalStateException("Source represented and target not represented, or the opposite");

		boolean pRepresented = (pSource != null);
		boolean sRepresented = (repS != null);
		boolean sSchemaNode = sn.contains(t.s);
		boolean oRepresented = (repO != null);
		boolean oSchemaNode = sn.contains(t.o);

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sSchemaNode, pRepresented, oRepresented, oSchemaNode);
		//LOGGER.debug(showCaseName(caseNumber));
		switch (caseNumber) {
			case SELF_SELF:
				handleDataTriple_SELF_SELF(t);
				break;
			case SN_RP_RO:
				handleDataTriple_TRS_RP_RO(t, repS, repO, pSource, pTarget);
				break;
			case SN_RP_UO:
				handleDataTriple_TRS_RP_UO(t, repS, repO, pSource, pTarget);
				break;
			case SN_UP_RO:
				handleDataTriple_TRS_UP_RO(t, repS, repO, pSource, pTarget);
				break;
			case SN_UP_UO:
				handleDataTriple_TRS_UP_UO(t, repS, repO, pSource, pTarget);
				break;
			case RS_RP_SN:
				handleDataTriple_RS_RP_TRO(t, repS, repO, pSource, pTarget);
				break;
			case RS_RP_RO:
				handleDataTriple_RS_RP_RO(t);
				break;
			case RS_RP_UO:
				handleDataTriple_RS_RP_UO(t);
				break;
			case RS_UP_SN:
				handleDataTriple_RS_UP_TRO(t, repS, repO, pSource, pTarget);
				break;
			case RS_UP_RO:
				handleDataTriple_RS_UP_RO(t);
				break;
			case RS_UP_UO:
				handleDataTriple_RS_UP_UO(t);
				break;
			case US_RP_SN:
				handleDataTriple_US_RP_TRO(t, repS, repO, pSource, pTarget);
				break;
			case US_RP_RO:
				handleDataTriple_US_RP_RO(t);
				break;
			case US_RP_UO:
				handleDataTriple_US_RP_UO(t);
				break;
			case US_UP_SN:
				handleDataTriple_US_UP_TRO(t, repS, repO, pSource, pTarget);
				break;
			case US_UP_RO:
				handleDataTriple_US_UP_RO(t);
				break;
			case US_UP_UO:
				handleDataTriple_US_UP_UO(t);
				break;
			default:
				throw new IllegalStateException("This case should not be encountered here");
		}
		//LOGGER.debug("After processing triple " + t.toString() + ", we have:\n" + this.toString());
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
		rep.put(t.o, t.o);
	}

	// -->
	// Debug methods
	// -->

	@Override
	protected void consistencyChecks() {
		for (Long s: edgesWithProv.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edgesWithProv.get(s);
			for (Long p: triplesOfThisSubject.keySet()) {
				TreeSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if (RDF2SQLEncoding.isDataProperty(p)) {
					if (objectsOfThisSandP.size() > 1)
						throw new IllegalStateException("Subject " + s + " has more than one edge with label " + p);
					for (Long o: objectsOfThisSandP) {
						if (ps.get(p) == null)
							throw new IllegalStateException("No source for " + p);
						if (!ps.get(p).equals(s))
							throw new IllegalStateException("Source of " + p + " is not " + s + " but " + ps.get(p));
						if (pt.get(p) == null)
							throw new IllegalStateException("No target for " + p);
						if (!pt.get(p).equals(o))
							throw new IllegalStateException("Target of " + p + " is not " + o + " but " + pt.get(p));
					}
				}
			}
		}
	}

	@Override
	protected String showCaseName(char caseNumber) { 
		switch (caseNumber) {
			case SELF_SELF:
				return "SELF_SELF";
			case SN_RP_RO:
				return "SN_RP_RO";
			case SN_RP_UO:
				return "SN_RP_UO";
			case SN_UP_RO:
				return "SN_UP_RO";
			case SN_UP_UO:
				return "SN_UP_UO";
			case RS_RP_SN:
				return "RS_RP_SN";
			case RS_RP_RO:
				return "RS_RP_RO";
			case RS_RP_UO:
				return "RS_RP_UO";
			case RS_UP_SN:
				return "RS_UP_SN";
			case RS_UP_RO:
				return "RS_UP_RO";
			case RS_UP_UO:
				return "RS_UP_UO";
			case US_RP_SN:
				return "US_RP_SN";
			case US_RP_RO:
				return "US_RP_RO";
			case US_RP_UO:
				return "US_RP_UO";
			case US_UP_SN:
				return "US_UP_SN";
			case US_UP_RO:
				return "US_UP_RO";
			case US_UP_UO:
				return "US_UP_UO";
			default:
				throw new IllegalStateException("Unrecognized case " + caseNumber);
		}
	}
}
