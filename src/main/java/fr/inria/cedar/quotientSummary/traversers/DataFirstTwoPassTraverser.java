package fr.inria.cedar.quotientSummary.traversers;

import fr.inria.cedar.quotientSummary.Summary;
import java.sql.Connection;

public class DataFirstTwoPassTraverser extends DataFirstTraverser {
	public DataFirstTwoPassTraverser(Summary summ, Connection conn) {
		super(summ, conn);
	}
}