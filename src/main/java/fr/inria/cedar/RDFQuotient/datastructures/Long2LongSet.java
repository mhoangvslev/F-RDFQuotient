//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.datastructures;

import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Long2LongSet {
	private static final Logger LOGGER = Logger.getLogger(Long2LongSet.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	protected final HashMap<Long, TreeSet<Long>> map;

	public Long2LongSet() {
		map = new HashMap<>();
	}

	public TreeSet<Long> get(long node) {
		return map.get(node);
	}

	public void put(long item, long set) {
		TreeSet<Long> theSet = map.computeIfAbsent(set, k -> new TreeSet<>());
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
			ll2.addAll(ll1);
			// the (augmented) ll2 is already the value associated to l2;
			// value l1 needs to disappear:
			map.remove(l1);
		}
	}

	@Override
	public String toString() {
		//LOGGER.debug("LONG2LONGSET DISPLAY");
		StringBuilder sb = new StringBuilder();
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
		TreeSet<Long> setFor = map.computeIfAbsent(k, k1 -> new TreeSet<>());
		setFor.add(v);
	}
}
