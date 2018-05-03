package fr.inria.cedar.quotientSummary.datastructures;

public class DecodedTriple implements Comparable<DecodedTriple> {
	final String s;
	final String p;
	final String o;

	public DecodedTriple(String s, String p, String o) {
		this.s = s;
		this.p = p;
		this.o = o;
	}

	@Override
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
	public int compareTo(DecodedTriple other) {
		int k = this.s.compareTo(other.getSource());
		if (k != 0)
			return k;
		k = this.p.compareTo(other.getProperty());
		if (k != 0)
			return k;
		k = this.o.compareTo(other.getObject());
		if (k != 0)
			return k;
		return 0;
	}
}
