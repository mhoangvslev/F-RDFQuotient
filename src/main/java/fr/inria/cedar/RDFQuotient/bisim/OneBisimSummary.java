//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.bisim;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class OneBisimSummary extends Summary{
	private static final Logger LOGGER = Logger.getLogger(OneBisimSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	private final HashMap<Long, TreeSet<Long>> n2ip; // node to incoming property set
	private final HashMap<Long, TreeSet<Long>> n2op; // node to outgoing property set
	// authority -> incoming property set -> outgoing property set -> summary node
	private final HashMap<Long, HashMap<TreeSet<Long>, HashMap<TreeSet<Long>, Long>>> ip2op2sn;

	public OneBisimSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_ONEFB_SUMMARY_PREFIX;
		this.isTypeFirst = false;
		this.isDataAndType = false;
		this.isTwoPass = true;
		this.n2ip = new HashMap<>();
		this.n2op = new HashMap<>();
		this.ip2op2sn = new HashMap<>();
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void classifyDataTriple(Triple t) {
        // a literal object shares its raw dictionary code with every occurrence of that value in the
        // graph, so it is resolved to a synthetic per-(literal, authority) node before use
        long resolvedO = resolveObjectNodeId(t.s, t.o);
        TreeSet<Long> previousSOP = n2op.computeIfAbsent(t.s, k -> new TreeSet<>());
        previousSOP.add(t.p);
        TreeSet<Long> previousOIP = n2ip.computeIfAbsent(resolvedO, k -> new TreeSet<>());
        previousOIP.add(t.p);
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
				TreeSet<Long> nip = n2ip.get(n);
				long authorityId = authorityOfResolvedNode(n);
				Long summaryNode = getSummaryNode(authorityId, nop, nip);
				if (summaryNode == null){
					summaryNode = createSummaryNode(authorityId, nop, nip);
				}
				//LOGGER.debug("REPRESENTED NODE (1) " + RDF2SQLEncoding.dictionaryDecode(n) + " BY " + sn);
				rep.put(n, summaryNode);
			}
		}
		// all nodes with incoming but not outgoing edges (those with both are covered above):
		for (long n: n2ip.keySet()){
			if (n2op.get(n) == null) {
				if (!sn.contains(n)) { // not a schema node
					TreeSet<Long> nop = n2op.get(n);
					TreeSet<Long> nip = n2ip.get(n);
					long authorityId = authorityOfResolvedNode(n);
					Long summaryNode = getSummaryNode(authorityId, nop, nip);
					if (summaryNode == null){
						summaryNode = createSummaryNode(authorityId, nop, nip);
					}
					//LOGGER.debug("REPRESENTED NODE (2) " + RDF2SQLEncoding.dictionaryDecode(n) + " BY " + sn);
					rep.put(n, summaryNode);
				}
			}
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
			TreeSet<Long> sip = n2ip.get(t.s);
			// probably both are null. We know rep doesn't exist, so we create it:
			long sAuthority = getOrComputeAuthorityId(t.s);
			repS = getSummaryNode(sAuthority, sop, sip);
			if (repS == null){
				repS = createSummaryNode(sAuthority, sop, sip);
			}
			rep.put(t.s, repS);
		}
		Long repO = rep.get(resolvedO);
		if (repO == null){
			TreeSet<Long> oop = n2op.get(resolvedO);
			TreeSet<Long> oip = n2ip.get(resolvedO);
			// probably both are null. We know rep doesn't exist, so we create it:
			long oAuthority = objectAuthorityId(t.s, t.o);
			repO = getSummaryNode(oAuthority, oop, oip);
			if (repO == null){
				repO = createSummaryNode(oAuthority, oop, oip);
			}
			rep.put(resolvedO, repO);
		}
		// Commented this out since the Traverser (also) adds the triple.
		// this.edgesWithProv.addTriple(repS, t.p, repO);
	}

	private long createSummaryNode(long authorityId, TreeSet<Long> nop, TreeSet<Long> nip) {
		long n = this.getNextSummaryNode();
        HashMap<TreeSet<Long>, HashMap<TreeSet<Long>, Long>> ip2op2snForAuthority = this.ip2op2sn.computeIfAbsent(authorityId, k -> new HashMap<>());
        HashMap<TreeSet<Long>, Long> o2n = ip2op2snForAuthority.computeIfAbsent(nip, k -> new HashMap<>());
        o2n.put(nop, n);
		return n;
	}

	private Long getSummaryNode(long authorityId, TreeSet<Long> nop, TreeSet<Long> nip) {
		HashMap<TreeSet<Long>, HashMap<TreeSet<Long>, Long>> ip2op2snForAuthority = this.ip2op2sn.get(authorityId);
		if (ip2op2snForAuthority == null){
			return null;
		}
		HashMap<TreeSet<Long>, Long> o2n = ip2op2snForAuthority.get(nip);
		if (o2n == null){
			return null;
		}
		return (o2n.get(nop));
	}
}
