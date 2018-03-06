package fr.inria.cedar.quotientSummary.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import fr.inria.cedar.commons.miscellaneous.Debugger;

public class Long2Long{
	// from the node to the ID of its clique
	HashMap<Long, Long> map;
	// from the ID of a clique, to the list of IDs of all the nodes
	HashMap<Long, ArrayList<Long>> inverse;
	
	public Long2Long(){
		map = new HashMap<Long, Long>();
		inverse = new HashMap<Long, ArrayList<Long>>();
	}
	
	public Long get(long node){
		return map.get(new Long(node));
	}
	
	public ArrayList<Long> getInverse(Long l){
		return inverse.get(l);
	}
	
	public void put(Long node, Long clique){
		map.put(node, clique);
		ArrayList<Long> nodesForC = inverse.get(clique);
		if (nodesForC == null){
			nodesForC = new ArrayList<Long>();
			inverse.put(clique,  nodesForC);
		}
		nodesForC.add(node);
	}

	public void display() {
		StringBuffer sb = new StringBuffer();
		sb.append("----------: \n");
		for (Long key: map.keySet()){
			sb.append("#"+ key + "|");
			sb.append(map.get(key) + " "); 
		}
		sb.append("Inverse:");
		for (Long value: inverse.keySet()){
			sb.append("*" + value + "|");
			for (Long key: inverse.get(value)){
				sb.append(key+",");
			}
			sb.setLength(sb.length()-1);
			sb.append("| "); 
		}
		Debugger.log(sb.toString());
	}

	/**
	 * value 1, value 2
	 * all keys previously associated to value 1 should now map to value 2
	 * entries previously associated to value 2 stay the same 
	 * @param v1
	 * @param v2
	 */
	public void replaceValue(Long v1, Long v2) {
		Debugger.log("Trying to replace value " + v1 + " with "+ v2 + " in:");
		this.display();
		ArrayList<Long> keys1 = inverse.get(v1);
		ArrayList<Long> keys2 = inverse.get(v2);
		
		if ((keys1 != null) && (keys2 != null)){
			for (Long l: keys1){
				keys2.add(l);
				map.put(l,  v2); // this erases (k1, v1)
			}
		}
		if (keys2 != null){
			inverse.put(v2, keys2);
		}
		inverse.remove(v1); 
	}

	/** remove the value on this key, and the inverse mapping
	 * 
	 * @param key
	 */
	public void remove(Long key) {
		Long value = map.get(key);
		if (value != null){
			map.remove(key);
			ArrayList<Long> a = inverse.get(value);
			a.remove(key);
			if (a.size() == 0){
				inverse.remove(value);
			}
		}
	}

	public Set<Long> getNodes() {
		return map.keySet();
	}

	public long countDistinctValues() {
		TreeSet<Long> values = new TreeSet<Long>(); 
		for (Long key: map.keySet()){
			Long val = map.get(key); 
			if (!values.contains(val)){
				values.add(val); 
			}
		}
		return values.size(); 
	}
}
