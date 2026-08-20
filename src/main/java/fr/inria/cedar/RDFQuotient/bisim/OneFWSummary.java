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
	private final HashMap<Long, HashMap<TreeSet<Long>, Long>> op2sn; // authority -> outgoing property set -> summary node
	private final HashMap<Long, Long> leafSummaryNodeByAuthority; // authority -> the shared summary node representing all of that authority's leaves
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
		this.leafSummaryNodeByAuthority = new HashMap<>();
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
		TreeSet<Long> previousPOS = n2op.computeIfAbsent(t.s, k -> new TreeSet<>());
		previousPOS.add(t.p);
		//LOGGER.info(t.s + " " + RDF2SQLEncoding.dictionaryDecode(t.s) + " has property " + t.p + " " + RDF2SQLEncoding.dictionaryDecode(t.p));
		//LOGGER.info("Properties of " + t.s + " are: " + previousPOS);

		// a literal object shares its raw dictionary code with every occurrence of that value in the
		// graph, so it is resolved to a synthetic per-(literal, authority) node before use; leaf-ness
		// itself is decided from the raw id, since only a real (never literal) resource can ever also
		// appear elsewhere as a subject with outgoing edges
		long resolvedO = resolveObjectNodeId(t.s, t.o);
		if (n2op.get(t.o) == null || n2op.get(t.o).isEmpty()) {
			leaves.add(resolvedO);
			//LOGGER.info(t.o + " " + RDF2SQLEncoding.dictionaryDecode(t.o) +  " is a leaf");
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
				long authorityId = authorityOfResolvedNode(n);
				Long summaryNode = getSummaryNode(authorityId, nop);
				if (summaryNode == null){
					summaryNode = createSummaryNode(authorityId, nop);
				}
				//LOGGER.info("REPRESENTED NON-LEAF NODE " + n + " " + RDF2SQLEncoding.dictionaryDecode(n) + " BY THE PROPERTY SET " + nop);
				rep.put(n, summaryNode);
			}
		}
		// all nodes with incoming but not outgoing edges (those with both are covered above), grouped
		// per authority so leaves from different sources are never silently merged together:
		for (Long n: leaves) {
			long authorityId = authorityOfResolvedNode(n);
			Long leafSummaryNode = leafSummaryNodeByAuthority.computeIfAbsent(authorityId, k -> createSummaryNode(authorityId, new TreeSet<>()));
			// LOGGER.info("REPRESENTED LEAF NODE " + RDF2SQLEncoding.dictionaryDecode(n) + " BY " + leafSummaryNode);
			rep.put(n, leafSummaryNode);
		}
	}

	// creates the last data node representatives (those not already represented above)
	// and represents all data triples
	@Override
	protected void representDataTriple(Triple t) {
		//System.out.println("\nREPRESENTING DATA TRIPLE " + RDF2SQLEncoding.decode(t).toString());
		long resolvedO = resolveObjectNodeId(t.s, t.o);
		Long repS = rep.get(t.s);
		if (repS == null){
			TreeSet<Long> sop = n2op.get(t.s);
			// probably both are null. We know rep doesn't exist, so we create it:
			long sAuthority = getOrComputeAuthorityId(t.s);
			repS = getSummaryNode(sAuthority, sop);
			if (repS == null){
				repS = createSummaryNode(sAuthority, sop);
			}
			rep.put(t.s, repS);
		}
		Long repO = rep.get(resolvedO);
		if (repO == null){
			TreeSet<Long> oop = n2op.get(resolvedO);
			// probably both are null. We know rep doesn't exist, so we create it:
			long oAuthority = objectAuthorityId(t.s, t.o);
			repO = getSummaryNode(oAuthority, oop);
			if (repO == null){
				repO = createSummaryNode(oAuthority, oop);
			}
			rep.put(resolvedO, repO);
		}
		// Commented this out since the Traverser (also) adds the triple.
		// this.edgesWithProv.addTriple(repS, t.p, repO);
	}

	private long createSummaryNode(long authorityId, TreeSet<Long> nop) {
		long n = this.getNextSummaryNode();
		HashMap<TreeSet<Long>, Long> op2snForAuthority = this.op2sn.computeIfAbsent(authorityId, k -> new HashMap<>());
		op2snForAuthority.put(nop, n);
		return n;
	}

	private Long getSummaryNode(long authorityId, TreeSet<Long> nop) {
		HashMap<TreeSet<Long>, Long> op2snForAuthority = this.op2sn.get(authorityId);
		if (op2snForAuthority == null) {
			return null;
		}
		return (op2snForAuthority.get(nop));
	}
}
