package fr.inria.cedar.quotientSummary.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class EdgesWithProvenanceCounts {
	private static final Logger LOGGER = Logger.getLogger(EdgesWithProvenanceCounts.class.getName());
	protected HashMap<Long, HashMap<Long, HashSet<Long>>> edges;
	protected HashMap<Long, HashMap<Long, HashMap<Long, Long>>> counts;

	public EdgesWithProvenanceCounts(){
		LOGGER.setLevel(Level.INFO);
		edges = new HashMap<>();
		counts = new HashMap<>();
	}

	public HashMap<Long, HashSet<Long>> get(long s){
		return edges.get(s);
	}

	/**
	 * Adds an integer-encoded triple to the summary
	 *
	 * @param s
	 * @param p
	 * @param o
	 */
	public final void addTriple(long s, long p, long o) {
		//LOGGER.debug("ADDING SUMMARY TRIPLE: " + s + " " + p + " " + o);
		HashMap<Long, HashSet<Long>> triplesForThisSubject = edges.get(s);
		HashMap<Long, HashMap<Long, Long>> countsForThisSubject = counts.get(s);

		if (triplesForThisSubject == null) { // no edges yet for this subject;
			// otherwise, s has already some edges
			triplesForThisSubject = new HashMap<>();
			countsForThisSubject = new HashMap<>();
			edges.put(s, triplesForThisSubject);
			counts.put(s, countsForThisSubject);
		}
		HashSet<Long> objectsForThisSubjectAndProperty = triplesForThisSubject.get(p);
		HashMap<Long, Long> countsForThisSubjectAndProperty = countsForThisSubject.get(p);
		if (objectsForThisSubjectAndProperty == null) { // no edges yet for this subject and property; otherwise, s has already some p edges
			objectsForThisSubjectAndProperty = new HashSet<>();
			triplesForThisSubject.put(p, objectsForThisSubjectAndProperty);
			countsForThisSubjectAndProperty = new HashMap<>();
			countsForThisSubject.put(p, countsForThisSubjectAndProperty);
		}
		if (!objectsForThisSubjectAndProperty.contains(o)) { // otherwise, s p o
			objectsForThisSubjectAndProperty.add(o);
			countsForThisSubjectAndProperty.put(o, 1L);
		}
		else {
			long count = countsForThisSubjectAndProperty.get(o);
			countsForThisSubjectAndProperty.put(o, (count + 1L));
		}
	}

	public boolean containsEdge(long s, long p, long o) {
		return edges.get(s) != null && edges.get(s).get(p) != null && edges.get(s).get(p).contains(o);
	}

	public void setCounter(long s, long p, long o, long value) {
		counts.get(s).get(p).put(o, value); // overwrites whatever was there
	}

	public long getCounter(long s, long p, long o) {
		HashMap<Long,  HashMap<Long, Long>> countsForS = counts.get(s);
		if (countsForS == null){
			return 0L;
		}
		HashMap<Long, Long> countsForSP = countsForS.get(p);
		if (countsForSP == null){
			return 0L;
		}
		return countsForSP.get(o) != null ? countsForSP.get(o) : 0L;
	}

	public void replaceNodeInSummaryEdges(long oldNode, long newNode) {
		// replacing oldNode with newNode in object position
		for (long s: edges.keySet()) {
			for (long p: edges.get(s).keySet()) {
				if (edges.get(s).get(p).contains(oldNode)) {
					long oldNodeCounter = getCounter(s, p, oldNode);
					edges.get(s).get(p).remove(oldNode);
					counts.get(s).get(p).remove(oldNode);
					if (!edges.get(s).get(p).contains(newNode)) {
						edges.get(s).get(p).add(newNode);
						counts.get(s).get(p).put(newNode, oldNodeCounter);
					}
					else {
						setCounter(s, p, newNode, oldNodeCounter + getCounter(s, p, newNode));
					}
				}
			}
		}

		// replacing oldNode with newNode in subject position
		if (edges.get(oldNode) != null) {
			HashMap<Long, HashMap<Long, HashSet<Long>>> newEdges = new HashMap<>();
			newEdges.put(newNode, new HashMap<>());
			for (long p: edges.get(oldNode).keySet()) {
				newEdges.get(newNode).put(p, edges.get(oldNode).get(p));
			}
			if (edges.get(newNode) == null) {
				edges.put(newNode, new HashMap<>());
				counts.put(newNode, new HashMap<>());
			}
			for (long p: newEdges.get(newNode).keySet()) {
				for (long o: newEdges.get(newNode).get(p)) {
					long oldNodeCounter = getCounter(oldNode, p, o);
					if (edges.get(newNode).get(p) == null) {
						edges.get(newNode).put(p, new HashSet<>());
						counts.get(newNode).put(p, new HashMap<>());
					}
					if (!edges.get(newNode).get(p).contains(o)) {
						edges.get(newNode).get(p).add(o);
						counts.get(newNode).get(p).put(o, oldNodeCounter);
					}
					else {
						setCounter(newNode, p, o, oldNodeCounter + getCounter(newNode, p, o));
					}
				}
			}
			edges.remove(oldNode);
			counts.remove(oldNode);
		}
	}

	public ArrayList<Triple> getSummaryEdges() {
		ArrayList<Triple> res = new ArrayList<>();
		for (long s : edges.keySet()) {
			HashMap<Long, HashSet<Long>> triplesOfThisSubject = edges.get(s);
			for (long p : triplesOfThisSubject.keySet()) {
				HashSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				for (long o : objectsOfThisSandP) {
					Triple t = new Triple(s, p, o);
					res.add(t);
				}
			}
		}
		return res;
	}

	public void removeTriple(long s, long p, long o) {
		//LOGGER.debug("REMOVING SUMMARY TRIPLE: " + s + " " + p + " " + o);
		if (edges.get(s) == null || edges.get(s).get(p) == null || !edges.get(s).get(p).contains(o)) {
			throw new IllegalStateException("Triple " + s + " " + p + " " + o + " does not exist in edges");
		}
		if (counts.get(s) == null || counts.get(s).get(p) == null || counts.get(s).get(p).get(o) == null) {
			throw new IllegalStateException("Triple " + s + " " + p + " " + o + " does not exist in edges counts");
		}

		edges.get(s).get(p).remove(o);
		counts.get(s).get(p).remove(o);

		if (edges.get(s).get(p).isEmpty()) {
			edges.get(s).remove(p);
			counts.get(s).remove(p);
			if (edges.get(s).keySet().isEmpty()) {
				edges.remove(s);
				counts.remove(s);
			}
		}
	}

	public Set<Long> keySet(){
		return edges.keySet();
	}

	public long totalEdgeCount(){
		long res = 0L;
		for (long s: counts.keySet()) {
			HashMap<Long, HashMap<Long, Long>> maps = counts.get(s);
			for (long p: maps.keySet()) {
				HashMap<Long, Long> mapsp = maps.get(p);
				for (long o: mapsp.keySet()) {
					res += mapsp.get(o);
				}
			}
		}
		return res;
	}

	public void display() {
		StringBuilder sb = new StringBuilder();
		for (Triple t: this.getSummaryEdges()){
			sb.append(t.toString()).append(": ").append(getCounter(t.s, t.p, t.o)).append("\n");
		}
		System.out.println(sb.toString());
	}
}
