package fr.inria.cedar.quotientSummary.controller;

import com.google.common.base.Preconditions;
import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.ontosql.rdfdb.dataloading.DataLoading;
import fr.inria.cedar.ontosql.rdfdb.dataloading.Parameters;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.strong.StrongSummary;
import fr.inria.cedar.quotientSummary.strong.TwoPassStrongSummary;
import fr.inria.cedar.quotientSummary.strong.TypedStrongSummary;
import fr.inria.cedar.quotientSummary.weak.TypedWeakSummary;
import fr.inria.cedar.quotientSummary.weak.WeakSummary;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Builder {
	private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());
	// Default properties files
	private static final String DEFAULT_CONFIG_FILE = System.getProperty("user.dir") + "/conf/dataLoading.properties";
	private static final String SATURATION_CONFIG_FILE = System.getProperty("user.dir") + "/conf/dataLoadingWithSaturation.properties";
	private static final String SATURATION_SHORTCUT_CONFIG_FILE = System.getProperty("user.dir") + "/conf/dataLoadingWithSaturationForShortcut.properties";
	private static String triplesTableName = "tmp_triples";
	private static String dictionaryTableName = "dictionary";
	private static Connection connectionInUse;
	private static Summary summaryInUse;

	public Builder() {
	}

	/**
	 * @param args
	 *
	 * @throws IOException
	 * @throws SQLException
	 * @throws UnsupportedDatabaseEngineException
	 */
	public static void main(String[] args) throws IOException, UnsupportedDatabaseEngineException, SQLException {
		LOGGER.setLevel(Level.INFO);
		if (args.length == 0) {
			printUsage();
			return;
		}
		String[] nextArguments = {};
		String[] filesToLoad = {};
		if (args.length > 1) {
			nextArguments = extractArguments(args);
			if (args.length > 2)
				filesToLoad = extractArguments(nextArguments);
		}
		switch (args[0]) {
			case "loadWithoutSaturation":
				connectionInUse = loadGraphInPostgres(nextArguments, false, false);
				return;
			case "loadWithSaturation":
				connectionInUse = loadGraphInPostgres(nextArguments, true, false);
				return;
			case "summarizeUnsaturated":
				summaryInUse = summarizeGraphFromPostgres(nextArguments, false);
				return;
			case "summarizeSaturated":
				summaryInUse = summarizeGraphFromPostgres(nextArguments, true);
				return;
			case "loadAndSummarize":
				connectionInUse = loadGraphInPostgres(filesToLoad, false, false);
				summaryInUse = summarizeGraphFromPostgres(nextArguments, false);
				return;
			case "loadWithSaturationAndSummarize":
				connectionInUse = loadGraphInPostgres(filesToLoad, true, false);
				summaryInUse = summarizeGraphFromPostgres(nextArguments, true);
				return;
			case "loadAndSummarizeUsingShortcut":
				connectionInUse = loadGraphInPostgres(filesToLoad, false, false);
				summaryInUse = summarizeGraphFromPostgres(nextArguments, false);
				saveSummary(Boolean.TRUE, "shortcut");
				exportSummary(filesToLoad); // TODO: figure out proper fileName
				closeConnection();
				connectionInUse = loadGraphInPostgres(filesToLoad, true, true);
				summaryInUse = summarizeGraphFromPostgres(nextArguments, true); // TODO: edit nextArguments args[1]
				return;
			case "saveSummaryComputedWithoutSaturation":
				saveSummary(Boolean.TRUE, "noSaturation");
				return;
			case "saveSummaryComputedClassicalWay":
				saveSummary(Boolean.FALSE, "classical");
				return;
			case "saveSummaryComputedUsingShortcut":
				saveSummary(Boolean.FALSE, "shortcut");
				return;
			case "exportSummary":
				exportSummary(nextArguments);
				return;
			case "dropPartialResultsTables":
				dropPartialResultsTables();
				return;
			case "closeConnection":
				closeConnection();
				return;
			default:
				break;
		}
		printUsage();
	}

	private static void printUsage() {
		System.out.println("The framework is designed to work with one graph at the time. Tables created until save are to be considered temporary.");
		System.out.println("Usage:");
		System.out.println("args[0]=loadWithoutSaturation: opens connection and loads the graph in Postgres without saturating it");
		System.out.println("args[0]=loadWithSaturation: opens connection and loads the graph in Postgres and saturates it");
		System.out.println("args[0]=summarizeUnsaturated: summarizes the unsaturated graph from Postgres");
		System.out.println("args[0]=summarizeSaturated: summarizes the saturated graph from Postgres");
		System.out.println("args[0]=loadWithSaturationAndSummarize: loads the graph in Postgres, saturates it, and summarizes it");
		System.out.println("args[0]=loadAndSummarizeUsingShortcut: loads the graph in Postgres, summarizes it, saturates it, and summarizes again (shortcut)");
		//System.out.println("args[0]=summarizeEncodedFile: build the summary out of integer-encoded triples in a file");
		System.out.println("args[0]=saveSummaryComputedClassicalWay: saves summary computed classical way to Postgres");
		System.out.println("args[0]=saveSummaryComputedUsingShortcut: saves summary computed using shortcut to Postgres");
		System.out.println("args[0]=exportSummary: saves summary to the disk in nt, dot and png formats");
		System.out.println("args[0]=dropPartialResultsTables: drops partial results tables in Postgres");
		System.out.println("args[0]=closeConnection: closes connection to Postgres");
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

	/**
	 * This method loads the data in Postgres through the ontoSQL loader
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
	private static Connection loadGraphInPostgres(String[] args, Boolean saturate, Boolean shortcut) throws FileNotFoundException, IOException, UnsupportedDatabaseEngineException, SQLException {
		LOGGER.info("Loading graph to Postgres");
		LOGGER.debug(System.getProperty("user.dir"));

		List<String> tripleFiles = new ArrayList<>();
		List<String> rdfsFiles = new ArrayList<>();

		int fileNo;
		for (fileNo = 0; fileNo < args.length; fileNo++) {
			String s = args[fileNo];
			if ((fileNo == 0) || ((args.length > 1) && (fileNo < args.length - 1))) {
				LOGGER.debug("Triple file: " + s);
				tripleFiles.add(s);
			}
			else {
				LOGGER.debug("Schema file: " + s);
				rdfsFiles.add(s);
			}
		}

		String configFile = DEFAULT_CONFIG_FILE;
		if (saturate) {
			if (shortcut)
				configFile = SATURATION_SHORTCUT_CONFIG_FILE;
			else
				configFile = SATURATION_CONFIG_FILE;
		}

		Properties properties = new Properties();
		properties.load(new FileReader(configFile));
		LOGGER.debug(properties.toString());
		triplesTableName = properties.getProperty("database.triples_table_name");
		dictionaryTableName = properties.getProperty("database.dictionary_table_name");
		Parameters settings = new Parameters();
		settings.setPropertiesFileName(configFile);

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
		LOGGER.info("Graph loaded to Postgres");

		Properties connectionProps = new Properties();
		connectionProps.put("user", properties.getProperty("database.user"));
		connectionProps.put("password", properties.getProperty("database.password"));

		String connectionURL = "jdbc:postgresql://" + properties.getProperty("database.host") + ":" + properties.getProperty("database.port") + "/" + properties.getProperty("database.name");
		Connection conn = DriverManager.getConnection(connectionURL, connectionProps);
		LOGGER.info("Connection to Postgres established with URL: " + connectionURL);
		Preconditions.checkState(conn != null, "No connection for " + connectionURL);
		return conn;
	}

	/**
	 * This method assumes the graph has already been loaded
	 *
	 * @param conn
	 * @param args
	 *
	 * @throws IOException
	 * @throws SQLException
	 */
	private static Summary summarizeGraphFromPostgres(String[] args, Boolean summarizeSaturated) throws SQLException, IOException {
		LOGGER.info("Summarizing graph from Postgres");
		Summary sum = createNewSummary(args[0]);
		args[0] = tableName(summarizeSaturated);
		String[] sumArgs = {args[0], args[1], dictionaryTableName};
		sum.summarizeFromPostgres(connectionInUse, sumArgs);
		LOGGER.info("Graph from Postgres summarized");
		return sum;
	}

	private static Summary createNewSummary(String summaryType) {
		String lowerCaseSummaryType = summaryType.toLowerCase();
		switch (lowerCaseSummaryType) {
			case "weak":
				return new WeakSummary();
			case "strong":
				return new StrongSummary();
			case "2pstrong":
				return new TwoPassStrongSummary();
			case "typedweak":
				return new TypedWeakSummary();
			case "typedstrong":
				return new TypedStrongSummary();
		}
		return null;
	}

	private static String tableName(Boolean saturated) {
		if (!saturated)
			return "tmp_encoded";

		try {
			ResultSet res = connectionInUse.getMetaData().getTables(null, null, "tmp_encoded_summarized_saturated", new String[] { "TABLE" });
			if(res.next()) // if tmp_encoded_summarized_saturated exists
				return "tmp_encoded_summarized_saturated";
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not find out if table " + "tmp_encoded_summarized_saturated" + " exists: " + e.toString());
		}

		return "tmp_encoded_saturated";
	}

	private static Summary readSummaryFromPostgres(String summaryType, Connection conn) {
		String lowerCaseSummaryType = summaryType.toLowerCase();
		switch (lowerCaseSummaryType) {
			case "weak":
				return new WeakSummary(conn);
			case "strong":
				return new StrongSummary(conn);
			case "2pstrong":
				return new TwoPassStrongSummary(conn);
			case "typedweak":
				return new TypedWeakSummary(conn);
			case "typedstrong":
				return new TypedStrongSummary(conn);
		}
		return null;
	}

	private static void saveSummary(Boolean partialResult, String summarizationTechnique) {
		dictionaryTableName = summaryInUse.saveSummaryInPostgres(connectionInUse, partialResult, summarizationTechnique, dictionaryTableName);
	}

	private static void exportSummary(String[] args) {
		LOGGER.info("Exporting summary to disk");
		summaryInUse.writeDecodedSummaryToNTFile(connectionInUse, args[0], dictionaryTableName);
		if (args.length > 1 && args[1].equals("draw"))
			summaryInUse.drawSummaryAndGraph(connectionInUse, args[0], triplesTableName, dictionaryTableName);
		LOGGER.info("Statistics: " + summaryInUse.getRunStatistics().toString());
		LOGGER.info("Summary exported to disk");
	}

	private static void closeConnection() throws SQLException {
		connectionInUse.close();
		LOGGER.info("Connection closed");
	}

	private static void dropPartialResultsTables() throws IllegalStateException {
		Statement stmt;
		try {
			stmt = connectionInUse.createStatement();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not create the statement: " + e.toString());
		}

		// drop tables matching tmp_* and dictionary
		try {
			connectionInUse.setAutoCommit(false);
			stmt.execute("select 'drop table '||tablename||';' from pg_tables where tablename like 'tmp_%'");
			connectionInUse.commit();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not drop partial results tables: " + e.toString());
		}

		LOGGER.info("All partial results tables dropped.");
	}
}
