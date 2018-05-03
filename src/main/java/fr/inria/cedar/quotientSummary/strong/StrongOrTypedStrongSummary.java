package fr.inria.cedar.quotientSummary.strong;

import java.util.ArrayList;
import java.util.HashMap;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongList;
import fr.inria.cedar.quotientSummary.datastructures.Triple;

public class StrongOrTypedStrongSummary extends Summary {
	Long2LongList sc; // for each source clique ID,  a source clique
	Long2LongList tc; // for each target clique ID,  its target clique
	Long2Long n2sc; // for each node, its source clique ID
	Long2Long n2tc; // for each node, its target clique ID
	Long2Long p2sc; // property to source clique
	Long2Long p2tc; // property to target clique

	long minCliqueID;
	
	protected long numberOfDataTriplesRead; 
	protected long numberOfTypeTriplesRead; 

	protected long emptySCCount; // le numéro de la clique vide
	protected long emptyTCCount; // le numéro de la clique vide
	
	HashMap<Long, HashMap<Long, Long>> untypedSummaryNodes; // source clique --> target clique --> summary node

	protected char TARGET=1;
	protected char SOURCE=0;

	// case classification
	// T: typed, U: untyped (apply to S and O)
	// R: already represented, N: not already represented (apply to S, P, O)
	protected final char US_RS_UO_RO_RP = 4;
	protected final char US_RS_UO_NO_RP = 5; 
	protected final char US_NS_UO_RO_RP = 7;
	protected final char US_NS_UO_NO_RP = 8;
	protected final char US_RS_UO_RO_NP = 12;
	protected final char US_RS_UO_NO_NP = 13; 
	protected final char US_NS_UO_RO_NP = 15;
	protected final char US_NS_UO_NO_NP = 16;
	
	public StrongOrTypedStrongSummary(){
		super(); 
		sc = new Long2LongList();
		tc = new Long2LongList();
		n2sc = new Long2Long();
		n2tc = new Long2Long();
		p2sc = new Long2Long();
		p2tc = new Long2Long();
		rep = new Long2Long();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE; 
		numberOfDataTriplesRead=0;
		numberOfTypeTriplesRead=0; 
	}
	
	/**
	 * Creates a new source clique with just p; updates sc and p2sc
	 * @param p
	 * @return
	 */
	protected Long makeAndAddNewSourceClique(Long p){
		Long res = new Long(this.minCliqueID);
		ArrayList<Long> actualSourceClique = new ArrayList<>(); 
		actualSourceClique.add(p);
		sc.put(res, actualSourceClique);
		p2sc.put(p,  res);
		Debugger.log("Added the new source clique for: " + res + " with property " + p);
		minCliqueID --;
		return res; 
	}

	/**
	 * Initializes a target clique for property p
	 * Also records the association between p and this target clique in p2c and tc
	 * @param p
	 * @return
	 */
	protected Long makeAndAddNewTargetClique(Long p){		
		Long targetCliqueID = new Long(this.minCliqueID);
		ArrayList<Long> actualTargetClique = new ArrayList<>(); 
		actualTargetClique.add(p);
		tc.put(targetCliqueID, actualTargetClique);
		p2tc.put(p,  targetCliqueID);
		//Debugger.log("Added the new target clique " + targetCliqueID + " which is [" + p + "]");
		minCliqueID --;
		return targetCliqueID; 
	}
	

	protected Long getEmptyTargetCliqueID() {
		Long res; 
		if (this.emptyTCCount == Long.MAX_VALUE){ // the empty source clique has not been created yet
			ArrayList<Long> emptyTC = new ArrayList<>();
			res = minCliqueID; // we invent a new source clique
			Debugger.log("ooooo> Initialized the empty target clique at: " + res);
			this.emptyTCCount = minCliqueID; 
			// add this to tc
			tc.put(minCliqueID, emptyTC);
			minCliqueID--;
		}
		else{ // the empty source clique has already been created, just copy it 
			res = this.emptyTCCount; 
		}	
		return res; 
	}

	/**
	 * Clique IDs are negative. So, the higher value is the one created first. We will keep the higher value and replace the 
	 * lower value with this higher value.
	 * An exception is made if one of the cliques is the empty clique: in this case, fusion systematically
	 * takes the other clique.
	 * Updates sc, tc, n2tc, p2sc, p2tc, summary (edges)
	 */
	protected Long fuseCliquesIntoCreatedFirst(Long c1, Long c2, char code) {
		if (code == SOURCE){
			// if c2 is the empty source clique OR (c2 was created before c1), replace c2 with c1
			if ((c1 > c2) || (c2.equals(this.getEmptySourceCliqueID()))){
				// this method treats its first parameter as "old" and the second as "new" 
				fuseSourceCliques(c2, c1);
				return c1; 
			}
			else{
				if ((c2 > c1) || (c1.equals(this.getEmptySourceCliqueID()))){
					fuseSourceCliques(c1, c2);
					return c2; 
				}
			}
		}
		else{
			if (code == TARGET){
				if ((c1 > c2) || (c2.equals(this.getEmptyTargetCliqueID()))){
					// this method treats its first parameter as "old" and the second as "new" 
					fuseTargetCliques(c2, c1);
					return c1; 
				}
				else{
					if ((c2 > c1) || (c1.equals(this.getEmptyTargetCliqueID()))){
						fuseTargetCliques(c1, c2);
						return c2; 
					}
				}
			}
			else{
				throw new Error("Unknown code!"); 
			}
		}
		return c1; 
	}

	
	// untyped subject, already represented: thus, it has a source clique that p must join
	// untyped object, not represented: we assign it target clique {p} and empty source clique
	// unknown property
	protected void handleDataTriple_US_RS_UO_NO_NP(Triple t, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// add p to the source clique of t.s; the clique ID changes if it was the empty source clique:
		long ssc = n2sc.get(t.s); 
		Long newSourceClique = addPropertyToSourceClique(t.p, ssc); 
		n2sc.put(t.s, newSourceClique);
		long ptc = makeAndAddNewTargetClique(t.p); 
		long repO = getOrCreateSummaryNode(this.getEmptySourceCliqueID(), ptc);
		rep.put(t.o, repO);
		n2sc.put(t.o, this.getEmptySourceCliqueID());
		n2tc.put(t.o, ptc);
		this.addTriple(rep.get(t.s), t.p, repO);
	}

	// untyped, represented subject: it has a source clique, which needs to gain p
	// untyped, represented object: it has a target clique, which needs to gain p
	// unknown property: it should be bound to these modified cliques
	protected void handleDataTriple_US_RS_UO_RO_NP(Triple t, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long newSourceCliqueS = 	addPropertyToSourceClique(t.p, sourceCliqueS);
		n2sc.put(t.s, newSourceCliqueS);
		Long newTargetCliqueO = addPropertyToTargetClique(t.p, targetCliqueO);
		n2tc.put(t.o, newTargetCliqueO);
		// neither the representatives nor the source, target cliques of t.s and t.o change
		this.addTriple(rep.get(t.s), t.p, rep.get(t.o));
	}
	protected void helper_UO_NO_RP(Triple t, Long sourceCliqueP, Long targetCliqueP) {
		// represent t.o as empty source clique + target clique of p
		Long emptySourceCliqueO = getEmptySourceCliqueID(); 
		//Debugger.log("helper_UO_NO_RP To represent " + t.o + ", looking for the node of empty source clique " + emptySourceCliqueO + " and target clique " + targetCliqueP); 
		Long repO = getOrCreateSummaryNode(emptySourceCliqueO, targetCliqueP);
		//Debugger.log("helper_UO_NO_RP Found: " + repO); 
		rep.put(t.o, repO);
		n2tc.put(t.o, targetCliqueP);
		n2sc.put(t.o, emptySourceCliqueO);
	}
	
	// untyped, represented subject
	// untyped, unrepresented object
	// known property
	protected void handleDataTriple_US_RS_UO_NO_RP(Triple t, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		helper_UO_NO_RP(t, sourceCliqueP, targetCliqueP); 
		// see what we do with t.s:
		Long newRepS = rep.get(t.s); 
		if (sourceCliqueS != sourceCliqueP) { // the source clique of P was not that of S
			//System.out.println("Source clique of " + t.s + "=" + sourceCliqueS + " differs from that of " + t.p + " which is " + sourceCliqueP ); 
			Long fusedCliqueS = fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
			//System.out.println("Fused them in into the one created first: " + fusedCliqueS); 
			newRepS = getOrCreateSummaryNode(fusedCliqueS, targetCliqueS); 
			//System.out.println("This leads the new representative of " + t.s + ": " + newRepS);
			rep.put(t.s, newRepS);
			n2tc.put(newRepS, targetCliqueS);
			n2sc.put(newRepS, fusedCliqueS);
		}
		else { // no need to do anything, the source clique of S is already that of p 
		}
		// adding triple:
		this.addTriple(newRepS, t.p, rep.get(t.o));
	}
	// untyped, unrepresented subject
	// untyped, represented object
	// known property
		protected void handleDataTriple_US_NS_UO_RO_RP(Triple t, Long sourceCliqueS,
				Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
			helper_US_NS_RP(t, sourceCliqueP, targetCliqueP); 
			// see what to do with t.o
			Long newRepO = rep.get(t.o); 
			if (targetCliqueO != targetCliqueP) {
				// this call updates n2tc, tc, existing summary edges
				Long fusedTargetPO = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET); 
				newRepO = getOrCreateSummaryNode(sourceCliqueO, fusedTargetPO); 
				n2tc.put(newRepO, fusedTargetPO); 
				n2sc.put(newRepO, sourceCliqueO);
				rep.put(t.o, newRepO);
			}
			else { // p is already known to be in the target clique of o (o and p have the same tc) 
				// no need to change the representative of o
			}
			// adding triple: 
			this.addTriple(rep.get(t.s), t.p, newRepO); 
		}

		/**
		 * Helper method which represents an (untyped) unrepresented subject 
		 * based on its known property p
		 * @param triple 
		 * @param sourceCliqueP
		 * @param targetCliqueP
		 */
		protected void helper_US_NS_RP(Triple t, Long sourceCliqueP, Long targetCliqueP) {
			// represent t.s as empty target clique + source clique of p
			Long emptyTargetCliqueS = getEmptyTargetCliqueID(); 
			Long repS = getOrCreateSummaryNode(sourceCliqueP, emptyTargetCliqueS);
			rep.put(t.s, repS);
			n2tc.put(t.s, emptyTargetCliqueS);
			n2sc.put(t.s, sourceCliqueP);
		}
		// untyped, unrepresented subject
		// untyped, represented object (this has appeared in data triples before)
		// unknown property
		protected void handleDataTriple_US_NS_UO_RO_NP(Triple t, Long sourceCliqueS,
				Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
			// create p's source clique
			long psc = makeAndAddNewSourceClique(t.p); 
			// represent s by the source clique of P and the empty target clique:
			long emptyTargetCliqueID = getEmptyTargetCliqueID();
			long repS = getOrCreateSummaryNode(psc, emptyTargetCliqueID);
			rep.put(t.s, repS);
			// o is already represented but its target clique did not include p (given that p was not known)
			// we need to add p to this target clique
			// then adjust t.o's representation
			Long newTargetCliqueO = addPropertyToTargetClique(t.p, targetCliqueO); 
			n2tc.put(t.o, newTargetCliqueO);
			this.addTriple(repS, t.p, rep.get(t.o));
			
		}
		// untyped, non represented subject
		// untyped, non represented object
		// unknown property
		protected void handleDataTriple_US_NS_UO_NO_NP(Triple t, Long sourceCliqueS,
				Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
			long psc = makeAndAddNewSourceClique(t.p); 
			long ptc = makeAndAddNewTargetClique(t.p); 
			long emptyTargetCliqueID = getEmptyTargetCliqueID();
			long repS = getOrCreateSummaryNode(psc, emptyTargetCliqueID);
			n2sc.put(t.s, psc);
			n2tc.put(t.s, emptyTargetCliqueID);
			rep.put(t.s, repS);
			long emptySourceCliqueID = getEmptySourceCliqueID();
			long repO = getOrCreateSummaryNode(emptySourceCliqueID, ptc);
			n2sc.put(t.o, emptySourceCliqueID);
			n2tc.put(t.o, ptc);
			rep.put(t.o, repO); 
			this.addTriple(repS, t.p, repO);
		}
		
		// untyped, unrepresented subject
		// untyped, unrepresented object
		// known property
		protected void handleDataTriple_US_NS_UO_NO_RP(Triple t,  Long sourceCliqueS,
				Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
			helper_US_NS_RP(t, sourceCliqueP, targetCliqueP);
			// represent t.o as target clique of p + empty source clique
			Long emptySourceCliqueO = getEmptySourceCliqueID();
			Long repO = getOrCreateSummaryNode(emptySourceCliqueO, targetCliqueP); 
			rep.put(t.o, repO);
			n2tc.put(t.o, targetCliqueP);
			n2sc.put(t.o, emptySourceCliqueO);
			// add triple:
			this.addTriple(rep.get(t.s), t.p, repO);
		}
	// toughest case: 
	// untyped, represented object
	// untyped, represented subject
	// represented property 
	protected void handleDataTriple_US_RS_UO_RO_RP(Triple t, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long newSCs = sourceCliqueS;
		Long newTCo = targetCliqueO; 
		Long newRepS = repS;
		Long newRepO = repO; 
		boolean sRepChanged = false; 
		boolean oRepChanged = false; 
		if (sourceCliqueS != sourceCliqueP) {
			sRepChanged = true; 
			newSCs = fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
		}
		if (targetCliqueO != targetCliqueP) {
			oRepChanged = true;
			//Debugger.log("Object node " + t.o + " has the target clique:");
			showClique(tc.get(targetCliqueO));
			//Debugger.log("while the property " + t.p + " has the target clique: "); 
			showClique(tc.get(targetCliqueP));
			//Debugger.log("Fusing them into the one created first"); 
			newTCo = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET); 
		}
		if (sRepChanged) {
			newRepS = getOrCreateSummaryNode(newSCs, targetCliqueS);
			n2tc.put(newRepS, targetCliqueS);
			n2sc.put(newRepS, newSCs);
			rep.put(t.s, newRepS);
		}
		if (oRepChanged) {
			newRepO = getOrCreateSummaryNode(sourceCliqueO, newTCo); 
			n2tc.put(newRepO, newTCo);
			n2sc.put(newRepO, sourceCliqueO);
			rep.put(t.o, newRepO);
		}
		// adding triple:
		this.addTriple(newRepS, t.p, newRepO);
	}

	
	/**
	 * Updates sc, n2sc, untypedSummaryNodes, summary (edges) 
	 * @param sourceCliqueOld
	 * @param sourceCliqueNew
	 */
	protected void fuseSourceCliques(Long sourceCliqueOld, Long sourceCliqueNew){
		Debugger.log("FuseSourceCliques:  " + sourceCliqueOld + " becomes " + sourceCliqueNew);
		//display();
		// add properties of target clique of o, to those of the target clique of p
		ArrayList<Long> realSCNew = this.sc.get(sourceCliqueNew);
		ArrayList<Long> realSCOld = this.sc.get(sourceCliqueOld);

		if (realSCOld != null){
			for (Long l: realSCOld){
				realSCNew.add(l); 
			}
		}
		this.sc.remove(sourceCliqueOld);
		// update n2sc to inform all the nodes mapped to sourceCliqueO, to map now to sourceCliqueP
		this.n2sc.replaceValue(sourceCliqueOld, sourceCliqueNew);
		replaceCliqueInSummary(sourceCliqueOld, sourceCliqueNew);

		this.p2sc.replaceValue(sourceCliqueOld, sourceCliqueNew);
		Debugger.log("After the fusion: ");
		//display();
	}

	/**
	 * Updates tc, n2tc, p2tc, summary (edges)
	 * @param targetCliqueOld
	 * @param targetCliqueNew
	 */
	protected void fuseTargetCliques(Long targetCliqueOld, Long targetCliqueNew){
		Debugger.log("FuseTargetCliques: " + targetCliqueOld + " into " + targetCliqueNew);
		
		// Do not display here as this requires rep to be fully filled and rep cannot be filled for new nodes before the fusion. So some nodes may be missing.
		//display();
		// then make targetCliqueO the same as targetCliqueP (keep smaller)
		// add properties of target clique of o, to those of the target clique of p
		ArrayList<Long> realTCNew = this.tc.get(targetCliqueNew);
		ArrayList<Long> realTCOld = this.tc.get(targetCliqueOld);

		if (realTCOld != null){
			for (Long l: realTCOld){
				realTCNew.add(l); 
			}
		}
		this.tc.remove(targetCliqueOld);
		// update n2tc to inform all the nodes mapped to targetCliqueO, to map now to targetCliqueP
		this.n2tc.replaceValue(targetCliqueOld, targetCliqueNew);
		replaceCliqueInSummary(targetCliqueOld, targetCliqueNew);
		this.p2tc.replaceValue(targetCliqueOld, targetCliqueNew);
		//Debugger.log("After the fusion: ");
		//display();
	}

	/**
	 * Adds p to the source clique indicated by sourceCliqueID; updates p2sc and sc
	 * @param p
	 * @param sourceCliqueID
	 */
	protected Long addPropertyToSourceClique(Long p, Long sourceCliqueID) {
		if (sourceCliqueID.equals(this.getEmptySourceCliqueID())) {
			// we cannot add p to the empty source clique because that one
			// needs to be unique and just represent the empty clique, throughout
			Long newSourceCliqueID = this.makeAndAddNewSourceClique(p);
			p2sc.put(p, newSourceCliqueID);
			return newSourceCliqueID; 
		}
		else {
			p2sc.put(p, sourceCliqueID);
			if (!(sc.get(sourceCliqueID).contains(p))){
				sc.get(sourceCliqueID).add(p);
			}	
			return sourceCliqueID; 
			//System.out.println("New source clique " + sourceCliqueID);
			//showClique(sc.get(sourceCliqueID)); 
		}
	}

	/**
	 * Adds p to the target clique indicated by targetCliqueID
	 * @param p
	 * @param targetCliqueID
	 */
	protected Long addPropertyToTargetClique(Long p, Long targetCliqueID) {
		if (targetCliqueID.equals(this.getEmptyTargetCliqueID())) {
			// existingTC was empty. In this case, we need to create a new
			// target clique and put just p inside, and return that one.
			Long newTargetClique = this.makeAndAddNewTargetClique(p);
			p2tc.put(p, newTargetClique);
			return newTargetClique; 
		}
		else {// existingTC was not empty. It suffices to add p to it. 
			if (!(tc.get(targetCliqueID).contains(p))){
				tc.get(targetCliqueID).add(p);
			}
			p2tc.put(p, targetCliqueID);
			//System.out.println("New target clique " + targetCliqueID);
			//showClique(tc.get(targetCliqueID)); 
			return targetCliqueID; 
		}
	}


	/**
	 * Replaces a clique ID with another cliqueID in the summary
	 * 
	 * Modifies untypedSummaryNodes and the summary edges
	 * 
	 * For simplicity (and in a somehow violent manner), it replaces either a source clique or a target clique
	 * Thus it is important that oldCliqueID is not allowed to match both a source clique and a target clique, 
	 * because we usually only want to replace one.
	 * @param oldCliqueID
	 * @param newCliqueID
	 */
	protected void replaceCliqueInSummary(Long oldCliqueID, Long newCliqueID) {		
		// replace in rep:
		// first, replace in second-level hash, if it occurs as a target clique:
		if (oldCliqueID > newCliqueID){
			throw new Error("Wrong replacement");
		}
		Debugger.log("Trying to replace " + oldCliqueID + " with " + newCliqueID + " in summary");
		for (Long l: this.untypedSummaryNodes.keySet()){
			HashMap<Long, Long> tcToNodes = untypedSummaryNodes.get(l);
			Long nodeOldTC = tcToNodes.get(oldCliqueID);
			Long nodeNewTC = tcToNodes.get(newCliqueID);
			// replace old with new; remove entry for old:
			if ((nodeOldTC != null) && (nodeNewTC!= null)){
				replaceInSummary(nodeOldTC, nodeNewTC); 
				tcToNodes.remove(oldCliqueID);
			}
			// if there was nothing there, nothing to do 
		}
		// then, replace in first-level hash: 
		HashMap<Long, Long> tcToNodesForOldSC = untypedSummaryNodes.get(oldCliqueID);
		HashMap<Long, Long> tcToNodesForNewSC = untypedSummaryNodes.get(newCliqueID);
		if (tcToNodesForOldSC != null){
			if (tcToNodesForNewSC == null){
				tcToNodesForNewSC = new HashMap<>();
				untypedSummaryNodes.put(newCliqueID,  tcToNodesForNewSC);
			}
			for (Long thisOldTC: tcToNodesForOldSC.keySet()){
				Long thisOldNode = tcToNodesForOldSC.get(thisOldTC); // this is not null
				Long thisNewNode = tcToNodesForNewSC.get(thisOldTC);
				if (thisNewNode == null){
					tcToNodesForNewSC.put(thisOldTC, thisOldNode); // no summary node replacement here
				}
				else{
					replaceInSummary(thisOldNode, thisNewNode); // if there was a summary node on the old and new clique with the same TC, use the new clique node
				}
			}
			untypedSummaryNodes.remove(oldCliqueID); // after the loop not to interfere with the cursor
		}
		else{ // oldSC was not a source clique, nothing left to do
		}
	}
	/**
	 * Creates a new (untyped) summary node and inserts it into untypedSummaryNodes
	 * @param sourceClique
	 * @param targetClique
	 * @return
	 */
	protected Long getOrCreateSummaryNode(Long sourceClique, Long targetClique) {
		assert(sourceClique != null & targetClique != null); 
		HashMap<Long, Long> targetCliquesForThisSourceClique = this.untypedSummaryNodes.get(sourceClique); 
		if (targetCliquesForThisSourceClique == null){
			targetCliquesForThisSourceClique = new HashMap<Long, Long>();
			this.untypedSummaryNodes.put(sourceClique, targetCliquesForThisSourceClique); 
		}
		Long node = targetCliquesForThisSourceClique.get(targetClique);
		if (node == null){
			//System.out.println("Created " + this.maxSummaryNode + " for source clique " + sourceClique + " and target clique " + targetClique); 
			node = getNextSummaryNode(); // from the Summary class
			this.untypedSummaryNodes.get(sourceClique).put(targetClique, node);
			//System.out.println("Put in untypedSummaryNodes " + sourceClique + "->" + targetClique + "->" + node);
		}
		return node; 
	}

	protected Long getEmptySourceCliqueID() {
		Long res; 
		if (this.emptySCCount == Long.MAX_VALUE){ // the empty source clique has not been created yet
			ArrayList<Long> emptySC = new ArrayList<>();
			res = minCliqueID; // we invent a new source clique
			//Debugger.log("ooooo> Initialized the empty source clique at: " + res);
			this.emptySCCount = minCliqueID; 
			// add this to sc
			sc.put(minCliqueID, emptySC);
			minCliqueID--;
		}
		else{ // the empty source clique has already been created, just copy it 
			res = this.emptySCCount; 
		}	
		return res; 
	}
}
