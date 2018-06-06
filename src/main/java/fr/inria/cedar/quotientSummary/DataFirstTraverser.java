package fr.inria.cedar.quotientSummary;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
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
		String getTypedTriplesString = ("select *  from " + summ.encodedTriplesTableName + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						// type triple
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						summ.representTypeTripleAfterData(t);
						summ.triplesSummarizedSoFar++;
						summ.typeTriplesSummarizedSoFar++;
						if (summ.checkConsistency) {
							summ.consistencyChecks();
						}
						//summ.drawSummaryAndGraph(conn, "after-" + summ.triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		summ.typeTriplesSummarizationTime = System.currentTimeMillis() - start;
	}

	@Override
	public void traverseAllTriples() {
		schemaNodesCollection();
		setUp();
		dataPass();
		typePass();

		summ.allTriplesSummarizationTime = setupTime + summ.schemaNodesCollectionTime /*+ summ.classSetCreationTime*/ + summ.nonTypeTriplesSummarizationTime + summ.typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + summ.triplesSummarizedSoFar + "input triples, created summary of size " + summ.edgesWithProv.getSummaryEdges().size()
			+ " triples overall in " + summ.allTriplesSummarizationTime + " ms");
	}
}