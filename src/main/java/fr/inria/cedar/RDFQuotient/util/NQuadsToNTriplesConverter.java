//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;

/*
	The actual triple loader (fr.inria.cedar.ontosql:ontosql-rdfdb, a prebuilt dependency with no
	source in this repo) only understands 3-term N-Triples lines: it splits on whitespace and, given
	a 4-term N-Quads line, silently glues the graph term onto the object rather than rejecting it.
	This class converts a genuine N-Quads file into a well-formed N-Triples file that loader can
	ingest correctly. The graph/context term is intentionally dropped: authority is derived from each
	node's own URI via regex (see Summary.getOrComputeAuthorityId and PLAN.md "Authority extraction"),
	not from RDF named-graph context, so nothing is lost by discarding it here.
 */
public class NQuadsToNTriplesConverter {
	public static boolean isNQuadsFile(String filename) {
		String lower = filename.toLowerCase();
		return lower.endsWith(".nq") || lower.endsWith(".nquads");
	}

	public static String convertToNTriplesFile(String nquadsFilename) {
		String outputFilename = trimExtension(nquadsFilename) + ".converted.nt";
		RDFParser parser = Rio.createParser(RDFFormat.NQUADS);
		try (InputStream in = new FileInputStream(nquadsFilename);
				OutputStream out = new FileOutputStream(outputFilename)) {
			RDFWriter writer = Rio.createWriter(RDFFormat.NTRIPLES, out);
			parser.setRDFHandler(writer);
			parser.parse(in, new File(nquadsFilename).toURI().toString());
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not convert N-Quads file to N-Triples: " + nquadsFilename, e);
		}
		return outputFilename;
	}

	private static String trimExtension(String fileName) {
		int lastDotPosition = fileName.lastIndexOf('.');
		return lastDotPosition < 0 ? fileName : fileName.substring(0, lastDotPosition);
	}
}
