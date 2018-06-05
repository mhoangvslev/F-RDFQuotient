package fr.inria.cedar.quotientSummary;

import java.sql.Connection;

public class DataFirstTraverser extends Traverser {
	public DataFirstTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}
}