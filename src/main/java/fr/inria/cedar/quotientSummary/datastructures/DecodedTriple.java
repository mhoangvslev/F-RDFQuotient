package fr.inria.cedar.quotientSummary.datastructures;

public class DecodedTriple implements Comparable {
	String s; 
	String p; 
	String o; 

	public DecodedTriple(String s, String p, String o) {
		this.s = s; 
		this.p = p; 
		this.o = o; 
	}

	public String toString() {
		return (s + " " + p + " " + o); 
	}

	public String getSource() {
		return s; 
	}
	public String getProperty() {
		return p; 
	}
	public String getObject() {
		return o; 
	}
	@Override
	public int compareTo(Object o) {
		try {
			DecodedTriple other = (DecodedTriple) o; 
			int k = this.s.compareTo(other.getSource()); 
			if (k!= 0) {
				return k; 
			}
			k = this.p.compareTo(other.getProperty()); 
			if (k!= 0) {
				return k; 
			}
			k = this.o.compareTo(other.getObject()); 
			if (k!= 0) {
				return k; 
			}
			return 0;
		}
		catch(ClassCastException e) {
			throw new Error("Should not compare " + this.getClass().getName() + " with " + o.getClass().getName()); 
		}
	}
}