//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.summarization.typedstrong;

import fr.inria.cedar.quotientSummary.summarization.Tests;
import static fr.inria.cedar.quotientSummary.summarization.Tests.compareWithReference;
import static fr.inria.cedar.quotientSummary.summarization.Tests.testSummarizationOfNotSaturated;
import static fr.inria.cedar.quotientSummary.summarization.Tests.testSummarizationOfSaturated;
import org.junit.Test;

public class TwoPassTypedStrongSummaryTests extends Tests {
	public static String summaryType() {
		return "2ptypedstrong";
	}

	public static String sourceFilename(int i) {
		return "src/test/resources/test" + i + "-" + summaryType() + "/test-" + i + ".nt";
	}

	// Tests for summarization of not saturated graph
	@Test
	public void summarizeNotSaturatedTest1() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(1), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest2() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(2), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest3() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(3), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest4() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(4), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest5() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(5), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest6() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(6), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest7() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(7), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest11() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(11), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest12() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(12), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest14() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(14), summaryType()));
	}

	// Tests for summarization of saturated graph
	// Tests with all graphs (saturation has no impact on the input graph if
	// it doesn't contain the schema however it may change the order of the
	// triples and it may be useful to check for the correctness)
	@Test
	public void summarizeSaturatedTest1() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(1), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest2() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(2), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest3() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(3), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest4() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(4), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest5() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(5), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest6() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(6), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest7() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(7), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest11() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(11), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest12() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(12), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest14() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(14), summaryType()));
	}
}