//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.controller;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.bisim.ForwardBackwardBisimulationSummary;
import fr.inria.cedar.RDFQuotient.bisim.OneBisimSummary;
import fr.inria.cedar.RDFQuotient.bisim.OneFWSummary;
import fr.inria.cedar.RDFQuotient.dataAndType.InputOutputAndTypedSummary;
import fr.inria.cedar.RDFQuotient.dataAndType.TypedSummary;
import fr.inria.cedar.RDFQuotient.strong.StrongSummary;
import fr.inria.cedar.RDFQuotient.strong.TwoPassStrongSummary;
import fr.inria.cedar.RDFQuotient.strong.TwoPassTypedStrongSummary;
import fr.inria.cedar.RDFQuotient.strong.TypedStrongSummary;
import fr.inria.cedar.RDFQuotient.util.PostgresIdentifier;
import fr.inria.cedar.RDFQuotient.weak.*;
import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.ontosql.rdfdb.dataloading.DataLoading;
import fr.inria.cedar.ontosql.rdfdb.dataloading.Parameters;
import org.apache.commons.cli.*;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.*;

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

//    static {
//        LOGGER.setLevel(Level.INFO);
//    }

    public Interface() {
    }

    private static String currentDateTime() {
        LOGGER.info("START currentDateTime");
        DateFormat localeLongDateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);
        LOGGER.info("END currentDateTime");
        return localeLongDateFormat.format(new Date());
    }

    private static void checkIfDatabaseServerIsRunning(Properties properties) {
        LOGGER.info("START checkIfDatabaseServerIsRunning");
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
        LOGGER.info("END checkIfDatabaseServerIsRunning");
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
        LOGGER.info("START setUpDatabaseConnection");
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
        LOGGER.info("END setUpDatabaseConnection");
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
        LOGGER.info("START getOrEstablishNewDatabaseConnection");
        if (databaseConnection == null) {
            setUpDatabaseConnection(properties);
        }
        LOGGER.info("END getOrEstablishNewDatabaseConnection");
        return databaseConnection;
    }

    // may return null if database connection was not established or closed
    public static Connection getDatabaseConnection() {
        return databaseConnection;
    }

    public static void closeDatabaseConnection() {
        LOGGER.info("START closeDatabaseConnection");
        try {
            databaseConnection.close();
            databaseConnection = null;
            LOGGER.info("Connection closed");
        }
        catch (SQLException ex) {
            LOGGER.error(ex);
            System.exit(1);
        }
        LOGGER.info("END closeDatabaseConnection");
    }

    // this is an emergency method if some library misbehaves and doesn't close connection, should not be used otherwise
    public static void forceCloseDatabaseConnection(Properties properties) {
        LOGGER.info("START forceCloseDatabaseConnection");
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
        LOGGER.info("END forceCloseDatabaseConnection");
    }

    public static String trimExtension(String fileName, boolean trimSlash) {
        LOGGER.info("START trimExtension");
        int lastDotPosition = Math.max(0, fileName.lastIndexOf("."));
        String separator = System.getProperty("file.separator");
        if(File.separator.equals("\\")) {
            fileName = fileName.replaceAll("\\/", "\\\\");
        }
        LOGGER.info("END trimExtension");
        return fileName.substring(trimSlash ? fileName.lastIndexOf(separator) + 1 : 0, lastDotPosition);
    }

    private static String deriveDatabaseNameFromFilename(String datasetFilename) {
        return PostgresIdentifier.escapeQuotes(trimExtension(datasetFilename, true));
    }

    private static void exportLoadingStatisticsToDisk(Properties loadingProperties) {
        LOGGER.info("START exportLoadingStatisticsToDisk");
        String datasetFilename = loadingProperties.getProperty("dataset.filename");
        String csvFilename = trimExtension(datasetFilename, false) + "-loading-statistics.csv";
        LOGGER.info("Loading statistics written to file " + csvFilename);
        long loadingTime = DataLoading.timeExecutionPerProcess.get("LoadTriplesToDatabase");
        long saturationTime = (!loadingProperties.getProperty("saturation.type").equals("NONE")) ? DataLoading.timeExecutionPerProcess.get("RDFGraphSaturator") : 0L;
        try (PrintWriter pw = new PrintWriter(csvFilename)) {
            String sb = "loadingTime,saturationTime\n" +
                    loadingTime + ',' + saturationTime + '\n';
            pw.write(sb);
        }
        catch (FileNotFoundException ex) {
            LOGGER.error(ex);
            System.exit(1);
        }
        LOGGER.info("END exportLoadingStatisticsToDisk");
    }

    private static void exportLoadingConfigurationToDisk(Properties loadingProperties) {
        String datasetFilename = loadingProperties.getProperty("dataset.filename");
        String propertiesFilename = trimExtension(datasetFilename, false) + "-loading-configuration.properties";
        LoadingProperties.writePropertiesFile(loadingProperties, propertiesFilename);
    }

    private static Properties reconcileProperties(Properties defaultProperties, String configurationFilename, Properties commandLineProperties) {
        Properties configurationFileProperties = ConfigurationProperties.getPropertiesFromFile(configurationFilename);
        return ConfigurationProperties.reconcileProperties(ConfigurationProperties.reconcileProperties(defaultProperties, configurationFileProperties), commandLineProperties);
    }

    private static boolean checkIfFileExists(String filename) {
        File fileToCheck = new File(filename);
        return fileToCheck.exists() && fileToCheck.isFile();
    }

    /*
        If both configurationFilename and properties are null or point to a
        file/object that does not contain valid configuration properties,
        default values will be used.

        Otherwise the following procedure applies:
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
    public static void load(String configurationFilename, Properties properties, boolean closeConnection) {
        LOGGER.info("START load");
        Properties defaultProperties = LoadingProperties.getDefaultProperties();
        Properties loadingProperties = reconcileProperties(defaultProperties, configurationFilename, properties);

        String datasetFilename = loadingProperties.getProperty("dataset.filename");
        if (!checkIfFileExists(datasetFilename)) {
            LOGGER.error("File " + datasetFilename + " does not exist.");
            System.exit(1);
        }

        // derive database name from filename if not specified
        if (!loadingProperties.containsKey("database.name") || loadingProperties.getProperty("database.name").equals("")) {
            String databaseName = deriveDatabaseNameFromFilename(datasetFilename);
            loadingProperties.put("database.name", databaseName);
        }

        System.out.println("********************************************************************************");
        System.out.println(currentDateTime());
        System.out.println("Executing the load operation using "
                + loadingProperties.getProperty("database.name")
                + " database with saturation "
                + (loadingProperties.getProperty("saturation.type").equals("NONE") ? "disabled" : "enabled"));
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
            LOGGER.info("Loading graph to database");
            DataLoading.process(datasets, loadingProperties);
            LOGGER.info("Graph loaded to database");
        }
        catch (UnsupportedDatabaseEngineException | FileNotFoundException ex) {
            LOGGER.error("Could not load dataset " + ex);
            System.exit(1);
        }

        if (loadingProperties.getProperty("statistics.export_to_csv_file").equals("true")) {
            LOGGER.info("Exporting loading statistics to disk");
            exportLoadingStatisticsToDisk(loadingProperties);
            LOGGER.info("Loading statistics exported to disk");
        }

        if (loadingProperties.getProperty("configuration.export_to_disk").equals("true")) {
            LOGGER.info("Exporting loading configuration to disk");
            exportLoadingConfigurationToDisk(loadingProperties);
            LOGGER.info("Loading configuration exported to disk");
        }

        if (!closeConnection) {
            // DataLoading closes connection by default, open if needed
            setUpDatabaseConnection(loadingProperties);
        }

        loadingProperties.getProperty("database.name");
        LOGGER.info("END load");
    }

    private static String checkConsistencyOfSummarizationProperties(Properties summarizationProperties) {
        LOGGER.info("START checkConsistencyOfSummarizationProperties");
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
                case "2ponefb":
                case "2ponefw":
                case "2pbisim":
                    message += typeGeneralization + "bisimulation-based summaries. ";
                    break;
                case "typed":
                case "2pinputoutput":
                    message += typeGeneralization + "data-and-type summaries. ";
                    break;
            }
        }

        if (summarizationProperties.getProperty("summary.export_to_database").equals("false")) {
            String drawingStyle = summarizationProperties.getProperty("drawing.style");
            switch (drawingStyle) {
                case "plain":
                case "split_leaves":
                case "split_and_fold_leaves":
                    message += "Exporting summary to database is mandatory in order to draw it.";
                    break;
            }
        }

        LOGGER.info("END checkConsistencyOfSummarizationProperties");
        return message.equals("") ? null : message;
    }

    private static Summary createNewSummary(Properties summarizationProperties) throws IllegalArgumentException {
        LOGGER.info("START createNewSummary");
        String summaryType = summarizationProperties.getProperty("summary.type");
        String triplesFileName = summarizationProperties.getProperty("dataset.filename");
        String triplesTableName = summarizationProperties.getProperty("database.triples_table_name");
        boolean summarizeSaturatedGraph = summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true");
        String encodedTriplesTableName = summarizeSaturatedGraph ? summarizationProperties.getProperty("database.encoded_saturated_triples_table_name") : summarizationProperties.getProperty("database.encoded_triples_table_name");
        String dictionaryTableName = summarizationProperties.getProperty("database.dictionary_table_name");
        switch (summaryType) {
            case "weak":
                return new WeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "strong":
                return new StrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "typedweak":
                return new TypedWeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "typedstrong":
                return new TypedStrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2pweak":
                return new TwoPassWeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2pweakunionfind":
                return new TwoPassWeakSummaryWithUnionFind(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2pstrong":
                return new TwoPassStrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2ptypedweak":
                return new TwoPassTypedWeakSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2ptypedstrong":
                return new TwoPassTypedStrongSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "typed":
                return new TypedSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2ponefw":
                return new OneFWSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2ponefb":
                return new OneBisimSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2pinputoutput":
                return new InputOutputAndTypedSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
            case "2pbisim":
                return new ForwardBackwardBisimulationSummary(triplesFileName, triplesTableName, encodedTriplesTableName, dictionaryTableName);
        }
        LOGGER.info("END createNewSummary");
        throw new IllegalArgumentException("Wrong summary identifier: " + summaryType);
    }

    private static void exportSummarizationStatisticsToDisk(Properties summarizationProperties, String summaryNTFilename) {
        LOGGER.info("START exportSummarizationStatisticsToDisk");
        String csvFileName = trimExtension(summaryNTFilename, false) + "-summarization-statistics.csv";
        LOGGER.info("Summarization statistics written to CSV file " + csvFileName);
        try (PrintWriter pw = new PrintWriter(csvFileName)) {
            HashMap<String, String> statistics = summary.getRunStatistics();
            statistics.put("summarySavingInPostgresTime", Long.toString(summarySavingInPostgresTime));
            statistics.put("summarySavingToDiskTime", Long.toString(summarySavingToDiskTime));
            StringBuilder sb = new StringBuilder();
            ArrayList<String> keys = new ArrayList<>(statistics.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                sb.append(key).append(',');
            }
            sb.append('\n');
            for (String key : keys) {
                sb.append(statistics.get(key)).append(',');
            }
            sb.append('\n');
            pw.write(sb.toString());
        }
        catch (FileNotFoundException ex) {
            LOGGER.error(ex);
            System.exit(1);
        }
        LOGGER.info("END exportSummarizationStatisticsToDisk");
    }

    private static void exportSummarizationConfigurationToDisk(Properties summarizationProperties, String summaryNTFilename) {
        String propertiesFilename = trimExtension(summaryNTFilename, false) + "-summarization-configuration.properties";
        SummarizationProperties.writePropertiesFile(summarizationProperties, propertiesFilename);
    }

    /*
        See load method comment: in examples use SummarizationProperties class instead of LoadingProperties.
        Returns a map with keys: "databaseName", "NTFilename" and "DOTFilename".
        If the corresponding name is not present, the value is set to null.
    */
    public static HashMap<String, String> summarize(String configurationFilename, Properties properties, boolean closeConnection) {
        LOGGER.info("START summarize");
        Properties defaultProperties = SummarizationProperties.getDefaultProperties();
        Properties summarizationProperties = reconcileProperties(defaultProperties, configurationFilename, properties);

        String datasetFilename = summarizationProperties.getProperty("dataset.filename");

        if(File.separator.equals("\\")) { // specify a different dot installation for Dot on Windows
            summarizationProperties.put("drawing.dot_installation", "C:\\Program Files\\Graphviz\\bin\\dot.exe");
        }

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
        String message = "Executing the summarize operation using "
                + summarizationProperties.getProperty("database.name")
                + " database, computing "
                + summarizationProperties.getProperty("summary.type")
                + " summary with"
                + (summarizationProperties.getProperty("summary.replace_type_with_most_general_type").equals("true") ? "" : "out")
                + " type generalization, on "
                + (summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true") ? "" : "not ")
                + "saturated graph, "
                + (summarizationProperties.getProperty("drawing.draw_input_graph").equals("true") ? "drawing input RDF graph visualization with DOT" : "no input RDF graph drawing");
        String stepByStep = summarizationProperties.getProperty("drawing.step_by_step");
        switch (stepByStep) {
            case "always":
                message += ", drawing summary step-by-step";
                break;
            case "when_changes":
                message += ", drawing summary when it changes";
                break;
        }
        String summaryDrawingStyle = summarizationProperties.getProperty("drawing.style");
        boolean drawingEnabled = false;
        switch (summaryDrawingStyle) {
            case "plain":
            case "split_leaves":
            case "split_and_fold_leaves":
                drawingEnabled = true;
                message += ", drawing summary visualizations with DOT in " + summaryDrawingStyle + " layout";
                break;
            default:
                message += ", no summary drawing";
                break;
        }
        System.out.println(message);
        System.out.println("********************************************************************************");

        try {
            LOGGER.info("Summarizing graph from Postgres");
            summary = createNewSummary(summarizationProperties);
            summary.setSummarizationProperties(summarizationProperties);
            getOrEstablishNewDatabaseConnection(summarizationProperties);
            if (summarizationProperties.getProperty("summary.step_by_step").equals("true")) {
                if (summarizationProperties.getProperty("drawing.step_by_step").equals("false")) {
                    LOGGER.warn("Configuration set to summary.step_by_step=true and drawing.step_by_step=false implies incremental execution without step-by-step drawings");
                }

                String summaryType = summarizationProperties.getProperty("summary.type");
                switch (summaryType) {
                    case "weak":
                    case "strong":
                    case "typedweak":
                    case "typedstrong":
                        break;
                    default:
                        LOGGER.warn("summary.step_by_step=true for " + summaryType + " which is not incremental: showing only second pass");
                }
            }
            summary.summarizeFromPostgres(databaseConnection);
            LOGGER.info("Graph from Postgres summarized");
        }
        catch (IllegalArgumentException ex) {
            LOGGER.error(ex);
            System.out.println("Make sure that the input graph is loaded into database.");
            System.exit(1);
        }

        summarySavingInPostgresTime = 0L;
        boolean exportToDatabase = summarizationProperties.getProperty("summary.export_to_database").equals("true");
        boolean exportRepresentationFunctionAndSummaryNodeStatistics = summarizationProperties.getProperty("summary.export_representation_function_and_node_statistics_to_database").equals("true");
        if (exportToDatabase) {
            LOGGER.info("Exporting summary to database");
            long start = System.currentTimeMillis();
            summary.exportSummaryToDatabase(databaseConnection, exportRepresentationFunctionAndSummaryNodeStatistics);
            summarySavingInPostgresTime = System.currentTimeMillis() - start;
            LOGGER.info("Summary exported to database");
        }

        summary.exportRepresentationFunctionToNTFile(databaseConnection, summarizationProperties.getProperty("summary.export_representation_function_to_nt_filename"));
        boolean representationStatisticsAlreadyComputed = exportToDatabase && exportRepresentationFunctionAndSummaryNodeStatistics;
        summary.exportNodeStatisticsToNTFile(databaseConnection, representationStatisticsAlreadyComputed, summarizationProperties.getProperty("summary.export_node_statistics_to_nt_filename"));
        summary.exportEdgeStatisticsToNTFile(databaseConnection, summarizationProperties.getProperty("summary.export_edge_statistics_to_nt_filename"));

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
            exportSummarizationStatisticsToDisk(summarizationProperties, NTFilename);
            LOGGER.info("Summarization statistics exported to disk");
        }

        if (summarizationProperties.getProperty("configuration.export_to_disk").equals("true")) {
            LOGGER.info("Exporting loading configuration to disk");
            exportSummarizationConfigurationToDisk(summarizationProperties, NTFilename);
            LOGGER.info("Loading configuration exported to disk");
        }

        if (drawingEnabled) {
            LOGGER.info("Exporting summary DOT drawing to disk");
        }
        // try to draw RDF graph and summary
        String DOTFilename = summary.writeDecodedSummaryToDOTFile(databaseConnection, summaryDrawingStyle);
        if (drawingEnabled) {
            LOGGER.info("Summary DOT drawing exported to disk");
        }

        if (closeConnection) {
            closeDatabaseConnection();
        }

        HashMap<String, String> names = new HashMap<>();
        names.put("databaseName", summarizationProperties.getProperty("database.name"));
        names.put("NTFilename", NTFilename);
        names.put("DOTFilename", DOTFilename);
        LOGGER.info("END summarize");
        return names;
    }

    /*
        See load method comment.
        Returns Summary object.
    */
    public static Summary read(String configurationFilename, Properties properties, boolean closeConnection) {
        LOGGER.info("START read");
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
        System.out.println("Executing the read operation using "
                + readingProperties.getProperty("database.name")
                + " database");
        System.out.println("********************************************************************************");

        try {
            LOGGER.info("Reading summary from Postgres");
            Summary s = new Summary(getOrEstablishNewDatabaseConnection(readingProperties));
            LOGGER.info("Summary read from Postgres");
            return s;
        }
        catch (SQLException ex) {
            throw new IllegalStateException("Could not read summary " + ex);
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
                .desc("- print the version of RDFQuotient")
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
                .longOpt("use-loading-properties")
                .desc("- set loading properties filename")
                .hasArg(true)
                .argName("filename")
                .required(false)
                .build();

        summarizationPropertiesOption = Option.builder("sp")
                .longOpt("use-summarization-properties")
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
        String version = Interface.class.getPackage().getImplementationVersion();
        if (version == null) {
            version = "";
        }
        final HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.setWidth(80);
        helpFormatter.setLeftPadding(0);
        String header = "RDFQuotient " + version
                + "\n\nThis framework is designed to work with one graph at a time.\n"
                + "Before using RDFQuotient make sure that the Postgres server is running.\n"
                + "Input RDF dataset file format is N-Triples and the file is assumed not to\n"
                + "contain any duplicated triples.\n\n"
                + "rdfquotient";
        String footer = "\n[ARGS] is a comma-separated list of assignments of form key=value, where key is a configuration property from the list of loading or summarization configuration properties.";
        helpFormatter.printHelp(header, "\n", options, footer, true);
    }

    private static Properties parseProperties(String commandLineProperties) {
        String[] properties = commandLineProperties.split(","); // no escaping assumed for commas
        Properties newProperties = new Properties();
        for (String assignement : properties) {
            String[] assigmentSplit = assignement.split("=");
            newProperties.put(assigmentSplit[0].trim(), assigmentSplit[1].trim());
        }
        return newProperties;
    }

    public static void main(String[] args) {
        setUpCommandLineInterface();
        databaseConnection = null;

        if (args.length == 0) {
            printHelp();
            return;
        }

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine arguments = parser.parse(options, args);

            if (arguments.hasOption(helpOption.getOpt())) {
                printHelp();
                return;
            }

            if (arguments.hasOption(versionOption.getOpt())) {
                String version = Interface.class.getPackage().getImplementationVersion();
                if (version == null) {
                    version = "";
                }
                System.out.println("RDFQuotient version: " + version);
                return;
            }

            String loadingPropertiesFilename = LoadingProperties.DEFAULT_LOADING_PROPERTIES_FILENAME;
            if (arguments.hasOption(loadingPropertiesOption.getOpt())) {
                loadingPropertiesFilename = arguments.getOptionValue(loadingPropertiesOption.getOpt());
            }

            String summarizationPropertiesFilename = SummarizationProperties.DEFAULT_SUMMARIZATION_PROPERTIES_FILENAME;
            if (arguments.hasOption(summarizationPropertiesOption.getOpt())) {
                summarizationPropertiesFilename = arguments.getOptionValue(summarizationPropertiesOption.getOpt());
            }

            if (arguments.hasOption(loadOption.getOpt())) {
                Properties commandLineProperties = parseProperties(arguments.getOptionValue(loadOption.getOpt()));
                if (arguments.hasOption(dryRunOption.getOpt())) {
                    Properties defaultProperties = LoadingProperties.getDefaultProperties();
                    Properties loadingProperties = reconcileProperties(defaultProperties, loadingPropertiesFilename, commandLineProperties);
                    // Dataset filename needs backslashes on Windows
                    if(File.separator.equals("\\")) {
                        String datasetFilename = loadingProperties.getProperty("dataset.filename");
                        datasetFilename = datasetFilename.replaceAll("\\/", "\\\\");
                        loadingProperties.setProperty("dataset.filename", datasetFilename);
                    }
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

            if (arguments.hasOption(summarizeOption.getOpt())) {
                Properties commandLineProperties = parseProperties(arguments.getOptionValue(summarizeOption.getOpt()));
                if (arguments.hasOption(dryRunOption.getOpt())) {
                    Properties defaultProperties = SummarizationProperties.getDefaultProperties();
                    Properties summarizationProperties = reconcileProperties(defaultProperties, summarizationPropertiesFilename, commandLineProperties);
                    if(File.separator.equals("\\")) {
                        String datasetFilename = summarizationProperties.getProperty("dataset.filename");
                        datasetFilename = datasetFilename.replaceAll("\\/", "\\\\");
                        summarizationProperties.setProperty("dataset.filename", datasetFilename);
                    }
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

            if (arguments.hasOption(readOption.getOpt())) {
                Properties commandLineProperties = parseProperties(arguments.getOptionValue(readOption.getOpt()));
                if (arguments.hasOption(dryRunOption.getOpt())) {
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
            LOGGER.error(ex);
            printHelp();
        }
    }

    // convenience methods
    public static HashMap<String, String> loadAndSummarize(Properties loadingProperties, Properties summarizationProperties) {
        load(null, loadingProperties, false);

        return summarize(null, summarizationProperties, true);
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

        loadingProperties.put("saturation.type", "NONE");
        load(null, loadingProperties, false);
        summarizationProperties.put("summary.summarize_saturated_graph", "false");
        HashMap<String, String> names = summarize(null, summarizationProperties, true);

        loadingProperties.put("saturation.type", "ASSERTION_SAT");
        loadingProperties.put("database.name", "");
        loadingProperties.put("dataset.filename", names.get("NTFilename"));
        summarizationProperties.put("database.name", "");
        summarizationProperties.put("dataset.filename", names.get("NTFilename"));
        summarizationProperties.put("summary.summarize_saturated_graph", "true");
        load(null, loadingProperties, false);
        names = summarize(null, summarizationProperties, true);

        return names;
    }

    // if need be
    public static Summary getSummary() {
        return summary;
    }
}
