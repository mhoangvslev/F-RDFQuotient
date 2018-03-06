package fr.inria.cedar.quotientSummary.datastructures;

/**
 * Represents an integer-encoded triple
 * 
 * @author ioanamanolescu
 *
 */
public class Triple {
	public long s; 
	public long p; 
	public long o;
	
	public Triple(long s, long p, long o){
		this.s=s; 
		this.o=o;
		this.p=p;
	}

	public String toString(){
		return ("<" + s + " " + p + " " + o + ">"); 
	}
	public void display() {
		System.out.println(toString()); 
	}
	
	public boolean equals(Object other){
		Triple t2 = ((Triple)other);
		if (s == t2.s && p == t2.p && this.o == t2.o){
			return true; 
		}
		return false; 
	}
}
