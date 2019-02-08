//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary;

import java.util.Properties;

public class SummarizationProperties {
	Properties prop;
	
	public SummarizationProperties(){
		prop = new Properties();
		
		// This prefix will be used for all the URIs of nodes created by summarization. 
		// Its value must be such that if one appends a short summary code (a few characters) then a number, the result is an URI.
		prop.put("prefixURIForSummaryNodes", "http://qs.org/");
		// Whether or not to compute support statistics and add them in the .nt printout of the summary
		prop.put("gatherStatistics", "false"); 
		// Whether or not to run consistency checks after each summarized triple 
		// This may make summarization significantly slower; set it to true only for debugging.
		prop.put("consistencyChecks", "false"); 
		// Maximum number of characters used to label a summary node when written in a DOT file
		// Currently, literals will be shown as a suffix of at most this length, while 
		// URIs will be shown as the suffix after the last slash (which may be longer than this).
		prop.put("maxNodeLabelLength", "20"); 
		// This URI will be used as a property, to denote the number of graph nodes represented by a given summary node
		prop.put("summaryNodeSupportURI", "http://quotientsummary.org/nodeSupport");
		// This URI will be used as a property, to denote the number of graph edges represented by a given summary edge
		prop.put("summaryEdgeSupportURI", "http://quotientsummary.org/edgeSupport");
		// This prefix will be used to assign URIs (through reification) to summary edges
		prop.put("reifiedSummaryEdgeURIPrefix", "http://quotientsummary.org/edge"); 
		//The next three URIs will be used to describe reified summary edges, in order to state their support
		prop.put("reifiedEdgeHasSubject", "http://quotientsummary.org/reifiedEdgeSubject");
		prop.put("reifiedEdgeHasProperty", "http://quotientsummary.org/reifiedEdgeProperty");
		prop.put("reifiedEdgeHasObject", "http://quotientsummary.org/reifiedEdgeObject");
		// Path to dot executable. If this is not found, it is not an error, just drawing won't work
		prop.put("pathToDot", "/usr/local/bin/dot"); 
		prop.put("maxTypesDrawnPerNameSpace", "20"); 
	}
	
	public void put(String name, String value){
		prop.put(name, value); 
	}

}
