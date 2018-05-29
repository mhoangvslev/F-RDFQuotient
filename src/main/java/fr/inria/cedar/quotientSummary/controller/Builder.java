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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
	private static String triplesTableName;
	private static String dictionaryTableName;
	private static String encodedTableName;
	private static String encodedSaturatedTableName;
	private static Connection connectionInUse;
	private static Summary summaryInUse;
	private static long saturationTime;

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
		if (args.length < 1) {
			printUsage();
			return;
		}
		String arg0 = args[0];
		String arg1 = "";
		String[] filesToLoad = {};

		if (args.length > 1) {
			arg1 = args[1];
			if (args.length > 2) {
				filesToLoad = extractArguments(args, 2);
			}
		}

		switch (arg0) {
			case "loadWithoutSaturation":
				connectionInUse = loadGraphInPostgres(false, false, filesToLoad);
				return;
			case "loadWithSaturation":
				connectionInUse = loadGraphInPostgres(true, false, filesToLoad);
				return;
			case "summarizeUnsaturated":
				summaryInUse = summarizeGraphFromPostgres(arg1, false, filesToLoad);
				return;
			case "summarizeSaturated":
				summaryInUse = summarizeGraphFromPostgres(arg1, true, filesToLoad);
				return;
			case "loadAndSummarize":
				connectionInUse = loadGraphInPostgres(false, false, filesToLoad);
				summaryInUse = summarizeGraphFromPostgres(arg1, false, filesToLoad);
				return;
			case "loadWithSaturationAndSummarize":
				connectionInUse = loadGraphInPostgres(true, false, filesToLoad);
				summaryInUse = summarizeGraphFromPostgres(arg1, true, filesToLoad);
				return;
			case "loadAndSummarizeUsingShortcut":
				connectionInUse = loadGraphInPostgres(false, false, filesToLoad);
				summaryInUse = summarizeGraphFromPostgres(arg1, false, filesToLoad);
				saveSummary(Boolean.TRUE, "shortcut");
				exportSummary("noSaturation", false, filesToLoad);
				closeConnection();
				String[] files = {filesToLoad[0].substring(0, filesToLoad[0].length() - 3) + "_" + prefix(arg1) + "noSaturation.nt"};
				connectionInUse = loadGraphInPostgres(true, true, files);
				summaryInUse = summarizeGraphFromPostgres(arg1, true, filesToLoad);
				return;
			case "saveSummaryComputedWithoutSaturation":
				saveSummary(Boolean.FALSE, "noSaturation");
				return;
			case "saveSummaryComputedClassicalWay":
				saveSummary(Boolean.FALSE, "classical");
				return;
			case "saveSummaryComputedUsingShortcut":
				saveSummary(Boolean.FALSE, "shortcut");
				return;
			case "exportSummaryComputedWithoutSaturation":
				exportSummary("noSaturation", arg1.equals("draw"), filesToLoad);
				return;
			case "exportSummaryComputedClassicalWay":
				exportSummary("classical", arg1.equals("draw"), filesToLoad);
				return;
			case "exportSummaryComputedUsingShortcut":
				exportSummary("shortcut", arg1.equals("draw"), filesToLoad);
				return;
			case "dropPartialResultsTables":
				dropPartialResultsTables();
				return;
			case "closeConnection":
				closeConnection();
				return;
			case "readSummaryComputedWithoutSaturation":
				Summary s = readSummaryFromPostgres(); 
				return;
			default:
				break;
		}
		printUsage();
	}

	private static String prefix(String summarizationTechnique) {
		switch(summarizationTechnique) {
			case "weak":
				return "w_";
			case "strong":
				return "s_";
			case "typedweak":
				return "tw_";
			case "typedstrong":
				return "ts_";
		}
		return null;
	}

	private static void printUsage() {
		System.out.println("The framework is designed to work with one graph at the time. Tables created until save are to be considered temporary.");
		System.out.println("Usage:");
		System.out.println("args[0]=loadWithoutSaturation: opens connection and loads the graph in Postgres without saturating it");
		System.out.println("args[0]=loadWithSaturation: opens connection and loads the graph in Postgres and saturates it");
		System.out.println("args[0]=summarizeUnsaturated: summarizes the unsaturated graph from Postgres");
		System.out.println("args[0]=summarizeSaturated: summarizes the saturated graph from Postgres");
		System.out.println("args[0]=loadAndSummarize: loads the graph in Postgres, and summarizes it");
		System.out.println("args[0]=loadWithSaturationAndSummarize: loads the graph in Postgres, saturates it, and summarizes it");
		System.out.println("args[0]=loadAndSummarizeUsingShortcut: loads the graph in Postgres, summarizes it, saturates it, and summarizes again (shortcut)");
		//System.out.println("args[0]=summarizeEncodedFile: build the summary out of integer-encoded triples in a file");
		System.out.println("args[0]=saveSummaryComputedWithoutSaturation: saves summary computed using only saturation to Postgres");
		System.out.println("args[0]=saveSummaryComputedClassicalWay: saves summary computed classical way to Postgres");
		System.out.println("args[0]=saveSummaryComputedUsingShortcut: saves summary computed using shortcut to Postgres");
		System.out.println("args[0]=exportSummaryComputedWithoutSaturation: saves summary computed using only saturation to the disk in nt, dot and png formats");
		System.out.println("args[0]=exportSummaryComputedClassicalWay: saves summaryy computed classical way to the disk in nt, dot and png formats");
		System.out.println("args[0]=exportSummaryComputedUsingShortcut: saves summary summary computed using only saturation to the disk in nt, dot and png formats");
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
	private static String[] extractArguments(String[] args, int shift) {
		if (shift < 1)
			throw new IllegalArgumentException("Shift must be at least 1");
		if (shift >= args.length)
			throw new IllegalArgumentException("Shift must be smaller than the args array size");
		String[] suffix = new String[args.length - shift];
		for (int i = 0; i < suffix.length; i++)
			suffix[i] = args[i + shift];
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
	private static Connection loadGraphInPostgres(Boolean saturate, Boolean shortcut, String[] files) throws FileNotFoundException, IOException, UnsupportedDatabaseEngineException, SQLException {
		LOGGER.info("Loading graph to Postgres");
		//LOGGER.debug(System.getProperty("user.dir"));

		List<String> tripleFiles = new ArrayList<>();
		List<String> rdfsFiles = new ArrayList<>();

		int fileNo;
		for (fileNo = 0; fileNo < files.length; fileNo++) {
			String s = files[fileNo];
			if ((fileNo == 0) || ((files.length > 1) && (fileNo < files.length - 1))) {
				//LOGGER.debug("Triple file: " + s);
				tripleFiles.add(s);
			}
			else {
				//LOGGER.debug("Schema file: " + s);
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
		//LOGGER.debug(properties.toString());
		triplesTableName = properties.getProperty("database.triples_table_name");
		dictionaryTableName = properties.getProperty("database.dictionary_table_name");
		encodedTableName = properties.getProperty("database.encoded_triples_table_name");
		encodedSaturatedTableName = properties.getProperty("database.encoded_saturated_triples_table_name");
		Parameters settings = new Parameters();
		settings.setPropertiesFileName(configFile);

		try {
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
		}
		catch (IOException ex) {
			LOGGER.error("Data loading failed: " + ex);
			throw ex;
		}
		LOGGER.info("Graph loaded to Postgres");

		saturationTime = (saturate) ? DataLoading.timeExecutionPerProcess.get("RDFGraphSaturator") : 0L;

		Properties connectionProps = new Properties();
		connectionProps.put("user", properties.getProperty("database.user"));
		connectionProps.put("password", properties.getProperty("database.password"));

		String connectionURL = "jdbc:postgresql://" + properties.getProperty("database.host") + ":" + 
			properties.getProperty("database.port") + "/" + properties.getProperty("database.name");
		Connection conn = DriverManager.getConnection(connectionURL, connectionProps);
		LOGGER.info("Connection to Postgres established with URL: " + connectionURL + " with user " + 
			properties.getProperty("database.user") + " and password " + properties.getProperty("database.password")); 
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
	private static Summary summarizeGraphFromPostgres(String summaryType, Boolean summarizeSaturated, String[] files) throws SQLException, IOException {
		LOGGER.info("Summarizing graph from Postgres");
		Summary sum = createNewSummary(summaryType, files[0], triplesTableName, tableName(summarizeSaturated), dictionaryTableName);
		sum.summarizeFromPostgres(connectionInUse);
		LOGGER.info("Graph from Postgres summarized");
		return sum;
	}

	private static Summary createNewSummary(String summaryType, String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		String lowerCaseSummaryType = summaryType.toLowerCase();
		switch (lowerCaseSummaryType) {
			case "weak":
				return new WeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "strong":
				return new StrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "typedweak":
				return new TypedWeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
			case "typedstrong":
				return new TypedStrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
		}
		return null;
	}

	private static String tableName(Boolean saturated) {
		if (!saturated)
			return encodedTableName;

		try {
			ResultSet res = connectionInUse.getMetaData().getTables(null, null, encodedTableName + "_summarized_saturated", new String[] { "TABLE" });
			if(res.next()) // if tmp_encoded_summarized_saturated exists
				return encodedTableName + "_summarized_saturated";
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not find out if table " + encodedTableName + "_summarized_saturated" + " exists: " + e.toString());
		}

		return encodedSaturatedTableName;
	}

	// This goes toward the needs of the projects which
	// use the summaries we build (and read them from
	// Postgres)
	// TODO decide on the final form this should take
	public static Summary readSummaryFromPostgres() {
		try {
			Summary s = new Summary(getConnection()); // leave it like this (call getConnection to ensure it is opened)
			LOGGER.info("Summary read from Postgres");
			return s;
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not read summary " + e.toString());
		}
	}

	// prop allows to override the properties that the Builder may already have, 
	// in particular to dictate it some connection parameters. 
	// TODO 
	public static Summary readSummaryFromPostgres(Properties prop) {
		try {
			Summary s = new Summary(getConnection()); // leave it like this (call getConnection to ensure it is opened)
			LOGGER.info("Summary read from Postgres");
			return s;
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not read summary " + e.toString());
		}
	}

	private static void saveSummary(Boolean partialResult, String summarizationTechnique) {
		summaryInUse.saveSummaryInPostgres(connectionInUse, partialResult, summarizationTechnique);
	}

	private static void exportSummary(String summarizationTechnique, Boolean draw, String[] files) throws FileNotFoundException {
		LOGGER.info("Exporting summary to disk");

		summaryInUse.writeDecodedSummaryToNTFile(connectionInUse, summarizationTechnique);
		if (draw)
			summaryInUse.drawSummaryAndGraph(connectionInUse, summarizationTechnique);

		int lastDotPosition = Math.max(0, files[0].lastIndexOf("."));
		String csvFileName = files[0].substring(0, lastDotPosition) + "_" + summaryInUse.getSummaryURIPrefix() + "_" + summarizationTechnique + "-statistics.csv";
		try (PrintWriter pw = new PrintWriter(new File(csvFileName))) {
			HashMap<String, Long> statistics = summaryInUse.getRunStatistics();
			StringBuilder sb = new StringBuilder();
			ArrayList<String> keys = new ArrayList<>();
			keys.addAll(statistics.keySet());
			Collections.sort(keys);
			for (String key: keys) {
				sb.append(key).append(',');
			}
			sb.append("saturationTime");
			sb.append('\n');
			for (String key: keys) {
				sb.append(statistics.get(key)).append(',');
			}
			sb.append(saturationTime);
			sb.append('\n');
			pw.write(sb.toString());
		}
		LOGGER.info("Statistics saved in " + csvFileName);
		LOGGER.info("Summary exported to disk");
	}

	private static void closeConnection() throws SQLException {
		connectionInUse.close();
		connectionInUse = null;
		LOGGER.info("Connection closed");
	}

	/**
	 * opens the connexion if not already done
	 * @return
	 */
	public static Connection getConnection() {
		if (connectionInUse == null){
			 getConnection(DEFAULT_CONFIG_FILE); 
		}
		return connectionInUse;
	}

	// encapsulates the work to get a connection
	// based on the properties specified in configfile
	// call it with different config files to control which set of
	// properties to use 
	private static void getConnection(String configFile) {
		Properties properties = new Properties();
		try {
			properties.load(new FileReader(configFile));
		}
		catch(IOException e) {
			throw new IllegalStateException("Could not initialize properties " + e.toString());
		}
		Properties connectionProps = new Properties();
		connectionProps.put("user", properties.getProperty("database.user").trim());
		connectionProps.put("password", properties.getProperty("database.password").trim());

		String connectionURL = "jdbc:postgresql://" + properties.getProperty("database.host") + ":" + 
				properties.getProperty("database.port") + "/" + properties.getProperty("database.name");
		try {
			connectionInUse = DriverManager.getConnection(connectionURL, connectionProps);
			// https://docs.oracle.com/javase/8/docs/api/java/sql/DriverManager.html#getConnection-java.lang.String-java.util.Properties-
			// states that if connectionURL and connectionProps disagree on the host, port or database name,
			// the one actually considered is implementation (driver) dependent. 
			// It is important not to allow the values of the two parameters to diverge. 
			LOGGER.info("Connection to Postgres established with URL: " + connectionURL);
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not open connection to " + connectionURL); 
		}
	}

	// gets a connection with parameters taken from the config file *and then* overriden by the custom properties
	private static void getConnection(String configFile, Properties customProp){
		Properties properties = new Properties();
		try {
			properties.load(new FileReader(configFile));
		}
		catch(IOException e) {
			throw new IllegalStateException("Could not initialize properties " + e.toString());
		}
		Properties connectionProps = new Properties();

		String databaseName = properties.getProperty("database.name").trim();
		String host = properties.getProperty("database.host").trim();
		String port = properties.getProperty("database.port").trim();
		String user = properties.getProperty("database.user").trim();
		String password = properties.getProperty("database.password").trim();

		connectionProps.put("user", user);
		connectionProps.put("password", password);

		String cDatabaseName = customProp.getProperty("database.name").trim();
		String cHost = customProp.getProperty("database.host").trim();
		String cPort = customProp.getProperty("database.port").trim();
		String cUser = customProp.getProperty("database.user").trim();
		String cPassword = customProp.getProperty("database.password").trim();

		if (cDatabaseName != null && cDatabaseName.length() > 0) {
			connectionProps.put("database", cDatabaseName);  
			databaseName = cDatabaseName; 
		}
		if (cHost != null && cHost.length() > 0) {
			connectionProps.put("host", cHost); 
			host = cHost; 
		}
		if (cPort != null && cPort.length() > 0) {
			connectionProps.put("port", cPort); 
			port = cPort; 
		}
		if (cUser != null && cUser.length() > 0) {
			connectionProps.put("user", cUser); 
			user = cUser; 
		}
		if (cPassword != null && cPassword.length() > 1) {
			connectionProps.put("password", cPassword); 
			password = cPassword; 
		}
		String connectionURL = "jdbc:postgresql://" + host + ":" + 	port + "/" + databaseName;
		try {
			// https://docs.oracle.com/javase/8/docs/api/java/sql/DriverManager.html#getConnection-java.lang.String-java.util.Properties-
			// states that if connectionURL and connectionProps disagree on the host, port or database name,
			// the one actually considered is implementation (driver) dependent. 
			// It is important not to allow the values of the two parameters to diverge. 
			connectionInUse = DriverManager.getConnection(connectionURL, connectionProps);
			LOGGER.info("Connection to Postgres established with URL: " + connectionURL  + " and " +  connectionProps.toString());
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not open connection to " + connectionURL + " and " + connectionProps.toString()); 
		}
	}

	public static void getConnection(Properties customProp){
		getConnection(DEFAULT_CONFIG_FILE, customProp); 
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
			stmt.execute("select 'drop table '||tablename||';' from pg_tables where tablename like 'tmp_%'");
			connectionInUse.commit();
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not drop partial results tables: " + e.toString());
		}

		LOGGER.info("All partial results tables dropped");
	}
}
