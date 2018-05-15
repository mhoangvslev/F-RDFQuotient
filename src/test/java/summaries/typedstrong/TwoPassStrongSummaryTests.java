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

	public File strong2p(int i) {
		System.out.println("################################################################################");
		System.out.println("Two-pass Strong summary test " + Integer.toString(i));
		System.out.println("################################################################################");

		String inputFileName = "src/test/resources/test" + i + "-2pstrong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-2pstrong/s_test-" + i + ".nt";
		try {
			String[] argsSum = {"loadAndSummarize", "2pstrong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedWithoutSaturation"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummary", inputFileName, "draw"};
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

	@Test
	public void test2pstrong1() {
		String referenceFileName = "src/test/resources/test1-2pstrong/s_test-1-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong2p(1);
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
	public void test2pstrong2() {
		String referenceFileName = "src/test/resources/test2-2pstrong/s_test-2-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong2p(2);
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
	public void test2pstrong3() {
		String referenceFileName = "src/test/resources/test3-2pstrong/s_test-3-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong2p(3);
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
	public void test2pstrong4() {
		String referenceFileName = "src/test/resources/test4-2pstrong/s_test-4-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong2p(4);
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
	public void test2pstrong5() {
		String referenceFileName = "src/test/resources/test5-2pstrong/s_test-5-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong2p(5);
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
	public void test2pstrong6() {
		String referenceFileName = "src/test/resources/test6-2pstrong/s_test-6-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong2p(6);
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
	public void test2pstrong7() {
		String referenceFileName = "src/test/resources/test7-2pstrong/s_test-7-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong2p(7);
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
	@Test
	public void test2pstrong9() {
		String referenceFileName = "src/test/resources/test9-2pstrong/s_test-9-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = strong2p(9);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary strong 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in strong test 9 " + e.toString());
		}
	}
}
