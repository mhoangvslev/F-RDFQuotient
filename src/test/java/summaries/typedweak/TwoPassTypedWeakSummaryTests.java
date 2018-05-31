package summaries.typedweak;

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

public class TwoPassTypedWeakSummaryTests {
	private static final Logger LOGGER = Logger.getLogger(TwoPassTypedWeakSummaryTests.class.getName());

	public File summarizeUsingTwoPassTypedWeakSummary(int i) {
		LOGGER.setLevel(Level.INFO);
		System.out.println("################################################################################");
		System.out.println("Typed Weak summary test " + Integer.toString(i) + " only summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-2ptypedweak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-2ptypedweak/test-" + i + "_2ptw_noSaturation.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "2ptypedweak", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public File saturateAndSummarizeUsingTwoPassTypedWeakSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Typed Weak summary test " + Integer.toString(i) + " saturation and summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-2ptypedweak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-2ptypedweak/test-" + i + "_2ptw_classical.nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", "2ptypedweak", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summarizationTechnique) {
		return "src/test/resources/test" + i + "-2ptypedweak/test-" + i + "_2ptw_" + summarizationTechnique + "-reference.nt";
	}

	// Summarization tests only with the graphs that have type triples
	@Test
	public void summarizeTwoPassTypedWeakTest1() {
		String referenceFileName = expectedOutput(1, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(1);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 1 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest2() {
		String referenceFileName = expectedOutput(2, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 2 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest3() {
		String referenceFileName = expectedOutput(3, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(3);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 3 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest4() {
		String referenceFileName = expectedOutput(4, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(4);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 4 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest5() {
		String referenceFileName = expectedOutput(5, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(5);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 5 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest6() {
		String referenceFileName = expectedOutput(6, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 6 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest7() {
		String referenceFileName = expectedOutput(7, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(7);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 7 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest11() {
		String referenceFileName = expectedOutput(11, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(11);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 11 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest12() {
		String referenceFileName = expectedOutput(12, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 12 " + e.toString());
		}
	}

	@Test
	public void summarizeTwoPassTypedWeakTest14() {
		String referenceFileName = expectedOutput(14, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTwoPassTypedWeakSummary(14);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 14 " + e.toString());
		}
	}

	// Saturation and summarization tests with all the graphs that have type
	// triples (saturation has no impact on the input graph if it doesn't
	// contain the schema however it may change the order of the triples and it
	// may be useful to check for the correctness)
	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest1() {
		String referenceFileName = expectedOutput(1, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(1);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 1 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest2() {
		String referenceFileName = expectedOutput(2, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 2 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest3() {
		String referenceFileName = expectedOutput(3, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(3);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 3 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest4() {
		String referenceFileName = expectedOutput(4, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(4);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 4 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest5() {
		String referenceFileName = expectedOutput(5, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(5);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 5 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest6() {
		String referenceFileName = expectedOutput(6, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 6 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest7() {
		String referenceFileName = expectedOutput(7, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(7);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 7 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest11() {
		String referenceFileName = expectedOutput(11, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(11);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 11 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest12() {
		String referenceFileName = expectedOutput(12, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 12 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTwoPassTypedWeakTest14() {
		String referenceFileName = expectedOutput(14, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTwoPassTypedWeakSummary(14);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary 2ptypedweak 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 2ptypedweak test 14 " + e.toString());
		}
	}
}
