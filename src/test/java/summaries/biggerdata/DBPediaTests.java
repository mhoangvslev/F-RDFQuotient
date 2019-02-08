//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package summaries.biggerdata;

import fr.inria.cedar.quotientSummary.controller.BuilderCmd;
import org.junit.Test;

public class DBPediaTests {
	@Test
	public void summarizeStrongTest1() {
		//String[] argsLoad = {"load", "../Bigger datasets/dbpedia/dbpedia_persondata_en_uniq.nt", "false", "true"};
		//BuilderCmd.main(argsLoad);
		String[] argsSummarize = {"summarize", "../Bigger datasets/dbpedia/dbpedia_persondata_en_uniq.nt", "strong", "false", "true", "true", "true"};
		BuilderCmd.main(argsSummarize);
	}
}