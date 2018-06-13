package fr.inria.cedar.quotientSummary.datastructures;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoLevelLongMap {
	private static final Logger LOGGER = Logger.getLogger(TwoLevelLongMap.class.getName());
	HashMap<Long, HashMap<Long, Long>> map;

	public TwoLevelLongMap(){
		LOGGER.setLevel(Level.INFO);
		map = new HashMap<>();
	}

	// modifies only untyped
	// may lead to temporary inconsistency as the cliques may still be the old ones
	public void replaceAtLowestLevel(Long oldNode, Long newNode) {
		if (oldNode.equals(newNode)) {
			return; 
		}
		for (Long sc: map.keySet()) {
			HashMap<Long, Long> tc2Nodes = map.get(sc);
			for (Long tc: tc2Nodes.keySet()) {
				Long node = tc2Nodes.get(tc);
				if (node.equals(oldNode)) {
					tc2Nodes.put(tc, newNode); 
				}
			}
		}
	}

	public Set<Long> keySet(){
		return map.keySet(); 
	}

	public HashSet<Triple> replaceAt2ndLevel(Long old, Long newX){
		HashSet<Triple> replacements = new HashSet<>();
		for (Long firstLevelKey: map.keySet()) {
			HashMap<Long, Long> onThisKey = map.get(firstLevelKey);
			Long valueOnOld = onThisKey.get(old); 
			Long valueOnNew = onThisKey.get(newX); 
			if (valueOnOld != null) { // the node on (sc, oldTC)
				if (valueOnNew != null) { // the node on (sc, newTC)
					// when this method is called, the new node (valueOnNew) is already correctly associated with its cliques
					// but it may coexist in untypedNodes with "old versions" (clique-node associations from before) 
					// Thus if below we overwrite the old node (valueOnOld) with the new node, that's OK: 
					// onThisKey.put(newX, valueOnNew); // not needed as this was already there
					onThisKey.remove(old);
					replacements.add(new Triple(firstLevelKey, valueOnOld, valueOnNew));
				}
				else { // there is an entry on oldTC but not on newTC. Move the oldTC entry on the new clique. 
					onThisKey.put(newX, valueOnOld);
					onThisKey.remove(old);
				}
			}
			//else {
			//	if there was no node for this sc and oldClique, nothing to do.
			//}
		}
		return replacements;
	}

	public HashSet<Triple> replaceAt1stLevel(Long old, Long newX) {
		HashSet<Triple> replacements = new HashSet<>();
		HashMap<Long, Long> entriesOnOld = map.get(old);
		HashMap<Long, Long> entriesOnNew = map.get(newX);
		if (entriesOnOld != null){ // there is some stuff to replace
			if (entriesOnNew != null){ // there were already entries on the new 
				// try to move entries of the old, onto the new, check for conflicts
				for (Long key2ndLevel: entriesOnOld.keySet()) {
					Long aNodeOnOld = entriesOnOld.get(key2ndLevel); // for sure not null
					Long correspondingNodeOnNew = entriesOnNew.get(key2ndLevel);
					if (correspondingNodeOnNew != null) {
						// when this method is called, the new node (correspondingNodeOnNew) is already correctly associated with its cliques
						// but it may coexist in untypedNodes with "old versions" (clique-node associations from before) 
						// Thus new should overwrite old: 
						// entriesOnNew.put(key2ndLevel, correspondingNodeOnNew); // not needed as this was already there.
						// We don't update entriesOnOld as it will be just replaced (at the end).
						replacements.add(new Triple(key2ndLevel, aNodeOnOld, correspondingNodeOnNew));
					}
					else{ // there was no previous node on newSC and tc; put the one from the oldSC:
						entriesOnNew.put(key2ndLevel, aNodeOnOld);
					}
				}
			}
			// now we can remove entriesOnOld as all its entries have been moved on newX or overwritten
			map.remove(old); 
		}
		//else {
		//	there is nothing on old (old source clique), thus nothing to do.
		//}
		return replacements;
	}

	public Long getIfExists(Long flk, Long slk) {
		if (map.get(flk) == null) {
			return null;
		}
		return map.get(flk).get(slk); 
	}

	public void add(Long flk, Long slk, Long v){
		HashMap<Long, Long> entriesOnFlk = map.get(flk);
		if (entriesOnFlk == null) {
			//LOGGER.debug("No target cliques for source clique " + sourceClique);
			entriesOnFlk = new HashMap<>();
			map.put(flk, entriesOnFlk);
		}
		Long node = entriesOnFlk.get(slk);
		if (node == null) {
			//LOGGER.debug("There was no node for target clique " + targetClique + " among those on source clique " + sourceClique);
			//LOGGER.debug("Created " + this.maxSummaryNode + " for source clique " + targetClique + " " + this.showCliqueAsString(sc.get(sourceClique)));
			//LOGGER.debug(" and target clique " + this.showCliqueAsString(tc.get(targetClique)));
			map.get(flk).put(slk, v);
			//LOGGER.debug("Put in two-level map: " + flk + "->" + slk + "->" + v);
		}
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (Long flk: map.keySet()) {
			sb.append(flk).append("=>{");
			//LOGGER.debug("Source clique: " + sc);
			HashMap<Long, Long> entriesOnFlk = map.get(flk);
			for (Long slk: entriesOnFlk.keySet()) {
				Long v = entriesOnFlk.get(slk);
				sb.append(slk).append(":").append(v).append(" ");
			}
			sb.append("} ");
		}
		return new String(sb);
	}

	public HashMap<Long, Long> get(Long n) {
		return map.get(n); 
	}
}