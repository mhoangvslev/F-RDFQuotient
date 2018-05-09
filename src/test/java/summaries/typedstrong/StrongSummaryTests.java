package summaries.typedstrong;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.controller.Builder;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class StrongSummaryTests {
	public File strong(int i) {
		System.out.println("################################################################################");
		System.out.println("Strong summary test " + Integer.toString(i));
		System.out.println("################################################################################");
		String inputFileName = "src/test/resources/test" + i + "-strong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-strong/s_test-" + i + ".nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", "strong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummary", inputFileName};
				Builder.main(argsSave);
			}
			catch (UnsupportedDatabaseEngineException ex) {
				Logger.getLogger(StrongSummaryTests.class.getName()).log(Level.SEVERE, null, ex);
			}
			finally {
				String[] argsCloseConnection = {"closeConnection"};
				try {
					Builder.main(argsCloseConnection);
				}
				catch (UnsupportedDatabaseEngineException ex1) {
					Logger.getLogger(StrongSummaryTests.class.getName()).log(Level.SEVERE, null, ex1);
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

	@Test
	public void teststrong1() {
		String referenceFileName = "src/test/resources/test1-strong/s_test-1-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong(1);
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

	@Test
	public void teststrong2() {
		String referenceFileName = "src/test/resources/test2-strong/s_test-2-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong(2);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary strong 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in strong test 2 " + e.toString());
		}
	}

	@Test
	public void teststrong3() {
		String referenceFileName = "src/test/resources/test3-strong/s_test-3-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong(3);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary strong 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in strong test 3 " + e.toString());
		}
	}

	@Test
	public void teststrong4() {
		String referenceFileName = "src/test/resources/test4-strong/s_test-4-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong(4);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary strong 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in strong test 4 " + e.toString());
		}
	}

	@Test
	public void teststrong5() {
		String referenceFileName = "src/test/resources/test5-strong/s_test-5-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong(5);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary strong 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in strong test 5 " + e.toString());
		}
	}

	@Test
	public void teststrong6() {
		String referenceFileName = "src/test/resources/test6-strong/s_test-6-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong(6);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary strong 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in strong test 6 " + e.toString());
		}
	}

	@Test
	public void teststrong7() {
		String referenceFileName = "src/test/resources/test7-strong/s_test-7-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong(7);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary strong 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in strong test 7 " + e.toString());
		}
	}
}
