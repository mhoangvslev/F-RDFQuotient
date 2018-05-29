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

public class LUBMTests {
	private static final Logger LOGGER = Logger.getLogger(LUBMTests.class.getName());

	public File summarize(int i, String summaryTypeLong, String summaryTypeShort, String summarizationTechnique, Boolean draw) {
		LOGGER.setLevel(Level.INFO);
		String[] metaArgs = new String[3];
		System.out.println("################################################################################");
		switch (summarizationTechnique) {
			case "noSaturation":
				metaArgs[0] = "loadAndSummarize";
				metaArgs[1] = "saveSummaryComputedWithoutSaturation";
				metaArgs[2] = "exportSummaryComputedWithoutSaturation";
				System.out.println("Test LUBM " + i + "M, " + summaryTypeLong + " summary, only summarization");
				break;
			case "classical":
				metaArgs[0] = "loadWithSaturationAndSummarize";
				metaArgs[1] = "saveSummaryComputedClassicalWay";
				metaArgs[2] = "exportSummaryComputedClassicalWay";
				System.out.println("Test LUBM " + i + "M, " + summaryTypeLong + " summary, saturation and summarization");
				break;
			case "shortcut":
				metaArgs[0] = "loadAndSummarizeUsingShortcut";
				metaArgs[1] = "saveSummaryComputedUsingShortcut";
				metaArgs[2] = "exportSummaryComputedUsingShortcut";
				System.out.println("Test LUBM " + i + "M, " + summaryTypeLong + " summary, summarization through shortcut");
				break;
			default:
				LOGGER.error("Wrong argument");
				return null;
		}
		System.out.println("################################################################################");

		String inputFileName = "/data/datasets/lubm/lubm" + i + "m.nt";
		String outputFileNameBase = "src/test/resources/LUBM-" + i + "M/LUBM-" + i + "M";
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
			throw new IllegalStateException("Unable to open .nt files in weak test LUBM " + i + "M " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summaryTypeShort, String summarizationTechnique) {
		return "src/test/resources/LUBM-" + i + "M/LUBM-" + i + "M_" + summaryTypeShort + "_" + summarizationTechnique + "-reference.nt";
	}

	@Test
	public void LUBMTest1() {
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
			assertTrue("Different summary LUBM test 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 1 " + e.toString());
		}
	}

	@Test
	public void LUBMTest2() {
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
			assertTrue("Different summary LUBM test 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 2 " + e.toString());
		}
	}

	@Test
	public void LUBMTest3() {
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
			assertTrue("Different summary LUBM test 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 3 " + e.toString());
		}
	}

	@Test
	public void LUBMTest4() {
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
			assertTrue("Different summary LUBM test 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 4 " + e.toString());
		}
	}

	@Test
	public void LUBMTest5() {
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
			assertTrue("Different summary LUBM test 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 5 " + e.toString());
		}
	}

	@Test
	public void LUBMTest6() {
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
			assertTrue("Different summary LUBM test 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 6 " + e.toString());
		}
	}

	@Test
	public void LUBMTest7() {
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
			assertTrue("Different summary LUBM test 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 7 " + e.toString());
		}
	}

	@Test
	public void LUBMTest8() {
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
			assertTrue("Different summary LUBM test 8", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 8 " + e.toString());
		}
	}

	@Test
	public void LUBMTest9() {
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
			assertTrue("Different summary LUBM test 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 9 " + e.toString());
		}
	}

	@Test
	public void LUBMTest10() {
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
			assertTrue("Different summary LUBM test 10", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 10 " + e.toString());
		}
	}

	@Test
	public void LUBMTest11() {
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
			assertTrue("Different summary LUBM test 11", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 11 " + e.toString());
		}
	}

	@Test
	public void LUBMTest12() {
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
			assertTrue("Different summary LUBM test 12", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			fail("Unable to open .nt files in LUBM test 12 " + e.toString());
		}
	}
}
