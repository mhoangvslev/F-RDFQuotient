package fr.inria.cedar.quotientSummary.summaries.weak;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLChecksForWeakSummarization {
	Connection conn; 

	public SQLChecksForWeakSummarization(Connection conn) {
		this.conn=conn; 
	}
	
	public void showCommonSources() throws SQLException {
		String findCommonSource = "select distinct d1.value, d2.value from encoded_triples et1, encoded_triples et2, dictionary d1, dictionary d2 where et1.s=et2.s and et1.p=d1.key and et2.p=d2.key and et1.p<et2.p order by d1.value, d2.value;";
		Statement stmt = conn.createStatement();
		System.out.println("=== Properties with common source in the dataset: ");
		ResultSet rs = stmt.executeQuery(findCommonSource);
		while (rs.next()) {
			System.out.println(rs.getString(1) + " - " + rs.getString(2)); 
		}
		rs.close();
		System.out.println("===");
		String findCommonTarget = "select distinct d1.value, d2.value from encoded_triples et1, encoded_triples et2, dictionary d1, dictionary d2 where et1.o=et2.o and et1.p=d1.key and et2.p=d2.key and et1.p<et2.p order by d1.value, d2.value;";
		System.out.println("=== Properties with common target in the dataset: ");
		rs = stmt.executeQuery(findCommonTarget);
		while (rs.next()) {
			System.out.println(rs.getString(1) + " - " + rs.getString(2)); 
		}
		rs.close();
		System.out.println("===");
		String findSourceTarget = "select distinct d1.value, d2.value from encoded_triples et1, encoded_triples et2, dictionary d1, dictionary d2 where et1.o=et2.s and et1.p=d1.key and et2.p=d2.key and et1.p<et2.p order by d1.value, d2.value;";
		System.out.println("=== Properties whose target is the source of another property in the dataset: ");
		rs = stmt.executeQuery(findSourceTarget);
		while (rs.next()) {
			System.out.println(rs.getString(1) + " - " + rs.getString(2)); 
		}
		rs.close();
		System.out.println("===");
	}
	
}
