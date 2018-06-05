package fr.inria.cedar.quotientSummary.weak;

import fr.inria.cedar.quotientSummary.datastructures.DisjointSetForest;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassWeakSummaryWithUnionFind extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassWeakSummaryWithUnionFind.class.getName());
	private final DisjointSetForest disjointSetForest;

	HashSet<Long> nodes;
	HashMap<Long, TreeSet<Long>> n2i;
	HashMap<Long, TreeSet<Long>> n2o;

	public TwoPassWeakSummaryWithUnionFind(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_WEAK_SUMMARY_WITH_UNION_FIND_PREFIX;
		this.isTypeFirst = false;
		this.isTwoPass = true;
		n2i = new HashMap<>();
		n2o = new HashMap<>();
		nodes = new HashSet<>();
		disjointSetForest = new DisjointSetForest();
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn) {
		// first pass
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
						else {
							handleDataTriple2P(t);
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}

		findSummaryNodesAndEdges();

		// second pass
		getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						if ((t.p != RDF2SQLEncoding.getSubClassCode())
						&& (t.p != RDF2SQLEncoding.getSubPropertyCode())
						&& (t.p != RDF2SQLEncoding.getDomainCode())
						&& (t.p != RDF2SQLEncoding.getRangeCode())) {
							Long repS = disjointSetForest.find(ps.get(t.p));
							Long repO = disjointSetForest.find(pt.get(t.p));
							if (sn.contains(t.s)) {
								repS = t.s;
							}
							if (sn.contains(t.o)) {
								repO = t.o;
							}
							rep.put(t.s, repS);
							rep.put(t.o, repO);
							edgesWithProv.addTriple(repS, t.p, repO);
						}
						triplesSummarizedSoFar++;
						nonTypeTriplesSummarizedSoFar++;
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

		nonTypeTriplesSummarizationTime = System.currentTimeMillis() - start - avoidCollisionsTime;
		LOGGER.info("Summarized " + nonTypeTriplesSummarizedSoFar + " data triples in " + nonTypeTriplesSummarizationTime + " ms");

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

		allTriplesSummarizationTime = nonTypeTriplesSummarizationTime + typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " overall triples in " + allTriplesSummarizationTime + " ms");
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	public void handleDataTriple2P(Triple t) {
		if (!sn.contains(t.s)) {
			if (n2o.get(t.s) == null) {
				n2o.put(t.s, new TreeSet<>());
			}
			n2o.get(t.s).add(t.p);

			Long repS = disjointSetForest.find(shiftNodeNumber(t.s));
			if (!ps.containsKey(t.p)) {
				ps.put(t.p, repS);
			}
			else {
				disjointSetForest.union(repS, ps.get(t.p)); // source-source union
			}
		}
		if (!sn.contains(t.o)) {
			if (n2i.get(t.o) == null) {
				n2i.put(t.o, new TreeSet<>());
			}
			n2i.get(t.o).add(t.p);

			Long repO = disjointSetForest.find(shiftNodeNumber(t.o));
			if (!pt.containsKey(t.p)) {
				pt.put(t.p, repO);
			}
			else {
				disjointSetForest.union(repO, pt.get(t.p)); // target-target union
			}
		}
		nodes.add(t.s);
		nodes.add(t.o);
	}

	// shifting node number by maxSummaryNode so that summary nodes have number that doesn't appear in the dictionary
	private Long shiftNodeNumber(Long nodeNumber) {
		return nodeNumber + maxSummaryNode;
	}

	private void findSummaryNodesAndEdges() {
		for (Long n: nodes) {
			if (n2o.containsKey(n) && n2i.containsKey(n)) {
				Long p1 = n2o.get(n).first();
				Long p2 = n2i.get(n).first();
				disjointSetForest.union(ps.get(p1), pt.get(p2)); // source-target union
			}
		}
	}
}