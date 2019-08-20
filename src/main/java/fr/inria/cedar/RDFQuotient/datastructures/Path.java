//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.datastructures;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Path implements Comparable<Path> {
	private static final Logger LOGGER = Logger.getLogger(Path.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	protected final ArrayList<Triple> triples;

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

	public void add(Triple t) {
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
		return (triples.indexOf(t) >= 0);
	}

	/**
	 * This does not appear to be called by HashMap.contains(Path).
	 * Instead, the compare method is called.
	 * @param other
	 */
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Path))
			return false;
		Path p2 = (Path) other;
		//LOGGER.debug("Checking if " + this.toString() + " equals " + other.toString());
		if (triples.size() != p2.triples.size()) {
			//LOGGER.debug("false");
			return false;
		}
		for (int i = 0; i < triples.size(); i++) {
			Triple t = triples.get(i);
			Triple tOther = p2.triples.get(i);
			if (!t.equals(tOther)) {
				//LOGGER.debug("False at " + i);
				return false;
			}
		}
		//LOGGER.debug("true");
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
		//Debbugger.log("Comparing " + this.toString() + " and " + p2.toString());
		if (this.triples.size() < p2.triples.size())
			return -1;
		if (this.triples.size() > p2.triples.size())
			return 1;
		for (int i = 0; i < triples.size(); i++) {
			Triple t = triples.get(i);
			Triple t2 = p2.triples.get(i);
			if (t.s < t2.s)
				return -1;
			else
				if (t.s > t2.s)
					return 1;
				else
					if (t.p < t2.p)
						return -1;
					else
						if (t.p > t2.p)
							return 1;
						else
							if (t.o < t2.o)
								return -1;
							else
								if (t.o > t2.o)
									return 1;
		}
		return 0;
	}

	public int getLength() {
		return triples.size();
	}

	public static Path createFromRS(ResultSet rs, int size) throws SQLException {
		Path path = new Path();
		for (int i = 0; i < size; i++) {
			long s = rs.getLong(i * 3 + 1);
			long p = rs.getLong(i * 3 + 2);
			long o = rs.getLong(i * 3 + 3);
			path.add(new Triple(s, p, o));
		}
		return path;
	}
}
