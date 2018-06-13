package fr.inria.cedar.quotientSummary.weak;

import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassTypedWeakSummary extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassTypedWeakSummary.class.getName());

	private final HashSet<Long> nodes;
	private final HashMap<Long, TreeSet<Long>> n2i;
	private final HashMap<Long, TreeSet<Long>> n2o;

	public TwoPassTypedWeakSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_TYPED_WEAK_SUMMARY_PREFIX;
		this.isTypeFirst = true;
		this.isTwoPass = true;
		cs = new Long2LongSet();
		n2cs = new Long2Long();
		n2c = new Long2LongSet();
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
			if (n2o.get(t.s) == null) {
				n2o.put(t.s, new TreeSet<>());
			}
			n2o.get(t.s).add(t.p);
		}
		if (!sn.contains(t.o) && !oTyped) {
			if (n2i.get(t.o) == null) {
				n2i.put(t.o, new TreeSet<>());
			}
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
			ArrayList<Long> outgoing = new ArrayList<>();
			if (n2o.containsKey(n)) {
				for (Long p: n2o.get(n)) {
					if (ps.get(p) != null) {
						outgoing.add(ps.get(p));
					}
				}
			}
			Long minOutgoing = (!outgoing.isEmpty()) ? getMin(outgoing) : getNextSummaryNode();

			ArrayList<Long> incoming = new ArrayList<>();
			if (n2i.containsKey(n)) {
				for (Long p: n2i.get(n)) {
					if (pt.get(p) != null) {
						incoming.add(pt.get(p));
					}
				}
			}
			Long minIncoming = (!incoming.isEmpty()) ? getMin(incoming) : getNextSummaryNode();

			Long min = (minOutgoing < minIncoming) ? minOutgoing : minIncoming;

			if (n2o.containsKey(n)) {
				for (Long p: n2o.get(n)) {
					if (ps.get(p) != null) { // apply source-target replacements
						pt.replaceAll((k, v) -> (Objects.equals(v, ps.get(p))) ? min : v);
					}
					ps.put(p, min);
				}
			}
			if (n2i.containsKey(n)) {
				for (Long p: n2i.get(n)) {
					if (pt.get(p) != null) { // apply source-target replacements
						ps.replaceAll((k, v) -> (Objects.equals(v, pt.get(p))) ? min : v);
					}
					pt.put(p, min);
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
		if (!sTyped) {
			Long repS = ps.get(t.p);
			if (sn.contains(t.s)) {
				repS = t.s;
			}
			rep.put(t.s, repS);
		}
		//else {
		// typed node already represented
		//}
		if (!oTyped) {
			Long repO = pt.get(t.p);
			if (sn.contains(t.o)) {
				repO = t.o;
			}
			rep.put(t.o, repO);
		}
		//else {
		// typed node already represented
		//}
	}
}