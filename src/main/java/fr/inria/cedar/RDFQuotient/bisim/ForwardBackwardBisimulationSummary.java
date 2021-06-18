//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.bisim;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class ForwardBackwardBisimulationSummary extends Summary {
	private static final Logger LOGGER = Logger.getLogger(ForwardBackwardBisimulationSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	protected class NodeSignature {
		protected HashSet<Long> outgoingNonTypeProperties;
		protected HashSet<Long> incomingNonTypeProperties;
		protected HashSet<Long> typeProperties; // only outgoing properties

		public NodeSignature(HashSet<Long> outgoingNonTypeProperties, HashSet<Long> incomingNonTypeProperties, HashSet<Long> typeProperties) {
			this.outgoingNonTypeProperties = outgoingNonTypeProperties;
			this.incomingNonTypeProperties = incomingNonTypeProperties;
			this.typeProperties = typeProperties;
		}

		public HashSet<Long> getOutgoingNonTypeProperties() {
			return outgoingNonTypeProperties;
		}

		public HashSet<Long> getInComingNonTypeProperties() {
			return incomingNonTypeProperties;
		}

		public HashSet<Long> getTypeProperties() {
			return typeProperties;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}

			if (obj == null) {
				return false;
			}

			if (!(obj instanceof NodeSignature)) {
				return false;
			}

			final NodeSignature o = (NodeSignature) obj;

			return Objects.equals(this.outgoingNonTypeProperties, o.outgoingNonTypeProperties)
				&& Objects.equals(this.incomingNonTypeProperties, o.incomingNonTypeProperties)
				&& Objects.equals(this.typeProperties, o.typeProperties);
		}

		@Override
		public int hashCode() {
			int hash = 3;
			hash = 59 * hash + Objects.hashCode(this.outgoingNonTypeProperties);
			hash = 59 * hash + Objects.hashCode(this.incomingNonTypeProperties);
			hash = 59 * hash + Objects.hashCode(this.typeProperties);

			return hash;
		}
	}

	protected class EquivalenceClass {
		protected HashSet<Long> inputGraphNodesRepresented;

		public EquivalenceClass(HashSet<Long> nodes) {
			inputGraphNodesRepresented = nodes;
		}

		public HashSet<Long> getNodes() {
			return inputGraphNodesRepresented;
		}

		public int getNumberOfNodesRepresented() {
			return inputGraphNodesRepresented.size();
		}

		// node -> property -> set of nodes
		public HashMap<Long, HashMap<Long, HashSet<Long>>> getNextHopNodesThroughOutgoingNonTypeProperties() {
			HashMap<Long, HashMap<Long, HashSet<Long>>> result = new HashMap<>();
			for (Long node: inputGraphNodesRepresented) {
				if (nodeToNextHopNodesByOutgoingNonSchemaProperty.containsKey(node)) {
					if (!result.containsKey(node)) {
						result.put(node, new HashMap<>());
					}
					for (Long property: nodeToNextHopNodesByOutgoingNonSchemaProperty.get(node).keySet()) {
						result.get(node).put(property, nodeToNextHopNodesByOutgoingNonSchemaProperty.get(node).get(property));
					}
				}
			}

			return result;
		}

		// node -> property -> set of nodes
		public HashMap<Long, HashMap<Long, HashSet<Long>>> getPreviousHopNodesThroughIncomingNonTypeProperties() {
			HashMap<Long, HashMap<Long, HashSet<Long>>> result = new HashMap<>();
			for (Long node: inputGraphNodesRepresented) {
				if (nodeToPreviousHopNodesByIncomingNonSchemaProperties.containsKey(node)) {
					if (!result.containsKey(node)) {
						result.put(node, new HashMap<>());
					}
					for (Long property: nodeToPreviousHopNodesByIncomingNonSchemaProperties.get(node).keySet()) {
						result.get(node).put(property, nodeToPreviousHopNodesByIncomingNonSchemaProperties.get(node).get(property));
					}
				}
			}

			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof EquivalenceClass)) {
				return false;
			}

			final EquivalenceClass other = (EquivalenceClass) obj;

			return Objects.equals(this.inputGraphNodesRepresented, other.inputGraphNodesRepresented);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(inputGraphNodesRepresented);
		}
	}

	protected class NeighborhoodEquivalencePattern {
		protected Boolean direction; // forward: true, backward: false
		protected Long property;
		protected HashSet<EquivalenceClass> setOfEquivalenceClasses;

		public NeighborhoodEquivalencePattern(Boolean direction, Long property, HashSet<EquivalenceClass> setOfEquivalenceClasses) {
			this.direction = direction;
			this.property = property;
			this.setOfEquivalenceClasses = setOfEquivalenceClasses;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this) {
				return true;
			}

			if (!(obj instanceof NeighborhoodEquivalencePattern)) {
				return false;
			}

			final NeighborhoodEquivalencePattern o = (NeighborhoodEquivalencePattern) obj;

			return Objects.equals(this.direction, o.direction)
				&& Objects.equals(this.property, o.property)
				&& Objects.equals(this.setOfEquivalenceClasses, o.setOfEquivalenceClasses);
		}

		@Override
		public int hashCode() {
			int hash = 3;
			hash = 59 * hash + Objects.hashCode(this.direction);
			hash = 59 * hash + Objects.hashCode(this.property);
			hash = 59 * hash + Objects.hashCode(this.property);

			return hash;
		}
	}

	protected long typeConstantCode;
	protected HashSet<Long> allNonSchemaNodes;
	protected HashMap<Long, HashMap<Long, HashSet<Long>>> nodeToNextHopNodesByOutgoingNonSchemaProperty;
	protected HashMap<Long, HashMap<Long, HashSet<Long>>> nodeToPreviousHopNodesByIncomingNonSchemaProperties;
	protected HashMap<Long, HashSet<Long>> nodeToSetOfTypes;
	protected HashMap<NodeSignature, HashSet<Long>> nodeSignatureToNodes;
	protected ArrayList<EquivalenceClass> equivalenceClasses;
	protected HashMap<Long, EquivalenceClass> nodeToEquivalenceClass;
	protected HashMap<Long, Long> nodeToEquivalenceClassID;

	public ForwardBackwardBisimulationSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_FORWARD_BACKWARD_BISIMULATION;
		this.isTypeFirst = false;
		this.isDataAndType = true;
		this.isTwoPass = true;
		typeConstantCode = RDF2SQLEncoding.getTypeCode();
		allNonSchemaNodes = new HashSet<>();
		nodeToNextHopNodesByOutgoingNonSchemaProperty = new HashMap<>();
		nodeToPreviousHopNodesByIncomingNonSchemaProperties = new HashMap<>();
		nodeToSetOfTypes = new HashMap<>();
		nodeSignatureToNodes = new HashMap<>();
		equivalenceClasses = new ArrayList<>();
		nodeToEquivalenceClass = new HashMap<>();
	}

	@Override
	protected void representTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void classifyDataOrTypeTriple(Triple t) {
		if (!nodeToNextHopNodesByOutgoingNonSchemaProperty.containsKey(t.s)) {
			nodeToNextHopNodesByOutgoingNonSchemaProperty.put(t.s, new HashMap<>());
		}
		if (!nodeToNextHopNodesByOutgoingNonSchemaProperty.get(t.s).containsKey(t.p)) {
			nodeToNextHopNodesByOutgoingNonSchemaProperty.get(t.s).put(t.p, new HashSet<>());
		}
		if (!nodeToPreviousHopNodesByIncomingNonSchemaProperties.containsKey(t.o)) {
			nodeToPreviousHopNodesByIncomingNonSchemaProperties.put(t.o, new HashMap<>());
		}
		if (!nodeToPreviousHopNodesByIncomingNonSchemaProperties.get(t.o).containsKey(t.p)) {
			nodeToPreviousHopNodesByIncomingNonSchemaProperties.get(t.o).put(t.p, new HashSet<>());
		}
		if (!nodeToSetOfTypes.containsKey(t.s)) {
			nodeToSetOfTypes.put(t.s, new HashSet<>());
		}

		if (!sn.contains(t.s)) {
			allNonSchemaNodes.add(t.s);
		}
		if (t.p == typeConstantCode) {
			nodeToSetOfTypes.get(t.s).add(t.o);
		}
		else {
			nodeToNextHopNodesByOutgoingNonSchemaProperty.get(t.s).get(t.p).add(t.o);
			nodeToPreviousHopNodesByIncomingNonSchemaProperties.get(t.o).get(t.p).add(t.s);
			allNonSchemaNodes.add(t.o);
		}
	}

	protected void constructNodeSignatureToNodesMapping() {
		HashSet<Long> outgoingNonTypeProperties;
		HashSet<Long> incomingNonTypeProperties;
		HashSet<Long> typeProperties;
		for (Long node: allNonSchemaNodes) {
			outgoingNonTypeProperties = new HashSet<>();
			if (nodeToNextHopNodesByOutgoingNonSchemaProperty.containsKey(node)) {
				outgoingNonTypeProperties.addAll(nodeToNextHopNodesByOutgoingNonSchemaProperty.get(node).keySet());
			}

			incomingNonTypeProperties = new HashSet<>();
			if (nodeToPreviousHopNodesByIncomingNonSchemaProperties.containsKey(node)) {
				incomingNonTypeProperties.addAll(nodeToPreviousHopNodesByIncomingNonSchemaProperties.get(node).keySet());
			}

			typeProperties = nodeToSetOfTypes.get(node);

			NodeSignature signature = new NodeSignature(outgoingNonTypeProperties, incomingNonTypeProperties, typeProperties);
			if (!nodeSignatureToNodes.containsKey(signature)) {
				nodeSignatureToNodes.put(signature, new HashSet<>());
			}
			nodeSignatureToNodes.get(signature).add(node);
		}
	}

	protected HashMap<Long, HashMap<Long, HashSet<EquivalenceClass>>> findEquivalenceClassSetsByProperty(HashMap<Long, HashMap<Long, HashSet<Long>>> nodesInNextOutgoingHopByProperty) {
		HashMap<Long, HashMap<Long, HashSet<EquivalenceClass>>> result = new HashMap<>();

		for (Long node: nodesInNextOutgoingHopByProperty.keySet()) {
			if (!result.containsKey(node)) {
				result.put(node, new HashMap<>());
			}
			for (Long property: nodesInNextOutgoingHopByProperty.get(node).keySet()) {
				if (!result.get(node).containsKey(property)) {
					result.get(node).put(property, new HashSet<>());
				}
				for (Long objectNode: nodesInNextOutgoingHopByProperty.get(node).get(property)) {
					result.get(node).get(property).add(nodeToEquivalenceClass.get(objectNode));
				}
			}
		}

		return result;
	}

	protected HashSet<EquivalenceClass> splitEquivalenceClass(
		HashSet<Long> nodes,
		HashMap<Long, HashMap<Long, HashSet<EquivalenceClass>>> outgoingNodeClasses,
		HashMap<Long, HashMap<Long, HashSet<EquivalenceClass>>> incomingNodeClasses
	) {
		// key: HashSet<Triple<Long, HashSet<EquivalenceClass>>>
		// set of combinations (direction (of the property), property, set of equivalence classes of the nodes reached through property)
		// value: HashSet<Long>
		// set of nodes with the same key
		HashMap<HashSet<NeighborhoodEquivalencePattern>, HashSet<Long>> setOfNodeCharacteristicsToSetOfNodes = new HashMap<>();

		HashSet<NeighborhoodEquivalencePattern> setOfNeighborhoodPatterns;
		for (Long node: nodes) {
			setOfNeighborhoodPatterns = new HashSet<>();

			// gather neighborhood patterns
			if (outgoingNodeClasses.containsKey(node)) {
				for (Long property: outgoingNodeClasses.get(node).keySet()) {
					setOfNeighborhoodPatterns.add(new NeighborhoodEquivalencePattern(Boolean.TRUE, property, outgoingNodeClasses.get(node).get(property)));
				}
			}
			if (incomingNodeClasses.containsKey(node)) {
				for (Long property: incomingNodeClasses.get(node).keySet()) {
					setOfNeighborhoodPatterns.add(new NeighborhoodEquivalencePattern(Boolean.FALSE, property, incomingNodeClasses.get(node).get(property)));
				}
			}

			// add the node to its group
			if (!setOfNodeCharacteristicsToSetOfNodes.containsKey(setOfNeighborhoodPatterns)) {
				setOfNodeCharacteristicsToSetOfNodes.put(setOfNeighborhoodPatterns, new HashSet<>());
			}
			setOfNodeCharacteristicsToSetOfNodes.get(setOfNeighborhoodPatterns).add(node);
		}

		HashSet<EquivalenceClass> result = new HashSet<>();
		EquivalenceClass equivalenceClass;
		HashSet<Long> setOfNodesInEquivalenceClass;
		for (HashSet<NeighborhoodEquivalencePattern> setOfNodeCharacteristics: setOfNodeCharacteristicsToSetOfNodes.keySet()) {
			setOfNodesInEquivalenceClass = setOfNodeCharacteristicsToSetOfNodes.get(setOfNodeCharacteristics);
			equivalenceClass = new EquivalenceClass(setOfNodesInEquivalenceClass);
			result.add(equivalenceClass);
			for (Long node: setOfNodesInEquivalenceClass) {
				nodeToEquivalenceClass.put(node, equivalenceClass);
			}
		}

		return result;
	}

	protected void findFixpointOfEquivalenceClasses() {
		boolean splitClass;
		ArrayList<EquivalenceClass> equivalenceClassesQueue;
		HashSet<Long> equivalenceClassNodes;
		HashMap<Long, HashMap<Long, HashSet<Long>>> nodesInNextHopByProperty;
		HashMap<Long, HashMap<Long, HashSet<Long>>> nodesInPreviousHopByProperty;
		HashMap<Long, HashMap<Long, HashSet<EquivalenceClass>>> equivalenceClassSetsInNextHopByProperty;
		HashMap<Long, HashMap<Long, HashSet<EquivalenceClass>>> equivalenceClassSetsInPreviousHopByProperty;
		do {
			splitClass = false;
			equivalenceClassesQueue = new ArrayList<>();
			for (EquivalenceClass equivalenceClass: equivalenceClasses) {
				equivalenceClassNodes = equivalenceClass.getNodes();

				// outgoing non-type properties
				nodesInNextHopByProperty = equivalenceClass.getNextHopNodesThroughOutgoingNonTypeProperties();
				// incoming non-type properties
				nodesInPreviousHopByProperty = equivalenceClass.getPreviousHopNodesThroughIncomingNonTypeProperties();

				// identify equivalence class sets among the nodes in nodesInNextOutgoingHopByProperty
				equivalenceClassSetsInNextHopByProperty = findEquivalenceClassSetsByProperty(nodesInNextHopByProperty);
				// identify equivalence class sets among the nodes in nodesInNextIncomingHopByProperty
				equivalenceClassSetsInPreviousHopByProperty = findEquivalenceClassSetsByProperty(nodesInPreviousHopByProperty);

				HashSet<EquivalenceClass> newEquivalenceClasses = splitEquivalenceClass(
					equivalenceClassNodes,
					equivalenceClassSetsInNextHopByProperty,
					equivalenceClassSetsInPreviousHopByProperty
				);
				splitClass = splitClass || (newEquivalenceClasses.size() > 1);

				// split this equivalence class
				equivalenceClassesQueue.addAll(newEquivalenceClasses);
			}
			equivalenceClasses = equivalenceClassesQueue;
		}
		while (splitClass);
	}

	protected HashMap<Long, Long> findNodeToEquivalenceClassIDMapping() {
		HashMap<EquivalenceClass, Long> equivalenceClassToID = new HashMap<>();
		HashMap<Long, Long> nodeToEquivalenceClassIDMapping = new HashMap<>();

		for (EquivalenceClass equivalenceClass: equivalenceClasses) {
			if (!equivalenceClassToID.containsKey(equivalenceClass)) {
				equivalenceClassToID.put(equivalenceClass, getNextSummaryNode());
			}
		}

		Long equivalenceClassID;
		for (Long node: nodeToEquivalenceClass.keySet()) {
			equivalenceClassID = equivalenceClassToID.get(nodeToEquivalenceClass.get(node));
			nodeToEquivalenceClassIDMapping.put(node, equivalenceClassID);
		}

		return nodeToEquivalenceClassIDMapping;
	}

	@Override
	protected void classificationPostProcessing() {
		// 1-fb, initial step
		constructNodeSignatureToNodesMapping();

		EquivalenceClass ec;
		for (NodeSignature nodeSignature: nodeSignatureToNodes.keySet()) {
			ec = new EquivalenceClass(nodeSignatureToNodes.get(nodeSignature));
			equivalenceClasses.add(ec);
			for (Long node: ec.getNodes()) {
				nodeToEquivalenceClass.put(node, ec);
			}
		}

		findFixpointOfEquivalenceClasses();

		nodeToEquivalenceClassID = findNodeToEquivalenceClassIDMapping();
	}

	@Override
	protected void representDataOrTypeTriple(Triple t) {
		// schema nodes already represented in collectSchemaNodes
		if (!sn.contains(t.s)) {
			rep.put(t.s, nodeToEquivalenceClassID.get(t.s));
		}
		if (!sn.contains(t.o)) {
			rep.put(t.o, nodeToEquivalenceClassID.get(t.o));
		}
	}
}
