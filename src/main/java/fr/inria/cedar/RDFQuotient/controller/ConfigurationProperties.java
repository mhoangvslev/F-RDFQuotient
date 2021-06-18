//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.controller;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.TreeMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class ConfigurationProperties {
	private static final Logger LOGGER = Logger.getLogger(ConfigurationProperties.class.getName());

	public ConfigurationProperties() {
		LOGGER.setLevel(Level.INFO);
	}

	public static Properties getPropertiesFromFile(String filename) {
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(filename));
		}
		catch (FileNotFoundException ex) {
			LOGGER.info(ex);
			return null;
		}
		catch (IOException ex) {
			LOGGER.info(ex);
			return null;
		}
		catch (NullPointerException ex) {
			return null;
		}
		return properties;
	}

	public static Properties reconcileProperties(Properties lessImportantProperties, Properties moreImportantProperties) {
		if (lessImportantProperties == null && moreImportantProperties == null) {
			throw new IllegalArgumentException("Both properties objects are nulls");
		}
		if (lessImportantProperties == null) {
			return moreImportantProperties;
		}
		if (moreImportantProperties == null) {
			return lessImportantProperties;
		}

		lessImportantProperties.putAll(moreImportantProperties);
		return lessImportantProperties;
	}

	public static String prettifiedToString(Properties properties) {
		String prettifiedProperties = "";

		TreeMap<String, String> propertiesSorted = new TreeMap<>();
		for (Object property: properties.keySet()) {
			propertiesSorted.put((String) property, properties.getProperty((String) property));
		}

		for (String property: propertiesSorted.keySet()) {
			prettifiedProperties += property + "=" + propertiesSorted.get(property) + "\n";
		}

		return prettifiedProperties.substring(0, prettifiedProperties.length() - 1);
	}
}
