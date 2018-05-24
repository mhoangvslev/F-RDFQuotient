package fr.inria.cedar.quotientSummary.datastructures;

import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Long2LongList {
	private static final Logger LOGGER = Logger.getLogger(Long2LongList.class.getName());
	// clique ID --> set of properties
	// or: class set ID --> set of types
	final HashMap<Long, ArrayList<Long>> map;

	public Long2LongList() {
		LOGGER.setLevel(Level.INFO);
		map = new HashMap<>();
	}

	public ArrayList<Long> get(long node) {
		return map.get(node);
	}

	public void put(long property, long clique) {
		ArrayList<Long> theClique = map.get(clique);
		if (theClique == null) {
			theClique = new ArrayList<>();
			map.put(clique, theClique);
		}
		if (theClique.indexOf(property) == -1)
			theClique.add(property);
	}

	public void remove(Long node) {
		map.remove(node);
	}

	public void put(long property, ArrayList<Long> clique) {
		map.put(property, clique);
	}

	// merges the entry of the first param into the entry of the second
	// then removes the entry of the first
	public void fuseKeyInto(Long l1, Long l2) {
		ArrayList<Long> ll1 = map.get(l1);
		ArrayList<Long> ll2 = map.get(l2);
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

	public String display() {
		StringBuffer sb = new StringBuffer();
		if (map.size() > 0) {
			sb.append("\n==============: \n");
			for (Long key: map.keySet()) {
				sb.append("#").append(key).append("|{");
				ArrayList<Long> values = map.get(key);
				for (Long val: values) {
					//sb.append(val).append(", ");
					sb.append(val).append(" (").append(RDF2SQLEncoding.dictionaryDecode(val)).append(")"); 
				}
				sb.append("} ");
			}
		}
		return new String(sb);
	}

	public Set<Long> keys() {
		return map.keySet();
	}
}
