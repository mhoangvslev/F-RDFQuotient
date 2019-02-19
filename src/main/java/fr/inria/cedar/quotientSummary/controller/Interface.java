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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
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
	private static final Logger LOGGER = Logger.getLogger(BuilderCmd.class.getName());
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

	public Interface() {
	}

	private static String trimNT(String fileName, boolean trimSlash) {
		int lastDotPosition = Math.max(0, fileName.lastIndexOf("."));
		return fileName.substring(trimSlash ? fileName.lastIndexOf("/") + 1 : 0, lastDotPosition);
	}

	private static String deriveDatabaseNameFromFilename(String datasetFilename) {
		return PostgresIdentifier.escapeQuotes(trimNT(datasetFilename, true));
	}

	private static void load(Properties loadingProperties, boolean closeConnection) {
		LOGGER.info("Loading graph to Postgres");

		String datasetFilename = loadingProperties.getProperty("dataset.filename");

		// derive database name from filename if not specified
		if (!loadingProperties.containsKey("database.name")) {
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

		if (closeConnection) {
			try {
				databaseConnection.close();
			}
			catch (SQLException ex) {
				LOGGER.error(ex.getMessage());
				System.exit(1);
			}
			databaseConnection = null;
		}

		long saturationTime = (loadingProperties.getProperty("saturation.enable").equals("true")) ? DataLoading.timeExecutionPerProcess.get("RDFGraphSaturator") : 0L;

		// TODO: export statistics

		LOGGER.info("Graph loaded to Postgres");
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

	private static Summary createNewSummary(Properties summarizationProperties) throws IllegalArgumentException {
		String summaryType = summarizationProperties.getProperty("summary.type");
		String triplesFileName = summarizationProperties.getProperty("dataset.filename");
		String triplesTableName = summarizationProperties.getProperty("database.triples_table_name");
		boolean summarizeSaturatedGraph = summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true");
		String encodedTriplesTableName = (summarizeSaturatedGraph) ? summarizationProperties.getProperty("database.encoded_saturated_triples_table_name") : summarizationProperties.getProperty("database.encoded_triples_table_name");
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

	public static void summarize(Properties summarizationProperties, boolean closeConnection) {
		String datasetFilename = summarizationProperties.getProperty("dataset.filename");

		// derive database name from filename if not specified
		if (!summarizationProperties.containsKey("database.name")) {
			String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
			summarizationProperties.put("database.name", databaseName);
		}

		setUpDatabaseConnection(summarizationProperties);

		LOGGER.info("Summarizing graph from Postgres");

		try {
			summary = createNewSummary(summarizationProperties);
			summary.summarizeFromPostgres(databaseConnection);
		}
		catch (IllegalArgumentException ex) {
			LOGGER.error(ex);
			System.exit(1);
		}

		LOGGER.info("Graph from Postgres summarized");

		// TODO: saving graph to Postgres
		// TODO: saving graph to disk
		// TODO: exporting statistics
		// TODO: drawing with DOT
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

	public static void printHelp() {
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

			String loadingPropertiesFileName = LoadingProperties.DEFAULT_LOADING_PROPERTIES_FILE_NAME;
			if (arguments.hasOption(loadingPropertiesOption.getArgName())) {
				loadingPropertiesFileName = arguments.getOptionValue(loadingPropertiesOption.getArgName());
			}

			String summarizationPropertiesFileName = SummarizationProperties.DEFAULT_SUMMARIZATION_PROPERTIES_FILE_NAME;
			if (arguments.hasOption(summarizationPropertiesOption.getArgName())) {
				summarizationPropertiesFileName = arguments.getOptionValue(summarizationPropertiesOption.getArgName());
			}

			if (arguments.hasOption(loadOption.getArgName())) {
				Properties loadingProperties = LoadingProperties.reconcileProperties(loadingPropertiesFileName, arguments.getOptionValue(loadOption.getArgName()));
				if (arguments.hasOption(dryRunOption.getArgName())) {
					load(loadingProperties, true);
				}
				else {
					System.out.println(loadingProperties.toString());
				}
				return;
			}

			if (arguments.hasOption(summarizeOption.getArgName())) {
				Properties summarizationProperties = SummarizationProperties.reconcileProperties(summarizationPropertiesFileName, arguments.getOptionValue(loadOption.getArgName()));
				if (arguments.hasOption(dryRunOption.getArgName())) {
					summarize(summarizationProperties, true);
				}
				else {
					System.out.println(summarizationProperties.toString());
				}
				return;
			}

			if (arguments.hasOption(readOption.getArgName())) {
				Properties readProperties = LoadingProperties.reconcileProperties(loadingPropertiesFileName, arguments.getOptionValue(loadOption.getArgName()));
				if (arguments.hasOption(dryRunOption.getArgName())) {
					//TODO
				}
				else {
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