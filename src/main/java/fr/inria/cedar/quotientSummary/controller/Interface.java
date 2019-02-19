//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.controller;

import com.google.common.base.Preconditions;
import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.ontosql.rdfdb.dataloading.Config;
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
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

public class Interface {
	private static final Logger LOGGER = Logger.getLogger(Interface.class.getName());
	private static Options options = null;
	private static Option loadOption;
	private static Option summarizeOption;
	private static Option readOption;
	private static Option helpOption;
	private static Option versionOption;
	private static Option dryRunOption;
	private static Option loadingPropertiesOption;
	private static Option summarizationPropertiesOption;
	private static Connection databaseConnection;
	private static Summary summary;
	private static long summarySavingToDiskTime;
	private static long summarySavingInPostgresTime;

	public Interface() {
	}

	private static String trimNT(String fileName, boolean trimSlash) {
		int lastDotPosition = Math.max(0, fileName.lastIndexOf("."));
		return fileName.substring(trimSlash ? fileName.lastIndexOf("/") + 1 : 0, lastDotPosition);
	}

	private static String deriveDatabaseNameFromFilename(String datasetFilename) {
		return PostgresIdentifier.escapeQuotes(trimNT(datasetFilename, true));
	}

	private static void exportLoadingStatisticsToDisk(Properties loadingProperties) {
		String datasetFilename = loadingProperties.getProperty("dataset.filename");
		String csvFilename = trimNT(datasetFilename, false) + "-loading-statistics.csv";

		LOGGER.info("Exporting loading statistics to disk to the file " + csvFilename);

		long loadingTime = DataLoading.timeExecutionPerProcess.get("LoadTriplesToDatabase");
		long saturationTime = (loadingProperties.getProperty("saturation.enable").equals("true")) ? DataLoading.timeExecutionPerProcess.get("RDFGraphSaturator") : 0L;

		try (PrintWriter pw = new PrintWriter(new File(csvFilename))) {
			StringBuilder sb = new StringBuilder();
			sb.append("loadingTime,saturationTime\n");
			sb.append(loadingTime).append(',').append(saturationTime).append('\n');
			pw.write(sb.toString());
		}
		catch (FileNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		LOGGER.info("Loading statistics exported to disk");
	}

	private static Properties reconcileProperties(Properties defaultProperties, String configurationFilename, Properties commandLineProperties) {
		Properties configurationFileProperties = ConfigurationProperties.getPropertiesFromFile(configurationFilename);
		return ConfigurationProperties.reconcileProperties(ConfigurationProperties.reconcileProperties(defaultProperties, configurationFileProperties), commandLineProperties);
	}

	public static void load(String configurationFilename, Properties commandLineProperties, boolean closeConnection) {
		Properties defaultProperties = LoadingProperties.getDefaultProperties();
		Properties loadingProperties = reconcileProperties(defaultProperties, configurationFilename, commandLineProperties);

		LOGGER.info("Loading graph to Postgres");
		String datasetFilename = loadingProperties.getProperty("dataset.filename");

		// derive database name from filename if not specified
		if (!loadingProperties.containsKey("database.name") || loadingProperties.getProperty("database.name").equals("")) {
			String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
			loadingProperties.put("database.name", databaseName);
		}

		List<String> datasetSourceFiles = new ArrayList<>();
		datasetSourceFiles.add(datasetFilename);
		Parameters datasets = new Parameters();
		datasets.setAllInFile(datasetSourceFiles);

		try {
			DataLoading.process(datasets, loadingProperties);
			Config configuration = new Config(loadingProperties);
			databaseConnection = configuration.getDataSource().getConnection();
		}
		catch (UnsupportedDatabaseEngineException | FileNotFoundException | SQLException ex) {
			LOGGER.error(ex.getMessage());
			System.exit(1);
		}

		LOGGER.info("Graph loaded to Postgres");

		if (loadingProperties.getProperty("statistics.export_to_csv_file").equals("true")) {
			LOGGER.info("Exporting loading statistics to disk");
			exportLoadingStatisticsToDisk(loadingProperties);
			LOGGER.info("Loading statistics exported to disk");
		}

		if (closeConnection) {
			closeDatabaseConnection();
		}
	}

	/*
		properties object needs to contain the following fields:
		- database.host
		- database.port
		- database.user
		- database.password
		- database.name
	*/
	public static void setUpDatabaseConnection(Properties properties) {
		try {
			Class.forName("org.postgresql.Driver");
		}
		catch (ClassNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		String connectionURL = "jdbc:postgresql://" + properties.getProperty("database.host") + ":" + properties.getProperty("database.port") + "/" + properties.getProperty("database.name");
		try {
			databaseConnection = DriverManager.getConnection(connectionURL, properties);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
		LOGGER.info("Connection to Postgres established with URL: " + connectionURL + " with user " + properties.getProperty("database.user") + " and password " + properties.getProperty("database.password"));

		Preconditions.checkState(databaseConnection != null, "No connection for " + connectionURL);
	}

	public static Connection getDatabaseConnection() throws IllegalStateException {
		if (databaseConnection == null) {
			throw new IllegalStateException("Connection is not set up.");
		}
		return databaseConnection;
	}

	public static void closeDatabaseConnection() {
		try {
			databaseConnection.close();
			databaseConnection = null;
			LOGGER.info("Connection closed");
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
	}

	private static Summary createNewSummary(Properties summarizationProperties) throws IllegalArgumentException {
		String summaryType = summarizationProperties.getProperty("summary.type");
		String triplesFileName = summarizationProperties.getProperty("dataset.filename");
		String triplesTableName = summarizationProperties.getProperty("database.triples_table_name");
		boolean summarizeSaturatedGraph = summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true");
		String encodedTriplesTableName = summarizeSaturatedGraph ? summarizationProperties.getProperty("database.encoded_saturated_triples_table_name") : summarizationProperties.getProperty("database.encoded_triples_table_name");
		String dictionaryTableName = summarizationProperties.getProperty("database.dictionary_table_name");
		switch (summaryType) {
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
		throw new IllegalArgumentException("Wrong summary identifier: " + summaryType);
	}

	private static void exportSummarizationStatisticsToDisk() {
		String csvFileName = trimNT(summary.getNTSummaryFileName(), false) + "-summarization-statistics.csv";
		LOGGER.info("Exporting summarization statistics to disk to the file " + csvFileName);
		try (PrintWriter pw = new PrintWriter(new File(csvFileName))) {
			HashMap<String, String> statistics = summary.getRunStatistics();
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

	public static void summarize(String configurationFilename, Properties commandLineProperties, boolean closeConnection) {
		Properties defaultProperties = SummarizationProperties.getDefaultProperties();
		Properties summarizationProperties = reconcileProperties(defaultProperties, configurationFilename, commandLineProperties);

		LOGGER.info("Summarizing graph from Postgres");
		String datasetFilename = summarizationProperties.getProperty("dataset.filename");

		// derive database name from filename if not specified
		if (!summarizationProperties.containsKey("database.name") || summarizationProperties.getProperty("database.name").equals("")) {
			String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
			summarizationProperties.put("database.name", databaseName);
		}

		try {
			summary = createNewSummary(summarizationProperties);
			summary.setSummarizationProperties(summarizationProperties);
			setUpDatabaseConnection(summarizationProperties);
			summary.summarizeFromPostgres(databaseConnection);
		}
		catch (IllegalArgumentException ex) {
			LOGGER.error(ex);
			System.out.println("Make sure that the input graph is loaded into database.");
			System.exit(1);
		}
		LOGGER.info("Graph from Postgres summarized");

		LOGGER.info("Exporting summary to disk to NT file");
		summarySavingToDiskTime = 0L;
		if (summarizationProperties.getProperty("summary.export_to_database").equals("true")) {
			long start = System.currentTimeMillis();
			summary.writeDecodedSummaryToNTFile(databaseConnection);
			summarySavingToDiskTime = System.currentTimeMillis() - start;
		}
		LOGGER.info("Summary NT file exported to disk");

		summarySavingInPostgresTime = 0L;
		if (summarizationProperties.getProperty("summary.export_to_database").equals("true")) {
			LOGGER.info("Saving summary to Postgres");
			long start = System.currentTimeMillis();
			summary.saveSummaryInPostgres(databaseConnection);
			summarySavingInPostgresTime = System.currentTimeMillis() - start;
			LOGGER.info("Summary saved in Postgres");
		}

		if (summarizationProperties.getProperty("statistics.export_to_csv_file").equals("true")) {
			LOGGER.info("Exporting loading statistics to disk");
			exportSummarizationStatisticsToDisk();
			LOGGER.info("Loading statistics exported to disk");
		}

		String drawingStyle = summarizationProperties.getProperty("drawing.style");
		if (drawingStyle.equals("plain")) {
			LOGGER.info("Exporting summary DOT drawing to disk");
			summary.writeEncodedSummaryToFileAndDraw(databaseConnection);
			LOGGER.info("Summary DOT drawing exported to disk");
		}
		else if (drawingStyle.equals("split_leaves")) {
			LOGGER.info("Exporting summary DOT drawing to disk");
			summary.writeDecodedSummaryToFileSplitLeavesAndDraw(databaseConnection);
			LOGGER.info("Summary DOT drawing exported to disk");
		}
		if (drawingStyle.equals("split_and_fold_leaves")) {
			LOGGER.info("Exporting summary DOT drawing to disk");
			summary.writeDecodedSummaryToFileSplitFoldLeavesAndDraw(databaseConnection);
			LOGGER.info("Summary DOT drawing exported to disk");
		}

		if (closeConnection) {
			closeDatabaseConnection();
		}
	}

	private static void setUpCommandLineInterface() {
		loadOption = Option.builder("l")
			.longOpt("load")
			.desc("- load the dataset into database\n[ARGS] must specify a value for dataset.filename\nCAUTION: database is dropped by default")
			.hasArg(true)
			.argName("[ARGS]")
			.required(false)
			.build();

		summarizeOption = Option.builder("s")
			.longOpt("summarize")
			.desc("- summarize the dataset\n[ARGS] must specify a value for dataset.filename or database.name")
			.hasArg(true)
			.argName("[ARGS]")
			.required(false)
			.build();

		readOption = Option.builder("r")
			.longOpt("read")
			.desc("- read summary from database\n[ARGS] must specify a value for database.name")
			.hasArg(true)
			.argName("[ARGS]")
			.required(false)
			.build();

		helpOption = Option.builder("h")
			.longOpt("help")
			.desc("- print help message")
			.required(false)
			.build();

		versionOption = Option.builder("v")
			.longOpt("version")
			.desc("- print version of RDQQuotient")
			.required(false)
			.build();

		dryRunOption = Option.builder("d")
			.longOpt("dry-run")
			.desc("- print the configuration used for a run")
			.required(false)
			.build();

		final OptionGroup mainOptions = new OptionGroup();
		mainOptions.addOption(loadOption);
		mainOptions.addOption(summarizeOption);
		mainOptions.addOption(readOption);
		mainOptions.addOption(helpOption);
		mainOptions.addOption(versionOption);
		mainOptions.addOption(dryRunOption);

		loadingPropertiesOption = Option.builder("lp")
			.longOpt("loading-properties")
			.desc("- set loading properties filename")
			.hasArg(true)
			.argName("filename")
			.required(false)
			.build();

		summarizationPropertiesOption = Option.builder("sp")
			.longOpt("summarization-properties")
			.desc("- set summarization properties filename")
			.hasArg(true)
			.argName("filename")
			.required(false)
			.build();

		final OptionGroup configurationFilesOptions = new OptionGroup();
		configurationFilesOptions.addOption(loadingPropertiesOption);
		configurationFilesOptions.addOption(summarizationPropertiesOption);

		options = new Options();
		options.addOptionGroup(mainOptions);
		options.addOptionGroup(configurationFilesOptions);
	}

	private static void printHelp() {
		String version;
		MavenXpp3Reader reader = new MavenXpp3Reader();
		try {
			Model model = reader.read(new FileReader("pom.xml"));
			version = model.getVersion();
		}
		catch(IOException | XmlPullParserException ex) {
			version = "";
		}
		final HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.setWidth(80);
		helpFormatter.setLeftPadding(0);
		System.out.println("RDFQuotient " + version);
		System.out.println();
		System.out.println("This framework is designed to work with one graph at the time.");
		System.out.println("Before using RDFQuotient make sure that Postgres server is running.");
		System.out.println();
		helpFormatter.printHelp("rdfquotient", "\n", options, "\n[ARGS] is a comma-separated list of assigments of form key=value, where key is a configuration property from the list of loading or summarization configuration properties.", true);
	}

	private static Properties parseProperties(String commandLineProperties) {
		String[] properties = commandLineProperties.split(","); // no escaping assumed for commas
		Properties newProperties = new Properties();
		for (String assignement: properties) {
			String[] assigmentSplit = assignement.split("=");
			newProperties.put(assigmentSplit[0].trim(), assigmentSplit[1].trim());
		}
		return newProperties;
	}

	public static void main(String[] args) {
		LOGGER.setLevel(Level.INFO);

		setUpCommandLineInterface();
		databaseConnection = null;

		if (args.length == 0) {
			printHelp();
			return;
		}

		final CommandLineParser parser = new DefaultParser();
		try {
			final CommandLine arguments = parser.parse(options, args);

			if (arguments.hasOption(helpOption.getArgName())) {
				printHelp();
				return;
			}

			if (arguments.hasOption(versionOption.getArgName())) {
				String version;
				MavenXpp3Reader reader = new MavenXpp3Reader();
				try {
					Model model = reader.read(new FileReader("pom.xml"));
					version = model.getVersion();
				}
				catch(IOException | XmlPullParserException ex) {
					version = "unknown";
				}
				System.out.println("RDFQuotient version: " + version);
				return;
			}

			String loadingPropertiesFilename = LoadingProperties.DEFAULT_LOADING_PROPERTIES_FILE_NAME;
			if (arguments.hasOption(loadingPropertiesOption.getArgName())) {
				loadingPropertiesFilename = arguments.getOptionValue(loadingPropertiesOption.getArgName());
			}

			String summarizationPropertiesFilename = SummarizationProperties.DEFAULT_SUMMARIZATION_PROPERTIES_FILE_NAME;
			if (arguments.hasOption(summarizationPropertiesOption.getArgName())) {
				summarizationPropertiesFilename = arguments.getOptionValue(summarizationPropertiesOption.getArgName());
			}

			if (arguments.hasOption(loadOption.getArgName())) {
				Properties commandLineProperties = parseProperties(arguments.getOptionValue(loadOption.getArgName()));
				if (arguments.hasOption(dryRunOption.getArgName())) {
					load(loadingPropertiesFilename, commandLineProperties, true);
				}
				else {
					Properties defaultProperties = LoadingProperties.getDefaultProperties();
					Properties loadingProperties = reconcileProperties(defaultProperties, loadingPropertiesFilename, commandLineProperties);
					System.out.println(loadingProperties.toString());
					if (loadingProperties.getProperty("database.drop_exisiting_db").equals("true")) {
						String datasetFilename = loadingProperties.getProperty("dataset.filename");
						String databaseName;
						if (!loadingProperties.containsKey("database.name") || loadingProperties.getProperty("database.name").equals("")) {
							databaseName = deriveDatabaseNameFromFilename(datasetFilename);
						}
						else {
							databaseName = loadingProperties.getProperty("database.name");
						}
						System.out.println("CAUTION: database " + databaseName + " will be dropped before loading.");
					}
				}
				return;
			}

			if (arguments.hasOption(summarizeOption.getArgName())) {
				Properties commandLineProperties = parseProperties(arguments.getOptionValue(summarizeOption.getArgName()));
				if (arguments.hasOption(dryRunOption.getArgName())) {
					summarize(summarizationPropertiesFilename, commandLineProperties, true);
				}
				else {
					Properties defaultProperties = SummarizationProperties.getDefaultProperties();
					Properties summarizationProperties = reconcileProperties(defaultProperties, summarizationPropertiesFilename, commandLineProperties);
					System.out.println(summarizationProperties.toString());
				}
				return;
			}

			if (arguments.hasOption(readOption.getArgName())) {
				Properties commandLineProperties = parseProperties(arguments.getOptionValue(readOption.getArgName()));
				if (arguments.hasOption(dryRunOption.getArgName())) {
					//TODO
				}
				else {
					Properties defaultProperties = LoadingProperties.getDefaultProperties();
					Properties readProperties = reconcileProperties(defaultProperties, loadingPropertiesFilename, commandLineProperties);
					System.out.println(readProperties.toString());
				}
				return;
			}
			printHelp();
		}
		catch (ParseException ex) {
			printHelp();
		}
	}
}