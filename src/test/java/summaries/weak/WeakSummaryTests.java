package summaries.weak;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.controller.Builder;
import fr.inria.cedar.quotientSummary.weak.WeakSummary;

public class WeakSummaryTests {


	public File weak(int i) {
		String inputFileName = "src/test/resources/test" + i + "-weak/test-" + i + ".nt"; 
		String outputFileName = "src/test/resources/test" + i + "-weak/w_test-" + i + ".nt";
		try {		
			Connection conn = Builder.loadSingleRDFInPostgres(inputFileName);	
			//countConnections("1", conn); 
			// the summarizer also gets the summary name
			String[] args = {"weak", inputFileName}; 
			Builder.summarizeGraphFromPostgres(conn, args); 
			//countConnections("2", conn); 
			conn.close();
			return new File(outputFileName);
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test "+ i + " " + e.toString()); 
		} catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString()); 
		}
	}

	private void countConnections(String prefix, Connection conn) {
		try ( Statement statement = conn.createStatement() ) {
            try ( ResultSet resultSet = statement.executeQuery(
                    "SELECT COUNT(*) " +
                    "FROM pg_stat_activity " +
                    "WHERE state ILIKE '%idle%'" ) ) {
                while ( resultSet.next() ) {
                    System.out.println(prefix +"### Currently there are " +  resultSet.getInt( 1 ) + " connections"); 
                }
                resultSet.close();
                statement.close();
            }
        }
        catch ( SQLException e ) {
            throw new IllegalStateException( e );
        }
	}

	@Test
	public void testweak1() {
		String referenceFileName = "src/test/resources/test1-weak/w_test-1-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			boolean b = FileUtils.contentEquals(weak(1), expectedOutput); 
			System.out.println("Files are equal: " + b); 
			
			assertTrue("Different summary weak 1", b); 
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 1 " + e.toString()); 
		}
	}
	@Test
	public void testweak2() {
		String referenceFileName = "src/test/resources/test2-weak/w_test-2-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary weak 2", FileUtils.contentEquals(weak(2), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 2 " + e.toString()); 
		}
	}
	@Test
	public void testweak3() { 
		String referenceFileName = "src/test/resources/test3-weak/w_test-3-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary weak 3", FileUtils.contentEquals(weak(3), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 3 " + e.toString()); 
		}
	}
	@Test
	public void testweak4() {
		String referenceFileName = "src/test/resources/test4-weak/w_test-4-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary weak 4", FileUtils.contentEquals(weak(4), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 4 " + e.toString()); 
		}
	}
	/*
	@Test
	public void testWeakSummarizationOfRDFNTFile() {
		//SummaryBuilder.loadInPostgresAndSummarize("resources/rdf-nt-files/model-03_17.nt"); 
		Summary.readSummaryFromFile("resources/rdf-nt-files/model-03_17.nt"); 
		//resources/rdf-nt-files/model-03_17.nt");		
	}*/
	
	@Test
    public void testWeakSummarizationOfIntegerEncodedFile()
    {

    	WeakSummary ws = new WeakSummary();
    	try {
    		ws.summarizeFromTripleFiles("src/test/resources/test1-weak/0.nt", 
    			     "src/test/resources/test1-weak/short.nt", "weak");
    		// TODO fix the test (use the correct output)
    		String expectedOutput = "<0 2 1>\n<2 5 1>\n<2 6 3>\n<3 8 0>\n";
    		String output = ws.toString();
    		System.out.println("Output: #" + output + "#");
    		System.out.println("Expected: #" + expectedOutput + "#");
    		Assert.assertEquals(expectedOutput, output);
    	} catch(Exception e){
    		Assert.fail();
    	}
    }
	
	public void testweak5() {
		String referenceFileName = "src/test/resources/test5-weak/w_test-5-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary weak 5", FileUtils.contentEquals(weak(5), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 5 " + e.toString()); 
		}
	}

	@Test
	public void testweak6() {
		String referenceFileName = "src/test/resources/test6-weak/w_test-6-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary weak 6", FileUtils.contentEquals(weak(6), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 6 " + e.toString()); 
		}
	}
	

	@Test
	public void testweak7() {
		String referenceFileName = "src/test/resources/test7-weak/w_test-7-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary weak 7", FileUtils.contentEquals(weak(7), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 7 " + e.toString()); 
		}
	}
	

	@Test
	public void testweak8() { // Macron file, from Huong
		String referenceFileName = "src/test/resources/test8-weak/w_test-8-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary weak 8", FileUtils.contentEquals(weak(8), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in weak test 8 " + e.toString()); 
		}
	}
	
}
