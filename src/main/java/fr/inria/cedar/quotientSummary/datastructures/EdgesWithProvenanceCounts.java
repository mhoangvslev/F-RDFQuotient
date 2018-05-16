package fr.inria.cedar.quotientSummary.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import fr.inria.cedar.quotientSummary.Summary;

public class EdgesWithProvenanceCounts {
	private static final Logger LOGGER = Logger.getLogger(Summary.class.getName());
	protected HashMap<Long, HashMap<Long, TreeSet<Long>>> edges;
	protected HashMap<Long, HashMap<Long, HashMap<Long, Long>>> counts;

	public EdgesWithProvenanceCounts(){
		edges = new HashMap<Long, HashMap<Long, TreeSet<Long>>>();
		counts = new HashMap<Long, HashMap<Long, HashMap<Long, Long>>>();
	}

	public HashMap<Long, TreeSet<Long>> get(Long s){
		return edges.get(s); 
	}

	/**
	 * Adds an integer-encoded triple to the summary
	 *
	 * @param s
	 * @param p
	 * @param o
	 */
	public final void addTriple(Long s, Long p, Long o) {
		System.out.println("ADDING SUMMARY TRIPLE: " + s + " " + p + " " + o);
		Triple t = new Triple(s, p, o);
		HashMap<Long, TreeSet<Long>> triplesForThisSubject = edges.get(s);
		HashMap<Long,  HashMap<Long, Long>> countsForThisSubject = counts.get(s); 
		
		if (triplesForThisSubject == null) { // no edges yet for this subject;
			// otherwise, s has already some edges
			triplesForThisSubject = new HashMap<>();
			countsForThisSubject = new  HashMap<Long, HashMap<Long, Long>>();
			edges.put(s, triplesForThisSubject);
			counts.put(s, countsForThisSubject); 
		}
		TreeSet<Long> objectsForThisSubjectAndProperty = triplesForThisSubject.get(p);
		HashMap<Long, Long> countsForThisSubjectAndProperty = countsForThisSubject.get(p); 
		if (objectsForThisSubjectAndProperty == null) { // no edges yet for this subject and property; otherwise, s has already some p edges
			objectsForThisSubjectAndProperty = new TreeSet<>();
			triplesForThisSubject.put(t.p, objectsForThisSubjectAndProperty);
			countsForThisSubjectAndProperty = new HashMap<Long, Long>(); 
			countsForThisSubject.put(t.p, countsForThisSubjectAndProperty); 
		}
		if (!objectsForThisSubjectAndProperty.contains(t.o)) { // otherwise, s p o
			objectsForThisSubjectAndProperty.add(o);
			countsForThisSubjectAndProperty.put(t.o, 1L);
		}
		else{
			Long count = countsForThisSubjectAndProperty.get(t.o);
			countsForThisSubjectAndProperty.put(t.o, (count+1)); 
		}
	}

	public void setCounter(Long s, Long p, Long o, Long value){
		HashMap<Long,  HashMap<Long, Long>> countsForS = counts.get(s); 
		if (countsForS == null){
			countsForS = new HashMap<Long, HashMap<Long, Long>>();
			counts.put(s, countsForS);
		}
		HashMap<Long, Long> countsForSP = countsForS.get(p);
		if (countsForSP == null){
			countsForSP = new HashMap<Long, Long>();
			countsForS.put(p, countsForSP);
		}
		countsForSP.put(o, value); // overwrites whatever was there
	}

	public Long getCounter(Long s, Long p, Long o){
		HashMap<Long,  HashMap<Long, Long>> countsForS = counts.get(s); 
		if (countsForS == null){
			return 0L; 
		}
		HashMap<Long, Long> countsForSP = countsForS.get(p);
		if (countsForSP == null){
			return 0L; 
		}
		return countsForSP.get(o); 
	}
	
	
	// replaces in summary edges
	public void replaceNodeInSummaryEdges(Long oldNode, Long newNode) {
		// replace oldNode wherever it existed as an object:
		for (long s : edges.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfO = edges.get(s);
			for (long p : triplesOfO.keySet()) {
				TreeSet<Long> objectsForSP = triplesOfO.get(p);
				TreeSet<Long> newObjectsForSP = new TreeSet<>();
				boolean changed = false;
				for (long o : objectsForSP) {
					if (o == oldNode) {
						if (!newObjectsForSP.contains(newNode)) {
							newObjectsForSP.add(newNode);
							setCounter(s, p, newNode, getCounter(s, p, oldNode)); // edge count transferred
						}
						else{ // newNode was already there, we need to add the edge count from oldNode to that of newNode
							setCounter(s, p, newNode, (getCounter(s, p, oldNode) + getCounter(s, p, newNode))); 
						}
						changed = true;
					}
					else{
						newObjectsForSP.add(o);
					}
				}
				if (changed)
					triplesOfO.replace(p, newObjectsForSP); // replace is not a structural modification of the map, thus no concurrent modification exception
			}
		}
		// above we have replaced old with new wherever it appeared as an object.
		// now let's also do it ***as a subject:***
		
		HashMap<Long, TreeSet<Long>> oldNodeIsSubject = edges.get(oldNode);
		HashMap<Long, HashMap<Long, Long>> countsOnOldSubject = counts.get(oldNode);
		if (oldNodeIsSubject != null) { // in some edges, oldNode was subject
			edges.remove(oldNode); // detach this entry from edges (but keep them in oldNodeIsSubject for now)
			counts.remove(oldNode); 

			HashMap<Long, TreeSet<Long>> newNodeIsSubject = edges.get(newNode);
			if (newNodeIsSubject == null) { // the new node was not previously a subject 
				//System.out.println("   SUMMARY.REPLACE IN EDGES: Adding on the new node " + newNode + " the triples of old node " + oldNode);
				edges.put(newNode, oldNodeIsSubject); // we're done
				counts.put(newNode, countsOnOldSubject); 
			} 
			else {  // there were already edges whose subject was the new node
				if (oldNodeIsSubject != null) { 
					// in this case we need to fuse the two maps so that each edge appears only once
					// we will do this by copying those oldNodeIsSubject triples
					// which were not already on the new node, into the properties
					// of the new node
					//System.out.println("   SUMMARY.REPLACE IN EDGES: There were edges both on old " + oldNode + " and on new " + newNode);

					for (Long p : oldNodeIsSubject.keySet()) { // iterate over the properties of the old node
						TreeSet<Long> oldNodeObjectsForP = oldNodeIsSubject.get(p);
						TreeSet<Long> newNodeObjectsForP = newNodeIsSubject.get(p);
						if (newNodeObjectsForP == null) { // the new node did not have this one => initializing
							//System.out.println("   SUMMARY.REPLACE IN EDGES: " + newNode + " did not have edges labeled " + oldNodeProperty
							//		+ ", he is taking them from " + oldNode);
							newNodeObjectsForP = new TreeSet<Long>();
							newNodeIsSubject.put(p, newNodeObjectsForP);
						}
						// whether the new node did or did not have triples labeled
						// oldNodeProperty, try to give him the triples labeled
						// oldNodeProperty of the old node:
						for (Long objectOfOldNode : oldNodeObjectsForP) {
							if (!newNodeObjectsForP.contains(objectOfOldNode)) {
								//System.out.println("   SUMMARY.REPLACE IN EDGES: " +newNode + " takes property " + oldNodeProperty + " with value "
								//		+ objectOfOldNode + " from " + oldNode);
								newNodeObjectsForP.add(objectOfOldNode);
								setCounter(newNode, p, objectOfOldNode, getCounter(oldNode, p, objectOfOldNode)); // transfer edge counts
							} 
							else {
								//System.out.println("   SUMMARY.REPLACE IN EDGES: " +newNode + " already had property " + oldNodeProperty + " with value "	+ objectOfOldNode);
								setCounter(newNode, p, objectOfOldNode,
										(getCounter(oldNode, p, objectOfOldNode) + getCounter(newNode, p, objectOfOldNode))); 
							}
						}
					}
				}
			}
		}
		else {
			// there was no edge with oldNode as a subject, no subject replacement to do
		}
		//System.out.println("   SUMMARY.REPLACE IN EDGES ends");

	}

	public ArrayList<Triple> getSummaryEdges() {
		ArrayList<Triple> res = new ArrayList<>();
		for (Long s : edges.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edges.get(s);
			for (Long p : triplesOfThisSubject.keySet()) {
				TreeSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				for (Long o : objectsOfThisSandP) {
					Triple t = new Triple(s, p, o);
					res.add(t);
				}
			}
		}
		return res;
	}

	public void removeTriple(Long s, Long p, Long o){
		HashMap<Long, TreeSet<Long>> edgesOfS = edges.get(s); 
		if (edgesOfS != null){
			TreeSet<Long> pEdgesOfS = edgesOfS.get(p);
			if (pEdgesOfS != null){
				pEdgesOfS.remove(o); 
			}
		}
	}

	public Set<Long> keySet(){
		return edges.keySet();
	}

	public Long totalEdgeCount(){
		long res = 0;
		for (Long s: counts.keySet()){
			HashMap<Long, HashMap<Long, Long>> maps = counts.get(s); 
			for (Long p: maps.keySet()){
				HashMap<Long, Long> mapsp = maps.get(p); 
				for (Long o: mapsp.keySet()){
					res += mapsp.get(o); 
				}
			}
		}
		return res; 
	}
	public void display() {
		StringBuffer sb = new StringBuffer();
		for (Triple t: this.getSummaryEdges()){
			sb.append(t.toString() + ": " + getCounter(t.s, t.p, t.o) + "\n"); 
		}
		System.out.println(new String(sb)); 
	}
}
