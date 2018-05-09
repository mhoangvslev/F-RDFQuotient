package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongList;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import com.ibm.db2.jcc.am.SqlException;
import com.ibm.db2.jcc.am.t;

public class StrongOrTypedStrongSummary extends Summary {
	Long2LongList sc; // for each source clique ID,  a source clique
	Long2LongList tc; // for each target clique ID,  its target clique
	Long2Long n2sc; // for each data node, its source clique ID
	Long2Long n2tc; // for each data node, its target clique ID
	Long2Long p2sc; // property to source clique
	Long2Long p2tc; // property to target clique
	long minCliqueID;
	protected long numberOfDataTriplesRead;
	protected long numberOfTypeTriplesRead;
	protected long emptySCCount; // le numéro de la clique vide
	protected long emptyTCCount; // le numéro de la clique vide
	HashMap<Long, HashMap<Long, Long>> untypedSummaryNodes; // source clique --> target clique --> summary node
	protected char TARGET = 1;
	protected char SOURCE = 0;
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

	// case classification
	// T: typed, U: untyped (apply to S and O)
	// R: already represented, N: not already represented (apply to S, P, O)
	protected final char TS_TO = 0;
	protected final char TS_UO_RO_RP = 1;
	protected final char TS_UO_NO_RP = 2;
	protected final char US_RS_TO_RP = 3;
	protected final char US_NS_TO_RP = 6;
	protected final char TS_UO_RO_NP = 9;
	protected final char TS_UO_NO_NP = 10;
	protected final char US_RS_TO_NP = 11;
	protected final char US_NS_TO_NP = 14;

	protected Connection conn; 

	public StrongOrTypedStrongSummary() {
		super();
		sc = new Long2LongList();
		tc = new Long2LongList();
		n2sc = new Long2Long();
		n2tc = new Long2Long();
		p2sc = new Long2Long();
		p2tc = new Long2Long();
		rep = new Long2Long();
		untypedSummaryNodes = new HashMap<Long, HashMap<Long, Long>> ();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE;
		numberOfDataTriplesRead = 0;
		numberOfTypeTriplesRead = 0;
	}

	protected String caseName(char c) { // 17 cases
		switch (c) {
		case TS_TO: {
			return "TS_TO";
		}
		case TS_UO_RO_RP: {
			return "TS_UO_RO_RP";
		}
		case TS_UO_NO_RP: {
			return "TS_UO_NO_RP";
		}
		case US_RS_TO_RP: {
			return "US_RS_TO_RP";
		}
		case US_RS_UO_RO_RP: {
			return "US_RS_UO_RO_RP";
		}
		case US_RS_UO_NO_RP: {
			return "US_RS_UO_NO_RP";
		}
		case US_NS_TO_RP: {
			return "US_NS_TO_RP";
		}
		case US_NS_UO_RO_RP: {
			return "US_NS_UO_RO_RP";
		}
		case US_NS_UO_NO_RP: {
			return "US_NS_UO_NO_RP";
		}
		case TS_UO_RO_NP: {
			return "TS_UO_RO_NP";
		}
		case TS_UO_NO_NP: {
			return "TS_UO_NO_NP";
		}
		case US_RS_TO_NP: {
			return "US_RS_TO_NP";
		}
		case US_RS_UO_RO_NP: {
			return "US_RS_UO_RO_NP";
		}
		case US_RS_UO_NO_NP: {
			return "US_RS_UO_NO_NP";
		}
		case US_NS_TO_NP: {
			return "US_NS_TO_NP";
		}
		case US_NS_UO_RO_NP: {
			return "US_NS_UO_RO_NP";
		}
		case US_NS_UO_NO_NP: {
			return "US_NS_UO_NO_NP";
		}
		}
		throw new IllegalStateException("Unrecognized case " + c);
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
		ArrayList<Long> actualSourceClique = new ArrayList<>();
		actualSourceClique.add(p);
		sc.put(res, actualSourceClique);
		p2sc.put(p, res);
		Debugger.log("Added the new source clique for: " + res + " with property " + p);
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
		ArrayList<Long> actualTargetClique = new ArrayList<>();
		actualTargetClique.add(p);
		tc.put(targetCliqueID, actualTargetClique);
		p2tc.put(p, targetCliqueID);
		//Debugger.log("Added the new target clique " + targetCliqueID + " which is [" + p + "]");
		minCliqueID--;
		return targetCliqueID;
	}

	protected Long getEmptyTargetCliqueID() {
		Long res;
		if (this.emptyTCCount == Long.MAX_VALUE) { // the empty source clique has not been created yet
			ArrayList<Long> emptyTC = new ArrayList<>();
			res = minCliqueID; // we invent a new source clique
			Debugger.log("ooooo> Initialized the empty target clique at: " + res);
			this.emptyTCCount = minCliqueID;
			// add this to tc
			tc.put(minCliqueID, emptyTC);
			minCliqueID--;
		}
		else // the empty source clique has already been created, just copy it 
			res = this.emptyTCCount;
		return res;
	}

	/**
	 * Clique IDs are negative. So, the higher value is the one created first. We will keep the higher value and replace the
	 * lower value with this higher value.
	 * An exception is made if one of the cliques is the empty clique: in this case, fusion systematically
	 * takes the other clique.
	 * Updates sc, tc, n2tc, p2sc, p2tc, summary (edges)
	 * @param c1
	 * @param c2
	 * @param code
	 * @return 
	 */
	protected Long fuseCliquesIntoCreatedFirst(Long c1, Long c2, char code) {
	
		if (code == SOURCE){
			boolean c1empty = c1.equals(this.getEmptySourceCliqueID());
			boolean c2empty = c2.equals(this.getEmptySourceCliqueID());
			Debugger.log("FuseCliquesIntoCreatedFirst SOURCE " + c1 + (c1empty?" (empty)":"") + " " + c2 + (c2empty?" (empty)":""));
			if (c1empty){
				return c2;
			}
			if (c2empty){
				return c1;
			}
			if (c1 > c2){// we know none is empty
				fuseSourceCliques(c2, c1);
				return c1;
			}
			if (c2 > c1){// we know none is empty
				fuseSourceCliques(c1, c2);
				return c2;
			}
		}
		else if (code == TARGET){
			boolean c1empty = c1.equals(this.getEmptyTargetCliqueID());
			boolean c2empty = c2.equals(this.getEmptyTargetCliqueID());
			Debugger.log("FuseCliquesIntoCreatedFirst TARGET " + c1 + (c1empty?" (empty)":"") + " " + c2 + (c2empty?" (empty)":""));
			
			if (c1empty){
				return c2;
			}
			if (c2empty){
				return c1;
			}
			if (c1 > c2){// we know none is empty
				fuseTargetCliques(c2, c1);
				return c1;
			}
			if (c2 > c1){// we know none is empty
				fuseTargetCliques(c1, c2);
				return c2;
			}
		}
		else throw new Error("Unknown code!");
		return c1;
	}

	// untyped subject, already represented: thus, it has a source clique that p must join
	// untyped object, not represented: we assign it target clique {p} and empty source clique
	// unknown property
	protected void handleDataTriple_US_RS_UO_NO_NP(Triple t, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// add p to the source clique of t.s; the clique ID changes if it was the empty source clique:
		Long ssc = n2sc.get(t.s);
		Long newSourceClique = addPropertyToSourceClique(t.p, ssc);
		Debugger.log("Source clique of subject " + t.s + " is now: " + newSourceClique);
		Long repS = rep.get(t.s);
		Long newRepS = repS; 
		// addPropertyToSourceClique may change the cliqueID if the source clique was empty. 
		// This may require to split t.s and represent it apart from its previous colleagues,
		// because with the current triple, t.s no longer has an empty source clique!
		if (!ssc.equals(newSourceClique)){
			newRepS = replaceAndMaybeSplitUntypedSummaryNodes(t.s, newSourceClique, SOURCE);
			if (!repS.equals(newRepS)){
				//this.changeRepresentationOfInto(t.s, newRepS);
				rep.put(t.s, newRepS);
			}
		}		
		n2sc.put(t.s, newSourceClique); // this line should stay after the call to Split...
		// so that this call still has access to the SC of t.s, unmodified (yet)

		// now representing the object
		Debugger.log("Representing previously unseen object " + t.o);
		long ptc = makeAndAddNewTargetClique(t.p);
		long repO = getOrCreateSummaryNode(this.getEmptySourceCliqueID(), ptc);
		rep.put(t.o, repO);
		n2sc.put(t.o, this.getEmptySourceCliqueID());
		n2tc.put(t.o, ptc);
		this.addTriple(newRepS, t.p, repO);
	}

	/**
	 * Modifies rep, untypedSummaryNodes, summary edges
	 * @param node
	 * @param newClique
	 * @param param
	 * @return
	 */
	protected Long replaceAndMaybeSplitUntypedSummaryNodes(Long node, Long newClique, char param) {
		Long thisNodeRep = rep.get(node);
		Long thisNodeNewRep = thisNodeRep; 
		boolean thisNodeAlone = (rep.getInverse(thisNodeRep).size() == 1);
		System.out.println("REPLACE-AND-SPLIT: " + node + " (" + RDF2SQLEncoding.dictionaryDecode(node) + 
				(thisNodeAlone?") is ":") is not ") + " the only one represented by " + thisNodeRep);
		if (param == SOURCE){
			Long oldClique = n2sc.get(node);
			Long otherClique = n2tc.get(node); 
			// the former source clique of s was empty, now we have to replace it with newSourceClique
			// if node was the only node represented by rep.get(node):
			//		if there was already a node for newClique and otherClique
			//      then represent s by that node (and possibly garbage collect its representative)
			//      else modify that node, by replacing in  untypedSummaryNodes, the empty source clique with the non-empty one
			if (thisNodeAlone){
				System.out.println("R&S SOURCE 0 for " + node + " (" + RDF2SQLEncoding.dictionaryDecode(node) + ") alone, w/ old source clique " + oldClique + 
						" " + showCliqueAsString(sc.get(oldClique)) + 
						" and new source clique " + newClique + " " + showCliqueAsString(sc.get(newClique)) +  		
						", for " + thisNodeRep + " (and target clique " + otherClique + 
						" " + showCliqueAsString(tc.get(otherClique)) + 
						")");

				thisNodeNewRep = findExistingSummaryNode(newClique, otherClique); 

				if (thisNodeNewRep != null){ // a representative exactly for this already existed; the node changes representation; 
					// TODO edge patching may be needed
					System.out.println("We already have summary node " + thisNodeNewRep + " for " + newClique + " and " + otherClique + 
							", representing " + node + " by it");
					boolean suppressionNeeded = rep.put(node, thisNodeNewRep);
					if (suppressionNeeded){
						this.replaceNodeInSummaryEdges(thisNodeRep, thisNodeNewRep);
						//this.removeAllSummaryEdgesInvolving(thisNodeRep);
					}
					System.out.println("R&S SOURCE 1 Updated representation: ");
					this.showRep();
					return thisNodeNewRep; 
				}
				else{ // we just update the existing node
					this.nodeOrientedCliqueReplacementInUntyped(node, thisNodeRep, oldClique, otherClique, newClique, SOURCE);
//					HashMap<Long, Long> oldCliqueEntries = untypedSummaryNodes.get(oldClique);
//					if (!((oldCliqueEntries.get(otherClique)).equals(thisNodeRep))){
//						String msg = ("On " + oldClique + " and " + otherClique + " we did not have " + thisNodeRep + " but " + (oldCliqueEntries.get(otherClique)));
//						System.out.println(msg);
//						throw new IllegalStateException(msg);
//					}
//					oldCliqueEntries.remove(otherClique);
//					if (oldCliqueEntries.size() == 0){
//						untypedSummaryNodes.remove(oldClique); 
//					}
//					HashMap<Long, Long> newCliqueEntries = untypedSummaryNodes.get(newClique);
//					if (newCliqueEntries == null){
//						newCliqueEntries = new HashMap<Long, Long>();
//						untypedSummaryNodes.put(newClique, newCliqueEntries);
//					}
//					newCliqueEntries.put(otherClique, thisNodeRep);
					System.out.println("R&S SOURCE 2 representation unchanged"); 
					return thisNodeRep; 
				}
			}
			// but if node was not the only one
			// then all the nodes previously represented by rep.get(node), other than node,
			// need to keep a representative for those (empty source clique, existing target clique)
			// while this node needs to be represented by a new node, corresponding to the (non-empty source clique, existing
			// target clique)
			else{ 
				System.out.println("REPLACE-AND-SPLIT SOURCE for " + node + " (" + 
						RDF2SQLEncoding.dictionaryDecode(node) + 
						") not alone, w/ old source clique " + oldClique + 
						" " + showCliqueAsString(sc.get(oldClique)) + 
						" and new source clique " + newClique + " " + showCliqueAsString(sc.get(newClique)) +  		
						", rep. by " + thisNodeRep + " (and target clique " + otherClique + 
						" " + showCliqueAsString(tc.get(otherClique)) + 
						"). We split its representative "  + thisNodeRep);
				thisNodeNewRep = this.getOrCreateSummaryNode(newClique, otherClique); 
				boolean suppressionNeeded = rep.put(node, thisNodeNewRep);
				if (suppressionNeeded){
					this.replaceNodeInSummaryEdges(thisNodeRep,thisNodeNewRep);
					//this.removeAllSummaryEdgesInvolving(thisNodeRep);
				}
				System.out.println("R&S SOURCE 3 Updated representation: ");
				this.showRep();
				// fix edges incoming to this node
				replayEdgesToPatchUpSummary(node, thisNodeNewRep, TARGET); // yes, TARGET
				// because now we want to set the edges of which this node is TARGET
				System.out.println("R&S SOURCE 3 returning");
				return thisNodeNewRep; 
			}
		}
		if (param == TARGET){	
			Long oldClique = n2tc.get(node); 
			Long otherClique = n2sc.get(node);
			if (thisNodeAlone){ // this node the only one represented by by nodeRep
				System.out.println("REPLACE-AND-SPLIT TARGET for " + node + " (" + RDF2SQLEncoding.dictionaryDecode(node) + 
						") alone, w/ old target clique " + oldClique + 
						" " + showCliqueAsString(tc.get(oldClique)) + 
						" and new target clique " + newClique + " " + showCliqueAsString(tc.get(newClique)) +  		
						", rep. by " + thisNodeRep + " (and source clique " + otherClique + 
						" " + showCliqueAsString(sc.get(otherClique)) + 
						")");

				thisNodeNewRep = findExistingSummaryNode(otherClique, newClique); 

				if (thisNodeNewRep != null){ // a representative exactly for this already existed; the node changes representation; 
					// TODO edge patching may be needed
					System.out.println("We already have summary node " + thisNodeNewRep + " on " + otherClique + "->" + newClique + 
							", representing " + node + " by it");
					boolean suppressionNeeded = rep.put(node, thisNodeNewRep);
					if (suppressionNeeded){
						this.replaceNodeInSummaryEdges(thisNodeRep, thisNodeNewRep);
						//this.removeAllSummaryEdgesInvolving(thisNodeRep);
					}
					System.out.println("R&S TARGET 1 Updated representation: ");
					this.showRep();
					return thisNodeNewRep; 
				}
				else{ // there was no representative, so we alter the existing representative node 
//					HashMap<Long, Long> thisSourceCliqueEntries = untypedSummaryNodes.get(otherClique);
//					if (thisSourceCliqueEntries == null){
//						throw new IllegalStateException("On " + otherClique + " we have nothing");
//					}
//					Long prevOnThisClique = thisSourceCliqueEntries.get(oldClique);
//					if (!prevOnThisClique.equals(thisNodeRep)){
//						throw new IllegalStateException("On " + otherClique + " and " + oldClique + " we did not have " + thisNodeRep);
//					}
//					thisSourceCliqueEntries.put(newClique, thisNodeRep);
//					thisSourceCliqueEntries.remove(oldClique);
					this.nodeOrientedCliqueReplacementInUntyped(node, thisNodeRep, oldClique, otherClique, newClique, TARGET);
					System.out.println("R&S TARGET 2 representation unchanged"); 
					return thisNodeRep; 
				}
			}
			else{ // several nodes were represented together here
				System.out.println("REPLACE-AND-SPLIT TARGET for " + node + " (" + 
						RDF2SQLEncoding.dictionaryDecode(node) + 
						") not alone, w/ target clique " + oldClique + 
						" " + showCliqueAsString(tc.get(oldClique)) + 
						" with " + newClique + " " + showCliqueAsString(tc.get(newClique)) +  		
						", for " + thisNodeRep + " (using also " + otherClique + 
						" " + showCliqueAsString(sc.get(otherClique)) + 
						"). We split its representative "  + thisNodeRep);
				thisNodeNewRep = this.getOrCreateSummaryNode(otherClique, newClique);
				boolean suppressionNeeded = rep.put(node, thisNodeNewRep);
				if (suppressionNeeded){
					this.replaceNodeInSummaryEdges(thisNodeRep, thisNodeNewRep);
					//this.removeAllSummaryEdgesInvolving(thisNodeRep);
				}
				System.out.println("R&S TARGET Updated representation: ");
				this.showRep();
				// take care of possible edges of which this node is a source
				replayEdgesToPatchUpSummary(node, thisNodeNewRep, SOURCE); // yes, SOURCE
				// because now we want to set the edges of which this node is SOURCE
				return thisNodeNewRep; 
			}
		}
		throw new IllegalStateException("Unrecognized parameter: neither SOURCE nor TARGET"); 
	}

	private Long findExistingSummaryNode(Long sc, Long tc) {
		if (untypedSummaryNodes.get(sc) == null){
			return null;
		}
		return untypedSummaryNodes.get(sc).get(tc); 
	}

	/**
	 * The data node node will soon be represented by newRep, breaking away from its
	 * representative so far (which is still rep.get(node)).
	 * This happens exactly when node had an empty clique which becomes non-empty because of
	 * the last examined triple.
	 * 
	 * TODO also revisit the edges of all the other nodes represented by rep.get(node),
	 * because we need to know which edges to give them, also...
	 * 
	 * @param node
	 * @param newRep
	 * @param param
	 */
	private void replayEdgesToPatchUpSummary(Long node, Long newRep, char param) {
		String patchUpQuery;
		if (param == SOURCE){
			// newRep must be the source of edges reflecting those of which node is a source
			patchUpQuery = "select p, o from encoded_triples et where et.s = " + node; 
			try{
				ResultSet rs = this.conn.prepareStatement(patchUpQuery).executeQuery();
				while (rs.next()){
					Long p = rs.getLong(1);
					Long o = rs.getLong(2);
					Long repO = rep.get(o); 
					if (repO != null){
						Debugger.log("Patch: adding outgoing " + p + " edge from " + newRep);
						this.addTriple(newRep, p, repO);
					}
				}
				rs.close();
			}
			catch(SQLException e){
				throw new IllegalStateException("Could not get replay edges " + e.toString());
			}
			// and rep should only keep the edges corresponding to some of the node it still represents 
			//
			// We will build a set of edges to remove. 
			// We remove an edge rep--a-->rep' iff for every node n still represented by rep and n' represented by n'
			// there exists no edge n--a-->n'
			patchUpQuery = "select o from encoded_triples where s=? and p=?";
			TreeSet<Triple> edgesToRemove = new TreeSet<Triple>();
			
		}
		if (param == TARGET){// we must replay the edges of which node is a target
			patchUpQuery = "select s, p from encoded_triples et where et.o = " + node; 
			try{
				ResultSet rs = this.conn.prepareStatement(patchUpQuery).executeQuery();
				while (rs.next()){
					Long p = rs.getLong(2);
					Long s = rs.getLong(1);
					Long repS = rep.get(s); 
					if (repS != null){
						Debugger.log("Patch: adding incoming " + p + " edge to " + newRep);
						this.addTriple(repS, p, newRep);
					}
				}
				rs.close();
			}
			catch(SQLException e){
				throw new IllegalStateException("Could not get replay edges " + e.toString());
			}
		}
	}

	// untyped, represented subject: it has a source clique, which needs to gain p
	// untyped, represented object: it has a target clique, which needs to gain p
	// unknown property: it should be bound to these modified cliques
	protected void handleDataTriple_US_RS_UO_RO_NP(Triple t, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		System.out.println("US_RS_UO_RO_NP SOURCE:"); 
		Long repS = rep.get(t.s); 
		Long newRepS = repS; 
		Long newSourceCliqueS = addPropertyToSourceClique(t.p, sourceCliqueS);
		if (sourceCliqueS.equals(this.getEmptySourceCliqueID())){
			newRepS = replaceAndMaybeSplitUntypedSummaryNodes(t.s, newSourceCliqueS, SOURCE);
			// this updates rep by itself and takes possible cleanup measures (node replacement etc.) 
		}
		n2sc.put(t.s, newSourceCliqueS); // this should stay after the call to Split...

		System.out.println("US_RS_UO_RO_NP TARGET:"); 
		Long repO = rep.get(t.o);
		Long newRepO = repO; 
		if (targetCliqueO == null){
			throw new IllegalStateException("Target clique of " + t.o + " is null");
		}
		Long newTargetCliqueO = addPropertyToTargetClique(t.p, targetCliqueO);
		if (targetCliqueO.equals(this.getEmptyTargetCliqueID())){
			newRepO = replaceAndMaybeSplitUntypedSummaryNodes(t.o, newTargetCliqueO, TARGET);
			// this updates rep by itself and takes possible cleanup measures (node replacement etc.) 
		}
		n2tc.put(t.o, newTargetCliqueO);
		// neither the representatives nor the source, target cliques of t.s and t.o change
		this.addTriple(newRepS, t.p, newRepO);
		System.out.println("US_RS_UO_RO_NP ends"); 
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
		Long repS = rep.get(t.s);
		Long newRepS = repS; 
		if (sourceCliqueS != sourceCliqueP) { // the source clique of P was not that of S
			System.out.println("Source clique of subject " + t.s + " (" + sourceCliqueS + ") differs from that of property " + t.p + ", which is " + sourceCliqueP ); 
			Long fusedCliqueS = fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
			System.out.println("Fused them into: " + fusedCliqueS); 
			newRepS = getOrCreateSummaryNode(fusedCliqueS, targetCliqueS);
			System.out.println("This leads the new representative of " + t.s + " being: " + newRepS);
			if (!repS.equals(newRepS)){  
				boolean suppressionNeeded = rep.put(t.s, newRepS); 
				if (suppressionNeeded){
					removeAllSummaryEdgesInvolving(repS); 
				}
			}
			n2tc.put(t.s,  targetCliqueS);
			n2sc.put(t.s, fusedCliqueS);
		}
		else { // no need to do anything, the source clique of S is already that of p 
		}
		// adding triple:
		this.addTriple(newRepS, t.p, rep.get(t.o));
		System.out.println("=== US_RS_UO_NO_RP");
	}

	/**
	 * Remove the edges of a phantom summary node
	 * (which used to represent some data nodes but now represents nobody)
	 * @param sn
	 */
	private void removeAllSummaryEdgesInvolving(Long sn) {
//		System.out.println("BEFORE REMOVING EDGES ARE:");
//		for (Triple t: getSummaryEdges()){
//			System.out.println(t.s + "--" + RDF2SQLEncoding.dictionaryDecode(t.p) + "-->" + t.s);
//		}
		// removing sn as a subject
		if (this.edges.get(sn)!=null){
			System.out.println("REMOVED " + this.edges.get(sn).keySet().size() + " edges whose source was " + sn);
			this.edges.remove(sn);
		}
		// removing sn as an object
		for (Long s: edges.keySet()){
			HashMap<Long, TreeSet<Long>> edgesS = edges.get(s);
			for (Long p: edgesS.keySet()){
				TreeSet<Long> edgesSP = edgesS.get(p);
				if (edgesSP.contains(sn)){ //TODO see if we can make this test more efficient, maybe modify Long2Long to use a set instead of a List
					edgesSP.remove(sn); // this removes only one occurrence but 
					// it does not appear likely that there would be more than one
					System.out.println("REMOVED edge " + s + "--" + p + "-->" + sn);
				}
			}
		}
	}

	// removes an entry from untyped summary nodes
	protected void removeFromUntyped(Long sc, Long tc){
		HashMap<Long, Long> entriesOnSC = untypedSummaryNodes.get(sc);
		if (entriesOnSC == null){
			throw new IllegalStateException("Source clique not present here!");
		}
		if (!entriesOnSC.containsKey(tc)){
			throw new IllegalStateException("Target clique not present here!");
		}
		entriesOnSC.remove(tc); 
	}


	// untyped, unrepresented subject
	// untyped, represented object
	// known property
	protected void handleDataTriple_US_NS_UO_RO_RP(Triple t, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		helper_US_NS_RP(t, sourceCliqueP, targetCliqueP);
		// see what to do with t.o
		Long repO = rep.get(t.o);
		Long newRepO = repO; 
		if (targetCliqueO != targetCliqueP) {
			Debugger.log("Looking into fusing target cliques of object: " + targetCliqueO + " and of property: "  + targetCliqueP);
			// this call updates n2tc, tc, existing summary edges
			Long fusedTargetPO = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET);
			// TODO check if o used to have an empty target clique, which needs split and replace
			Debugger.log("Retaining " + fusedTargetPO + " as target clique for object " + t.o + " (" + RDF2SQLEncoding.dictionaryDecode(t.o) + ")");
			newRepO = getOrCreateSummaryNode(sourceCliqueO, fusedTargetPO);
			n2tc.put(t.o, fusedTargetPO);
			n2sc.put(t.o, sourceCliqueO);
			rep.put(t.o, newRepO);
			if (!repO.equals(newRepO)){
				this.replaceNodeInSummaryEdges(repO, newRepO);
			}
			// TODO maybe repO will still be seen as representing some folks?...
			Debugger.log("Represented " + t.o + " on the source clique " + sourceCliqueO + " and target clique " + fusedTargetPO);
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
	 *
	 * @param t
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
		Debugger.log("Represented new subject " + t.s + " based on source clique " + sourceCliqueP + " and empty target clique "
				+ emptyTargetCliqueS); 
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
		n2sc.put(t.s, psc);
		n2tc.put(t.s, emptyTargetCliqueID); 
		rep.put(t.s, repS);
		// o is already represented but its target clique did not include p (given that p was not known)
		// we need to add p to this target clique, which may lead to a new target clique if the previous one was empty
		// (and only in this case)
		Long repO = rep.get(t.o); 
		Long newTargetCliqueO = addPropertyToTargetClique(t.p, targetCliqueO);
		if (targetCliqueO.equals(this.getEmptyTargetCliqueID()) && (!targetCliqueO.equals(newTargetCliqueO))){
			repO = this.replaceAndMaybeSplitUntypedSummaryNodes(t.o, newTargetCliqueO, TARGET); 
		}
		// then adjust t.o's representation
		n2tc.put(t.o, newTargetCliqueO); // this should stay after the call to Split...
		this.addTriple(repS, t.p, repO);

	}

	// untyped, non represented subject
	// untyped, non represented object
	// unknown property
	protected void handleDataTriple_US_NS_UO_NO_NP(Triple t, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// initialize p's cliques
		long psc = makeAndAddNewSourceClique(t.p);
		long ptc = makeAndAddNewTargetClique(t.p);
		// s has an empty target clique for all we know
		long emptyTargetCliqueID = getEmptyTargetCliqueID();
		long repS = getOrCreateSummaryNode(psc, emptyTargetCliqueID);
		n2sc.put(t.s, psc);
		n2tc.put(t.s, emptyTargetCliqueID);
		rep.put(t.s, repS);
		// o has an empty source clique for all we know
		long emptySourceCliqueID = getEmptySourceCliqueID();
		long repO = getOrCreateSummaryNode(emptySourceCliqueID, ptc);
		n2sc.put(t.o, emptySourceCliqueID);
		n2tc.put(t.o, ptc);
		rep.put(t.o, repO);
		// add triple
		this.addTriple(repS, t.p, repO);
	}

	// untyped, unrepresented subject
	// untyped, unrepresented object
	// known property
	protected void handleDataTriple_US_NS_UO_NO_RP(Triple t, Long sourceCliqueS,
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
		if (sourceCliqueS != sourceCliqueP) {
			System.out.println("US_RS_UO_RO_RP SOURCE");
			newSCs = fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
			System.out.println("SC of s " + t.s + " (" + RDF2SQLEncoding.dictionaryDecode(t.s) + 
					" was empty: " + (sourceCliqueS.equals(this.getEmptySourceCliqueID())));
			if (sourceCliqueS.equals(this.getEmptySourceCliqueID())){ // here we may need to split and make quite some mess
				newRepS = this.replaceAndMaybeSplitUntypedSummaryNodes(t.s, newSCs, SOURCE); 
				// this includes its own suppressionNeeded tests
			}
			else{ // this is easier
				newRepS = getOrCreateSummaryNode(newSCs, targetCliqueS);
				if (!repS.equals(newRepS)){
					// TODO see if we want to push this in getOrCreateSummaryNode
					boolean replacementNeeded = rep.put(t.s, newRepS);
					if (replacementNeeded){
						//this.removeAllSummaryEdgesInvolving(repS);
						this.replaceNodeInSummaryEdges(repS, newRepS);
					}
				}
				else{// got the same node back, but we need to "manually" modify untypedSummaryNodes as its cliques
					// have changed...
					nodeOrientedCliqueReplacementInUntyped(t.s, repS, sourceCliqueS, targetCliqueS, newSCs, SOURCE); 					
				}
			}
			// in all cases, update repS
			n2tc.put(t.s, targetCliqueS);
			n2sc.put(t.s, newSCs);

		}
		if (targetCliqueO != targetCliqueP) {
			//Debugger.log("Object node " + t.o + " (" + RDF2SQLEncoding.dictionaryDecode(t.o) + ") has the target clique " + targetCliqueO + ": ");
			//showClique(tc.get(targetCliqueO));
			//Debugger.log("while the property " + t.p + " (" + RDF2SQLEncoding.dictionaryDecode(t.p) + ") has the target clique " + targetCliqueP + ": "); 
			//showClique(tc.get(targetCliqueP));
			//Debugger.log("Fusing them into the one created first"); 
			System.out.println("US_RS_UO_RO_RP TARGET");
			Debugger.log("TC of o " + t.o + " was " + ((targetCliqueO.equals(this.getEmptyTargetCliqueID()))?"":" not ") +
					" empty");
			newTCo = fuseCliquesIntoCreatedFirst(targetCliqueO, targetCliqueP, TARGET);
			System.out.println("US_RS_UO_RO_RP Fusion resulted in target clique: " + newTCo);
			if (targetCliqueO.equals(this.getEmptyTargetCliqueID())){
				newRepO = this.replaceAndMaybeSplitUntypedSummaryNodes(t.o, newTCo, TARGET); 
				// this includes its own replacementNeeded tests
			}
			else{
				newRepO = getOrCreateSummaryNode(sourceCliqueO, newTCo);
				System.out.println("Possibly renewed representative of object " +  t.o + " is: " + newRepO + " was: " + repO);
				System.out.println("Untyped nodes are now: " + this.showUntypedSummaryNodes());
				// update the representation of o
				if (!repO.equals(newRepO)){
					boolean replacementNeeded = rep.put(t.o, newRepO);
					if (replacementNeeded){
						//this.removeAllSummaryEdgesInvolving(repO);
						this.replaceNodeInSummaryEdges(repO, newRepO);
					}
				}
				else{// got the same node back, but we need to "manually" modify untypedSummaryNodes as its cliques
					// have changed...
					//replaceInUntypedSummaryNode(t.o, repO, targetCliqueO, sourceCliqueO, newTCo, TARGET); 					
				}
			}
			n2tc.put(t.o, newTCo);
			n2sc.put(t.o, sourceCliqueO);
		}
		// adding triple:
		this.addTriple(newRepS, t.p, newRepO);
	}

	/**
	 * Modifies exclusively untypedNodes to signal that one summary node has changed one clique
	 * @param node data node 
	 * @param thisNodeRep its representative
	 * @param oldClique previous clique (source or target depending on param) 
	 * @param otherClique the other (unaffected) clique of node
	 * @param newClique the new clique (source or target depending on param)
	 * @param param SOURCE or TARGET
	 */
	private void nodeOrientedCliqueReplacementInUntyped(Long node, Long thisNodeRep, Long oldClique, Long otherClique, Long newClique, char param) {
		if (param == SOURCE){
			// we need to replace oldClique-->otherClique-->sumNode by newClique-->otherClique-->sumNode
			HashMap<Long, Long> oldCliqueEntries = untypedSummaryNodes.get(oldClique);
			if (!((oldCliqueEntries.get(otherClique)).equals(thisNodeRep))){
				String msg = ("On " + oldClique + " and " + otherClique + " we did not have " + thisNodeRep + " but " + (oldCliqueEntries.get(otherClique)));
				System.out.println(msg);
				throw new IllegalStateException(msg);
			}
			oldCliqueEntries.remove(otherClique);
			if (oldCliqueEntries.size() == 0){
				untypedSummaryNodes.remove(oldClique); 
			}
			HashMap<Long, Long> newCliqueEntries = untypedSummaryNodes.get(newClique);
			if (newCliqueEntries == null){
				newCliqueEntries = new HashMap<Long, Long>();
				untypedSummaryNodes.put(newClique, newCliqueEntries);
			}
			newCliqueEntries.put(otherClique, thisNodeRep);
		}
		if (param == TARGET){
			System.out.println("REPLACING IN UNTYPED target clique " + oldClique + " of " + node + " (source clique " +
					otherClique + ", rep. by " + thisNodeRep + ") by new target clique " + newClique); 
			HashMap<Long, Long> thisSourceCliqueEntries = untypedSummaryNodes.get(otherClique);
			if (thisSourceCliqueEntries == null){
				throw new IllegalStateException("On " + otherClique + " we have nothing");
			}
			Long prevOnThisClique = thisSourceCliqueEntries.get(oldClique);
			if (!prevOnThisClique.equals(thisNodeRep)){
				throw new IllegalStateException("On " + otherClique + " and " + oldClique + " we did not have " + thisNodeRep);
			}
			thisSourceCliqueEntries.put(newClique, thisNodeRep);
			thisSourceCliqueEntries.remove(oldClique);
		}
	}

	/**
	 * Updates sc, n2sc, untypedSummaryNodes, summary (edges)
	 *
	 * @param sourceCliqueOld
	 * @param sourceCliqueNew
	 */
	protected void fuseSourceCliques(Long sourceCliqueOld, Long sourceCliqueNew) {
		Debugger.log("FuseSourceCliques:  " + sourceCliqueOld + " becomes " + sourceCliqueNew);
		//display();
		// add properties of target clique of o, to those of the target clique of p
		ArrayList<Long> realSCNew = this.sc.get(sourceCliqueNew);
		ArrayList<Long> realSCOld = this.sc.get(sourceCliqueOld);

		if (realSCOld != null)
			for (Long l: realSCOld)
				realSCNew.add(l);
		this.sc.remove(sourceCliqueOld);
		// update n2sc to inform all the nodes mapped to sourceCliqueO, to map now to sourceCliqueP
		this.n2sc.replaceValue(sourceCliqueOld, sourceCliqueNew);
		replaceCliqueInSummaryBasedOnExistingNodes(sourceCliqueOld, sourceCliqueNew);

		this.p2sc.replaceValue(sourceCliqueOld, sourceCliqueNew);
		Debugger.log("After the fusion: ");
		//display();
	}

	/**
	 * Updates tc, n2tc, p2tc, summary (edges)
	 *
	 * @param targetCliqueOld
	 * @param targetCliqueNew
	 */
	protected void fuseTargetCliques(Long targetCliqueOld, Long targetCliqueNew) {
		System.out.println("FUSE TARGET CLIQUE " + targetCliqueOld + " into " + targetCliqueNew);
		// Do not display here as this requires rep to be fully filled and rep cannot be filled for new nodes before the fusion. So some nodes may be missing.
		//display();
		// then make targetCliqueO the same as targetCliqueP (keep smaller)
		// add properties of target clique of o, to those of the target clique of p
		ArrayList<Long> realTCNew = this.tc.get(targetCliqueNew);
		ArrayList<Long> realTCOld = this.tc.get(targetCliqueOld);

		if (realTCOld != null)
			for (Long l: realTCOld)
				realTCNew.add(l);
		this.tc.remove(targetCliqueOld);//TODO this clearly does not remove all over
		// update n2tc to inform all the nodes mapped to targetCliqueO, to map now to targetCliqueP
		this.n2tc.replaceValue(targetCliqueOld, targetCliqueNew);
		replaceCliqueInSummaryBasedOnExistingNodes(targetCliqueOld, targetCliqueNew);
		this.p2tc.replaceValue(targetCliqueOld, targetCliqueNew);
		//Debugger.log("After the fusion: ");
		//display();
	}

	/**
	 * Adds p to the source clique indicated by sourceCliqueID; updates p2sc and sc
	 *
	 * @param p
	 * @param sourceCliqueID
	 * @return 
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
			if (!(sc.get(sourceCliqueID).contains(p)))
				sc.get(sourceCliqueID).add(p);
			return sourceCliqueID;
			//System.out.println("New source clique " + sourceCliqueID);
			//showClique(sc.get(sourceCliqueID)); 
		}
	}

	/**
	 * Adds p to the target clique indicated by targetCliqueID
	 *
	 * @param p
	 * @param targetCliqueID
	 * @return 
	 */
	protected Long addPropertyToTargetClique(Long p, Long targetCliqueID) {
		if (targetCliqueID == null){
			throw new IllegalStateException("Null target clique"); 
		}
		if (targetCliqueID.equals(this.getEmptyTargetCliqueID())) {
			// existingTC was empty. In this case, we need to create a new
			// target clique and put just p inside, and return that one.
			Long newTargetClique = this.makeAndAddNewTargetClique(p);
			p2tc.put(p, newTargetClique);
			return newTargetClique;
		}
		else {// existingTC was not empty. It suffices to add p to it. 
			if (!(tc.get(targetCliqueID).contains(p)))
				tc.get(targetCliqueID).add(p);
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
	 *
	 * May 2018: it only replaces when the new clique already has known, associated nodes
	 * @param oldCliqueID
	 * @param newCliqueID
	 */
	protected void replaceCliqueInSummaryBasedOnExistingNodes(Long oldCliqueID, Long newCliqueID) {
		// replace in rep:
		// first, replace in second-level hash, if it occurs as a target clique:
		if (oldCliqueID > newCliqueID)
			throw new Error("Wrong replacement");
		Debugger.log("REPLACE CLIQUE IN SUMMARY (UNTYPED NODES): " + oldCliqueID + " with " + newCliqueID + " in summary");
		for (Long l: this.untypedSummaryNodes.keySet()) {
			System.out.println("Source clique: " + l);
			HashMap<Long, Long> tcToNodes = untypedSummaryNodes.get(l);
			Long nodeOldTC = tcToNodes.get(oldCliqueID);
			Long nodeNewTC = tcToNodes.get(newCliqueID);
			// replace old with new; remove entry for old:
			if ((nodeOldTC != null) && (nodeNewTC != null)) { // TODO is it really sound to replace?...
				// is no node yet on the newTC
				replaceNodeInSummaryEdges(nodeOldTC, nodeNewTC);
				// tcToNodes.remove(oldCliqueID); May 8, 2018
			}
			// the three lines below added on May 8, 2018. They move the node toward newCliqueID in all cases.
			if (nodeOldTC != null){
				System.out.println("Moving " + nodeOldTC + " on new TC " +  newCliqueID);
				tcToNodes.put(newCliqueID, nodeOldTC);
				tcToNodes.remove(oldCliqueID);
			}
			// if there was nothing there, nothing to do 
		}
		// then, replace in first-level hash: 
		HashMap<Long, Long> tcToNodesForOldSC = untypedSummaryNodes.get(oldCliqueID);
		HashMap<Long, Long> tcToNodesForNewSC = untypedSummaryNodes.get(newCliqueID);
		if (tcToNodesForOldSC != null) {
			//System.out.println("There were entries on oldSC " + oldCliqueID);
			if (tcToNodesForNewSC == null) {
				tcToNodesForNewSC = new HashMap<>();
				untypedSummaryNodes.put(newCliqueID, tcToNodesForNewSC);// added the map for newCliqueID if not already there
			}
			for (Long thisOldTC: tcToNodesForOldSC.keySet()) {
				Long thisOldNode = tcToNodesForOldSC.get(thisOldTC); // this is not null
				Long thisNewNode = tcToNodesForNewSC.get(thisOldTC);
				if (thisNewNode == null)  { // on the new SC, there was no node for this TC, whereas
					// on the old SC there was a node. In this case, carry that node to the new SC and this TC.
					tcToNodesForNewSC.put(thisOldTC, thisOldNode); 
				}
				else{ // there were two nodes for this TC, one on the oldSC and one on the newSC. 
					// TODO is it really sound to replace?...
					System.out.println("There was a node " + thisOldNode + " on oldSC " + oldCliqueID + " and TC " + thisOldTC);
					replaceNodeInSummaryEdges(thisOldNode, thisNewNode); // if there was a summary node on the old and new clique with the same TC, use the new clique node
				}
			}
			untypedSummaryNodes.remove(oldCliqueID); // after the loop not to interfere with the cursor
		}
		else { // oldSC was not a source clique, nothing left to do
		}
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
		assert ((sourceClique != null) && (targetClique != null));
		HashMap<Long, Long> targetCliquesForThisSourceClique = this.untypedSummaryNodes.get(sourceClique);
		if (targetCliquesForThisSourceClique == null) {
			Debugger.log("No target cliques for source clique " + sourceClique);
			targetCliquesForThisSourceClique = new HashMap<>();
			this.untypedSummaryNodes.put(sourceClique, targetCliquesForThisSourceClique);
		}
		Long node = targetCliquesForThisSourceClique.get(targetClique);
		if (node == null) {
			//Debugger.log("There was no node for target clique " + targetClique + " among those on source clique " + sourceClique);
			Debugger.log("Created " + this.maxSummaryNode + " for source clique " + targetClique + " " + this.showCliqueAsString(sc.get(sourceClique)));
			Debugger.log(" and target clique " + this.showCliqueAsString(tc.get(targetClique)));
			node = getNextSummaryNode(); // from the Summary class
			this.untypedSummaryNodes.get(sourceClique).put(targetClique, node);
			Debugger.log("Put in untypedSummaryNodes " + sourceClique + "->" + targetClique + "->" + node);
		}
		return node;
	}

	protected Long getEmptySourceCliqueID() {
		Long res;
		if (this.emptySCCount == Long.MAX_VALUE) { // the empty source clique has not been created yet
			ArrayList<Long> emptySC = new ArrayList<>();
			res = minCliqueID; // we invent a new source clique
			//Debugger.log("ooooo> Initialized the empty source clique at: " + res);
			this.emptySCCount = minCliqueID;
			// add this to sc
			sc.put(minCliqueID, emptySC);
			minCliqueID--;
		}
		else // the empty source clique has already been created, just copy it 
			res = this.emptySCCount;
		return res;
	}
	protected String showUntypedSummaryNodes() {
		StringBuffer sb = new StringBuffer();
		for(Long sc: this.untypedSummaryNodes.keySet()){
			sb.append(sc + "=>{");
			//System.out.println("Source clique: " + sc);
			HashMap<Long, Long> tc2Nodes = this.untypedSummaryNodes.get(sc);
			for (Long tc: tc2Nodes.keySet()){
				Long node = tc2Nodes.get(tc);
				sb.append(tc + ":" + node + " ");
			}
			sb.append("} ");
		}
		return new String(sb);
	}
}
