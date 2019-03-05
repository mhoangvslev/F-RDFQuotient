//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.controller;

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
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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

	private static String currentDateTime() {
		DateFormat localeLongDateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);
		return localeLongDateFormat.format(new Date());
	}

	private static void checkIfDatabaseServerIsRunning(Properties properties) {
		try {
			Class.forName("org.postgresql.Driver");
		}
		catch (ClassNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		String connectionURL = "jdbc:postgresql://";
		try {
			connectionURL += properties.getProperty("database.host")
				+ ":" + properties.getProperty("database.port")
				+ "/?user=" + URLEncoder.encode(properties.getProperty("database.user"), "UTF-8")
				+ "&password=" + URLEncoder.encode(properties.getProperty("database.password"), "UTF-8");
		}
		catch (UnsupportedEncodingException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		Connection connection = null;
		try {
			connection = DriverManager.getConnection(connectionURL);
		}
		catch (SQLException ex) {
			LOGGER.error("Could not establish connection to Postgres server " + ex);
			System.exit(1);
		}

		if (connection == null) {
			LOGGER.error("No connection for " + connectionURL);
			System.exit(1);
		}

		try {
			connection.close();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
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
	private static void setUpDatabaseConnection(Properties properties) {
		try {
			Class.forName("org.postgresql.Driver");
		}
		catch (ClassNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		String connectionURL = "jdbc:postgresql://";
		try {
			connectionURL += properties.getProperty("database.host")
				+ ":" + properties.getProperty("database.port")
				+ "/" + URLEncoder.encode(properties.getProperty("database.name"), "UTF-8")
				+ "?user=" + URLEncoder.encode(properties.getProperty("database.user"), "UTF-8")
				+ "&password=" + URLEncoder.encode(properties.getProperty("database.password"), "UTF-8");
		}
		catch (UnsupportedEncodingException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
		try {
			databaseConnection = DriverManager.getConnection(connectionURL);
		}
		catch (SQLException ex) {
			LOGGER.error("Could not establish connection to Postgres server " + ex);
			System.exit(1);
		}
		LOGGER.info("Connection to Postgres established with URL: " + connectionURL);

		if (databaseConnection == null) {
			LOGGER.error("No connection for " + connectionURL);
			System.exit(1);
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
	public static Connection getOrEstablishNewDatabaseConnection(Properties properties) {
		if (databaseConnection == null) {
			setUpDatabaseConnection(properties);
		}
		return databaseConnection;
	}

	// may return null if database connection was not established or closed
	public static Connection getDatabaseConnection() {
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

	// this is an emergency method if some library misbehaves and doesn't close connection, should not be used otherwise
	public static void forceCloseDatabaseConnection(Properties properties) {
		try {
			Class.forName("org.postgresql.Driver");
		}
		catch (ClassNotFoundException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		String connectionURL = "jdbc:postgresql://";
		try {
			connectionURL += properties.getProperty("database.host")
				+ ":" + properties.getProperty("database.port")
				+ "/?user=" + URLEncoder.encode(properties.getProperty("database.user"), "UTF-8")
				+ "&password=" + URLEncoder.encode(properties.getProperty("database.password"), "UTF-8");
		}
		catch (UnsupportedEncodingException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		Connection connection = null;
		try {
			connection = DriverManager.getConnection(connectionURL);
		}
		catch (SQLException ex) {
			LOGGER.error("Could not establish connection to Postgres server " + ex);
			System.exit(1);
		}
		LOGGER.info("Connection to Postgres established with URL: " + connectionURL);

		if (connection == null) {
			LOGGER.error("No connection for " + connectionURL);
			System.exit(1);
		}

		Statement statement;
		try {
			statement = connection.createStatement();
			String datasetFilename = properties.getProperty("dataset.filename");
			// derive database name from filename if not specified
			if (!properties.containsKey("database.name") || properties.getProperty("database.name").equals("")) {
				String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
				properties.put("database.name", databaseName);
			}
			statement.executeQuery("select pg_terminate_backend(pid) from pg_stat_activity where datname='" + properties.getProperty("database.name") + "'");
			connection.close();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}
	}

	public static String trimExtension(String fileName, boolean trimSlash) {
		int lastDotPosition = Math.max(0, fileName.lastIndexOf("."));
		return fileName.substring(trimSlash ? fileName.lastIndexOf("/") + 1 : 0, lastDotPosition);
	}

	private static String deriveDatabaseNameFromFilename(String datasetFilename) {
		return PostgresIdentifier.escapeQuotes(trimExtension(datasetFilename, true));
	}

	private static void exportLoadingStatisticsToDisk(Properties loadingProperties) {
		String datasetFilename = loadingProperties.getProperty("dataset.filename");
		String csvFilename = trimExtension(datasetFilename, false) + "-loading-statistics.csv";
		LOGGER.info("Loading statistics written to file " + csvFilename);
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
	}

	private static Properties reconcileProperties(Properties defaultProperties, String configurationFilename, Properties commandLineProperties) {
		Properties configurationFileProperties = ConfigurationProperties.getPropertiesFromFile(configurationFilename);
		return ConfigurationProperties.reconcileProperties(ConfigurationProperties.reconcileProperties(defaultProperties, configurationFileProperties), commandLineProperties);
	}

	/*
		If both configurationFilename and properties are null or point to a
		file/object that does not contain valid configuration properties,
		default values will be used.

		Otherwise the following precedure applies:
		1. Take default values
		2. Overwrite them with the values from the file configurationFilename
		3. Overwrite them with the values from the object properties

		Parameter closeConnection controls whether to close connection to the
		database after the call to this method.

		Examples:
		1.
		Properties myProperties = new Properties;
		myProperties.put("dataset.filename", "datasets/my_dataset.nt");
		Interface.load(LoadingProperties.getDefaultLoadingPropertiesFilename(), myProperties, true);

		2.
		Properties myProperties = LoadingProperties.getDefaultProperties();
		myProperties.put("dataset.filename", "datasets/my_dataset.nt");
		Interface.load(null, myProperties, true);

		3.
		Properties myProperties = LoadingProperties.getDefaultProperties();
		myProperties.put("dataset.filename", "datasets/my_dataset.nt");
		Interface.load("conf/my_config_file.properties", myProperties, true);

		Returns a name of the database.
	*/
	public static String load(String configurationFilename, Properties properties, boolean closeConnection) {
		Properties defaultProperties = LoadingProperties.getDefaultProperties();
		Properties loadingProperties = reconcileProperties(defaultProperties, configurationFilename, properties);

		String datasetFilename = loadingProperties.getProperty("dataset.filename");

		// derive database name from filename if not specified
		if (!loadingProperties.containsKey("database.name") || loadingProperties.getProperty("database.name").equals("")) {
			String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
			loadingProperties.put("database.name", databaseName);
		}

		System.out.println("********************************************************************************");
		System.out.println(currentDateTime());
		System.out.println("Executing load operation using "
			+ loadingProperties.getProperty("database.name")
			+ " database with saturation "
			+ (loadingProperties.getProperty("saturation.enable").equals("true") ? "enabled" : "disabled"));
		System.out.println("********************************************************************************");

		// check if loading properties are correct
		checkIfDatabaseServerIsRunning(loadingProperties);
		if (databaseConnection != null) {
			try {
				databaseConnection.close();
			}
			catch (SQLException ex) {
				LOGGER.error("Could not close database connection " + ex);
				System.exit(1);
			}
			databaseConnection = null;
		}

		List<String> datasetSourceFiles = new ArrayList<>();
		datasetSourceFiles.add(datasetFilename);
		Parameters datasets = new Parameters();
		datasets.setAllInFile(datasetSourceFiles);

		try {
			LOGGER.info("Loading graph to Postgres");
			DataLoading.process(datasets, loadingProperties);
			LOGGER.info("Graph loaded to Postgres");
		}
		catch (Exception ex) {
			LOGGER.error("Could not load dataset " + ex);
			System.exit(1);
		}

		if (loadingProperties.getProperty("statistics.export_to_csv_file").equals("true")) {
			LOGGER.info("Exporting loading statistics to disk");
			exportLoadingStatisticsToDisk(loadingProperties);
			LOGGER.info("Loading statistics exported to disk");
		}

		if (!closeConnection) {
			// DataLoading closes connection by default, open if needed
			setUpDatabaseConnection(loadingProperties);
		}

		return loadingProperties.getProperty("database.name");
	}

	private static String checkConsistencyOfSummarizationProperties(Properties summarizationProperties) {
		String message = "";

		if (summarizationProperties.getProperty("summary.replace_type_with_most_general_type").equals("true")) {
			String summaryType = summarizationProperties.getProperty("summary.type");
			String typeGeneralization = "Type generalization is not supported for ";
			switch (summaryType) {
				case "weak":
				case "2pweak":
				case "2pweakunionfind":
					message += typeGeneralization + "weak summaries. ";
					break;
				case "strong":
				case "2pstrong":
					message += typeGeneralization + "strong summaries. ";
					break;
				case "onefb":
				case "onefw":
					message += typeGeneralization + "bisimulation-based summaries. ";
					break;
			}
		}

		if (summarizationProperties.getProperty("summary.export_to_database").equals("false")) {
			String drawingStyle = summarizationProperties.getProperty("drawing.style");
			switch (drawingStyle) {
				case "plain":
				case "split_leaves":
				case "split_and_fold_leaves":
					message += "Exporting summary to database is manadatory in order to draw it.";
					break;
			}
		}

		return message.equals("") ? null : message;
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

	private static void exportSummarizationStatisticsToDisk(Properties summarizationProperties) {
		String datasetFilename = summarizationProperties.getProperty("dataset.filename");
		String csvFileName = trimExtension(datasetFilename, false) + "-summarization-statistics.csv";
		LOGGER.info("Summarization statistics written to CSV file " + csvFileName);
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
	}

	/*
		See load method comment: in examples use SummarizationProperties class instead of LoadingProperties.
		Returns a map with keys: "databaseName", "NTFilename" and "DOTFilename".
		If the corresponding name is not present, the value is set to null.
	*/
	public static HashMap<String, String> summarize(String configurationFilename, Properties properties, boolean closeConnection) {
		Properties defaultProperties = SummarizationProperties.getDefaultProperties();
		Properties summarizationProperties = reconcileProperties(defaultProperties, configurationFilename, properties);

		String datasetFilename = summarizationProperties.getProperty("dataset.filename");

		// derive database name from filename if not specified
		if (!summarizationProperties.containsKey("database.name") || summarizationProperties.getProperty("database.name").equals("")) {
			String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
			summarizationProperties.put("database.name", databaseName);
		}

		String summarizationPropertiesConsistencyStatus = checkConsistencyOfSummarizationProperties(summarizationProperties);
		if (summarizationPropertiesConsistencyStatus != null) {
			LOGGER.error(summarizationPropertiesConsistencyStatus);
			System.exit(1);
		}

		System.out.println("********************************************************************************");
		System.out.println(currentDateTime());
		String message = "Executing summarize operation using "
			+ summarizationProperties.getProperty("database.name")
			+ " database, computing "
			+ summarizationProperties.getProperty("summary.type")
			+ " summary with"
			+ (summarizationProperties.getProperty("summary.replace_type_with_most_general_type").equals("true") ? "" : "out")
			+ " type generalization, on "
			+ (summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true") ? "" : "not ")
			+ "saturated graph";
		String drawingStyle = summarizationProperties.getProperty("drawing.style");
		boolean drawingEnabled = false;
		switch (drawingStyle) {
			case "plain":
			case "split_leaves":
			case "split_and_fold_leaves":
				drawingEnabled = true;
				message += ", drawing visualizations with DOT in " + drawingStyle + " layout";
				break;
			default:
				message += ", no drawing";
				break;
		}
		System.out.println(message);
		System.out.println("********************************************************************************");

		try {
			LOGGER.info("Summarizing graph from Postgres");
			summary = createNewSummary(summarizationProperties);
			summary.setSummarizationProperties(summarizationProperties);
			getOrEstablishNewDatabaseConnection(summarizationProperties);
			summary.summarizeFromPostgres(databaseConnection);
			LOGGER.info("Graph from Postgres summarized");
		}
		catch (IllegalArgumentException ex) {
			LOGGER.error(ex);
			System.out.println("Make sure that the input graph is loaded into database.");
			System.exit(1);
		}

		summarySavingInPostgresTime = 0L;
		if (summarizationProperties.getProperty("summary.export_to_database").equals("true")) {
			LOGGER.info("Exporting summary to Postgres");
			long start = System.currentTimeMillis();
			summary.saveSummaryInPostgres(databaseConnection);
			summarySavingInPostgresTime = System.currentTimeMillis() - start;
			LOGGER.info("Summary exported to Postgres");
		}

		String NTFilename = null;
		summarySavingToDiskTime = 0L;
		if (summarizationProperties.getProperty("summary.export_to_nt_file").equals("true")) {
			LOGGER.info("Exporting summary to disk to NT file");
			long start = System.currentTimeMillis();
			NTFilename = summary.writeDecodedSummaryToNTFile(databaseConnection);
			summarySavingToDiskTime = System.currentTimeMillis() - start;
			LOGGER.info("Summary NT file exported to disk");
		}

		if (summarizationProperties.getProperty("statistics.export_to_csv_file").equals("true")) {
			LOGGER.info("Exporting summarization statistics to disk");
			exportSummarizationStatisticsToDisk(summarizationProperties);
			LOGGER.info("Summarization statistics exported to disk");
		}

		String DOTFilename = null;
		if (drawingEnabled) {
			LOGGER.info("Exporting summary DOT drawing to disk");
			DOTFilename = summary.writeDecodedSummaryToDOTFile(databaseConnection, drawingStyle);
			LOGGER.info("Summary DOT drawing exported to disk");
		}

		if (closeConnection) {
			closeDatabaseConnection();
		}

		HashMap<String, String> names = new HashMap<>();
		names.put("databaseName", summarizationProperties.getProperty("database.name"));
		names.put("NTFilename", NTFilename);
		names.put("DOTFilename", DOTFilename);

		return names;
	}

	/*
		See load method comment.
		Returns Summary object.
	*/
	public static Summary read(String configurationFilename, Properties properties, boolean closeConnection) {
		Properties defaultProperties = SummarizationProperties.getDefaultProperties();
		Properties readingProperties = reconcileProperties(defaultProperties, configurationFilename, properties);

		String datasetFilename = readingProperties.getProperty("dataset.filename");

		// derive database name from filename if not specified
		if (!readingProperties.containsKey("database.name") || readingProperties.getProperty("database.name").equals("")) {
			String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
			readingProperties.put("database.name", databaseName);
		}

		System.out.println("********************************************************************************");
		System.out.println(currentDateTime());
		System.out.println("Executing read operation using "
			+ readingProperties.getProperty("database.name")
			+ " database");
		System.out.println("********************************************************************************");

		try {
			LOGGER.info("Reading summary from Postgres");
			Summary s = new Summary(getOrEstablishNewDatabaseConnection(readingProperties));
			LOGGER.info("Summary read from Postgres");
			return s;
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not read summary " + e.toString());
		}
		finally {
			if (closeConnection) {
				closeDatabaseConnection();
			}
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

		final OptionGroup dryRunOptions = new OptionGroup();
		dryRunOptions.addOption(dryRunOption);

		options = new Options();
		options.addOptionGroup(mainOptions);
		options.addOptionGroup(configurationFilesOptions);
		options.addOptionGroup(dryRunOptions);
	}

	private static void printHelp() {
		String version;
		MavenXpp3Reader reader = new MavenXpp3Reader();
		try {
			Model model = reader.read(new FileReader("pom.xml"));
			version = model.getVersion();
		}
		catch(Exception ex) {
			version = "";
		}
		final HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.setWidth(80);
		helpFormatter.setLeftPadding(0);
		String header = "RDFQuotient " + version
			+ "\n\nThis framework is designed to work with one graph at a time.\n"
			+ "Before using RDFQuotient make sure that Postgres server is running.\n"
			+ "Input RDF dataset file format is N-Triples and the file is assumed not to\n"
			+ "contain any duplicated triples.\n\n"
			+ "rdfquotient";
		String footer = "\n[ARGS] is a comma-separated list of assigments of form key=value, where key is a configuration property from the list of loading or summarization configuration properties.";
		helpFormatter.printHelp(header, "\n", options, footer, true);
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

			if (arguments.hasOption(helpOption.getLongOpt())) {
				printHelp();
				return;
			}

			if (arguments.hasOption(versionOption.getLongOpt())) {
				String version;
				MavenXpp3Reader reader = new MavenXpp3Reader();
				try {
					Model model = reader.read(new FileReader("pom.xml"));
					version = model.getVersion();
				}
				catch(Exception ex) {
					version = "unknown";
				}
				System.out.println("RDFQuotient version: " + version);
				return;
			}

			String loadingPropertiesFilename = LoadingProperties.DEFAULT_LOADING_PROPERTIES_FILENAME;
			if (arguments.hasOption(loadingPropertiesOption.getLongOpt())) {
				loadingPropertiesFilename = arguments.getOptionValue(loadingPropertiesOption.getLongOpt());
			}

			String summarizationPropertiesFilename = SummarizationProperties.DEFAULT_SUMMARIZATION_PROPERTIES_FILENAME;
			if (arguments.hasOption(summarizationPropertiesOption.getLongOpt())) {
				summarizationPropertiesFilename = arguments.getOptionValue(summarizationPropertiesOption.getLongOpt());
			}

			if (arguments.hasOption(loadOption.getLongOpt())) {
				Properties commandLineProperties = parseProperties(arguments.getOptionValue(loadOption.getLongOpt()));
				if (arguments.hasOption(dryRunOption.getLongOpt())) {
					Properties defaultProperties = LoadingProperties.getDefaultProperties();
					Properties loadingProperties = reconcileProperties(defaultProperties, loadingPropertiesFilename, commandLineProperties);
					// derive database name from filename if not specified
					if (!loadingProperties.containsKey("database.name") || loadingProperties.getProperty("database.name").equals("")) {
						String datasetFilename = loadingProperties.getProperty("dataset.filename");
						String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
						loadingProperties.put("database.name", databaseName);
					}
					loadingProperties.remove("database.engine");
					System.out.println(ConfigurationProperties.prettifiedToString(loadingProperties));
					if (loadingProperties.getProperty("database.drop_existing_db").equals("true")) {
						System.out.println("\nCAUTION: database " + loadingProperties.getProperty("database.name") + " will be dropped before loading.");
					}
				}
				else {
					load(loadingPropertiesFilename, commandLineProperties, true);
				}
				return;
			}

			if (arguments.hasOption(summarizeOption.getLongOpt())) {
				Properties commandLineProperties = parseProperties(arguments.getOptionValue(summarizeOption.getLongOpt()));
				if (arguments.hasOption(dryRunOption.getLongOpt())) {
					Properties defaultProperties = SummarizationProperties.getDefaultProperties();
					Properties summarizationProperties = reconcileProperties(defaultProperties, summarizationPropertiesFilename, commandLineProperties);
					// derive database name from filename if not specified
					if (!summarizationProperties.containsKey("database.name") || summarizationProperties.getProperty("database.name").equals("")) {
						String datasetFilename = summarizationProperties.getProperty("dataset.filename");
						String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
						summarizationProperties.put("database.name", databaseName);
					}
					System.out.println(ConfigurationProperties.prettifiedToString(summarizationProperties));
				}
				else {
					summarize(summarizationPropertiesFilename, commandLineProperties, true);
				}
				return;
			}

			if (arguments.hasOption(readOption.getLongOpt())) {
				Properties commandLineProperties = parseProperties(arguments.getOptionValue(readOption.getLongOpt()));
				if (arguments.hasOption(dryRunOption.getLongOpt())) {
					Properties defaultProperties = LoadingProperties.getDefaultProperties();
					Properties readingProperties = reconcileProperties(defaultProperties, loadingPropertiesFilename, commandLineProperties);
					// derive database name from filename if not specified
					if (!readingProperties.containsKey("database.name") || readingProperties.getProperty("database.name").equals("")) {
						String datasetFilename = readingProperties.getProperty("dataset.filename");
						String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
						readingProperties.put("database.name", databaseName);
					}
					readingProperties.remove("database.engine");
					System.out.println(ConfigurationProperties.prettifiedToString(readingProperties));
				}
				else {
					Summary s = read(loadingPropertiesFilename, commandLineProperties, true);
				}
				System.out.println("\nThis operation does not have any effect in command line mode; it should be used in a programmatic way in Java code.");
				return;
			}
			printHelp();
		}
		catch (ParseException ex) {
			printHelp();
		}
	}

	// convenience methods
	public static HashMap<String, String> loadAndSummarize(Properties loadingProperties, Properties summarizationProperties) {
		load(null, loadingProperties, false);
		HashMap<String, String> names = summarize(null, summarizationProperties, true);

		return names;
	}

	public static HashMap<String, String> loadAndSummarizeThroughShortcut(Properties loadingProperties, Properties summarizationProperties) {
		String summaryType = summarizationProperties.getProperty("summary.type");
		switch (summaryType) {
			case "typedweak":
			case "2ptypedweak":
			case "typedstrong":
			case "2ptypedstrong":
				throw new IllegalArgumentException("No shortcut for typed summaries");
		}

		loadingProperties.put("saturation.enable", "false");
		load(null, loadingProperties, false);
		summarizationProperties.put("summary.summarize_saturated_graph", "false");
		HashMap<String, String> names = summarize(null, summarizationProperties, true);

		loadingProperties.put("saturation.enable", "true");
		loadingProperties.put("database.name", "");
		loadingProperties.put("dataset.filename", names.get("NTFilename"));
		summarizationProperties.put("database.name", "");
		summarizationProperties.put("dataset.filename", names.get("NTFilename"));
		summarizationProperties.put("summary.summarize_saturated_graph", "true");
		load(null, loadingProperties, false);
		names = summarize(null, summarizationProperties, true);

		return names;
	}
}