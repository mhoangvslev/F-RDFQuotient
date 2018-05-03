package summaries.typedstrong;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import fr.inria.cedar.quotientSummary.controller.Builder;

/**
 * Unit test for simple StrongSummarization.
 */
public class TypedStrongSummaryTests 
{

	public File typedstrong(int i) {
		System.out.println("Typed Strong test");
		String inputFileName = "src/test/resources/test" + i + "-typedstrong/test-" + i + ".nt"; 
		String outputFileName = "src/test/resources/test" + i + "-typedstrong/ts-test-" + i + ".nt";
		try {		
			Connection conn = Builder.loadSingleRDFInPostgres(inputFileName);	
			//countConnections("1", conn); 
			// the summarizer also gets the summary name
			String[] args = {"typedstrong", inputFileName}; 
			Builder.summarizeGraphFromPostgres(conn, args); 
			//countConnections("2", conn); 
			conn.close();
			return new File(outputFileName);
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test "+ i + " " + e.toString()); 
		} catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString()); 
		}
	}
	
	@Test @Ignore 
	public void testtypedstrong1() {
		String referenceFileName = "src/test/resources/test1-typedstrong/ts-test-1-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedstrong 1", FileUtils.contentEquals(typedstrong(1), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 1 " + e.toString()); 
		}
	}
	@Test @Ignore
	public void testtypedstrong2() {
		String referenceFileName = "src/test/resources/test2-typedstrong/ts-test-2-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedstrong 2", FileUtils.contentEquals(typedstrong(2), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 2 " + e.toString()); 
		}
	}
	@Test @Ignore
	public void testtypedstrong4() {
		String referenceFileName = "src/test/resources/test4-typedstrong/ts-test-4-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedstrong 4", FileUtils.contentEquals(typedstrong(4), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 4 " + e.toString()); 
		}
	}
	@Test @Ignore
	public void testtypedstrong5() {
		String referenceFileName = "src/test/resources/test5-typedstrong/ts-test-5-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedstrong 5", FileUtils.contentEquals(typedstrong(5), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 5 " + e.toString()); 
		}
	}
	@Test @Ignore
	public void testtypedstrong6() {
		String referenceFileName = "src/test/resources/test6-typedstrong/ts-test-6-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedstrong 6", FileUtils.contentEquals(typedstrong(6), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 6 " + e.toString()); 
		}
	}
	@Test @Ignore
	public void testtypedstrong7() {
		String referenceFileName = "src/test/resources/test7-typedstrong/ts-test-7-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedstrong 7", FileUtils.contentEquals(typedstrong(7), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedstrong test 7 " + e.toString()); 
		}
	}
}
