package fr.inria.cedar.quotientSummary.datastructures;

import java.util.Objects;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * Represents an integer-encoded triple
 *
 * @author ioanamanolescu
 *
 */
public class Triple implements Comparable {
	private static final Logger LOGGER = Logger.getLogger(Triple.class.getName());

	public final long s;
	public final long p;
	public final long o;

	public Triple(long s, long p, long o) {
		LOGGER.setLevel(Level.INFO);
		this.s = s;
		this.o = o;
		this.p = p;
	}

	@Override
	public String toString() {
		return ("<" + s + " " + p + " " + o + ">");
	}

	public void display() {
		System.out.println(toString());
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
	public int compareTo(Object o) {
		if (o.getClass() == this.getClass()){
			Triple ot = (Triple)o; 
			if (this.s < ot.s){
				return -1; 
			}
			if (this.s > ot.s){
				return 1; 
			}
			if (this.p < ot.p){
				return -1; 
			}
			if (this.p > ot.p){
				return 1; 
			}
			if (this.o < ot.o){
				return -1; 
			}
			if (this.o > ot.o){
				return 1; 
			}
			return 0; 
		}
		else{
			return -1; 
		}
	}
}
