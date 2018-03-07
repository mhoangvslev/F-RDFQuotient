package fr.inria.cedar.quotientSummary.datastructures;

import java.util.Objects;

/**
 * Represents an integer-encoded triple
 * 
 * @author ioanamanolescu
 *
 */
public class Triple {
	public final long s; 
	public final long p; 
	public final long o;
	
	public Triple(long s, long p, long o){
		this.s = s; 
		this.o = o;
		this.p = p;
	}

	@Override
	public String toString(){
		return ("<" + s + " " + p + " " + o + ">"); 
	}

	public void display() {
		System.out.println(toString()); 
	}
	
	@Override
	public boolean equals(Object other){
		Triple t2 = ((Triple)other);
		if (s == t2.s && p == t2.p && this.o == t2.o){
			return true; 
		}
		return false; 
	}

	@Override
	public int hashCode() {
		return Objects.hash(s, p, o);
	}
}
