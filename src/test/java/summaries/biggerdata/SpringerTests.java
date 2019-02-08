//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package summaries.biggerdata;

import fr.inria.cedar.quotientSummary.controller.BuilderCmd;
import org.junit.Test;

public class SpringerTests {
	@Test
	public void summarizeWeakTest1() {
		//String[] argsLoad = {"load", "../Bigger datasets/Springer/conference.nt", "false", "true"};
		//BuilderCmd.main(argsLoad);
		String[] argsSummarize = {"summarize", "../Bigger datasets/Springer/conference.nt", "weak", "false", "true", "true", "true"};
		BuilderCmd.main(argsSummarize);
	}

	@Test
	public void summarizeWeakTest2() {
		//String[] argsLoad = {"load", "../Bigger datasets/Springer/conference.nt", "false", "true"};
		//BuilderCmd.main(argsLoad);
		String[] argsSummarize = {"summarize", "../Bigger datasets/Springer/conference.nt", "2pweak", "false", "true", "true", "true"};
		BuilderCmd.main(argsSummarize);
	}
}