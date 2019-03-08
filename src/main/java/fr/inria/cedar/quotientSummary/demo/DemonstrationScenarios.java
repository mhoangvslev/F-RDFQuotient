//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.demo;

import fr.inria.cedar.quotientSummary.controller.Interface;
import fr.inria.cedar.quotientSummary.controller.LoadingProperties;
import fr.inria.cedar.quotientSummary.controller.SummarizationProperties;
import java.util.Properties;

public class DemonstrationScenarios {
	private static final String[] DATASET_FILENAMES = {"demo/test-1.nt"};

	public static void loadDatasets() {
		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("saturation.enable", "true");
		loadingProperties.put("statistics.export_to_csv_file", "false");
		loadingProperties.put("configuration.export_to_disk", "false");

		for (String datasetFilename: DATASET_FILENAMES) {
			loadingProperties.put("dataset.filename", datasetFilename);
			Interface.load(null, loadingProperties, true);
		}
	}

	public static void scenario1() { // summarize step-by-step, stopping after every triple
		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", DATASET_FILENAMES[0]);
		summarizationProperties.put("summary.type", "weak");
		summarizationProperties.put("summary.summarize_saturated_graph", "false");
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("summary.nt_file_prefix", "summariesNT/");
		summarizationProperties.put("summary.step_by_step", "true");
		summarizationProperties.put("drawing.style", "plain");
		summarizationProperties.put("drawing.step_by_step", "true");
		summarizationProperties.put("drawing.dot_file_prefix", "summariesDOT/");
		summarizationProperties.put("drawing.png_file_prefix", "summariesPNG/");
		summarizationProperties.put("statistics.export_to_csv_file", "false");
		summarizationProperties.put("configuration.export_to_disk", "false");

		Interface.summarize(null, summarizationProperties, true);
	}

	public static void main(String[] argv) {
		loadDatasets();
		scenario1();
	}
}