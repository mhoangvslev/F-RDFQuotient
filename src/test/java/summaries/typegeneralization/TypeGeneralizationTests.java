//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package summaries.typegeneralization;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.controller.Builder;

/**
 * 
 * @author ioanamanolescu
 * 
 * I tried to add a test to validate thr correctness of the type generalization feature.
 * 
 * The code below checks that the right *nt* answer is obtained. But this is pretty useless
 * because type generalization is coded at the drawing level, not at the summarization level,
 * thus the *nt* file we output is not affected by type generalization (only the DOT file is!)
 * Type generalization is coded at the drawing level because:
 * 
 * a. this was easier to code
 * b. (more fundamentally) we did not want to "break the summarization contract" which says:
 * 
 * if n type t in G, then rep(n) type rep(t) in the summary
 * Generalizing *in the summary* breaks this contract. 
 * And indeed, this is also a reason for not coding it this way: the code has many checks that ensure
 * that the right numbers of triples have been reflected, that any type present in the input is present in the output etc.
 * If we "read triple a type t" and attempt not to have rep(a) type rep(t) in the summary, the code
 * safety checks will scream.
 * 
 * All of this means that the only meaningful testing would be based on the DOT file.
 * 
 * However, the DOT file is impacted by a. the random choice of colors and b. the limit of how many
 * types to display from a given namespace. c. any other parameters that impact drawing.
 * Which means that the test class needs to be able to force some parameter values (to ensure the
 * execution environment). And I'm not sure how to do this now. 
 *  
 */
public class TypeGeneralizationTests {
	private static final Logger LOGGER = Logger.getLogger(TypeGeneralizationTests.class.getName());
	
	public static File summarize(int i, String summarizationMethod){ // use "typedstrong" or "typedweak"
		LOGGER.setLevel(Level.INFO);
		String inputFileName =  "src/test/resources/test" + i + "-typegen/test-" + i + ".nt";
		String outputFileName = "src/test/resources/test" + i + "-typegen/test-" + i + "_" + 
				((summarizationMethod.equals("typedstrong"))?"ts":"tw") + "-reference.nt";
		
		System.out.println("############################################");
		System.out.println("Type generalization test: " + summarizationMethod + " summarization of " + inputFileName);
		System.out.println("#############################################");
		try {
			String[] argsSum = {"loadAndSummarize", summarizationMethod, inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedWithoutSaturation"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedClassicalWay", 
						"foldleaves",  
						inputFileName};
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
			throw new IllegalStateException("Unable to open .nt files in " + summarizationMethod +
					" "+ inputFileName + " or to write output file in " + outputFileName + " " + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}
	private String expectedOutput(int i, String summarizationMethod) {
		return "src/test/resources/test" + i + 
				"-typegen/test-" + i + "_" + 
				((summarizationMethod.equals("typedstrong"))?"ts":"tw") + "-reference.nt";
	}

	@Test
	public void summarizeTypeGenTest15() {
		String referenceFileName = expectedOutput(15, "typedstrong");
		File expectedOutput = new File(referenceFileName);
		try {
			File testOutput = summarize(15, "typedstrong");
			if (!testOutput.exists()) {
				fail("Test output not found at " + referenceFileName);
			}
			assertTrue("Different ts type-generalization summary 15", FileUtils.contentEquals(testOutput, expectedOutput));
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files in test15-typegen " + e.toString());
		}
	}
}
