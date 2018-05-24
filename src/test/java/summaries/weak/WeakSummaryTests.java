package summaries.weak;

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

public class WeakSummaryTests {
	private static final Logger LOGGER = Logger.getLogger(WeakSummaryTests.class.getName());

	public File summarizeUsingWeakSummary(int i) {
		LOGGER.setLevel(Level.INFO);
		System.out.println("################################################################################");
		System.out.println("Weak summary test " + Integer.toString(i) + " only summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-weak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-weak/test-" + i + "_w_noSaturation.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "weak", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in weak test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public File saturateAndSummarizeUsingWeakSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Weak summary test " + Integer.toString(i) + " saturation and summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-weak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-weak/test-" + i + "_w_classical.nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", "weak", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in weak test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public File summarizeThroughShortcutUsingWeakSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Weak summary test " + Integer.toString(i) + " summarization through shortcut");
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
	}

	private String expectedOutput(int i, String summarizationTechnique) {
		return "src/test/resources/test" + i + "-weak/test-" + i + "_w_" + summarizationTechnique + "-reference.nt";
	}

	@Test
	public void summarizeWeakTest1() {
		String referenceFileName = expectedOutput(1, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(1);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(2, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(2);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(3, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(3);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(4, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(4);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(5, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(5);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(6, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(6);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(7, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(7); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(8, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(8); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(9, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(9); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(10, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(10);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(11, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(11);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
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
		String referenceFileName = expectedOutput(12, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingWeakSummary(12);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 12 " + e.toString());
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
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 12 " + e.toString());
		}
	}

	// Shortcut tests only with the graphs that have a schema
	@Test
	public void summarizeThroughShortcutWeakTest6() {
		String referenceFileName = expectedOutput(6, "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeThroughShortcutUsingWeakSummary(6);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
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
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 12 " + e.toString());
		}
	}
}
