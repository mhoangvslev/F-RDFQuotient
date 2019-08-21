//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.dataAndType;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TypedSummary extends Summary {
	private static final Logger LOGGER = Logger.getLogger(TypedSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	long untypedNodesSummaryNode;

	public TypedSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_WEAK_SUMMARY_PREFIX;
		this.isTypeFirst = true;
		this.isDataAndType = false;
		this.isTwoPass = true;
		untypedNodesSummaryNode = getNextSummaryNode();
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