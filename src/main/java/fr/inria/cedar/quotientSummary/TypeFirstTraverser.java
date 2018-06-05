package fr.inria.cedar.quotientSummary;

import java.sql.Connection;

public class TypeFirstTraverser extends Traverser {
	public TypeFirstTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}
}