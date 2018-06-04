package fr.inria.cedar.quotientSummary.traversers;

import java.sql.Connection;

public class DataFirstTraverser extends Traverser {
	public DataFirstTraverser(Connection conn) {
		super(conn);
	}
}