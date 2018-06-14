package fr.inria.cedar.quotientSummary.bisim;

import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class OneBisimSummary extends Summary{
	private static final Logger LOGGER = Logger.getLogger(OneBisimSummary.class.getName());

	private final HashMap<Long, TreeSet<Long>> n2ip; // node to incoming property set
	private final HashMap<Long, TreeSet<Long>> n2op; // node to outgoing property set
	private final HashMap<TreeSet<Long>, HashMap<TreeSet<Long>, Long>> ip2op2sn; // incoming property set to outgoing property set to summary node

	public OneBisimSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = ONEFB_SUMMARY_PREFIX;
		this.isTypeFirst = false;
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
		TreeSet<Long> previousSOP = n2op.get(t.s);
		if (previousSOP == null){
			previousSOP = new TreeSet<>();
			n2op.put(t.s, previousSOP);
		}
		previousSOP.add(t.p);
		TreeSet<Long> previousOIP = n2ip.get(t.o);
		if (previousOIP == null){
			previousOIP = new TreeSet<>();
			n2ip.put(t.o, previousOIP);
		}
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
				Long summaryNode = getSummaryNode(nop, nip);
				if (summaryNode == null){
					summaryNode = createSummaryNode(nop, nip);
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
					Long summaryNode = getSummaryNode(nop, nip);
					if (summaryNode == null){
						summaryNode = createSummaryNode(nop, nip);
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
		//LOGGER.debug("REPRESENTING DATA TRIPLE " + RDF2SQLEncoding.decode(t).toString());
		Long repS = rep.get(t.s);
		if (repS == null){
			TreeSet<Long> sop = n2op.get(t.s);
			TreeSet<Long> sip = n2ip.get(t.s);
			// probably both are null. We know rep doesn't exist, so we create it:
			repS = getSummaryNode(sop, sip);
			if (repS == null){
				repS = createSummaryNode(sop, sip);
			}
			rep.put(t.s, repS);
		}
		Long repO = rep.get(t.o);
		if (repO == null){
			TreeSet<Long> oop = n2op.get(t.s);
			TreeSet<Long> oip = n2ip.get(t.s);
			// probably both are null. We know rep doesn't exist, so we create it:
			repO = getSummaryNode(oop, oip);
			if (repO == null){
				repO = createSummaryNode(oop, oip);
			}
			rep.put(t.o, repO);
		}
		this.edgesWithProv.addTriple(repS, t.p, repO);
	}

	private long createSummaryNode(TreeSet<Long> nop, TreeSet<Long> nip) {
		long n = this.getNextSummaryNode();
		HashMap<TreeSet<Long>, Long> o2n = this.ip2op2sn.get(nip);
		if (o2n == null){
			o2n = new HashMap<>();
			this.ip2op2sn.put(nip, o2n);
		}
		o2n.put(nop, n);
		return n;
	}

	private Long getSummaryNode(TreeSet<Long> nop, TreeSet<Long> nip) {
		HashMap<TreeSet<Long>, Long> o2n = this.ip2op2sn.get(nip);
		if (o2n == null){
			return null;
		}
		return (o2n.get(nop));
	}

	@Override
	protected void consistencyChecks() {
		// TODO Auto-generated method stub
	}
}
