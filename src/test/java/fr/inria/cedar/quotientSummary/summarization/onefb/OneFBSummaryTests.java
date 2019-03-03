//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.summarization.onefb;

import fr.inria.cedar.quotientSummary.summarization.Tests;
import static fr.inria.cedar.quotientSummary.summarization.Tests.compareWithReference;
import static fr.inria.cedar.quotientSummary.summarization.Tests.testSummarizationOfNotSaturated;
import static fr.inria.cedar.quotientSummary.summarization.Tests.testSummarizationOfSaturated;
import static fr.inria.cedar.quotientSummary.summarization.Tests.testSummarizationThroughShortcut;
import org.junit.Test;

public class OneFBSummaryTests extends Tests {
	public static String summaryType() {
		return "onefb";
	}

	public static String sourceFilename(int i) {
		return "src/test/resources/test" + i + "-" + summaryType() + "/test-" + i + ".nt";
	}

	// Tests for summarization of not saturated graph
	@Test
	public void summarizeNotSaturatedTest6() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(6), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest8() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(8), summaryType()));
	}

	@Test
	public void summarizeNotSaturatedTest9() {
		compareWithReference(testSummarizationOfNotSaturated(sourceFilename(9), summaryType()));
	}

	// Tests for summarization of saturated graph
	// Tests with all graphs (saturation has no impact on the input graph if
	// it doesn't contain the schema however it may change the order of the
	// triples and it may be useful to check for the correctness)
	@Test
	public void summarizeSaturatedTest6() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(6), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest8() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(8), summaryType()));
	}

	@Test
	public void summarizeSaturatedTest9() {
		compareWithReference(testSummarizationOfSaturated(sourceFilename(9), summaryType()));
	}

	// Tests for summarization of a graph through shortcut
	// only with the graphs that have a schema

	@Test
	public void summarizeThroughShortcutTest6() {
		compareWithReference(testSummarizationThroughShortcut(sourceFilename(6), summaryType()));
	}
}