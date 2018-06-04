package fr.inria.cedar.quotientSummary.traversers;

import java.sql.Connection;

public class TypeFirstTwoPassTraverser extends TypeFirstTraverser {
	public TypeFirstTwoPassTraverser(Connection conn) {
		super(conn);
	}
}