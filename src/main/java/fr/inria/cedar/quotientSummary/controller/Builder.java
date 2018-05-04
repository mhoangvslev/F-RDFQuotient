package fr.inria.cedar.quotientSummary.controller;

import com.google.common.base.Preconditions;
import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.ontosql.rdfdb.dataloading.DataLoading;
import fr.inria.cedar.ontosql.rdfdb.dataloading.Parameters;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.strong.StrongSummary;
import fr.inria.cedar.quotientSummary.strong.TypedStrongSummary;
import fr.inria.cedar.quotientSummary.weak.TypedWeakSummary;
import fr.inria.cedar.quotientSummary.weak.WeakSummary;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Builder {
	// Default properties file
	private static final String DEFAULT_CONFIG_FILE = System.getProperty("user.dir") + "/conf/dataLoading.properties";

	public Builder() {
		try {
			getConnection();
		}
		catch (UnsupportedDatabaseEngineException | IOException | SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 * @param args
	 *   args[0] determines what will be done:
	 *     load: load the data in Postgres
	 *     summarize: summarize the data from Postgres
	 *     loadSummarize: load the data in Postgres and summarize it from there
	 *     summarizeEncodedFile: build the summary out of integer-encoded triples in a file.
	 *
	 * @throws IOException
	 * @throws SQLException
	 * @throws UnsupportedDatabaseEngineException
	 */
	public static void main(String[] args) throws IOException, UnsupportedDatabaseEngineException, SQLException {
		if (args.length == 0) {
			printUsage();
			return;
		}
		String[] nextArguments = extractArguments(args);
		switch (args[0].toLowerCase()) {
			case "load":
				try (Connection conn = loadRDFInPostgres(nextArguments)) {
				}
				return;
			case "summarize":
				try (Connection conn = getConnection()) {
					summarizeGraphFromPostgres(conn, nextArguments);
				}
				return;
			case "loadsummarize":
				// in this case args[1] is the summary type; the loader doesn't need this information
				String[] filesToLoad = extractArguments(nextArguments);
				// the loader only gets the files to load
				try (Connection conn = loadRDFInPostgres(filesToLoad)) {
					// the summarizer also gets the summary name
					summarizeGraphFromPostgres(conn, nextArguments);
				}
				return;
			default:
				break;
		}
		printUsage();
	}

	/**
	 * This returns the connection to the Postgres database where the dictionary-encoded RDF graph is / will be stored.
	 *
	 * @return the connection
	 *
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws UnsupportedDatabaseEngineException
	 * @throws SQLException
	 */
	// connection balance: +1
	private static Connection getConnection() throws FileNotFoundException, IOException, UnsupportedDatabaseEngineException, SQLException {
		// first fill in the connection properties from the default config file
		Properties properties = new Properties();
		properties.load(new FileReader(DEFAULT_CONFIG_FILE));
		System.out.println(properties.toString());

		// then add the specific properties of this loader
		Properties connectionProps = new Properties();
		connectionProps.put("user", properties.getProperty("database.user"));
		connectionProps.put("password", properties.getProperty("database.password"));

		String connectionURL = "jdbc:postgresql://" + properties.getProperty("database.host")
							   + ":" + properties.getProperty("database.port") + "/" + properties.getProperty("database.name");
		Connection conn = DriverManager.getConnection(connectionURL, connectionProps);
		System.out.println("Connection URL is: " + connectionURL);
		Preconditions.checkState(conn != null, "No connection for " + connectionURL);
		return conn;
	}

	private static void printUsage() {
		System.out.println("Usage:");
		System.out.println("args[0]=load: loads the data in Postgres");
		System.out.println("args[0]=summarize: summarize the data from Postgres");
		System.out.println("args[0]=loadSummarize: load the data in Postgres and summarize it from there");
		System.out.println("args[0]=summarizeEncodedFile: build the summary out of integer-encoded triples in a file");
	}

	/**
	 * Small helper function to extract all but the first argument
	 *
	 * @param args
	 *
	 * @return
	 */
	private static String[] extractArguments(String[] args) {
		String[] suffix = new String[args.length - 1];
		for (int i = 0; i < suffix.length; i++)
			suffix[i] = args[i + 1];
		return suffix;
	}

	/** This method loads data in Postgres through the ontoSQL loader.
	 *
	 * @param args a list of file names
	 *
	 * @return
	 *
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws UnsupportedDatabaseEngineException
	 * @throws SQLException
	 *   Convention:
	 *     if there are at least two files
	 *     then the first file contains the data and the last contains the schema
	 *     otherwise (only one file) that file contains everything (data and schema)
	 */
	// connection balance: +1
	public static Connection loadRDFInPostgres(String[] args) throws FileNotFoundException, IOException, UnsupportedDatabaseEngineException, SQLException {
		System.out.println(System.getProperty("user.dir"));

		List<String> tripleFiles = new ArrayList<>();
		List<String> rdfsFiles = new ArrayList<>();

		int fileNo;
		for (fileNo = 0; fileNo < args.length; fileNo++) {
			String s = args[fileNo];
			if ((fileNo == 0) || ((args.length > 1) && (fileNo < args.length - 1))) {
				System.out.println("Triple file: " + s);
				tripleFiles.add(s);
			}
			else {
				System.out.println("Schema file: " + s);
				rdfsFiles.add(s);
			}
		}

		Properties properties = new Properties();
		properties.load(new FileReader(DEFAULT_CONFIG_FILE));
		System.out.println(properties.toString());
		Parameters settings = new Parameters();
		settings.setPropertiesFileName(DEFAULT_CONFIG_FILE);

		if (rdfsFiles.isEmpty())
			for (String tripleFile: tripleFiles) {
				settings.getAllInFiles().add(tripleFile);
				DataLoading.process(settings);
			}
		else
			for (String tripleFile: tripleFiles) {
				settings.getTripleFiles().add(tripleFile);
				settings.setRdfsFile(rdfsFiles.get(0));
				DataLoading.process(settings);
			}
		System.out.println("Loading finished");

		Properties connectionProps = new Properties();
		connectionProps.put("user", properties.getProperty("database.user"));
		connectionProps.put("password", properties.getProperty("database.password"));

		String connectionURL = "jdbc:postgresql://" + properties.getProperty("database.host")
							   + ":" + properties.getProperty("database.port") + "/" + properties.getProperty("database.name");
		Connection conn = DriverManager.getConnection(connectionURL, connectionProps);
		System.out.println("Connection URL is: " + connectionURL);
		Preconditions.checkState(conn != null, "No connection for " + connectionURL);
		return conn;
	}

	// Connection balance: +1
	public static Connection loadSingleRDFInPostgres(String fileName) {
		String[] files = {fileName};
		try {
			return loadRDFInPostgres(files);
		}
		catch (UnsupportedDatabaseEngineException | IOException | SQLException e) {
			throw new IllegalStateException("Unable to load RDF from " + fileName + " " + e.toString());
		}
	}

	/**
	 * Supposes the graph has already been loaded
	 *
	 * @param conn
	 * @param args
	 *
	 * @throws IOException
	 * @throws SQLException
	 */
	// connection balance: 0
	public static void summarizeGraphFromPostgres(Connection conn, String[] args) throws SQLException, IOException {
		Summary sum = createNewSummary(args[0]);
		Debugger.turnOff();
		sum.summarizeFromRDBMS(conn, extractArguments(args));
		System.out.println("RDF graph summarized.");
		sum.saveSummaryInPostgres(conn, args[1]);
		sum.writeDecodedSummaryToNTFile(conn, args[1]);
		sum.drawSummaryAndGraph(conn, args[1]);
		System.out.println(sum.getRunStatistics().toString());
	}

	private static Summary createNewSummary(String summaryType) {
		String lowerCaseSummaryType = summaryType.toLowerCase();
		switch (lowerCaseSummaryType) {
			case "weak":
				return new WeakSummary();
			case "strong":
				return new StrongSummary();
			case "typedweak":
				return new TypedWeakSummary();
			case "typedstrong":
				return new TypedStrongSummary();
		}
		return null;
	}

	private static Summary readSummaryFromPostgres(String summaryType, Connection conn) {
		String lowerCaseSummaryType = summaryType.toLowerCase();
		switch (lowerCaseSummaryType) {
			case "weak":
				return new WeakSummary(conn);
			case "strong":
				return new StrongSummary(conn);
			case "typedweak":
				return new TypedWeakSummary(conn);
			case "typedstrong":
				return new TypedStrongSummary(conn);
		}
		return null;
	}
}
