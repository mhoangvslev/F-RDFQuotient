//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.drawing;

import fr.inria.cedar.quotientSummary.controller.Interface;
import fr.inria.cedar.quotientSummary.controller.LoadingProperties;
import fr.inria.cedar.quotientSummary.controller.SummarizationProperties;
import fr.inria.cedar.quotientSummary.summarization.Tests;
import java.util.Properties;
import org.junit.Test;

public class StepByStepDrawingTests extends Tests {
	public static String summaryType() {
		return "weak";
	}

	public static String sourceFilename(int i) {
		return "src/test/resources/test" + i + "-step-by-step/test-" + i + ".nt";
	}

	public static String testSummarizationOfNotSaturated(String datasetFilename, String summaryType) {
		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", datasetFilename);
		loadingProperties.put("statistics.export_to_csv_file", "false");

		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", datasetFilename);
		summarizationProperties.put("summary.type", summaryType);
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("summary.nt_file_prefix", "summariesNT/");
		summarizationProperties.put("drawing.style", "plain");
		summarizationProperties.put("drawing.step_by_step", "true");
		summarizationProperties.put("drawing.dot_file_prefix", "summariesDOT/");
		summarizationProperties.put("drawing.png_file_prefix", "summariesPNG/");
		summarizationProperties.put("statistics.export_to_csv_file", "false");

		return Interface.loadAndSummarize(loadingProperties, summarizationProperties).get("NTFilename");
	}

	@Test
	public void summarizeNotSaturatedTest1() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(16), summaryType()));
	}
}