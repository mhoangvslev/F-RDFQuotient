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

			if (!(obj instanceof NodeSignature)) {
				return false;
			}

			NodeSignature o = (NodeSignature) obj;

			return outgoingNonTypeProperties.equals(o.outgoingNonTypeProperties)
				&& incomingNonTypeProperties.equals(o.incomingNonTypeProperties)
				&& typeProperties.equals(o.typeProperties);
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
		}

		public HashSet<Long> getNodes() {
			return inputGraphNodesRepresented;
		}

		public int getNumberOfNodesRepresented() {
			return inputGraphNodesRepresented.size();
		}

		public HashMap<Long, ArrayList<Long>> getNextHopNodesThroughOutgoingNonTypeProperties() {
			// TODO
			return null;
		}

		public HashMap<Long, ArrayList<Long>> getNextHopNodesThroughIncomingNonTypeProperties() {
			// TODO
			return null;
		}
	}

	protected long typeConstantCode;
	protected HashSet<Long> allNonSchemaNodes;
	protected HashMap<Long, HashSet<Long>> nodeToNextHopNodesByOutgoingNonSchemaProperty;
	protected HashMap<Long, HashSet<Long>> nodeToPreviousHopNodesByIncomingNonSchemaProperties;
	protected HashMap<Long, HashSet<Long>> nodeToSetOfTypes;
	protected HashMap<NodeSignature, HashSet<Long>> nodeSignatureToNodes;
	protected HashMap<Long, Long> nodeToEquivalenceClassID;

	protected void constructNodeToSignatureMapping() {
		HashSet<Long> outgoingNonTypeProperties;
		HashSet<Long> incomingNonTypeProperties;
		HashSet<Long> typeProperties;
		for (Long node: allNonSchemaNodes) {
			outgoingNonTypeProperties = nodeToNextHopNodesByOutgoingNonSchemaProperty.get(node);
			incomingNonTypeProperties = nodeToPreviousHopNodesByIncomingNonSchemaProperties.get(node);
			typeProperties = nodeToSetOfTypes.get(node);

			NodeSignature signature = new NodeSignature(outgoingNonTypeProperties, incomingNonTypeProperties, typeProperties);
			if (!nodeSignatureToNodes.containsKey(signature)) {
				nodeSignatureToNodes.put(signature, new HashSet<>());
			}
			nodeSignatureToNodes.get(signature).add(node);
		}
	}

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
	protected void classifyDataTriple(Triple t) {
		if (!nodeToNextHopNodesByOutgoingNonSchemaProperty.containsKey(t.s)) {
			nodeToNextHopNodesByOutgoingNonSchemaProperty.put(t.s, new HashSet<>());
		}
		if (!nodeToPreviousHopNodesByIncomingNonSchemaProperties.containsKey(t.o)) {
			nodeToPreviousHopNodesByIncomingNonSchemaProperties.put(t.o, new HashSet<>());
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
			nodeToNextHopNodesByOutgoingNonSchemaProperty.get(t.s).add(t.o);
			nodeToPreviousHopNodesByIncomingNonSchemaProperties.get(t.o).add(t.s);
			allNonSchemaNodes.add(t.o);
		}
	}

	ArrayList<EquivalenceClass> findFixpointOfEquivalenceClasses(ArrayList<EquivalenceClass> equivalenceClassesQueue) {
		boolean splitClass = false;
		ArrayList<EquivalenceClass> equivalenceClassesNewQueue = new ArrayList<>();
		ArrayList<EquivalenceClass> equivalenceClassesNewClasses = new ArrayList<>();
		do {
			equivalenceClassesNewQueue.clear();
			for (EquivalenceClass equivalenceClass: equivalenceClassesQueue) {
				// outgoing non-type properties
				equivalenceClassesNewClasses.clear();
				// TODO
				equivalenceClassesNewQueue.addAll(equivalenceClassesNewClasses);

				// incoming non-type properties
				equivalenceClassesNewClasses.clear();
				// TODO
				equivalenceClassesNewQueue.addAll(equivalenceClassesNewClasses);
			}
			equivalenceClassesQueue = equivalenceClassesNewQueue;
		}
		while(!splitClass);

		return equivalenceClassesQueue;
	}

	protected HashMap<Long, Long> findNodeToEquivalenceClassIDMapping(ArrayList<EquivalenceClass> equivalenceClasses) {
		HashMap<EquivalenceClass, Long> equivalenceClassToID = new HashMap<>();
		HashMap<Long, Long> nodeToEquivalenceClassMapping = new HashMap<>();

		Long equivalenceClassID;
		for (EquivalenceClass equivalenceClass: equivalenceClasses) {
			if (!equivalenceClassToID.containsKey(equivalenceClass)) {
				equivalenceClassToID.put(equivalenceClass, getNextSummaryNode());
			}
			equivalenceClassID = equivalenceClassToID.get(equivalenceClass);
			for (Long node: equivalenceClass.getNodes()) {
				nodeToEquivalenceClassMapping.put(node, equivalenceClassID);
			}
		}

		return nodeToEquivalenceClassMapping;
	}

	@Override
	protected void classificationPostProcessing() {
		constructNodeToSignatureMapping();

		ArrayList<EquivalenceClass> equivalenceClasses = new ArrayList<>();
		for (NodeSignature nodeSignature: nodeSignatureToNodes.keySet()) {
			equivalenceClasses.add(new EquivalenceClass(nodeSignatureToNodes.get(nodeSignature)));
		}

		equivalenceClasses = findFixpointOfEquivalenceClasses(equivalenceClasses);

		nodeToEquivalenceClassID = findNodeToEquivalenceClassIDMapping(equivalenceClasses);
	}

	@Override
	protected void representDataTriple(Triple t) {
		// schema nodes already represented in collectSchemaNodes
		if (!sn.contains(t.s)) {
			rep.put(t.s, nodeToEquivalenceClassID.get(t.s));
		}
		if (!sn.contains(t.o)) {
			rep.put(t.o, nodeToEquivalenceClassID.get(t.o));
		}
	}
}