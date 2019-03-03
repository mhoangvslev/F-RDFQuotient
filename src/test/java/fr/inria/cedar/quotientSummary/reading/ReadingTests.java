//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.reading;

import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.controller.Interface;
import fr.inria.cedar.quotientSummary.controller.LoadingProperties;
import fr.inria.cedar.quotientSummary.controller.SummarizationProperties;
import fr.inria.cedar.quotientSummary.summarization.Tests;
import java.util.Properties;
import org.junit.Test;

public class ReadingTests extends Tests {
	@Test
	public void readingTest() {
		String inputFilename = "src/test/resources/rdf-nt-files/test-1.nt";

		// Load test dataset
		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", inputFilename);
		loadingProperties.put("database.name", "reading_test");
		loadingProperties.put("statistics.export_to_csv_file", "false");

		// first argument can be replaced with configuration file name e.g. LoadingProperties.getDefaultPropertiesFilename()
		// last argument specifies database connection to not be closed
		Interface.load(null, loadingProperties, false);


		// Summarize test dataset
		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", inputFilename);
		summarizationProperties.put("database.name", "weak_test");
		summarizationProperties.put("summary.type", "weak");
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("summary.export_to_nt_file", "false");
		summarizationProperties.put("drawing.style", "none");
		summarizationProperties.put("statistics.export_to_csv_file", "false");

		// first argument can be replaced with configuration file name e.g. SummarizationProperties.getDefaultPropertiesFilename()
		// last argument specifies database connection to not be closed
		Interface.summarize(null, summarizationProperties, false);


		// Read test dataset from Postgres
		// first argument can be replaced with configuration file name e.g. LoadingProperties.getDefaultPropertiesFilename()
		// last argument specifies database connection to be closed
		Summary s = Interface.read(null, loadingProperties, true);
	}
}