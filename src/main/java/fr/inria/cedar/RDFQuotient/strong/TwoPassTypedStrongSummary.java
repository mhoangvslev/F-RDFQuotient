//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.strong;

import fr.inria.cedar.RDFQuotient.datastructures.Long2Long;
import fr.inria.cedar.RDFQuotient.datastructures.Long2LongSet;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassTypedStrongSummary extends StrongOrTypedStrongSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassTypedStrongSummary.class.getName());

	static {
		LOGGER.setLevel(Level.INFO);
	}

	//protected HashMap<Long, HashMap<Long, TreeSet<Long>>> edges;

	public TwoPassTypedStrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_TYPED_STRONG_SUMMARY_PREFIX;
		this.isTypeFirst = true;
		this.isDataAndType = false;
		this.isTwoPass = true;
		//this.edges = new HashMap<>();
		cs = new Long2LongSet();
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

	@Override
	protected void classificationPostProcessing() {
	}

	@Override
	protected void classifyDataTriple(Triple t) {
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);
		boolean sTyped = (classSetS != null);
		boolean oTyped = (classSetO != null);

		sourceCliqueS = n2sc.get(t.s);
		//targetCliqueS = n2tc.get(t.s);
		//sourceCliqueO = n2sc.get(t.o);
		targetCliqueO = n2tc.get(t.o);

		sourceCliqueP = p2sc.get(t.p);
		targetCliqueP = p2tc.get(t.p);

		boolean sSchemaNode = sn.contains(t.s);
		boolean oSchemaNode = sn.contains(t.o);

		Long newSourceClique;
		Long newTargetClique;
		if (!sSchemaNode && !sTyped) {
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
		if (!oSchemaNode && !oTyped) {
			if (targetCliqueP == null && targetCliqueO == null) {
				targetCliqueP = makeAndAddNewTargetClique(t.p);
				p2tc.put(t.p, targetCliqueP);
				n2tc.put(t.o, targetCliqueP);
			}
			else if (targetCliqueP != null && targetCliqueO == null) {
				n2tc.put(t.o, targetCliqueP);
			}
			else if (targetCliqueP == null && targetCliqueO != null) {
				targetCliqueP = makeAndAddNewTargetClique(t.p);
				newTargetClique = cliqueFusionResult(targetCliqueP, targetCliqueO, TARGET);
				fuseCliqueInto(targetCliqueO, newTargetClique, TARGET);
				fuseCliqueInto(targetCliqueP, newTargetClique, TARGET);
				computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetClique, TARGET);
				p2tc.put(t.p, newTargetClique);
				n2tc.put(t.o, newTargetClique);
			}
			else { // both not-null
				newTargetClique = cliqueFusionResult(targetCliqueP, targetCliqueO, TARGET);
				fuseCliqueInto(targetCliqueO, newTargetClique, TARGET);
				fuseCliqueInto(targetCliqueP, newTargetClique, TARGET);
				computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetClique, TARGET);
				p2tc.put(t.p, newTargetClique);
				n2tc.put(t.o, newTargetClique);
			}
		}
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
			if (!clique1.equals(this.getEmptyTargetCliqueID()) && (!clique1.equals(cliqueNew))){
				//LOGGER.debug("CLIQUE REPLACE IN UNTYPED, P2, N2 TARGET: we'll replace " + clique1 +  " with " + cliqueNew);
				//LOGGER.debug("CLIQUE REPLACE IN UNTYPED, P2, N2 TARGET: that is " + this.showCliqueAsString(tc.get(clique1)) + " with " + this.showCliqueAsString(tc.get(clique2)));
				toBeReplaced.add(clique1);
			}
			if (!clique2.equals(this.getEmptyTargetCliqueID()) && (!clique2.equals(cliqueNew))){
				//LOGGER.debug("CLIQUE REPLACE IN UNTYPED, P2, N2 TARGET: we'll replace " + clique2 + " with " + cliqueNew);
				toBeReplaced.add(clique2);
			}
		}
		// apply:
		for (Long oldClique: toBeReplaced) {
			replaceCliqueInP2(oldClique, cliqueNew, param);
			replaceCliqueInN2(oldClique, cliqueNew, param);
		}
	}

	@Override
	protected void representDataTriple(Triple t) {
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);
		boolean sTyped = (classSetS != null);
		boolean oTyped = (classSetO != null);
		if (!sTyped && !sn.contains(t.s)) {
			sourceCliqueS = n2sc.get(t.s) != null ? n2sc.get(t.s) : getEmptySourceCliqueID();
			targetCliqueS = n2tc.get(t.s) != null ? n2tc.get(t.s) : getEmptyTargetCliqueID();
			rep.put(t.s, getOrCreateSummaryNode(sourceCliqueS, targetCliqueS));
		}
		//else {
		// typed or schema node already represented
		//}
		if (!oTyped && !sn.contains(t.o)) {
			sourceCliqueO = n2sc.get(t.o) != null ? n2sc.get(t.o) : getEmptySourceCliqueID();
			targetCliqueO = n2tc.get(t.o) != null ? n2tc.get(t.o) : getEmptyTargetCliqueID();
			rep.put(t.o, getOrCreateSummaryNode(sourceCliqueO, targetCliqueO));
		}
		//else {
		// typed or schema node already represented
		//}
	}
}