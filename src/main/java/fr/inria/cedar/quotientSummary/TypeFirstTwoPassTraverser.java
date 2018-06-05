package fr.inria.cedar.quotientSummary;

import java.sql.Connection;

public class TypeFirstTwoPassTraverser extends TypeFirstTraverser {
	public TypeFirstTwoPassTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}
}