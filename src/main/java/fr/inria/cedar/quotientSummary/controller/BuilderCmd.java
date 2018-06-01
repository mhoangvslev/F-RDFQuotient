package fr.inria.cedar.quotientSummary.controller;

import com.google.common.base.Preconditions;
import fr.inria.cedar.ontosql.rdfdb.dataloading.DataLoading;
import fr.inria.cedar.ontosql.rdfdb.dataloading.Parameters;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.bisim.OneBisimSummary;
import fr.inria.cedar.quotientSummary.strong.StrongSummary;
import fr.inria.cedar.quotientSummary.strong.TwoPassStrongSummary;
import fr.inria.cedar.quotientSummary.strong.TwoPassTypedStrongSummary;
import fr.inria.cedar.quotientSummary.strong.TypedStrongSummary;
import fr.inria.cedar.quotientSummary.weak.TwoPassTypedWeakSummary;
import fr.inria.cedar.quotientSummary.weak.TwoPassWeakSummary;
import fr.inria.cedar.quotientSummary.weak.TwoPassWeakSummaryWithUnionFind;
import fr.inria.cedar.quotientSummary.weak.TypedWeakSummary;
import fr.inria.cedar.quotientSummary.weak.WeakSummary;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
	private static long saturationTime;
	private static long summarySavingInPostgresTime;
	private static long summarySavingToDiskTime;

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
			return;
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
					exportLoadingStatisticsToDisk(args[1]);
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
			String databaseName = trimNT(datasetName, true);
			properties.put("database.name", databaseName);

			triplesTableName = properties.getProperty("database.triples_table_name");
			dictionaryTableName = properties.getProperty("database.dictionary_table_name");
			encodedTriplesTableName = properties.getProperty("database.encoded_triples_table_name");
			encodedSaturatedTriplesTableName = properties.getProperty("database.encoded_saturated_triples_table_name");

			settings = new Parameters();
			settings.setPropertiesFileName(CONFIGURATION_FILE);
		}
		catch (FileNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
		catch (IOException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
	}

	private static void getConnection() {
		Properties connectionProps = new Properties();

		connectionProps.put("user", properties.getProperty("database.user"));
		connectionProps.put("password", properties.getProperty("database.password"));

		String connectionURL = "jdbc:postgresql://" + properties.getProperty("database.host")
			+ ":" + properties.getProperty("database.port") + "/"
			+ properties.getProperty("database.name");
		try {
			connectionInUse = DriverManager.getConnection(connectionURL, connectionProps);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
		LOGGER.info("Connection to Postgres established with URL: " + connectionURL
			+ " with user " + properties.getProperty("database.user")
			+ " and password " + properties.getProperty("database.password"));

		Preconditions.checkState(connectionInUse != null, "No connection for " + connectionURL);
	}

	private static void closeConnection() {
		try {
			connectionInUse.close();
			connectionInUse = null;
			LOGGER.info("Connection closed");
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
	}

	private static String trimNT(String fileName, boolean trimSlash) {
		int lastDotPosition = Math.max(0, fileName.lastIndexOf("."));
		int lastSlashPosition = 0;
		if (trimSlash) {
			lastSlashPosition = Math.max(0, fileName.lastIndexOf("/"));
			if (lastDotPosition - lastSlashPosition < 1) {
				throw new IllegalStateException("Was not able to extract a core component of the file name " + fileName);
			}
		}
		return fileName.substring(lastSlashPosition, lastDotPosition);
	}

	private static void load(String datasetName, Boolean loadSaturated) {
		if (loadSaturated) {
			properties.put("saturation.enable", "true");
		}
		else {
			properties.put("saturation.enable", "false");
		}

		LOGGER.info("Loading graph to Postgres");
		try {
			settings.getAllInFiles().add(datasetName);
			DataLoading.process(settings);
		}
		catch (IOException ex) {
			LOGGER.error("Data loading failed: " + ex);
			System.exit(1);
		}
		saturationTime = (loadSaturated) ? DataLoading.timeExecutionPerProcess.get("RDFGraphSaturator") : 0L;
		LOGGER.info("Graph loaded to Postgres");
	}

	private static void exportLoadingStatisticsToDisk(String datasetName) {
		String csvFileName = trimNT(datasetName, false) + "-loading-statistics.csv";
		LOGGER.info("Exporting loading statistics to disk to the file " + csvFileName);
		try (PrintWriter pw = new PrintWriter(new File(csvFileName))) {
			StringBuilder sb = new StringBuilder();
			sb.append("saturationTime").append('\n');
			sb.append(saturationTime).append('\n');
			pw.write(sb.toString());
		}
		catch (FileNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
		LOGGER.info("Loading statistics exported to disk");
	}

	private static Summary createNewSummary(String summaryType, String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		String lowerCaseSummaryType = summaryType.toLowerCase();
		switch (lowerCaseSummaryType) {
			case "weak":
				return new WeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "2pweak":
				return new TwoPassWeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "2pweakunionfind":
				return new TwoPassWeakSummaryWithUnionFind(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "strong":
				return new StrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "2pstrong":
				return new TwoPassStrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "typedweak":
				return new TypedWeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "2ptypedweak":
				return new TwoPassTypedWeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "typedstrong":
				return new TypedStrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "2ptypedstrong":
				return new TwoPassTypedStrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "onefb":
				return new OneBisimSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName); 
		}
		return null;
	}

	private static void summarize(String datasetName, String summaryType, Boolean summarizeSaturated) {
		LOGGER.info("Summarizing graph from Postgres");
		summaryInUse = createNewSummary(summaryType, datasetName, triplesTableName, summarizeSaturated ? encodedSaturatedTriplesTableName : encodedTriplesTableName, dictionaryTableName);
		summaryInUse.summarizeFromPostgres(connectionInUse);
		LOGGER.info("Graph from Postgres summarized");
	}

	private static void saveSummaryInPostgres() {
		long start = System.currentTimeMillis();
		summaryInUse.saveSummaryInPostgres(connectionInUse, false, "");
		summarySavingInPostgresTime = System.currentTimeMillis() - start;
	}

	private static void exportSummaryToDisk() {
		LOGGER.info("Exporting summary to disk");
		long start = System.currentTimeMillis();
		summaryInUse.writeDecodedSummaryToNTFile(connectionInUse);
		summarySavingToDiskTime = System.currentTimeMillis() - start;
		LOGGER.info("Summary exported to disk");
	}

	private static void exportSummarizationStatisticsToDisk() {
		String csvFileName = trimNT(summaryInUse.getNTSummaryFileName(), false) + "-summarization-statistics.csv";
		LOGGER.info("Exporting summarization statistics to disk to the file " + csvFileName);
		try (PrintWriter pw = new PrintWriter(new File(csvFileName))) {
			HashMap<String, String> statistics = summaryInUse.getRunStatistics();
			statistics.put("summarySavingInPostgresTime", Long.toString(summarySavingInPostgresTime));
			statistics.put("summarySavingToDiskTime", Long.toString(summarySavingToDiskTime));
			StringBuilder sb = new StringBuilder();
			ArrayList<String> keys = new ArrayList<>();
			keys.addAll(statistics.keySet());
			Collections.sort(keys);
			for (String key: keys) {
				sb.append(key).append(',');
			}
			sb.append('\n');
			for (String key: keys) {
				sb.append(statistics.get(key)).append(',');
			}
			sb.append('\n');
			pw.write(sb.toString());
		}
		catch (FileNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
		LOGGER.info("Summarization statistics exported to disk");
	}
}