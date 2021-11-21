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

public class DataFirstTraverser extends Traverser {
	private static final Logger LOGGER = Logger.getLogger(DataFirstTraverser.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	public DataFirstTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}

	@Override
	protected void typePass() {
		long start = System.currentTimeMillis();
		StringBuilder getTypedTriplesString = new StringBuilder();
		getTypedTriplesString.append("select * from ").append(PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName)).append(" where ");
		String prefix = "";
		for (long typeCode: allTypeConstantCodes) { // allTypeConstantCodes guaranteed to be non-empty
			getTypedTriplesString.append(prefix).append("p = ").append(typeCode);
			prefix = " or ";
		}

		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(10000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString.toString())) {
					do {
						Triple t = null;

						if (summ.haltStepByStep) {
							t = haltStepByStepAndAskForTripleFromUser(true);
						}
						if (t == null) {
							if (rs.next()) {
								t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
							}
							else {
								if (summ.haltStepByStep) {
									LOGGER.info("No more type triples");
								}
								break;
							}
						}
						t = new Triple (t.s, t.p, summ.rep.get(t.o));

						// type triple
						summ.representTypeTripleAfterData(t);
						summ.triplesSummarizedSoFar++;
						summ.typeTriplesSummarizedSoFar++;
						if (summ.checkConsistency) {
							summ.consistencyChecks();
						}
						if (summ.drawStepByStep.equals("true")) {
							drawStepByStep(summ.drawStepByStep);
						}
					}
					while (true);
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e);
		}
		summ.typeTriplesSummarizationTime += System.currentTimeMillis() - start;
	}

	@Override
	public void traverseAllTriples() {
		setUp();

		if (summ.replaceTypeWithMostGeneralType) {
			mostGeneralTypePass();
		}
		dataPass();
		typePass();
		genericPropertyTriplesPass();

		summ.allTriplesSummarizationTime = setupTime + summ.schemaNodesCollectionTime + summ.nonTypeTriplesSummarizationTime + summ.typeTriplesSummarizationTime + summ.genericPropertyTriplesSummarizationTime;
		LOGGER.info("Summarized " + summ.triplesSummarizedSoFar + " input triples, created summary of size " + summ.edgesWithProv.getSummaryEdges().size() + " triples overall in " + summ.allTriplesSummarizationTime + " ms");
	}
}
