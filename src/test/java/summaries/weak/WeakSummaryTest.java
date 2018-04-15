package summaries.weak;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Test;

import fr.inria.cedar.quotientSummary.controller.Builder;

public class WeakSummaryTest {


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
	@Test
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
	
	public File typedweak(int i) {
		String inputFileName = "src/test/resources/test" + i + "-typedweak/test-" + i + ".nt"; 
		String outputFileName = "src/test/resources/test" + i + "-typedweak/tw_test-" + i + ".nt";
		try {		
			Connection conn = Builder.loadSingleRDFInPostgres(inputFileName);		
			// the summarizer also gets the summary name
			String[] args = {"typedweak", inputFileName}; 
			Builder.summarizeGraphFromPostgres(conn, args); 
			conn.close();
			return new File(outputFileName);
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test "+ i + " " + e.toString()); 
		} catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString()); 
		}
	}

	@Test
	public void testtypedweak1() {
		String referenceFileName = "src/test/resources/test1-typedweak/tw_test-1-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedweak 1", FileUtils.contentEquals(typedweak(1), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 1 " + e.toString()); 
		}
	}
	@Test
	public void testtypedweak2() {
		String referenceFileName = "src/test/resources/test2-typedweak/tw_test-2-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedweak 2", FileUtils.contentEquals(typedweak(2), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 2 " + e.toString()); 
		}
	}
	@Test
	public void testtypedweak3() {
		String referenceFileName = "src/test/resources/test3-typedweak/tw_test-3-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedweak 3", FileUtils.contentEquals(typedweak(3), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 3 " + e.toString()); 
		}
	}
	@Test
	public void testtypedweak4() {
		String referenceFileName = "src/test/resources/test4-typedweak/tw_test-4-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedweak 4", FileUtils.contentEquals(typedweak(4), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 4 " + e.toString()); 
		}
	}
	@Test
	public void testtypedweak5() {
		String referenceFileName = "src/test/resources/test5-typedweak/tw_test-5-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedweak 5", FileUtils.contentEquals(typedweak(5), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 5 " + e.toString()); 
		}
	}
	@Test
	public void testtypedweak6() {
		String referenceFileName = "src/test/resources/test6-typedweak/tw_test-6-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedweak 6", FileUtils.contentEquals(typedweak(6), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 6 " + e.toString()); 
		}
	}
	@Test
	public void testtypedweak7() {
		String referenceFileName = "src/test/resources/test7-typedweak/tw_test-7-reference.nt";
		File expectedOutput = new File(referenceFileName);
		try {
			assertTrue("Different summary typedweak 7", FileUtils.contentEquals(typedweak(7), expectedOutput));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in typedweak test 7 " + e.toString()); 
		}
	}
}
