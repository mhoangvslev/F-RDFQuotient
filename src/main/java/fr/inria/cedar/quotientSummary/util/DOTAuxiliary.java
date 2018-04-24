package fr.inria.cedar.quotientSummary.util;

import java.util.HashMap;
import java.util.TreeSet;

public class DOTAuxiliary{
	public static String[] svgColorNames = {"antiquewhite1", "aquamarine1", 
			"cornflowerblue", "gold", "tomato", "chartreuse", "cadetblue1",
			"blueviolet", "lightpink", "magenta", "yellow",
			 "plum", "wheat", "mediumpurple1", "coral",
			"lightgoldenrod", "orange", "khaki1", "orangered", "navy",
			"lightpink", "magenta", "cyan", "firebrick"};

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
			int modulo = (int)(summaryNodeCode % (svgColorNames.length)); 
			coloredSummaryNodes.put(new Long(summaryNodeCode), 
					modulo); 
			//Debugger.log("-<-<-<-<-<-< Assigned " + svgColorNames[nextSummaryColorToGive] + " for " + summaryNodeCode);
			return svgColorNames[modulo];
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

	public void resetColors() {
		coloredSummaryNodes = new HashMap<Long, Integer>();
		coloredRDFNodes = new TreeSet<Long>();
		nextSummaryColorToGive = -1; 
	}

}