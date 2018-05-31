package summaries.biggerdata;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.controller.Builder;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BSBMTests {
	private static final Logger LOGGER = Logger.getLogger(BSBMTests.class.getName());

	public String summarize(int testNumber, int i, String summaryTypeLong, String summaryTypeShort, String summarizationTechnique, Boolean draw) {
		LOGGER.setLevel(Level.INFO);
		String[] metaArgs = new String[3];
		System.out.println("################################################################################");
		switch (summarizationTechnique) {
			case "noSaturation":
				metaArgs[0] = "loadAndSummarize";
				metaArgs[1] = "saveSummaryComputedWithoutSaturation";
				metaArgs[2] = "exportSummaryComputedWithoutSaturation";
				System.out.println("BSBM Test " + testNumber + ": BSBM " + i + "M, " + summaryTypeLong + " summary, only summarization");
				break;
			case "classical":
				metaArgs[0] = "loadWithSaturationAndSummarize";
				metaArgs[1] = "saveSummaryComputedClassicalWay";
				metaArgs[2] = "exportSummaryComputedClassicalWay";
				System.out.println("BSBM Test " + testNumber + ": BSBM " + i + "M, " + summaryTypeLong + " summary, saturation and summarization");
				break;
			case "shortcut":
				metaArgs[0] = "loadAndSummarizeUsingShortcut";
				metaArgs[1] = "saveSummaryComputedUsingShortcut";
				metaArgs[2] = "exportSummaryComputedUsingShortcut";
				System.out.println("BSBM Test " + testNumber + ": BSBM " + i + "M, " + summaryTypeLong + " summary, summarization through shortcut");
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
			return outputFileName;
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test BSBM " + i + "M " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summaryTypeShort, String summarizationTechnique) {
		return "src/test/resources/BSBM-" + i + "M/BSBM-" + i + "M_" + summaryTypeShort + "_" + summarizationTechnique + ".nt";
	}

	@Test
	public void BSBMTest1() {
		String expectedOutput = expectedOutput(1, "w", "noSaturation");
		String testOutput = summarize(1, 1, "weak", "w", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 1", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest2() {
		String expectedOutput = expectedOutput(1, "w", "classical");
		String testOutput = summarize(2, 1, "weak", "w", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 2", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest3() {
		String expectedOutput = expectedOutput(1, "w", "shortcut");
		String testOutput = summarize(3, 1, "weak", "w", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 3", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest4() {
		String expectedOutput = expectedOutput(1, "tw", "noSaturation");
		String testOutput = summarize(4, 1, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 4", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest5() {
		String expectedOutput = expectedOutput(1, "tw", "classical");
		String testOutput = summarize(5, 1, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 5", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest6() {
		String expectedOutput = expectedOutput(1, "s", "noSaturation");
		String testOutput = summarize(6, 1, "strong", "s", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 6", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest7() {
		String expectedOutput = expectedOutput(1, "s", "classical");
		String testOutput = summarize(7, 1, "strong", "s", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 7", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest8() {
		String expectedOutput = expectedOutput(1, "s", "shortcut");
		String testOutput = summarize(8, 1, "strong", "s", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 8", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest9() {
		String expectedOutput = expectedOutput(1, "ts", "noSaturation");
		String testOutput = summarize(9, 1, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 9", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest10() {
		String expectedOutput = expectedOutput(1, "ts", "classical");
		String testOutput = summarize(10, 1, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 10", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest11() {
		String expectedOutput = expectedOutput(10, "w", "noSaturation");
		String testOutput = summarize(11, 10, "weak", "w", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 11", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest12() {
		String expectedOutput = expectedOutput(10, "w", "classical");
		String testOutput = summarize(12, 10, "weak", "w", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 12", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest13() {
		String expectedOutput = expectedOutput(10, "w", "shortcut");
		String testOutput = summarize(13, 10, "weak", "w", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 13", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest14() {
		String expectedOutput = expectedOutput(10, "tw", "noSaturation");
		String testOutput = summarize(14, 10, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 14", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest15() {
		String expectedOutput = expectedOutput(10, "tw", "classical");
		String testOutput = summarize(15, 10, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 15", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest16() {
		String expectedOutput = expectedOutput(10, "s", "noSaturation");
		String testOutput = summarize(16, 10, "strong", "s", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 16", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest17() {
		String expectedOutput = expectedOutput(10, "s", "classical");
		String testOutput = summarize(17, 10, "strong", "s", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 17", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest18() {
		String expectedOutput = expectedOutput(10, "s", "shortcut");
		String testOutput = summarize(18, 10, "strong", "s", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 18", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest19() {
		String expectedOutput = expectedOutput(10, "ts", "noSaturation");
		String testOutput = summarize(19, 10, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 19", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest20() {
		String expectedOutput = expectedOutput(10, "ts", "classical");
		String testOutput = summarize(20, 10, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 20", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest21() {
		String expectedOutput = expectedOutput(100, "w", "noSaturation");
		String testOutput = summarize(21, 100, "weak", "w", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 21", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest22() {
		String expectedOutput = expectedOutput(100, "w", "classical");
		String testOutput = summarize(22, 100, "weak", "w", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 22", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest23() {
		String expectedOutput = expectedOutput(100, "w", "shortcut");
		String testOutput = summarize(23, 100, "weak", "w", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 23", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest24() {
		String expectedOutput = expectedOutput(100, "tw", "noSaturation");
		String testOutput = summarize(24, 100, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 24", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest25() {
		String expectedOutput = expectedOutput(100, "tw", "classical");
		String testOutput = summarize(25, 100, "typedweak", "tw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 25", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest26() {
		String expectedOutput = expectedOutput(100, "s", "noSaturation");
		String testOutput = summarize(26, 100, "strong", "s", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 26", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest27() {
		String expectedOutput = expectedOutput(100, "s", "classical");
		String testOutput = summarize(27, 100, "strong", "s", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 27", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest28() {
		String expectedOutput = expectedOutput(100, "s", "shortcut");
		String testOutput = summarize(28, 100, "strong", "s", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 28", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest29() {
		String expectedOutput = expectedOutput(100, "ts", "noSaturation");
		String testOutput = summarize(29, 100, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 29", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest30() {
		String expectedOutput = expectedOutput(100, "ts", "classical");
		String testOutput = summarize(30, 100, "typedstrong", "ts", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 30", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest31() {
		String expectedOutput = expectedOutput(1, "2ps", "noSaturation");
		String testOutput = summarize(31, 1, "2pstrong", "2ps", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 31", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest32() {
		String expectedOutput = expectedOutput(1, "2ps", "classical");
		String testOutput = summarize(32, 1, "2pstrong", "2ps", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 32", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest33() {
		String expectedOutput = expectedOutput(1, "2ps", "shortcut");
		String testOutput = summarize(33, 1, "2pstrong", "2ps", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 33", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest34() {
		String expectedOutput = expectedOutput(10, "2ps", "noSaturation");
		String testOutput = summarize(34, 10, "2pstrong", "2ps", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 34", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest35() {
		String expectedOutput = expectedOutput(10, "2ps", "classical");
		String testOutput = summarize(35, 10, "2pstrong", "2ps", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 35", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest36() {
		String expectedOutput = expectedOutput(10, "2ps", "shortcut");
		String testOutput = summarize(36, 10, "2pstrong", "2ps", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 36", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest37() {
		String expectedOutput = expectedOutput(100, "2ps", "noSaturation");
		String testOutput = summarize(37, 100, "2pstrong", "2ps", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 37", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest38() {
		String expectedOutput = expectedOutput(100, "2ps", "classical");
		String testOutput = summarize(38, 100, "2pstrong", "2ps", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 38", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest39() {
		String expectedOutput = expectedOutput(100, "2ps", "shortcut");
		String testOutput = summarize(39, 100, "2pstrong", "2ps", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 39", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest40() {
		String expectedOutput = expectedOutput(1, "1fb", "noSaturation");
		String testOutput = summarize(40, 1, "onefb", "1fb", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 40", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest41() {
		String expectedOutput = expectedOutput(10, "1fb", "noSaturation");
		String testOutput = summarize(41, 10, "onefb", "1fb", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 41", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest42() {
		String expectedOutput = expectedOutput(1, "2pw", "noSaturation");
		String testOutput = summarize(42, 1, "2pweak", "2pw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 42", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest43() {
		String expectedOutput = expectedOutput(1, "2pw", "classical");
		String testOutput = summarize(43, 1, "2pweak", "2pw", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 43", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest44() {
		String expectedOutput = expectedOutput(1, "2pw", "shortcut");
		String testOutput = summarize(44, 1, "2pweak", "2pw", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 44", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest45() {
		String expectedOutput = expectedOutput(10, "2pw", "noSaturation");
		String testOutput = summarize(45, 10, "2pweak", "2pw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 45", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest46() {
		String expectedOutput = expectedOutput(10, "2pw", "classical");
		String testOutput = summarize(46, 10, "2pweak", "2pw", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 46", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest47() {
		String expectedOutput = expectedOutput(10, "2pw", "shortcut");
		String testOutput = summarize(47, 10, "2pweak", "2pw", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 47", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest48() {
		String expectedOutput = expectedOutput(100, "2pw", "noSaturation");
		String testOutput = summarize(48, 100, "2pweak", "2pw", "noSaturation", Boolean.FALSE);
		assertTrue("Different summary BSBM test 48", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest49() {
		String expectedOutput = expectedOutput(100, "2pw", "classical");
		String testOutput = summarize(49, 100, "2pweak", "2pw", "classical", Boolean.FALSE);
		assertTrue("Different summary BSBM test 49", testOutput.equals(expectedOutput));
	}

	@Test
	public void BSBMTest50() {
		String expectedOutput = expectedOutput(100, "2pw", "shortcut");
		String testOutput = summarize(50, 100, "2pweak", "2pw", "shortcut", Boolean.FALSE);
		assertTrue("Different summary BSBM test 50", testOutput.equals(expectedOutput));
	}
}
