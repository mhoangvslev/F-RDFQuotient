//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.dataAndType;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class InputOutputAndTypedSummary extends Summary {
	private static final Logger LOGGER = Logger.getLogger(InputOutputAndTypedSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	public InputOutputAndTypedSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_INPUT_OUTPUT_AND_TYPED_SUMMARY_PREFIX;
		this.isTypeFirst = false;
		this.isDataAndType = true;
		this.isTwoPass = true;
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void classifyDataTriple(Triple t) {
		// TODO
	}

	@Override
	protected void classificationPostProcessing() {
		// TODO
	}

	@Override
	protected void representDataTriple(Triple t) {
		// schema nodes already represented in collectSchemaNodes
		/*if (!sn.contains(t.s)) {
			repS = ps.get(t.p);
			rep.put(t.s, repS);
		}
		if (!sn.contains(t.o)) {
			repO = pt.get(t.p);
			rep.put(t.o, repO);
		}*/
	}
}