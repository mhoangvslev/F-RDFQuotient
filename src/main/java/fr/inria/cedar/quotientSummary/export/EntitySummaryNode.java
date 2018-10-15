package fr.inria.cedar.quotientSummary.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

public class EntitySummaryNode {
	long node;
	long ownCardinality;
	String hiddenDotName; 
	
	SummaryExport exporter; 
	
	HashMap<Long, Long> types; 
	
	// properties in sorted order
	TreeMap<String, Long> outgoingPropertiesMap;
	TreeMap<String, Long> leafChildrenMap;
	TreeMap<String, Long> propCardinalitiesMap; 
	TreeMap<String, Long> childCardinalitiesMap;
	TreeMap<String, Long> typesMap; 
	
	TreeSet<String> genericProperties;
	
	ArrayList<String> fullTypes; // for each type, how many subjects of this entity have this type
	int maxTypesDisplayedPerNameSpace = 5; 
			
	private static final Logger LOGGER = Logger.getLogger(EntitySummaryNode.class.getName());
	
	public EntitySummaryNode(long node, long ownCardinality, String hiddenDotName, SummaryExport exporter) {
		//LOGGER.info("Created ESN " + hiddenDotName + " for " + node + " (" + ownCardinality + ")"); 
		this.exporter = exporter; 
		this.node = node;
		this.ownCardinality = ownCardinality;
		this.hiddenDotName = hiddenDotName; 
		outgoingPropertiesMap = new TreeMap<String, Long>();
		leafChildrenMap = new TreeMap<String, Long>();
		propCardinalitiesMap = new TreeMap<String, Long>();
		childCardinalitiesMap = new TreeMap<String, Long>();	
		genericProperties = new TreeSet<String>(); 
		types = new HashMap<Long, Long>(); 
		fullTypes = new ArrayList<String>(); 
	}
	
	public void addLeafChild(long prop, long leafChild, long propCard, long childCard) {
		//LOGGER.info("Adding to ESN " + node + " child " + leafChild + " (" + childCard + 
		//		") on property " + prop + " " + RDF2SQLEncoding.dictionaryDecode(prop) + 
		//		" (" + propCard + ")"); 
	
		String propName =  RDF2SQLEncoding.dictionaryDecode(prop);
		String propertyInDot = exporter.getVeryShortForDot(propName); 
	
		this.outgoingPropertiesMap.put(propertyInDot, prop); 
		this.leafChildrenMap.put(propertyInDot, leafChild);
		this.propCardinalitiesMap.put(propertyInDot, propCard);
		this.childCardinalitiesMap.put(propertyInDot, childCard); 
		if (exporter.summary.isGeneric(prop)) {
			genericProperties.add(propertyInDot); 
		}
	}
	
	public void addType(long newType, long typeCardinality) {
		types.put(newType, typeCardinality); 
	}

	public void addNodeDescriptionTo(BufferedWriter bw, DOTAuxiliary dax) {
		try {
			String nColor = dax.getSummaryNodeColor(node);
			String fontColor = (dax.isDarkColor(nColor)?"white":"black"); 
			bw.write("\"" + hiddenDotName + "\" [ label=< <TABLE BGCOLOR=\"" + nColor + "\"> <TR><TD><FONT color=\"" + fontColor  +
					"\" POINT-SIZE=\"24.0\" > " + hiddenDotName);
			addTypeDescriptionTo(bw); 
			bw.write(" </FONT> </TD> </TR>");
			for (String propertyInDot: outgoingPropertiesMap.keySet()) {
				boolean genericProperty = genericProperties.contains(propertyInDot); 
				bw.write(" <TR><TD><FONT color=\"" + fontColor + "\" " +
						(genericProperty?" FACE=\"Times-Italic\"":"") + 
						" POINT-SIZE=\"14.0\"> " + 	propertyInDot +
						( (propCardinalitiesMap.get(propertyInDot) >=0)?(" (" + propCardinalitiesMap.get(propertyInDot) + 
								" &rarr; " + childCardinalitiesMap.get(propertyInDot) + ") "):"") +
						"</FONT></TD></TR>\n");
			}
			bw.write("</TABLE>> ]\n");
		}
		catch(IOException ioe) {
			throw new IllegalStateException(ioe.toString());
		}
	}
	// if a node has very many types, show at most five, then write "... X more types from this namespace"
	void addTypeDescriptionTo(BufferedWriter bw) {
		try {
			for (long nodeType: types.keySet()) {
				String s = (RDF2SQLEncoding.dictionaryDecode(nodeType)).replaceAll(">", "").replaceAll("<", "");
				fullTypes.add(s + ": " + types.get(nodeType)); 
			}
			String prevNameSpace = "";
			String crtNameSpace = "";
			int ommittedFromCrtNameSpace = 0; 
			int typesInCurrentNameSpace = 0; 
			for (String fullType: fullTypes) {
				crtNameSpace = fullType.substring(0, fullType.lastIndexOf('/'));
				if (!prevNameSpace.equals(crtNameSpace)) {
					// we just entered in this namespace; let's first finish with the previous one: 
					if (ommittedFromCrtNameSpace > 0) {
						bw.write("<BR/>..." + ommittedFromCrtNameSpace + " more type" +
								((ommittedFromCrtNameSpace > 1)?"s":"") + " from " + prevNameSpace);
					}
					// now reset the counter
					typesInCurrentNameSpace = 1; 
					ommittedFromCrtNameSpace = 0; 
				}
				else {
					typesInCurrentNameSpace ++; 
				}
				if (typesInCurrentNameSpace < this.maxTypesDisplayedPerNameSpace) {					
					bw.write("<BR/>" + fullType);
				}
				else { // we had to cut the tail
					ommittedFromCrtNameSpace ++; 
				}
				prevNameSpace = crtNameSpace; 
			}
		}
		catch(IOException ioe) {
			throw new IllegalStateException(ioe.toString());
		}
	}

}
