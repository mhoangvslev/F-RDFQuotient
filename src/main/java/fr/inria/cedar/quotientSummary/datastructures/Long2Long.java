package fr.inria.cedar.quotientSummary.datastructures;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Long2Long {
	private static final Logger LOGGER = Logger.getLogger(Long2Long.class.getName());
	// from the node to the ID of its clique
	final HashMap<Long, Long> map;
	// from the ID of a clique, to the list of IDs of all the nodes
	final HashMap<Long, TreeSet<Long>> inverse;

	public Long2Long() {
		LOGGER.setLevel(Level.INFO);
		map = new HashMap<>();
		inverse = new HashMap<>();
	}

	public Long get(long node) {
		return map.get(node);
	}

	public TreeSet<Long> getInverse(Long l) {
		return inverse.get(l);
	}

	/**
	 * Returns true if node had a previous representative who now becomes a 
	 * representative of no one.
	 * @param k
	 * @param v
	 * @return
	 */
	public boolean put(Long k, Long v) {
		//LOGGER.debug("Long2Long: upon entering put " + clique + " on " + node + ": " + this.display()); 
		boolean res = false;

		Long previous = map.get(k);

		if (previous != null){
			TreeSet<Long> inversePrev = inverse.get(previous);
			if (inversePrev == null){
				//LOGGER.debug("Long2Long: Problem " + this.display());
				throw new IllegalStateException("Map has " + previous + " on " + k + " but nothing in inverse for " + previous); 
			}
			inversePrev.remove(k);
			//LOGGER.debug("Long2Long: " + k + " no  longer mapped to " + previous);
			if (inversePrev.isEmpty()){
				//LOGGER.debug("Long2Long: No one is represented by " + previous + " any more!");
				res = true;
				inverse.remove(previous); 
			}
		}
		map.put(k, v);

		TreeSet<Long> keysForV = inverse.get(v);
		if (keysForV == null) {
			keysForV = new TreeSet<>();
			inverse.put(v, keysForV);
		}
		if (!keysForV.contains(k)){
			keysForV.add(k);
		}
		return res; 
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (!map.keySet().isEmpty()) {
			sb.append("\n----------: \n");
			for (Long key: map.keySet()) {
				sb.append("#").append(key).append("|");
				//sb.append(RDF2SQLEncoding.dictionaryDecode(key) + "|");
				sb.append(map.get(key)).append(" ");
			}
			sb.append("Inverse:");
			for (Long value: inverse.keySet()) {
				sb.append("*").append(value).append("|");
				for (Long key: inverse.get(value))
					sb.append(key).append(",");
					//sb.append(RDF2SQLEncoding.dictionaryDecode(key) + ",");
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
		//LOGGER.debug("Trying to replace value " + v1 + " with " + v2 + " in:");
		TreeSet<Long> keysWithV1 = inverse.get(v1);
		if (keysWithV1 != null){
			TreeSet<Long> keysWithV2 = inverse.get(v2);
			if (keysWithV2 == null){
				keysWithV2 = new TreeSet<>();
				inverse.put(v2, keysWithV2); 
			}
			for (Long l: keysWithV1) {
				keysWithV2.add(l);
				map.put(l, v2); // this erases (l, v1)
			}
		}
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
			TreeSet<Long> a = inverse.get(value);
			a.remove(key);
			if (a.isEmpty())
				inverse.remove(value);
		}
	}

	public Set<Long> getKeys() {
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
