package fr.inria.cedar.quotientSummary.controller;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class CustomSummarization {
	private static final Logger LOGGER = Logger.getLogger(CustomSummarization.class.getName());

	public static File summarize(String fileName, String summarizationMethod){
		LOGGER.setLevel(Level.INFO);
		System.out.println("############################################");
		System.out.println("Custom " + summarizationMethod + " summarization of " + fileName);
		System.out.println("#############################################");

		String inputFileName =  fileName;
		String outputFileName = fileName.substring(0, fileName.length() - 3)  + "_" + summarizationMethod + "_noSaturation.nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", summarizationMethod, inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedClassicalWay"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedClassicalWay", "foldleaves", inputFileName};
				Builder.main(argsExport);
			}
			catch (UnsupportedDatabaseEngineException ex) {
				LOGGER.error(ex);
			}
			finally {
				String[] argsCloseConnection = {"closeConnection"};
				try {
					Builder.main(argsCloseConnection);
				}
				catch (UnsupportedDatabaseEngineException ex1) {
					LOGGER.error(ex1);
				}
			}
			return new File(outputFileName);
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in " + summarizationMethod + 
					" "+ fileName + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public static void main(String[] argv) {
//		String fileName = argv[0]; 
//		String summarizationMethod = argv[1]; 
//		File f = summarize(fileName, summarizationMethod); 
		String[] fileNames = new String[] 
//				//{ "conference", "enelshops", "foodista", "frenchpolitics",
//				//"lubm1m", "mondial", "nasa", "nobelprizes", {"pokedex"}; {"bsbm1m"}; {"watdiv10m"}; {"department"}; {"untypdepartment"}; 
				{"untypcoursedept"}; 
		String directory = "src/test/resources/rdf-nt-files/"; 
		String [] summarizationMethods = new String[] {"weak", "strong", "typedweak", "typedstrong", "onefb", "onefw"}; 
		for (String fileName: fileNames) {
			for (String summarizationMethod: summarizationMethods) {
				summarize(directory + fileName + ".nt", summarizationMethod); 
			}
		}
	}
}
