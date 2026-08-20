//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.weak;

import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class WeakSummary extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(WeakSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	public WeakSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = WEAK_SUMMARY_PREFIX;
		this.isTypeFirst = false;
		this.isDataAndType = false;
		this.isTwoPass = false;
	}

	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean sSchemaNode, boolean pRepresented, boolean oRepresented, boolean oSchemaNode) {
		if (sSchemaNode) {
			if (oSchemaNode) {
				return SELF_SELF;
			}
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
				if (oSchemaNode) {
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
				if (oSchemaNode) {
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
				if (oSchemaNode) {
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
				if (oSchemaNode) {
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
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void handleDataTriple(Triple t) {
		//LOGGER.debug("\n### Read data triple: " + t.toString());
		repS = rep.get(t.s);
		repO = rep.get(t.o);
		sAuthority = getOrComputeAuthorityId(t.s);
		oAuthority = coarseObjectAuthorityId(t.o);
		sourceP = sourceOfProperty(t.p, sAuthority);
		targetP = targetOfProperty(t.p, oAuthority);

		boolean pRepresented = sourceP != null || targetP != null;
		boolean sRepresented = repS != null;
		boolean sSchemaNode = sn.contains(t.s);
		boolean oRepresented = repO != null;
		boolean oSchemaNode = sn.contains(t.o);

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sSchemaNode, pRepresented, oRepresented, oSchemaNode);
		//LOGGER.debug(showCaseName(caseNumber));
		switch (caseNumber) {
			case SELF_SELF:
				handleDataTriple_SELF_SELF(t);
				break;
			case SN_RP_RO:
				handleDataTriple_TRS_RP_RO(t);
				break;
			case SN_RP_UO:
				handleDataTriple_TRS_RP_UO(t);
				break;
			case SN_UP_RO:
				handleDataTriple_TRS_UP_RO(t);
				break;
			case SN_UP_UO:
				handleDataTriple_TRS_UP_UO(t);
				break;
			case RS_RP_SN:
				handleDataTriple_RS_RP_TRO(t);
				break;
			case RS_RP_RO:
				handleDataTriple_RS_RP_RO(t);
				break;
			case RS_RP_UO:
				handleDataTriple_RS_RP_UO(t);
				break;
			case RS_UP_SN:
				handleDataTriple_RS_UP_TRO(t);
				break;
			case RS_UP_RO:
				handleDataTriple_RS_UP_RO(t);
				break;
			case RS_UP_UO:
				handleDataTriple_RS_UP_UO(t);
				break;
			case US_RP_SN:
				handleDataTriple_US_RP_TRO(t);
				break;
			case US_RP_RO:
				handleDataTriple_US_RP_RO(t);
				break;
			case US_RP_UO:
				handleDataTriple_US_RP_UO(t);
				break;
			case US_UP_SN:
				handleDataTriple_US_UP_TRO(t);
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
	}

	// -->
	// Debug methods
	// -->

	@Override
	protected void consistencyChecks() {
		for (Long s: edgesWithProv.keySet()) {
			HashMap<Long, HashSet<Long>> triplesOfThisSubject = edgesWithProv.get(s);
			for (Long p: triplesOfThisSubject.keySet()) {
				HashSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if (RDF2SQLEncoding.isDataProperty(p)) {
					if (objectsOfThisSandP.size() > 1)
						throw new IllegalStateException("Subject " + s + " has more than one edge with label " + p);
					for (Long o: objectsOfThisSandP) {
						// ps/pt are now scoped per authority; since s/o are summary node ids (not
						// dictionary-backed), their authority cannot be recomputed here, so we check
						// existence across all authority partitions instead
						HashMap<Long, Long> psForP = ps.get(p);
						if (psForP == null || !psForP.containsValue(s)) {
							throw new IllegalStateException("No source for " + p + " matching " + s);
						}
						HashMap<Long, Long> ptForP = pt.get(p);
						if (ptForP == null || !ptForP.containsValue(o)) {
							throw new IllegalStateException("No target for " + p + " matching " + o);
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
			default:
				throw new IllegalStateException("Unrecognized case " + caseNumber);
		}
	}
}
