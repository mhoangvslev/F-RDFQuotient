//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient;

import fr.inria.cedar.RDFQuotient.datastructures.EdgesWithProvenanceCounts;
import fr.inria.cedar.RDFQuotient.datastructures.Long2Long;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import fr.inria.cedar.RDFQuotient.util.PostgresIdentifier;
import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Scanner;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public abstract class Traverser {
	private static final Logger LOGGER = Logger.getLogger(Traverser.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	protected final Summary summ;
	protected final Connection conn;
	protected long setupTime;
	protected HashSet<Long> allTypeConstantCodes;
	protected long subClassCode;
	protected long subPropertyCode;
	protected long domainCode;
	protected long rangeCode;

	protected long numberOfSummaryNodesSoFar;
	protected long numberOfSummaryEdgesSoFar;

	public Traverser(Summary summ, Connection conn) {
		this.summ = summ;
		this.conn = conn;
		numberOfSummaryNodesSoFar = 0;
		numberOfSummaryEdgesSoFar = 0;
	}

	protected void schemaNodesCollection() {
		long start = System.currentTimeMillis();
		summ.collectSchemaNodes(conn);
		summ.schemaNodesCollectionTime += System.currentTimeMillis() - start;

		summ.avoidCollisionsWhenAssigningSummaryNodes(conn);
	}

	protected void setUp() {
		schemaNodesCollection();

		long start = System.currentTimeMillis();
		try {
			conn.setAutoCommit(false);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}

		// this is needed to find the constants associated to special RDF properties
		summ.setTypeURIs();
		RDF2SQLEncoding.setUp(conn, summ.dictionaryTableName, summ.defaultTypeURI, summ.variantTypeURIs);
		allTypeConstantCodes = RDF2SQLEncoding.getAllTypeCodes();
		subClassCode = RDF2SQLEncoding.getSubClassCode();
		subPropertyCode = RDF2SQLEncoding.getSubPropertyCode();
		domainCode = RDF2SQLEncoding.getDomainCode();
		rangeCode = RDF2SQLEncoding.getRangeCode();

		// clean up
		RDF2SQLEncoding.dropUserTriplesTable();
		RDF2SQLEncoding.dropEncodedUserTriplesTable();

		summ.setUpClassFieldsDependingOnProperties();
		setupTime = System.currentTimeMillis() - start;
	}

	protected void haltStepByStep() {
		LOGGER.info("Press ENTER to continue");
		try {
			System.in.read();
		}
		catch (IOException ex) {
			LOGGER.error(ex);
		}
	}

	protected Triple haltStepByStepAndAskForTripleFromUser(boolean typeTriple) {
		LOGGER.info("Press ENTER to continue, press n to add new " + (typeTriple ? "" : "non-") + "type triple");
		Scanner scanner = new Scanner(System.in);
		String line = scanner.nextLine();
		if (line.equals("n")) {
			LOGGER.info("Type a new triple:");
			String userTriple = scanner.nextLine();

			if (!userTriple.matches(
				// subject: IRI or blank node
				"(<.*>|_:\\p{Alnum}+)" +
				// property: IRI or blank node
				"([ \t])(<.*>|_:\\p{Alnum}+)" +
				// object: IRI, blank node or literal
				"([ \t])(<.*>|_:\\p{Alnum}+|\"(?:[^\"\\\\]|\\\\.)*\")" +
				// language tag and/or datatype IRI
				"(@\\p{Alnum}+(-\\p{Alnum}+)?|\\^\\^<.*>)*" +
				// dot after the triple
				"([ \t])\\." +
				// comments
				"(\\s+#\\s.*)?")) {
				LOGGER.error("Wrong format of the input triple, retry");
				return haltStepByStepAndAskForTripleFromUser(typeTriple);
			}
			String[] userTripleSplit = userTriple.split("\\s+");
			String s = userTripleSplit[0];
			String p = userTripleSplit[1];
			String o = userTripleSplit[2];

			long sEncoded = -1;
			long pEncoded = -1;
			long oEncoded = -1;
			try {
				sEncoded = RDF2SQLEncoding.dictionaryEncode(s);
			}
			catch (IllegalStateException ex) {
				LOGGER.error(ex);
			}
			if (sEncoded == -1) {
				sEncoded = RDF2SQLEncoding.addNewEntryToDictionary(s, summ.dictionaryTableName);
			}
			try {
				pEncoded = RDF2SQLEncoding.dictionaryEncode(p);
			}
			catch (IllegalStateException ex) {
				LOGGER.error(ex);
			}
			if (pEncoded == -1) {
				pEncoded = RDF2SQLEncoding.addNewEntryToDictionary(p, summ.dictionaryTableName);
			}
			try {
				oEncoded = RDF2SQLEncoding.dictionaryEncode(o);
			}
			catch (IllegalStateException ex) {
				LOGGER.error(ex);
			}
			if (oEncoded == -1) {
				oEncoded = RDF2SQLEncoding.addNewEntryToDictionary(o, summ.dictionaryTableName);
			}

			HashSet<Long> typeCodes = RDF2SQLEncoding.getAllTypeCodes();
			long classCode = RDF2SQLEncoding.getClassCode();
			long propertyCode = RDF2SQLEncoding.getPropertyCode();
			if (typeTriple && !(typeCodes.contains(pEncoded))) {
				LOGGER.error("Expected type triple, addition cancelled, retry");
				return haltStepByStepAndAskForTripleFromUser(typeTriple);
			}
			if (!typeTriple && typeCodes.contains(pEncoded)) {
				LOGGER.error("Expected type non-triple, addition cancelled, retry");
				return haltStepByStepAndAskForTripleFromUser(typeTriple);
			}

			RDF2SQLEncoding.createIfNotExistsUserTriplesTable();
			RDF2SQLEncoding.createIfNotExistsUserEncodedTriplesTable();
			RDF2SQLEncoding.addNewTripleToUserTriplesTable(s, p, o, summ.triplesTableName);
			RDF2SQLEncoding.addNewTripleToUserEncodedTriplesTable(sEncoded, pEncoded, oEncoded, summ.triplesSummarizedSoFar, summ.encodedTriplesTableName);

			if (RDF2SQLEncoding.isSchemaProperty(pEncoded)) {
				summ.sn.add(sEncoded);
				summ.rep.put(sEncoded, sEncoded);
				summ.sn.add(oEncoded);
				summ.rep.put(oEncoded, oEncoded);
			}
			if (typeCodes.contains(pEncoded)) {
				summ.sn.add(oEncoded);
				summ.rep.put(oEncoded, oEncoded);
				if (oEncoded == classCode || oEncoded == propertyCode) {
					summ.sn.add(sEncoded);
					summ.rep.put(sEncoded, sEncoded);
				}
			}

			return new Triple(sEncoded, pEncoded, oEncoded);
		}
		return null;
	}

	protected void drawStepByStep(String mode) {
		long numberOfSummaryEdges = summ.edgesWithProv.numberOfDistinctEdges();
		Long2Long repCopy = null;
		EdgesWithProvenanceCounts edgesCopy = null;
		if (summ.isTypeFirst() && numberOfSummaryEdges == 0) {
			repCopy = new Long2Long(summ.rep);
			edgesCopy = new EdgesWithProvenanceCounts(summ.edgesWithProv);
			summ.representTypeTriplesBeforeData();
		}

		long currentNumberOfSummaryNodes = summ.rep.numberOfDistinctValues();
		long currentNumberOfSummaryEdges = summ.edgesWithProv.numberOfDistinctEdges();
		if ((mode.equals("always") || (mode.equals("when_changes")
			&& (numberOfSummaryNodesSoFar != currentNumberOfSummaryNodes
			|| numberOfSummaryEdgesSoFar != currentNumberOfSummaryEdges)))
			&& currentNumberOfSummaryEdges > 0) {
			numberOfSummaryNodesSoFar = currentNumberOfSummaryNodes;
			numberOfSummaryEdgesSoFar = currentNumberOfSummaryEdges;
			String numberOfTriples = String.format((Locale) null, "%03d", summ.triplesSummarizedSoFar);
			summ.drawGraphAndSummary(conn, "_after_" + numberOfTriples + "_triples");
		}

		// revert changes
		if (summ.isTypeFirst() && numberOfSummaryEdges == 0) {
			summ.rep = repCopy;
			summ.edgesWithProv = edgesCopy;
		}
	}

	protected void drawStepByStep(Triple t) {
		String numberOfTriples = String.format((Locale) null, "%09d", summ.triplesSummarizedSoFar);
		summ.drawGraphAndSummary(conn, "_after_" + numberOfTriples + "-" + t.s + "-" + t.p + "-" + t.o);
	}

	protected void mostGeneralTypePass() {
		long start = System.currentTimeMillis();
		try {
			summ.computeMostGeneralType(conn);
		}
		catch (IllegalStateException ex) {
			LOGGER.error(ex);
		}
		summ.typeTriplesSummarizationTime += System.currentTimeMillis() - start;
	}

	// data and schema triples
	protected void dataPass() {
		long start = System.currentTimeMillis();
		StringBuilder getUntypedTriplesString = new StringBuilder();
		getUntypedTriplesString.append("select * from ").append(PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName))
				.append(" where ");
		String prefix = "";
		for (long typeCode: allTypeConstantCodes) { // allTypeConstantCodes guaranteed to be non-empty
			getUntypedTriplesString.append(prefix).append("p <> ").append(typeCode);
			prefix = " and ";
		}
		getUntypedTriplesString.append(summ.summarizationProperties.getProperty("database.deterministic_ordering").equals("false") ? "" : "order by s, p, o");

		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString.toString())) {
					do {
						Triple t = null;

						if (summ.haltStepByStep) {
							t = haltStepByStepAndAskForTripleFromUser(false);
						}
						if (t == null) {
							if (rs.next()) {
								t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
							}
							else {
								if (summ.haltStepByStep) {
									LOGGER.info("No more data triples");
								}
								break;
							}
						}

						if ((t.p == subClassCode)
							|| (t.p == subPropertyCode)
							|| (t.p == domainCode)
							|| (t.p == rangeCode)) { // schema triple
							// s and o represented in collectSchemaNodes
							summ.edgesWithProv.addTriple(summ.rep.get(t.s), t.p, summ.rep.get(t.o));
							summ.triplesSummarizedSoFar++;
							summ.nonTypeTriplesSummarizedSoFar++;
						}
						else { // data triple
							if (summ.genericPropertiesIgnoredInCliques.contains(t.p)) {
								summ.genericPropertyTriples.add(t);
							}
							else {
								summ.handleDataTriple(t);
								summ.triplesSummarizedSoFar++;
								summ.nonTypeTriplesSummarizedSoFar++;
							}
						}
						if (summ.checkConsistency) {
							summ.consistencyChecks();
						}
						if (summ.drawStepByStep.equals("true")) {
							drawStepByStep(summ.drawStepByStep);
						}
					}
					while (true);
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e);
		}

		summ.nonTypeTriplesSummarizationTime = System.currentTimeMillis() - start;
	}

	// first pass
	protected void dataTriplesClassification() {
		long start = System.currentTimeMillis();

		StringBuilder getUntypedTriplesString = new StringBuilder();
		getUntypedTriplesString.append("select * from ").append(PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName))
				.append(" where ");
		String prefix = "";
		for (long typeCode: allTypeConstantCodes) { // allTypeConstantCodes guaranteed to be non-empty
			getUntypedTriplesString.append(prefix).append("p <> ").append(typeCode);
			prefix = " and ";
		}
		getUntypedTriplesString.append(summ.summarizationProperties.getProperty("database.deterministic_ordering").equals("false") ? "" : "order by s, p, o");

		Triple t;
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString.toString())) {
					while (rs.next()) {
						t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if ((t.p == subClassCode)
							|| (t.p == subPropertyCode)
							|| (t.p == domainCode)
							|| (t.p == rangeCode)) { // schema triple
							// s and o represented in collectSchemaNodes
							summ.edgesWithProv.addTriple(summ.rep.get(t.s), t.p, summ.rep.get(t.o));
						}
						else { // data triple
							if (summ.genericPropertiesIgnoredInCliques.contains(t.p)) {
								summ.genericPropertyTriples.add(t);
							}
							else {
								summ.classifyDataTriple(t);
							}
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e);
		}

		summ.classificationPostProcessing();

		summ.nonTypeTriplesSummarizationTime = System.currentTimeMillis() - start;
	}

	// second pass
	protected void dataTriplesRepresentation() {
		long start = System.currentTimeMillis();

		StringBuilder getUntypedTriplesString = new StringBuilder();
		getUntypedTriplesString.append("select * from ").append(PostgresIdentifier.escapedQuotedId(summ.encodedTriplesTableName))
				.append(" where ");
		String prefix = "";
		for (long typeCode: allTypeConstantCodes) { // allTypeConstantCodes guaranteed to be non-empty
			getUntypedTriplesString.append(prefix).append("p <> ").append(typeCode);
			prefix = " and ";
		}
		getUntypedTriplesString.append(summ.summarizationProperties.getProperty("database.deterministic_ordering").equals("false") ? "" : "order by s, p, o");

		Triple t;
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString.toString())) {
					while (rs.next()) {
						if (summ.haltStepByStep) {
							haltStepByStep();
						}
						t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if (summ.genericPropertiesIgnoredInCliques.contains(t.p)) { // avoid generic property triples, they will be represented later
							continue;
						}
						if ((t.p != subClassCode)
							&& (t.p != subPropertyCode)
							&& (t.p != domainCode)
							&& (t.p != rangeCode)) { // data triple
							summ.representDataTriple(t);
							// a literal object may have been represented under a resolved (possibly
							// synthetic per-occurrence) id rather than its own raw id - see
							// Summary.resolveObjectNodeId and usesLiteralOccurrenceResolution
							long representedO = summ.usesLiteralOccurrenceResolution ? summ.resolveObjectNodeId(t.s, t.o) : t.o;
							summ.edgesWithProv.addTriple(summ.rep.get(t.s), t.p, summ.rep.get(representedO));
						}
						summ.triplesSummarizedSoFar++;
						summ.nonTypeTriplesSummarizedSoFar++;
						if (summ.checkConsistency) {
							summ.consistencyChecks();
						}
						if (summ.drawStepByStep.equals("true")) {
							drawStepByStep(summ.drawStepByStep);
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e);
		}

		summ.nonTypeTriplesSummarizationTime += System.currentTimeMillis() - start;
	}

	// ommitted generic property triples
	public void genericPropertyTriplesPass() {
		long start = System.currentTimeMillis();
		summ.prepareRepresentationOfGenericPropertyTriples();
		LOGGER.info("Starting final pass on " + summ.genericPropertyTriples.size() + " generic property triples");
		for (Triple t: summ.genericPropertyTriples) {
			//LOGGER.info("Generic triple: " + t.toString());
			summ.representGenericPropertyTriple(t);
			summ.triplesSummarizedSoFar++;
			summ.typeTriplesSummarizedSoFar++;
			if (summ.checkConsistency) {
				summ.consistencyChecks();
			}
		}

		summ.genericPropertyTriplesSummarizationTime = System.currentTimeMillis() - start;
	}

	// type triples
	protected void typePass() {
	}

	public abstract void traverseAllTriples();
}
