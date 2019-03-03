//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.controller;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class LoadingProperties extends ConfigurationProperties {
	private static final Logger LOGGER = Logger.getLogger(LoadingProperties.class.getName());

	static final String DEFAULT_LOADING_PROPERTIES_FILENAME = "conf/loading.properties";

	public LoadingProperties() {
		LOGGER.setLevel(Level.INFO);
	}

	public static String getDefaultPropertiesFilename() {
		return DEFAULT_LOADING_PROPERTIES_FILENAME;
	}

	public static Properties getDefaultProperties() {
		Properties properties = new Properties();

		properties.put("dataset.filename", "test.nt");

		// OntoSQL extra configuration
		properties.put("database.engine", "POSTGRESQL");

		properties.put("database.host", "localhost");
		properties.put("database.port", "5432");
		properties.put("database.user", "postgres");
		properties.put("database.password", "postgres");
		properties.put("database.name", "");
		properties.put("database.drop_existing_db", "true");
		// Valid options: TRIPLES_TABLE, TABLE_PER_ROLE_AND_CONCEPT
		properties.put("database.storage_layout", "TRIPLES_TABLE");
		properties.put("database.triples_table_name", "triples");
		properties.put("database.encoded_triples_table_name", "encoded_triples");
		properties.put("database.dictionary_table_name", "dictionary");
		properties.put("dictionary.fetch_size", "1000");
		properties.put("saturation.enable", "false");
		properties.put("database.encoded_saturated_triples_table_name", "encoded_saturated_triples");
		properties.put("saturation.batch_size", "1000");
		properties.put("statistics.export_to_csv_file", "true");

		// OntoSQL extra configuration
		properties.put("statistics.create_tables_flag", "false");

		return properties;
	}

	public static void writeDefaultPropertiesFile() {
		try {
			try (PrintWriter pw = new PrintWriter(new FileWriter(DEFAULT_LOADING_PROPERTIES_FILENAME))) {
				Properties properties = getDefaultProperties();
				properties.remove("database.engine");

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

	public static void main(String[] args) {
		writeDefaultPropertiesFile();
	}
}
