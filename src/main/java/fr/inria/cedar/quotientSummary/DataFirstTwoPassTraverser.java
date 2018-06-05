package fr.inria.cedar.quotientSummary;

import java.sql.Connection;

public class DataFirstTwoPassTraverser extends DataFirstTraverser {
	public DataFirstTwoPassTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}
}