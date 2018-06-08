package fr.inria.cedar.quotientSummary.util;

import fr.inria.cedar.quotientSummary.datastructures.DecodedTriple;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
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
	protected static long typeCode = -1; // this is the long associated by OntoSQL to rdf:type. 
	protected static long subClassCode = -1;
	protected static long subPropertyCode = -1;
	protected static long domainCode = -1;
	protected static long rangeCode = -1;
	static HashMap<Long, String> codeToURIOrLiteral;
	static HashMap<String, Long> uriOrLiteralToCode;
	static Connection conn;
	protected static PreparedStatement stmtDecode;
	protected static PreparedStatement stmtEncode;

	public RDF2SQLEncoding() {
	}

	/**
	 * It is crucial to call this method in order for the summarization or any summary usage code to work OK.
	 *
	 * @param givenConn
	 * @param dictionaryTableName
	 */
	public static void setUp(Connection givenConn, String dictionaryTableName) {
		LOGGER.setLevel(Level.INFO);
		conn = givenConn;
		try {
			stmtDecode = conn.prepareStatement("select value from " + dictionaryTableName + " where key=?");
			stmtEncode = conn.prepareStatement("select key from " + dictionaryTableName + " where value=?");
		}
		catch (SQLException e) {
			throw new IllegalStateException("Could not prepare encode/decode statements " + e.toString());
		}
		codeToURIOrLiteral = new HashMap<>();
		uriOrLiteralToCode = new HashMap<>();
		setRDFBuiltInPropertyCodes();
	}

	public static Connection getConnection() {
		return conn;
	}

	public static void setConnection(Connection givenConn) {
		conn = givenConn;
	}

	public static long getTypeCode() {
		return typeCode;
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

	public static void setRDFBuiltInPropertyCodes() {
		setTypeCode();
		//LOGGER.debug("rdf:type code is " + typeCode);
		setSubClassCode();
		//LOGGER.debug("rdfs:subclass  code is: " + subClassCode);
		setSubPropertyCode();
		//LOGGER.debug("rdfs:subproperty code is: " + subPropertyCode);
		setDomainCode();
		//LOGGER.debug("rdfs:domain code is: " + domainCode);
		setRangeCode();
		//LOGGER.debug("rdfs:range code is: " + rangeCode);
	}

	private static void setTypeCode() {
		typeCode = dictionaryEncode("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>");
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

	/**
	 * Gets the dictionary code for a specific URI. Returns -1 if URI not found in the dictionary.
	 *
	 * @param URI
	 *
	 * @return
	 */
	public static long dictionaryEncode(String URI) {
		//LOGGER.debug("DictionaryEncode will ask query: " + stmtEncode); 
		// try to use the cache if possible
		Long alreadyKnownCode = uriOrLiteralToCode.get(URI);
		if (alreadyKnownCode != null)
			return alreadyKnownCode;
		long code = -1;
		try {
			stmtEncode.setString(1, URI);
			try (ResultSet rs = stmtEncode.executeQuery()) {
				//LOGGER.debug("Asked query: " + learnCodeQueryString);
				if (rs.next()) {
					code = rs.getLong(1);
					//LOGGER.debug("The code of " + URI + " is: " + constantCode);
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Not able to encode " + e.toString());
		}
		// feed the cache: 
		uriOrLiteralToCode.put(URI, code);
		return code;
	}

	public static String dictionaryDecode(long URL) {
		// try to use the cache if possible
		String alreadyKnownURIOrLiteral = codeToURIOrLiteral.get(URL);
		if (alreadyKnownURIOrLiteral != null)
			return alreadyKnownURIOrLiteral;
		try {
			stmtDecode.setLong(1, URL);
			ResultSet rs = stmtDecode.executeQuery();
			if (rs.next()) {
				String s = rs.getString(1);
				// feed the cache: 
				codeToURIOrLiteral.put(URL, s);
				return s;
			}
			else
				throw new IllegalStateException("No value for code " + URL);
		}
		catch (SQLException e) {
			throw new IllegalStateException("Not able to decode ");
		}
	}

	public static boolean isSchemaProperty(long p) {
		return ((p == subPropertyCode) && (subPropertyCode != -1))
			   || ((p == subClassCode) && (subClassCode != -1))
			   || ((p == domainCode) && (domainCode != -1))
			   || ((p == rangeCode) && (rangeCode != -1));
	}

	public static boolean isSpecialProperty(long p) {
		return ((p == typeCode) && (typeCode != -1)) || isSchemaProperty(p);
	}

	public static boolean isDataProperty(long p) {
		return (!(isSpecialProperty(p)));
	}

	public static DecodedTriple decode(Triple t) {
		return new DecodedTriple(dictionaryDecode(t.s), dictionaryDecode(t.p), dictionaryDecode(t.o));
	}
}
