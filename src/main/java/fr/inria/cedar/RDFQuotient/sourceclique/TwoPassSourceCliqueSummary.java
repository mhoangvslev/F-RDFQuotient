//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.sourceclique;

import java.util.HashMap;
import java.util.TreeSet;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import fr.inria.cedar.RDFQuotient.strong.StrongOrTypedStrongSummary;

/**
 * This is an attempt at a two-pass summarization that considers two nodes are equivalent if they have
 * the same source clique, regardless of their target clique.
 * 
 * The class is not finished. It is based on StrongOrTypedStrongSummary, but it appears likely that more
 * methods from that class should be overwritten to ensure that we get the desired behavior. 
 * At a minimum, checks are needed to makes sure the "target clique" part of the code is not actually used.
 * 
 * TODO finalize this class.
 * 
 * @author ioanamanolescu
 * Dec 17, 2018
 *
 */
public class TwoPassSourceCliqueSummary extends StrongOrTypedStrongSummary{
	private static final Logger LOGGER = Logger.getLogger(TwoPassSourceCliqueSummary.class.getName());

	// this should be used instead of untypedSummaryNodes in the superclass
	HashMap<Long, Long> untypedNodesBySC;
	
	public TwoPassSourceCliqueSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		untypedNodesBySC = new HashMap<Long, Long>();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_SOURCE_SUMMARY_PREFIX;
		this.isTypeFirst = false;
		this.isTwoPass = true;
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	@Override
	protected void classifyDataTriple(Triple t) {
		sourceCliqueS = n2sc.get(t.s);

		sourceCliqueP = p2sc.get(t.p);

		boolean sSchemaNode = sn.contains(t.s);

		Long newSourceClique;
		if (!sSchemaNode) {
			if (sourceCliqueP == null && sourceCliqueS == null) {
				sourceCliqueP = makeAndAddNewSourceClique(t.p);
				p2sc.put(t.p, sourceCliqueP);
				n2sc.put(t.s, sourceCliqueP);
			}
			else if (sourceCliqueP != null && sourceCliqueS == null) {
				n2sc.put(t.s, sourceCliqueP);
			}
			else if (sourceCliqueP == null && sourceCliqueS != null) {
				sourceCliqueP = makeAndAddNewSourceClique(t.p);
				newSourceClique = cliqueFusionResult(sourceCliqueP, sourceCliqueS, SOURCE);
				fuseCliqueInto(sourceCliqueS, newSourceClique, SOURCE);
				fuseCliqueInto(sourceCliqueP, newSourceClique, SOURCE);
				computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceClique, SOURCE);
				p2sc.put(t.p, newSourceClique);
				n2sc.put(t.s, newSourceClique);
			}
			else { // both not-null
				newSourceClique = cliqueFusionResult(sourceCliqueP, sourceCliqueS, SOURCE);
				fuseCliqueInto(sourceCliqueS, newSourceClique, SOURCE);
				fuseCliqueInto(sourceCliqueP, newSourceClique, SOURCE);
				computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceClique, SOURCE);
				p2sc.put(t.p, newSourceClique);
				n2sc.put(t.s, newSourceClique);
			}
		}
	}

	@Override
	protected void classificationPostProcessing() {
	}

	protected void computeAndApplyCliqueReplacements(Long clique1, Long clique2, Long cliqueNew, char param) {
		TreeSet<Long> toBeReplaced = new TreeSet<>();
		if (param == SOURCE){
			if (!clique1.equals(this.getEmptySourceCliqueID()) && (!clique1.equals(cliqueNew))){
				//LOGGER.debug("CLIQUE REPLACE IN UNTYPED, P2, N2 SOURCE: we'll replace " + clique1 + " with " + cliqueNew);
				toBeReplaced.add(clique1); 
			}
			if (!clique2.equals(this.getEmptySourceCliqueID()) && (!clique2.equals(cliqueNew))){
				//LOGGER.debug("CLIQUE REPLACE IN UNTYPED, P2, N2 SOURCE: we'll replace " + clique2 + " with " + cliqueNew);
				toBeReplaced.add(clique2); 
			}
		}
		else if (param == TARGET){
			// nothing
		}
		// apply: 
		for (Long oldClique: toBeReplaced) {
			replaceCliqueInP2(oldClique, cliqueNew, param);
			replaceCliqueInN2(oldClique, cliqueNew, param);
		}
	}

	@Override
	protected void representDataTriple(Triple t) {
		if (sn.contains(t.s)) {
			rep.put(t.s, t.s);
		}
		else {
			sourceCliqueS = n2sc.get(t.s) != null ? n2sc.get(t.s) : getEmptySourceCliqueID();
			rep.put(t.s, getOrCreateSummaryNode(sourceCliqueS));
		}
		if (sn.contains(t.o)) {
			rep.put(t.o, t.o);
		}
		else {
			sourceCliqueO = n2sc.get(t.o) != null ? n2sc.get(t.o) : getEmptySourceCliqueID();
			rep.put(t.o, getOrCreateSummaryNode(sourceCliqueO));
		}
	}
	
	/**
	 * Creates a new (untyped) summary node and inserts it into untypedNodesBySC
	 *
	 * @param sourceClique
	 * @param targetClique
	 *
	 * @return
	 */
	protected Long getOrCreateSummaryNode(Long sourceClique) {
		Long node = untypedNodesBySC.get(sourceClique); 
		if (node != null) {
			return node;
		}
		else {
			node = getNextSummaryNode(); // from the Summary class; 
			untypedNodesBySC.put(sourceClique, node);
			return node;
		}
	}

}
