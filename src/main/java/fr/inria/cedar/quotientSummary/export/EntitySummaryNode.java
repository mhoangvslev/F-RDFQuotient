package fr.inria.cedar.quotientSummary.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;

public class EntitySummaryNode {
	long node;
	long ownCardinality;
	String hiddenDotName; 
	
	ArrayList<Long> outgoingProperties;
	ArrayList<Long> leafChildren; 
	ArrayList<Long> propCardinalities;
	ArrayList<Long> childCardinalities;
	ArrayList<Long> types; 
		
	public EntitySummaryNode(long node, long ownCardinality, String hiddenDotName) {
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
			String fontColor = (dax.isDarkColor(nColor)?", fontcolor=white ":"black"); 
			bw.write(hiddenDotName + " [ label=< <TABLE BGCOLOR=\"" + nColor + "\"> <TR><TD><FONT color=\"" + fontColor  +
					"\" POINT-SIZE=\"24.0\" > " + hiddenDotName + " (" + ownCardinality + ") </FONT> </TD> </TR>");
			for (int i = 0; i < outgoingProperties.size(); i ++) {
				// TODO
			}
			bw.write("</TABLE>> ]\n");
		}
		catch(IOException ioe) {
			throw new IllegalStateException(ioe.toString());
		}
	}

}
