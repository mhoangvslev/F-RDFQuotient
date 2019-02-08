//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary;

import java.sql.Connection;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TypeFirstTwoPassTraverser extends TypeFirstTraverser {
	private static final Logger LOGGER = Logger.getLogger(TypeFirstTwoPassTraverser.class.getName());

	public TypeFirstTwoPassTraverser(Summary summ, Connection conn) {
		super(summ, conn);
		LOGGER.setLevel(Level.INFO);
	}

	@Override
	public void traverseAllTriples() {
		setUp();

		if (summ.replaceTypeWithMostGeneralType) {
			mostGeneralTypePass();
		}
		typePass();
		dataTriplesClassification();
		dataTriplesRepresentation();
		genericPropertyTriplesPass();

		summ.allTriplesSummarizationTime = setupTime + summ.schemaNodesCollectionTime + summ.classSetCreationTime + summ.nonTypeTriplesSummarizationTime + summ.typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + summ.triplesSummarizedSoFar + " input triples, created summary of size " + summ.edgesWithProv.getSummaryEdges().size()
			+ " triples overall in " + summ.allTriplesSummarizationTime + " ms");
	}
}