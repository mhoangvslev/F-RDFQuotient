package fr.inria.cedar.quotientSummary.controller;

import java.io.FileReader;
import java.io.IOException;
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

public class NewBuilder {
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

	public NewBuilder() {
	}

	private static void setUpCommandLineInterface() {
		loadOption = Option.builder("l")
			.longOpt("load")
			.desc("- load the dataset into database\n[ARGS] must specify a value for dataset.filename\nCAUTION: database is dropped by default") //TODO
			.hasArg(true)
			.argName("[ARGS]")
			.required(false)
			.build();

		summarizeOption = Option.builder("s")
			.longOpt("summarize")
			.desc("- summarize the dataset\n[ARGS] must specify a value for dataset.filename or database.name") //TODO
			.hasArg(true)
			.argName("[ARGS]")
			.required(false)
			.build();

		readOption = Option.builder("r")
			.longOpt("read")
			.desc("- read summary from database\n[ARGS] must specify a value for database.name") //TODO
			.hasArg(true)
			.argName("[ARGS]")
			.required(false)
			.build();

		helpOption = Option.builder("h")
			.longOpt("help")
			.desc("- print help message") //TODO
			.required(false)
			.build();

		versionOption = Option.builder("v")
			.longOpt("version")
			.desc("- print version of RDQQuotient") //TODO
			.required(false)
			.build();

		dryRunOption = Option.builder("d")
			.longOpt("dry-run")
			.desc("- print the configuration used for a run") //TODO
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
			.desc("- set loading properties filename") //TODO
			.hasArg(true)
			.argName("filename")
			.required(false)
			.build();

		summarizationPropertiesOption = Option.builder("sp")
			.longOpt("summarization-properties")
			.desc("- set summarization properties filename") //TODO
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
					version = "";
				}
				System.out.println(version);
				return;
			}

			if (arguments.hasOption(loadingPropertiesOption.getArgName())) {
				//TODO
			}

			if (arguments.hasOption(summarizationPropertiesOption.getArgName())) {
				//TODO
			}

			if (arguments.hasOption(loadOption.getArgName())) {
				//TODO: prepare configuration
				if (arguments.hasOption(dryRunOption.getArgName())) {
					//TODO
				}
				else {
					//TODO
				}
				return;
			}

			if (arguments.hasOption(summarizeOption.getArgName())) {
				//TODO: prepare configuration
				if (arguments.hasOption(dryRunOption.getArgName())) {
					//TODO
				}
				else {
					//TODO
				}
				return;
			}

			if (arguments.hasOption(readOption.getArgName())) {
				//TODO: prepare configuration
				if (arguments.hasOption(dryRunOption.getArgName())) {
					//TODO
				}
				else {
					//TODO
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

/*List<String> datasetSourceFiles = new ArrayList<>();
datasetSourceFiles.add(ntFilePath);

Paremeters datasets = new Parameters();
parameters.setAllInFile(datasetSourceFiles);

Properties loadingPropertiesOption = setUpConfiguration();

DataLoading.process(parameters, properties);

Config configuration = new Config(properties);
Connection databaseConnection = configuration.getDataSource().getConnection();*/