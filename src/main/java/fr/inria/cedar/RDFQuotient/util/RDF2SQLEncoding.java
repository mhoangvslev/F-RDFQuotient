//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.util;

import fr.inria.cedar.RDFQuotient.datastructures.DecodedTriple;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * This class serves exactly to encode / decode the five special RDF properties we are interested in
 *
 * @author ioanamanolescu
 *
 */
public class RDF2SQLEncoding {
	private static final Logger LOGGER = Logger.getLogger(RDF2SQLEncoding.class.getName());
	private static long defaultTypeCode = -1; // this is the long associated by OntoSQL to rdf:type.
	private static HashSet<Long> allTypeCodes;
	private static long subClassCode = -1;
	private static long subPropertyCode = -1;
	private static long domainCode = -1;
	private static long rangeCode = -1;
	private static long classCode = -1;
	private static long propertyCode = -1;
	private static HashMap<Long, String> codeToURIOrLiteral = new HashMap<>();
	private static HashMap<String, Long> uriOrLiteralToCode = new HashMap<>();
	private static Connection conn;
	private static PreparedStatement stmtDecode;
	private static PreparedStatement stmtEncode;

	static {
		LOGGER.setLevel(Level.INFO);
	}

	public RDF2SQLEncoding() {
	}

	/**
	 * It is crucial to call this method in order for the summarization or any summary usage code to work OK.
	 *
	 * @param givenConn
	 * @param dictionaryTableName
	 */
	public static void setUp(Connection givenConn, String dictionaryTableName, String defaultTypeURI, String[] variantTypeURIs) {
		conn = givenConn;
		try {
			stmtDecode = conn.prepareStatement("select value from " + PostgresIdentifier.escapedQuotedId(dictionaryTableName) + " where key=?");
			stmtEncode = conn.prepareStatement("select key from " + PostgresIdentifier.escapedQuotedId(dictionaryTableName) + " where value=?");
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not prepare encode/decode statements " + e);
		}
		codeToURIOrLiteral = new HashMap<>();
		uriOrLiteralToCode = new HashMap<>();
		setRDFBuiltInPropertyCodes();
		setTypeCodes(defaultTypeURI, variantTypeURIs);
	}

	public static Connection getConnection() {
		return conn;
	}

	public static void setConnection(Connection givenConn) {
		conn = givenConn;
	}

	public static long getDefaultTypeCode() {
		return defaultTypeCode;
	}

	public static HashSet<Long> getAllTypeCodes() {
		return allTypeCodes;
	}

	public static long getSubClassCode() {
		return subClassCode;
	}

	public static long getDomainCode() {
		return domainCode;
	}

	public static long getSubPropertyCode() {
		return subPropertyCode;
	}

	public static long getRangeCode() {
		return rangeCode;
	}

	public static long getClassCode() {
		return classCode;
	}

	public static long getPropertyCode() {
		return propertyCode;
	}

	public static void setRDFBuiltInPropertyCodes() {
		setSubClassCode();
		//LOGGER.debug("rdfs:subClassOf code is: " + subClassCode);
		setSubPropertyCode();
		//LOGGER.debug("rdfs:subPropertyOf code is: " + subPropertyCode);
		setDomainCode();
		//LOGGER.debug("rdfs:domain code is: " + domainCode);
		setRangeCode();
		//LOGGER.debug("rdfs:range code is: " + rangeCode);
		setClassCode();
		setPropertyCode();
	}

	public static void setTypeCodes(String defaultTypeURI, String[] variantTypeURIs) {
		setDefaultTypeCode(defaultTypeURI);
		//LOGGER.debug("rdf:type code is " + typeCode);
		setAllTypeCodes(variantTypeURIs);
	}

	private static void setDefaultTypeCode(String defaultTypeURI) {
		defaultTypeCode = dictionaryEncode(defaultTypeURI);
	}

	private static void setAllTypeCodes(String[] variantTypeURIs) {
		allTypeCodes.add(defaultTypeCode);
		long variantTypeCode;
		for (String variantTypeURI: variantTypeURIs) {
			variantTypeCode = dictionaryEncode(variantTypeURI);
			allTypeCodes.add(variantTypeCode);
		}
	}

	private static void setSubClassCode() {
		subClassCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#subClassOf>");
	}

	private static void setSubPropertyCode() {
		subPropertyCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>");
	}

	private static void setDomainCode() {
		domainCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#domain>");
	}

	private static void setRangeCode() {
		rangeCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#range>");
	}

	private static void setClassCode() {
		classCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#Class>");
	}

	public static void setPropertyCode() {
		propertyCode = dictionaryEncode("<http://www.w3.org/1999/02/22-rdf-syntax-ns#Property>");
	}

	/**
	 * Gets the dictionary code for a specific URI. Returns -1 if URI not found in the dictionary.
	 *
	 * @param URI
	 *
	 * @return
	 */
	public static long dictionaryEncode(String URI) {
		// try to use the cache if possible
		Long alreadyKnownCode = uriOrLiteralToCode.get(URI);
		if (alreadyKnownCode != null) {
			return alreadyKnownCode;
		}
		long code = -1;
		try {
			stmtEncode.setString(1, URI);
			//LOGGER.info("Asking query: |" + stmtEncode + " with " + URI + "|");
			try (ResultSet rs = stmtEncode.executeQuery()) {
				if (rs.next()) {
					code = rs.getLong(1);
					//LOGGER.info("The code of " + URI + " is: " + code);
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Not able to encode " + e);
		}
		// feed the cache:
		uriOrLiteralToCode.put(URI, code);
		return code;
	}

	public static String dictionaryDecode(long encodedURI) {
		// try to use the cache if possible
		String alreadyKnownURIOrLiteral = codeToURIOrLiteral.get(encodedURI);
		if (alreadyKnownURIOrLiteral != null) {
			return alreadyKnownURIOrLiteral;
		}
		try {
			stmtDecode.setLong(1, encodedURI);
			ResultSet rs = stmtDecode.executeQuery();
			if (rs.next()) {
				String s = rs.getString(1);
				//LOGGER.info("We got #" + s + "#");
				// feed the cache:
				codeToURIOrLiteral.put(encodedURI, s);
				return s;
			}
			else {
				throw new IllegalStateException("No value for code " + encodedURI);
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Not able to decode ");
		}
	}

	public static long addNewEntryToDictionary(String n, String dictionaryTableName) {
		long nEncoded = -1;
		try {
			// determine encoding
			PreparedStatement stmtDictionarySize = conn.prepareStatement("select count(*) from " + PostgresIdentifier.escapedQuotedId(dictionaryTableName));
			ResultSet rs = stmtDictionarySize.executeQuery();
			if (rs.next()) {
				// new encoding is double the size of dictionary (to avoid collision with new summary IDs and with newly added triples)
				nEncoded = rs.getLong(1) * 2;
			}

			// add to dictionary
			PreparedStatement stmtAddToDictionary = conn.prepareStatement("insert into " + PostgresIdentifier.escapedQuotedId(dictionaryTableName) + " values (?, ?)");
			stmtAddToDictionary.setLong(1, nEncoded);
			stmtAddToDictionary.setString(2, n);
			stmtAddToDictionary.executeUpdate();
			conn.commit();
			uriOrLiteralToCode.put(n, nEncoded);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
		return nEncoded;
	}

	public static void createIfNotExistsUserTriplesTable() {
		try {
			PreparedStatement stmtcreateIfNotExistsUserTriplesTable = conn.prepareStatement("create table if not exists user_triples (s text, p text, o text)");
			stmtcreateIfNotExistsUserTriplesTable.executeUpdate();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
	}

	public static void createIfNotExistsUserEncodedTriplesTable() {
		try {
			PreparedStatement stmtcreateIfNotExistsUserEncodedTriplesTable = conn.prepareStatement("create table if not exists user_encoded_triples (s integer, p integer, o integer, added_after integer)");
			stmtcreateIfNotExistsUserEncodedTriplesTable.executeUpdate();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
	}

	public static void addNewTripleToUserTriplesTable(String s, String p, String o, String triplesTableName) {
		try {
			PreparedStatement stmtAddToUserTriplesTable = conn.prepareStatement("insert into user_triples values (?, ?, ?)");
			stmtAddToUserTriplesTable.setString(1, s);
			stmtAddToUserTriplesTable.setString(2, p);
			stmtAddToUserTriplesTable.setString(3, o);
			stmtAddToUserTriplesTable.executeUpdate();
			conn.commit();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
	}

	public static void addNewTripleToUserEncodedTriplesTable(long sEncoded, long pEncoded, long oEncoded, long addedAfter, String encodedTriplesTableName) {
		try {
			PreparedStatement stmtAddToUserEncodedTriplesTable = conn.prepareStatement("insert into user_encoded_triples values (?, ?, ?, ?)");
			stmtAddToUserEncodedTriplesTable.setLong(1, sEncoded);
			stmtAddToUserEncodedTriplesTable.setLong(2, pEncoded);
			stmtAddToUserEncodedTriplesTable.setLong(3, oEncoded);
			stmtAddToUserEncodedTriplesTable.setLong(4, addedAfter);
			stmtAddToUserEncodedTriplesTable.executeUpdate();
			conn.commit();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
	}

	public static void dropUserTriplesTable() {
		try {
			PreparedStatement stmtDropUserTriplesTable = conn.prepareStatement("drop table if exists user_triples;");
			stmtDropUserTriplesTable.executeUpdate();
			conn.commit();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
	}

	public static void dropEncodedUserTriplesTable() {
		try {
			PreparedStatement stmtDropEncodedUserTriplesTable = conn.prepareStatement("drop table if exists user_encoded_triples;");
			stmtDropEncodedUserTriplesTable.executeUpdate();
			conn.commit();
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
	}

	public static boolean isSchemaProperty(long p) {
		return ((p == subPropertyCode) && (subPropertyCode != -1))
			   || ((p == subClassCode) && (subClassCode != -1))
			   || ((p == domainCode) && (domainCode != -1))
			   || ((p == rangeCode) && (rangeCode != -1));
	}

	public static boolean isSpecialProperty(long p) {
		return ((p == defaultTypeCode) && (defaultTypeCode != -1)) || isSchemaProperty(p);
	}

	public static boolean isDataProperty(long p) {
		return (!(isSpecialProperty(p)));
	}

	public static DecodedTriple decode(Triple t) {
		return new DecodedTriple(dictionaryDecode(t.s), dictionaryDecode(t.p), dictionaryDecode(t.o));
	}
}
