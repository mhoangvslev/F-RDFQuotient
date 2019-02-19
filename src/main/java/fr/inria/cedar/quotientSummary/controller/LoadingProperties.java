//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.controller;

import java.util.Enumeration;
import java.util.Properties;

public class LoadingProperties {
	static final String DEFAULT_LOADING_PROPERTIES_FILE_NAME = "conf/loading.properties";

	static Properties reconcileProperties(String loadingPropertiesFileName, String optionValue) {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
	public Properties prop;

	public LoadingProperties(){
		prop = new Properties();

		// ontoSQL properties:
		prop.put("database.engine", "POSTGRESQL");
		prop.put("database.host", "localhost");
		prop.put("database.port", "5432");
		prop.put("database.name", "testkwd1fb");
		prop.put("database.user", "postgres");
		prop.put("database.password", "postgres");
		prop.put("database.triples_table_name", "triples");
		prop.put("database.encoded_triples_table_name", "encoded_triples");
		prop.put("database.dictionary_table_name", "dictionary");
		prop.put("database.storage_layout", "TRIPLES_TABLE");
		prop.put("database.storage_layout", "TABLE_PER_ROLE_AND_CONCEPT");
		prop.put("database.drop_existing_db", "true");
		prop.put("saturation.batch_size", "1000");
		prop.put("saturation.enable", "false");
		prop.put("statistics.create_tables_flag", "false");
		prop.put("dictionary.fetch_size", "1000");
	}

	public void put(String name, String value){
		prop.put(name, value);
	}

	public void overrideFrom(Properties otherProps){
		Enumeration<?> e = otherProps.propertyNames();
		while (e.hasMoreElements()){
			String name = (String)(e.nextElement());
			String value = otherProps.getProperty(name);
			prop.put(name,  value);
		}
	}

	public Properties getProperties() {
		return prop;
	}
}
