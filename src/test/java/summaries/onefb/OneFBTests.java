package summaries.onefb;

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

public class OneFBTests {
	private static final Logger LOGGER = Logger.getLogger(OneFBTests.class.getName());

	public File summarizeUsingOneFBSummary(int i) {
		LOGGER.setLevel(Level.INFO);
		System.out.println("################################################################################");
		System.out.println("OneFB summary test " + i + " only summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-onefb/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-onefb/test-" + i + "_1fb_noSaturation.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "onefb", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in strong test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summarizationTechnique) {
		return "src/test/resources/test" + i + "-onefb/test-" + i + "_1fb_" + summarizationTechnique + "-reference.nt";
	}
	@Test
	public void summarizeOneFBTest6() {
		String referenceFileName = expectedOutput(6, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingOneFBSummary(6);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary one-fb 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 1-fb test 6 " + e.toString());
		}
	}
	@Test
	public void summarizeOneFBTest8() {
		String referenceFileName = expectedOutput(8, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingOneFBSummary(8);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary one-fb 8", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 1-fb test 8 " + e.toString());
		}
	}
	@Test
	public void summarizeOneFBTest9() {
		String referenceFileName = expectedOutput(9, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingOneFBSummary(9);
			if (!testOutput.exists()) {
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()) {
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary one-fb 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in 1-fb test 9 " + e.toString());
		}
	}
	
}
