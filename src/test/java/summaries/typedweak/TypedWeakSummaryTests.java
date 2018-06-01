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

public class TypedWeakSummaryTests {
	private static final Logger LOGGER = Logger.getLogger(TypedWeakSummaryTests.class.getName());

	public File summarizeUsingTypedWeakSummary(int i) {
		LOGGER.setLevel(Level.INFO);
		System.out.println("################################################################################");
		System.out.println("Typed Weak summary test " + Integer.toString(i) + " only summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-typedweak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-typedweak/test-" + i + "_tw_noSaturation.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "typedweak", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in typedweak test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public File saturateAndSummarizeUsingTypedWeakSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Typed Weak summary test " + Integer.toString(i) + " saturation and summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-typedweak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-typedweak/test-" + i + "_tw_classical.nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", "typedweak", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in typedweak test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summarizationTechnique) {
		return "src/test/resources/test" + i + "-typedweak/test-" + i + "_tw_" + summarizationTechnique + "-reference.nt";
	}

	// Summarization tests only with the graphs that have type triples
	@Test
	public void summarizeTypedWeakTest1() {
		String referenceFileName = expectedOutput(1, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(1);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 1 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest2() {
		String referenceFileName = expectedOutput(2, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 2 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest3() {
		String referenceFileName = expectedOutput(3, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(3);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 3 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest4() {
		String referenceFileName = expectedOutput(4, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(4);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 4 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest5() {
		String referenceFileName = expectedOutput(5, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(5);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 5 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest6() {
		String referenceFileName = expectedOutput(6, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 6 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest7() {
		String referenceFileName = expectedOutput(7, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(7);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 7 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest11() {
		String referenceFileName = expectedOutput(11, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(11);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 11 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest12() {
		String referenceFileName = expectedOutput(12, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 12 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedWeakTest14() {
		String referenceFileName = expectedOutput(14, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedWeakSummary(14);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 14 " + e.toString());
		}
	}

	// Saturation and summarization tests with all the graphs that have type
	// triples (saturation has no impact on the input graph if it doesn't
	// contain the schema however it may change the order of the triples and it
	// may be useful to check for the correctness)
	@Test
	public void saturateAndSummarizeTypedWeakTest1() {
		String referenceFileName = expectedOutput(1, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(1);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 1 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest2() {
		String referenceFileName = expectedOutput(2, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(2);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 2 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest3() {
		String referenceFileName = expectedOutput(3, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(3);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 3 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest4() {
		String referenceFileName = expectedOutput(4, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(4);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 4 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest5() {
		String referenceFileName = expectedOutput(5, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(5);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 5 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest6() {
		String referenceFileName = expectedOutput(6, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 6 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest7() {
		String referenceFileName = expectedOutput(7, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(7);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 7 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest11() {
		String referenceFileName = expectedOutput(11, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(11);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 11 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest12() {
		String referenceFileName = expectedOutput(12, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(12);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 12 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedWeakTest14() {
		String referenceFileName = expectedOutput(14, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedWeakSummary(14);
			if (!testOutput.exists()) {
				fail("Test output not found");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedweak 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 14 " + e.toString());
		}
	}
}
