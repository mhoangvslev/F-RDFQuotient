//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.summarization.authority;

import fr.inria.cedar.RDFQuotient.summarization.Tests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers the authority equivalence component (see PLAN.md): entities from different sources must
 * never be merged into the same summary node, even when they'd otherwise be indistinguishable, while
 * entities from the same source must still merge exactly as before.
 * <p>
 * Dataset (test-authority.nt): entity1 and entity2 share authority https://a.example.org and an
 * identical type set {Person} - they should still merge into one summary node (regression check).
 * entity3 has a different authority (https://b.example.org) but the same type set - it must get its
 * own, separate summary node (the actual federation behavior under test).
 * <p>
 * Uses TypedSummary ("typed"), the most self-contained equivalence path (pure type-based grouping,
 * unaffected by the strong/weak families' coarser literal-authority policy).
 * <p>
 * NOT byte-exact golden-file comparison like the rest of the suite (compareWithReference): asserting
 * exact output would require regenerating a reference .nt file, which needs an actual summarization
 * run against Postgres - not available while writing this. Structural assertions on the output
 * instead: node counts and the authority-embedded URI prefixes (see the fixpoint fix in PLAN.md).
 */
public class AuthorityFederationTests extends Tests {
	private static final String DATASET = "src/test/resources/test-authority-typed/test-authority.nt";
	private static final String PERSON_TYPE_TRIPLE_SUFFIX =
		" <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://qstest.org/class#Person> .";

	@Test
	public void entitiesFromDifferentAuthoritiesAreNotMerged() throws IOException {
		String summaryFilename = testSummarizationOfNotSaturated(DATASET, "typed");
		List<String> lines = Files.readAllLines(Paths.get(summaryFilename));

		HashSet<String> personSummaryNodeSubjects = new HashSet<>();
		Pattern subjectPattern = Pattern.compile("^(<[^>]+>) ");
		for (String line : lines) {
			if (line.endsWith(PERSON_TYPE_TRIPLE_SUFFIX)) {
				Matcher m = subjectPattern.matcher(line);
				assertTrue("Could not parse subject out of type triple: " + line, m.find());
				personSummaryNodeSubjects.add(m.group(1));
			}
		}

		// entity1+entity2 (same authority) merge into one summary node; entity3 (different
		// authority) must get its own - so exactly two Person summary nodes, not one
		assertEquals("Expected two distinct summary nodes typed Person (one per authority), got: "
				+ personSummaryNodeSubjects,
			2, personSummaryNodeSubjects.size());

		// the fixpoint fix (PLAN.md) mints each summary node's URI under its own authority, so the
		// two Person nodes' URIs should be distinguishable by which authority prefixes them
		boolean oneUnderAuthorityA = false;
		boolean oneUnderAuthorityB = false;
		for (String subject : personSummaryNodeSubjects) {
			if (subject.startsWith("<https://a.example.org/")) {
				oneUnderAuthorityA = true;
			}
			if (subject.startsWith("<https://b.example.org/")) {
				oneUnderAuthorityB = true;
			}
		}
		assertTrue("Expected one Person summary node minted under https://a.example.org/, got: "
			+ personSummaryNodeSubjects, oneUnderAuthorityA);
		assertTrue("Expected one Person summary node minted under https://b.example.org/, got: "
			+ personSummaryNodeSubjects, oneUnderAuthorityB);
	}
}
