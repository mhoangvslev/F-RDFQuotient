//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.dataAndType;

import fr.inria.cedar.RDFQuotient.bisim.OneBisimSummary;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class InputOutputAndTypedSummary extends OneBisimSummary {
	private static final Logger LOGGER = Logger.getLogger(InputOutputAndTypedSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	public InputOutputAndTypedSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
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
	protected void representTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void classifyDataOrTypeTriple(Triple t) {
		super.classifyDataTriple(t);
	}

	@Override
	protected void representDataOrTypeTriple(Triple t) {
		super.representDataTriple(t);
	}
}
