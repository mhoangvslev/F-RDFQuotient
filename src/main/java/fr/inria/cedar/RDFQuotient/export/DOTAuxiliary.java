//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.export;

import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class DOTAuxiliary {
	private static final Logger LOGGER = Logger.getLogger(DOTAuxiliary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	public static String[] diverseColorNames = {"antiquewhite1", "aquamarine1",
											"cornflowerblue", "gold", "tomato", "chartreuse", "cadetblue1",
											"blueviolet", "lightpink", "magenta", "yellow",
											"plum", "wheat", "mediumpurple1", "coral",
											"lightgoldenrod", "orange", "khaki1", "orangered", "navy",
											"lightpink", "magenta", "cyan", "firebrick"};
	public static String[] bwColorNames = {"white"};
	public static String[] ivoryColorNames = {"ivory"};
	public static String[] lightColorNames = {"white", "lightcyan", "ivory", "azure", "lemonchiffon", "mistyrose1",
											   "lavender", "beige", "aliceblue", "greenyellow",
											  "thistle", "paleturquoise", "pink", "yellow"};
	public static TreeSet<String> darkColorNames;
	public static String[] colorNames = diverseColorNames;

	// color index for each summary node (may cycle if there are more
	// summary nodes than colors)
	public HashMap<Long, Integer> coloredSummaryNodes;

	// whether or not the RDF node has already been colored.
	// We do not store colors for them as we will use the colors
	// of their from the representative node.
	public TreeSet<Long> coloredRDFNodes;
	public int nextSummaryColorToGive;

	public HashSet<Long> schemaNodes;

	public DOTAuxiliary(String colorScheme) {
		// diverse is the default
		if (colorScheme.toLowerCase().equals("bw")) {
			colorNames = bwColorNames;
		}
		if (colorScheme.toLowerCase().equals("ivory")) {
			colorNames = ivoryColorNames;
		}
		if (colorScheme.toLowerCase().equals("light")){
			colorNames = lightColorNames;
		}
		coloredSummaryNodes = new HashMap<>();
		coloredRDFNodes = new TreeSet<>();
		schemaNodes = new HashSet<>();
		nextSummaryColorToGive = -1;
		initDarkColors();
	}
	private void initDarkColors(){
		darkColorNames = new TreeSet<>();
		darkColorNames.add("blueviolet");
		darkColorNames.add("navy");
		darkColorNames.add("firebrick");
	}
	public boolean isDarkColor(String s){
		return darkColorNames.contains(s);
	}
	public String getSummaryNodeColor(long summaryNodeCode) {
		Integer colorForThisNode = coloredSummaryNodes.get(summaryNodeCode);
		if (colorForThisNode == null) {
			int modulo = (int) (summaryNodeCode % (colorNames.length));
			coloredSummaryNodes.put(summaryNodeCode, modulo);
			//System.out.println("-<-<-<-<-<-< Assigned " + svgColorNames[modulo] + " for " + summaryNodeCode);
			return colorNames[modulo];
		}
		else{
			//System.out.println("-<-<-<-<-<-< Retrieved " + svgColorNames[colorForThisNode] + " for " + summaryNodeCode);
			return colorNames[colorForThisNode];
		}
	}

	public boolean unknownSummaryNode(long summaryNodeCode) {
		boolean b = (coloredSummaryNodes.get(summaryNodeCode) == null);
		//System.out.println("-<-<-<-<-<-< " + summaryNodeCode + " unknown: " + b);
		return b;
	}

	public boolean unknownRDFNode(long RDFNodeCode) {
		boolean b = !(coloredRDFNodes.contains(RDFNodeCode));
		//LOGGER.debug("-o-o-o-o-o-o- " + RDFNodeCode + " unknown: " + b);
		coloredRDFNodes.add(RDFNodeCode);
		return b;
	}

	public void resetColors() {
		coloredSummaryNodes = new HashMap<>();
		coloredRDFNodes = new TreeSet<>();
		nextSummaryColorToGive = -1;
	}

	public boolean unknownSchemaNode(long s) {
		if (!schemaNodes.contains(s)){
			schemaNodes.add(s);
			return true;
		}
		else{
			return false;
		}
	}
}
