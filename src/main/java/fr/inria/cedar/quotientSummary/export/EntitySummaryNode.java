package fr.inria.cedar.quotientSummary.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

public class EntitySummaryNode {
	long node;
	long ownCardinality;
	String hiddenDotName; 
	
	SummaryExport exporter; 
	
	ArrayList<Long> outgoingProperties;
	ArrayList<Long> leafChildren; 
	ArrayList<Long> propCardinalities;
	ArrayList<Long> childCardinalities;
	ArrayList<Long> types; 
	private static final Logger LOGGER = Logger.getLogger(EntitySummaryNode.class.getName());
	
	public EntitySummaryNode(long node, long ownCardinality, String hiddenDotName, SummaryExport exporter) {
		//LOGGER.info("Created ESN " + hiddenDotName + " for " + node + " (" + ownCardinality + ")"); 
		this.exporter = exporter; 
		this.node = node;
		this.ownCardinality = ownCardinality;
		this.hiddenDotName = hiddenDotName; 
		outgoingProperties = new ArrayList<Long>();
		leafChildren = new ArrayList<Long>();
		propCardinalities = new ArrayList<Long>();
		childCardinalities = new ArrayList<Long>();	
		types = new ArrayList<Long>(); 
	}
	
	public void addLeafChild(long prop, long leafChild, long propCard, long childCard) {
		//LOGGER.info("Adding to ESN " + node + " child " + leafChild + " (" + childCard + ") on property " + prop + " (" + propCard + ")"); 
		this.outgoingProperties.add(prop);
		this.leafChildren.add(leafChild);
		this.propCardinalities.add(propCard);
		this.childCardinalities.add(childCard); 
	}
	
	public void addType(long newType) {
		types.add(newType); 
	}

	public void addNodeDescriptionTo(BufferedWriter bw, DOTAuxiliary dax) {
		try {
			String nColor = dax.getSummaryNodeColor(node);
			String fontColor = (dax.isDarkColor(nColor)?"white":"black"); 
			bw.write("\"" + hiddenDotName + "\" [ label=< <TABLE BGCOLOR=\"" + nColor + "\"> <TR><TD><FONT color=\"" + fontColor  +
					"\" POINT-SIZE=\"24.0\" > " + hiddenDotName);
			for (long nodeType: types) {
				bw.write("<BR/>" + RDF2SQLEncoding.dictionaryDecode(nodeType).replaceAll(">", "").replaceAll("<", ""));
			}
			bw.write(" </FONT> </TD> </TR>");
			for (int i = 0; i < outgoingProperties.size(); i ++) {
				String propName =  RDF2SQLEncoding.dictionaryDecode(outgoingProperties.get(i));
				String propertyInDot = exporter.getVeryShortForDot(propName); 
				bw.write(" <TR><TD><FONT color=\"" + fontColor + "\" POINT-SIZE=\"14.0\"> " + propertyInDot + " (" + propCardinalities.get(i) + " &rarr; " + childCardinalities.get(i) + ") </FONT></TD></TR> ");
			}
			bw.write("</TABLE>> ]\n");
		}
		catch(IOException ioe) {
			throw new IllegalStateException(ioe.toString());
		}
	}

}
