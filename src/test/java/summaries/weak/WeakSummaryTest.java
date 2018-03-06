package summaries.weak;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import fr.inria.cedar.quotientSummary.summaries.weak.WeakSummarization;

public class WeakSummaryTest {

	@Test
    public void testWeakSummary()
    {
    	WeakSummarization ws = new WeakSummarization();
    	try{
    		ws.summarizeFromTripleFiles("src/test/resources/test1-weak/0.nt", 
    			     "src/test/resources/test1-weak/short.nt", "weak");
    		// TODO fix the test (use the correct output)
    		String expectedOutput = "<0 2 1>\n<2 5 1>\n<2 6 3>\n<3 8 0>\n";
    		String output = ws.toString();
    		System.out.println("Output: #" + output + "#");
    		System.out.println("Expected: #" + expectedOutput + "#");
    		assertTrue(output.equals(expectedOutput));
    	}
    	catch(Exception e){
    		e.printStackTrace();
    	}
    }
}
