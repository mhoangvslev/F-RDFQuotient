//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient;

import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import fr.inria.cedar.RDFQuotient.util.PostgresIdentifier;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class DataAndTypeTwoPassTraverser extends DataAndTypeTraverser {
	private static final Logger LOGGER = Logger.getLogger(DataAndTypeTwoPassTraverser.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	public DataAndTypeTwoPassTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}

	// first pass
	protected void dataAndTypeTriplesClassification() {
		long start = System.currentTimeMillis();
		String getAllTriplesString = "select * from "
			+ PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName)
			+ (summ.summarizationProperties.getProperty("database.deterministic_ordering").equals("false") ? "" : "order by s, p, o");
		Triple t;
		try {
			try (Statement getAllTriples = conn.createStatement()) {
				getAllTriples.setFetchSize(10000);
				try (ResultSet rs = getAllTriples.executeQuery(getAllTriplesString)) {
					while (rs.next()) {
						t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if ((t.p == subClassCode)
							|| (t.p == subPropertyCode)
							|| (t.p == domainCode)
							|| (t.p == rangeCode)) { // schema triple
							// s and o represented in collectSchemaNodes
							summ.edgesWithProv.addTriple(summ.rep.get(t.s), t.p, summ.rep.get(t.o));
						}
						else { // data or type triple
							if (summ.genericPropertiesIgnoredInCliques.contains(t.p)) {
								summ.genericPropertyTriples.add(t);
							}
							else {
								summ.classifyDataOrTypeTriple(t);
							}
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e);
		}

		summ.classificationPostProcessing();

		summ.dataAndTypeTriplesSummarizationTime = System.currentTimeMillis() - start;
	}

	// second pass
	protected void dataAndTypeTriplesRepresentation() {
		long start = System.currentTimeMillis();
		String getAllTriplesString = "select * from "
			+ PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName)
			+ (summ.summarizationProperties.getProperty("database.deterministic_ordering").equals("false") ? "" : "order by s, p, o");
		Triple t;
		try {
			try (Statement getAllTriples = conn.createStatement()) {
				getAllTriples.setFetchSize(10000);
				try (ResultSet rs = getAllTriples.executeQuery(getAllTriplesString)) {
					while (rs.next()) {
						if (summ.haltStepByStep) {
							haltStepByStep();
						}
						t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if (summ.genericPropertiesIgnoredInCliques.contains(t.p)) { // avoid generic property triples, they will be represented later
							continue;
						}
						if ((t.p != subClassCode)
							&& (t.p != subPropertyCode)
							&& (t.p != domainCode)
							&& (t.p != rangeCode)) { // data triple
							summ.representDataOrTypeTriple(t);
							summ.edgesWithProv.addTriple(summ.rep.get(t.s), t.p, summ.rep.get(t.o));
						}
						summ.triplesSummarizedSoFar++;
						summ.dataAndTypeTriplesSummarizedSoFar++;
						if (summ.checkConsistency) {
							summ.consistencyChecks();
						}
						if (summ.drawStepByStep.equals("true")) {
							drawStepByStep(summ.drawStepByStep);
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e);
		}

		summ.dataAndTypeTriplesSummarizationTime += System.currentTimeMillis() - start;
	}

	@Override
	public void traverseAllTriples() {
		setUp();

		dataAndTypeTriplesClassification();
		dataAndTypeTriplesRepresentation();
		genericPropertyTriplesPass();

		summ.allTriplesSummarizationTime = setupTime + summ.schemaNodesCollectionTime + summ.dataAndTypeTriplesSummarizationTime + summ.genericPropertyTriplesSummarizationTime;
		LOGGER.info("Summarized " + summ.triplesSummarizedSoFar + " input triples, created summary of size " + summ.edgesWithProv.getSummaryEdges().size() + " triples overall in " + summ.allTriplesSummarizationTime + " ms");
	}
}
