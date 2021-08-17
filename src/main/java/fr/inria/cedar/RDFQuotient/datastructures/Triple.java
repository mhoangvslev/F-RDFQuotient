//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.datastructures;

import java.util.Objects;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Represents an integer-encoded triple
 *
 * @author ioanamanolescu
 *
 */
public class Triple implements Comparable<Triple> {
	private static final Logger LOGGER = Logger.getLogger(Triple.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	public final long s;
	public final long p;
	public final long o;

	public Triple(long s, long p, long o) {
		this.s = s;
		this.o = o;
		this.p = p;
	}

	@Override
	public String toString() {
		return ("<" + s + " " + p + " " + o + ">");
	}

	public void display() {
		System.out.println(this);
	}

	@Override
	public boolean equals(Object other) {
		if (other == null)
			return false;
		if (other == this)
			return true;
		if (!(other instanceof Triple))
			return false;
		Triple t2 = ((Triple) other);
		return (s == t2.s) && (p == t2.p) && (o == t2.o);
	}

	@Override
	public int hashCode() {
		return Objects.hash(s, p, o);
	}

	@Override
	public int compareTo(Triple that) {
		if (that.getClass() == this.getClass()){
			if (this.s < that.s){
				return -1;
			}
			if (this.s > that.s){
				return 1;
			}
			if (this.p < that.p){
				return -1;
			}
			if (this.p > that.p){
				return 1;
			}
			return Long.compare(this.o, that.o);
		}
		else{
			return -1;
		}
	}
}
