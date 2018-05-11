package fr.inria.cedar.quotientSummary.controller;

import com.google.common.base.Preconditions;
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
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class Builder {
	private static final Logger LOGGER = Logger.getLogger(Builder.class.getName());
	// Default properties files
	private static final String DEFAULT_CONFIG_FILE = System.getProperty("user.dir") + "/conf/dataLoading.properties";
	private static final String SATURATION_CONFIG_FILE = System.getProperty("user.dir") + "/conf/dataLoadingWithSaturation.properties";
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
				connectionInUse = loadGraphInPostgres(nextArguments, false);
				return;
			case "loadWithSaturation":
				connectionInUse = loadGraphInPostgres(nextArguments, true);
				return;
			case "summarizeUnsaturated":
				summaryInUse = summarizeGraphFromPostgres(nextArguments, false);
				return;
			case "summarizeSaturated":
				summaryInUse = summarizeGraphFromPostgres(nextArguments, true);
				return;
			case "saturate":
				saturate();
				return;
			case "loadWithSaturationAndSummarize":
				connectionInUse = loadGraphInPostgres(filesToLoad, true);
				summaryInUse = summarizeGraphFromPostgres(nextArguments, true);
				return;
			case "loadAndSummarizeUsingShortcut":
				connectionInUse = loadGraphInPostgres(filesToLoad, false);
				summarizeGraphFromPostgres(nextArguments, false);
				saturate();
				summaryInUse = summarizeGraphFromPostgres(nextArguments, true);
				return;
			case "saveSummary":
				saveSummary();
				return;
			case "exportSummary":
				exportSummary(nextArguments);
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
		System.out.println("Usage:");
		System.out.println("args[0]=loadWithoutSaturation: opens connection and loads the graph in Postgres without saturating it");
		System.out.println("args[0]=loadWithSaturation: opens connection and loads the graph in Postgres and saturates it");
		System.out.println("args[0]=summarizeUnsaturated: summarizes the unsaturated graph from Postgres");
		System.out.println("args[0]=summarizeSaturated: summarizes the saturated graph from Postgres");
		System.out.println("args[0]=saturate: saturates unsaturated graph from Postgres");
		System.out.println("args[0]=loadWithSaturationAndSummarize: loads the graph in Postgres, saturates it, and summarizes it");
		System.out.println("args[0]=loadAndSummarizeUsingShortcut: loads the graph in Postgres, summarizes it, saturates it, and summarizes again (shortcut)");
		//System.out.println("args[0]=summarizeEncodedFile: build the summary out of integer-encoded triples in a file");
		System.out.println("args[0]=saveSummary: saves summary to Postgres");
		System.out.println("args[0]=exportSummary: saves summary to the disk in nt, dot and png formats");
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
	private static Connection loadGraphInPostgres(String[] args, Boolean saturate) throws FileNotFoundException, IOException, UnsupportedDatabaseEngineException, SQLException {
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
		if (saturate)
			configFile = SATURATION_CONFIG_FILE;

		Properties properties = new Properties();
		properties.load(new FileReader(configFile));
		LOGGER.debug(properties.toString());
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
		sum.summarizeFromPostgres(connectionInUse, args);
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
			case "typedweak":
				return new TypedWeakSummary();
			case "typedstrong":
				return new TypedStrongSummary();
		}
		return null;
	}

	private static String tableName(Boolean summarizeSaturated) {
		return "encoded_triples"; // TODO: return correct name
	}

	private static void saturate() {
		return; // TODO: use variable connectionInUse
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

	private static void saveSummary() {
		summaryInUse.saveSummaryInPostgres(connectionInUse, ""); // TODO: tableName
	}

	private static void exportSummary(String[] args) {
		LOGGER.info("Exporting summary to disk");
		summaryInUse.writeDecodedSummaryToNTFile(args[0]);
		summaryInUse.drawSummaryAndGraph(connectionInUse, args[0]);
		LOGGER.info("Statistics: " + summaryInUse.getRunStatistics().toString());
		LOGGER.info("Summary exported to disk");
	}

	private static void closeConnection() throws SQLException {
		connectionInUse.close();
		LOGGER.info("Connection closed");
	}
}
