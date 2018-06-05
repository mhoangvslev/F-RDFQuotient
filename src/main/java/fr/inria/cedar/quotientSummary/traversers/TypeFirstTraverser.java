package fr.inria.cedar.quotientSummary.traversers;

import fr.inria.cedar.quotientSummary.Summary;
import java.sql.Connection;

public class TypeFirstTraverser extends Traverser {
	public TypeFirstTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}
}