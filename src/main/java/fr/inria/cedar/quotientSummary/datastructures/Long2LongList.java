package fr.inria.cedar.quotientSummary.datastructures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class Long2LongList {
	// clique ID --> set of properties
	// or: class set ID --> set of types
	final HashMap<Long, ArrayList<Long>> map;
	
	public Long2LongList(){
		map = new HashMap<>();
	}
	
	public ArrayList<Long> get(long node){
		return map.get(new Long(node));
	}
	
	public void put(long property, long clique){
		ArrayList<Long> theClique = map.get(new Long(clique));
		if (theClique == null){
			theClique = new ArrayList<>();
			map.put(clique,  theClique);
		}
		if (theClique.indexOf(property)==-1){
			theClique.add(new Long(property));
		}
	}
	public void remove(Long node){
		map.remove(node);
	}
	public void put(long property, ArrayList<Long> clique){
		map.put(property, clique);
	}

	// merges the entry of the first param into the entry of the second
	// then removes the entry of the first
	public void fuseKeyInto(Long l1, Long l2) {
		ArrayList<Long> ll1 = map.get(l1);
		ArrayList<Long> ll2 = map.get(l2);
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
	
	public void display(){
		StringBuffer sb = new StringBuffer();
		sb.append("==============: \n");
		for (Long key: map.keySet()){
			sb.append("#"+ key + "|{");
			ArrayList<Long> values = map.get(key);
			for (Long val: values){
				sb.append(val + ", ");
			}
			sb.append("} ");
		}
		System.out.println(sb + " (" + map.size() + " entries)");
	}

	public Set<Long> keys() {
		return map.keySet();
	}
}
