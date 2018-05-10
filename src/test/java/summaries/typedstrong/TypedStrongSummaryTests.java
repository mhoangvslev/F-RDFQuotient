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

public class TypedStrongSummaryTests {
	public File typedstrong(int i) {
		System.out.println("################################################################################");
		System.out.println("Typed Strong summary test " + Integer.toString(i));
		System.out.println("################################################################################");
		String inputFileName = "src/test/resources/test" + i + "-typedstrong/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-typedstrong/ts_test-" + i + ".nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", "typedstrong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummary", inputFileName};
				Builder.main(argsSave);
				argsSave[0] = "exportSummary";
				Builder.main(argsSave);
			}
			catch (UnsupportedDatabaseEngineException ex) {
				Logger.getLogger(TypedStrongSummaryTests.class.getName()).error(ex);
			}
			finally {
				String[] argsCloseConnection = {"closeConnection"};
				try {
					Builder.main(argsCloseConnection);
				}
				catch (UnsupportedDatabaseEngineException ex1) {
					Logger.getLogger(TypedStrongSummaryTests.class.getName()).error(ex1);
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

	@Test
	public void testtypedstrong1() {
		String referenceFileName = "src/test/resources/test1-typedstrong/ts_test-1-reference.nt";
		File expectedOutput = new File(referenceFileName);
		if (!expectedOutput.exists()){
			fail("Expected output not found " + referenceFileName);
		}
		try {
			File testOutput = typedstrong(1);
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
	public void testtypedstrong2() {
		String referenceFileName = "src/test/resources/test2-typedstrong/ts_test-2-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = typedstrong(2);
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
	public void testtypedstrong3() {
		String referenceFileName = "src/test/resources/test3-typedstrong/ts_test-3-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = typedstrong(3);
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
	public void testtypedstrong4() {
		String referenceFileName = "src/test/resources/test4-typedstrong/ts_test-4-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = typedstrong(4);
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
	public void testtypedstrong5() {
		String referenceFileName = "src/test/resources/test5-typedstrong/ts_test-5-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = typedstrong(5);
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
	public void testtypedstrong6() {
		String referenceFileName = "src/test/resources/test6-typedstrong/ts_test-6-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = typedstrong(6);
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
	public void testtypedstrong7() {
		String referenceFileName = "src/test/resources/test7-typedstrong/ts_test-7-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = typedstrong(7); 
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
}
