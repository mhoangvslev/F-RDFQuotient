package fr.inria.cedar.quotientSummary.datastructures;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class Path implements Comparable {

	ArrayList<Triple> triples;
	
	public Path() {
		triples = new ArrayList<Triple>();
	}
	
	public Path(Triple t) {
		triples = new ArrayList<Triple>();
		triples.add(t); 
	}
	public static Path appendOneBefore(Triple t, Path p) {
		Path pNew = new Path();
		pNew.add(t); 
		pNew.triples.addAll(p.triples); 
		return pNew; 
	}

	public static Path appendOneAfter(Triple t, Path p) {
		Path pNew = new Path();
		pNew.triples.addAll(p.triples);
		pNew.add(t); 
		return pNew; 
	}
	void add(Triple t) {
		triples.add(t); 
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer(); 
		sb.append("||"); 
		triples.stream().forEach(t -> sb.append(t.toString())); 
		sb.append("||"); 
		return new String(sb); 
	}

	public Triple getFirstEdge() {
		return triples.get(0); 
	}

	public boolean contains(Triple t) {
		return (triples.indexOf(t)>=0); 
	}
	public boolean equals(Object other){
		Path p2 = (Path)other; 
		for (Triple t: triples) {
			if (!p2.contains(t)){
				return false; 
			}
		}
		for (Triple t: p2.getTriples()) {
			if (!triples.contains(t)) {
				return false; 
			}
		}
		return true; 
	}

	public ArrayList<Triple> getTriples() {
		return triples; 
	}

	public int compareTo(Object o) {
		Path p2 = (Path) o; 
		for (Triple t: triples) {
			if (!p2.contains(t)){
				return -1; 
			}
		}
		for (Triple t: p2.getTriples()) {
			if (!triples.contains(t)) {
				return 1; 
			}
		}
		return 0; 
	}

	public int getLength() {
		return triples.size(); 
	}

	public static Path createFromRS(ResultSet rs, int size) throws SQLException {
		Path path = new Path(); 
		for (int i = 0; i < size; i ++) {
			long s = rs.getLong(i*3+1);
			long p = rs.getLong(i*3+2);
			long o = rs.getLong(i*3+3);
			path.add(new Triple(s, p, o)); 
		}
		return path; 
	}
}
