//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/quotientSummary/blob/master/LICENCE.txt

package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.EdgeTransferSpecification;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.ReplacementSpecification;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.datastructures.TwoLevelLongMap;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class StrongOrTypedStrongSummary extends Summary {
	private static final Logger LOGGER = Logger.getLogger(StrongOrTypedStrongSummary.class.getName());

	protected Long2LongSet sc; // for each source clique ID, its source clique
	protected Long2LongSet tc; // for each target clique ID, its target clique
	protected Long2Long n2sc; // for each data node, its source clique ID
	protected Long2Long n2tc; // for each data node, its target clique ID
	protected Long2Long p2sc; // property to source clique
	protected Long2Long p2tc; // property to target clique
	protected TwoLevelLongMap untypedSummaryNodes; // source clique --> target clique --> summary node
	// for patching edges, we really need to store the data graph in memory... :(
	protected HashMap<Long, Long2LongSet> triplesBySubject; // s-->{p-->{o}} s, o are data nodes
	protected HashMap<Long, Long2LongSet> triplesByObject; // o-->{p-->{s}}
	protected long minCliqueID;
	protected long emptySCCount; // empty source clique number (will never change)
	protected long emptyTCCount; // empty target clique number (will never change) 
	protected char SOURCE = 0;
	protected char TARGET = 1;

	protected Long sourceCliqueS;
	protected Long sourceCliqueO;
	protected Long targetCliqueS;
	protected Long targetCliqueO;
	protected Long sourceCliqueP;
	protected Long targetCliqueP;

	// U means unrepresented (so far) 
	// R means represented (so far) 
	// TRS means typed (thus, already represented) represented so far
	// SN means schema node
	protected final static char SELF_SELF = 1;

	protected final static char SN_RP_RO = 2;
	protected final static char SN_RP_UO = 3;

	protected final static char SN_UP_RO = 5;
	protected final static char SN_UP_UO = 6;

	protected final static char RS_RP_SN = 7;
	protected final static char RS_RP_RO = 8;
	protected final static char RS_RP_UO = 9;

	protected final static char RS_UP_SN = 10;
	protected final static char RS_UP_RO = 11;
	protected final static char RS_UP_UO = 12;

	protected final static char US_RP_SN = 13;
	protected final static char US_RP_RO = 14;
	protected final static char US_RP_UO = 15;

	protected final static char US_UP_SN = 16;
	protected final static char US_UP_RO = 17;
	protected final static char US_UP_UO = 18;

	public StrongOrTypedStrongSummary() {
		super();
		LOGGER.setLevel(Level.INFO);
		sc = new Long2LongSet();
		tc = new Long2LongSet();
		n2sc = new Long2Long();
		n2tc = new Long2Long();
		p2sc = new Long2Long();
		p2tc = new Long2Long();
		untypedSummaryNodes = new TwoLevelLongMap();
		triplesBySubject = new HashMap<>();
		triplesByObject = new HashMap<>();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE;
	}

	// -->
	// Common handlers
	// -->

	// toughest case:
	// untyped, represented object
	// represented property
	// untyped, represented subject
	protected void handleDataTriple_RS_RP_RO(Triple t) {
		// determine future cliques
		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);
		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		Long newSourceCliqueO;
		Long newTargetCliqueS;

		if (t.s == t.o) {
			newSourceCliqueO = newSourceCliqueS;
			newTargetCliqueS = newTargetCliqueO;
		}
		else {
			newSourceCliqueO = sourceCliqueO;
			newTargetCliqueS = targetCliqueS;
		}

		// determine current representatives
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		// determine future representatives: we create them but do nothing else so far
		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, newTargetCliqueS);
		Long newRepO = getOrCreateSummaryNode(newSourceCliqueO, newTargetCliqueO);

		// for s (o), we will either replace the former with the new
		// representative, or change the representative just of s (o) while
		// leaving its old representative in the summary, together with the
		// other nodes it used to represent
		// if replaceForS = true, we replace, otherwise, we give the new
		// representative to s and leave it untouched for the others
		// replaceForS = true also leads to many clique replacements and
		// fusions, which the other does not
		boolean replaceForS = true;
		boolean replaceForO = true;
		// due to the current triple, newSourceCliqueS for sure is not empty
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			// other nodes were (and still are) on the empty sourceCliqueS and targetCliqueS
			if (rep.getInverse(repS).size() > 1) {
				// In this case, we should not replace repS with newRepS, but
				// only represent s by newRepS -- and keep repS!
				// Also, we should not replace sourceCliqueS with newSC, but
				// create newSourceCliqueS and keep sourceCliqueS!
				replaceForS = false;
				if (t.s == t.o) {
					replaceForO = false;
				}
			}
		}
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
				if (t.s == t.o) {
					replaceForS = false;
				}
			}
		}

		// so far summary unchanged (except for the side effect that some nodes
		// were created -- not connected anywhere yet -- thus the next summary
		// node number has increased)
		// now we start to apply the decisions
		//LOGGER.debug("RS_RP_RO REPLACE S: " + replaceForS + " REPLACE O: " + replaceForO);
		// really modify cliques (and do nothing else)
		if (replaceForS) {
			fuseCliqueInto(sourceCliqueS, newSourceCliqueS, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSourceCliqueS, SOURCE);
		}
		//else {
		//	if we are not replacing but splitting, scs was empty, the new clique of S is that of P, no clique creation is needed
		//}
		if (replaceForO) {
			fuseCliqueInto(targetCliqueO, newTargetCliqueO, TARGET);
			fuseCliqueInto(targetCliqueP, newTargetCliqueO, TARGET);
		}
		//else {
		// do nothing
		//}
		//LOGGER.debug("RS_RP_RO After clique fusions, source cliques are " + sc.toString() + "\ntarget cliques are: " + tc.toString());
		//LOGGER.debug("RS_RP_RO while untyped is: " + untypedSummaryNodes.toString());

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)) {
				ReplacementSpecification repsS = new ReplacementSpecification(newSourceCliqueS, targetCliqueS, repS, newRepS);
				nodeReps.add(repsS);
			}
		}
		if (replaceForO) {
			if (!newRepO.equals(repO)) {
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTargetCliqueO, repO, newRepO);
				if (checkConsistency) {
					repsO.checkForConflicts(nodeReps);
				}
				nodeReps.add(repsO);
			}
		}

		// now compute and then apply the clique replacements in untyped, where
		// they were still not applied compute sourceCliqueReplacements and
		// apply them:
		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}
		//else {
		//	nothing to do because newRepS is correctly inserted in untypedNodes, on its cliques
		//}
		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}
		//else {
		// do nothing
		//}

		// now patching summary edges if needed; this may involve the removal of
		// a summary node that no longer represents anybody (because t.s or t.o
		// was the only node it represented, and now t.s/t.o has moved away to
		// another representative). This happens only if t.s/t.o had an empty
		// source/target clique and has split to another summary representative.
		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForS) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.s, TARGET));
		}
		if (!replaceForO) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.o, SOURCE));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		// now modify summary edges
		// apply nodeReplacements in all cases, because it only contains
		// replacements that should be made; e.g., if replaceForS is false but
		// replaceForO is true, it contains those node replacements that are
		// needed because of O, and will replace nothing wrongly around s
		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		//LOGGER.debug("RS_RP_RO after node and clique replacement, untyped is: " + untypedSummaryNodes.toString());
		//LOGGER.debug("RS_RP_RO while rep is: " + rep.toString());

		// now modifying rep:
		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		// now fixing s and o's cliques
		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);

		// adding the triple:
		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, represented object
	// unrepresented property
	// untyped, represented subject
	protected void handleDataTriple_RS_UP_RO(Triple t) {
		// sourceCliqueP is null, targetCliqueP is null
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);
		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		Long newSourceCliqueO;
		Long newTargetCliqueS;

		if (t.s == t.o) {
			newSourceCliqueO = newSourceCliqueS;
			newTargetCliqueS = newTargetCliqueO;
		}
		else {
			newSourceCliqueO = sourceCliqueO;
			newTargetCliqueS = targetCliqueS;
		}

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, newTargetCliqueS);
		Long newRepO = getOrCreateSummaryNode(newSourceCliqueO, newTargetCliqueO);

		boolean replaceForS = true;
		boolean replaceForO = true;
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			if (rep.getInverse(repS).size() > 1) {
				replaceForS = false;
				if (t.s == t.o) {
					replaceForO = false;
				}
			}
		}
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
				if (t.s == t.o) {
					replaceForS = false;
				}
			}
		}

		if (replaceForS) {
			fuseCliqueInto(sourceCliqueS, newSourceCliqueS, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSourceCliqueS, SOURCE);
		}
		if (replaceForO) {
			fuseCliqueInto(targetCliqueO, newTargetCliqueO, TARGET);
			fuseCliqueInto(targetCliqueP, newTargetCliqueO, TARGET);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)) {
				ReplacementSpecification repsS = new ReplacementSpecification(newSourceCliqueS, targetCliqueS, repS, newRepS);
				nodeReps.add(repsS);
			}
		}
		if (replaceForO) {
			if (!newRepO.equals(repO)) {
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTargetCliqueO, repO, newRepO);
				if (checkConsistency) {
					repsO.checkForConflicts(nodeReps);
				}
				nodeReps.add(repsO);
			}
		}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}
		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForS) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.s, TARGET));
		}
		if (!replaceForO) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.o, SOURCE));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// represented property
	// untyped, represented object
	// in this case the subject should be represented based on the source clique of P and the empty target clique
	protected void handleDataTriple_US_RP_RO(Triple t) {
		Long newSourceCliqueS = sourceCliqueP;
		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		// unrepresented subject
		Long repS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID());
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTargetCliqueO);

		boolean replaceForO = true;
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
			}
		}

		if (replaceForO) {
			fuseCliqueInto(targetCliqueO, newTargetCliqueO, TARGET);
			fuseCliqueInto(targetCliqueP, newTargetCliqueO, TARGET);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForO) {
			if (!newRepO.equals(repO)) {
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTargetCliqueO, repO, newRepO);
				nodeReps.add(repsO);
			}
		}

		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForO) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.o, SOURCE));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);
		n2tc.put(t.s, getEmptyTargetCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, represented subject
	// represented property
	// untyped, represented object
	// in this case the object should be represented based on the target clique of P and the empty target clique
	protected void handleDataTriple_RS_RP_UO(Triple t) {
		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);
		Long newTargetCliqueO = targetCliqueP;

		Long repS = rep.get(t.s);
		Long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, targetCliqueS);
		Long newRepO = repO;

		boolean replaceForS = true;
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			if (rep.getInverse(repS).size() > 1) {
				replaceForS = false;
			}
		}

		if (replaceForS) {
			fuseCliqueInto(sourceCliqueS, newSourceCliqueS, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSourceCliqueS, SOURCE);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)) {
				ReplacementSpecification repsS = new ReplacementSpecification(newSourceCliqueS, targetCliqueS, repS, newRepS);
				nodeReps.add(repsS);
			}
		}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForS) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.s, TARGET));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);
		n2sc.put(t.o, getEmptySourceCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// unknown property
	// untyped, represented object
	// in this case the subject should be represented based on the (newly created) source clique of P and the empty target clique
	protected void handleDataTriple_US_UP_RO(Triple t) {
		// sourceCliqueS is null, targetCliqueS is null, sourceCliqueP is null, targetCliqueP is null
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = sourceCliqueP;
		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		Long repS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID()); 
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTargetCliqueO);

		boolean replaceForO = true;
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
			}
		}

		if (replaceForO) {
			fuseCliqueInto(targetCliqueO, newTargetCliqueO, TARGET);
			fuseCliqueInto(targetCliqueP, newTargetCliqueO, TARGET);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForO) {
			if (!newRepO.equals(repO)) {
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTargetCliqueO, repO, newRepO);
				nodeReps.add(repsO);
			}
		}

		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForO) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.o, SOURCE));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);
		n2tc.put(t.s, getEmptyTargetCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, represented subject
	// unknown property
	// untyped, unrepresented object
	// in this case the object should be represented based on the empty source clique and the (newly created) target clique of p
	protected void handleDataTriple_RS_UP_UO(Triple t) {
		// sourceCliqueP is null, targetCliqueP is null, sourceCliqueO is null, targetCliqueO is null
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);
		Long newTargetCliqueO = targetCliqueP;

		Long repS = rep.get(t.s);
		Long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, targetCliqueS);
		Long newRepO = repO;

		boolean replaceForS = true;
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			if (rep.getInverse(repS).size() > 1) {
				replaceForS = false;
			}
		}

		if (replaceForS) {
			fuseCliqueInto(sourceCliqueS, newSourceCliqueS, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSourceCliqueS, SOURCE);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)) {
				ReplacementSpecification repsS = new ReplacementSpecification(newSourceCliqueS, targetCliqueS, repS, newRepS);
				nodeReps.add(repsS);
			}
		}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForS) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.s, TARGET));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);
		n2sc.put(t.o, getEmptySourceCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// represented property
	// untyped, unrepresented object
	// in this case the subject should be represented based on the source clique
	// of P and the empty target clique and the object should be represented
	// based on the empty source clique and the target source clique of P
	protected void handleDataTriple_US_RP_UO(Triple t) {
		Long newSourceCliqueS = sourceCliqueP;
		Long newTargetCliqueO = targetCliqueP;

		Long newRepS;
		Long newRepO;

		if (t.s == t.o) {
			if (newSourceCliqueS == null) {
				newSourceCliqueS = makeAndAddNewSourceClique(t.p);
			}
			else if (newTargetCliqueO == null) {
				newTargetCliqueO = makeAndAddNewTargetClique(t.p);
			}
			//else { // both not null
			//}
			newRepS = getOrCreateSummaryNode(newSourceCliqueS, newTargetCliqueO);
			newRepO = getOrCreateSummaryNode(newSourceCliqueS, newTargetCliqueO);

			n2sc.put(t.o, newSourceCliqueS);
			n2tc.put(t.s, newTargetCliqueO);
		}
		else {
			newRepS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID());
			newRepO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

			n2sc.put(t.o, getEmptySourceCliqueID());
			n2tc.put(t.s, getEmptyTargetCliqueID());
		}

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// unknown property
	// untyped, unrepresented object
	// in this case the subject should be represented based on the (newly
	// created) source clique of P and the empty target clique and the object
	// should be represented based on the empty source clique and the (newly
	// created) target source clique of P
	protected void handleDataTriple_US_UP_UO(Triple t) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = sourceCliqueP;
		Long newTargetCliqueO = targetCliqueP;

		Long newRepS;
		Long newRepO;

		if (t.s == t.o) {
			newRepS = getOrCreateSummaryNode(newSourceCliqueS, newTargetCliqueO);
			newRepO = getOrCreateSummaryNode(newSourceCliqueS, newTargetCliqueO);

			n2sc.put(t.o, newSourceCliqueS);
			n2tc.put(t.s, newTargetCliqueO);
		}
		else {
			newRepS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID());
			newRepO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

			n2sc.put(t.o, getEmptySourceCliqueID());
			n2tc.put(t.s, getEmptyTargetCliqueID());
		}

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	protected void handleDataTriple_SELF_SELF(Triple t) {
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		edgesWithProv.addTriple(repS, t.p, repO);
	}

	// untyped, represented subject
	// represented property
	// typed, represented object
	protected void handleDataTriple_RS_RP_TRO(Triple t) {
		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, targetCliqueS);
		Long newRepO = repO;

		boolean replaceForS = true;
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			if (rep.getInverse(repS).size() > 1) {
				replaceForS = false;
			}
		}

		if (replaceForS) {
			fuseCliqueInto(sourceCliqueS, newSourceCliqueS, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSourceCliqueS, SOURCE);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)) {
				ReplacementSpecification repsS = new ReplacementSpecification(newSourceCliqueS, targetCliqueS, repS, newRepS);
				nodeReps.add(repsS);
			}
		}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForS) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.s, TARGET));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);

		n2sc.put(t.s, newSourceCliqueS);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject
	// represented property
	// untyped, represented object
	// we need to unify the target clique of O with the target clique of P
	protected void handleDataTriple_TRS_RP_RO(Triple t) {
		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTargetCliqueO);

		boolean replaceForO = true;
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
			}
		}

		if (replaceForO) {
			fuseCliqueInto(targetCliqueO, newTargetCliqueO, TARGET);
			fuseCliqueInto(targetCliqueP, newTargetCliqueO, TARGET);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForO) {
			if (!newRepO.equals(repO)) {
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTargetCliqueO, repO, newRepO);
				nodeReps.add(repsO);
			}
		}

		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForO) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.o, SOURCE));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.o, newRepO);

		n2tc.put(t.o, newTargetCliqueO);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, represented subject: it has a source clique, which needs to gain p
	// unknown property
	// typed, represented object which won't change
	protected void handleDataTriple_RS_UP_TRO(Triple t) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, targetCliqueS);
		Long newRepO = repO;

		boolean replaceForS = true;
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			if (rep.getInverse(repS).size() > 1) {
				replaceForS = false;
			}
		}

		if (replaceForS) {
			fuseCliqueInto(sourceCliqueS, newSourceCliqueS, SOURCE);
			fuseCliqueInto(sourceCliqueP, newSourceCliqueS, SOURCE);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForS) {
			if (!newRepS.equals(repS)) {
				ReplacementSpecification repsS = new ReplacementSpecification(newSourceCliqueS, targetCliqueS, repS, newRepS);
				nodeReps.add(repsS);
			}
		}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForS) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.s, TARGET));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.s, newRepS);

		n2sc.put(t.s, newSourceCliqueS);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject won't change
	// unknown property
	// untyped, represented object, with a target clique which needs to change as p was unknown
	protected void handleDataTriple_TRS_UP_RO(Triple t) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTargetCliqueO);

		boolean replaceForO = true;
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
			}
		}

		if (replaceForO) {
			fuseCliqueInto(targetCliqueO, newTargetCliqueO, TARGET);
			fuseCliqueInto(targetCliqueP, newTargetCliqueO, TARGET);
		}

		ArrayList<ReplacementSpecification> nodeReps = new ArrayList<>();
		if (replaceForO) {
			if (!newRepO.equals(repO)) {
				ReplacementSpecification repsO = new ReplacementSpecification(sourceCliqueO, newTargetCliqueO, repO, newRepO);
				nodeReps.add(repsO);
			}
		}

		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}

		EdgeTransferSpecification edgesTransfersSpecification = new EdgeTransferSpecification();
		if (!replaceForO) {
			edgesTransfersSpecification.addAll(EdgeTransferSpecification.determineEdgesToTransfer(triplesBySubject, triplesByObject, t.o, SOURCE));
		}
		edgesTransfersSpecification.applyTransfers(edgesWithProv, rep, t.s, newRepS, t.o, newRepO);

		for (ReplacementSpecification reps: nodeReps) {
			edgesWithProv.replaceNodeInSummaryEdges(reps.getOldNode(), reps.getNewNode());
		}
		for (ReplacementSpecification reps: nodeReps) {
			rep.replaceValue(reps.getOldNode(), reps.getNewNode());
		}

		rep.put(t.o, newRepO);

		n2tc.put(t.o, newTargetCliqueO);

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// known property
	// typed, represented object
	protected void handleDataTriple_US_RP_TRO(Triple t) {
		Long newSourceCliqueS = sourceCliqueP;

		Long repS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID());
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.s, newRepS);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.s, getEmptyTargetCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject
	// known property
	// untyped, unrepresented object
	protected void handleDataTriple_TRS_RP_UO(Triple t) {
		Long newTargetCliqueO = targetCliqueP;

		Long repS = rep.get(t.s);
		Long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.o, newRepO);

		n2tc.put(t.o, newTargetCliqueO);
		n2sc.put(t.o, getEmptySourceCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// unknown property
	// typed (thus represented) object
	protected void handleDataTriple_US_UP_TRO(Triple t) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = sourceCliqueP;

		Long repS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID());
		Long repO = rep.get(t.o);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.s, newRepS);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.s, getEmptyTargetCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// typed, represented subject which won't change
	// unknown property: both its cliques need to be created
	// untyped, unrepresented object
	protected void handleDataTriple_TRS_UP_UO(Triple t) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newTargetCliqueO = targetCliqueP;

		Long repS = rep.get(t.s);
		Long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.o, newRepO);

		n2tc.put(t.o, newTargetCliqueO);
		n2sc.put(t.o, getEmptySourceCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// -->
	// Auxiliary methods
	// -->

	/**
	 * Read-only
	 * 
	 * We will take the bigger clique's ID.
	 * An exception is made if one of the cliques is the empty clique: in this case, fusion systematically
	 * takes the other clique.
	 * @param c1
	 * @param c2
	 * @param code
	 * @return 
	 */
	protected Long cliqueFusionResult(Long c1, Long c2, char code) {
		if (code == SOURCE){
			boolean c1empty = c1.equals(this.getEmptySourceCliqueID());
			boolean c2empty = c2.equals(this.getEmptySourceCliqueID());
			//LOGGER.debug("FuseCliquesIntoCreatedFirst SOURCE " + c1 + (c1empty?" (empty)":"") + " " + c2 + (c2empty?" (empty)":""));
			if (c1empty) {
				return c2;
			}
			if (c2empty) {
				return c1;
			}
			int c1Size = p2sc.getInverse(c1).size();
			int c2Size = p2sc.getInverse(c2).size();
			if (c1Size > c2Size) { // we know none is empty
				return c1;
			}
			if (c2Size > c1Size) { // we know none is empty
				return c2;
			}
		}
		else if (code == TARGET){
			boolean c1empty = c1.equals(this.getEmptyTargetCliqueID());
			boolean c2empty = c2.equals(this.getEmptyTargetCliqueID());
			//LOGGER.debug("FuseCliquesIntoCreatedFirst TARGET " + c1 + (c1empty?" (empty)":"") + " " + c2 + (c2empty?" (empty)":""));

			if (c1empty) {
				return c2;
			}
			if (c2empty) {
				return c1;
			}
			int c1Size = p2tc.getInverse(c1).size();
			int c2Size = p2tc.getInverse(c2).size();
			if (c1Size > c2Size) { // we know none is empty
				return c1;
			}
			if (c2Size > c1Size) { // we know none is empty
				return c2;
			}
		}
		else {
			throw new IllegalStateException("Unknown code!");
		}
		return c1;
	}

	// updates untypedSummaryNodes, p2sc, p2tc
	// decides how many clique replacements we need and applies them
	protected void computeAndApplyCliqueReplacements(Long clique1, Long clique2, Long cliqueNew, char param, ArrayList<ReplacementSpecification> nodeReps) {
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
			// now we replace just the nodes in untyped (not the cliques yet),
			// because the nodes are at the lowest (value) level
			// IMPORTANT: If we replace the cliques first, we may be wrongly
			// overwriting nodes: sc1-->tc1-->n1, sc1-->tc2-->n2, if we need to
			// replace tc1 with tc2, we will overwrite n2 and lose it!
			// serious problem!
			replaceCliqueInUntyped(oldClique, cliqueNew, param, nodeReps);
			replaceCliqueInP2(oldClique, cliqueNew, param);
			replaceCliqueInN2(oldClique, cliqueNew, param);
		}
	}

	// modifies sc, tc, n2sc, n2tc (NOT untyped nodes)
	// physically moves properties of the old clique into the new clique,
	// replace old clique with new clique in n2tc (or n2sc), 
	// remove the old clique from sc or tc
	protected void fuseCliqueInto(Long oldClique, Long newClique, char param){
		if (oldClique.equals(newClique)){
			return; 
		}
		if (param == SOURCE){
			this.sc.get(newClique).addAll(this.sc.get(oldClique)); 
			if (!oldClique.equals(this.getEmptySourceCliqueID())){ // if oldClique is empty, it should not be replaced/removed!
				n2sc.replaceValue(oldClique, newClique);
				this.sc.remove(oldClique);
			}
		}
		else if (param == TARGET){
			this.tc.get(newClique).addAll(this.tc.get(oldClique)); 
			if (!oldClique.equals(this.getEmptyTargetCliqueID())){ // if oldClique is empty, it should not be replaced/removed!
				n2tc.replaceValue(oldClique, newClique);
				this.tc.remove(oldClique);
			}
		}
	}

	// modifies only untyped
	protected void replaceCliqueInUntyped(Long oldClique, Long newClique, char param, ArrayList<ReplacementSpecification> nodeReps){
		if (oldClique.equals(newClique)) {
			return; 
		}
		if (param == TARGET) { // we find all occurrences of the old cliques (in the 2nd level), remove them and replace with the new clique
			HashSet<Triple> replacements = untypedSummaryNodes.replaceAt2ndLevel(oldClique, newClique);
			for (Triple t: replacements) {
				Long sourceClique = t.s;
				Long targetClique = newClique;
				Long oldNode = t.p;
				Long newNode = t.o;
				ReplacementSpecification reps = new ReplacementSpecification(sourceClique, targetClique, oldNode, newNode);
				if (checkConsistency) {
					reps.checkForConflicts(nodeReps);
				}
				nodeReps.add(reps);
			}
		}
		else if (param == SOURCE) {
			HashSet<Triple> replacements = untypedSummaryNodes.replaceAt1stLevel(oldClique, newClique);
			for (Triple t: replacements) {
				Long sourceClique = newClique;
				Long targetClique = t.s;
				Long oldNode = t.p;
				Long newNode = t.o;
				ReplacementSpecification reps = new ReplacementSpecification(sourceClique, targetClique, oldNode, newNode);
				if (checkConsistency) {
					reps.checkForConflicts(nodeReps);
				}
				nodeReps.add(reps);
			}
		}
	}

	protected void replaceCliqueInP2(Long oldClique, Long newClique, char param) {
		if (param == SOURCE) {
			this.p2sc.replaceValue(oldClique, newClique);
		}
		else if (param == TARGET) {
			this.p2tc.replaceValue(oldClique, newClique);
		}
	}

	protected void replaceCliqueInN2(Long oldClique, Long newClique, char param) {
		if (param == SOURCE) {
			this.n2sc.replaceValue(oldClique, newClique);
		}
		else if (param == TARGET) {
			this.n2tc.replaceValue(oldClique, newClique);
		}
	}

	protected boolean addingPropertyToSWillCauseASplit(Long repS, Long sourceCliqueS, Long s) {
		if (sourceCliqueS.equals(this.getEmptySourceCliqueID())) { // due to the current triple, newSCS for sure is not empty.
			HashSet<Long> inverseRep = rep.getInverse(repS);
			if (inverseRep.size() > 1) { // several nodes shared this representative: they all had empty sourceCliqueID
				// and is is the only one for which this situation changes. Thus, we will split.
				return true;
			}
			if (inverseRep.size() == 1) { // there was only one node with this representative, and empty sourceClique.
				for (Long l: inverseRep) {
					if (l.equals(s)) { // but it is exactly 
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Creates a new (untyped) summary node and inserts it into untypedSummaryNodes
	 *
	 * @param sourceClique
	 * @param targetClique
	 *
	 * @return
	 */
	protected Long getOrCreateSummaryNode(Long sourceClique, Long targetClique) {
		Long node = untypedSummaryNodes.getIfExists(sourceClique, targetClique);
		if (node != null) {
			return node;
		}
		else {
			node = getNextSummaryNode(); // from the Summary class; 
			untypedSummaryNodes.add(sourceClique, targetClique, node);
			return node;
		}
	}

	protected Long getEmptySourceCliqueID() {
		Long res;
		if (emptySCCount == Long.MAX_VALUE) { // the empty source clique has not been created yet
			TreeSet<Long> emptySC = new TreeSet<>();
			res = minCliqueID; // we invent a new source clique
			emptySCCount = minCliqueID;
			// add this to sc
			sc.put(minCliqueID, emptySC);
			minCliqueID--;
		}
		else // the empty source clique has already been created, just copy it 
			res = emptySCCount;
		return res;
	}

	/**
	 * Creates a new source clique with just p; updates sc and p2sc
	 *
	 * @param p
	 *
	 * @return
	 */
	protected Long makeAndAddNewSourceClique(Long p) {
		Long res = this.minCliqueID;
		TreeSet<Long> actualSourceClique = new TreeSet<>();
		actualSourceClique.add(p);
		sc.put(res, actualSourceClique);
		p2sc.put(p, res);
		//LOGGER.debug("NEW SOURCE CLIQUE FOR " + p+ "(" + RDF2SQLEncoding.dictionaryDecode(p) + "): " + res);  
		minCliqueID--;
		return res;
	}

	/**
	 * Initializes a target clique for property p
	 * Also records the association between p and this target clique in p2c and tc
	 *
	 * @param p
	 *
	 * @return
	 */
	protected Long makeAndAddNewTargetClique(Long p) {
		Long targetCliqueID = this.minCliqueID;
		TreeSet<Long> actualTargetClique = new TreeSet<>();
		actualTargetClique.add(p);
		tc.put(targetCliqueID, actualTargetClique);
		p2tc.put(p, targetCliqueID);
		//LOGGER.debug("Added the new target clique " + targetCliqueID + " which is [" + p + "]");
		//LOGGER.debug("NEW TARGET CLIQUE FOR " + p+ "(" + RDF2SQLEncoding.dictionaryDecode(p) + "): " + targetCliqueID);  
		minCliqueID--;
		return targetCliqueID;
	}

	protected Long getEmptyTargetCliqueID() {
		Long res;
		if (emptyTCCount == Long.MAX_VALUE) { // the empty source clique has not been created yet
			TreeSet<Long> emptyTC = new TreeSet<>();
			res = minCliqueID; // we invent a new source clique
			//LOGGER.debug("ooooo> Initialized the empty target clique at: " + res);
			emptyTCCount = minCliqueID;
			// add this to tc
			tc.put(minCliqueID, emptyTC);
			minCliqueID--;
		}
		else // the empty source clique has already been created, just copy it 
			res = emptyTCCount;
		return res;
	}

	protected void cacheTriple(Triple t) {
		// cache it by the subject:
		Long2LongSet edgesOfS = this.triplesBySubject.get(t.s);
		if (edgesOfS == null) {
			edgesOfS = new Long2LongSet();
			triplesBySubject.put(t.s, edgesOfS);
		}
		edgesOfS.add(t.p, t.o);
		//LOGGER.debug("CACHED BY SUBJECT " + t.s + " on " + t.p + ": " + t.o + " resulting in " + showTriplesBySubject());
		// cache it by the object:
		Long2LongSet edgesOfO = this.triplesByObject.get(t.o);
		if (edgesOfO == null) {
			edgesOfO = new Long2LongSet();
			triplesByObject.put(t.o, edgesOfO);
		}
		edgesOfO.add(t.p, t.s);
		//LOGGER.debug("CACHED BY OBJECT " + t.o + " on " + t.p + ": " + t.s + " resulting in " + showTriplesByObject());
	}

	// -->
	// Debug methods
	// -->

	@Override
	protected void consistencyChecks(){
		String msg = "After " + triplesSummarizedSoFar + " triples: ";
		for (Long dataNode: n2sc.getKeys()){
			//LOGGER.debug("Checking from data node: " + dataNode);
			Long nodeRep = rep.get(dataNode);
			if (nodeRep == null) {
				msg += "Unrepresented data node " + dataNode;
				//LOGGER.debug(msg);
				throw new IllegalStateException(msg);
			}
			Long nsc = n2sc.get(dataNode);
			Long ntc = n2tc.get(dataNode);
			HashMap<Long, Long> tc2Nodes = untypedSummaryNodes.get(nsc);
			if (tc2Nodes == null) {
				msg += "untypedSummaryNodes has no (target clique, node) pairs on source clique " + nsc + " of node " + dataNode + "(" + RDF2SQLEncoding.dictionaryDecode(dataNode) + ")"; 
				//LOGGER.debug(msg);
				throw new IllegalStateException(msg);
			}
			Long tcn = tc2Nodes.get(ntc);
			if (tcn == null) {
				msg += "No node found on source clique " + nsc + " for target clique " + ntc + " of data node " + dataNode + " or (" + RDF2SQLEncoding.dictionaryDecode(dataNode) + ") while its representative is " + nodeRep;
				//LOGGER.debug(msg);
				throw new IllegalStateException(msg);
			}
			if (!(tcn.equals(nodeRep))) {
				msg += nsc + "=>" + ntc + ": " + tcn + " while the representative of " + dataNode + " is " + nodeRep;
				//LOGGER.debug(msg);
				throw new IllegalStateException(msg);
			}
		}
		Long totalEdgeCount = edgesWithProv.totalEdgeCount(); 
		if (!totalEdgeCount.equals(triplesSummarizedSoFar)){
			msg = "In edges we have a total of " + totalEdgeCount + " edges while we have summarized so far " + triplesSummarizedSoFar + " triples";
			throw new IllegalStateException(msg);
		}

		for (Long s: edgesWithProv.keySet()) {
			for (Long p: edgesWithProv.get(s).keySet()) {
				if (edgesWithProv.get(s).get(p).size() < 1) {
					msg += "In edges we have an empty set for s = " + s + " and p = " + p;
					throw new IllegalStateException(msg);
				}
			}
		}

		HashMap<Long, HashMap<Long, HashMap<Long, Long>>> edgesWithProvCountsByRep = new HashMap<>();
		for (Long s: triplesBySubject.keySet()) {
			for (Long p: triplesBySubject.get(s).keys()) {
				for (long o: triplesBySubject.get(s).get(p)) {
					Long repS = rep.get(s);
					Long repO = rep.get(o);
					if (edgesWithProv.get(repS) == null || edgesWithProv.get(repS).get(p) == null || !edgesWithProv.get(repS).get(p).contains(repO)) {
						msg += "We don't have a summary edge representing data triple " + s + " " + p + " " + o;
						throw new IllegalStateException(msg);
					}
					if (edgesWithProvCountsByRep.get(repS) == null) {
						edgesWithProvCountsByRep.put(repS, new HashMap<>());
					}
					if (edgesWithProvCountsByRep.get(repS).get(p) == null) {
						edgesWithProvCountsByRep.get(repS).put(p, new HashMap<>());
					}
					if (edgesWithProvCountsByRep.get(repS).get(p).get(repO) == null) {
						edgesWithProvCountsByRep.get(repS).get(p).put(repO, 1L);
					}
					else {
						long oldCount = edgesWithProvCountsByRep.get(repS).get(p).get(repO);
						edgesWithProvCountsByRep.get(repS).get(p).put(repO, oldCount + 1L);
					}
				}
			}
		}
		for (Long s: edgesWithProvCountsByRep.keySet()) {
			for (Long p: edgesWithProvCountsByRep.get(s).keySet()) {
				for (long o: edgesWithProvCountsByRep.get(s).get(p).keySet()) {
					long counter = edgesWithProv.getCounter(s, p, o);
					long counterByRep = edgesWithProvCountsByRep.get(s).get(p).get(o);
					if (counter != counterByRep) {
						msg += "Invalid counter for summary triple " + s + " " + p + " " + o;
						throw new IllegalStateException(msg);
					}
				}
			}
		}
	}

	protected String showCaseName(char caseNumber) {
		throw new IllegalStateException("This check is not defined here, define it in specialized classes");
	}

	protected String showTriplesByObject(){
		StringBuffer sb = new StringBuffer();
		for (Long node: triplesByObject.keySet()){
			sb.append(node).append("<~~");
			Long2LongSet edgesOfNode = triplesByObject.get(node);
			sb.append(edgesOfNode.toString()).append(" "); 
		}
		return new String(sb); 
	}

	protected String showTriplesBySubject(){
		StringBuffer sb = new StringBuffer();
		for (Long node: triplesBySubject.keySet()){
			sb.append(node).append("~~>");
			Long2LongSet edgesOfNode = triplesBySubject.get(node);
			sb.append(edgesOfNode.toString()).append(" "); 
		}
		return new String(sb); 
	}

	@Override
	public void display() {
		System.out.println("=== SUMMARY " + this.getClass().getName() + "\nSource cliques: " + sc.toString());
		System.out.println("Target cliques: " + tc.toString());
		System.out.println("Data nodes to source cliques: " + n2sc.toString());
		System.out.println("Data nodes to target cliques: " + n2tc.toString());
		System.out.println("Property to source cliques: " + p2sc.toString());
		System.out.println("Property to target cliques: " + p2tc.toString());
		System.out.println("Untyped summary nodes: " + untypedSummaryNodes.toString());
		System.out.println("Representation function: ");
		System.out.println(showRep());
		System.out.println("Summary edges: ");
		edgesWithProv.display();
		System.out.println("===");
	}
}
