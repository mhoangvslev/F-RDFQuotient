package fr.inria.cedar.quotientSummary.datastructures;

import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

public class Long2LongSet {
	final HashMap<Long, TreeSet<Long>> map;

	public Long2LongSet(){
		map = new HashMap<>();
	}

	public TreeSet<Long> get(long node){
		return map.get(new Long(node));
	}

	public void put(long item, long set){
		TreeSet<Long> theSet = map.get(new Long(set));
		if (theSet == null){
			theSet = new TreeSet<>();
			map.put(set,  theSet);
		}
		if (!theSet.contains(item)){
			theSet.add(new Long(item));
		}
	}
	public void remove(Long node){
		map.remove(node);
	}
	public void put(long property, TreeSet<Long> item){
		map.put(property, item);
	}

	// merges the entry of the first param into the entry of the second
	// then removes the entry of the first
	public void fuseKeyInto(Long l1, Long l2) {
		TreeSet<Long> ll1 = map.get(l1);
		TreeSet<Long> ll2 = map.get(l2);
		if ((ll1 != null) && (ll2 != null)){
			// added all content of l1 into l2
			for (Long i1: ll1){
				if (!ll2.contains(i1)){
					ll2.add(i1);	
				}
			}
			// the (augmented) ll2 is already the value associated to l2; 
			// value l1 needs to disappear:
			map.remove(l1);
		}
	}

	public String display(){
		StringBuffer sb = new StringBuffer();
		if (!map.keySet().isEmpty()) {
			sb.append("\n==============: \n");
			for (Long key: map.keySet()){
				sb.append("#"+ key + "|{");
				TreeSet<Long> values = map.get(key);
				for (Long val: values){
					sb.append(val + ", ");
				}
				sb.append("} "+ " (" + map.size() + " entries)");
			}
		}	
		return new String(sb); 
	}

	public Set<Long> keys() {
		return map.keySet();
	}

	public void add(long o, long newClassSetID) {
		TreeSet<Long> setFor = map.get(o);
		if (setFor == null) {
			setFor = new TreeSet<Long>();
			setFor.add(newClassSetID);
		}
		else {
			if (!setFor.contains(newClassSetID)) {
				setFor.add(newClassSetID); 
			}
		}
	}
}
