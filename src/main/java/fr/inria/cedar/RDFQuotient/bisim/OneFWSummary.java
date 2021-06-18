//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.bisim;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class OneFWSummary extends Summary {
	private static final Logger LOGGER = Logger.getLogger(OneFWSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	private final HashMap<Long, TreeSet<Long>> n2op; // node to outgoing property set
	private final HashMap<TreeSet<Long>, Long> op2sn; // outgoing property set to summary node
	HashSet<Long> leaves;

	public OneFWSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_ONEFW_SUMMARY_PREFIX;
		this.isTypeFirst = false;
		this.isDataAndType = false;
		this.isTwoPass = true;
		this.n2op = new HashMap<>();
		this.op2sn = new HashMap<>();
		this.leaves = new HashSet<>();
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void classifyDataTriple(Triple t) {
		leaves.remove(t.s);  // s is certainly not a leaf
		//LOGGER.info(t.s + " " + RDF2SQLEncoding.dictionaryDecode(t.s) + " not a leaf");
		TreeSet<Long> previousPOS = n2op.get(t.s);
		if (previousPOS == null){
			previousPOS = new TreeSet<>();
			n2op.put(t.s, previousPOS);
		}
		previousPOS.add(t.p);
		//LOGGER.info(t.s + " " + RDF2SQLEncoding.dictionaryDecode(t.s) + " has property " + t.p + " " + RDF2SQLEncoding.dictionaryDecode(t.p));
		//LOGGER.info("Properties of " + t.s + " are: " + previousPOS);

		//add o as a leaf unless it is known to have triples
		if (n2op.get(t.o) == null) {
			leaves.add(t.o);
			//LOGGER.info(t.o + " " + RDF2SQLEncoding.dictionaryDecode(t.o) +  " is a leaf");
		}
		else {
			if (n2op.get(t.o).isEmpty()) {
				leaves.add(t.o);
				//LOGGER.info(t.o + " " + RDF2SQLEncoding.dictionaryDecode(t.o) + " is a leaf");
			}
		}
	}

	@Override
	protected void classificationPostProcessing() {
		representDataNodes();
	}

	private void representDataNodes() {
		// all nodes with outgoing edges and possibly incoming edges:
		for (long n: n2op.keySet()){
			if (!sn.contains(n)) { // not a schema node
				TreeSet<Long> nop = n2op.get(n);
				Long summaryNode = getSummaryNode(nop);
				if (summaryNode == null){
					summaryNode = createSummaryNode(nop);
				}
				//LOGGER.info("REPRESENTED NON-LEAF NODE " + n + " " + RDF2SQLEncoding.dictionaryDecode(n) + " BY THE PROPERTY SET " + nop);
				rep.put(n, summaryNode);
			}
		}
//		// all nodes with incoming but not outgoing edges (those with both are covered above):
		Long leafSummaryNode = createSummaryNode(new TreeSet<>());
		for (Long n: leaves) {
			// LOGGER.info("REPRESENTED LEAF NODE " + RDF2SQLEncoding.dictionaryDecode(n) + " BY " + leafSummaryNode);
			rep.put(n, leafSummaryNode);
		}
	}

	// creates the last data node representatives (those not already represented above)
	// and represents all data triples
	@Override
	protected void representDataTriple(Triple t) {
		//System.out.println("\nREPRESENTING DATA TRIPLE " + RDF2SQLEncoding.decode(t).toString());
		Long repS = rep.get(t.s);
		if (repS == null){
			TreeSet<Long> sop = n2op.get(t.s);
			// probably both are null. We know rep doesn't exist, so we create it:
			repS = getSummaryNode(sop);
			if (repS == null){
				repS = createSummaryNode(sop);
			}
			rep.put(t.s, repS);
		}
		Long repO = rep.get(t.o);
		if (repO == null){
			TreeSet<Long> oop = n2op.get(t.s);
			// probably both are null. We know rep doesn't exist, so we create it:
			repO = getSummaryNode(oop);
			if (repO == null){
				repO = createSummaryNode(oop);
			}
			rep.put(t.o, repO);
		}
		// Commented this out since the Traverser (also) adds the triple.
		// this.edgesWithProv.addTriple(repS, t.p, repO);
	}

	private long createSummaryNode(TreeSet<Long> nop) {
		long n = this.getNextSummaryNode();
		this.op2sn.put(nop, n);
		return n;
	}

	private Long getSummaryNode(TreeSet<Long> nop) {
		return (op2sn.get(nop));
	}
}
