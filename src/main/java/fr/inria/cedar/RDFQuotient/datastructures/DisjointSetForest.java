//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.datastructures;

import java.util.HashMap;
import java.util.Objects;

public class DisjointSetForest {
	private final HashMap<Long, Tree> forest;
	private static class Tree {
		public Long parent;
		public Long rank;

		public Tree(Long parent, Long rank) {
			this.parent = parent;
			this.rank = rank;
		}
	}

	public DisjointSetForest() {
		forest = new HashMap<>();
	}

	public Long find(Long element) {
		Tree tree = forest.get(element);
		if (tree == null) {
			// make set
			tree = new Tree(null, 0L);
			forest.put(element, tree);
		}
		if (tree.parent == null) {
			return element;
		}

		// path compression
		tree.parent = find(tree.parent);
		return tree.parent;
	}

	public void union(Long element1, Long element2) {
		Long root1 = find(element1);
		Long root2 = find(element2);
		Tree root1Tree = forest.get(root1);
		Tree root2Tree = forest.get(root2);

		// union by rank
		if (root1Tree.rank > root2Tree.rank) {
			root2Tree.parent = root1;
		}
		else if (root1Tree.rank < root2Tree.rank) {
			root1Tree.parent = root2;
		}
		else if (!Objects.equals(root1, root2)) {
			root2Tree.parent = root1; // we could take the other way around, equal ranks mean a tie
			root1Tree.rank = root1Tree.rank + 1L;
		}
	}
}
