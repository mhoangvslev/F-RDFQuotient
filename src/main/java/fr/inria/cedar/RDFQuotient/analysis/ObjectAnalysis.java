//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.analysis;

import fr.inria.cedar.RDFQuotient.controller.Interface;
import fr.inria.cedar.RDFQuotient.controller.LoadingProperties;
import fr.inria.cedar.RDFQuotient.controller.SummarizationProperties;
import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class ObjectAnalysis {
	private static final Logger LOGGER = Logger.getLogger(ObjectAnalysis.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	private long objectNo; // the number of distinct subjects of data triples
	private long typedNo; // the number of distinct subjects of type triples
	private long typedObjectNo; // the number of distinct subjects of data triples and typed triple
	private long untypedObjectNo; // the number of distinct untyped subjects of data triples
	private long typedNoData; // the number of typed resources with no data triple

	private Connection conn;

	public ObjectAnalysis() {
	}

	public void analyze(String datasetFilename) throws IOException {
		System.out.println("############################################");
		System.out.println("Analysis of " + datasetFilename);
		System.out.println("#############################################");
		try {
			Properties loadingProperties = LoadingProperties.getDefaultProperties();
			loadingProperties.put("dataset.filename", datasetFilename);
			loadingProperties.put("statistics.export_to_csv_file", "false");
			Interface.load(null, loadingProperties, false);

			Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
			summarizationProperties.put("dataset.filename", datasetFilename);
			summarizationProperties.put("summary.type", "weak");
			summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
			summarizationProperties.put("drawing.style", "plain");
			summarizationProperties.put("statistics.export_to_csv_file", "false");
			Interface.summarize(null, summarizationProperties, false);

			conn = Interface.getDatabaseConnection();

			RDF2SQLEncoding.setUp(conn, "dictionary"); // fingers crossed
		}
		catch(Exception e) {
			LOGGER.info("Error: " + e);
		}
		String typedSubjectsStmt = "create table typed as select distinct s from tmp_encoded_sat ";
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
		//LOGGER.info("TypedSubjectsStatement is: " + typedSubjectsStmt);

		this.createAndIndexOneColTable("typed", typedSubjectsStmt);
		LOGGER.info("Typed table done.");

		// data subjects
		hasWhere = false;
		String dataSubjectsStmt = "create table hasdataprops as select distinct s from tmp_encoded_sat ";
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


		this.createAndIndexOneColTable("hasdataprops", dataSubjectsStmt);
		//LOGGER.info("DataSubjectsStatement is: " + dataSubjectsStmt);
		LOGGER.info("HasDataProps done.");


		this.createAndIndexOneColTable("datatyped", "create table datatyped as (select * from typed natural join hasdataprops);");
		LOGGER.info("DataTyped done.");

		Statement stat = null;
		try{
			stat = conn.createStatement();
		}
		catch(SQLException e) {
			LOGGER.error("Could not create statement " + e);
		}
		ResultSet rs;
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

		String countDataUntyped = "with aux as (select * from hasdataprops except select * from datatyped) select count(*) from aux";
		try {
			rs = stat.executeQuery(countDataUntyped);
			if (rs.next()) {
				this.untypedObjectNo = rs.getLong(1);
			}
		}
		catch(SQLException e) {
			LOGGER.info("Could not count untyped data property subjects");
		}

		String countTypedNoData = "with aux as (select * from typed except select * from datatyped) select count(*) from aux";
		try {
			rs = stat.executeQuery(countTypedNoData);
			if (rs.next()) {
				this.typedNoData = rs.getLong(1);
			}
		}
		catch(SQLException e) {
			LOGGER.info("Could not count typed objects without any data");
		}

		System.out.println("Typed: " + this.typedNo +
				" objects: " + this.objectNo +
				" untypedObjects: " + this.untypedObjectNo +
				" typedNoData: " + this.typedNoData);

		try {
			Interface.closeDatabaseConnection();
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	void createAndIndexOneColTable(String tableName, String createStatement) {
		Statement stat = null;
		try{
			stat = conn.createStatement();
			stat.executeUpdate(createStatement);
		}
		catch(SQLException e) {
			if (e.toString().indexOf("already exists") >= 0) {
				try {
					stat.executeUpdate("drop table " + tableName);
					stat.executeUpdate(createStatement);
				}
				catch(SQLException e2) {
					LOGGER.error("Could still not create " + tableName + e2.toString());
				}
			}
			else{
				LOGGER.info("Could not create " + tableName + " " + e);
			}
		}
		try {
			stat.executeUpdate("create index idx"+tableName + " on " + tableName + "(s)");
		}
		catch(SQLException e) {
			LOGGER.error("Could not create index " + e);
		}
	}

	public static void main(String[] args) throws IOException {
		ObjectAnalysis o = new ObjectAnalysis();
		String[] fileNames = new String[] {"lubm10m"}; //{"lubm10m", "frenchpolitics", "lubm1m", "mondial", "nasa", "nobelprizes", "pokedex", "bsbm1m", "watdiv10m"};
		String directory = "src/test/resources/rdf-nt-files/";
		for (String fileName: fileNames) {
			o.analyze(directory + fileName + ".nt");
		}
	}
}
