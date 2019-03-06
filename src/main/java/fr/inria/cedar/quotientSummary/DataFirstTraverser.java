//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.PostgresIdentifier;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class DataFirstTraverser extends Traverser {
	private static final Logger LOGGER = Logger.getLogger(DataFirstTraverser.class.getName());

	public DataFirstTraverser(Summary summ, Connection conn) {
		super(summ, conn);
		LOGGER.setLevel(Level.INFO);
	}

	@Override
	protected void typePass() {
		long start = System.currentTimeMillis();
		String getTypedTriplesString = ("select *  from " + PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName) + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						// type triple
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), summ.rep.get(rs.getLong(3)));
						summ.representTypeTripleAfterData(t);
						summ.triplesSummarizedSoFar++;
						summ.typeTriplesSummarizedSoFar++;
						if (summ.checkConsistency) {
							summ.consistencyChecks();
						}
						if (summ.drawStepByStep) {
							drawStepByStep();
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
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

		summ.allTriplesSummarizationTime = setupTime + summ.schemaNodesCollectionTime /*+ summ.classSetCreationTime*/ + summ.nonTypeTriplesSummarizationTime + summ.typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + summ.triplesSummarizedSoFar + " input triples, created summary of size " + summ.edgesWithProv.getSummaryEdges().size()
			+ " triples overall in " + summ.allTriplesSummarizationTime + " ms");
	}
}