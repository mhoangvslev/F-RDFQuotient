package fr.inria.cedar.quotientSummary.traversers;

import java.sql.Connection;

public class DataFirstTwoPassTraverser extends DataFirstTraverser {
	public DataFirstTwoPassTraverser(Connection conn) {
		super(conn);
	}
}