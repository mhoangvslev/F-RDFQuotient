package fr.inria.cedar.quotientSummary.traversers;

import fr.inria.cedar.quotientSummary.Summary;
import java.sql.Connection;

public class TypeFirstTwoPassTraverser extends TypeFirstTraverser {
	public TypeFirstTwoPassTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}
}