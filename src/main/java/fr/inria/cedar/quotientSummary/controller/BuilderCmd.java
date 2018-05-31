package fr.inria.cedar.quotientSummary.controller;

import fr.inria.cedar.ontosql.rdfdb.dataloading.DataLoading;
import fr.inria.cedar.ontosql.rdfdb.dataloading.Parameters;
import fr.inria.cedar.quotientSummary.Summary;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.util.Properties;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class BuilderCmd {
	private static final Logger LOGGER = Logger.getLogger(BuilderCmd.class.getName());
	private static final String CONFIGURATION_FILE = System.getProperty("user.dir") + "/conf/dataLoadingCmd.properties";

	private static Properties properties;
	private static Parameters settings;
	private static String triplesTableName;
	private static String dictionaryTableName;
	private static String encodedTriplesTableName;
	private static String encodedSaturatedTriplesTableName;
	private static Connection connectionInUse;
	private static Summary summaryInUse;

	public BuilderCmd() {
	}

	private static void displayUsageInfo() {
		System.out.println("The framework is designed to work with one graph at the time");
		System.out.println("Usage:");
		System.out.println("args[0]=load fileName opt1: loads a file fileName to the database fileName");
		System.out.println("    if opt1 is true its saturation is computed and stored in the same database");
		System.out.println("    if opt2 is true it exports loading statistics to disk");
		System.out.println("args[0]=summarize fileName summaryType opt1 opt2 opt3 opt4");
		System.out.println("    summarizes a graph from the database fileName using summaryType algorithm and");
		System.out.println("    if opt1 is true it uses the saturated version of the graph");
		System.out.println("    if opt2 is true it saves the summary to Postgres");
		System.out.println("    if opt3 is true it exports the summary to disk");
		System.out.println("    if opt4 is true it exports summarization statistics to disk");
	}

	public static void main(String[] args) {
		LOGGER.setLevel(Level.INFO);

		if (args.length == 0) {
			displayUsageInfo();
		}

		switch(args[0]) {
			case "load":
				if (args.length != 4) {
					displayUsageInfo();
				}
				setUpConfiguration(args[1]);
				getConnection();
				load(args[1], "true".equals(args[2]));
				if ("true".equals(args[3])) {
					exportLoadingStatisticsToDisk();
				}
				closeConnection();
				return;
			case "summarize":
				if (args.length != 7) {
					displayUsageInfo();
				}
				setUpConfiguration(args[1]);
				getConnection();
				summarize(args[1], args[2], "true".equals(args[3]));
				if ("true".equals(args[4])) {
					saveSummaryInPostgres();
				}
				if ("true".equals(args[5])) {
					exportSummaryToDisk();
				}
				if ("true".equals(args[6])) {
					exportSummarizationStatisticsToDisk();
				}
				closeConnection();
				return;
			default:
				displayUsageInfo();
		}
	}

	/*
		The configuration file must specify values for the following attributes:
			database.engine
			database.host
			database.port
			database.user
			database.password
			database.storage_layout
			database.drop_existing_db
			dictionary.fetch_size
			saturation.batch_size
			database.triples_table_name
			database.encoded_triples_table_name
			database.dictionary_table_name
			database.encoded_saturated_triples_table_name
	*/
	private static void setUpConfiguration(String datasetName) {
		properties = new Properties();
		try {
			properties.load(new FileReader(CONFIGURATION_FILE));
		}
		catch (FileNotFoundException ex) {
			LOGGER.error(ex);
		}
		catch (IOException ex) {
			LOGGER.error(ex);
		}

		triplesTableName = properties.getProperty("database.triples_table_name");
		dictionaryTableName = properties.getProperty("database.dictionary_table_name");
		encodedTriplesTableName = properties.getProperty("database.encoded_triples_table_name");
		encodedSaturatedTriplesTableName = properties.getProperty("database.encoded_saturated_triples_table_name");

		settings = new Parameters();
		settings.setPropertiesFileName(CONFIGURATION_FILE);
	}

	private static void getConnection() {
		// TODO
	}

	private static void closeConnection() {
		// TODO
	}

	private static void load(String datasetName, Boolean loadSaturated) {
		// TODO
		// set database.name to fileName
		// set saturation.enable = true
		LOGGER.info("Loading graph to Postgres");
		try {
			settings.getAllInFiles().add(datasetName);
			DataLoading.process(settings);
		}
		catch (IOException ex) {
			LOGGER.error("Data loading failed: " + ex);
			return;
		}
		LOGGER.info("Graph loaded to Postgres");
	}

	private static void exportLoadingStatisticsToDisk() {
		// TODO
		// use -loading-statistics as a suffix
	}

	private static String abbreviation(String summaryType) {
		switch(summaryType) {
			case "weak":
				return "w";
			case "2pweak":
				return "2pw";
			case "2pweakunionfind":
				return "2pwuf";
			case "strong":
				return "s";
			case "2pstrong":
				return "2ps";
			case "typedweak":
				return "tw";
			case "2ptypedweak":
				return "2ptw";
			case "typedstrong":
				return "ts";
			case "2ptypedstrong":
				return "2pts";
			case "onefb":
				return "1fb";
		}
		return null;
	}

	private static void summarize(String datasetName, String summaryType, Boolean summarizeSaturated) {
		// TODO
	}

	private static void saveSummaryInPostgres() {
		// TODO
	}

	private static void exportSummaryToDisk() {
		// TODO
	}

	private static void exportSummarizationStatisticsToDisk() {
		// TODO
		// use -summarization-statistics as a suffix
	}
}