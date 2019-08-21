//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.strong;

import fr.inria.cedar.RDFQuotient.datastructures.Long2Long;
import fr.inria.cedar.RDFQuotient.datastructures.Long2LongSet;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TypedStrongSummary extends StrongOrTypedStrongSummary {
	private static final Logger LOGGER = Logger.getLogger(TypedStrongSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	protected final static char TRS_RP_RO = 21;
	protected final static char TRS_RP_UO = 22;

	protected final static char TRS_UP_RO = 25;
	protected final static char TRS_UP_UO = 26;

	protected final static char RS_RP_TRO = 28;
	protected final static char RS_UP_TRO = 29;

	protected final static char US_UP_TRO = 31;
	protected final static char US_RP_TRO = 32;

	public TypedStrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TYPED_STRONG_SUMMARY_PREFIX;
		this.isTypeFirst = true;
		this.isDataAndType = false;
		this.isTwoPass = false;
		cs = new Long2LongSet();
		acs = new Long2LongSet();
		n2sc = new Long2Long();
		n2cs = new Long2Long();
		cs2csID = new HashMap<>();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE;
	}

	@Override
	protected void representTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean sTyped, boolean sSchemaNode, boolean pRepresented, boolean oRepresented, boolean oTyped, boolean oSchemaNode) {
		if ((sTyped || sSchemaNode) && (oTyped || oSchemaNode)) {
			return SELF_SELF;
		}
		if (sTyped) {
			if (pRepresented) {
				if (oRepresented) {
					return TRS_RP_RO;
				}
				else {
					return TRS_RP_UO;
				}
			}
			else {
				if (oRepresented) {
					return TRS_UP_RO;
				}
				else {
					return TRS_UP_UO;
				}
			}
		}
		else if (sSchemaNode) {
			if (pRepresented) {
				if (oRepresented) {
					return SN_RP_RO;
				}
				else {
					return SN_RP_UO;
				}
			}
			else {
				if (oRepresented) {
					return SN_UP_RO;
				}
				else {
					return SN_UP_UO;
				}
			}
		}
		else if (sRepresented) {
			if (pRepresented) {
				if (oTyped) {
					return RS_RP_TRO;
				}
				else if (oSchemaNode) {
					return RS_RP_SN;
				}
				else if (oRepresented) {
					return RS_RP_RO;
				}
				else {
					return RS_RP_UO;
				}
			}
			else {
				if (oTyped) {
					return RS_UP_TRO;
				}
				else if (oSchemaNode) {
					return RS_UP_SN;
				}
				else if (oRepresented) {
					return RS_UP_RO;
				}
				else {
					return RS_UP_UO;
				}
			}
		}
		else {
			if (pRepresented) {
				if (oTyped) {
					return US_RP_TRO;
				}
				else if (oSchemaNode) {
					return US_RP_SN;
				}
				else if (oRepresented) {
					return US_RP_RO;
				}
				else {
					return US_RP_UO;
				}
			}
			else {
				if (oTyped) {
					return US_UP_TRO;
				}
				else if (oSchemaNode) {
					return US_UP_SN;
				}
				else if (oRepresented) {
					return US_UP_RO;
				}
				else {
					return US_UP_UO;
				}
			}
		}
	}

	@Override
	protected void handleDataTriple(Triple t) {
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);

		sourceCliqueS = n2sc.get(t.s);
		targetCliqueS = n2tc.get(t.s);
		sourceCliqueO = n2sc.get(t.o);
		targetCliqueO = n2tc.get(t.o);

		sourceCliqueP = p2sc.get(t.p);
		targetCliqueP = p2tc.get(t.p);

		Long repO = rep.get(t.o);
		Long repS = rep.get(t.s);

		boolean pRepresented = sourceCliqueP != null || targetCliqueP != null;
		boolean sRepresented = repS != null;
		boolean sTyped = classSetS != null;
		boolean sSchemaNode = sn.contains(t.s);
		boolean oRepresented = repO != null;
		boolean oTyped = classSetO != null;
		boolean oSchemaNode = sn.contains(t.o);

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sSchemaNode, sTyped, pRepresented, oRepresented, oTyped, oSchemaNode);
		//LOGGER.debug("Case " + this.showCaseName(caseNumber));
		switch (caseNumber) {
			case SELF_SELF:
				handleDataTriple_SELF_SELF(t);
				break;
			case SN_RP_RO:
			case TRS_RP_RO:
				handleDataTriple_TRS_RP_RO(t);
				break;
			case SN_RP_UO:
			case TRS_RP_UO:
				handleDataTriple_TRS_RP_UO(t);
				break;
			case RS_RP_SN:
			case RS_RP_TRO:
				handleDataTriple_RS_RP_TRO(t);
				break;
			case RS_RP_RO:
				handleDataTriple_RS_RP_RO(t);
				break;
			case RS_RP_UO:
				handleDataTriple_RS_RP_UO(t);
				break;
			case US_RP_SN:
			case US_RP_TRO:
				handleDataTriple_US_RP_TRO(t);
				break;
			case US_RP_RO:
				handleDataTriple_US_RP_RO(t);
				break;
			case US_RP_UO:
				handleDataTriple_US_RP_UO(t);
				break;
			case SN_UP_RO:
			case TRS_UP_RO:
				handleDataTriple_TRS_UP_RO(t);
				break;
			case SN_UP_UO:
			case TRS_UP_UO:
				handleDataTriple_TRS_UP_UO(t);
				break;
			case RS_UP_SN:
			case RS_UP_TRO:
				handleDataTriple_RS_UP_TRO(t);
				break;
			case RS_UP_RO:
				handleDataTriple_RS_UP_RO(t);
				break;
			case RS_UP_UO:
				handleDataTriple_RS_UP_UO(t);
				break;
			case US_UP_SN:
			case US_UP_TRO:
				handleDataTriple_US_UP_TRO(t);
				break;
			case US_UP_RO:
				handleDataTriple_US_UP_RO(t);
				break;
			case US_UP_UO:
				handleDataTriple_US_UP_UO(t);
				break;
			default:
				throw new IllegalStateException("Unknown case;");
		}
		cacheTriple(t);
	}

	// -->
	// Debug methods
	// -->

	@Override
	protected String showCaseName(char caseNumber) {
		switch (caseNumber) {
			case SELF_SELF:
				return "SELF_SELF";
			case SN_RP_RO:
				return "SN_RP_RO";
			case SN_RP_UO:
				return "SN_RP_UO";
			case SN_UP_RO:
				return "SN_UP_RO";
			case SN_UP_UO:
				return "SN_UP_UO";
			case RS_RP_SN:
				return "RS_RP_SN";
			case RS_RP_RO:
				return "RS_RP_RO";
			case RS_RP_UO:
				return "RS_RP_UO";
			case RS_UP_SN:
				return "RS_UP_SN";
			case RS_UP_RO:
				return "RS_UP_RO";
			case RS_UP_UO:
				return "RS_UP_UO";
			case US_RP_SN:
				return "US_RP_SN";
			case US_RP_RO:
				return "US_RP_RO";
			case US_RP_UO:
				return "US_RP_UO";
			case US_UP_SN:
				return "US_UP_SN";
			case US_UP_RO:
				return "US_UP_RO";
			case US_UP_UO:
				return "US_UP_UO";
			case TRS_RP_RO:
				return "TRS_RP_RO";
			case TRS_RP_UO:
				return "TRS_RP_UO";
			case TRS_UP_RO:
				return "TRS_UP_RO";
			case TRS_UP_UO:
				return "TRS_UP_UO";
			case RS_RP_TRO:
				return "RS_RP_TRO";
			case RS_UP_TRO:
				return "RS_UP_TRO";
			case US_UP_TRO:
				return "US_UP_TRO";
			case US_RP_TRO:
				return "US_RP_TRO";
			default:
				throw new IllegalStateException("Unrecognized case " + caseNumber);
		}
	}

	private String showLongSet(TreeSet<Long> s){
		StringBuffer sb = new StringBuffer();
		sb.append("{");
		for (Long e: s){
			sb.append(e).append(" ");
		}
		sb.append("}");
		return new String(sb);
	}

	private void displayClassSets() {
		for (TreeSet<Long> classSets: cs2csID.keySet()){
			StringBuilder thisCSBuffer = new StringBuilder();
			thisCSBuffer.append(showLongSet(classSets));
			thisCSBuffer.append("-->");
			thisCSBuffer.append(cs2csID.get(classSets));
			System.out.println(thisCSBuffer.toString());
		}
	}

	public void displayRepThroughCliques() {
		StringBuffer sb = new StringBuffer();
		sb.append("n2sc: ");
		for (Long node: n2sc.getKeys()) {
			if (node == null)
				throw new IllegalStateException("Null node");
			Long thisNodeSC = n2sc.get(node);
			if (thisNodeSC == null)
				throw new IllegalStateException("Null source clique");
			System.out.println("n2sc: " + node + "->" + thisNodeSC);
			Long thisNodeTC = n2tc.get(node);
			if (thisNodeTC == null)
				throw new IllegalStateException("Null target clique for " + node);
			if (untypedSummaryNodes == null)
				throw new IllegalStateException("Untyped summary nodes");
			if (untypedSummaryNodes.get(thisNodeSC) == null)
				throw new IllegalStateException("Unknown source clique " + thisNodeSC);
			Long summaryNode = untypedSummaryNodes.get(thisNodeSC).get(thisNodeTC);
			if (summaryNode == null)
				throw new IllegalStateException("Null summary node");
			sb.append(node).append("->").append(summaryNode).append(" ");
		}
		System.out.println(sb);
	}

	@Override
	public void display() {
		System.out.println("TYPED STRONG SUMMARY\nClass set IDs to class sets: " + cs.toString());
		System.out.println("Nodes to class set IDs: " + n2cs.toString());
		System.out.println("Source cliques: " + sc.toString());
		System.out.println("Target cliques: " + tc.toString());
		System.out.println("Nodes to source cliques: " + n2sc.toString());
		System.out.println("Nodes to target cliques: " + n2tc.toString());
		System.out.println("Property to source cliques: " + p2sc.toString());
		System.out.println("Property to target cliques: " + p2tc.toString());
		System.out.println("Representation function: ");
		System.out.println(showRep());
		System.out.println("Cs to cs ID: ");
		displayClassSets();
		System.out.println("Summary edges: ");
		edgesWithProv.display();
	}
}
