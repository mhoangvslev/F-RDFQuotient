package fr.inria.cedar.quotientSummary.util;

import java.util.HashMap;
import java.util.TreeSet;

public class DOTAuxiliary{
	public static String[] svgColorNames = {"antiquewhite1", "aquamarine1", 
			"cornflowerblue", "gold", "tomato", "chartreuse", "cadetblue1",
			"antiqueblue3", "blueviolet", "lightpink", "magenta",
			"yellow", "plum", "wheat", "mediumpurple1", "coral",
			"lightgoldenrod", "orange", "khaki1", "orangered", "navy",
			"magenta",};

	// color index for each summary node (may cycle if there are more summary nodes than colors)
	public  HashMap<Long, Integer> coloredSummaryNodes;
	
	// whether or not the RDF node has already been colored. We do not store colors for them
	// as we will use the colors from the representative node.
	public  TreeSet<Long> coloredRDFNodes; 

	public  int nextSummaryColorToGive; 
	
	public DOTAuxiliary() {
		coloredSummaryNodes = new HashMap<Long, Integer>();
		coloredRDFNodes = new TreeSet<Long>();
		nextSummaryColorToGive = -1; 
	}
	
	public  String getSummaryNodeColor(long summaryNodeCode) {
		Integer colorForThisNode = coloredSummaryNodes.get(summaryNodeCode);
		if (colorForThisNode == null) {
			nextSummaryColorToGive++;
			if (nextSummaryColorToGive == svgColorNames.length) {
				nextSummaryColorToGive = 0; 
			}
			coloredSummaryNodes.put(new Long(summaryNodeCode), nextSummaryColorToGive); 
			//Debugger.log("-<-<-<-<-<-< Assigned " + svgColorNames[nextSummaryColorToGive] + " for " + summaryNodeCode);
			return svgColorNames[nextSummaryColorToGive];
		}
		else {
			//Debugger.log("-<-<-<-<-<-< Retrieved " + svgColorNames[colorForThisNode] + " for " + summaryNodeCode);
			return svgColorNames[colorForThisNode]; 
		}
	}

	public  boolean unknownSummaryNode(long summaryNodeCode) {
		boolean b = !(coloredSummaryNodes.keySet().contains(summaryNodeCode)); 
		////Debugger.log("-<-<-<-<-<-< " + summaryNodeCode + " unknown: " + b);
		return b;
	}

	public boolean unknownRDFNode(long RDFNodeCode) {
		boolean b = !(coloredRDFNodes.contains(RDFNodeCode)); 
		//Debugger.log("-o-o-o-o-o-o- " + RDFNodeCode + " unknown: " + b);
		coloredRDFNodes.add(RDFNodeCode);
		return b;
	}

}