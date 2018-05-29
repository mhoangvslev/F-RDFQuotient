package summaries.biggerdata;

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
import org.junit.Ignore;
import org.junit.Test;

public class BSBMTests {
	private static final Logger LOGGER = Logger.getLogger(BSBMTests.class.getName());

	public File summarize(int i, String summaryTypeLong, String summaryTypeShort, String summarizationTechnique, Boolean draw) {
		LOGGER.setLevel(Level.INFO);
		String[] metaArgs = new String[3];
		System.out.println("################################################################################");
		switch (summarizationTechnique) {
			case "noSaturation":
				metaArgs[0] = "loadAndSummarize";
				metaArgs[1] = "saveSummaryComputedWithoutSaturation";
				metaArgs[2] = "exportSummaryComputedWithoutSaturation";
				System.out.println("Test BSBM " + i + "M, " + summaryTypeLong + " summary, only summarization");
				break;
			case "classical":
				metaArgs[0] = "loadWithSaturationAndSummarize";
				metaArgs[1] = "saveSummaryComputedClassicalWay";
				metaArgs[2] = "exportSummaryComputedClassicalWay";
				System.out.println("Test BSBM " + i + "M, " + summaryTypeLong + " summary, saturation and summarization");
				break;
			case "shortcut":
				metaArgs[0] = "loadAndSummarizeUsingShortcut";
				metaArgs[1] = "saveSummaryComputedUsingShortcut";
				metaArgs[2] = "exportSummaryComputedUsingShortcut";
				System.out.println("Test BSBM " + i + "M, " + summaryTypeLong + " summary, summarization through shortcut");
				break;
			default:
				LOGGER.error("Wrong argument");
				return null;
		}
		System.out.println("################################################################################");

		String inputFileName = "/data/datasets/bsbm/bsbm" + i + "m.nt";
		String outputFileNameBase = "src/test/resources/BSBM-" + i + "M/BSBM-" + i + "M";
		String outputFileName = outputFileNameBase + "_" + summaryTypeShort + "_" + summarizationTechnique + ".nt";
		try {
			String[] argsSum = {metaArgs[0], summaryTypeLong, inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {metaArgs[1]};
				Builder.main(argsSave);
				String[] argsExport = {metaArgs[2], draw ? "draw" : "do_not_draw", outputFileNameBase + ".nt"};
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
			throw new IllegalStateException("Unable to open .nt files in weak test BSBM " + i + "M " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summaryTypeShort, String summarizationTechnique) {
		return "src/test/resources/BSBM-" + i + "M/BSBM-" + i + "M_" + summaryTypeShort + "_" + summarizationTechnique + "-reference.nt";
	}

	@Test
	public void BSBMTest1() {
		String referenceFileName = expectedOutput(1, "w", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "weak", "w", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 1 " + e.toString());
		}
	}

	@Test
	public void BSBMTest2() {
		String referenceFileName = expectedOutput(1, "w", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "weak", "w", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 2 " + e.toString());
		}
	}

	@Test
	public void BSBMTest3() {
		String referenceFileName = expectedOutput(1, "w", "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "weak", "w", "shortcut", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 3 " + e.toString());
		}
	}

	@Test
	public void BSBMTest4() {
		String referenceFileName = expectedOutput(1, "tw", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "typedweak", "tw", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 4 " + e.toString());
		}
	}

	@Test
	public void BSBMTest5() {
		String referenceFileName = expectedOutput(1, "tw", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "typedweak", "tw", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 5 " + e.toString());
		}
	}

	@Test
	public void BSBMTest6() {
		String referenceFileName = expectedOutput(1, "s", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "strong", "s", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 6 " + e.toString());
		}
	}

	@Test
	public void BSBMTest7() {
		String referenceFileName = expectedOutput(1, "s", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "strong", "s", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 7 " + e.toString());
		}
	}

	@Test
	public void BSBMTest8() {
		String referenceFileName = expectedOutput(1, "s", "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "strong", "s", "shortcut", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 8", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 8 " + e.toString());
		}
	}

	@Test
	public void BSBMTest9() {
		String referenceFileName = expectedOutput(1, "ts", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 9 " + e.toString());
		}
	}

	@Test
	public void BSBMTest10() {
		String referenceFileName = expectedOutput(1, "ts", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(1, "typedstrong", "ts", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 10", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 10 " + e.toString());
		}
	}

	@Test
	public void BSBMTest11() {
		String referenceFileName = expectedOutput(10, "w", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "weak", "w", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 11 " + e.toString());
		}
	}

	@Test
	public void BSBMTest12() {
		String referenceFileName = expectedOutput(10, "w", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "weak", "w", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 12 " + e.toString());
		}
	}

	@Test
	public void BSBMTest13() {
		String referenceFileName = expectedOutput(10, "w", "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "weak", "w", "shortcut", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 13", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 13 " + e.toString());
		}
	}

	@Test
	public void BSBMTest14() {
		String referenceFileName = expectedOutput(10, "tw", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "typedweak", "tw", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 14", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 14 " + e.toString());
		}
	}

	@Test
	public void BSBMTest15() {
		String referenceFileName = expectedOutput(10, "tw", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "typedweak", "tw", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 15", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 15 " + e.toString());
		}
	}

	@Test
	public void BSBMTest16() {
		String referenceFileName = expectedOutput(10, "s", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "strong", "s", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 16", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 16 " + e.toString());
		}
	}

	@Test
	public void BSBMTest17() {
		String referenceFileName = expectedOutput(10, "s", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "strong", "s", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 17", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 17 " + e.toString());
		}
	}

	@Test
	public void BSBMTest18() {
		String referenceFileName = expectedOutput(10, "s", "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "strong", "s", "shortcut", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 18", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 18 " + e.toString());
		}
	}

	@Test
	public void BSBMTest19() {
		String referenceFileName = expectedOutput(10, "ts", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 19", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 19 " + e.toString());
		}
	}

	@Test
	public void BSBMTest20() {
		String referenceFileName = expectedOutput(10, "ts", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(10, "typedstrong", "ts", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 20", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 20 " + e.toString());
		}
	}

	@Test
	public void BSBMTest21() {
		String referenceFileName = expectedOutput(100, "w", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "weak", "w", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 21", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 21 " + e.toString());
		}
	}

	@Test
	public void BSBMTest22() {
		String referenceFileName = expectedOutput(100, "w", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "weak", "w", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 22", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 22 " + e.toString());
		}
	}

	@Test
	public void BSBMTest23() {
		String referenceFileName = expectedOutput(100, "w", "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "weak", "w", "shortcut", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 23", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 23 " + e.toString());
		}
	}

	@Test
	public void BSBMTest24() {
		String referenceFileName = expectedOutput(100, "tw", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "typedweak", "tw", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 24", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 24 " + e.toString());
		}
	}

	@Test
	public void BSBMTest25() {
		String referenceFileName = expectedOutput(100, "tw", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "typedweak", "tw", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 25", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 25 " + e.toString());
		}
	}

	@Test
	public void BSBMTest26() {
		String referenceFileName = expectedOutput(100, "s", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "strong", "s", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 26", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 26 " + e.toString());
		}
	}

	@Test
	public void BSBMTest27() {
		String referenceFileName = expectedOutput(100, "s", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "strong", "s", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 27", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 27 " + e.toString());
		}
	}

	@Test
	public void BSBMTest28() {
		String referenceFileName = expectedOutput(100, "s", "shortcut");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "strong", "s", "shortcut", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 28", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 28 " + e.toString());
		}
	}

	@Test
	public void BSBMTest29() {
		String referenceFileName = expectedOutput(100, "ts", "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 29", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 29 " + e.toString());
		}
	}

	@Test
	public void BSBMTest30() {
		String referenceFileName = expectedOutput(100, "ts", "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(100, "typedstrong", "ts", "classical", Boolean.FALSE);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary BSBM test 30", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in BSBM test 30 " + e.toString());
		}
	}
}
