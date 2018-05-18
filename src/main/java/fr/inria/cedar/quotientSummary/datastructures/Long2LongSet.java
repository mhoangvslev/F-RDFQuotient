package fr.inria.cedar.quotientSummary.datastructures;

import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Long2LongSet {
	private static final Logger LOGGER = Logger.getLogger(Long2LongSet.class.getName());
	final HashMap<Long, TreeSet<Long>> map;

	public Long2LongSet() {
		LOGGER.setLevel(Level.INFO);
		map = new HashMap<>();
	}

	public TreeSet<Long> get(long node) {
		return map.get(node);
	}

	public void put(long item, long set) {
		TreeSet<Long> theSet = map.get(set);
		if (theSet == null) {
			theSet = new TreeSet<>();
			map.put(set, theSet);
		}
		if (!theSet.contains(item))
			theSet.add(item);
	}

	public void remove(Long node) {
		map.remove(node);
	}

	public void put(long property, TreeSet<Long> item) {
		map.put(property, item);
	}

	// merges the entry of the first param into the entry of the second
	// then removes the entry of the first
	public void fuseKeyInto(Long l1, Long l2) {
		TreeSet<Long> ll1 = map.get(l1);
		TreeSet<Long> ll2 = map.get(l2);
		if ((ll1 != null) && (ll2 != null)) {
			// added all content of l1 into l2
			for (Long i1: ll1)
				if (!ll2.contains(i1))
					ll2.add(i1);
			// the (augmented) ll2 is already the value associated to l2; 
			// value l1 needs to disappear:
			map.remove(l1);
		}
	}

	@Override
	public String toString() {
		//System.out.println("LONG2LONGSET DISPLAY");
		StringBuffer sb = new StringBuffer();
		if (!map.keySet().isEmpty()) {
			for (Long key: map.keySet()) {
				sb.append("#").append(key).append("|{");
				TreeSet<Long> values = map.get(key);
				for (Long val: values){
					String decodedVal = ""; 
					try{
						decodedVal = RDF2SQLEncoding.dictionaryDecode(val);
					}
					catch(Exception e){
						// nothing -- this value was not part of the dictionary
					}
					sb.append(val).append(decodedVal.equals("")?"":(" (" + decodedVal + ")")).append(", ");
				}
				sb.append("} ");
			}
			sb.append("(").append(map.size()).append(" entries)");
		}
		return new String(sb);
	}

	public Set<Long> keys() {
		return map.keySet();
	}

	public void add(Long k, Long v) {
		TreeSet<Long> setFor = map.get(k);
		if (setFor == null) {
			setFor = new TreeSet<>();
			map.put(k, setFor); 
		}
		if (!setFor.contains(v))
			setFor.add(v);
		}
}
