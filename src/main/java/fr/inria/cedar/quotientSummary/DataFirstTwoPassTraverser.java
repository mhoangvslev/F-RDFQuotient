package fr.inria.cedar.quotientSummary;

import java.sql.Connection;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class DataFirstTwoPassTraverser extends DataFirstTraverser {
	private static final Logger LOGGER = Logger.getLogger(DataFirstTwoPassTraverser.class.getName());

	public DataFirstTwoPassTraverser(Summary summ, Connection conn) {
		super(summ, conn);
		LOGGER.setLevel(Level.INFO);
	}

	@Override
	public void traverseAllTriples() {
		schemaNodesCollection();
		setUp();
		dataTriplesClassification();
		dataTriplesRepresentation();
		typePass();

		summ.allTriplesSummarizationTime = setupTime + summ.schemaNodesCollectionTime /*+ summ.classSetCreationTime*/ + summ.nonTypeTriplesSummarizationTime + summ.typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + summ.triplesSummarizedSoFar + " input triples, created summary of size " + summ.edgesWithProv.getSummaryEdges().size()
			+ " triples overall in " + summ.allTriplesSummarizationTime + " ms");
	}
}