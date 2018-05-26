package fr.inria.cedar.quotientSummary.datastructures;

import java.util.HashMap;
import java.util.Objects;
import javafx.util.Pair;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class EdgeTransferSpecification {
	private static final Logger LOGGER = Logger.getLogger(EdgeTransferSpecification.class.getName());

	private static final char SOURCE = 0;
	private static final char TARGET = 1;

	private HashMap<Long, HashMap<Long, HashMap<Long, Pair<Long, Character>>>> edgesToTransfer;

	public EdgeTransferSpecification() {
		LOGGER.setLevel(Level.INFO);
		edgesToTransfer = new HashMap<>();
	}

	// Examines the data node in order to determine which edges in the summary are going to be transferred
	public static HashMap<Long, HashMap<Long, HashMap<Long, Pair<Long, Character>>>> determineEdgesToTransfer(
		Long2Long rep,
		HashMap<Long, Long2LongSet> triplesBySubject,
		HashMap<Long, Long2LongSet> triplesByObject,
		Long dataNode,
		Long summaryNode,
		char param
	) {
		// CAUTION: uses old rep

		HashMap<Long, HashMap<Long, HashMap<Long, Pair<Long, Character>>>> edgesToTransfer = new HashMap<>();

		if (param == SOURCE) { // distribute the edges outgoing from dataNode
			if (triplesBySubject.get(dataNode) != null) {
				for (Long p: triplesBySubject.get(dataNode).keys()) {
					for (Long o: triplesBySubject.get(dataNode).get(p)) {
						Long repO = rep.get(o);
						if (repO != null) {
							if (edgesToTransfer.get(summaryNode) == null) {
								edgesToTransfer.put(summaryNode, new HashMap<>());
							}
							if (edgesToTransfer.get(summaryNode).get(p) == null) {
								edgesToTransfer.get(summaryNode).put(p, new HashMap<>());
							}
							if (edgesToTransfer.get(summaryNode).get(p).get(repO) == null) {
								edgesToTransfer.get(summaryNode).get(p).put(repO, new Pair(1L, SOURCE));
							}
							else {
								edgesToTransfer.get(summaryNode).get(p).put(repO, new Pair(edgesToTransfer.get(summaryNode).get(p).get(repO).getKey() + 1L, SOURCE));
							}
						}
					}
				}
			}
		}
		else if (param == TARGET) { // distribute the edges incoming to dataNode
			if (triplesByObject.get(dataNode) != null) {
				for (Long p: triplesByObject.get(dataNode).keys()) {
					for (Long s: triplesByObject.get(dataNode).get(p)) {
						Long repS = rep.get(s);
						if (repS != null) {
							if (edgesToTransfer.get(repS) == null) {
								edgesToTransfer.put(repS, new HashMap<>());
							}
							if (edgesToTransfer.get(repS).get(p) == null) {
								edgesToTransfer.get(repS).put(p, new HashMap<>());
							}
							if (edgesToTransfer.get(repS).get(p).get(summaryNode) == null) {
								edgesToTransfer.get(repS).get(p).put(summaryNode, new Pair(1L, TARGET));
							}
							else {
								edgesToTransfer.get(repS).get(p).put(summaryNode, new Pair(edgesToTransfer.get(repS).get(p).get(summaryNode).getKey() + 1L, TARGET));
							}
						}
					}
				}
			}
		}

		return edgesToTransfer;
	}

	public void addAll(HashMap<Long, HashMap<Long, HashMap<Long, Pair<Long, Character>>>> edgesToTransferToAdd) {
		if (edgesToTransfer.isEmpty()) {
			edgesToTransfer = edgesToTransferToAdd;
		}
		else { // 2 splits
			for (Long s: edgesToTransferToAdd.keySet()) {
				for (Long p: edgesToTransferToAdd.get(s).keySet()) {
					for (Long o: edgesToTransferToAdd.get(s).get(p).keySet()) {
						Long counter = edgesToTransferToAdd.get(s).get(p).get(o).getKey();
						Character param = edgesToTransferToAdd.get(s).get(p).get(o).getValue();
						if (edgesToTransfer.get(s) == null) {
							edgesToTransfer.put(s, new HashMap<>());
						}
						if (edgesToTransfer.get(s).get(p) == null) {
							edgesToTransfer.get(s).put(p, new HashMap<>());
						}
						if (edgesToTransfer.get(s).get(p).get(o) == null) {
							edgesToTransfer.get(s).get(p).put(o, new Pair(counter, param));
						}
						else {
							// reconcile
							if (!Objects.equals(edgesToTransfer.get(s).get(p).get(o).getKey(), counter)) {
								throw new IllegalStateException("Impossible case, counters cannot differ");
							}
							//else {
							// we already know the specification of this summary edge transfer (we can have it only once, counters do NOT sum up!)
							//}
						}
					}
				}
			}
		}
	}

	public void applyTransfers(EdgesWithProvenanceCounts edgesWithProv, Long repS, Long newRepS, Long repO, Long newRepO) {
		for (Long s: edgesToTransfer.keySet()) {
			for (Long p: edgesToTransfer.get(s).keySet()) {
				for (Long o: edgesToTransfer.get(s).get(p).keySet()) {
					Long counter = edgesToTransfer.get(s).get(p).get(o).getKey();
					Character param = edgesToTransfer.get(s).get(p).get(o).getValue();
					Long summaryEdgeCounter = edgesWithProv.getCounter(s, p, o);
					if (counter > summaryEdgeCounter) {
						throw new IllegalStateException("The value which is subtracted cannot be greater then summary counter for this edge");
					}

					if (Objects.equals(summaryEdgeCounter, counter)) {
						edgesWithProv.removeTriple(s, p, o);
					}
					else {
						edgesWithProv.setCounter(s, p, o, summaryEdgeCounter - counter);
					}

					// reconcile
					Long finalS = s;
					Long finalO = o;
					if (Objects.equals(s, repS)) {
						finalS = newRepS;
					}
					if (Objects.equals(o, repS)) {
						finalO = newRepS;
					}
					if (Objects.equals(o, repO)) {
						finalO = newRepO;
					}
					if (Objects.equals(s, repO)) {
						finalS = newRepO;
					}
					Long alreadyThere = edgesWithProv.getCounter(finalS, p, finalO);
					if (alreadyThere == null || alreadyThere == 0L) {
						edgesWithProv.addTriple(finalS, p, finalO);
						edgesWithProv.setCounter(finalS, p, finalO, counter);
					}
					else {
						edgesWithProv.setCounter(finalS, p, finalO, counter + alreadyThere);
					}
				}
			}
		}
	}
}