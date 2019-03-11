//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.summarization;

import fr.inria.cedar.RDFQuotient.controller.Interface;
import fr.inria.cedar.RDFQuotient.controller.LoadingProperties;
import fr.inria.cedar.RDFQuotient.controller.SummarizationProperties;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Tests {
	private static final Logger LOGGER = Logger.getLogger(Tests.class.getName());

	public static String testSummarizationOfNotSaturated(String datasetFilename, String summaryType) {
		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", datasetFilename);
		loadingProperties.put("saturation.enable", "false");
		loadingProperties.put("statistics.export_to_csv_file", "false");
		loadingProperties.put("configuration.export_to_disk", "false");

		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", datasetFilename);
		summarizationProperties.put("summary.type", summaryType);
		summarizationProperties.put("summary.summarize_saturated_graph", "false");
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("summary.nt_file_prefix", "");
		summarizationProperties.put("drawing.style", "plain");
		summarizationProperties.put("drawing.step_by_step", "false");
		summarizationProperties.put("drawing.dot_file_prefix", "");
		summarizationProperties.put("drawing.png_file_prefix", "");
		summarizationProperties.put("statistics.export_to_csv_file", "false");
		summarizationProperties.put("configuration.export_to_disk", "false");

		return Interface.loadAndSummarize(loadingProperties, summarizationProperties).get("NTFilename");
	}

	public static String testSummarizationOfSaturated(String datasetFilename, String summaryType) {
		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", datasetFilename);
		loadingProperties.put("saturation.enable", "true");
		loadingProperties.put("statistics.export_to_csv_file", "false");
		loadingProperties.put("configuration.export_to_disk", "false");

		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", datasetFilename);
		summarizationProperties.put("summary.type", summaryType);
		summarizationProperties.put("summary.summarize_saturated_graph", "true");
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("summary.nt_file_prefix", "");
		summarizationProperties.put("drawing.style", "plain");
		summarizationProperties.put("drawing.step_by_step", "false");
		summarizationProperties.put("drawing.dot_file_prefix", "");
		summarizationProperties.put("drawing.png_file_prefix", "");
		summarizationProperties.put("statistics.export_to_csv_file", "false");
		summarizationProperties.put("configuration.export_to_disk", "false");

		return Interface.loadAndSummarize(loadingProperties, summarizationProperties).get("NTFilename");
	}

	public static String testSummarizationThroughShortcut(String datasetFilename, String summaryType) {
		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", datasetFilename);
		loadingProperties.put("statistics.export_to_csv_file", "false");
		loadingProperties.put("configuration.export_to_disk", "false");

		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", datasetFilename);
		summarizationProperties.put("summary.type", summaryType);
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("summary.nt_file_prefix", "");
		summarizationProperties.put("drawing.style", "plain");
		summarizationProperties.put("drawing.step_by_step", "false");
		summarizationProperties.put("drawing.dot_file_prefix", "");
		summarizationProperties.put("drawing.png_file_prefix", "");
		summarizationProperties.put("statistics.export_to_csv_file", "false");
		summarizationProperties.put("configuration.export_to_disk", "false");

		return Interface.loadAndSummarizeThroughShortcut(loadingProperties, summarizationProperties).get("NTFilename");
	}

	// validates the test
	public static void compareWithReference(String summaryNTFilename) {
		try {
			String referenceNTFilename = summaryNTFilename.substring(0, summaryNTFilename.length() - 3) + "-reference.nt";
			File testOutput = new File(summaryNTFilename);
			File expectedOutput = new File(referenceNTFilename);
			if (!testOutput.exists()) {
				fail("Test output not found: " + summaryNTFilename);
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found: " + referenceNTFilename);
			}
			assertTrue("Summary file "
				   + summaryNTFilename
				   + " does not match reference file: "
				   + referenceNTFilename,
				   FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException ex) {
			LOGGER.error(ex);
		}
	}
}