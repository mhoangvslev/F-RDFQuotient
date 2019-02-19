//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.controller;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
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
			LOGGER.error(ex);
			return null;
		}
		catch (IOException ex) {
			LOGGER.error(ex);
			return null;
		}
		return properties;
	}

	public static Properties reconcileProperties(Properties lessImportantProperties, Properties moreImportantProperties) {
		lessImportantProperties.putAll(moreImportantProperties);
		return lessImportantProperties;
	}
}