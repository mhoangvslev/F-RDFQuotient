package summaries.biggerdata;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.controller.Builder;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LUBMTests {
	private static final Logger LOGGER = Logger.getLogger(LUBMTests.class.getName());

	public String summarize(int testNumber, int i, String summaryTypeLong, String summaryTypeShort, String summarizationTechnique, Boolean draw) {
		LOGGER.setLevel(Level.INFO);
		String[] metaArgs = new String[3];
		System.out.println("################################################################################");
		switch (summarizationTechnique) {
			case "noSaturation":
				metaArgs[0] = "loadAndSummarize";
				metaArgs[1] = "saveSummaryComputedWithoutSaturation";
				metaArgs[2] = "exportSummaryComputedWithoutSaturation";
				System.out.println("LUBM Test " + testNumber + ": LUBM " + i + "M, " + summaryTypeLong + " summary, only summarization");
				break;
			case "classical":
				metaArgs[0] = "loadWithSaturationAndSummarize";
				metaArgs[1] = "saveSummaryComputedClassicalWay";
				metaArgs[2] = "exportSummaryComputedClassicalWay";
				System.out.println("LUBM Test " + testNumber + ": LUBM " + i + "M, " + summaryTypeLong + " summary, saturation and summarization");
				break;
			case "shortcut":
				metaArgs[0] = "loadAndSummarizeUsingShortcut";
				metaArgs[1] = "saveSummaryComputedUsingShortcut";
				metaArgs[2] = "exportSummaryComputedUsingShortcut";
				System.out.println("LUBM Test " + testNumber + ": LUBM " + i + "M, " + summaryTypeLong + " summary, summarization through shortcut");
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
			return outputFileName;
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test LUBM " + i + "M " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summaryTypeShort, String summarizationTechnique) {
		return "src/test/resources/LUBM-" + i + "M/LUBM-" + i + "M_" + summaryTypeShort + "_" + summarizationTechnique + ".nt";
	}

	@Test
	public void LUBMTest1() {
		String expectedOutput = expectedOutput(1, "w", "noSaturation");
		String testOutput = summarize(1, 1, "weak", "w", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 1", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest2() {
		String expectedOutput = expectedOutput(1, "tw", "noSaturation");
		String testOutput = summarize(2, 1, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 2", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest3() {
		String expectedOutput = expectedOutput(1, "s", "noSaturation");
		String testOutput = summarize(3, 1, "strong", "s", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 3", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest4() {
		String expectedOutput = expectedOutput(1, "ts", "noSaturation");
		String testOutput = summarize(4, 1, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 4", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest5() {
		String expectedOutput = expectedOutput(10, "w", "noSaturation");
		String testOutput = summarize(5, 10, "weak", "w", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 5", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest6() {
		String expectedOutput = expectedOutput(10, "tw", "noSaturation");
		String testOutput = summarize(6, 10, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 6", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest7() {
		String expectedOutput = expectedOutput(10, "s", "noSaturation");
		String testOutput = summarize(7, 10, "strong", "s", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 7", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest8() {
		String expectedOutput = expectedOutput(10, "ts", "noSaturation");
		String testOutput = summarize(8, 10, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 8", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest9() {
		String expectedOutput = expectedOutput(100, "w", "noSaturation");
		String testOutput = summarize(9, 100, "weak", "w", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 9", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest10() {
		String expectedOutput = expectedOutput(100, "tw", "noSaturation");
		String testOutput = summarize(10, 100, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 10", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest11() {
		String expectedOutput = expectedOutput(100, "s", "noSaturation");
		String testOutput = summarize(11, 100, "strong", "s", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 11", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest12() {
		String expectedOutput = expectedOutput(100, "ts", "noSaturation");
		String testOutput = summarize(12, 100, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 12", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest13() {
		String expectedOutput = expectedOutput(1, "2ps", "noSaturation");
		String testOutput = summarize(13, 1, "2pstrong", "2ps", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 13", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest14() {
		String expectedOutput = expectedOutput(10, "2ps", "noSaturation");
		String testOutput = summarize(14, 10, "2pstrong", "2ps", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 14", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest15() {
		String expectedOutput = expectedOutput(100, "2ps", "noSaturation");
		String testOutput = summarize(15, 100, "2pstrong", "2ps", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 15", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest16() {
		String expectedOutput = expectedOutput(1, "1fb", "noSaturation");
		String testOutput = summarize(16, 1, "onefb", "1fb", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 16", testOutput.equals(expectedOutput));
	}

	@Test
	public void LUBMTest17() {
		String expectedOutput = expectedOutput(10, "1fb", "noSaturation");
		String testOutput = summarize(17, 10, "onefb", "1fb", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary LUBM test 17", testOutput.equals(expectedOutput));
	}
}
