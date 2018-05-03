package fr.inria.cedar.quotientSummary.datastructures;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

public class Long2Long {
	// from the node to the ID of its clique
	final HashMap<Long, Long> map;
	// from the ID of a clique, to the list of IDs of all the nodes
	final HashMap<Long, ArrayList<Long>> inverse;

	public Long2Long() {
		map = new HashMap<>();
		inverse = new HashMap<>();
	}

	public Long get(long node) {
		return map.get(node);
	}

	public ArrayList<Long> getInverse(Long l) {
		return inverse.get(l);
	}

	public void put(Long node, Long clique) {
		map.put(node, clique);
		ArrayList<Long> nodesForC = inverse.get(clique);
		if (nodesForC == null) {
			nodesForC = new ArrayList<>();
			inverse.put(clique, nodesForC);
		}
		nodesForC.add(node);
	}

	public String display() {
		StringBuilder sb = new StringBuilder();
		if (!map.keySet().isEmpty()) {
			sb.append("\n----------: \n");
			for (Long key: map.keySet()) {
				sb.append("#").append(key).append("|");
				sb.append(map.get(key)).append(" ");
			}
			sb.append("Inverse:");
			for (Long value: inverse.keySet()) {
				sb.append("*").append(value).append("|");
				for (Long key: inverse.get(value))
					sb.append(key).append(",");
				sb.setLength(sb.length() - 1);
				sb.append("| ");
			}
		}
		return sb.toString();
	}

	/**
	 * value 1, value 2
	 * all keys previously associated to value 1 should now map to value 2
	 * entries previously associated to value 2 stay the same
	 *
	 * @param v1
	 * @param v2
	 */
	public void replaceValue(Long v1, Long v2) {
		Debugger.log("Trying to replace value " + v1 + " with " + v2 + " in:");
		this.display();
		ArrayList<Long> keys1 = inverse.get(v1);
		ArrayList<Long> keys2 = inverse.get(v2);

		if ((keys1 != null) && (keys2 != null))
			for (Long l: keys1) {
				keys2.add(l);
				map.put(l, v2); // this erases (k1, v1)
			}
		if (keys2 != null)
			inverse.put(v2, keys2);
		inverse.remove(v1);
	}

	/** remove the value on this key, and the inverse mapping
	 *
	 * @param key
	 */
	public void remove(Long key) {
		Long value = map.get(key);
		if (value != null) {
			map.remove(key);
			ArrayList<Long> a = inverse.get(value);
			a.remove(key);
			if (a.isEmpty())
				inverse.remove(value);
		}
	}

	public Set<Long> getNodes() {
		return map.keySet();
	}

	public long countDistinctValues() {
		TreeSet<Long> values = new TreeSet<>();
		for (Long key: map.keySet()) {
			Long val = map.get(key);
			if (!values.contains(val))
				values.add(val);
		}
		return values.size();
	}
}
