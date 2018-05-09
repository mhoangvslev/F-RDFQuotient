package summaries.weak;

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

public class WeakSummaryTests {
	public File weak(int i) {
		System.out.println("################################################################################");
		System.out.println("Weak summary test " + Integer.toString(i));
		System.out.println("################################################################################");
		String inputFileName = "src/test/resources/test" + i + "-weak/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-weak/w_test-" + i + ".nt";
		try {
			String[] argsSum = {"loadWithSaturationAndSummarize", "weak", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummary", inputFileName};
				Builder.main(argsSave);
			}
			catch (UnsupportedDatabaseEngineException ex) {
				Logger.getLogger(WeakSummaryTests.class.getName()).log(Level.SEVERE, null, ex);
			}
			finally {
				String[] argsCloseConnection = {"closeConnection"};
				try {
					Builder.main(argsCloseConnection);
				}
				catch (UnsupportedDatabaseEngineException ex1) {
					Logger.getLogger(WeakSummaryTests.class.getName()).log(Level.SEVERE, null, ex1);
				}
			}
			return new File(outputFileName);
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test " + i + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	@Test
	public void testweak1() {
		String referenceFileName = "src/test/resources/test1-weak/w_test-1-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(1);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 1", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 1 " + e.toString());
		}
	}

	@Test
	public void testweak2() {
		String referenceFileName = "src/test/resources/test2-weak/w_test-2-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(2);
			if (!testOutput.exists()){
				fail("Test output not found ");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 2", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 2 " + e.toString());
		}
	}

	@Test
	public void testweak3() {
		String referenceFileName = "src/test/resources/test3-weak/w_test-3-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(3);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 3", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 3 " + e.toString());
		}
	}

	@Test
	public void testweak4() {
		String referenceFileName = "src/test/resources/test4-weak/w_test-4-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(4);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 4", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 4 " + e.toString());
		}
	}

	@Test
	public void testweak5() {
		String referenceFileName = "src/test/resources/test5-weak/w_test-5-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(5);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 5", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 5 " + e.toString());
		}
	}

	@Test
	public void testweak6() {
		String referenceFileName = "src/test/resources/test6-weak/w_test-6-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(6);
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 6", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 6 " + e.toString());
		}
	}

	@Test
	public void testweak7() {
		String referenceFileName = "src/test/resources/test7-weak/w_test-7-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(7); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 7", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 7 " + e.toString());
		}
	}

	@Test
	public void testweak8() {
		String referenceFileName = "src/test/resources/test8-weak/w_test-8-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(8); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 8", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 8 " + e.toString());
		}
	}

	@Test
	public void testweak9() {
		String referenceFileName = "src/test/resources/test9-weak/w_test-9-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = weak(9); 
			if (!testOutput.exists()){
				fail("Test output not found");
			}
			if (!expectedOutput.exists()){
				fail("Expected output not found " + referenceFileName);
			}
			assertTrue("Different summary weak 9", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 9 " + e.toString());
		}
	}
}
