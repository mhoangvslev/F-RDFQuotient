package summaries.weak;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.Test;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.summaries.SummaryBuilder;
import fr.inria.cedar.quotientSummary.summaries.weak.WeakSummarization;
import junit.framework.Assert;

public class WeakSummaryTest {

	@Test
	public void testWeakSummarizationOfRDFNTFile() {
		String[] args = new String[1];
		args[0] = "resources/rdf-nt-files/model-03_17.nt"; 
		Connection conn; 
		try {
			conn = SummaryBuilder.loadRDFInPostgres(args);
		} catch (IOException | UnsupportedDatabaseEngineException | SQLException e) {
			throw new IllegalStateException("Loading RDF graph in Postgres failed: " + e.toString()); 
		} 	
		try {
			SummaryBuilder.summarizeGraphFromPostgres(conn, args);
		} catch (SQLException | IOException e) {
			throw new IllegalStateException("Summarizing RDF graph from Postgres failed: " + e.toString()); 
		} 
		
	}
	
	@Test
    public void testWeakSummarizationOfIntegerEncodedFile()
    {

    	WeakSummarization ws = new WeakSummarization();
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
	
}
