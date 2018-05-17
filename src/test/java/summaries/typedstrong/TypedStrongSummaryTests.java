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

public class TypedStrongSummaryTests {
	private static final Logger LOGGER = Logger.getLogger(TypedStrongSummaryTests.class.getName());

	public File summarizeUsingTypedStrongSummary(int i) {
		LOGGER.setLevel(Level.INFO);
		System.out.println("################################################################################");
		System.out.println("Typed Strong summary test " + Integer.toString(i) + " only summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-typedstrong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-typedstrong/test-" + i + "_ts_noSaturation.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "typedstrong", inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in typedstrong test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	public File saturateAndSummarizeUsingTypedStrongSummary(int i) {
		System.out.println("################################################################################");
		System.out.println("Typed Strong summary test " + Integer.toString(i) + " saturation and summarization");
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-typedstrong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-typedstrong/test-" + i + "_ts_classical.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "typedstrong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedWithoutSaturation"};
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
			throw new IllegalStateException("Unable to open .nt files in typedstrong test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	private String expectedOutput(int i, String summarizationTechnique) {
		return "src/test/resources/test" + i + "-typedstrong/test-" + i + "_ts_" + summarizationTechnique + "-reference.nt";
	}

	@Test
	public void summarizeTypedStrongTest1() {
		String referenceFileName = expectedOutput(1, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		if (!expectedOutput.exists()){
			fail("Expected output not found " + referenceFileName);
		}
		try {
			File testOutput = summarizeUsingTypedStrongSummary(1);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 1 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedStrongTest2() {
		String referenceFileName = expectedOutput(2, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedStrongSummary(2);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 2 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedStrongTest3() {
		String referenceFileName = expectedOutput(3, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedStrongSummary(3);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 3 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedStrongTest4() {
		String referenceFileName = expectedOutput(4, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedStrongSummary(4);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 4 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedStrongTest5() {
		String referenceFileName = expectedOutput(5, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedStrongSummary(5);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 5 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedStrongTest6() {
		String referenceFileName = expectedOutput(6, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedStrongSummary(6);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 6 " + e.toString());
		}
	}

	@Test
	public void summarizeTypedStrongTest7() {
		String referenceFileName = expectedOutput(7, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedStrongSummary(7); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 7 " + e.toString());
		}
	}
	
	@Test
	public void summarizeTypedStrongTest8() {
		String referenceFileName = expectedOutput(8, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedStrongSummary(8); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 8", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 8 " + e.toString());
		}
	}
	
	@Test
	public void summarizeTypedStrongTest9() {
		String referenceFileName = expectedOutput(9, "noSaturation");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarizeUsingTypedStrongSummary(9); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 9 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedStrongTest1() {
		String referenceFileName = expectedOutput(1, "classical");
		File expectedOutput = new File(referenceFileName);
		if (!expectedOutput.exists()){
			fail("Expected output not found " + referenceFileName);
		}
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(1);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 1 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedStrongTest2() {
		String referenceFileName = expectedOutput(2, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(2);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 2 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedStrongTest3() {
		String referenceFileName = expectedOutput(3, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(3);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 3 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedStrongTest4() {
		String referenceFileName = expectedOutput(4, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(4);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 4 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedStrongTest5() {
		String referenceFileName = expectedOutput(5, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(5);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 5 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedStrongTest6() {
		String referenceFileName = expectedOutput(6, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(6);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 6 " + e.toString());
		}
	}

	@Test
	public void saturateAndSummarizeTypedStrongTest7() {
		String referenceFileName = expectedOutput(7, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(7); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 7 " + e.toString());
		}
	}
	
	@Test
	public void saturateAndSummarizeTypedStrongTest8() {
		String referenceFileName = expectedOutput(8, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(8); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 8", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 8 " + e.toString());
		}
	}
	
	@Test
	public void saturateAndSummarizeTypedStrongTest9() {
		String referenceFileName = expectedOutput(9, "classical");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = saturateAndSummarizeUsingTypedStrongSummary(9); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary typedstrong 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 9 " + e.toString());
		}
	}
}
