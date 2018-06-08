package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class StrongSummary extends StrongOrTypedStrongSummary {
	private static final Logger LOGGER = Logger.getLogger(StrongSummary.class.getName());

	public StrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = STRONG_SUMMARY_PREFIX;
		this.isTypeFirst = false;
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
	protected void handleDataTriple(Triple t) {
		Long sourceCliqueS = n2sc.get(t.s);
		Long targetCliqueS = n2tc.get(t.s);
		Long sourceCliqueO = n2sc.get(t.o);
		Long targetCliqueO = n2tc.get(t.o);

		Long sourceCliqueP = p2sc.get(t.p);
		Long targetCliqueP = p2tc.get(t.p);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		boolean pRepresented = (sourceCliqueP != null);
		boolean sRepresented = (repS != null);
		boolean sSchemaNode = sn.contains(t.s);
		boolean oRepresented = (repO != null);
		boolean oSchemaNode = sn.contains(t.o);

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sSchemaNode, pRepresented, oRepresented, oSchemaNode);
		//LOGGER.debug("\n" + t.toString() + " " + RDF2SQLEncoding.decode(t) + " case: " + showCaseName(caseNumber));
		switch (caseNumber) {
			case SELF_SELF:
				handleDataTriple_SELF_SELF(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case SN_RP_RO:
				handleDataTriple_TRS_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case SN_RP_UO:
				handleDataTriple_TRS_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_RP_SN:
				handleDataTriple_RS_RP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_RP_RO:
				handleDataTriple_RS_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_RP_UO:
				handleDataTriple_RS_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_RP_SN:
				handleDataTriple_US_RP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_RP_RO:
				handleDataTriple_US_RP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_RP_UO:
				handleDataTriple_US_RP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case SN_UP_RO:
				handleDataTriple_TRS_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case SN_UP_UO:
				handleDataTriple_TRS_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_UP_SN:
				handleDataTriple_RS_UP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_UP_RO:
				handleDataTriple_RS_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case RS_UP_UO:
				handleDataTriple_RS_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_UP_SN:
				handleDataTriple_US_UP_TRO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_UP_RO:
				handleDataTriple_US_UP_RO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			case US_UP_UO:
				handleDataTriple_US_UP_UO(t, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP);
				break;
			default:
				throw new IllegalStateException("Unknown case;");
		}
		cacheTriple(t);
		//display(); 
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
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
			default:
				throw new IllegalStateException("Unrecognized case " + caseNumber);
		}
	}
}
