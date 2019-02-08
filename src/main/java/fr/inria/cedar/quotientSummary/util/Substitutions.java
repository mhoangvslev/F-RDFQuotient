//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * The class computes a set of substitutions given two lists of number pairs.
 * Each number n should be substituted with the smallest number p that appears
 * - in a pair with n, or
 * - in a pair with another number m such that (m should be substituted with p)
 * In practice it appears we never need to compute substitutions from two lists longer than 2, because
 * at a maximum, a data triple leads to two substitutions (one for its source, one for its target).
 *
 * If a number n in one of the lists has no p as above, then there will be no substitution for n.
 *
 * @author ioanamanolescu
 *
 */
public class Substitutions {
	private static final Logger LOGGER = Logger.getLogger(Substitutions.class.getName());
	private final ArrayList<Long> l1;
	private final ArrayList<Long> l2;
	private HashMap<Long, Long> substitutions;

	/**
	 * First and third go into list1
	 * Second and fourth go into list2
	 * If the four values are different:
	 * - The smallest among l1[0] (thus l11) and l2[0] (this l21) will replace the other;
	 * - the smallest among l1[1] (thus l12) and l2[1] (thus l22) will replace the other. 
	 * @param l11
	 * @param l21
	 * @param l12
	 * @param l22
	 */
	public Substitutions(Long l11, Long l21, Long l12, Long l22) {
		LOGGER.setLevel(Level.INFO);
		l1 = new ArrayList<>();
		l1.add(l11);
		l1.add(l12);
		l2 = new ArrayList<>();
		l2.add(l21);
		l2.add(l22);
		//LOGGER.debug("Substitution: first source: " +l11 + " second source: " + l21 + " first target: " + l12 + " second target: " + l22);
		computeSubstitutions();
	}

	/**
	 * 
	 * @param l1 First number list
	 * @param l2 Second number list 
	 */
	public Substitutions(ArrayList<Long> l1, ArrayList<Long> l2) {
		LOGGER.setLevel(Level.INFO);
		this.l1 = l1;
		this.l2 = l2;
		computeSubstitutions();
	}

	/**
	 * Each number goes alone in a list
	 * @param n1
	 * @param n2
	 */
	public Substitutions(Long n1, Long n2) {
		LOGGER.setLevel(Level.INFO);
		//LOGGER.debug("Substitution: first: " + n1 + " second: " + n2);
		l1 = new ArrayList<>();
		l1.add(n1);
		l2 = new ArrayList<>();
		l2.add(n2);
		computeSubstitutions();
	}

	private void computeSubstitutions() {
		substitutions = new HashMap<>();
		for (int i = 0; i < l1.size(); i++) {
			Long n1 = l1.get(i);
			Long n2 = l2.get(i);
			//LOGGER.debug("Comparing " + n1 + " " + n2); // lists are compared PAIRWISE, l1[0] with l2[0], then l1[1] with l2[1]
			// thus if the four values are different
			// * the smallest among l1[0] and l2[0] will replace the other;
			// * the smallest among l1[1] and l2[1] will replace the other. 
			Long n1aux = substitutions.get(n1);
			Long n2aux = substitutions.get(n2);
			if (n1 > n2) { // we should replace n1 by n2,
				//LOGGER.debug("n1 > n2");
				// (or by a  (smaller) substitution of n2, if it exists)
				if (n1aux != null) // in this case we already know the proposed replacement of n1, namely n1aux, and n1aux < n1
					if (n2aux != null) { // in this case we already know the proposed replacement of n2, namely n2aux, and n2aux< n2
						Long smallest = Math.min(n1aux, n2aux);
						if (n1 > smallest)
							substitutions.put(n1, smallest);
						if (n2 > smallest)
							substitutions.put(n2, smallest);
						if (n1aux > smallest)
							substitutions.put(n1aux, smallest);
						if (n2aux > smallest)
							substitutions.put(n2aux, smallest);
					}
					else { // n2aux == null, we compare n2 and n1aux, because we already know n1>n1aux and n1>n2
						Long smallest = Math.min(n1aux, n2);
						if (n1 > smallest)
							substitutions.put(n1, smallest);
						if (n2 > smallest)
							substitutions.put(n2, smallest);
						if (n1aux > smallest)
							substitutions.put(n1aux, smallest);
					}
				else // n1aux is null
					if (n2aux != null) // replace n1 by n2aux, which is sure to be smaller than n2
						substitutions.put(n1, n2aux);
					else // n1aux is null, n2aux is null
						substitutions.put(n1, n2);
			}
			if (n2 > n1) { // we should replace n2 by n1,
				//LOGGER.debug("n2 > n1");
				// except if n2 is already replaced by someone smaller
				// than (n1 or the substitution of n1, if it exists)
				// in which case, n1 should be replaced by the smallest, too
				if (n2aux != null) // in this case n2aux < n2
					if (n1aux != null) { // in this case n1aux < n1
						Long smallest = Math.min(n1aux, n2aux);
						if (n1 > smallest)
							substitutions.put(n1, smallest);
						if (n2 > smallest)
							substitutions.put(n2, smallest);
						if (n1aux > smallest)
							substitutions.put(n1aux, smallest);
						if (n2aux > smallest)
							substitutions.put(n2aux, smallest);
					}
					else { // n1aux == null, we compare n1 and n2aux
						Long smallest = Math.min(n1, n2aux);
						if (n1 > smallest)
							substitutions.put(n1, smallest);
						if (n2 > smallest)
							substitutions.put(n2, smallest);
						if (n2aux > smallest)
							substitutions.put(n2aux, smallest);
					}
				else // n2aux is null
					if (n1aux != null) // replace n2 by n1aux
						substitutions.put(n2, n1aux);
					else // n1aux is null, n2aux is null
						substitutions.put(n2, n1);
			}
		}
		//LOGGER.debug("Now substitutions is: " + this.toString());
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("Substitution created with l1=[");
		for (Long l: l1){
			sb.append(l).append(" ");
		}
		sb.append("] and l2=[");
		for (Long l: l2){
			sb.append(l).append(" ");
		}
		sb.append("] leads to: "); 
		for (Long k: substitutions.keySet())
			sb.append(k).append("->").append(substitutions.get(k)).append(";");
		return new String(sb);
	}

	public Long get(Long n) {
		return substitutions.get(n);
	}

	public Set<Long> getNodesToBeReplaced() {
		return substitutions.keySet();
	}

	public static void main(String[] argv){
		Substitutions s = new Substitutions(1L, 2L, 3L, 5L);
		//LOGGER.debug(s.toString());
		Substitutions s2 = new Substitutions(1L, 3L, 3L, 5L);
		//LOGGER.debug(s2.toString());
		Substitutions s3 = new Substitutions(1L, 3L);
		//LOGGER.debug(s3.toString());
	}
}
