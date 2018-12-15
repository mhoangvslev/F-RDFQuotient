package fr.inria.cedar.quotientSummary.analysis;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.sql.Statement;

import java.sql.Connection;
import java.sql.ResultSet;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.controller.Builder;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

public class ObjectAnalysis {
	private static final Logger LOGGER = Logger.getLogger(ObjectAnalysis.class.getName());

	long objectNo; // the number of distinct subjects of data triples
	long typedNo; // the number of distinct subjects of type triples
	long typedObjectNo; // the number of distinct subjects of data triples and typed triple 
	long untypedObjectNo; // the number of distinct untyped subjects of data triples
	long typedNoData; // the number of typed resources with no data triple

	private static long typeCode = -1; // this is the long associated by OntoSQL to rdf:type. 
	private static long subClassCode = -1;
	private static long subPropertyCode = -1;
	private static long domainCode = -1;
	private static long rangeCode = -1;
	private static long classCode = -1; 
	private static long propertyCode = -1; 


	public ObjectAnalysis() {

	}

	public void analyze(String fileName) throws IOException {
		LOGGER.setLevel(Level.INFO);
		System.out.println("############################################");
		System.out.println("Analysis of " + fileName);
		System.out.println("#############################################");
		Connection conn = null; 
		String inputFileName =  fileName;
		try {
			String[] argsSum = {"loadWithoutSaturation", inputFileName};
			Builder.main(argsSum);
			conn = Builder.getConnection(); 
			RDF2SQLEncoding.setUp(conn, "dictionary"); // fingers crossed
		}
		catch(Exception e) {
			LOGGER.info("Error: " + e);
		}
		String typedSubjectsStmt = "create table typed as select distinct s from tmp_encoded ";
		boolean hasWhere = false; 

		// typed subjects
		if (RDF2SQLEncoding.getTypeCode() >= 0) {
			if (!hasWhere) {
				typedSubjectsStmt = typedSubjectsStmt + " where ";
				hasWhere = true;
			}
			typedSubjectsStmt = typedSubjectsStmt + " p = " + RDF2SQLEncoding.getTypeCode();
		}
		else {
			typedSubjectsStmt = "create table typed (s long)"; 
		}
		LOGGER.info("TypedSubjectsStatement is: " + typedSubjectsStmt);
		
		Statement stat = null;
		try{
			stat = conn.createStatement();
			stat.executeUpdate(typedSubjectsStmt);
		}
		catch(SQLException e) {
			if (e.toString().indexOf("already exists") >= 0) {
				try {
					stat.executeUpdate("drop table typed");
					stat.executeUpdate(typedSubjectsStmt);
				}
				catch(SQLException e2) {
					LOGGER.error("Could still not create typed subjects: " + e2.toString());
				}
			}
			else{
				LOGGER.info("Could not create hasdataprops: " + e);
			}
		}
		// data subjects
		hasWhere = false; 
		String dataSubjectsStmt = "create table hasdataprops as select distinct s from tmp_encoded ";
		if (RDF2SQLEncoding.getTypeCode() >= 0) {
			if (!hasWhere) {
				dataSubjectsStmt = dataSubjectsStmt + " where ";
				hasWhere = true;
			}
			dataSubjectsStmt = dataSubjectsStmt + " p <> " + RDF2SQLEncoding.getTypeCode() + " and";
		}
		if (RDF2SQLEncoding.getSubClassCode() >= 0) {
			if (!hasWhere) {
				dataSubjectsStmt = dataSubjectsStmt + " where ";
				hasWhere = true;
			}
			dataSubjectsStmt = dataSubjectsStmt + " p <> " + RDF2SQLEncoding.getSubClassCode()+ " and";
		}
		if (RDF2SQLEncoding.getSubPropertyCode() >= 0) {
			if (!hasWhere) {
				dataSubjectsStmt = dataSubjectsStmt + " where ";
				hasWhere = true;
			}
			dataSubjectsStmt = dataSubjectsStmt + " p <> " + RDF2SQLEncoding.getSubPropertyCode()+ " and";
		}
		if (RDF2SQLEncoding.getDomainCode() >= 0) {
			if (!hasWhere) {
				dataSubjectsStmt = dataSubjectsStmt + " where ";
				hasWhere = true;
			}
			dataSubjectsStmt = dataSubjectsStmt + " p <> " + RDF2SQLEncoding.getDomainCode()+ " and";
		}
		if (RDF2SQLEncoding.getRangeCode() >= 0) {
			if (!hasWhere) {
				dataSubjectsStmt = dataSubjectsStmt + " where ";
				hasWhere = true;
			}
			dataSubjectsStmt = dataSubjectsStmt + " p <> " + RDF2SQLEncoding.getRangeCode()+ " and";
		}
		if (hasWhere) {
			dataSubjectsStmt = dataSubjectsStmt.substring(0, dataSubjectsStmt.length() - 4);
		}
		LOGGER.info("DataSubjectsStatement is: " + dataSubjectsStmt);

		ResultSet rs = null; 
		try {
			stat.executeUpdate(dataSubjectsStmt);
		}
		catch(SQLException e) {
			if (e.toString().indexOf("already exists") >= 0) {
				try {
					stat.executeUpdate("drop table hasdataprops");
					stat.executeUpdate(dataSubjectsStmt);
				}
				catch(SQLException e2) {
					LOGGER.error("Still an error: " + e2.toString());
				}
			}
			else{
				LOGGER.info("Could not create hasdataprops: " + e);
			}
		}
		String countTyped = "select count(*) from typed";
		try {
			rs = stat.executeQuery(countTyped);
			if (rs.next()) {
				this.typedNo = rs.getLong(1);
			}
		}
		catch(SQLException e) {
			LOGGER.info("Could not count typed objects");
		}

		String countData = "select count(*) from hasdataprops";
		try {
			rs = stat.executeQuery(countData);
			if (rs.next()) {
				this.objectNo = rs.getLong(1);
			}
		}
		catch(SQLException e) {
			LOGGER.info("Could not count data property subjects");
		}
		System.out.println("Typed: " + this.typedNo + " objects: " + this.objectNo);
		String[] argsCloseConnection = {"closeConnection"};
		try {
			Builder.main(argsCloseConnection);
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	public static void main(String[] argv) throws IOException {
		ObjectAnalysis o = new ObjectAnalysis();
		String[] fileNames = new String[] {"test-1"}; //, "enelshops", "foodista", "frenchpolitics","lubm1m", "mondial", "nasa", "nobelprizes", "pokedex", "bsbm1m", "watdiv10m"};   
		String directory = "src/test/resources/rdf-nt-files/"; 
		for (String fileName: fileNames) {
			o.analyze(directory + fileName + ".nt"); 
		}
	}

}
