package fr.inria.cedar.quotientSummary.datastructures;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

public class Path implements Comparable<Path> {

	final ArrayList<Triple> triples;
	
	public Path() {
		triples = new ArrayList<>();
	}
	
	public Path(Triple t) {
		triples = new ArrayList<>();
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
	
	@Override
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
	
	@Override
	public boolean equals(Object other){
		if (!(other instanceof Path)) {
			return false;
		}
		Path p2 = (Path) other; 
		if (triples.size() != p2.triples.size()) {
			return false; 
		}
		for (int i = 0; i < triples.size(); i ++) {
			Triple t = triples.get(i);
			Triple tOther = p2.triples.get(i);
			if (!t.equals(tOther)) {
				return false; 
			}
		}
		return true; 
	}

	@Override
	public int hashCode() {
		return Objects.hash(triples);
	}

	public ArrayList<Triple> getTriples() {
		return triples; 
	}

	@Override
	public int compareTo(Path p2) {
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
