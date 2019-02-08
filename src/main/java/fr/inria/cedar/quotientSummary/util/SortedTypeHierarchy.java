//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.util;

import java.util.HashMap;
import java.util.HashSet;

public class SortedTypeHierarchy {
	HashMap<Long, HashSet<Long>> sortedNodes; // at level 0, the nodes which are no one's superclasses
	// at level 1, the nodes which are 
	HashMap<Long, HashSet<Long>> edges; // n1 --> n2 for every n1 which is a subclass of n2
}
