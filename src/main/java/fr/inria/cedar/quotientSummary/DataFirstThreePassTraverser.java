package fr.inria.cedar.quotientSummary;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.PostgresIdentifier;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

import java.util.HashSet;

public class DataFirstThreePassTraverser extends DataFirstTraverser {
	HashSet<Triple> triplesForThirdPass; 
	
	private static final Logger LOGGER = Logger.getLogger(DataFirstThreePassTraverser.class.getName());

	public DataFirstThreePassTraverser(Summary summ, Connection conn) {
		super(summ, conn);
		triplesForThirdPass = new HashSet<Triple>();
		RDF2SQLEncoding.setUp(conn, summ.dictionaryTableName); 
		summ.setGenericProperties(conn);
		LOGGER.setLevel(Level.INFO);
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
							if (summ.dataPropsNotInCliques.contains(t.p)) {
								triplesForThirdPass.add(t); 
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
		LOGGER.info("Set aside " + triplesForThirdPass.size() + " triples");
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
							if (summ.dataPropsNotInCliques.contains(t.p)) { // Avoid special property triples
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

	
	public void thirdPass() {
		summ.prepareRepresentationOfSpecialDataTriples(); 
		for (Triple t: triplesForThirdPass) {
			summ.representSpecialDataTriple(t);
			summ.triplesSummarizedSoFar++;
			summ.typeTriplesSummarizedSoFar++;
			if (summ.checkConsistency) {
				summ.consistencyChecks();
			}
			//summ.drawSummaryAndGraph(conn, "after-" + summ.triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
		}		
	}

	@Override
	public void traverseAllTriples() {
		schemaNodesCollection();
		setUp();
		dataTriplesClassification();
		dataTriplesRepresentation();
		typePass();
		thirdPass();

		summ.allTriplesSummarizationTime = setupTime + summ.schemaNodesCollectionTime /*+ summ.classSetCreationTime*/ + summ.nonTypeTriplesSummarizationTime + summ.typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + summ.triplesSummarizedSoFar + " input triples, created summary of size " + summ.edgesWithProv.getSummaryEdges().size()
				+ " triples overall in " + summ.allTriplesSummarizationTime + " ms");
	}
}