package fr.inria.cedar.quotientSummary;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public abstract class Traverser {
	private static final Logger LOGGER = Logger.getLogger(Traverser.class.getName());

	protected final Summary summ;
	protected final Connection conn;
	protected long setupTime;
	protected long typeConstantCode;
	long subClassCode;
	long subPropertyCode;
	long domainCode;
	long rangeCode;

	public Traverser(Summary summ, Connection conn) {
		this.summ = summ;
		this.conn = conn;
		LOGGER.setLevel(Level.INFO);
	}

	protected void schemaNodesCollection() {
		long start = System.currentTimeMillis();
		summ.collectSchemaNodes(conn);
		summ.schemaNodesCollectionTime = System.currentTimeMillis() - start;

		start = System.currentTimeMillis();
		summ.avoidCollisionsWhenAssigningSummaryNodes(conn);
		summ.schemaNodesCollectionTime -= (System.currentTimeMillis() - start);
	}

	protected void setUp() {
		long start = System.currentTimeMillis();
		try {
			conn.setAutoCommit(false);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn, summ.dictionaryTableName);
		typeConstantCode = RDF2SQLEncoding.getTypeCode();
		subClassCode = RDF2SQLEncoding.getSubClassCode();
		subPropertyCode = RDF2SQLEncoding.getSubPropertyCode();
		domainCode = RDF2SQLEncoding.getDomainCode();
		rangeCode = RDF2SQLEncoding.getRangeCode();
		setupTime = System.currentTimeMillis() - start;
	}

	// data and schema triples
	protected void dataPass() {
		long start = System.currentTimeMillis();
		String getUntypedTriplesString = ("select *  from " + summ.encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if ((t.p == subClassCode)
						|| (t.p == subPropertyCode)
						|| (t.p == domainCode)
						|| (t.p == rangeCode)) { // schema triple
							summ.edgesWithProv.addTriple(t.s, t.p, t.o);
							summ.rep.put(t.s, t.s);
							summ.rep.put(t.o, t.o);
						}
						else { // data triple
							summ.handleDataTriple(t);
						}
						summ.triplesSummarizedSoFar++;
						summ.nonTypeTriplesSummarizedSoFar++;
						if (summ.checkConsistency) {
							summ.consistencyChecks();
						}
						//summ.drawSummaryAndGraph(conn, "after-" + summ.triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		summ.nonTypeTriplesSummarizationTime = System.currentTimeMillis() - start;
	}

	// first pass
	protected void dataTriplesClassification() {
		long start = System.currentTimeMillis();
		String getUntypedTriplesString = ("select *  from " + summ.encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if ((t.p == subClassCode)
						|| (t.p == subPropertyCode)
						|| (t.p == domainCode)
						|| (t.p == rangeCode)) { // schema triple
							summ.edgesWithProv.addTriple(t.s, t.p, t.o);
							summ.rep.put(t.s, t.s);
							summ.rep.put(t.o, t.o);
						}
						else { // data triple
							summ.classifyDataTriple(t);
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}

		summ.classificationPostProcessing();

		summ.nonTypeTriplesSummarizationTime = System.currentTimeMillis() - start;
	}

	// second pass
	protected void dataTriplesRepresentation() {
		long start = System.currentTimeMillis();
		String getUntypedTriplesString = ("select *  from " + summ.encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if ((t.p != subClassCode)
						&& (t.p != subPropertyCode)
						&& (t.p != domainCode)
						&& (t.p != rangeCode)) { // data triple
							summ.representDataTriple(t);
							summ.edgesWithProv.addTriple(summ.rep.get(t.s), t.p, summ.rep.get(t.o));
						}
						summ.triplesSummarizedSoFar++;
						summ.nonTypeTriplesSummarizedSoFar++;
						if (summ.checkConsistency) {
							summ.consistencyChecks();
						}
						//summ.drawSummaryAndGraph(conn, "after-" + summ.triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		summ.nonTypeTriplesSummarizationTime += System.currentTimeMillis() - start;
	}

	// type triples
	protected void typePass() {
	}

	public void traverseAllTriples() {
	}
}
