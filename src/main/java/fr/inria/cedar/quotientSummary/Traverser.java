package fr.inria.cedar.quotientSummary;

import java.sql.Connection;

public abstract class Traverser {
	private final Summary summ;
	private final Connection conn;

	public Traverser(Summary summ, Connection conn) {
		this.summ = summ;
		this.conn = conn;
	}

	private void schemaNodesDetection() {
		summ.collectSchemaNodes(conn);
	}

	private void dataPass() {
	}

	private void secondDataPass() {
	}

	private void typePass() {
	}

	public void traverseAllTriples() {
	}
}
