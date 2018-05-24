package summaries.typedstrong;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.controller.Builder;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class TwoPassStrongSummaryTests {
	private static final Logger LOGGER = Logger.getLogger(TwoPassStrongSummaryTests.class.getName());
	public File summarizeUsingStrongSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Strong summary test " + Integer.toString(i) + " only summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-2pstrong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-2pstrong/test-" + i + "_s_noSaturation.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "strong", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in 2pstrong test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public File saturateAndSummarizeUsingStrongSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Strong summary test " + Integer.toString(i) + " saturation and summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-strong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-strong/test-" + i + "_s_classical.nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", "strong", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in strong test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public File summarizeThroughShortcutUsingStrongSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Strong summary test " + Integer.toString(i) + " summarization through shortcut");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-strong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-strong/test-" + i + "_s_shortcut.nt";
		try {
			String[] argsSum = {"loadAndSummarizeUsingShortcut", "strong", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in strong test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summarizationTechnique) {
		return "src/test/resources/test" + i + "-strong/test-" + i + "_s_" + summarizationTechnique + "-reference.nt";
	}

	@Test
	public void summarizeStrongTest1() {
		String referenceFileName = expectedOutput(1, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingStrongSummary(1);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary strong 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in strong test 1 " + e.toString());
		}
	}
}