package fr.inria.cedar.quotientSummary;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.PostgresIdentifier;
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
	protected long subClassCode;
	protected long subPropertyCode;
	protected long domainCode;
	protected long rangeCode;

	public Traverser(Summary summ, Connection conn) {
		this.summ = summ;
		this.conn = conn;
		// Next 2 lines: Ioana, Oct 15, 2018
		RDF2SQLEncoding.setUp(conn, summ.dictionaryTableName);
		summ.setGenericProperties(conn);
		// end of Ioana's fix, Oct 15, 2018
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
		String getUntypedTriplesString = ("select *  from " + PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName) + " where p <> " + typeConstantCode);
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
							// s, o represented in collectSchemaNodes
						}
						else { // data triple
							if (summ.genericPropertiesIgnoredInCliques.contains(t.p)) {
								summ.genericPropertyTriples.add(t);
							}
							else {
								summ.handleDataTriple(t);
							}
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
		String getUntypedTriplesString = ("select *  from " + PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName) + " where p <> " + typeConstantCode);
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
							// s, o represented in collectSchemaNodes
						}
						else { // data triple
							if (summ.genericPropertiesIgnoredInCliques.contains(t.p)) {
								summ.genericPropertyTriples.add(t);
							}
							else {
								summ.classifyDataTriple(t);
							}
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
		String getUntypedTriplesString = ("select *  from " + PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName) + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if (summ.genericPropertiesIgnoredInCliques.contains(t.p)) { // avoid generic property triples
							continue;
						}
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

	// ommitted generic property triples
	public void genericPropertyTriplesPass() {
		summ.setGenericProperties(conn);
		summ.prepareRepresentationOfGenericPropertyTriples();
		LOGGER.info("Starting third pass on " + summ.genericPropertyTriples.size() + " generic triples");
		for (Triple t: summ.genericPropertyTriples) {
			//LOGGER.info("Generic triple: " + t.toString());
			summ.representGenericPropertyTriple(t);
			summ.triplesSummarizedSoFar++;
			summ.typeTriplesSummarizedSoFar++;
			if (summ.checkConsistency) {
				summ.consistencyChecks();
			}
		}
	}

	// type triples
	protected void typePass() {
	}

	public void traverseAllTriples() {
	}
}
