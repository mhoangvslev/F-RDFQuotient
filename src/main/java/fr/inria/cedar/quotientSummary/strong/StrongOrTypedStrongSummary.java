package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.datastructures.TwoLevelLongMap;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class StrongOrTypedStrongSummary extends Summary {
	private static final Logger LOGGER = Logger.getLogger(StrongOrTypedStrongSummary.class.getName());

	Long2LongSet sc; // for each source clique ID, its source clique
	Long2LongSet tc; // for each target clique ID, its target clique
	Long2Long n2sc; // for each data node, its source clique ID
	Long2Long n2tc; // for each data node, its target clique ID
	Long2Long p2sc; // property to source clique
	Long2Long p2tc; // property to target clique
	long minCliqueID;
	protected long emptySCCount; // empty source clique number (will never change)
	protected long emptyTCCount; // empty target clique number (will never change) 
	TwoLevelLongMap untypedSummaryNodes; // source clique --> target clique --> summary node
	protected char SOURCE = 0;
	protected char TARGET = 1;
	// case classification
	// TRS: typed (thus represented), RS: untyped represented, US: untyped, unrepresented
	// similarly for O
	// UP: unknown property (no source nor target clique), RP: represented property (source and/or target clique)
	protected final char RS_RP_RO = 4;
	protected final char RS_RP_UO = 5;
	protected final char US_RP_RO = 7;
	protected final char US_RP_UO = 8;
	protected final char RS_UP_RO = 12;
	protected final char RS_UP_UO = 13;
	protected final char US_UP_RO = 15;
	protected final char US_UP_UO = 16;
	protected final char TRS_TRO = 0;
	protected final char TRS_RP_RO = 1;
	protected final char TRS_RP_UO = 2;
	protected final char RS_RP_TRO = 3;
	protected final char US_RP_TRO = 6;
	protected final char TRS_UP_RO = 9;
	protected final char TRS_UP_UO = 10;
	protected final char RS_UP_TRO = 11;
	protected final char US_UP_TRO = 14;

	// for patching edges, we really need to store the data graph in memory... :(
	HashMap<Long, Long2LongSet> triplesBySubject; // s-->{p-->{o}} s, o are data nodes
	HashMap<Long, Long2LongSet> triplesByObject; // s-->{p-->{o}}

	public StrongOrTypedStrongSummary() {
		super();
		LOGGER.setLevel(Level.INFO);
		sc = new Long2LongSet();
		tc = new Long2LongSet();
		n2sc = new Long2Long();
		n2tc = new Long2Long();
		p2sc = new Long2Long();
		p2tc = new Long2Long();
		rep = new Long2Long();
		untypedSummaryNodes = new TwoLevelLongMap();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE;
		triplesBySubject = new HashMap<>();
		triplesByObject = new HashMap<>();
	}

	protected String caseName(char c) {
		// 17 cases
		switch (c) {
			// this cases cover both TRS_UP_TRO and TRS_RP_TRO
			case TRS_TRO: { 
				return "TRS_TRO";
			}
			case TRS_RP_RO: {
				return "TRS_RO_RP";
			}
			case TRS_RP_UO: {
				return "TRS_RP_RO";
			}
			case RS_RP_TRO: {
				return "RS_RP_TRO";
			}
			case RS_RP_RO: {
				return "RS_RP_RO";
			}
			case RS_RP_UO: {
				return "RS_RP_UO";
			}
			case US_RP_TRO: {
				return "US_RP_TRO";
			}
			case US_RP_RO: {
				return "US_RP_RO";
			}
			case US_RP_UO: {
				return "US_RP_UO";
			}
			case TRS_UP_RO: {
				return "TRS_UP_RO";
			}
			case TRS_UP_UO: {
				return "TRS_UP_UO";
			}
			case RS_UP_TRO: {
				return "RS_UP_TRO";
			}
			case RS_UP_RO: {
				return "RS_UP_RO";
			}
			case RS_UP_UO: {
				return "RS_UP_UO";
			}
			case US_UP_TRO: {
				return "US_UP_TRO";
			}
			case US_UP_RO: {
				return "US_UP_RO";
			}
			case US_UP_UO: {
				return "US_UP_UO";
			}
		}
		throw new IllegalStateException("Unrecognized case " + c);
	}

	// toughest case:
	// untyped, represented object
	// represented property
	// untyped, represented subject
	protected void handleDataTriple_RS_RP_RO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// determine future cliques
		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);
		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		// determine current representatives
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		// determine future representatives: we create them but do nothing else so far
		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, targetCliqueS);
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTargetCliqueO);

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
			}
		}
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
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
		//	otherwise do nothing
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
				repsO.checkForConflicts(nodeReps);
				nodeReps.add(repsO);
			}
		}

		// now we replace just the nodes in untyped (not the cliques yet),
		// because the nodes are at the lowest (value) level
		// IMPORTANT: If we replace the cliques first, we may be wrongly
		// overwriting nodes: sc1-->tc1-->n1, sc1-->tc2-->n2, if we need to
		// replace tc1 with tc2, we will overwrite n2 and lose it!
		// serious problem!
		//for (ReplacementSpecification reps: nodeReps) {
		//	untypedSummaryNodes.applyTargetedReplacement(reps);
		//}
		// if repS and/or repO did not need to be replaced (because scs and/or
		// tco were empty), there is nothing to do at this stage, because
		// newRepS and resp. newRepO are already well inserted in untyped, on
		// their respective cliques
		//LOGGER.debug("RS_RP_RO after node but before clique replacement, untyped is: " + untypedSummaryNodes.toString());

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
		//	otherwise do nothing
		//}

		// now patching summary edges if needed; this may involve the removal of
		// a summary node that no longer represents anybody (because t.s or t.o
		// was the only node it represented, and now t.s/t.o has moved away to
		// another representative). This happens only if t.s/t.o had an empty
		// source/target clique and has split to another summary representative.
		if (!replaceForS) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repS, newRepS, t.s, TARGET), repS, newRepS, TARGET);
		}
		if (!replaceForO) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repO, newRepO, t.o, SOURCE), repO, newRepO, SOURCE);
		}

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
	protected void handleDataTriple_RS_UP_RO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// sourceCliqueP is null, targetCliqueP is null
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = cliqueFusionResult(sourceCliqueS, sourceCliqueP, SOURCE);
		Long newTargetCliqueO = cliqueFusionResult(targetCliqueO, targetCliqueP, TARGET);

		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);

		Long newRepS = getOrCreateSummaryNode(newSourceCliqueS, targetCliqueS);
		Long newRepO = getOrCreateSummaryNode(sourceCliqueO, newTargetCliqueO);

		boolean replaceForS = true;
		boolean replaceForO = true;
		if (sourceCliqueS.equals(getEmptySourceCliqueID())) {
			if (rep.getInverse(repS).size() > 1) {
				replaceForS = false;
			}
		}
		if (targetCliqueO.equals(getEmptyTargetCliqueID())) {
			if (rep.getInverse(repO).size() > 1) {
				replaceForO = false;
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
				repsO.checkForConflicts(nodeReps);
				nodeReps.add(repsO);
			}
		}

		//for (ReplacementSpecification reps: nodeReps) {
		//	untypedSummaryNodes.applyTargetedReplacement(reps);
		//}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}
		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}

		if (!replaceForS) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repS, newRepS, t.s, TARGET), repS, newRepS, TARGET);
		}
		if (!replaceForO) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repO, newRepO, t.o, SOURCE), repO, newRepO, SOURCE);
		}

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
	protected void handleDataTriple_US_RP_RO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
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

		//for (ReplacementSpecification reps: nodeReps) {
		//	untypedSummaryNodes.applyTargetedReplacement(reps);
		//}

		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}

		if (!replaceForO) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repO, newRepO, t.o, SOURCE), repO, newRepO, SOURCE);
		}

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
	protected void handleDataTriple_RS_RP_UO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
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

		//for (ReplacementSpecification reps: nodeReps) {
		//	untypedSummaryNodes.applyTargetedReplacement(reps);
		//}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}

		if (!replaceForS) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repS, newRepS, t.s, TARGET), repS, newRepS, TARGET);
		}

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
	protected void handleDataTriple_US_UP_RO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
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

		//for (ReplacementSpecification reps: nodeReps) {
		//	untypedSummaryNodes.applyTargetedReplacement(reps);
		//}

		if (replaceForO) {
			computeAndApplyCliqueReplacements(targetCliqueO, targetCliqueP, newTargetCliqueO, TARGET, nodeReps);
		}

		if (!replaceForO) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repO, newRepO, t.o, SOURCE), repO, newRepO, SOURCE);
		}

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
	protected void handleDataTriple_RS_UP_UO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
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

		//for (ReplacementSpecification reps: nodeReps) {
		//	untypedSummaryNodes.applyTargetedReplacement(reps);
		//}

		if (replaceForS) {
			computeAndApplyCliqueReplacements(sourceCliqueS, sourceCliqueP, newSourceCliqueS, SOURCE, nodeReps);
		}

		if (!replaceForS) {
			updateEdgesWithDistribution(distributeSummaryEdgesThroughCounts(repS, newRepS, t.s, TARGET), repS, newRepS, TARGET);
		}

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
	protected void handleDataTriple_US_RP_UO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long newSourceCliqueS = sourceCliqueP;
		Long newTargetCliqueO = targetCliqueP;

		Long repS = getOrCreateSummaryNode(sourceCliqueP, getEmptyTargetCliqueID());
		Long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), targetCliqueP);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);
		n2sc.put(t.o, getEmptySourceCliqueID());
		n2tc.put(t.s, getEmptyTargetCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	// untyped, unrepresented subject
	// unknown property
	// untyped, unrepresented object
	// in this case the subject should be represented based on the (newly
	// created) source clique of P and the empty target clique and the object
	// should be represented based on the empty source clique and the (newly
	// created) target source clique of P
	protected void handleDataTriple_US_UP_UO(Triple t, Long sourceCliqueS, Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		sourceCliqueP = makeAndAddNewSourceClique(t.p);
		targetCliqueP = makeAndAddNewTargetClique(t.p);

		Long newSourceCliqueS = sourceCliqueP;
		Long newTargetCliqueO = targetCliqueP;

		Long repS = getOrCreateSummaryNode(newSourceCliqueS, getEmptyTargetCliqueID());
		Long repO = getOrCreateSummaryNode(getEmptySourceCliqueID(), newTargetCliqueO);

		Long newRepS = repS;
		Long newRepO = repO;

		rep.put(t.s, newRepS);
		rep.put(t.o, newRepO);

		n2sc.put(t.s, newSourceCliqueS);
		n2tc.put(t.o, newTargetCliqueO);
		n2sc.put(t.o, getEmptySourceCliqueID());
		n2tc.put(t.s, getEmptyTargetCliqueID());

		edgesWithProv.addTriple(newRepS, t.p, newRepO);
	}

	/**************************** auxiliary methods below **************************/ 

	/**
	 * Read-only
	 * 
	 * Clique IDs are negative. So, the higher value is the one created first. We will keep the higher value and replace the
	 * lower value with this higher value.
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
			if (c1empty){
				return c2;
			}
			if (c2empty){
				return c1;
			}
			if (c1 > c2){// we know none is empty
				return c1;
			}
			if (c2 > c1){// we know none is empty
				return c2;
			}
		}
		else if (code == TARGET){
			boolean c1empty = c1.equals(this.getEmptyTargetCliqueID());
			boolean c2empty = c2.equals(this.getEmptyTargetCliqueID());
			//LOGGER.debug("FuseCliquesIntoCreatedFirst TARGET " + c1 + (c1empty?" (empty)":"") + " " + c2 + (c2empty?" (empty)":""));

			if (c1empty){
				return c2;
			}
			if (c2empty){
				return c1;
			}
			if (c1 > c2){// we know none is empty
				return c1;
			}
			if (c2 > c1){// we know none is empty
				return c2;
			}
		}
		else throw new IllegalStateException("Unknown code!");
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
				//LOGGER.debug("CLIQUE REPLACE IN UNTYPED, P2, N2 TARGET: that is " + 
				//		this.showCliqueAsString(tc.get(clique1)) + 
				//		" with " + this.showCliqueAsString(tc.get(clique2))); 
				toBeReplaced.add(clique1); 
			}
			if (!clique2.equals(this.getEmptyTargetCliqueID()) && (!clique2.equals(cliqueNew))){
				//LOGGER.debug("CLIQUE REPLACE IN UNTYPED, P2, N2 TARGET: we'll replace " + clique2 + " with " + cliqueNew);
				toBeReplaced.add(clique2); 
			}
		}
		// apply: 
		for (Long oldClique: toBeReplaced){
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
		if (oldClique.equals(newClique)){
			return; 
		}
		if (param == TARGET){ // we find all occurrences of the old cliques (in the 2nd level), remove them and replace with the new clique
			// if this leads to overwriting a node in a different way, throw an error
			HashMap<Long, Long> replacements = untypedSummaryNodes.replaceAt2ndLevel(oldClique, newClique);
			// source and target cliques don't matter any more, don't check for conflicts in replacements
			for (Long repl: replacements.keySet()) {
				ReplacementSpecification reps = new ReplacementSpecification(0L, 0L, repl, replacements.get(repl));
				nodeReps.add(reps);
			}
		}
		else if (param == SOURCE){
			HashMap<Long, Long> replacements = untypedSummaryNodes.replaceAt1stLevel(oldClique, newClique);
			// source and target cliques don't matter any more, don't check for conflicts in replacements
			for (Long repl: replacements.keySet()) {
				ReplacementSpecification reps = new ReplacementSpecification(0L, 0L, repl, replacements.get(repl));
				nodeReps.add(reps);
			}
		}
	}

	protected void replaceCliqueInP2(Long oldClique, Long newClique, char param){
		if (param == SOURCE){
			this.p2sc.replaceValue(oldClique, newClique);
		}
		else if (param == TARGET){
			this.p2tc.replaceValue(oldClique, newClique);
		}
	}
	protected void replaceCliqueInN2(Long oldClique, Long newClique, char param){
		if (param == SOURCE){
			this.n2sc.replaceValue(oldClique, newClique);
		}
		else if (param == TARGET){
			this.n2tc.replaceValue(oldClique, newClique);
		}
	}

	// returns on oldRep, the edges to remove, 
	// and on newRep, the edges to create for it
	protected HashMap<Long, Long2LongSet> distributeSummaryEdgesThroughCounts(long oldRep, long newRep, long dataNode, char param){
		HashMap<Long, Long2LongSet> res = new HashMap<>();
		Long2LongSet summEdgesToAddOnNewRep = new Long2LongSet(); 
		Long2LongSet summEdgesToRemoveOnOldRep = new Long2LongSet(); 
		res.put(oldRep, summEdgesToRemoveOnOldRep);
		res.put(newRep, summEdgesToAddOnNewRep);

		if (param == SOURCE){ // we must distribute edges outgoing from oldRep and newRep, which now represents dataNode
			//LOGGER.debug("DISTRIBUTING OUTGOING SUMMARY EDGES of summary node " + oldRep + " with the new summary node " + newRep + " due to " + dataNode);

			// traverse the data edges outgoing node and, for each of them:
			// - mark the corresponding summary edge as needing to be added to the new summary node; 
			// - decrease the counter of the corresponding summary edge starting from the old node, and if the counter is 0, mark that edge for removal
			Long2LongSet dataEdgesFromNode = this.triplesBySubject.get(dataNode); 
			if (dataEdgesFromNode != null){
				for (Long p: dataEdgesFromNode.keys()){
					for (Long o: dataEdgesFromNode.get(p)){
						// data edge dataNode--p-->o
						//LOGGER.debug("For " +  dataNode  + "--" + p + "-->" + o + ")");
						Long repO = rep.get(o); // this supposes that rep(o) has not been updated yet
						if (repO != null){ // represented by summary edge oldRep--p-->repO
							summEdgesToAddOnNewRep.add(p, repO); 
							//LOGGER.debug("Seeking repr. counter  of: oldRep (" + oldRep + ")--" + p + "-->repO(" + repO + ")");
							//edgesWithProv.display();
							Long edgeCountLeft = edgesWithProv.getCounter(oldRep, p, repO) - 1; //was: o instead of repO
							if (edgeCountLeft == 0){
								summEdgesToRemoveOnOldRep.add(p, repO);
							}
						}
					}
				}
			}
		}
		else if (param == TARGET){ // we must distribute edges incoming in oldRep and/or newRep, which now represents node
			//LOGGER.debug("DISTRIBUTING INCOMING SUMMARY EDGES of " + oldRep + " with the new " + newRep + " due to " + node);
			//LOGGER.debug("DISTRIBUTING INCOMING SUMMARY EDGES: incoming edges are " + displayTriplesByObject());

			Long2LongSet dataEdgesToNode = this.triplesByObject.get(dataNode); 
			if (dataEdgesToNode != null){
				//LOGGER.debug("DISTRIBUTING INCOMING SUMMARY EDGES: " + node + " has " + edgesToNode.keys().size() + " distinct incoming properties");
				for (Long p: dataEdgesToNode.keys()){
					//LOGGER.debug("DISTRIBUTING INCOMING SUMMARY EDGES: " + node + " has " + p + " incoming edge(s)");  
					for (Long s: dataEdgesToNode.get(p)){
						// data edge s--p-->node
						Long repS = rep.get(s); 
						//LOGGER.debug("DISTRIBUTING INCOMING SUMMARY EDGES " + node + " had a " + p + " edge from " + s + " and representative of " + s + " is " + repS); 
						if (repS != null){ // represented by summary edge: repS --p-->oldRep); 
							summEdgesToAddOnNewRep.add(p, repS); 
							Long edgeCountLeft = edgesWithProv.getCounter(repS,  p, oldRep) - 1; // was: s instead of repS
							if (edgeCountLeft == 0){
								summEdgesToRemoveOnOldRep.add(p, repS); 
							}
							//LOGGER.debug("DISTRIBUTING INCOMING SUMMARY EDGES adding to new node " + newRep + " a " + p + " edge from " + repS); 
						}
					}
				}
			}
		}
		return res; 
	}

	// on rep there are edges to remove
	// on newRep there are edges to add
	protected void updateEdgesWithDistribution(HashMap<Long, Long2LongSet> edgesToAddAndRemove, Long rep, Long newRep, char param){
		if (param == SOURCE){
			addOutgoingEdges(newRep, edgesToAddAndRemove.get(newRep));
			removeOutgoingEdges(rep, edgesToAddAndRemove.get(rep));
		}
		else if (param == TARGET){
			addIncomingEdges(newRep, edgesToAddAndRemove.get(newRep));
			removeIncomingEdges(rep, edgesToAddAndRemove.get(rep));
		}
	}

	protected boolean addingPropertyToSWillCauseASplit(Long repS, Long sourceCliqueS, Long s){
		if (sourceCliqueS.equals(this.getEmptySourceCliqueID())){ // due to the current triple, newSCS for sure is not empty.
			TreeSet<Long> inverseRep = rep.getInverse(repS); 
			if (inverseRep.size()  > 1){ // several nodes shared this representative: they all had empty sourceCliqueID
				// and is is the only one for which this situation changes. Thus, we will split.
				return true; 
			}
			if (inverseRep.size() == 1){ // there was only one node with this representative, and empty sourceClique.
				for (Long l: inverseRep){
					if (l.equals(s)){ // but it is exactly 
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
		if (node != null){
			return node; 
		}
		else{
			node = getNextSummaryNode(); // from the Summary class; 
			untypedSummaryNodes.add(sourceClique, targetClique, node);
			return node;
		}
	}

	protected Long getEmptySourceCliqueID() {
		Long res;
		if (this.emptySCCount == Long.MAX_VALUE) { // the empty source clique has not been created yet
			TreeSet<Long> emptySC = new TreeSet<>();
			res = minCliqueID; // we invent a new source clique
			this.emptySCCount = minCliqueID;
			// add this to sc
			sc.put(minCliqueID, emptySC);
			minCliqueID--;
		}
		else // the empty source clique has already been created, just copy it 
			res = this.emptySCCount;
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
		if (this.emptyTCCount == Long.MAX_VALUE) { // the empty source clique has not been created yet
			TreeSet<Long> emptyTC = new TreeSet<>();
			res = minCliqueID; // we invent a new source clique
			//LOGGER.debug("ooooo> Initialized the empty target clique at: " + res);
			this.emptyTCCount = minCliqueID;
			// add this to tc
			tc.put(minCliqueID, emptyTC);
			minCliqueID--;
		}
		else // the empty source clique has already been created, just copy it 
			res = this.emptyTCCount;
		return res;
	}

	protected void cacheTriple(Triple t){
		// cache it by the subject:
		Long2LongSet edgesOfS = this.triplesBySubject.get(t.s);
		if (edgesOfS == null){
			edgesOfS = new Long2LongSet();
			this.triplesBySubject.put(t.s, edgesOfS);
		}
		edgesOfS.add(t.p, t.o);
		//LOGGER.debug("CACHED BY SUBJECT " + t.s + " on " + t.p + ": " + t.o + " resulting in " + displayTriplesBySubject());
		// cache it by the object:
		Long2LongSet edgesOfO = this.triplesByObject.get(t.s);
		if (edgesOfO == null){
			edgesOfO = new Long2LongSet();
			this.triplesByObject.put(t.o, edgesOfO);
		}
		edgesOfO.add(t.p, t.s);
		//LOGGER.debug("CACHED BY OBJECT " + t.o + " on " + t.p + ": " + t.s + " resulting in " + displayTriplesByObject());
		//displayTriplesByObject();
	}

	protected void consistencyChecks(){
		String msg;
		for (Long dataNode: n2sc.getKeys()){
			//LOGGER.debug("Checking from data node: " + dataNode);
			Long nodeRep = rep.get(dataNode);
			if (nodeRep == null){
				msg = "Unrepresented data node " + dataNode;
				//LOGGER.debug(msg);
				throw new IllegalStateException(msg);
			}
			Long nsc = n2sc.get(dataNode);
			Long ntc = n2tc.get(dataNode);
			HashMap<Long, Long> tc2Nodes = untypedSummaryNodes.get(nsc);
			if (tc2Nodes == null){
				msg = "untypedSummaryNodes has no (target clique, node) pairs on source clique " + nsc + " of node " + dataNode + "(" + RDF2SQLEncoding.dictionaryDecode(dataNode) + ")"; 
				//LOGGER.debug(msg);
				throw new IllegalStateException(msg);
			}
			Long tcn = tc2Nodes.get(ntc);
			if (tcn == null){
				msg = "No node found on source clique " + nsc + " for target clique " + ntc + " of data node " + dataNode + " or (" + RDF2SQLEncoding.dictionaryDecode(dataNode) + ") while its representative is " + nodeRep;
				//LOGGER.debug(msg);
				throw new IllegalStateException(msg);
			}
			if (!(tcn.equals(nodeRep))){
				msg = nsc + "=>" + ntc + ": " + tcn + " while the representative of " + dataNode + " is " + nodeRep;
				//LOGGER.debug(msg);
				throw new IllegalStateException(msg);
			}
		}
		Long totalEdgeCount = edgesWithProv.totalEdgeCount(); 
		if (!totalEdgeCount.equals(dataTriplesSummarizedSoFar)){
			msg = "In edges we have a total of " + totalEdgeCount + " edges while we have summarized so far " + dataTriplesSummarizedSoFar + " data triples";
			throw new IllegalStateException(msg);
		}
	}

	protected String displayTriplesByObject(){
		StringBuffer sb = new StringBuffer();
		for (Long node: triplesByObject.keySet()){
			sb.append(node).append("<~~");
			Long2LongSet edgesOfNode = triplesByObject.get(node);
			sb.append(edgesOfNode.toString()).append(" "); 
		}
		return new String(sb); 
	}

	protected String displayTriplesBySubject(){
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
		LOGGER.debug("=== SUMMARY " + this.getClass().getName() + "\nSource cliques: " + sc.toString());
		LOGGER.debug("Target cliques: " + tc.toString());
		LOGGER.debug("Data nodes to source cliques: " + n2sc.toString());
		LOGGER.debug("Data nodes to target cliques: " + n2tc.toString());
		LOGGER.debug("Property to source cliques: " + p2sc.toString());
		LOGGER.debug("Property to target cliques: " + p2tc.toString());
		LOGGER.debug("Untyped summary nodes: " + untypedSummaryNodes.toString());
		LOGGER.debug("Representation function: ");
		LOGGER.debug(showRep());
		LOGGER.debug("Summary edges: ");
		edgesWithProv.display();
		LOGGER.debug("===");
	}
}
