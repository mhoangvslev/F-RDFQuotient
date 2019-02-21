//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package summaries.weak;

import fr.inria.cedar.quotientSummary.controller.Interface;
import fr.inria.cedar.quotientSummary.controller.LoadingProperties;
import fr.inria.cedar.quotientSummary.controller.SummarizationProperties;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class WeakSummaryTests {
	private static final Logger LOGGER = Logger.getLogger(WeakSummaryTests.class.getName());

	public File summarizeUsingWeakSummary(int i) {
		LOGGER.setLevel(Level.INFO);
		System.out.println("################################################################################");
		System.out.println("Weak summary test " + i + " only summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-weak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-weak/test-" + i + "_w.nt";

		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", inputFileName);
		loadingProperties.put("database.name", "weak_test");
		loadingProperties.put("statistics.export_to_csv_file", "false");
		Interface.load(null, loadingProperties, false);

		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", inputFileName);
		summarizationProperties.put("database.name", "weak_test");
		summarizationProperties.put("summary.type", "weak");
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("drawing.style", "plain");
		summarizationProperties.put("statistics.export_to_csv_file", "false");
		Interface.summarize(null, summarizationProperties, true);

		return new File(outputFileName);
	}

	public File saturateAndSummarizeUsingWeakSummary(int i) {
		LOGGER.setLevel(Level.INFO);
		System.out.println("################################################################################");
		System.out.println("Weak summary test " + i + " saturation and summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-weak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-weak/test-" + i + "_sat_w.nt";

		Properties loadingProperties = LoadingProperties.getDefaultProperties();
		loadingProperties.put("dataset.filename", inputFileName);
		loadingProperties.put("database.name", "weak_test");
		loadingProperties.put("saturation.enable", "true");
		loadingProperties.put("statistics.export_to_csv_file", "false");
		Interface.load(null, loadingProperties, false);

		Properties summarizationProperties = SummarizationProperties.getDefaultProperties();
		summarizationProperties.put("dataset.filename", inputFileName);
		summarizationProperties.put("database.name", "weak_test");
		summarizationProperties.put("summary.type", "weak");
		summarizationProperties.put("summary.summarize_saturated_graph", "true");
		summarizationProperties.put("summary.replace_type_with_most_general_type", "false");
		summarizationProperties.put("drawing.style", "plain");
		summarizationProperties.put("statistics.export_to_csv_file", "false");
		Interface.summarize(null, summarizationProperties, true);

		return new File(outputFileName);
	}

	/*public File summarizeThroughShortcutUsingWeakSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Weak summary test " + i + " summarization through shortcut");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-weak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-weak/test-" + i + "_w_shortcut.nt";
		try {
			String[] argsSum = {"loadAndSummarizeUsingShortcut", "weak", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedUsingShortcut"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedUsingShortcut", "draw", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in weak test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}*/

	private String expectedOutput(int i, String saturated) {
		return "src/test/resources/test" + i + "-weak/test-" + i + "_w" + (saturated.equals("") ? "" : "_" + saturated) + "-reference.nt";
	}

	@Test
	public void summarizeWeakTest1() {
		String referenceFileName = expectedOutput(1, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(1);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 1 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest2() {
		String referenceFileName = expectedOutput(2, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 2 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest3() {
		String referenceFileName = expectedOutput(3, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(3);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 3 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest4() {
		String referenceFileName = expectedOutput(4, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(4);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 4 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest5() {
		String referenceFileName = expectedOutput(5, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(5);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 5 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest6() {
		String referenceFileName = expectedOutput(6, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 6 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest7() {
		String referenceFileName = expectedOutput(7, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(7);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 7 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest8() {
		String referenceFileName = expectedOutput(8, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(8);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 8", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 8 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest9() {
		String referenceFileName = expectedOutput(9, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(9);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 9 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest10() {
		String referenceFileName = expectedOutput(10, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(10);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 10", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 10 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest11() {
		String referenceFileName = expectedOutput(11, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(11);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 11 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest12() {
		String referenceFileName = expectedOutput(12, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 12 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest13() {
		String referenceFileName = expectedOutput(13, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(13);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 13", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 13 " + e.toString());
		}
	}

	@Test
	public void summarizeWeakTest14() {
		String referenceFileName = expectedOutput(14, "");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(14);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 14 " + e.toString());
		}
	}

	// Saturation and summarization tests with all graphs (saturation has no
	// impact on the input graph if it doesn't contain the schema however it
	// may change the order of the triples and it may be useful to check for
	// the correctness)
	@Test
	public void saturateAndSummarizeWeakTest1() {
		String referenceFileName = expectedOutput(1, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(1);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 1 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest2() {
		String referenceFileName = expectedOutput(2, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 2 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest3() {
		String referenceFileName = expectedOutput(3, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(3);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 3 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest4() {
		String referenceFileName = expectedOutput(4, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(4);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 4 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest5() {
		String referenceFileName = expectedOutput(5, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(5);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 5 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest6() {
		String referenceFileName = expectedOutput(6, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 6 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest7() {
		String referenceFileName = expectedOutput(7, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(7);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 7 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest8() {
		String referenceFileName = expectedOutput(8, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(8);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 8", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 8 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest9() {
		String referenceFileName = expectedOutput(9, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(9);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 9 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest10() {
		String referenceFileName = expectedOutput(10, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(10);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 10", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 10 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest11() {
		String referenceFileName = expectedOutput(11, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(11);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 11 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest12() {
		String referenceFileName = expectedOutput(12, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 12 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest13() {
		String referenceFileName = expectedOutput(13, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(13);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 13", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 13 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeWeakTest14() {
		String referenceFileName = expectedOutput(14, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(14);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 14 " + e.toString());
		}
	}

	// Shortcut tests only with the graphs that have a schema
	/*@Test
	public void summarizeThroughShortcutWeakTest6() {
		String referenceFileName = expectedOutput(6, "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeThroughShortcutUsingWeakSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 6 " + e.toString());
		}
	}

	@Test
	public void summarizeThroughShortcutWeakTest12() {
		String referenceFileName = expectedOutput(12, "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeThroughShortcutUsingWeakSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 12 " + e.toString());
		}
	}*/

	/**
	 * Test with custom config file
	 */
	@Test public void summarizeWeakTestCustom() {
		String referenceFileName = expectedOutput(2, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingWeakSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 2 " + e.toString());
		}
	}
}
