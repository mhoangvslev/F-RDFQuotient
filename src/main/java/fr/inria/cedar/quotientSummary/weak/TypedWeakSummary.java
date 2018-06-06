package fr.inria.cedar.quotientSummary.weak;

import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TypedWeakSummary extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(TypedWeakSummary.class.getName());

	protected final static char TRS_RP_RO = 21;
	protected final static char TRS_RP_UO = 22;

	protected final static char TRS_UP_RO = 25;
	protected final static char TRS_UP_UO = 26;

	protected final static char RS_RP_TRO = 28;
	protected final static char RS_UP_TRO = 29;

	protected final static char US_RP_TRO = 31;
	protected final static char US_UP_TRO = 32;

	public TypedWeakSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.isTypeFirst = true;
		this.isTwoPass = false;
		cs = new Long2LongSet();
		n2cs = new Long2Long();
		n2c = new Long2LongSet();
		cs2csID = new HashMap<>();
		this.summaryTablePrefix = TYPED_WEAK_SUMMARY_PREFIX;
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
		//LOGGER.debug("### Data triple: " + t.toString());
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p);

		// Properties appearing in triples where one node is typed and the other is not,
		// may have a source but lack a target, or the opposite.
		// Thus, is "represented" a property having a source OR a target. It doesn't have to have both.
		// If a property only occurs between typed nodes, it is considered non represented.
		boolean pRepresented = ((pSource != null) || (pTarget != null)); 
		boolean sRepresented = (repS != null);
		boolean sTyped = (n2cs.get(t.s) != null);
		boolean sSchemaNode = sn.contains(t.s);
		boolean oRepresented = (repO != null);
		boolean oTyped = (n2cs.get(t.o) != null);
		boolean oSchemaNode = sn.contains(t.o);

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sSchemaNode, sTyped, pRepresented, oRepresented, oTyped, oSchemaNode);
//		LOGGER.debug("\nCase: " + this.showCaseName(caseNumber) + " " + t.toString() + " " + 
//			RDF2SQLEncoding.dictionaryDecode(t.s) + " " + 
//			RDF2SQLEncoding.dictionaryDecode(t.p) + " " +
//			RDF2SQLEncoding.dictionaryDecode(t.o));
		switch (caseNumber) {
			case SELF_SELF:
				handleDataTriple_SELF_SELF(t);
				break;
			case SN_RP_RO:
			case TRS_RP_RO:
				handleDataTriple_TRS_RP_RO(t, repS, repO, pSource, pTarget);
				break;
			case SN_RP_UO:
			case TRS_RP_UO:
				handleDataTriple_TRS_RP_UO(t, repS, repO, pSource, pTarget);
				break;
			case SN_UP_RO:
			case TRS_UP_RO:
				handleDataTriple_TRS_UP_RO(t, repS, repO, pSource, pTarget);
				break;
			case SN_UP_UO:
			case TRS_UP_UO:
				handleDataTriple_TRS_UP_UO(t, repS, repO, pSource, pTarget);
				break;
			case RS_RP_SN:
			case RS_RP_TRO:
				handleDataTriple_RS_RP_TRO(t, repS, repO, pSource, pTarget);
				break;
			case RS_RP_RO:
				handleDataTriple_RS_RP_RO(t);
				break;
			case RS_RP_UO:
				handleDataTriple_RS_RP_UO(t);
				break;
			case RS_UP_SN:
			case RS_UP_TRO:
				handleDataTriple_RS_UP_TRO(t, repS, repO, pSource, pTarget);
				break;
			case RS_UP_RO:
				handleDataTriple_RS_UP_RO(t);
				break;
			case RS_UP_UO:
				handleDataTriple_RS_UP_UO(t);
				break;
			case US_RP_SN:
			case US_RP_TRO:
				handleDataTriple_US_RP_TRO(t, repS, repO, pSource, pTarget);
				break;
			case US_RP_RO:
				handleDataTriple_US_RP_RO(t);
				break;
			case US_RP_UO:
				handleDataTriple_US_RP_UO(t);
				break;
			case US_UP_SN:
			case US_UP_TRO:
				handleDataTriple_US_UP_TRO(t, repS, repO, pSource, pTarget);
				break;
			case US_UP_RO:
				handleDataTriple_US_UP_RO(t);
				break;
			case US_UP_UO:
				handleDataTriple_US_UP_UO(t);
				break;
			default:
				throw new IllegalStateException("This case should not be encountered here");
		}

		//LOGGER.debug("After processing triple " + t.toString() + ", we have:\n" + this.toString()); 
		//safetyCheck(); 
	}

	// -->
	// Debug methods
	// -->

	@Override
	protected void consistencyChecks() {
		for (Long s: edgesWithProv.keySet()) { // s is a summary node 
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edgesWithProv.get(s);
			if (triplesOfThisSubject == null)
				throw new IllegalStateException("No triples whose subject is " + s);
			if (n2cs.getInverse(s) == null) { // untyped s
				for (Long p: triplesOfThisSubject.keySet()) {
					TreeSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
					if (RDF2SQLEncoding.isDataProperty(p)) {
						if (objectsOfThisSandP.size() > 1)
							throw new IllegalStateException("Subject " + s + " has more than one edge with label " + p);
						for (Long o: objectsOfThisSandP) {
							if (n2cs.getInverse(o) == null) { // untyped o
								Long sp = ps.get(p); 
								if (sp == null){
									throw new IllegalStateException("Null source for property " + p + " (" +
												RDF2SQLEncoding.dictionaryDecode(p) + ") of untyped node " + s + 
												 " (" +	RDF2SQLEncoding.dictionaryDecode(s) + ")");
								}
								else{
									if (!sp.equals(s)){
										throw new IllegalStateException("Source of " + p + " is not " + s + " but " + sp);
									}
								}
								Long tp = pt.get(p); 
								if (tp == null){
									throw new IllegalStateException("Null target for property " + p + " (" +
												RDF2SQLEncoding.dictionaryDecode(p) + ") incoming untyped node " + o + 
												 " (" +	RDF2SQLEncoding.dictionaryDecode(o) + ")");
								}
								else{
									if (!tp.equals(o)){
										throw new IllegalStateException("Target of " + p + " is not " + o + " but " + tp);
									}
								}
							}
						}
					}
				}
			}
		}
	}

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
			case US_RP_TRO:
				return "US_RP_TRO";
			case US_UP_TRO:
				return "US_UP_TRO";
			default:
				throw new IllegalStateException("Unrecognized case " + caseNumber);
		}
	}
}
