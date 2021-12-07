//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.analysis;

import fr.inria.cedar.RDFQuotient.controller.Interface;
import fr.inria.cedar.RDFQuotient.controller.LoadingProperties;
import fr.inria.cedar.RDFQuotient.util.PostgresIdentifier;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Accuracy {
	private static final Logger LOGGER = Logger.getLogger(Accuracy.class.getName());

	protected static Connection connection;

	static {
		LOGGER.setLevel(Level.INFO);
	}

	static protected String createSummaryPatternsTable(String summaryEdgesTableName) {
		String summaryPatternsTableName = summaryEdgesTableName.replace("_edges", "_patterns");

		try {
			PreparedStatement stmtDropSummaryPatternsTable = connection.prepareStatement("DROP TABLE IF EXISTS " + summaryPatternsTableName);
			stmtDropSummaryPatternsTable.executeUpdate();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		Statement stmt;
		try {
			stmt = connection.createStatement();
			stmt.execute("CREATE TABLE "
						 + PostgresIdentifier.escapedQuotedId(summaryPatternsTableName)
						 + "AS (" +
						 "SELECT DISTINCT p1, p2, connection_type " +
						 "FROM (" +
							"SELECT e1.p AS p1, e2.p AS p2, 'os' AS connection_type " +
							"FROM " + summaryEdgesTableName + " e1 " +
							"JOIN " + summaryEdgesTableName + " e2 ON e1.o = e2.s " +
								"UNION " +
							"SELECT e1.p AS p1, e2.p AS p2, 'oo' AS connection_type " +
							"FROM " + summaryEdgesTableName + " e1 " +
							"JOIN " + summaryEdgesTableName + " e2 ON e1.o = e2.o " +
								"UNION " +
							"SELECT e1.p AS p1, e2.p AS p2, 'ss' AS connection_type " +
							"FROM " + summaryEdgesTableName + " e1 " +
							"JOIN " + summaryEdgesTableName + " e2 ON e1.s = e2.s " +
							") AS subquery)");
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		return summaryPatternsTableName;
	}

	static protected ResultSet getSummaryPatterns(String summaryPatternsTableName) {
		try {
			Statement stmt = connection.createStatement();
			return stmt.executeQuery("SELECT * FROM " + summaryPatternsTableName);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		return null;
	}

	// boolean query
	static protected boolean summaryPatternExistsInInputGraphPatterns(
		String encodedTriplesTableName,
		String summaryPatternP1,
		String summaryPatternP2,
		String summaryPatternConnectionType
	) {
		try {
			Statement stmt = connection.createStatement();
			String query = null;
			switch (summaryPatternConnectionType) {
				case "os":
					query = "SELECT * " +
							"FROM " + encodedTriplesTableName + " e1 " +
							"JOIN " + encodedTriplesTableName + " e2 ON e1.o = e2.s " +
							"WHERE e1.p = " + summaryPatternP1 +
							" AND e2.p = " + summaryPatternP2 +
							" LIMIT 1";
					break;
				case "oo":
					query = "SELECT * " +
							"FROM " + encodedTriplesTableName + " e1 " +
							"JOIN " + encodedTriplesTableName + " e2 ON e1.o = e2.o " +
							"WHERE e1.p = " + summaryPatternP1 +
							" AND e2.p = " + summaryPatternP2 +
							" LIMIT 1";
					break;
				case "ss":
					query = "SELECT * " +
							"FROM " + encodedTriplesTableName + " e1 " +
							"JOIN " + encodedTriplesTableName + " e2 ON e1.s = e2.s " +
							"WHERE e1.p = " + summaryPatternP1 +
							" AND e2.p = " + summaryPatternP2 +
							" LIMIT 1";
					break;
			}

			ResultSet rs = stmt.executeQuery(query);

			return rs.next();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		return false;
	}

	static public void main(String[] args) {
		String databaseName = args[0];
		String encodedTriplesTableName = args[1];
		String summaryEdgesTableName;
		String summaryPatternsTableName;
		ResultSet summaryPatterns;
		String summaryPatternP1;
		String summaryPatternP2;
		String summaryPatternConnectionType;
		long numberOfSummaryPatterns;
		long numberOfTruePositives;

		Properties properties = LoadingProperties.getDefaultProperties();
		properties.put("database.name", databaseName);
		connection = Interface.getOrEstablishNewDatabaseConnection(properties);

		System.out.println("dataset" + "," + "summary_edges" + "," + "AL_2_accuracy");
		for (int i = 2; i < args.length; i++) {
			numberOfSummaryPatterns = 0;
			numberOfTruePositives = 0;
			summaryEdgesTableName = args[i];
			summaryPatternsTableName = createSummaryPatternsTable(summaryEdgesTableName);
			summaryPatterns = getSummaryPatterns(summaryPatternsTableName);
			try {
				while (summaryPatterns.next()) {
					summaryPatternP1 = summaryPatterns.getString("p1");
					summaryPatternP2 = summaryPatterns.getString("p2");
					summaryPatternConnectionType = summaryPatterns.getString("connection_type");
					if (summaryPatternExistsInInputGraphPatterns(encodedTriplesTableName, summaryPatternP1, summaryPatternP2, summaryPatternConnectionType)) {
						numberOfTruePositives++;
					}
					numberOfSummaryPatterns++;
				}
			}
			catch (SQLException ex) {
				LOGGER.error(ex);
				System.exit(1);
			}

			System.out.println(databaseName + "," + summaryEdgesTableName + "," + (double) numberOfTruePositives / (double) numberOfSummaryPatterns);
		}

		Interface.closeDatabaseConnection();
	}

	public Accuracy() {
	}
}
