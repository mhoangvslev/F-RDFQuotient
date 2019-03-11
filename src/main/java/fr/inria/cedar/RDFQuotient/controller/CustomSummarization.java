//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.controller;

import java.util.Properties;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class CustomSummarization {
	private static final Logger LOGGER = Logger.getLogger(CustomSummarization.class.getName());

	public static void loadAndSummarize(String datasetFilename, String summaryType){
		LOGGER.setLevel(Level.INFO);
		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", datasetFilename);
		loadingProperties.put("statistics.export_to_csv_file", "false");

		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", datasetFilename);
		summarizationProperties.put("summary.type", summaryType);
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("drawing.style", "plain");
		summarizationProperties.put("statistics.export_to_csv_file", "false");

		Interface.loadAndSummarize(loadingProperties, summarizationProperties);
	}

	public static void main(String[] argv) {
		String directory = "src/test/resources/rdf-nt-files/";
		String[] fileNames = new String[] {"test-15"};//{"test-15", "conference", "enelshops", "foodista", "frenchpolitics", "lubm1m", "mondial", "nasa", "nobelprizes", "pokedex", "bsbm1m", "watdiv10m"};
		String [] summaryTypes = new String[] {"typedstrong"}; //, "strong", "typedweak", "typedstrong", "onefb", "onefw"};
		for (String fileName: fileNames) {
			for (String summaryType: summaryTypes) {
				loadAndSummarize(directory + fileName + ".nt", summaryType);
			}
		}
	}
}