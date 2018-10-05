package fr.inria.cedar.quotientSummary.controller;

import com.google.common.base.Preconditions;
import fr.inria.cedar.ontosql.rdfdb.dataloading.DataLoading;
import fr.inria.cedar.ontosql.rdfdb.dataloading.Parameters;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.bisim.OneBisimSummary;
import fr.inria.cedar.quotientSummary.bisim.OneFWSummary;
import fr.inria.cedar.quotientSummary.strong.StrongSummary;
import fr.inria.cedar.quotientSummary.strong.TwoPassStrongSummary;
import fr.inria.cedar.quotientSummary.strong.TwoPassTypedStrongSummary;
import fr.inria.cedar.quotientSummary.strong.TypedStrongSummary;
import fr.inria.cedar.quotientSummary.util.PostgresIdentifier;
import fr.inria.cedar.quotientSummary.weak.TwoPassTypedWeakSummary;
import fr.inria.cedar.quotientSummary.weak.TwoPassWeakSummary;
import fr.inria.cedar.quotientSummary.weak.TwoPassWeakSummaryWithUnionFind;
import fr.inria.cedar.quotientSummary.weak.TypedWeakSummary;
import fr.inria.cedar.quotientSummary.weak.WeakSummary;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
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
		System.out.println("args[0]=load fileName opt1 opt2: loads a file fileName to the database fileName");
		System.out.println("    if opt1 is true its saturation is computed and stored in the same database");
		System.out.println("    if opt2 is true it exports loading statistics to disk");
		System.out.println("args[0]=load fileName opt1 opt2 opt3: loads a file fileName to the database fileName");
		System.out.println("    opt1 specifies the storage layout of the database: either TRIPLES_TABLE or TABLE_PER_ROLE_AND_CONCEPT");
		System.out.println("    if opt2 is true its saturation is computed and stored in the same database");
		System.out.println("    if opt3 is true it exports loading statistics to disk");
		System.out.println("args[0]=summarize fileName summaryType opt1 opt2 opt3 opt4 opt5");
		System.out.println("    summarizes a graph from the database fileName using summaryType algorithm and");
		System.out.println("    if opt1 is true it uses the saturated version of the graph");
		System.out.println("    if opt2 is true it saves the summary to Postgres");
		System.out.println("    if opt3 is true it exports the summary to disk");
		System.out.println("    if opt4 is true it exports summarization statistics to disk");
		System.out.println("    if opt5 is false then it doesn't draw with DOT, if set to plain draws a graph with default layout, if set to splitleaves it draws splitting leaves, and if set to foldleaves it uses folded layout");
	}

	public static void main(String[] args) {
		LOGGER.setLevel(Level.INFO);

		if (args.length == 0) {
			displayUsageInfo();
			return;
		}

		String fileName, layout = "TRIPLES_TABLE", summaryType;
		boolean saturate, exportLoadingStatistics, saturated, saveInPostgres, exportToDisk, exportSummarizationStatistics;
		switch (args[0]) {
		case "load":
			switch (args.length) {
				case 4:
					saturate = "true".equals(args[2]);
					exportLoadingStatistics = "true".equals(args[3]);
					break;
				case 5:
					layout = args[2];
					if (!layout.equals("TRIPLES_TABLE") && !layout.equals("TABLE_PER_ROLE_AND_CONCEPT")) {
						displayUsageInfo();
						return;
					}
					saturate = "true".equals(args[3]);
					exportLoadingStatistics = "true".equals(args[4]);
					break;
				default:
					displayUsageInfo();
					return;
			}
			fileName = args[1];
			setUpConfiguration(fileName, layout, saturate);
			load(fileName, saturate);
			if (exportLoadingStatistics) {
				exportLoadingStatisticsToDisk(fileName);
			}
			return;
		case "summarize":
			if (args.length != 8) {
				displayUsageInfo();
				return;
			}
			fileName = args[1];
			summaryType = args[2];
			saturated = "true".equals(args[3]);
			saveInPostgres = "true".equals(args[4]);
			exportToDisk = "true".equals(args[5]);
			exportSummarizationStatistics = "true".equals(args[6]);
			setUpConfiguration(fileName, "TRIPLES_TABLE", saturated);
			getConnection();
			summarize(fileName, summaryType, saturated);
			if (saveInPostgres) {
				saveSummaryInPostgres(saturated);
			}
			if (exportToDisk) {
				exportSummaryToDisk(saturated, args[7]);
			}
			if (exportSummarizationStatistics) {
				exportSummarizationStatisticsToDisk(saturated);
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
	private static void setUpConfiguration(String datasetName, String layout, boolean loadSaturated) { // add another boolean for layout, like saturation
		properties = new Properties();
		try {
			properties.load(new FileReader(CONFIGURATION_FILE));

			String databaseName = PostgresIdentifier.escapeQuotes(trimNT(datasetName, true));
			properties.put("database.name", databaseName);

			properties.put("database.storage_layout", layout);
			if (loadSaturated) {
				properties.put("saturation.enable", "true");
			}
			else {
				properties.put("saturation.enable", "false");
			}

			triplesTableName = properties.getProperty("database.triples_table_name");
			dictionaryTableName = properties.getProperty("database.dictionary_table_name");
			encodedTriplesTableName = properties.getProperty("database.encoded_triples_table_name");
			encodedSaturatedTriplesTableName = properties.getProperty("database.encoded_saturated_triples_table_name");

			String customPropertiesFileName = "conf/" + databaseName + ".properties";
			File customProperties = new File(customPropertiesFileName);
			OutputStream out = new FileOutputStream(customProperties);
			properties.store(out, "Custom properties file");

			settings = new Parameters();
			settings.setPropertiesFileName(customPropertiesFileName);
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
		try {
			Class.forName("org.postgresql.Driver");
		}
		catch (ClassNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

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

	public static String trimNT(String fileName, boolean trimSlash) {
		int lastDotPosition = Math.max(0, fileName.lastIndexOf("."));
		return fileName.substring(trimSlash ? fileName.lastIndexOf("/") + 1 : 0, lastDotPosition);
	}

	private static void load(String datasetName, boolean loadSaturated) {
		LOGGER.info("Loading graph to Postgres");
		settings.getAllInFiles().add(datasetName);
		DataLoading.process(settings);
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
			case "onefw":
				return new OneFWSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			}
		return null;
	}

	private static void summarize(String datasetName, String summaryType, boolean summarizeSaturated) {
		LOGGER.info("Summarizing graph from Postgres");
		summaryInUse = createNewSummary(summaryType, datasetName, triplesTableName, summarizeSaturated ? encodedSaturatedTriplesTableName : encodedTriplesTableName, dictionaryTableName);
		summaryInUse.summarizeFromPostgres(connectionInUse);
		LOGGER.info("Graph from Postgres summarized");
	}

	private static void saveSummaryInPostgres(boolean summarizeSaturated) {
		long start = System.currentTimeMillis();
		summaryInUse.saveSummaryInPostgres(connectionInUse, false, summarizeSaturated ? "sat" : "");
		summarySavingInPostgresTime = System.currentTimeMillis() - start;
	}

	private static void exportSummaryToDisk(boolean summarizeSaturated, String draw) {
		LOGGER.info("Exporting summary NT file to disk");
		long start = System.currentTimeMillis();
		summaryInUse.writeDecodedSummaryToNTFile(connectionInUse, summarizeSaturated ? "sat" : "");
		summarySavingToDiskTime = System.currentTimeMillis() - start;
		LOGGER.info("Summary NT file exported to disk");

		LOGGER.info("Exporting summary DOT drawing to disk");
		if (draw.toLowerCase().equals("plain"))
			summaryInUse.drawSummaryAndGraph(connectionInUse, summarizeSaturated ? "sat" : "no_sat");
		if (draw.toLowerCase().equals("splitleaves"))
			summaryInUse.writeDecodedSummaryToFileSplitLeavesAndDraw(connectionInUse, summarizeSaturated ? "sat" : "no_sat");
		if (draw.toLowerCase().equals("foldleaves") || draw.toLowerCase().equals("draw"))
			summaryInUse.writeDecodedSummaryToFileSplitFoldLeavesAndDraw(connectionInUse, summarizeSaturated ? "sat" : "no_sat");
		LOGGER.info("Summary DOT drawing exported to disk");
	}

	private static void exportSummarizationStatisticsToDisk(boolean summarizeSaturated) {
		String csvFileName = trimNT(summaryInUse.getNTSummaryFileName(summarizeSaturated ? "sat" : ""), false) + "-summarization-statistics.csv";
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
