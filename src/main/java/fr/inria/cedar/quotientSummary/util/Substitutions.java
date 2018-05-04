package fr.inria.cedar.quotientSummary.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * The class computes a set of substitutions given two lists of number pairs.
 * Each number n should be substituted with the smallest number p that appears
 * - in a pair with n, or
 * - in a pair with another number m such that (p should be substituted with n)
 * In practice it appears we never need to compute substitutions from two lists longer than 2, because
 * at a maximum, inspecting a data triple leads to two substitutions (one for its source, one for its target).
 *
 * If a number n in one of the lists has no p as above, then there will be no substitution for n.
 *
 * @author ioanamanolescu
 *
 */
public class Substitutions {
	ArrayList<Long> l1;
	ArrayList<Long> l2;
	HashMap<Long, Long> substitutions;

	public Substitutions(Long l11, Long l21, Long l12, Long l22) {
		l1 = new ArrayList<>();
		l1.add(l11);
		l1.add(l12);
		l2 = new ArrayList<>();
		l2.add(l21);
		l2.add(l22);
		computeSubstitutions();
	}

	public Substitutions(ArrayList<Long> l1, ArrayList<Long> l2) {
		this.l1 = l1;
		this.l2 = l2;
		computeSubstitutions();
	}

	public Substitutions(Long n1, Long n2) {
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
			System.out.println("Comparing " + n1 + " " + n2);
			Long n1aux = substitutions.get(n1);
			Long n2aux = substitutions.get(n2);
			if (n1 > n2) // we should replace n1 by n2,
				//System.out.println("n1>n2");
				// except if n1 is already replaced by someone smaller
				// than (n2 or the substitution of n2, if it exists)
				// in which case, n2 should be replaced by the smallest, too
				if (n1aux != null) // in this case n1aux < n1
					if (n2aux != null) { // in this case n2aux< n2
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
					else { // n2aux == null, we compare n2 and n1aux
						Long smallest = Math.min(n1aux, n2);
						if (n1 > smallest)
							substitutions.put(n1, smallest);
						if (n2 > smallest)
							substitutions.put(n2, smallest);
						if (n1aux > smallest)
							substitutions.put(n1aux, smallest);
					}
				else // n1aux is null
					if (n2aux != null) // replace n1 by n2aux
						substitutions.put(n1, n2aux);
					else // n1aux is null, n2aux is null
						substitutions.put(n1, n2);
			if (n2 > n1) // we should replace n2 by n1,
				//System.out.println("n2 > n1");
				// except if n2 is already replaced by someone smaller
				// than (n1 or the substitution of n1, if it exists)
				// in which case, n1 should be replaced by the smallest, too
				if (n2aux != null) // in this case n2aux < n2
					if (n1aux != null) { // in this case n1aux< n1
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
		//System.out.println("Now substitutions is: " + this.toString());
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
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
}
