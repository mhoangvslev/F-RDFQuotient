//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.export;

import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.log4j.Logger;

public class EntitySummaryNode {
	final long node;
	final long ownCardinality;
	final String hiddenDotName;

	final SummaryExport exporter;

	HashMap<Long, Long> actualTypes; // for each actual type of a resource represented by this entity, how many times that happened
	final HashSet<Long> groupByTypes; // each of the top types corresponding to this node's class set (or actual types)

	// properties in sorted order
    final TreeMap<String, Long> outgoingPropertiesMap;
	final TreeMap<String, Long> leafChildrenMap;
	final TreeMap<String, Long> propCardinalitiesMap;
	final TreeMap<String, Long> childCardinalitiesMap;
	TreeMap<String, Long> typesMap;

	final TreeSet<String> genericProperties;

	final ArrayList<String> fullTypes; // for each type, how many subjects of this entity have this type
	final int maxTypesDrawnPerNameSpace;

	private static final Logger LOGGER = Logger.getLogger(EntitySummaryNode.class.getName());

	public EntitySummaryNode(long node, long ownCardinality, String hiddenDotName, SummaryExport exporter) {
		//LOGGER.info("Created ESN " + hiddenDotName + " for " + node + " (" + ownCardinality + ")");
		this.exporter = exporter;
		this.node = node;
		this.ownCardinality = ownCardinality;
		this.hiddenDotName = hiddenDotName;
		outgoingPropertiesMap = new TreeMap<>();
		leafChildrenMap = new TreeMap<>();
		propCardinalitiesMap = new TreeMap<>();
		childCardinalitiesMap = new TreeMap<>();
		genericProperties = new TreeSet<>();
		actualTypes = new HashMap<>();
		groupByTypes = new HashSet<>();
		fullTypes = new ArrayList<>();
		maxTypesDrawnPerNameSpace = exporter.summary.getMaxTypesDisplayedPerNameSpace();
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
		actualTypes.put(newType, typeCardinality);
	}

	public void addNodeDescriptionTo(BufferedWriter bw, DOTAuxiliary dax) {
		try {
			String nColor = dax.getSummaryNodeColor(node);
			String fontColor = (dax.isDarkColor(nColor)?"white":"black");
			bw.write("\"" + hiddenDotName + "\" [ label=< <TABLE BGCOLOR=\"" + nColor + "\"> <TR><TD><FONT color=\"" + fontColor  +
					"\" POINT-SIZE=\"12.0\" FACE=\"Times-Bold\"> " + hiddenDotName);
			addTypeDescriptionTo(bw, fontColor);
			bw.write(" </FONT> </TD> </TR>");
			for (String propertyInDot: outgoingPropertiesMap.keySet()) {
				boolean genericProperty = genericProperties.contains(propertyInDot);
				bw.write(" <TR><TD><FONT color=\"" + fontColor + "\" " +
						(genericProperty?" FACE=\"Times-Italic\"":"") +
						" POINT-SIZE=\"12.0\"> " + 	propertyInDot +
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

	/**
	 * This:
	 * - takes the actualTypes and makes them groupByTypes
	 * - sets the groupByTypes to the newly given map typeToCard.
	 * This assumes that before it's called, actualTypes has been filled in with all the type triples from the summary.
	 * When we group by generalized class sets, the type triples in the summary are not the correct ones, instead they go to the generalized class set types.
	 * Thus, this call opportunistically recycles the "previous actual types" (which were not actual types at all; instead, they were GCS types)
	 * into the group by types that they really are.
	 * @param typeToCard A type-to-cardinality map.
	 */
	void setActualTypes(HashMap<Long, Long> typeToCard) {
		this.groupByTypes.addAll(this.actualTypes.keySet());
		this.actualTypes = typeToCard;
		if (this.actualTypes == null) {
			this.actualTypes = new HashMap<>();
			//LOGGER.info("Currently no actual types");
		}
		//else {
			//LOGGER.info("Now " + actualTypes.size() + " actual types");
		//}
	}
	// if a node has very many actual types, show at most five, then write "... X more types from this namespace"
	void addTypeDescriptionTo(BufferedWriter bw, String fontColor) {
		try {
			// start by writing the group-by types: these should be non-empty iff
			// the summarization has generalized the class set
			if (groupByTypes.size() > 0) {
				for (long groupByType: groupByTypes) {
					String s = (RDF2SQLEncoding.dictionaryDecode(groupByType)).replaceAll(">", "").replaceAll("<", "");
					bw.write("<BR/>" + s);
					//LOGGER.info("PRINTING ENTITY OF TYPE " + s);
				}
				bw.write("</FONT></TD></TR><TR><TD><FONT color=\"" + fontColor + "\" POINT-SIZE=\"12.0\">");
			}
			for (long nodeType: actualTypes.keySet()) {
				String s = (RDF2SQLEncoding.dictionaryDecode(nodeType)).replaceAll(">", "").replaceAll("<", "");
				fullTypes.add(s + ": " + actualTypes.get(nodeType));
			}
			//LOGGER.info(actualTypes.size() + " actual types, " + fullTypes.size() + " full types");
			String prevNameSpace = "";
			String crtNameSpace = "";
			int lastIndexOfSlash;
			int ommittedFromCrtNameSpace = 0;
			int typesInCurrentNameSpace = 0;
			boolean firstType = true;
			for (String fullType: fullTypes) {
				lastIndexOfSlash = fullType.lastIndexOf('/');
				if (lastIndexOfSlash == -1) {
					crtNameSpace = fullType;
				}
				else {
					crtNameSpace = fullType.substring(0, lastIndexOfSlash);
				}

				if (!prevNameSpace.equals(crtNameSpace)) {
					// we just entered in this namespace; acknowledge ommissions if any before moving to new namespace:
					if (ommittedFromCrtNameSpace > 0) {
						bw.write("<BR/>..." + ommittedFromCrtNameSpace + " more type" +
								((ommittedFromCrtNameSpace > 1)?"s":"") + " from " + crtNameSpace);
					}
					// now reset the counter
					typesInCurrentNameSpace = 1;
					ommittedFromCrtNameSpace = 0;
				}
				else {
					typesInCurrentNameSpace ++;
				}
				if (typesInCurrentNameSpace < this.maxTypesDrawnPerNameSpace) {
					if (firstType) {
						if (groupByTypes.isEmpty()) {
							// if there are no group-by types, this is the first type
							// after the node name
							bw.write("<BR/>");
						}
						firstType = false;
					}
					else {
						bw.write("<BR/>");
					}
					bw.write(fullType);
				}
				else { // we had to cut the tail
					ommittedFromCrtNameSpace ++;
				}
				prevNameSpace = crtNameSpace;
			}
			//if there were some ommissions in the last traversed namespace, we need to acknowledge them here:
			if (ommittedFromCrtNameSpace > 0) {
				bw.write("<BR/>..." + ommittedFromCrtNameSpace + " more type" +
						((ommittedFromCrtNameSpace > 1)?"s":"") + " from " + crtNameSpace);
			}
		}
		catch(IOException ioe) {
			throw new IllegalStateException(ioe.toString());
		}
	}

}
