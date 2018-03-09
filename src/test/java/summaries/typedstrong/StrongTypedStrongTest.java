package summaries.typedstrong;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import fr.inria.cedar.quotientSummary.summaries.typedstrong.StrongSummarization;

/**
 * Unit test for simple StrongSummarization.
 */
public class StrongTypedStrongTest 
{

    @Test
    public void testStrongSummary()
    {
    	StrongSummarization ss = new StrongSummarization();
    	try{
    		ss.summarizeFromTripleFiles("src/test/resources/test1-strong/type.nt", 
    			     "src/test/resources/test1-strong/data.nt", "strong");
    		String expectedOutput = "<0 11 1>\n<0 22 2>\n<0 33 2>\n<0 22 3>\n<0 44 4>\n<4 22 5>\n<0 66 0>\n<4 77 1>\n" +
    			                   "<6 88 0>\n<7 99 6>\n<0 111 7>\n<0 2 3000>\n<0 2 4000>\n<0 2 5000>\n<0 2 6000>\n"+
    			                   "<0 2 1000>\n<4 2 7000>\n<4 2 9000>\n";
    		String output = ss.toString();
    		System.out.println("Output: #" + output + "#");
    		System.out.println("Expected: #" + expectedOutput + "#");
    		assertTrue(output.equals(expectedOutput));
    	}
    	catch(Exception e){
    		e.printStackTrace();
    	}
    }
    
    @Test
    public void testTypedStrongSummary()
    {
    	StrongSummarization ss = new StrongSummarization();
    	try{
    		ss.summarizeFromTripleFiles("src/test/resources/test1-strong/type.nt", 
    			     "src/test/resources/test1-strong/data.nt", "typedstrong");
    		String expectedOutput = "<0 2 3000>\n<0 2 4000>\n<0 2 5000>\n<0 2 6000>\n<0 2 1000>\n<5 2 7000>\n" + 
    		"<5 2 9000>\n<0 11 7>\n<0 22 8>\n<0 33 8>\n<0 44 5>\n<0 44 10>\n<5 22 8>\n<0 66 0>\n" + 
    		"<5 77 7>\n<11 88 0>\n<12 99 11>\n<0 111 13>\n"; 
    		String output = ss.toString();
    		System.out.println("Output:" + output + "#");
    		System.out.println("Expected:" + expectedOutput + "#");
    		assertTrue(output.equals(expectedOutput));
    	}
    	catch(Exception e){
    		e.printStackTrace();
    	}
    }
}
