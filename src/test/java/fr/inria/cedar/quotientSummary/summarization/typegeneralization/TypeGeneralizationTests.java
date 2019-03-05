//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.summarization.typegeneralization;

import fr.inria.cedar.quotientSummary.controller.Interface;
import fr.inria.cedar.quotientSummary.controller.LoadingProperties;
import fr.inria.cedar.quotientSummary.controller.SummarizationProperties;
import fr.inria.cedar.quotientSummary.summarization.Tests;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 * @author ioanamanolescu
 *
 * I tried to add a test to validate the correctness of the type generalization feature.
 *
 * The code below checks that the right *nt* answer is obtained. But this is pretty useless
 * because type generalization is coded at the drawing level, not at the summarization level,
 * thus the *nt* file we output is not affected by type generalization (only the DOT file is!)
 * Type generalization is coded at the drawing level because:
 *
 * a. this was easier to code
 * b. (more fundamentally) we did not want to "break the summarization contract" which says:
 *
 * if n type t in G, then rep(n) type rep(t) in the summary
 * Generalizing *in the summary* breaks this contract.
 * And indeed, this is also a reason for not coding it this way: the code has many checks that ensure
 * that the right numbers of triples have been reflected, that any type present in the input is present in the output etc.
 * If we "read triple a type t" and attempt not to have rep(a) type rep(t) in the summary, the code
 * safety checks will scream.
 *
 * All of this means that the only meaningful testing would be based on the DOT file.
 *
 * However, the DOT file is impacted by a. the random choice of colors and b. the limit of how many
 * types to display from a given namespace. c. any other parameters that impact drawing.
 * Which means that the test class needs to be able to force some parameter values (to ensure the
 * execution environment). And I'm not sure how to do this now.
 *
 */
// PG: I tried to address the issues mentioned above while refactoring

public class TypeGeneralizationTests extends Tests {
	private static final Logger LOGGER = Logger.getLogger(TypeGeneralizationTests.class.getName());

	public static String sourceFilename(int i) {
		return "src/test/resources/test" + i + "-typegen/test-" + i + ".nt";
	}

	public static String testSummarizationWithMostGeneralTypes(String datasetFilename, String summaryType) {
		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", datasetFilename);
		loadingProperties.put("statistics.export_to_csv_file", "false");

		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", datasetFilename);
		summarizationProperties.put("summary.type", summaryType);
		summarizationProperties.put("summary.replace_type_with_most_general_type", "true");
		summarizationProperties.put("summary.nt_file_prefix", "");
		summarizationProperties.put("drawing.style", "split_and_fold_leaves");
		summarizationProperties.put("drawing.color_scheme", "bw");
		summarizationProperties.put("drawing.dot_file_prefix", "");
		summarizationProperties.put("drawing.png_file_prefix", "");
		summarizationProperties.put("statistics.export_to_csv_file", "false");

		return Interface.loadAndSummarize(loadingProperties, summarizationProperties).get("DOTFilename");
	}

	// validates the test
	public static void compareDOTFileWithReference(String summaryDOTFilename) {
		try {
			String referenceDOTFilename = summaryDOTFilename.substring(0, summaryDOTFilename.length() - 4) + "-reference.dot";
			File testOutput = new File(summaryDOTFilename);
			File expectedOutput = new File(referenceDOTFilename);
			if (!testOutput.exists()) {
				fail("Test output not found: " + summaryDOTFilename);
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found: " + referenceDOTFilename);
			}
			assertTrue("Summary file "
				   + summaryDOTFilename
				   + " does not match reference file: "
				   + referenceDOTFilename,
				   FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException ex) {
			LOGGER.error(ex);
		}
	}

	@Test
	public void summarizeTypeGenTest15() {
		// compares DOT files
		compareDOTFileWithReference(testSummarizationWithMostGeneralTypes(sourceFilename(15), "typedstrong")); // use "typedweak" or "typedstrong"
	}
}