//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient;

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
		setUp();

		if (summ.replaceTypeWithMostGeneralType) {
			mostGeneralTypePass();
		}
		dataTriplesClassification();
		dataTriplesRepresentation();
		typePass();
		genericPropertyTriplesPass();

		summ.allTriplesSummarizationTime = setupTime + summ.schemaNodesCollectionTime /*+ summ.classSetCreationTime*/ + summ.nonTypeTriplesSummarizationTime + summ.typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + summ.triplesSummarizedSoFar + " input triples, created summary of size " + summ.edgesWithProv.getSummaryEdges().size()
			+ " triples overall in " + summ.allTriplesSummarizationTime + " ms");
	}
}