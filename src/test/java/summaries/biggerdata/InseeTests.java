//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package summaries.biggerdata;

import fr.inria.cedar.quotientSummary.controller.BuilderCmd;
import org.junit.Test;

public class InseeTests {
	@Test
	public void test1() {
		//String[] argsLoad = {"load", "../Bigger datasets/insee/insee_geo.nt", "false", "false"};
		//BuilderCmd.main(argsLoad);
		String[] argsSummarize = {"summarize", "../Bigger datasets/insee/insee_geo.nt", "strong", "false", "true", "true", "false"};
		BuilderCmd.main(argsSummarize);
	}

	@Test
	public void test2() {
		//String[] argsLoad = {"load", "../Bigger datasets/insee/insee_geo.nt", "false", "false"};
		//BuilderCmd.main(argsLoad);
		String[] argsSummarize = {"summarize", "../Bigger datasets/insee/insee_geo.nt", "2pstrong", "false", "true", "true", "false"};
		BuilderCmd.main(argsSummarize);
	}
}