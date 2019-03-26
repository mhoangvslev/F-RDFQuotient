//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.controller;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class SummarizationProperties extends ConfigurationProperties {
	private static final Logger LOGGER = Logger.getLogger(SummarizationProperties.class.getName());

	static final String DEFAULT_SUMMARIZATION_PROPERTIES_FILENAME = "conf/summarization.properties";

	public SummarizationProperties() {
		LOGGER.setLevel(Level.INFO);
	}

	public static String getDefaultPropertiesFilename() {
		return DEFAULT_SUMMARIZATION_PROPERTIES_FILENAME;
	}

	public static Properties getDefaultProperties() {
		Properties properties = new Properties();

		properties.put("dataset.filename", "test.nt");

		// Database configuration

		properties.put("database.host", "localhost");
		properties.put("database.port", "5432");
		properties.put("database.user", "postgres");
		properties.put("database.password", "postgres");
		properties.put("database.name", "");
		properties.put("database.triples_table_name", "triples");
		properties.put("database.encoded_triples_table_name", "encoded_triples");
		properties.put("database.dictionary_table_name", "dictionary");
		properties.put("database.encoded_saturated_triples_table_name", "encoded_saturated_triples");

		// Whether to sort encode_triples and encoded_saturated_triples tables
		// used for tests, should not be used for big graphs
		properties.put("database.deterministic_ordering", "false");

		// Summarization configuration

		properties.put("summary.summarize_saturated_graph", "false");

		// Valid options: weak, strong, typedweak, typedstrong, 2pweak, 2pweakunionfind, 2pstrong, 2ptypedweak, 2ptypedstrong, onefb, onefw
		properties.put("summary.type", "typedweak");

		// Whether to omit generic RDF properties from clique computation
		properties.put("summary.omit_generic_properties_from_cliques", "true");

		// Which generic properties to use: comma-separated values
		properties.put("summary.generic_properties", "<http://www.w3.org/2000/01/rdf-schema#label>,<http://www.w3.org/2000/01/rdf-schema#comment>,<http://www.w3.org/1999/02/22-rdf-syntax-ns#value>,<http://www.w3.org/2002/07/owl#sameAs>");

		// In type triples, whether to replace the type with the most general type
		properties.put("summary.replace_type_with_most_general_type", "true");

		// Whether to run consistency checks after each summarized triple
		// This may make summarization significantly slower; set it to true only for debugging.
		properties.put("summary.consistency_checks", "false");

		// Whether to export to NT file
		properties.put("summary.export_to_nt_file", "true");

		// Prefix added to summary NT file, usually to dispatch it into a separate directory
		properties.put("summary.nt_file_prefix", "summariesNT/");

		// Whether to export to database
		properties.put("summary.export_to_database", "true");

		// Whether to store representation function and node statistics while exporting to database
		properties.put("summary.save_representation_function_and_node_statistics", "true");

		// Whether to compute support statistics and add them in the .nt printout of the summary
		properties.put("summary.gather_representation_counts", "true");

		// Whether to execute step-by-step summarization
		properties.put("summary.step_by_step", "false");

		// Whether to export run statistics
		properties.put("statistics.export_to_csv_file", "true");

		// Whether to export summarization configuration to disk
		properties.put("configuration.export_to_disk", "false");

		// Drawing configuration

		// Path to dot executable; if this is not found, it is not an error, just drawing won't work
		properties.put("drawing.dot_installation", "/usr/local/bin/dot");

		// Whether to remove DOT file in case of successful execution of dot
		properties.put("drawing.remove_dot_file", "false");

		// Prefixes added to drawing files, usually to dispatch them into separate directories
		properties.put("drawing.dot_file_prefix", "summariesDOT/");
		properties.put("drawing.png_file_prefix", "summariesPNG/");

		// Whether to draw input RDF graph
		properties.put("drawing.draw_input_graph", "false");

		// Whether to draw step-by-step drawings, valid options: false, always, when_changes
		properties.put("drawing.step_by_step", "false");

		// Visualization variant, valid options: plain, split_leaves, split_and_fold_leaves; if invalid option is specified, drawing will be suppressed
		properties.put("drawing.style", "split_and_fold_leaves");

		// The color scheme to use for drawing; available color schemes are: diverse (default), bw, ivory, and light
		properties.put("drawing.color_scheme", "diverse");

		// This prefix will be used for all the URIs of nodes created by summarization.
		// Its value must be such that if one appends a short summary code (a few characters) then a number, the result is an URI.
		properties.put("drawing.summary_node_URI_prefix", "http://rq.org/");

		// Whether to include a graph label when drawing the summary, or not
		properties.put("drawing.title", "true");

		// Maximum number of characters used to label a summary node when written in a DOT file
		// Currently, literals will be shown as a suffix of at most this length, while
		// URIs will be shown as the suffix after the last slash (which may be longer than this).
		properties.put("drawing.max_node_label_length", "20");

		// The maximum number of types to be displayed from each namespace (more are replaced with "...")
		properties.put("drawing.max_types_drawn_per_namespace", "5");

		// This URI will be used as a property, to denote the number of graph nodes represented by a given summary node
		properties.put("drawing.summary_node_support_URI_prefix", "http://rq.org/nodeSupport");

		// This URI will be used as a property, to denote the number of graph edges represented by a given summary edge
		properties.put("drawing.summary_edge_support_URI_prefix", "http://rq.org/edgeSupport");

		// This prefix will be used to assign URIs (through reification) to summary edges
		properties.put("drawing.reified_summary_edge_URI_prefix", "http://rq.org/edge");

		// The next three URIs will be used to describe reified summary edges, in order to state their support
		properties.put("drawing.reified_edge_subject_URI_prefix", "http://rq.org/reifiedEdgeSubject");
		properties.put("drawing.reified_edge_property_URI_prefix", "http://rq.org/reifiedEdgeProperty");
		properties.put("drawing.reified_edge_object_URI_prefix", "http://rq.org/reifiedEdgeObject");

		return properties;
	}

	public static void writePropertiesFile(Properties properties, String filename) {
		try {
			try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
				TreeMap<String, String> propertiesSorted = new TreeMap<>();
				for (Object property: properties.keySet()) {
					propertiesSorted.put((String) property, properties.getProperty((String) property));
				}

				Iterator<String> it = propertiesSorted.keySet().iterator();
				while(it.hasNext()) {
					String property = it.next();
					if (it.hasNext()) {
						pw.println(property + "=" + propertiesSorted.get(property));
					}
					else {
						pw.print(property + "=" + propertiesSorted.get(property));
					}
				}
			}
		}
		catch (IOException ex) {
			LOGGER.error(ex);
		}
	}

	public static void writeDefaultPropertiesFile() {
		Properties properties = getDefaultProperties();
		String filename = DEFAULT_SUMMARIZATION_PROPERTIES_FILENAME;
		writePropertiesFile(properties, filename);
	}

	public static void main(String[] args) {
		writeDefaultPropertiesFile();
	}
}