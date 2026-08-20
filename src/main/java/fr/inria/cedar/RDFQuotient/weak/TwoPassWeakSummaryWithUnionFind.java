//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.weak;

import fr.inria.cedar.RDFQuotient.datastructures.DisjointSetForest;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassWeakSummaryWithUnionFind extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassWeakSummaryWithUnionFind.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	private final DisjointSetForest disjointSetForest;
	private final HashSet<Long> nodes;
	private final HashMap<Long, TreeSet<Long>> n2i;
	private final HashMap<Long, TreeSet<Long>> n2o;

	public TwoPassWeakSummaryWithUnionFind(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_WEAK_SUMMARY_WITH_UNION_FIND_PREFIX;
		this.isTypeFirst = false;
		this.isDataAndType = false;
		this.isTwoPass = true;
		n2i = new HashMap<>();
		n2o = new HashMap<>();
		nodes = new HashSet<>();
		disjointSetForest = new DisjointSetForest();
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void classifyDataTriple(Triple t) {
		if (!sn.contains(t.s)) {
            n2o.computeIfAbsent(t.s, k -> new TreeSet<>());
			n2o.get(t.s).add(t.p);

			long sAuthorityId = getOrComputeAuthorityId(t.s);
			repS = disjointSetForest.find(shiftNodeNumber(t.s));
			HashMap<Long, Long> psForP = ps.computeIfAbsent(t.p, k -> new HashMap<>());
			if (!psForP.containsKey(sAuthorityId)) {
				psForP.put(sAuthorityId, repS);
			}
			else {
				disjointSetForest.union(repS, psForP.get(sAuthorityId)); // source-source union
			}
		}
		if (!sn.contains(t.o)) {
            n2i.computeIfAbsent(t.o, k -> new TreeSet<>());
			n2i.get(t.o).add(t.p);

			long oAuthorityId = coarseObjectAuthorityId(t.o);
			repO = disjointSetForest.find(shiftNodeNumber(t.o));
			HashMap<Long, Long> ptForP = pt.computeIfAbsent(t.p, k -> new HashMap<>());
			if (!ptForP.containsKey(oAuthorityId)) {
				ptForP.put(oAuthorityId, repO);
			}
			else {
				disjointSetForest.union(repO, ptForP.get(oAuthorityId)); // target-target union
			}
		}
		nodes.add(t.s);
		nodes.add(t.o);
	}

	// shifting node number by maxSummaryNode so that summary nodes have number that doesn't appear in the dictionary
	private Long shiftNodeNumber(Long nodeNumber) {
		return nodeNumber + maxSummaryNode;
	}

	@Override
	protected void classificationPostProcessing() {
		findSummaryNodesAndEdges();
	}

	protected void findSummaryNodesAndEdges() {
		for (Long n: nodes) {
			if (n2o.containsKey(n) && n2i.containsKey(n)) {
				Long p1 = n2o.get(n).first();
				Long p2 = n2i.get(n).first();
				// n plays both roles here, so it cannot be a literal (only a real resource can have
				// outgoing edges); its own authority governs both lookups
				long authorityId = getOrComputeAuthorityId(n);
				disjointSetForest.union(ps.get(p1).get(authorityId), pt.get(p2).get(authorityId)); // source-target union
			}
		}
	}

	@Override
	protected void representDataTriple(Triple t) {
		// schema nodes already represented in collectSchemaNodes
		if (!sn.contains(t.s)) {
			repS = disjointSetForest.find(ps.get(t.p).get(getOrComputeAuthorityId(t.s)));
			rep.put(t.s, repS);
		}
		if (!sn.contains(t.o)) {
			repO = disjointSetForest.find(pt.get(t.p).get(coarseObjectAuthorityId(t.o)));
			rep.put(t.o, repO);
		}
	}
}
