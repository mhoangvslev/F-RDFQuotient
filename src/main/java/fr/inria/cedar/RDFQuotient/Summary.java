//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient;

import fr.inria.cedar.RDFQuotient.controller.SummarizationProperties;
import fr.inria.cedar.RDFQuotient.datastructures.EdgesWithProvenanceCounts;
import fr.inria.cedar.RDFQuotient.datastructures.Long2Long;
import fr.inria.cedar.RDFQuotient.datastructures.Long2LongSet;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import fr.inria.cedar.RDFQuotient.export.SummaryExport;
import fr.inria.cedar.RDFQuotient.util.PostgresIdentifier;
import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import fr.inria.cedar.ontosql.rdfdb.dictionaryencoder.PostgresDatabaseHandler;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Summary {
    private static final Logger LOGGER = Logger.getLogger(Summary.class.getName());
    protected static final SimpleDateFormat SD_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    static {
        LOGGER.setLevel(Level.INFO);
    }

    protected final HashSet<Triple> genericPropertyTriples = new HashSet<>();
    public Long2Long rep; // representation function
    protected HashSet<Long> sn; // schema nodes
    // data, schema and type triples:
    //   for each subject
    //     for each property
    //       the set of objects such that (subject, property, object) is in the summary
    public EdgesWithProvenanceCounts edgesWithProv;

    // The following three attribute serve to identify and store the class sets for RDF resources
    protected Long2LongSet cs; // for each class set ID, a class set. This is the class set that will be used to
    // decide node equivalence.
    // It can be the exact set of types of a node; or it can be the set of their generalization (if parameter replaceTypeWithMostGeneralType is true).
    protected Long2LongSet acs; // for each class set ID, a set of actual types encountered on nodes which fall in this
    // class set. This is used only if parameter replaceTypeWithMostGeneralType is true.
    protected Long2Long n2cs; // for each data node, its class set ID. This is also the rep function for typed nodes
    protected HashMap<TreeSet<Long>, Long> cs2csID; // for each set of types known so far, the ID of that set

    protected Traverser traverser;

    // these serve to represent the nodes that may have types but no data property
    protected long typeOnlyNodeID;
    protected boolean typeOnlyNodeAlreadySeen;

    protected long maxSummaryNode;
    protected Properties summarizationProperties;

    // summaryTablePrefix must be instantiated with a specific string for each summary type, so that each summary is exported into separate Postgres tables
    protected String summaryTablePrefix;
    protected boolean isTypeFirst = false;
    protected boolean isDataAndType = false;
    protected boolean isTwoPass = false;
    protected static final String ROOT_SUMMARY_PREFIX = "";
    protected static final String WEAK_SUMMARY_PREFIX = "w";
    protected static final String STRONG_SUMMARY_PREFIX = "s";
    protected static final String TYPED_WEAK_SUMMARY_PREFIX = "tw";
    protected static final String TYPED_STRONG_SUMMARY_PREFIX = "ts";
    protected static final String TWO_PASS_WEAK_SUMMARY_PREFIX = "2pw";
    protected static final String TWO_PASS_WEAK_SUMMARY_WITH_UNION_FIND_PREFIX = "2pwuf";
    protected static final String TWO_PASS_STRONG_SUMMARY_PREFIX = "2ps";
    protected static final String TWO_PASS_TYPED_WEAK_SUMMARY_PREFIX = "2ptw";
    protected static final String TWO_PASS_TYPED_STRONG_SUMMARY_PREFIX = "2pts";
    protected static final String TWO_PASS_SOURCE_SUMMARY_PREFIX = "2psc";
    protected static final String TYPED_SUMMARY_PREFIX = "t";
    protected static final String TWO_PASS_ONEFW_SUMMARY_PREFIX = "2p1fw";
    protected static final String TWO_PASS_ONEFB_SUMMARY_PREFIX = "2p1fb";
    protected static final String TWO_PASS_INPUT_OUTPUT_AND_TYPED_SUMMARY_PREFIX = "2pioat";
    protected static final String TWO_PASS_FORWARD_BACKWARD_BISIMULATION = "2pfb";

    protected String triplesFileName = "";
    protected String triplesTableName = "";
    protected String encodedTriplesTableName = "";
    protected String dictionaryTableName = "";
    protected String repTableName = "";
    protected String edgeTableName = "";
    protected boolean checkConsistency = false;
    protected String drawStepByStep = "false";
    protected boolean haltStepByStep = false;
    protected boolean gatherStatistics = false;

    // statistics
    protected long triplesSummarizedSoFar = 0;
    protected long typeTriplesSummarizedSoFar = 0;
    protected long nonTypeTriplesSummarizedSoFar = 0;
    protected long dataAndTypeTriplesSummarizedSoFar = 0;

    protected long schemaNodesCollectionTime = 0;
    protected long classSetCreationTime = 0;
    protected long typeTriplesSummarizationTime = 0;
    protected long dataAndTypeTriplesSummarizationTime = 0;
    protected long genericPropertyTriplesSummarizationTime = 0;
    protected long nonTypeTriplesSummarizationTime = 0;
    protected long allTriplesSummarizationTime = 0;

    protected long representationFunctionSavingExportingTime = 0;
    protected long summaryEdgesSavingTime = 0;
    // for each summary node, the number of graph nodes it represented
    protected HashMap<Long, Long> summaryNodeStatistics;
    // for each summary edge, the number of graph edge it represented
    protected HashMap<Triple, Long> summaryEdgeStatistics;
    protected long numberOfLeaves;

    // exporter utility
    protected SummaryExport exporter;

    // properties to ignore when building cliques
    protected HashSet<Long> genericPropertiesIgnoredInCliques;
    protected HashMap<Long, Long> sourcesOfGenericPropertiesIgnoredInCliques;
    protected HashMap<Long, Long> targetsOfGenericPropertiesIgnoredInCliques;

    // in type triples, whether to replace the type with the most general type
    protected boolean replaceTypeWithMostGeneralType = false;
    protected HashMap<Long, Long> topClass; //for each class, its most general superclass (or itself if nothing else is found)
    protected HashMap<Long, HashSet<Long>> topClasses; //for each class, the set of its most general superclasses (or itself if nothing else is found)

    protected HashMap<Long, HashMap<Long, Long>> summaryNodeToActualTypeToCardinality;
    protected HashMap<Long, HashSet<Long>> generalizers;

    protected String defaultTypeURI;
    protected String[] variantTypeURIs;

    public Summary() {
        summaryTablePrefix = ROOT_SUMMARY_PREFIX;
        typeOnlyNodeAlreadySeen = false;
        sn = new HashSet<>();
        rep = new Long2Long();
        edgesWithProv = new EdgesWithProvenanceCounts();

        summaryNodeStatistics = new HashMap<>();
        summaryEdgeStatistics = new HashMap<>();

        genericPropertiesIgnoredInCliques = new HashSet<>();
        targetsOfGenericPropertiesIgnoredInCliques = new HashMap<>();
        sourcesOfGenericPropertiesIgnoredInCliques = new HashMap<>();

        // initialize summarizationProperties with default values from code
        summarizationProperties = SummarizationProperties.getDefaultProperties();
    }

    public void setSummarizationProperties(Properties newProperties) {
        summarizationProperties = newProperties;
    }

    // overwrite summarizationProperties entries with values specified in newProperties, other entries in summarizationProperties left unmodified
    public void updateSummarizationProperties(Properties newProperties) {
        summarizationProperties = SummarizationProperties.reconcileProperties(summarizationProperties, newProperties);
    }

    // Interface.summarize method should be used instead.
    // Only some, exceptional circumstances, like for example
    // programmatic usage from outside of this project source code,
    // may justify a usage of setSummarizationProperty at a level of Summary class.
    public void setSummarizationProperty(String key, String value) {
        summarizationProperties.put(key, value);
    }

    private void setGenericProperties() {
        if (summarizationProperties.getProperty("summary.omit_generic_properties_from_cliques").equalsIgnoreCase("true")) {
            String[] props = summarizationProperties.getProperty("summary.generic_properties").split(",");
            for (String nonCliqueP : props) {
                //LOGGER.info("Generic property: " + nonCliqueP);
                this.genericPropertiesIgnoredInCliques.add(RDF2SQLEncoding.dictionaryEncode(nonCliqueP));
            }
        }
    }

    private void setMostGeneralType() {
        boolean replace = summarizationProperties.getProperty("summary.replace_type_with_most_general_type").equalsIgnoreCase("true");
        //LOGGER.debug("Replace types with the most general type: " + (replace ? "true" : "false"));
        replaceTypeWithMostGeneralType = replace;
        if (replace) {
            this.summaryNodeToActualTypeToCardinality = new HashMap<>();
            this.generalizers = new HashMap<>();
            this.topClass = new HashMap<>();
            this.topClasses = new HashMap<>();
        }
    }

    private void setDefaultTypeURI() {
        this.defaultTypeURI = summarizationProperties.getProperty("summary.default_type_property_URI");
        if (this.defaultTypeURI.equals("")) {
            LOGGER.error("The summarization property summary.default_type_property_URI must not be an empty string");
            System.exit(1);
        }
    }

    private void setVariantTypeURIs() {
        this.variantTypeURIs = summarizationProperties.getProperty("summary.variant_type_property_URIs").split(",");
    }

    public void setTypeURIs() {
        setDefaultTypeURI();
        setVariantTypeURIs();
    }

    public String getDefaultTypeURI() {
        return this.defaultTypeURI;
    }

    public String[] getVariantTypeURIs() {
        return this.variantTypeURIs;
    }

    public void setUpClassFieldsDependingOnProperties() {
        try {
            checkConsistency = summarizationProperties.getProperty("summary.consistency_checks").equalsIgnoreCase("true");
            drawStepByStep = summarizationProperties.getProperty("drawing.step_by_step").toLowerCase();
            haltStepByStep = summarizationProperties.getProperty("summary.step_by_step").equalsIgnoreCase("true");
            gatherStatistics = summarizationProperties.getProperty("summary.add_representation_counts_in_nt_and_dot_files").equalsIgnoreCase("true");
        }
        catch (Exception ex) {
            throw new IllegalStateException("Unable to extract property information: " + ex);
        }
        setGenericProperties();
        setMostGeneralType();
    }

    public Summary(Connection conn) throws SQLException {
        this.rep = new Long2Long();
        this.edgesWithProv = new EdgesWithProvenanceCounts();
        try {
            conn.setAutoCommit(false);
        }
        catch (SQLException ex) {
            LOGGER.error(ex);
        }
        //LOGGER.info("Trying to read summary from Postgres");
        Statement stmt = conn.createStatement();
        try {
            ResultSet rs = stmt.executeQuery("select name from saved_summary_table_names where role='dictionary'");
            if (rs.next()) {
                this.dictionaryTableName = rs.getString(1);
            }
            else {
                throw new IllegalStateException("Could not learn the name of the dictionary table");
            }
            rs = stmt.executeQuery("select name from saved_summary_table_names where role='representation'");
            if (rs.next()) {
                this.repTableName = rs.getString(1);
            }
            else {
                throw new IllegalStateException("Could not learn the name of the representation table");
            }
            rs = stmt.executeQuery("select name from saved_summary_table_names where role='edges'");
            if (rs.next()) {
                this.edgeTableName = rs.getString(1);
            }
            else {
                throw new IllegalStateException("Could not learn the name of the edge table");
            }
            rs = stmt.executeQuery("select name from saved_summary_table_names where role='encoded_triples'");
            if (rs.next()) {
                this.encodedTriplesTableName = rs.getString(1);
            }
            else {
                throw new IllegalStateException("Could not learn the name of the encoded triples table");
            }
            rs = stmt.executeQuery("select name from saved_summary_table_names where role='default_type_URI'");
            if (rs.next()) {
                this.defaultTypeURI = rs.getString(1);
            }
            else {
                throw new IllegalStateException("Could not learn the name of the encoded triples table");
            }
            rs = stmt.executeQuery("select name from saved_summary_table_names where role='variant_type_URIs'");
            if (rs.next()) {
                this.variantTypeURIs = rs.getString(1).split(",");
            }
            else {
                throw new IllegalStateException("Could not learn the name of the encoded triples table");
            }
            rs.close();
        }
        catch (SQLException e) {
            stmt.close();
            conn.close();
            throw new IllegalStateException("Could not read summary from Postgres " + e);
        }
        RDF2SQLEncoding.setUp(conn, dictionaryTableName, defaultTypeURI, variantTypeURIs);
        //LOGGER.debug("Set up special URIs from dictionary");
        String getSummaryTriples = getSummaryTriplesSQLQuery();
        try (
                Statement getTriples = conn.createStatement();
                ResultSet rs = getTriples.executeQuery(getSummaryTriples)
        ) {
            while (rs.next()) {
                long s = rs.getLong(1);
                long p = rs.getLong(2);
                long o = rs.getLong(3);
                edgesWithProv.addTriple(s, p, o);
            }
        }
        //LOGGER.info("Summary read from Postgres");
    }

    /**
     * Fills in the exporter object
     */
    protected void ensureExporter() {
        if (exporter == null) {
            exporter = new SummaryExport(this, summarizationProperties, dictionaryTableName, triplesFileName, encodedTriplesTableName);
        }
    }

    /*
     * Creates indexes on rep table
     * @param conn
     */
    protected void addIndexesToRepTable(Connection conn) {
        LOGGER.info("Building indexes on repTable");
        // create indexes on rep table
        final PostgresDatabaseHandler databaseHandler = new PostgresDatabaseHandler(conn, triplesTableName, encodedTriplesTableName, dictionaryTableName);
        final List<String> attrs = new ArrayList<>();
        String indexName = repTableName + "_i_gs";
        attrs.add("graphnode");
        attrs.add("summarynode");
        try {
            databaseHandler.createIndex(PostgresIdentifier.escapedQuotedId(repTableName), PostgresIdentifier.escapedQuotedId(indexName), attrs);
        }
        catch (SQLException ex) {
            LOGGER.error("Couldn't create index " + indexName + " on " + repTableName + " " + ex);
            return;
        }
        attrs.clear();
        indexName = repTableName + "_i_sg";
        attrs.add("summarynode");
        attrs.add("graphnode");
        try {
            databaseHandler.createIndex(PostgresIdentifier.escapedQuotedId(repTableName), PostgresIdentifier.escapedQuotedId(indexName), attrs);
        }
        catch (SQLException ex) {
            LOGGER.error("Couldn't create index " + indexName + " on " + repTableName + " " + ex);
            return;
        }
        LOGGER.info("Indexes built successfully");
    }

    // we need to be sure that integers which we invent to represent nodes
    // will not collide with the codes already given to classes and properties
    // (which, in this implementation, for simplicity, are preserved).
    protected void avoidCollisionsWhenAssigningSummaryNodes(Connection conn) {
        long maxClassOrPropertyCode = 0;

        for (long typeConstantCode: RDF2SQLEncoding.getAllTypeCodes()) {
            if (typeConstantCode != -1) {
                maxClassOrPropertyCode = this.maxO(conn, typeConstantCode);
            }
        }

        long subClassCode = RDF2SQLEncoding.getSubClassCode();
        if (subClassCode != -1) {
            maxClassOrPropertyCode = Math.max(maxClassOrPropertyCode, this.maxSPO(conn, subClassCode));
        }

        long subPropertyCode = RDF2SQLEncoding.getSubPropertyCode();
        if (subPropertyCode != -1) {
            maxClassOrPropertyCode = Math.max(maxClassOrPropertyCode, this.maxSPO(conn, subPropertyCode));
        }

        long domainCode = RDF2SQLEncoding.getDomainCode();
        if (domainCode != -1) {
            maxClassOrPropertyCode = Math.max(maxClassOrPropertyCode, this.maxSPO(conn, domainCode));
        }

        long rangeCode = RDF2SQLEncoding.getRangeCode();
        if (rangeCode != -1) {
            maxClassOrPropertyCode = Math.max(maxClassOrPropertyCode, this.maxSPO(conn, domainCode));
        }

        this.jumpSummaryNodeCount(maxClassOrPropertyCode + 1);
    }

    protected long maxSPO(Connection conn, long property) {
        long maxS = maxS(conn, property);
        long maxP = maxO(conn, property);
        long maxO = maxO(conn, property);
        return Math.max(maxS, Math.max(maxP, maxO));
    }

    protected long maxS(Connection conn, long property) {
        String jumpRepString = ("select max(s) from " + PostgresIdentifier.escapedQuotedId(encodedTriplesTableName) + " t1 where p = " + property);
        try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Unable to determine the highest subject code" + e);
        }
        return -1;
    }

    protected long maxP(Connection conn, long property) {
        String jumpRepString = ("select max(p) from " + PostgresIdentifier.escapedQuotedId(encodedTriplesTableName) + " t1 where p = " + property);
        try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Unable to determine the highest property code " + e);
        }
        return -1;
    }

    protected long maxO(Connection conn, long property) {
        String jumpRepString = ("select max(o) from " + PostgresIdentifier.escapedQuotedId(encodedTriplesTableName) + " t1 where p = " + property);
        try (ResultSet rs = conn.createStatement().executeQuery(jumpRepString)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Unable to determine the highest object code " + e);
        }
        return -1;
    }

    protected void collectSchemaNodes(Connection conn) {
        setTypeURIs();
        RDF2SQLEncoding.setUp(conn, dictionaryTableName, defaultTypeURI, variantTypeURIs);
        long subClassCode = RDF2SQLEncoding.getSubClassCode();
        long subPropertyCode = RDF2SQLEncoding.getSubPropertyCode();
        long domainCode = RDF2SQLEncoding.getDomainCode();
        long rangeCode = RDF2SQLEncoding.getRangeCode();
        HashSet<Long> typeCodes = RDF2SQLEncoding.getAllTypeCodes();
        long classCode = RDF2SQLEncoding.getClassCode();
        long propertyCode = RDF2SQLEncoding.getPropertyCode();

        StringBuilder getTriplesString = new StringBuilder();
        getTriplesString.append("select distinct s from ").append(PostgresIdentifier.escapedQuotedId(encodedTriplesTableName))
                .append(" where p = ").append(subClassCode)
                .append(" or p = ").append(subPropertyCode)
                .append(" or p = ").append(domainCode)
                .append(" or p = ").append(rangeCode);
        try {
            try (Statement getTriples = conn.createStatement()) {
                getTriples.setFetchSize(10000);
                try (ResultSet rs = getTriples.executeQuery(getTriplesString.toString())) {
                    while (rs.next()) {
                        long s = rs.getLong(1);
                        sn.add(s);
                        rep.put(s, s);
                    }
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Postgres error encountered while collecting schema nodes " + e);
        }

        getTriplesString.setLength(0);
        getTriplesString.append("select distinct o from ").append(PostgresIdentifier.escapedQuotedId(encodedTriplesTableName))
                .append(" where p = ").append(subClassCode)
                .append(" or p = ").append(subPropertyCode)
                .append(" or p = ").append(domainCode)
                .append(" or p = ").append(rangeCode);
        for (long typeCode: typeCodes) {
            getTriplesString.append(" or p = ").append(typeCode);
        }

        try {
            try (Statement getTriples = conn.createStatement()) {
                getTriples.setFetchSize(10000);
                try (ResultSet rs = getTriples.executeQuery(getTriplesString.toString())) {
                    while (rs.next()) {
                        long o = rs.getLong(1);
                        sn.add(o);
                        rep.put(o, o);
                    }
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Postgres error encountered while collecting schema nodes " + e);
        }

        getTriplesString.setLength(0);
        // add nodes declared to be of type rdf:Class or rdf:Property
        getTriplesString.append("select distinct s from ").append(PostgresIdentifier.escapedQuotedId(encodedTriplesTableName))
                .append(" where (o = ").append(classCode)
                .append(" or o = ").append(propertyCode)
                .append(") and (");
        String prefix = "";
        for (long typeCode: typeCodes) { // typeCodes guaranteed to be non-empty
            getTriplesString.append(prefix).append("p = ").append(typeCode);
            prefix = " or ";
        }
        getTriplesString.append(")");

        try {
            try (Statement getTriples = conn.createStatement()) {
                getTriples.setFetchSize(10000);
                try (ResultSet rs = getTriples.executeQuery(getTriplesString.toString())) {
                    while (rs.next()) {
                        long s = rs.getLong(1);
                        sn.add(s);
                        rep.put(s, s);
                        //LOGGER.info(s + " " + RDF2SQLEncoding.dictionaryDecode(s) + " is a schema node");
                    }
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Postgres error encountered while collecting schema nodes " + e);
        }
    }

    void computeMostGeneralType(Connection conn) {
        setTypeURIs();
        RDF2SQLEncoding.setUp(conn, dictionaryTableName, defaultTypeURI, variantTypeURIs);
        long subClassCode = RDF2SQLEncoding.getSubClassCode();
        LOGGER.info("Computing most general types");
        // traverse all the subClassOf triples and gather the most general superclasses of every class (according to the schema)
        generalizers = new HashMap<>();
        String getTriplesString = "select s, o from " + PostgresIdentifier.escapedQuotedId(encodedTriplesTableName)
                + " where p = " + subClassCode;
        try { // we learn all the subclass edges
            try (Statement getTriples = conn.createStatement()) {
                getTriples.setFetchSize(10000);
                try (ResultSet rs = getTriples.executeQuery(getTriplesString)) {
                    while (rs.next()) {
                        Long s = rs.getLong(1);
                        Long o = rs.getLong(2);

                        HashSet<Long> generalizersOfS = generalizers.computeIfAbsent(s, k -> new HashSet<>());
                        generalizersOfS.add(o);

                        //LOGGER.info(RDF2SQLEncoding.dictionaryDecode(s) + " subclass of " + RDF2SQLEncoding.dictionaryDecode(o));
                        //						if (topClass.containsKey(s)) {
                        //							throw new IllegalStateException("Type " + RDF2SQLEncoding.dictionaryDecode(s)
                        //							+ " has more than one supertype: ");
                        //							+ RDF2SQLEncoding.dictionaryDecode(mostGeneralSuperClass.get(s)) + " and "
                        //							+ RDF2SQLEncoding.dictionaryDecode(mostGeneralSuperClass.get(o)));
                        //						}
                        //						else {
                        //							topClass.put(s, o);
                        //						}
                    }
                }
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Postgres error encountered while collecting schema nodes " + e);
        }

        // now compute the closure of the "most general supertype".
        // for each class that is a subclass of someone else
        for (Long s : generalizers.keySet()) {
            // determine the most general types of s
            topClasses.put(s, extractTopTypes(gatherAllSuperTypes(s)));
        }
//		for (Long c1: generalizers.keySet()) {
//			//LOGGER.info("Most general superclasses of " + c1 + " (" +
//					RDF2SQLEncoding.dictionaryDecode(c1) + "): [");
//			StringBuffer sb = new StringBuffer();
//			for (Long topc1: topClasses.get(c1)) {
//				sb.append(RDF2SQLEncoding.dictionaryDecode(topc1) + " ");
//			}
//			LOGGER.info(new String(sb)+"]");
//		}
    }

    /**
     * Helper method for generalized class sets:
     * Using the generalizer edges, this returns all the (close or far) supertypes of s (and also s itself)
     */
    private HashSet<Long> gatherAllSuperTypes(Long s) {
        HashSet<Long> res = new HashSet<>();
        res.add(s);
        recGatherAllSuperTypes(res);
        return res;
    }

    private void recGatherAllSuperTypes(HashSet<Long> res) {
        HashSet<Long> res2 = new HashSet<>(res);
        int nres = res.size();
        for (Long superS : res) { // add to res2 all the generalizers of any type in res
            HashSet<Long> genSuperS = generalizers.get(superS);
            if (genSuperS != null) {
                res2.addAll(genSuperS);
            }
        }
        res.addAll(res2);
        if (nres < res.size()) { // if there have been new types in res
            recGatherAllSuperTypes(res);
        }
    }

    /**
     * Helper method for generalized class sets:
     * Using the generalized edges, returns those superTypes that do not have a supertype (thus, those that are top types)
     */
    private HashSet<Long> extractTopTypes(HashSet<Long> superTypes) {
        HashSet<Long> topTypes = new HashSet<>();
        for (Long superType : superTypes) {
            if (generalizers.get(superType) == null) {
                topTypes.add(superType);
            }
        }
        return topTypes;
    }

    protected String showRep() {
        StringBuilder sb = new StringBuilder();
        for (long node : this.rep.getKeys()) {
            //sb.append(node).append("=>").append(rep.get(node)).append(" ");
            sb.append(node).append(" (").append(RDF2SQLEncoding.dictionaryDecode(node)).append(") => ").append(rep.get(node)).append("\n");
        }
        return sb.toString();
    }

    protected long getNextSummaryNode() {
        long node = this.maxSummaryNode;
        this.maxSummaryNode++;
        return node;
    }

    /**
     * This method is needed in order to avoid collisions between IDs assigned
     * for class sets, and IDs assigned based on property cliques.
     */
    protected void jumpSummaryNodeCount(long n) {
        this.maxSummaryNode += n;
    }

    /**
     * write in summaryNodeStatistics the number of
     * data nodes each summary node represented
     */
    public void gatherNodeStatistics() {
        summaryNodeStatistics.clear();
        long summaryNode;
        for (Long l : rep.getKeys()) {
			summaryNodeStatistics.merge(rep.get(l), 1L, Long::sum);
        }
    }

    public void gatherEdgeStatistics() {
		summaryEdgeStatistics.clear();
        long represented;
        for (Triple t : this.edgesWithProv.getSummaryEdges()) {
            summaryEdgeStatistics.put(t, edgesWithProv.getCounter(t.s, t.p, t.o));
        }
    }

    /**
     * Summarizes an RDF graph assuming the data triples are in Postgres
     */
    public void summarizeFromPostgres(Connection conn) {
        if (isDataAndType) {
            if (isTwoPass) {
                traverser = new DataAndTypeTwoPassTraverser(this, conn);
            }
            else {
                traverser = new DataAndTypeTraverser(this, conn);
            }
        }
        else {
            if (isTypeFirst) {
                if (isTwoPass) {
                    traverser = new TypeFirstTwoPassTraverser(this, conn);
                }
                else {
                    traverser = new TypeFirstTraverser(this, conn);
                }
            }
            else {
                if (isTwoPass) {
                    traverser = new DataFirstTwoPassTraverser(this, conn);
                }
                else {
                    traverser = new DataFirstTraverser(this, conn);
                }
            }
        }

        traverser.traverseAllTriples();
    }

    protected void handleDataTriple(Triple t) {
        throw new IllegalStateException("Not implemented at this level");
    }

    protected void handleDataOrTypeTriple(Triple t) {
        throw new IllegalStateException("Not implemented at this level");
    }

    /**
     * This implementation should be shared by Weak and Strong
     */
    protected void representTypeTripleAfterData(Triple t) {
        Long repS = rep.get(t.s);
        if (repS != null) {
            edgesWithProv.addTriple(repS, RDF2SQLEncoding.getDefaultTypeCode(), t.o);
        }
        else {
            if (!typeOnlyNodeAlreadySeen) {
                typeOnlyNodeID = getNextSummaryNode();
                typeOnlyNodeAlreadySeen = true;
            }
            edgesWithProv.addTriple(typeOnlyNodeID, RDF2SQLEncoding.getDefaultTypeCode(), t.o);
            rep.put(t.s, typeOnlyNodeID);
        }
        // o already represented in collectSchemaNodes
    }

    /**
     * For special properties we do not want to have in cliques
     * Ioana, Oct 9, 2018
     * <p>
     * Assigns a new summary node as source and target of each special data property
     */
    public void prepareRepresentationOfGenericPropertyTriples() {
        for (Long l : genericPropertiesIgnoredInCliques) {
            this.sourcesOfGenericPropertiesIgnoredInCliques.put(l, getNextSummaryNode());
            this.targetsOfGenericPropertiesIgnoredInCliques.put(l, getNextSummaryNode());
        }
    }

    /**
     * For special properties we do not want to have in cliques
     * Ioana, Oct 9, 2018
     * <p>
     * Represents each special data triple by the (existing or not) representative
     * of its subject, and by the (for sure existing) representative of its object
     */
    protected void representGenericPropertyTriple(Triple t) {
        Long repS = rep.get(t.s);
        if (repS == null) {// the node has not been seen before
            // therefore we must create its representative
            //LOGGER.info("Created new subject for " + t.p);
            repS = sourcesOfGenericPropertiesIgnoredInCliques.get(t.p);
            rep.put(t.s, repS);
        }
        Long repO = rep.get(t.o);
        if (repO == null) {// the node has not been seen before
            repO = targetsOfGenericPropertiesIgnoredInCliques.get(t.p);
            rep.put(t.o, repO);
        }
        edgesWithProv.addTriple(repS, t.p, repO);
    }

    /**
     * This method takes into account the contribution of a type triple, to type-first summarization.
     * Notably, it:
     * - records the triple's consequences in the class set (creates the class set if it did not exist, adds the class to it etc.)
     * - if type generalization is used, it adds the most general supertype of the type in the class set, but also records the actual type
     * in the actual class set
     */
    protected void handleTypeTripleBeforeData(Triple t) {
        if (sn.contains(t.s)) { // schemaNode rdf:type classNode, represent right away
            // s and o already represented in collectSchemaNodes
            edgesWithProv.addTriple(rep.get(t.s), t.p, rep.get(t.o));
            return;
        }

        // not a schema triple
        HashSet<Long> oTopClasses = null; //top ancestors of this type
        if (this.replaceTypeWithMostGeneralType) {
            oTopClasses = this.topClasses.get(t.o);
            if (oTopClasses == null) {
                oTopClasses = new HashSet<>();
                oTopClasses.add(t.o);
                topClasses.put(t.o, oTopClasses);
            }
        }
        Long repS = n2cs.get(t.s);
        boolean firstSightS = (repS == null);
        TreeSet<Long> sClassSet; // the types according to which t.s will be represented

        if (firstSightS) {
            sClassSet = new TreeSet<>();
            if (this.replaceTypeWithMostGeneralType) { // add the top classes of the class we found here
                sClassSet.addAll(oTopClasses);
            }
            else {
                sClassSet.add(t.o); // add the actual class we found here
            }
            repS = cs2csID.get(sClassSet); // type-based representative
            // comparison between sets uses equals and compares the structures of the sets
            if (repS == null) { // we create it
                repS = getNextSummaryNode();
                cs.put(repS, sClassSet); // installs the new class set
                cs2csID.put(sClassSet, repS); // installs the new class set
            }
            // whether or not newClassSetID was known:
            n2cs.put(t.s, repS); // erases/replaces previously known class set ID
        }
        else { // not the first time we encounter s.
            sClassSet = cs.get(repS);
            // Does its class set change now?
            boolean sClassSetChanges = false;
            if (this.replaceTypeWithMostGeneralType) {
                // the class set changes if the top classes of o have a class not in classSetOfThisNode
                for (Long x : oTopClasses) {
                    if (!sClassSet.contains(x)) {
                        sClassSetChanges = true;
                        break;
                    }
                }
            }
            else { // the class set changes if t.o was not already in classSetOfThisNode
                sClassSetChanges = !sClassSet.contains(t.o);
            }

            if (sClassSetChanges) { // we already had some types for t.s but not the one we consider this time
                // n is moving from sClassSet to newSClassSet.
                Long oldSRep = n2cs.get(t.s);
                // form the new class set of this node:
                TreeSet<Long> newSClassSet = new TreeSet<>();
                newSClassSet.addAll(sClassSet); // add the previous class set
                if (this.replaceTypeWithMostGeneralType) {
                    newSClassSet.addAll(oTopClasses);
                }
                else {
                    newSClassSet.add(t.o); // add the appropriate type in
                }
                Long newSRep = cs2csID.get(newSClassSet);
                if (newSRep == null) {
                    newSRep = getNextSummaryNode();
                    cs.put(newSRep, newSClassSet);
                    cs2csID.put(newSClassSet, newSRep);
                }
                if (this.replaceTypeWithMostGeneralType) {
                    //this.summaryNodeToActualTypeToCardinality.put(newClassSetID,
                    //		this.summaryNodeToActualTypeToCardinality.get(oldClassSetID));
                    HashMap<Long, Long> reconciled =
                            addStatistics(summaryNodeToActualTypeToCardinality.get(newSRep),
                                    summaryNodeToActualTypeToCardinality.get(oldSRep));
                    this.summaryNodeToActualTypeToCardinality.put(newSRep, reconciled);
                    //this.summaryNodeToActualTypeToCardinality.remove(oldSRep);
                }
                n2cs.put(t.s, newSRep);
            }
        }
        // Last block of the method: whether or not s had been seen before or not
        if (this.replaceTypeWithMostGeneralType) {
            Long sRep = n2cs.get(t.s); // n.s has a class set representative by now
            HashMap<Long, Long> actualTypeCountSRep = this.summaryNodeToActualTypeToCardinality.computeIfAbsent(sRep, k -> new HashMap<>());
            // also create actual class set
            // keep a count of this type:
            actualTypeCountSRep.merge(t.o, 1L, Long::sum);
        }
    }

    // helper
    private String decodeTopTypes(HashSet<Long> topClassesOfO) {
        StringBuilder sb = new StringBuilder();
        for (Long l : topClassesOfO) {
            sb.append(RDF2SQLEncoding.dictionaryDecode(l)).append(" ");
        }
        return new String(sb);
    }

    /**
     * Added on Dec 21, 2018
     *
     * @param hm1 a Long->Long map
     * @param hm2 a Long->Long map
     * @return hm a map summing the two input maps (count absent key for 0)
     */
    private HashMap<Long, Long> addStatistics(HashMap<Long, Long> hm1, HashMap<Long, Long> hm2) {
        if (hm1 == null && hm2 == null) {
            throw new IllegalStateException("Two empty lists");
        }
        if (hm1 == null) {
            return hm2;
        }
        if (hm2 == null) {
            return hm1;
        }
        HashMap<Long, Long> res = new HashMap<>();
        // first, add all the values on keys from hm1:
        for (Long l1 : hm1.keySet()) {
            Long x = res.get(l1); // initialize the count for this key
            if (x == null) {
                x = 0L;
            }
            x += hm1.get(l1); // take the count from hm1
            Long y = hm2.get(l1); // add the value from hm2, if any
            if (y != null) {
                x += y;
            }
            res.put(l1, x);
        }
        for (Long l2 : hm2.keySet()) {// second, add also those hm2 keys that are not in hm1
            // those that are in both have already been accounted for
            if (hm1.get(l2) == null) {
                res.put(l2, hm2.get(l2));
            }
        }
        return res;
    }

    /**
     * This method adds the type triples in the summary, based on the structures previously filled in while traversing those triples.
     * It is called only once and will output all the type triples of the summary.
     */
    protected void representTypeTriplesBeforeData() {
        //LOGGER.debug("POST HANDLE TYPE TRIPLES");
        // Dec 17, 2018: the block below allows to represent all the type triples correctly.
        // However, there are bad interactions with some strong summarization code that assumes that
        // the "class set" data structures are correlated with the represented type triples.
        // TODO decide if we want to enforce this (debug the NPEs thrown by TypedStrong summarization)
        // or if we fix the representation of type triples differently.
        //		if (this.replaceTypeWithMostGeneralType) {
        //			for (Long classSetID: this.summaryNodeToActualTypeToCardinality.keySet()) {
        //				HashMap<Long, Long> typesWithCardOfThisNode = this.summaryNodeToActualTypeToCardinality.get(classSetID);
        //				for (Long actualType: typesWithCardOfThisNode.keySet()) {
        //					long card = typesWithCardOfThisNode.get(actualType);
        //					for (int i = 0; i < card; i ++) {
        //						// we do it the right number of times in order for the counts to be correct
        //						edgesWithProv.addTriple(classSetID, RDF2SQLEncoding.getTypeCode(), actualType);
        //					}
        //				}
        //			}
        //		}
        //		else {// classical summarization, using the types from classSet
        for (long node : n2cs.getKeys()) {
            long thisClassSetID = n2cs.get(node);
            rep.put(node, thisClassSetID); // Dec 17, 2018
            TreeSet<Long> thisClassSet = cs.get(thisClassSetID); // the class set IS the representative
            for (long thisClass : thisClassSet) {
                edgesWithProv.addTriple(thisClassSetID, RDF2SQLEncoding.getDefaultTypeCode(), thisClass);
                // o already represented in collectSchemaNodes
            }
        }
        //}
    }

    protected void classifyDataTriple(Triple t) {
        throw new IllegalStateException("Not implemented at this level");
    }

    protected void classifyDataOrTypeTriple(Triple t) {
        throw new IllegalStateException("Not implemented at this level");
    }

    protected void classificationPostProcessing() {
        throw new IllegalStateException("Not implemented at this level");
    }

    protected void representDataTriple(Triple t) {
        throw new IllegalStateException("Not implemented at this level");
    }

    protected void representDataOrTypeTriple(Triple t) {
        throw new IllegalStateException("Not implemented at this level");
    }

    protected void consistencyChecks() {
        throw new IllegalStateException("Not implemented at this level");
    }

    static protected boolean existsTable(Connection conn, String tableName) {
        try {
            ResultSet res = conn.getMetaData().getTables(null, null, PostgresIdentifier.escapedQuotedId(tableName), new String[]{"TABLE"});
            return res.next();
        }
        catch (SQLException e) {
            throw new IllegalStateException("Could not find out if table " + tableName + " exists: " + e);
        }
    }

    static protected boolean hasIndex(Connection conn, String tableName) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet res = meta.getIndexInfo(null, null, PostgresIdentifier.escapedQuotedId(tableName), true, true);
            return res.next();
        }
        catch (SQLException e) {
            throw new IllegalStateException("Could not find out if an index exists on " + tableName + ": " + e);
        }
    }

    void exportRepresentationFunctionToDatabaseAndComputeSummaryNodeStatistics(Connection conn, Statement stmt, String newSummaryTableNameRep, HashMap<Long, Long> summaryNodeStatisticsForDB) {
        try {
            long start = System.currentTimeMillis();
            // create the table (it may have existed)
            if (!existsTable(conn, newSummaryTableNameRep)) {
                stmt.execute("create table " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameRep) + "(graphNode int not null, summaryNode int not null)");
                //LOGGER.debug("Table " + newSummaryTableNameRep + " created");
            }
            //else {
            //LOGGER.debug("Did not create " + newSummaryTableNameRep + " table as it was already there");
            //}
            // empty it (even if the creation failed, e.g. because the table was already there)
            stmt.executeUpdate("delete from " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameRep));

            // now insert all the rep entries:
            String insertIntoRep = "insert into " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameRep) + " values(?, ?)";
            try (PreparedStatement insertInRep = conn.prepareStatement(insertIntoRep)) {
                long sumNode;
                for (long origNode : rep.getKeys()) {
                    sumNode = rep.get(origNode);
                    insertInRep.setLong(1, origNode);
                    insertInRep.setLong(2, sumNode);
                    insertInRep.executeUpdate();

                    // update the node statistics
                    summaryNodeStatisticsForDB.merge(sumNode, 1L, Long::sum);
                }
            }
            // if (!hasIndex(conn, "encoded_rep"))
            //	stmt.executeUpdate("create index indRepS on encoded_rep(graphNode)");
            // This gives some errors in the JDBC driver, perhaps it is not implemented properly.
            conn.commit();
            representationFunctionSavingExportingTime += System.currentTimeMillis() - start;
            LOGGER.info("Representation function exported in " + representationFunctionSavingExportingTime + " ms");
        }
        catch (SQLException e) {
            throw new IllegalStateException("Could not insert summary triples in " + newSummaryTableNameRep + ": " + e);
        }
    }

    void exportSummaryNodeStatisticsToDatabase(Connection conn, Statement stmt, String newSummaryTableNameNodeStats, HashMap<Long, Long> summaryNodeStatisticsForDB) {
        try {
            long start = System.currentTimeMillis();
            // create the table (it may have existed)
            if (!existsTable(conn, newSummaryTableNameNodeStats)) {
                stmt.execute("create table " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameNodeStats) + "(snode int not null, count int not null)");
                //LOGGER.debug("Table " + newSummaryTableNodeStats + " created");
            }
            //else {
            //LOGGER.debug("Did not create " + newSummaryTableNodeStats + " table as it was already there");
            //}
            // empty it (even if the creation failed, e.g. because the table was already there)
            stmt.executeUpdate("delete from " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameNodeStats));
            conn.commit();

            String insertIntoNodeStats = "insert into " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameNodeStats) + " values(?, ?)";
            try (PreparedStatement insertInNodeStats = conn.prepareStatement(insertIntoNodeStats)) {
                for (Long sumNode : summaryNodeStatisticsForDB.keySet()) {
                    Long nodeCount = summaryNodeStatisticsForDB.get(sumNode);
                    //LOGGER.debug("Saving in Postgres node statistics for: " + sumNode);
                    insertInNodeStats.setLong(1, sumNode);
                    insertInNodeStats.setLong(2, nodeCount);
                    insertInNodeStats.executeUpdate();
                }
                // if (!hasIndex(conn, "encoded_summary"))
                //	stmt.executeUpdate("create index indSummaryS on encoded_summary(s)");
                conn.commit();
                long summaryNodeStatsSavingTime = System.currentTimeMillis() - start;
                LOGGER.info("Exported " + summaryNodeStatisticsForDB.size() + " node statistics in " + summaryNodeStatsSavingTime + " ms");
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Could not export node statistics in " + newSummaryTableNameNodeStats + ": " + e);
        }
    }

    void exportSummaryEdgesToDatabase(Connection conn, Statement stmt, String newSummaryTableNameEdges, HashMap<Triple, Long> summaryEdgeStatisticsForDB) {
        try {
            long start = System.currentTimeMillis();
            // create the table (it may have existed)
            if (!existsTable(conn, newSummaryTableNameEdges)) {
                stmt.execute("create table " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameEdges) + "(s int not null, p int not null, o int not null, count int not null)");
                //LOGGER.debug("Table " + newSummaryTableNameEdges + " created");
            }
            //else {
            //LOGGER.debug("Did not create " + newSummaryTableNameEdges + " table as it was already there");
            //}
            // empty it (even if the creation failed, e.g. because the table was already there)
            stmt.executeUpdate("delete from " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameEdges));
            conn.commit();

            // now insert all the summary edges
            String insertIntoSummary = "insert into " + PostgresIdentifier.escapedQuotedId(newSummaryTableNameEdges) + " values(?, ?, ?, ?)";
            long represented;
            try (PreparedStatement insertInSummary = conn.prepareStatement(insertIntoSummary)) {
                ArrayList<Triple> edges = edgesWithProv.getSummaryEdges();
                for (Triple t : edges) {
                    //LOGGER.debug("Saving in Postgres edge: " + t.toString());
                    insertInSummary.setLong(1, t.s);
                    insertInSummary.setLong(2, t.p);
                    insertInSummary.setLong(3, t.o);
                    represented = edgesWithProv.getCounter(t.s, t.p, t.o);
                    insertInSummary.setLong(4, represented);
                    summaryEdgeStatisticsForDB.put(t, represented);
                    insertInSummary.executeUpdate();
                }
                // if (!hasIndex(conn, "encoded_summary"))
                //	stmt.executeUpdate("create index indSummaryS on encoded_summary(s)");
                conn.commit();
                summaryEdgesSavingTime += System.currentTimeMillis() - start;
                LOGGER.info("Exported " + edges.size() + " summary edges in " + summaryEdgesSavingTime + " ms");
            }
        }
        catch (SQLException e) {
            throw new IllegalStateException("Could not insert summary triples in " + newSummaryTableNameEdges + ": " + e);
        }
    }

    /**
     * Exports a summary into new Postgres tables:
     * 1. the rep table: the representation function
     * 2. the edges table: the summary edges encoded as integers
     * 3. the nodeStats table: the counts of how many nodes a summary node
     * represents
     * The property and class URIs here are encoded exactly like in the input.
     * The subjects and objects in the summary edges are just "new integer
     * codes".
     */
    public void exportSummaryToDatabase(Connection conn, boolean exportRepresentationFunctionAndSummaryNodeStatistics) {
        try {
            conn.setAutoCommit(false);
        }
        catch (SQLException ex) {
            LOGGER.error(ex);
        }
        String timestamp = SD_FORMAT.format(new Timestamp(System.currentTimeMillis()));
        boolean summarizeSaturatedGraph = summarizationProperties.getProperty("summary.summarize_saturated_graph").equals("true");
        String newTableName = "summary_" + timestamp + (summarizeSaturatedGraph ? "_sat" : "") + "_" + getSummaryTablePrefix();

        String newSummaryTableNameRep = newTableName + "_rep";
        // Ioana, Sept 25, 2018: we need the following line in order for the drawing with split leaves
        // to know where to look for the representation table
        this.repTableName = newSummaryTableNameRep;
        String newSummaryTableNameNodeStats = newTableName + "_nodeStats";
        String newSummaryTableNameEdges = newTableName + "_edges";
        LOGGER.info("Exporting the " + this.getClass().getSimpleName() + " representation function to database in table: " + newSummaryTableNameRep);
        LOGGER.info("Exporting the " + this.getClass().getSimpleName() + " node statistics to database in table: " + newSummaryTableNameNodeStats);
        LOGGER.info("Exporting the " + this.getClass().getSimpleName() + " edges with statistics to database in table: " + newSummaryTableNameEdges);

        HashMap<Long, Long> summaryNodeStatisticsForDB = new HashMap<>();
        HashMap<Triple, Long> summaryEdgeStatisticsForDB = new HashMap<>();

        Statement stmt;
        try {
            stmt = conn.createStatement();
        }
        catch (SQLException e) {
            throw new IllegalStateException("Could not create the statement: " + e);
        }

        exportRepresentationFunctionToDatabaseAndComputeSummaryNodeStatistics(conn, stmt, newSummaryTableNameRep, summaryNodeStatisticsForDB);
        if (exportRepresentationFunctionAndSummaryNodeStatistics) {
            exportSummaryNodeStatisticsToDatabase(conn, stmt, newSummaryTableNameNodeStats, summaryNodeStatisticsForDB);
            // spare some computation later
            summaryNodeStatistics = summaryNodeStatisticsForDB;
        }

        exportSummaryEdgesToDatabase(conn, stmt, newSummaryTableNameEdges, summaryEdgeStatisticsForDB);
        // spare some computation later
        summaryEdgeStatistics = summaryEdgeStatisticsForDB;

        // saving the table names in Postgres
        try {
            stmt.executeUpdate("create table if not exists saved_summary_table_names(role varchar, name varchar)");
            stmt.executeUpdate("insert into saved_summary_table_names values ('encoded_triples', '" + encodedTriplesTableName + "')");
            stmt.executeUpdate("insert into saved_summary_table_names values ('dictionary', '" + dictionaryTableName + "')");
            stmt.executeUpdate("insert into saved_summary_table_names values ('representation', '" + newSummaryTableNameRep + "')");
            stmt.executeUpdate("insert into saved_summary_table_names values ('summary_node_stats', '" + newSummaryTableNameNodeStats + "')");
            stmt.executeUpdate("insert into saved_summary_table_names values ('edges', '" + newSummaryTableNameEdges + "')");
            stmt.executeUpdate("insert into saved_summary_table_names values ('default_type_URI', '" + defaultTypeURI + "')");
            StringBuilder sb = new StringBuilder();
            String prefix = "";
            for (String variantTypeURI: variantTypeURIs) {
                sb.append(prefix).append(variantTypeURI);
                prefix = ",";
            }
            stmt.executeUpdate("insert into saved_summary_table_names values ('variant_type_URIs', '" + sb + "')");
            conn.commit();
        }
        catch (SQLException e) {
            throw new IllegalStateException("Could not create table_names: " + e);
        }
    }

    public void exportRepresentationFunctionToNTFile(Connection conn, String exportRepresentationFunctionToNTFilename) {
        if (!exportRepresentationFunctionToNTFilename.equals("")) {
            ensureExporter();
            exporter.exportRepresentationFunctionToNTFile(conn, exportRepresentationFunctionToNTFilename);
        }
    }

    public void exportNodeStatisticsToNTFile(Connection conn, boolean representationCountsAlreadyComputed, String exportNodeStatisticsToNTFilename) {
        if (!exportNodeStatisticsToNTFilename.equals("")) {
            ensureExporter();
            exporter.exportNodeStatisticsToNTFile(conn, representationCountsAlreadyComputed, exportNodeStatisticsToNTFilename);
        }
    }

    public void exportEdgeStatisticsToNTFile(Connection conn, String exportEdgeStatisticsToNTFilename) {
        if (!exportEdgeStatisticsToNTFilename.equals("")) {
            ensureExporter();
            exporter.exportEdgeStatisticsToNTFile(conn, exportEdgeStatisticsToNTFilename);
        }
    }

    public void drawGraphAndSummary(Connection conn, String suffix) {
        ensureExporter();

        String drawingStyle = summarizationProperties.getProperty("drawing.style");
        if (!drawingStyle.equals("plain")) {
            LOGGER.warn("Drawing style for step-by-step drawing set to plain, despite configuration set to: " + drawingStyle);
            // change to plain layout
            summarizationProperties.put("drawing.style", "plain");
        }

        String graphDOTFileName = exporter.getDOTFileName(false, suffix);
        String graphPNGFileName = exporter.getPNGFileName(false, suffix);
        exporter.writeRDFGraphToDOTFile(conn, graphDOTFileName, graphPNGFileName);

        if (gatherStatistics) {
            gatherNodeStatistics();
            gatherEdgeStatistics();
        }
        String summaryDOTFileName = exporter.getDOTFileName(true, suffix);
        String summaryPNGFileName = exporter.getPNGFileName(true, suffix);
        exporter.writeSummaryToDOTFile(conn, summaryDOTFileName, summaryPNGFileName);

        // revert changes
        summarizationProperties.put("drawing.style", drawingStyle);
    }

    /**
     * Writes the summary in RDF (in .nt format) then also in DOT (.dot format); also attempts to draw it using DOT.
     */
    public void writeEncodedSummaryToFileAndDraw(Connection conn) {
        ensureExporter();
        addIndexesToRepTable(conn);

        exporter.writeEncodedSummaryToFile(exporter.getNTSummaryFileName());

        String summaryDOTFileName = exporter.getDOTFileName(true, "encoded");
        String summaryPNGFileName = exporter.getPNGFileName(true, "encoded");
        exporter.writeEncodedSummaryToDOTFile(summaryDOTFileName, summaryPNGFileName);
    }

    public String writeDecodedSummaryToNTFile(Connection conn) {
        ensureExporter();
        return exporter.writeDecodedSummaryToNTFile(conn);
    }

    /**
     * Writes the summary in DOT (.dot format), then also attempts to draw it
     * using DOT according to specified summaryDrawingStyle adding prefixes to
     * DOT and PNG filenames
     */
    public String writeDecodedSummaryToDOTFile(Connection conn, String summaryDrawingStyle) {
        ensureExporter();
        addIndexesToRepTable(conn);

        if (summarizationProperties.getProperty("drawing.draw_input_graph").equals("true")) {
            String style = summarizationProperties.getProperty("drawing.style");
            summarizationProperties.put("drawing.style", "input_graph");
            String graphDOTFileName = exporter.getDOTFileName(false, "");
            String graphPNGFileName = exporter.getPNGFileName(false, "");
            exporter.writeRDFGraphToDOTFile(conn, graphDOTFileName, graphPNGFileName);
            summarizationProperties.put("drawing.style", style);
        }

        String summaryDOTFileName = exporter.getDOTFileName();
        String summaryPNGFileName = exporter.getPNGFileName();
        switch (summaryDrawingStyle) {
            case "plain":
                exporter.writeSummaryToDOTFile(conn, summaryDOTFileName, summaryPNGFileName);
                break;
            case "split_leaves":
                exporter.writeSummaryToDOTFileSplitLeaves(conn, summaryDOTFileName, summaryPNGFileName);
                break;
            case "split_and_fold_leaves":
                exporter.writeSummaryToDOTFileSplitAndFoldLeaves(conn, summaryDOTFileName, summaryPNGFileName);
                break;
            default:
                summaryDOTFileName = null;
        }
        return summaryDOTFileName;
    }

    public void display() {
        System.out.println("SUMMARY " + this.getClass().getName());
        edgesWithProv.display();
        System.out.println("REPRESENTATION: " + rep.toString());
        System.out.println("=======");
    }

    public Long getTriplesSummarizedSoFar() {
        return triplesSummarizedSoFar;
    }

    public HashSet<Long> getGenericPropertiesIgnoredInCliques() {
        return genericPropertiesIgnoredInCliques;
    }

    public void setNumberOfLeaves(long newNumberOfLeaves) {
        numberOfLeaves = newNumberOfLeaves;
    }

    protected final String getSummaryTriplesSQLQuery() {
        return "select * from " + PostgresIdentifier.escapedQuotedId(edgeTableName);
    }

    public final String getEncodedRepSQLQuery() {
        return "select summarynode from " + PostgresIdentifier.escapedQuotedId(repTableName) + " where graphnode=?";
    }

    public String getSummaryTablePrefix() {
        if (this.summaryTablePrefix.equals("")) {
            throw new IllegalStateException("The method should not be called on an instance of the root Summary type");
        }
        return summaryTablePrefix;
    }

    // whether or not a certain property is generic
    public boolean isGeneric(Long p) {
        return this.genericPropertiesIgnoredInCliques.contains(p);
    }

    public HashMap<String, String> getRunStatistics() {
        HashMap<String, String> stats = new HashMap<>();

        stats.put("inputFileName", triplesFileName);
        stats.put("summaryType", getSummaryTablePrefix());

        stats.put("summaryEdgesSavingTime", Long.toString(summaryEdgesSavingTime));
        stats.put("representationFunctionSavingTime", Long.toString(representationFunctionSavingExportingTime));

        stats.put("schemaNodesCollectionTime", Long.toString(schemaNodesCollectionTime));
        stats.put("classSetCreationTime", Long.toString(classSetCreationTime));
        stats.put("typeTriplesSummarizationTime", Long.toString(typeTriplesSummarizationTime));
        stats.put("nonTypeTriplesSummarizationTime", Long.toString(nonTypeTriplesSummarizationTime));
        stats.put("dataAndTypeTriplesCollectionTime", Long.toString(dataAndTypeTriplesSummarizationTime));
        stats.put("genericPropertyTriplesSummarizationTime", Long.toString(genericPropertyTriplesSummarizationTime));
        stats.put("allTriplesSummarizationTime", Long.toString(allTriplesSummarizationTime));

        stats.put("inputGraphSize", Long.toString(triplesSummarizedSoFar));
        stats.put("inputGraphTypeTriples", Long.toString(typeTriplesSummarizedSoFar));
        stats.put("inputGraphNonTypeTriples", Long.toString(nonTypeTriplesSummarizedSoFar));
        stats.put("dataAndTypeTriplesSummarizedSoFar", Long.toString(dataAndTypeTriplesSummarizedSoFar));
        stats.put("outputGraphSize", Integer.toString(edgesWithProv.getSummaryEdges().size()));

        stats.put("inputGraphNumberOfNodes", Long.toString(rep.numberOfKeys()));
        stats.put("outputGraphNumberOfNodes", Long.toString(rep.numberOfDistinctValues()));

        stats.put("outputGraphNumberOfLeaves", Long.toString(numberOfLeaves));

        return stats;
    }

    protected void displayClique(HashSet<Long> clique) {
        System.out.println(showCliqueAsString(clique));
    }

    protected String showCliqueAsString(HashSet<Long> clique) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
		for (long l : clique) {
			sb.append(l).append("(").append(RDF2SQLEncoding.dictionaryDecode(l)).append(") ");
		}
        sb.append("]");
        return new String(sb);
    }

    protected HashMap<Long, HashSet<Long>> getEdgesFrom(long s) {
        return this.edgesWithProv.get(s);
    }

    protected HashMap<Long, HashSet<Long>> getEdgesTo(long o) {
        HashMap<Long, HashSet<Long>> res = new HashMap<>();
        for (long s : edgesWithProv.keySet()) {
            for (long p : edgesWithProv.get(s).keySet()) {
                // if there is an edge s--p-->o
                if (edgesWithProv.get(s).get(p).contains(o)) {
                    // the first edge labeled p which goes into o
                    HashSet<Long> onP = res.computeIfAbsent(p, k -> new HashSet<>());

                    onP.add(s); // add s on p in the result
                }
            }
        }
        return res;
    }

    public String getEdgesToString() {
        StringBuilder sb = new StringBuilder();
        for (Triple t : edgesWithProv.getSummaryEdges()) {
            sb.append(t.toString()).append(" ");
        }
        return new String(sb);
    }

    public ArrayList<Triple> getSummaryEdges() {
        return edgesWithProv.getSummaryEdges();
    }

    public HashMap<Long, Long> getSummaryNodeStatistics() {
        return this.summaryNodeStatistics;
    }

    public HashMap<Triple, Long> getSummaryEdgeStatistics() {
        return this.summaryEdgeStatistics;
    }

    public String getEncodedTriplesTableName() {
        return this.encodedTriplesTableName;
    }

    public String getRepresentationTableName() {
        return this.repTableName;
    }

    public String getDictionaryTableName() {
        return this.dictionaryTableName;
    }

    public boolean isTypeFirst() {
        return this.isTypeFirst;
    }

    public long getRepresentative(long l) {
        return rep.get(l);
    }

    public HashSet<Long> getSchemaNodes() {
        return sn;
    }

    public boolean generalizeTypes() {
        return this.replaceTypeWithMostGeneralType;
    }

    public Long getRepresentedNodeNumber(Long s) {
        Long res = summaryNodeStatistics.get(s);
        if (res != null) {
            return res;
        }
        else {
            return 0L;
        }
    }

    public Long getRepresentedTripleNumber(Triple t) {
        Long res = summaryEdgeStatistics.get(t);
        if (res != null) {
            return res;
        }
        else {
            return 0L;
        }
    }

    /**
     * If we are using type generalization, we need to separately account for the
     * actual types of the nodes. This method returns the types and respective cardinalities
     * of the data nodes corresponding to a summary node.
     *
     * @param s the data node
     * @return a map of the form type --> cardinality
     */
    public HashMap<Long, Long> getActualTypesWithStatistics(long s) {
        return this.summaryNodeToActualTypeToCardinality.get(s);
    }

    public int getMaxTypesDisplayedPerNameSpace() {
        return Integer.parseInt(summarizationProperties.getProperty("drawing.max_types_drawn_per_namespace"));
    }
}
