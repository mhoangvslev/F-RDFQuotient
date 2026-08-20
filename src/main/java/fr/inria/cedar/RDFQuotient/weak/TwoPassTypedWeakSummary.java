//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.weak;

import fr.inria.cedar.RDFQuotient.datastructures.Long2Long;
import fr.inria.cedar.RDFQuotient.datastructures.Long2LongSet;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassTypedWeakSummary extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassTypedWeakSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	private final HashSet<Long> nodes;
	private final HashMap<Long, HashSet<Long>> n2i;
	private final HashMap<Long, HashSet<Long>> n2o;

	public TwoPassTypedWeakSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_TYPED_WEAK_SUMMARY_PREFIX;
		this.isTypeFirst = true;
		this.isDataAndType = false;
		this.isTwoPass = true;
		cs = new Long2LongSet();
		n2cs = new Long2Long();
		cs2csID = new HashMap<>();
		n2i = new HashMap<>();
		n2o = new HashMap<>();
		nodes = new HashSet<>();
	}

	@Override
	protected void representTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void classifyDataTriple(Triple t) {
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);
		boolean sTyped = (classSetS != null);
		boolean oTyped = (classSetO != null);

		if (!sn.contains(t.s) && !sTyped) {
			n2o.computeIfAbsent(t.s, k -> new HashSet<>());
			n2o.get(t.s).add(t.p);
		}
		if (!sn.contains(t.o) && !oTyped) {
			n2i.computeIfAbsent(t.o, k -> new HashSet<>());
			n2i.get(t.o).add(t.p);
		}
		nodes.add(t.s);
		nodes.add(t.o);
	}

	private Long getMin(ArrayList<Long> list) {
		Long min = list.get(0);
		for (Long i : list) {
			min = min < i ? min : i;
		}
		return min;
	}

	@Override
	protected void classificationPostProcessing() {
		findSummaryNodesAndEdges();
	}

	protected void findSummaryNodesAndEdges() {
		for (Long n: nodes) {
			// n plays a source role only where it's non-literal (n2o membership already guarantees
			// that), and a target role governed by its own authority, or AUTHORITY_NONE if literal
			long authorityId = coarseObjectAuthorityId(n);
			ArrayList<Long> outgoing = new ArrayList<>();
			if (n2o.containsKey(n)) {
				for (Long p: n2o.get(n)) {
					Long sp = sourceOfProperty(p, authorityId);
					if (sp != null) {
						outgoing.add(sp);
					}
				}
			}
			long minOutgoing = (!outgoing.isEmpty()) ? getMin(outgoing) : getNextSummaryNode();

			ArrayList<Long> incoming = new ArrayList<>();
			if (n2i.containsKey(n)) {
				for (Long p: n2i.get(n)) {
					Long tp = targetOfProperty(p, authorityId);
					if (tp != null) {
						incoming.add(tp);
					}
				}
			}
			long minIncoming = (!incoming.isEmpty()) ? getMin(incoming) : getNextSummaryNode();

			Long min = Math.min(minOutgoing, minIncoming);

			if (n2o.containsKey(n)) {
				for (Long p: n2o.get(n)) {
					Long sp = sourceOfProperty(p, authorityId);
					if (sp != null) { // apply source-target replacements, within this authority only
						for (HashMap<Long, Long> ptForP2: pt.values()) {
							Long v = ptForP2.get(authorityId);
							if (v != null && v.equals(sp)) {
								ptForP2.put(authorityId, min);
							}
						}
					}
					ps.computeIfAbsent(p, k -> new HashMap<>()).put(authorityId, min);
				}
			}
			if (n2i.containsKey(n)) {
				for (Long p: n2i.get(n)) {
					Long tp = targetOfProperty(p, authorityId);
					if (tp != null) { // apply source-target replacements, within this authority only
						for (HashMap<Long, Long> psForP2: ps.values()) {
							Long v = psForP2.get(authorityId);
							if (v != null && v.equals(tp)) {
								psForP2.put(authorityId, min);
							}
						}
					}
					pt.computeIfAbsent(p, k -> new HashMap<>()).put(authorityId, min);
				}
			}
		}
	}

	@Override
	protected void representDataTriple(Triple t) {
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);
		boolean sTyped = (classSetS != null);
		boolean oTyped = (classSetO != null);
		if (!sTyped && !sn.contains(t.s)) {
			repS = sourceOfProperty(t.p, getOrComputeAuthorityId(t.s));
			rep.put(t.s, repS);
		}
		//else {
		// typed or schema node already represented
		//}
		if (!oTyped && !sn.contains(t.o)) {
			repO = targetOfProperty(t.p, coarseObjectAuthorityId(t.o));
			rep.put(t.o, repO);
		}
		//else {
		// typed or schema node already represented
		//}
	}
}
