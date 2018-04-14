package fr.inria.cedar.quotientSummary.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import fr.inria.cedar.quotientSummary.datastructures.DecodedTriple;
import fr.inria.cedar.quotientSummary.datastructures.Triple;

/**
 * This class serves exactly to encode / decode the five special RDF properties we are interested in
 * 
 * @author ioanamanolescu
 *
 */
public class RDF2SQLEncoding {
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

	/** 
	 * It is crucial to call this method in order for the summarization or any summary usage code to work OK.
	 * @param givenConn
	 * @throws SQLException
	 */
	public static void setUp(Connection givenConn) {
		conn = givenConn; 
		try {
			stmtDecode = conn.prepareStatement("select value from dictionary where key=?"); 
			stmtEncode = conn.prepareStatement("select key from dictionary where value=?");
		}
		catch(SQLException e) {
			throw new IllegalStateException("Could not prepare encode/decode statements " + e.toString()); 
		}
		codeToURIOrLiteral = new HashMap<>(); 
		uriOrLiteralToCode = new HashMap<>(); 
		setRDFBuiltInPropertyCodes(); 
	}

	public RDF2SQLEncoding() {
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
		System.out.println("rdf:type code is " + typeCode); 
		setSubClassCode();
		System.out.println("rdfs:subclass  code is: " + subClassCode); 
		setSubPropertyCode();
		System.out.println("rdfs:subproperty code is: " + subPropertyCode); 
		setDomainCode();
		System.out.println("rdfs:domain code is: " + domainCode); 
		setRangeCode();
		System.out.println("rdfs:range code is: " + rangeCode); 
	}
	private static void setTypeCode()  {
		typeCode = dictionaryEncode("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>");
	}
	private static void setSubClassCode() {
		subClassCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#subClassOf>");
	}
	private static void setSubPropertyCode()  {
		subPropertyCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>");
	}
	private static void setDomainCode() {
		domainCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#domain>");
	}
	private static void setRangeCode()  {
		rangeCode = dictionaryEncode("<http://www.w3.org/2000/01/rdf-schema#range>");
	}

	/**
	 * Gets the dictionary code for a specific URI. Returns -1 if URI not found in the dictionary.
	 * @param URI
	 * @return
	 * @throws SQLException
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
			ResultSet rs = stmtEncode.executeQuery(); 
			//Debugger.log("Asked query: " + learnCodeQueryString);
			if (rs.next()){
				code  = rs.getInt(1); 
				//Debugger.log("The code of " + URI + " is: " + constantCode);
			}
			rs.close(); 
		}
		catch(SQLException e) {
			throw new IllegalStateException("Not able to encode " + e.toString()); 
		}
		// feed the cache: 
		uriOrLiteralToCode.put(URI, code); 
		return code; 
	}
	public static String dictionaryDecode(Long URL) {
		// try to use the cache if possible
		String alreadyKnownURIOrLiteral = codeToURIOrLiteral.get(URL); 
		if (alreadyKnownURIOrLiteral != null) {
			return alreadyKnownURIOrLiteral; 
		}
		try {
			stmtDecode.setLong(1, URL);
			ResultSet rs = stmtDecode.executeQuery();
			if (rs.next()) {
				String s = rs.getString(1); 
				// feed the cache: 
				codeToURIOrLiteral.put(URL,  s);
				return s; 
			}
			else {
				throw new IllegalStateException("No value for code " + URL); 
			}
		}
		catch(SQLException e) {
			throw new IllegalStateException("Not able to decode"); 
		}
	}

	public static boolean isSchemaProperty(Long p) {
		if (((p == subPropertyCode) && (subPropertyCode != -1)) || 
				((p == subClassCode) && (subClassCode != -1)) || 
				((p == domainCode) && (domainCode != -1)) || 
				((p == rangeCode) && (rangeCode != -1))) {
			return true; 
		}
		return false;
	}
	
	public static boolean isSpecialProperty(Long p) {
		if (((p == typeCode) && (typeCode != -1)) || isSchemaProperty(p)) {
			return true; 
		}
		return false;
	}
	public static boolean isDataProperty(Long p) {
		return (!(isSpecialProperty(p)));
	}

	public static DecodedTriple decode(Triple t)  {
		return new DecodedTriple(dictionaryDecode(t.s), dictionaryDecode(t.p), dictionaryDecode(t.o)); 
	}
}
