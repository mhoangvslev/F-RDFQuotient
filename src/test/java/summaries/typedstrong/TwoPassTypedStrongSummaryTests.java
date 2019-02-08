//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package summaries.typedstrong;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.controller.Builder;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class TwoPassTypedStrongSummaryTests {
	private static final Logger LOGGER = Logger.getLogger(TwoPassTypedStrongSummaryTests.class.getName());

	public File summarizeUsingTwoPassTypedStrongSummary(int i) {
		LOGGER.setLevel(Level.INFO);
		System.out.println("################################################################################");
		System.out.println("Typed Strong summary test " + i + " only summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-2ptypedstrong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-2ptypedstrong/test-" + i + "_2pts_noSaturation.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "2ptypedstrong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedWithoutSaturation"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedWithoutSaturation", "draw", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public File saturateAndSummarizeUsingTwoPassTypedStrongSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Typed Strong summary test " + i + " saturation and summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-2ptypedstrong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-2ptypedstrong/test-" + i + "_2pts_classical.nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", "2ptypedstrong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedClassicalWay"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedClassicalWay", "draw", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summarizationTechnique) {
		return "src/test/resources/test" + i + "-2ptypedstrong/test-" + i + "_2pts_" + summarizationTechnique + "-reference.nt";
	}

	// Summarization tests only with the graphs that have type triples
	@Test
	public void summarizeTwoPassTypedStrongTest1() {
		String referenceFileName = expectedOutput(1, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(1);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 1 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest2() {
		String referenceFileName = expectedOutput(2, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 2 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest3() {
		String referenceFileName = expectedOutput(3, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(3);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 3 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest4() {
		String referenceFileName = expectedOutput(4, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(4);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 4 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest5() {
		String referenceFileName = expectedOutput(5, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(5);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 5 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest6() {
		String referenceFileName = expectedOutput(6, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 6 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest7() {
		String referenceFileName = expectedOutput(7, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(7);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 7 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest11() {
		String referenceFileName = expectedOutput(11, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(11);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 11 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest12() {
		String referenceFileName = expectedOutput(12, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 12 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedStrongTest14() {
		String referenceFileName = expectedOutput(14, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedStrongSummary(14);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 14 " + e.toString());
		}
	}

	// Saturation and summarization tests with all the graphs that have type
	// triples (saturation has no impact on the input graph if it doesn't
	// contain the schema however it may change the order of the triples and it
	// may be useful to check for the correctness)
	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest1() {
		String referenceFileName = expectedOutput(1, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(1);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 1 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest2() {
		String referenceFileName = expectedOutput(2, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 2 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest3() {
		String referenceFileName = expectedOutput(3, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(3);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 3 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest4() {
		String referenceFileName = expectedOutput(4, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(4);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 4 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest5() {
		String referenceFileName = expectedOutput(5, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(5);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 5 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest6() {
		String referenceFileName = expectedOutput(6, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 6 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest7() {
		String referenceFileName = expectedOutput(7, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(7);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 7 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest11() {
		String referenceFileName = expectedOutput(11, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(11);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 11 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest12() {
		String referenceFileName = expectedOutput(12, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 12 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedStrongTest14() {
		String referenceFileName = expectedOutput(14, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedStrongSummary(14);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedstrong 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedstrong test 14 " + e.toString());
		}
	}
}
