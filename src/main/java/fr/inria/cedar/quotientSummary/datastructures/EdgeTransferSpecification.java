package fr.inria.cedar.quotientSummary.datastructures;

import java.util.HashMap;
import java.util.HashSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class EdgeTransferSpecification {
	private static final Logger LOGGER = Logger.getLogger(EdgeTransferSpecification.class.getName());

	private static final char SOURCE = 0;
	private static final char TARGET = 1;

	private HashMap<Long, HashMap<Long, HashSet<Long>>> edgesToTransfer;

	public EdgeTransferSpecification() {
		LOGGER.setLevel(Level.INFO);
		edgesToTransfer = new HashMap<>();
	}

	// Examines the data node in order to determine which edges in the summary are going to be transferred
	public static HashMap<Long, HashMap<Long, HashSet<Long>>> determineEdgesToTransfer(
		HashMap<Long, Long2LongSet> triplesBySubject,
		HashMap<Long, Long2LongSet> triplesByObject,
		long dataNode,
		char param
	) {
		HashMap<Long, HashMap<Long, HashSet<Long>>> edgesToTransfer = new HashMap<>();

		if (param == SOURCE) { // distribute the edges outgoing from dataNode
			if (triplesBySubject.get(dataNode) != null) {
				for (long p: triplesBySubject.get(dataNode).keys()) {
					for (long o: triplesBySubject.get(dataNode).get(p)) {
						if (edgesToTransfer.get(dataNode) == null) {
							edgesToTransfer.put(dataNode, new HashMap<>());
						}
						if (edgesToTransfer.get(dataNode).get(p) == null) {
							edgesToTransfer.get(dataNode).put(p, new HashSet<>());
						}
						edgesToTransfer.get(dataNode).get(p).add(o);
					}
				}
			}
		}
		else if (param == TARGET) { // distribute the edges incoming to dataNode
			if (triplesByObject.get(dataNode) != null) {
				for (long p: triplesByObject.get(dataNode).keys()) {
					for (long s: triplesByObject.get(dataNode).get(p)) {
						if (edgesToTransfer.get(s) == null) {
							edgesToTransfer.put(s, new HashMap<>());
						}
						if (edgesToTransfer.get(s).get(p) == null) {
							edgesToTransfer.get(s).put(p, new HashSet<>());
						}
						edgesToTransfer.get(s).get(p).add(dataNode);
					}
				}
			}
		}

		return edgesToTransfer;
	}

	public void addAll(HashMap<Long, HashMap<Long, HashSet<Long>>> edgesToTransferToAdd) {
		if (edgesToTransfer.isEmpty()) {
			edgesToTransfer = edgesToTransferToAdd;
		}
		else { // 2 splits
			for (long s: edgesToTransferToAdd.keySet()) {
				for (long p: edgesToTransferToAdd.get(s).keySet()) {
					for (long o: edgesToTransferToAdd.get(s).get(p)) {
						if (edgesToTransfer.get(s) == null) {
							edgesToTransfer.put(s, new HashMap<>());
						}
						if (edgesToTransfer.get(s).get(p) == null) {
							edgesToTransfer.get(s).put(p, new HashSet<>());
						}
						edgesToTransfer.get(s).get(p).add(o);
					}
				}
			}
		}
	}

	public void applyTransfers(EdgesWithProvenanceCounts edgesWithProv, Long2Long rep, Long splitEdgeS, Long newRepS, Long splitEdgeO, Long newRepO) {
		for (long s: edgesToTransfer.keySet()) {
			for (long p: edgesToTransfer.get(s).keySet()) {
				for (long o: edgesToTransfer.get(s).get(p)) {
					Long repSTriple = rep.get(s);
					Long repOTriple = rep.get(o);
					long summaryEdgeCounter = edgesWithProv.getCounter(repSTriple, p, repOTriple);
					if (summaryEdgeCounter == 1L) {
						edgesWithProv.removeTriple(repSTriple, p, repOTriple);
					}
					else {
						edgesWithProv.setCounter(repSTriple, p, repOTriple, summaryEdgeCounter - 1L);
					}
				}
			}
		}

		rep.put(splitEdgeS, newRepS);
		rep.put(splitEdgeO, newRepO);

		for (long s: edgesToTransfer.keySet()) {
			for (long p: edgesToTransfer.get(s).keySet()) {
				for (long o: edgesToTransfer.get(s).get(p)) {
					Long finalS = rep.get(s);
					Long finalO = rep.get(o);
					boolean alreadyThere = edgesWithProv.containsEdge(finalS, p, finalO);
					if (alreadyThere) {
						edgesWithProv.setCounter(finalS, p, finalO, edgesWithProv.getCounter(finalS, p, finalO) + 1L);
					}
					else {
						edgesWithProv.addTriple(finalS, p, finalO);
						edgesWithProv.setCounter(finalS, p, finalO, 1L);
					}
				}
			}
		}
	}
}