//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.dataAndType;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.datastructures.Long2Long;
import fr.inria.cedar.RDFQuotient.datastructures.Long2LongSet;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import java.util.HashMap;
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
		this.summaryTablePrefix = TYPED_SUMMARY_PREFIX;
		this.isTypeFirst = true;
		this.isDataAndType = false;
		this.isTwoPass = false;
		cs = new Long2LongSet();
		acs = new Long2LongSet();
		n2cs = new Long2Long();
		cs2csID = new HashMap<>();
		untypedNodesSummaryNode = getNextSummaryNode();
	}

	@Override
	protected void representTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void handleDataTriple(Triple t) {
		//LOGGER.debug("### Data triple: " + t.toString());

		boolean sTyped = n2cs.get(t.s) != null;
		boolean sSchemaNode = sn.contains(t.s);
		boolean oTyped = n2cs.get(t.o) != null;
		boolean oSchemaNode = sn.contains(t.o);

		// schema nodes already represented in collectSchemaNodes
		// typed nodes already represented in typePass
		if (!sSchemaNode && !sTyped) {
			rep.put(t.s, untypedNodesSummaryNode);
		}
		if (!oSchemaNode && !oTyped) {
			rep.put(t.o, untypedNodesSummaryNode);
		}

		edgesWithProv.addTriple(rep.get(t.s), t.p, rep.get(t.o));
	}
}