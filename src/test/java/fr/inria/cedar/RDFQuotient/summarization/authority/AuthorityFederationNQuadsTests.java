//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.summarization.authority;

import fr.inria.cedar.RDFQuotient.summarization.Tests;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * N-Quads counterpart to {@link AuthorityFederationTests}: both the input dataset and the summary
 * output are genuine N-Quads here, and the output is verified with a real RDF library (RDF4J)
 * rather than regex-on-lines.
 * <p>
 * Dataset (test-authority.nq): entity1 and entity2 share authority https://a.example.org and an
 * identical type set {Person} - they must still merge into one summary node (regression check).
 * entity3 has a different authority (https://b.example.org) but the same type set - it must get
 * its own, separate summary node (the federation behavior under test). entity1 additionally has a
 * literal-valued prop#name triple, exercising the literal-authority-inheritance path from PLAN.md
 * ("Authority extraction"). Each input line's graph term (its own per-source named graph) is
 * intentionally distinct from the entity/authority URIs, to confirm it is genuinely parsed and
 * then correctly dropped - authority comes from the subject/object URI regex, not from the input's
 * named-graph context (see NQuadsToNTriplesConverter).
 * <p>
 * The summary output's own graph term follows a different, unrelated rule (PLAN.md "Output:
 * N-Quads for GRAPH-clause querying"): every summary quad's graph = the authority of its subject.
 */
public class AuthorityFederationNQuadsTests extends Tests {
	private static final String DATASET = "src/test/resources/test-authority-typed/test-authority.nq";
	private static final IRI PERSON_TYPE = SimpleValueFactory.getInstance().createIRI("http://qstest.org/class#Person");
	private static final IRI NAME_PROPERTY = SimpleValueFactory.getInstance().createIRI("http://qstest.org/prop#name");
	private static final Pattern AUTHORITY_PATTERN = Pattern.compile("^(https?://.*)/[^/]*$");

	private static String authorityOf(String uri) {
		Matcher m = AUTHORITY_PATTERN.matcher(uri);
		return m.matches() ? m.group(1) : null;
	}

	@Test
	public void entitiesFromDifferentAuthoritiesAreNotMergedInNQuadsOutput() throws IOException {
		String summaryFilename = testSummarizationOfNotSaturatedNQuads(DATASET, "typed");

		Model model;
		try (FileInputStream in = new FileInputStream(summaryFilename)) {
			model = Rio.parse(in, "", RDFFormat.NQUADS);
		}

		// entity1+entity2 (same authority) must merge into one Person summary node; entity3
		// (different authority) must get its own - so exactly two distinct Person summary nodes
		HashSet<Resource> personSummaryNodeSubjects = new HashSet<>();
		for (Statement st : model.filter(null, RDF.TYPE, PERSON_TYPE)) {
			personSummaryNodeSubjects.add(st.getSubject());
		}
		assertEquals("Expected two distinct summary nodes typed Person (one per authority), got: "
				+ personSummaryNodeSubjects,
			2, personSummaryNodeSubjects.size());

		// the fixpoint fix (PLAN.md) mints each summary node's URI under its own authority
		boolean oneUnderAuthorityA = false;
		boolean oneUnderAuthorityB = false;
		for (Resource subject : personSummaryNodeSubjects) {
			String uri = subject.stringValue();
			if (uri.startsWith("https://a.example.org/")) {
				oneUnderAuthorityA = true;
			}
			if (uri.startsWith("https://b.example.org/")) {
				oneUnderAuthorityB = true;
			}
		}
		assertTrue("Expected one Person summary node minted under https://a.example.org/, got: "
			+ personSummaryNodeSubjects, oneUnderAuthorityA);
		assertTrue("Expected one Person summary node minted under https://b.example.org/, got: "
			+ personSummaryNodeSubjects, oneUnderAuthorityB);

		// every summary quad's graph term must equal the authority of its own subject (PLAN.md:
		// "graph = subject's authority"); every subject here has a real (non-AUTHORITY_NONE)
		// authority, so every statement must carry a graph term
		for (Statement st : model) {
			String subjectAuthority = authorityOf(st.getSubject().stringValue());
			assertNotNull("Summary statement subject has no computable authority: " + st, subjectAuthority);
			assertNotNull("Summary statement has no graph term, but its subject has a real authority: " + st,
				st.getContext());
			assertEquals("Summary statement's graph term must equal its subject's authority: " + st,
				subjectAuthority, st.getContext().stringValue());
		}

		// the literal-valued name triple must land in authority A's graph, proving it inherited
		// entity1's authority rather than falling through to AUTHORITY_NONE/the default graph
		boolean sawNameTriple = false;
		for (Statement st : model.filter(null, NAME_PROPERTY, null)) {
			assertNotNull("Name triple has no graph term: " + st, st.getContext());
			assertEquals("Name triple's graph term must be authority A: " + st,
				"https://a.example.org", st.getContext().stringValue());
			sawNameTriple = true;
		}
		assertTrue("Expected to find the prop#name triple in the output", sawNameTriple);
	}
}
