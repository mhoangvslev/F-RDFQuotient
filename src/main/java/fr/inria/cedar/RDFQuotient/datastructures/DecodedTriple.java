//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.datastructures;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class DecodedTriple implements Comparable<DecodedTriple> {
	private static final Logger LOGGER = Logger.getLogger(DecodedTriple.class.getName());

	final String s;
	final String p;
	final String o;

	public DecodedTriple(String s, String p, String o) {
		LOGGER.setLevel(Level.INFO);
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
