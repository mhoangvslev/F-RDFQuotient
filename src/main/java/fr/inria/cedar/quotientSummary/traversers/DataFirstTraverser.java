package fr.inria.cedar.quotientSummary.traversers;

import fr.inria.cedar.quotientSummary.Summary;
import java.sql.Connection;

public class DataFirstTraverser extends Traverser {
	public DataFirstTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}
}