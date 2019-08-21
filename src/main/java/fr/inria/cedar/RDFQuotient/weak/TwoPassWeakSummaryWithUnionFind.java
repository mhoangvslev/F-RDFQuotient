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
			if (n2o.get(t.s) == null) {
				n2o.put(t.s, new TreeSet<>());
			}
			n2o.get(t.s).add(t.p);

			repS = disjointSetForest.find(shiftNodeNumber(t.s));
			if (!ps.containsKey(t.p)) {
				ps.put(t.p, repS);
			}
			else {
				disjointSetForest.union(repS, ps.get(t.p)); // source-source union
			}
		}
		if (!sn.contains(t.o)) {
			if (n2i.get(t.o) == null) {
				n2i.put(t.o, new TreeSet<>());
			}
			n2i.get(t.o).add(t.p);

			repO = disjointSetForest.find(shiftNodeNumber(t.o));
			if (!pt.containsKey(t.p)) {
				pt.put(t.p, repO);
			}
			else {
				disjointSetForest.union(repO, pt.get(t.p)); // target-target union
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
				disjointSetForest.union(ps.get(p1), pt.get(p2)); // source-target union
			}
		}
	}

	@Override
	protected void representDataTriple(Triple t) {
		// schema nodes already represented in collectSchemaNodes
		if (!sn.contains(t.s)) {
			repS = disjointSetForest.find(ps.get(t.p));
			rep.put(t.s, repS);
		}
		if (!sn.contains(t.o)) {
			repO = disjointSetForest.find(pt.get(t.p));
			rep.put(t.o, repO);
		}
	}
}