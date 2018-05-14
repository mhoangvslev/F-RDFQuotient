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
	protected HashMap<Long, HashMap<Long, TreeSet<Long>>> counts;

	public EdgesWithProvenanceCounts(){
		edges = new HashMap<Long, HashMap<Long, TreeSet<Long>>>();
		counts = new HashMap<Long, HashMap<Long, TreeSet<Long>>>();
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
		Triple t = new Triple(s, p, o);

		HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edges.get(s);
		if (triplesOfThisSubject == null) { // no edges yet for this subject;
			// otherwise, s has already some edges
			triplesOfThisSubject = new HashMap<>();
			edges.put(s, triplesOfThisSubject);
		}
		TreeSet<Long> objectsOfThisSubjectAndProperty = triplesOfThisSubject.get(p);
		if (objectsOfThisSubjectAndProperty == null) { // no edges yet for this subject and property; otherwise, s has already some p edges
			objectsOfThisSubjectAndProperty = new TreeSet<>();
			triplesOfThisSubject.put(t.p, objectsOfThisSubjectAndProperty);
		}
		if (!objectsOfThisSubjectAndProperty.contains(t.o)) { // otherwise, s p o
			objectsOfThisSubjectAndProperty.add(o);
		}
	}


	// replaces in summary edges, not in rep
	// this one is called by the Weak summarization classes
	// TODO one day check if it's identical to the one below or not
	public void replaceNodeInSummaryEdges(Long oldNode, Long newNode) {
		// replace oldNode wherever it existed as an object:
		for (long s : edges.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edges.get(s);
			for (long propOfThisSubject : triplesOfThisSubject.keySet()) {
				TreeSet<Long> objectsForThisSubjectAndProperty = triplesOfThisSubject.get(propOfThisSubject);
				TreeSet<Long> newObjectsForThisSubjectAndProperty = new TreeSet<>();
				boolean arrayChanged = false;
				for (long o : objectsForThisSubjectAndProperty)
					if (o == oldNode) {
						if (!newObjectsForThisSubjectAndProperty.contains(newNode))
							newObjectsForThisSubjectAndProperty.add(newNode);
						arrayChanged = true;
					}
					else
						newObjectsForThisSubjectAndProperty.add(o);

				if (arrayChanged)
					triplesOfThisSubject.replace(propOfThisSubject, newObjectsForThisSubjectAndProperty); // replace is not a structural modification of the map, thus no concurrent modification exception
			}
		}
		// above we have replaced old with new wherever it appeared *** as an object ***
		// now let's also do it for the subject:
		HashMap<Long, TreeSet<Long>> oldNodeIsSubject = edges.get(oldNode);
		if (oldNodeIsSubject != null) { // in some edges, oldNode was subject
			//System.out.println("   SUMMARY.REPLACE IN EDGES: Removing edges whose subject is " + oldNode);
			edges.remove(oldNode); // detach this entry from edges (but keep
			// them in oldNodeIsSubject for now)

			HashMap<Long, TreeSet<Long>> newNodeIsSubject = edges.get(newNode);
			if (newNodeIsSubject == null) { // the new node was not previously a
				// subject of some edges
				//System.out.println("   SUMMARY.REPLACE IN EDGES: Adding on the new node " + newNode + " the triples of old node " + oldNode);
				edges.put(newNode, oldNodeIsSubject); // we're done
			} else // there were already edges whose subject was the new node
				if (oldNodeIsSubject != null) { // in this case we need to fuse the
					// two maps so that each edge
					// appears only once
					// we will do this by copying those oldNodeIsSubject triples
					// which were not already on the new node, into the properties
					// of the new node
					//System.out.println("   SUMMARY.REPLACE IN EDGES: There were edges both on old " + oldNode + " and on new " + newNode);

					for (Long oldNodeProperty : oldNodeIsSubject.keySet()) { // iterate
						// over the properties of the old node
						TreeSet<Long> oldNodeObjectsForThisProperty = oldNodeIsSubject.get(oldNodeProperty);
						TreeSet<Long> newNodeObjectsForThisProperty = newNodeIsSubject.get(oldNodeProperty);
						if (newNodeObjectsForThisProperty == null) { // the new node
							// did not have this one
							//System.out.println("   SUMMARY.REPLACE IN EDGES: " + newNode + " did not have edges labeled " + oldNodeProperty
							//		+ ", he is taking them from " + oldNode);
							newNodeObjectsForThisProperty = new TreeSet<Long>();
							newNodeIsSubject.put(oldNodeProperty, newNodeObjectsForThisProperty);
						}
						// whether the new node did or did not have triples labeled
						// oldNodeProperty, try to give him the triples labeled
						// oldNodeProperty of the old node:
						for (Long objectOfOldNode : oldNodeObjectsForThisProperty)
							if (!newNodeObjectsForThisProperty.contains(objectOfOldNode)) {
								//System.out.println("   SUMMARY.REPLACE IN EDGES: " +newNode + " takes property " + oldNodeProperty + " with value "
								//		+ objectOfOldNode + " from " + oldNode);
								newNodeObjectsForThisProperty.add(objectOfOldNode);
							} 
							else {
								//System.out.println("   SUMMARY.REPLACE IN EDGES: " +newNode + " already had property " + oldNodeProperty + " with value "	+ objectOfOldNode);
							}
					}
				}
		}
		else {
			// there was no edge with oldNode as a subject, no subject replacement to do
		}
		//System.out.println("   SUMMARY.REPLACE IN EDGES ends");

	}

	// called by 5 handlers in StrongOrTypedStrongSummary
	// updates exactly edges, does nothing else, changes nothing else
	// TODO one day check if it's identical to the one below or not
	public void replaceNodeInEdges(Long oldNode, Long newNode){
		// replace oldNode wherever it existed as an object:
		for (long s : edges.keySet()) {
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edges.get(s);
			for (long propOfThisSubject : triplesOfThisSubject.keySet()) {
				TreeSet<Long> objectsForThisSubjectAndProperty = triplesOfThisSubject.get(propOfThisSubject);
				TreeSet<Long> newObjectsForThisSubjectAndProperty = new TreeSet<Long>();
				boolean changed = false;
				for (long o : objectsForThisSubjectAndProperty)
					if (o == oldNode) {
						if (!newObjectsForThisSubjectAndProperty.contains(newNode))
							newObjectsForThisSubjectAndProperty.add(newNode);
						changed = true;
					} else
						newObjectsForThisSubjectAndProperty.add(o);
				if (changed)
					triplesOfThisSubject.replace(propOfThisSubject, newObjectsForThisSubjectAndProperty); // replace
				// is not a structural modification of the map, thus no concurrent modification exception
			}
		}
		// above we have replaced old with new wherever it appeared *** as an
		// object ***

		// now let's also do it for the subject:
		HashMap<Long, TreeSet<Long>> oldNodeIsSubject = edges.get(oldNode);
		if (oldNodeIsSubject != null) { // in some edges, oldNode was subject
			edges.remove(oldNode); // detach this entry from edges (but keep them in oldNodeIsSubject for now)
			HashMap<Long, TreeSet<Long>> newNodeIsSubject = edges.get(newNode);
			if (newNodeIsSubject == null) { // the new node was not previously a
				// subject of some edges
				edges.put(newNode, oldNodeIsSubject); // we're done
			} else // there were already edges whose subject was the new node
				if (oldNodeIsSubject != null) { // in this case we need to fuse the
					// two maps so that each edge appears only once
					// we will do this by copying those oldNodeIsSubject triples
					// which were not already on the new node, into the properties
					// of the new node
					for (Long oldNodeProperty : oldNodeIsSubject.keySet()) { // iterate
						// over the properties of the old node
						TreeSet<Long> oldNodeObjectsForThisProperty = oldNodeIsSubject.get(oldNodeProperty);
						TreeSet<Long> newNodeObjectsForThisProperty = newNodeIsSubject.get(oldNodeProperty);
						if (newNodeObjectsForThisProperty == null) { // the new node
							// did not have this one
							newNodeObjectsForThisProperty = new TreeSet<Long>();
							newNodeIsSubject.put(oldNodeProperty, newNodeObjectsForThisProperty);
						}
						// whether the new node did or did not have triples labeled
						// oldNodeProperty, try to give him the triples labeled
						// oldNodeProperty of the old node:
						for (Long objectOfOldNode : oldNodeObjectsForThisProperty)
							if (!newNodeObjectsForThisProperty.contains(objectOfOldNode)) {
								newNodeObjectsForThisProperty.add(objectOfOldNode);
							} else{ // already had this property
							} 
					}
				}
		} else { // there was no edge with oldNode as a subject, no subject
			// replacement to do
		}
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
}
