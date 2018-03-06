package fr.inria.cedar.quotientSummary.summaries.typedstrong;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;

/**
 * Strong and type strong summarization -- NOT FINISHED
 * @author ioanamanolescu
 *
 */
public class StrongSummarization extends fr.inria.cedar.quotientSummary.summaries.Summarization 
{
	Long2LongSet sc; // for each source clique ID,  a source clique
	Long2LongSet tc; // for each target clique ID,  its target clique
	Long2LongSet cs; // for each class set ID, a class set
	Long2Long n2sc; // for each node, its source clique ID
	Long2Long n2tc; // for each node, its target clique ID
	Long2Long n2cs; // for each node, its class set ID. This is also the rep function for typed nodes
	Long2Long p2sc; // property to source clique
	Long2Long p2tc; // property to target clique
	Long2Long c2cs; // class to class set

	long minCliqueID;

	HashMap<Long, HashMap<Long, Long>> untypedSummaryNodes; // source clique --> target clique --> summary node

	private long emptySCCount; // le numéro de la clique vide
	private long emptyTCCount; // le numéro de la clique vide
	private long TYPE; // the number to be used for the type property
	private boolean firstType;
	private char TARGET=1;
	private char SOURCE=0;
	
	// case classification
	// T: typed, U: untyped, R: already represented, N: not already represented, S, P, O
	private final char TS_TO = 0; 
	private final char TS_UO_RO_RP = 1;
	private final char TS_UO_NO_RP = 2;
	private final char US_RS_TO_RP = 3;
	private final char US_RS_UO_RO_RP = 4;
	private final char US_RS_UO_NO_RP = 5; 
	private final char US_NS_TO_RP = 6;
	private final char US_NS_UO_RO_RP = 7;
	private final char US_NS_UO_NO_RP = 8;
	private final char TS_UO_RO_NP = 10;
	private final char TS_UO_NO_NP = 11;
	private final char US_RS_TO_NP = 12;
	private final char US_RS_UO_RO_NP = 13;
	private final char US_RS_UO_NO_NP = 14; 
	private final char US_NS_TO_NP = 15;
	private final char US_NS_UO_RO_NP = 16;
	private final char US_NS_UO_NO_NP = 17;
	
	
	String caseName(char c){
		switch(c){
			case TS_TO: { return "TS_TO"; }
			case TS_UO_RO_RP: { return "TS_UO_RO_RP"; }
			case TS_UO_NO_RP: { return "TS_UO_NO_RP"; }
			case US_RS_TO_RP: { return "US_RS_TO_RP"; }
			case US_RS_UO_RO_RP: { return "US_RS_UO_RO_RP"; }
			case US_RS_UO_NO_RP: { return "US_RS_UO_NO_RP"; }
			case US_NS_TO_RP: { return "US_NS_TO_RP"; }
			case US_NS_UO_RO_RP: { return "US_NS_UO_RO_RP"; }
			case US_NS_UO_NO_RP: { return "US_NS_UO_NO_RP"; }
			case TS_UO_RO_NP: { return "TS_UO_RO_NP"; }
			case TS_UO_NO_NP: { return "TS_UO_NO_NP"; }
			case US_RS_TO_NP: { return "US_RS_TO_NP"; }
			case US_RS_UO_RO_NP: { return "US_RS_UO_RO_NP"; }
			case US_RS_UO_NO_NP: { return "US_RS_UO_NO_NP"; }
			case US_NS_TO_NP: { return "US_NS_TO_NP"; }
			case US_NS_UO_RO_NP: { return "US_NS_UO_RO_NP"; }
			case US_NS_UO_NO_NP: { return "US_NS_UO_NO_NP"; }
		}
		throw new Error("Unrecognized case " + c); 
	}
	
	private char decode(Long classSetS, Long classSetO, Long sourceCliqueS, Long sourceCliqueO, Long sourceCliqueP){
		if (classSetS != null){ // TS
			if (classSetO != null){ // TS, TO
				return TS_TO; 
			}
			else{//TS, UO
				if (sourceCliqueO != null){// TS, UO, RO
					if (sourceCliqueP != null){// TS, UO, RO, RP
						return TS_UO_RO_RP; 
					}
					else{// TS, UO, RO, NP
						return TS_UO_RO_NP; 
					}
				}
				else{ // TS, UO, NO
					if (sourceCliqueP != null){ // TS, UO, NO, RP
						return TS_UO_NO_RP;
					}
					else { // TS, UO, NO, NP
						return TS_UO_NO_NP; 
					}
				}
			}
		}
		else // US
		{
			if (sourceCliqueS != null){// US, RS
				if (classSetO != null){ // US, RS, TO
					if (sourceCliqueP != null){// US, RS, TO, RP
						return US_RS_TO_RP;
					}
					else{ // US, RS, TO, NP
						return US_RS_TO_NP; 
					}
				}
				else{ // US, RS, UO
					if (sourceCliqueO != null){ //US, RS, UO, RO
						if (sourceCliqueP != null){ //US, RS, UO, RO, RP
							return US_RS_UO_RO_RP; 
						}
						else{ //US, RS, UO, RO, NP
							return US_RS_UO_RO_NP; 
						}
					}
					else{// US, RS, UO, NO
						if (sourceCliqueP != null){ // US, RS, UO, NO, RP
							return US_RS_UO_NO_RP;
						}
						else{ // US, RS, UO, NO, NP 
							return US_RS_UO_NO_NP; 
						}
					}
				}
			}
			else{// US, NS
				if (classSetO != null){ // US, NS, TO
					if (sourceCliqueP != null){// US, NS, TO, RP
						return US_NS_TO_RP;
					}
					else{ // US, NS, TO, NP
						return US_NS_TO_NP; 
					}
				}
				else{ // US, NS, UO
					if (sourceCliqueO != null){ //US, NS, UO, RO
						if (sourceCliqueP != null){ //US, NS, UO, RO, RP
							return US_NS_UO_RO_RP; 
						}
						else{ //US, NS, UO, RO, NP
							return US_NS_UO_RO_NP; 
						}
					}
					else{// US, NS, UO, NO
						if (sourceCliqueP != null){ // US, NS, UO, NO, RP
							return US_NS_UO_NO_RP;
						}
						else{ // US, NS, UO, NO, NP 
							return US_NS_UO_NO_NP; 
						}
					}
				}
			}
		}
	}
	
	public StrongSummarization(){
		super(); 
		sc = new Long2LongSet();
		tc = new Long2LongSet();
		cs = new Long2LongSet();
		n2sc = new Long2Long();
		n2tc = new Long2Long();
		n2cs = new Long2Long();
		p2sc = new Long2Long();
		p2tc = new Long2Long();
		c2cs = new Long2Long();
		rep = new Long2Long();
		untypedSummaryNodes = new HashMap<Long, HashMap<Long, Long>>();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE; 
		firstType=true;
		numberOfDataTriplesRead=0;
		numberOfTypeTriplesRead=0; 
	}

	/**
	 * Paranoid method for safety check. Throws an error is something is not coherent across the 
	 * data structures.
	 */
	public void cliqueSafetyCheck(){
		if (n2sc.getNodes().size() != n2tc.getNodes().size()){
			throw new Error("n2sc has " + n2sc.getNodes().size() + " while n2tc has " + 
					n2tc.getNodes().size() + " entries"); 
		}
		if (n2sc.getNodes().size() != rep.getNodes().size()){
			throw new Error("n2sc has " + n2sc.getNodes().size() + " while rep has " + 
					rep.getNodes().size() + " entries"); 
		}
		if (rep.getNodes().size() != n2tc.getNodes().size()){
			throw new Error("rep has " + rep.getNodes().size() + " while n2tc has " + 
					n2tc.getNodes().size() + " entries"); 
		}
		if (p2sc.getNodes().size() != p2tc.getNodes().size()){
			display();
			throw new Error("After " + this.numberOfDataTriplesRead + " data triples, " + 
					p2sc.getNodes().size() + " properties have source cliques while " +
					p2tc.getNodes().size() + " properties have target cliques "); 
		}
		// there is no reason why numbers of source cliques may be equal to numbers of target cliques
		//
		// The number of source and target clique in untypedSummaryNodes may be less than those in p2tc, p2sc, n2tc, n2sc.
		// This is because typed nodes may be source or target of a data property and in this case, a source (target) clique is created for the data property, but is not associated to any node,
		// as typed nodes do not have source/target cliques. 		
	}

	private long countDistinctTargetCliquesInCliqueToNodesMap() {
		TreeSet<Long> uniqueTCs = new TreeSet<Long>();
		for (Long sourceClique: untypedSummaryNodes.keySet()){
			HashMap<Long, Long> map = untypedSummaryNodes.get(sourceClique); 
			for (Long targetClique: map.keySet()){
				if (targetClique == this.emptyTCCount){
					continue;  // not counting the empty tc because it does not appear in p2tc
				}
				if (!uniqueTCs.contains(targetClique)){
					uniqueTCs.add(targetClique); 
				}
			}
		}
		return uniqueTCs.size(); 
	}

	private long countDistinctSourceCliquesInCliqueToNodesMap() {
		return this.untypedSummaryNodes.keySet().size(); // this does not count the empty sc
	}



	public void display(){
		System.out.println("TYPED STRONG SUMMARY\nClass to class set IDs:");
		//c2cs.display();
		System.out.println("Class set IDs to class sets:");
		//cs.display();
		System.out.println("Nodes to class set IDs");
		//n2cs.display();
		System.out.println("Source cliques:");
		sc.display();
		System.out.println("Target cliques:");
		tc.display();
		System.out.println("Nodes to source cliques");
		n2sc.display();
		System.out.println("Nodes to target cliques");
		n2tc.display();
		System.out.println("Property to source cliques: ");
		p2sc.display();
		System.out.println("Property to target cliques: ");
		p2tc.display();
		System.out.println("Representation function for untyped nodes: ");
		showRep();
		System.out.println("Summary: ");
		for (Triple t: this.getSummaryEdges()){
			t.display();
		}
	}

	public void showRepThroughCliques(){
		StringBuffer sb = new StringBuffer();
		sb.append("n2sc: ");
		for (Long node: n2sc.getNodes()){
			if (node == null){
				throw new Error("Null node"); 
			}
			Long thisNodeSC = n2sc.get(node);
			if (thisNodeSC == null){
				throw new Error("Null source clique");
			}
			Long thisNodeTC = n2tc.get(node);
			if (thisNodeTC == null){
				throw new Error("Null target clique for " + node); 
			}
			Long summaryNode = untypedSummaryNodes.get(thisNodeSC).get(thisNodeTC);
			if(summaryNode == null){
				throw new Error("Null summary node");
			}
			sb.append(node + "->" + summaryNode+ " ");
		}
		System.out.println(sb); 	
	}

	public void handleTypeTripleBeforeData(Triple t){
		//display();
		if (firstType){
			TYPE = t.p;
			firstType=false; 
		}
		Long classSetIDForThisType = c2cs.get(t.o);
		if (classSetIDForThisType == null){
			classSetIDForThisType = new Long(maxSummaryNode);
			c2cs.put(new Long(t.o), classSetIDForThisType);
			Debugger.log("Created class set ID " + classSetIDForThisType + " for type " + t.o);
			maxSummaryNode++;
		}
		// now classSetIDForThisType exists
		// create the class set for this type if necessary:
		ArrayList<Long> thisTypeClassSet = cs.get(classSetIDForThisType);
		if (thisTypeClassSet == null){
			thisTypeClassSet = new ArrayList<Long>();
			cs.put(classSetIDForThisType, thisTypeClassSet);

		}
		// add the type to this class set if necessary:
		if (!(thisTypeClassSet.contains(t.o))){
			Debugger.log("Added type " + t.o + " at class set " + thisTypeClassSet);
			thisTypeClassSet.add(t.o);
		}
		// If the node had another class set before, fuse
		Long thisNodeClassSetID = n2cs.get(new Long(t.s));

		if (thisNodeClassSetID != null){ // the class sets need to be fused
			ArrayList<Long> thisNodeClassSet = cs.get(thisNodeClassSetID);
			if (thisNodeClassSetID < classSetIDForThisType){
				Debugger.log("(1) Fusing class set " + classSetIDForThisType + " into " + thisNodeClassSetID);
				// unify both into thisNodeClassSetID:
				// - thisNodeClassSet gets all the properties of thisTypeCS
				for (Long cl: thisTypeClassSet){
					thisNodeClassSet.add(cl);
				}
				// in this case, we are updating the class set of the o class
				c2cs.put(t.o, thisNodeClassSetID);
				// and the class set (thus, representative) of this node: 
				n2cs.replaceValue(classSetIDForThisType, thisNodeClassSetID);
				//n2cs.put(t.s, thisNodeClassSetID);
				// all the nodes previously attached to thisNodeClassSetID need to change
				cs.fuseKeyInto(classSetIDForThisType, thisNodeClassSetID);
			}
			if (thisNodeClassSetID > classSetIDForThisType){
				Debugger.log("(2) Fusing class set " + thisNodeClassSetID + " into " + classSetIDForThisType);
				// unify both into classSetIDForThisType:
				// - classSetIDForThisType gets all the properties of thisNodeClassSet
				for (Long cl: thisNodeClassSet){
					thisTypeClassSet.add(cl);
				}
				// - replace the class set in cs:
				cs.remove(thisNodeClassSetID);
				n2cs.replaceValue(thisNodeClassSetID, classSetIDForThisType);
				//n2cs.put(t.s, classSetIDForThisType);
				// all the nodes previously attached to thisNodeClassSetID need to change
				cs.fuseKeyInto(thisNodeClassSetID, classSetIDForThisType);
			}
		}
		else{ // the node did not have another class set before
			n2cs.put(new Long(t.s), classSetIDForThisType);
		}
		//display();
		this.numberOfTypeTriplesRead ++; 
	}

	/**
	 * Updates sc, n2sc, untypedSummaryNodes, summary (edges) 
	 * @param sourceCliqueOld
	 * @param sourceCliqueNew
	 */
	private void fuseSourceCliques(Long sourceCliqueOld, Long sourceCliqueNew){
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
	 * Updates tc, n2tc, untypedSummaryNodes, summary (edges)
	 * @param sourceCliqueOld
	 * @param sourceCliqueNew
	 */
	private void fuseTargetCliques(Long targetCliqueOld, Long targetCliqueNew){
		Debugger.log("FuseTargetCliques: " + targetCliqueOld + " into " + targetCliqueNew);
		if (targetCliqueNew == this.emptyTCCount){
			throw new Error("Should not use the empty target clique in a place where we had something else"); 
		}
		// Do not display here as this requires rep to be fully filled and rep cannot be filled for new nodes before the fusion. So some nodes may be missing.
		//display();
		// then make targetCliqueO the same as targetCliqueP (keep smaller)
		// add properties of target clique of o, to those of the target clique of p
		ArrayList<Long> realTCNew = this.tc.get(targetCliqueNew);
		ArrayList<Long> realTCOld = this.tc.get(targetCliqueOld);

		if (realTCNew == null){
			this.tc.display();
			throw new Error("After reading " + this.numberOfDataTriplesRead + 
					" data triples, there is no clique on the new target clique " + targetCliqueNew); 

		}

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

	public void handleDataTriple(Triple t){
		// 18 cases: (TS, USR, USN) x (TO, UOR, UON) x (PR, PN)  also multiplied by: which cliques are empty and their consequences on fusion
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);

		Long sourceCliqueS = n2sc.get(t.s);
		Long targetCliqueS = n2tc.get(t.s);
		Long sourceCliqueO = n2sc.get(t.o);
		Long targetCliqueO = n2tc.get(t.o);

		Long sourceCliqueP = p2sc.get(t.p);
		Long targetCliqueP = p2tc.get(t.p);

		checkSymmetry(sourceCliqueS, targetCliqueS, sourceCliqueO, targetCliqueO, sourceCliqueP, targetCliqueP); 
		
		char caseNumber = decode(classSetS, classSetO, sourceCliqueS, sourceCliqueO, sourceCliqueP); 
		
		switch(caseNumber){
		case TS_TO: { handleDataTriple_TS_TO(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case TS_UO_RO_RP: { handleDataTriple_TS_UO_RO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case TS_UO_NO_RP: { handleDataTriple_TS_UO_NO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_RS_TO_RP: { handleDataTriple_US_RS_TO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_RS_UO_RO_RP: { handleDataTriple_US_RS_UO_RO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_RS_UO_NO_RP: { handleDataTriple_US_RS_UO_NO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_NS_TO_RP: { handleDataTriple_US_NS_TO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_NS_UO_RO_RP: { handleDataTriple_US_NS_UO_RO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_NS_UO_NO_RP: { handleDataTriple_US_NS_UO_NO_RP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case TS_UO_RO_NP: { handleDataTriple_TS_UO_RO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case TS_UO_NO_NP: { handleDataTriple_TS_UO_NO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_RS_TO_NP: { handleDataTriple_US_RS_TO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_RS_UO_RO_NP: { handleDataTriple_US_RS_UO_RO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_RS_UO_NO_NP: { handleDataTriple_US_RS_UO_NO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_NS_TO_NP: { handleDataTriple_US_NS_TO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_NS_UO_RO_NP: { handleDataTriple_US_NS_UO_RO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
		case US_NS_UO_NO_NP: { handleDataTriple_US_NS_UO_NO_NP(t, classSetS, classSetO, sourceCliqueS, sourceCliqueO, targetCliqueS, targetCliqueO, sourceCliqueP, targetCliqueP); }
	}
		
		if (classSetS != null){ // typed subject
			if (classSetO != null){ // typed object
				// TS_TO
			}
			else{ // TS, UO
				// try to find the representative of the object, if it is known: can be rep(o), or (sc(p), tc(p))
				Long repO = rep.get(t.o);
				if (repO == null){ // not already represented as an untyped node
					Debugger.log("=== TS/   UO/NO === Typed subject: " + t.s + " untyped object: " + t.o + " not already represented");
					// sourceCliqueO needs to be created, and is empty as far as we know (o has no outgoing edge so far)
					sourceCliqueO = this.getEmptySourceCliqueID();			
					// associate o to its source clique
					n2sc.put(t.o, sourceCliqueO);
					// targetCliqueO needs to be created or taken from targetCliqueP
					if (targetCliqueP != null){ // take the property's target clique if known
						targetCliqueO = targetCliqueP; // no need to update the target clique of P, it already has p
					}
					else{
						targetCliqueO = this.makeAndAddNewTargetClique(t.p);
					}
					if (sourceCliqueP == null){
						sourceCliqueP = this.getEmptySourceCliqueID(); 
						p2sc.put(t.p, sourceCliqueP);
					}
					// associate o to its target clique
					n2tc.put(t.o, targetCliqueO);
					// get or create summary node 
					Long targetSummaryNode = getOrCreateSummaryNode(sourceCliqueO, targetCliqueO); 
					Debugger.log("(1) Created summary node " + targetSummaryNode + " for source clique " + sourceCliqueO + " and target clique " + targetCliqueO);
					rep.put(t.o,  targetSummaryNode);
					this.addTriple(classSetS, t.p, targetSummaryNode);
				}
				else{ 
					System.out.println("=== TS/   UO/RO === Typed subject: " + t.s + " untyped object: " + t.o + " already represented");
					// we just need to add a triple and possibly fuse cliques
					if (targetCliqueP != null){ // if p already known
						System.out.println("Property " + t.p + " already encountered in data triples");
						if (targetCliqueO != this.emptyTCCount){
							if (targetCliqueP < targetCliqueO){// then fuse targetCliqueP into targetCliqueO (keep the one created first; in our setting, this means the highest, because they are all negative)
								// add properties of target clique of o, to those of the target clique of p
								this.fuseTargetCliques(targetCliqueP, targetCliqueO); 
								// add a new triple in the summary if needed, use the representative of t.o
								Debugger.log("Creating summary triple 0.8"); 
								this.addTriple(classSetS, t.p, repO); 
								// update the summary if needed
								replaceCliqueInSummary(targetCliqueP, targetCliqueO);

							}
							else {
								if (targetCliqueP > targetCliqueO){// then fuse targetCliqueO into targetCliqueP (keep the one created first, that is, the highest) 
									this.fuseTargetCliques(targetCliqueO, targetCliqueP);

									// add the new triple to the summary if needed
									Debugger.log("Creating summary triple 1"); 
									addTriple(classSetS, t.p, repO); 
									// update the summary if needed
									replaceCliqueInSummary(targetCliqueO, targetCliqueP);
								}
								else { // targetCliqueP was already targetCliqueO; nothing to do
								}
							}
						}
						else { // targetCliqueO is the empty target clique (o was thought with no input edges so far). Then, o should copy the clique of p and no other fusion should happen.
							targetCliqueO = targetCliqueP;
							this.n2tc.put(t.o, targetCliqueO);
							addTriple(classSetS, t.p, repO); 
						}
					}
					else{ // targetCliqueP was null, but targetCliqueO was not
						Debugger.log(t.p + " had no target clique so far; it will take the target clique of " + t.o + " namely " + targetCliqueO);
						// targetCliqueP becomes targetCliqueO 
						// add p to the target clique of o
						ArrayList<Long> realTCO = this.tc.get(targetCliqueO);
						if (!realTCO.contains(t.p)){
							realTCO.add(t.p);
						}
						// update p2tc to reflect the clique of p
						this.p2tc.put(t.p, targetCliqueO);
						// no need to update n2tc as the target clique of o has not changed

						// p should get an empty source clique:
						Long emptySourceCliqueP = this.getEmptySourceCliqueID();
						p2sc.put(t.p, emptySourceCliqueP);
						// get or create the new target node (probably a creation): 
						Long targetSummaryNode = getOrCreateSummaryNode(emptySourceCliqueP, targetCliqueO);
						rep.put(t.o, targetSummaryNode);
						// add the new triple to the summary if needed
						Debugger.log("Creating summary triple 1.5"); 
						addTriple(classSetS, t.p, targetSummaryNode); 
						// we do not update the summary as targetCliqueP is still null
					}
				}
			}
		}
		else{// untyped subject
			if (classSetO != null){ // typed object
				// US, TO
				// try to find the representative of the subject, if it is known: can be rep(s), or (sc(p), tc(p))
				Long repS = rep.get(t.s);
				if (repS == null){ 
					Debugger.log("=== US/   UO/TO === Untyped subject: " + t.s + ", not already represented; typed object: " + t.o);

					if (sourceCliqueS != null || targetCliqueS != null){
						throw new Error("The node is not represented yet but its cliques are known");
					}
					// create/identify the source and target cliques for s:
					// sourceCliqueS needs to be created or taken from sourceCliqueP
					if (sourceCliqueP != null){ // take the property's source clique if known
						sourceCliqueS = sourceCliqueP;
						Debugger.log("Source clique of s taken from " + sourceCliqueP + " of property " + t.p);
						// no need to update the source clique of P, it already has p
					}
					else{
						// we invent a new source clique
						sourceCliqueS = this.makeNewSourceClique(t.p); 
						sourceCliqueP = sourceCliqueS; 
					}
					// associate s to its source clique
					n2sc.put(t.s, sourceCliqueS);

					// targetCliqueS needs to be created; empty for now, for all we know
					targetCliqueS = this.getEmptyTargetCliqueID(); 
					// associate s to its source clique
					n2tc.put(t.s, targetCliqueS);

					if (targetCliqueP == null){
						// p is alone in its target clique for all we know now.  
						targetCliqueP = this.makeAndAddNewTargetClique(t.p);
						// the target of p is a typed node in this case, and a typed node does not get associated target cliques. 
						// Only the untyped source and target nodes of data properties lead to cliques for them. 
					}

					// we need to create the source node; the target node is the representative of the typed node, anyway
					Long sourceSummaryNode = getOrCreateSummaryNode(sourceCliqueS, targetCliqueS); 
					rep.put(t.s, sourceSummaryNode);
					Debugger.log("Creating summary triple 2"); 
					addTriple(sourceSummaryNode, t.p, classSetO);
				}
				else{ // Untyped s, already represented; typed object 
					// then s has a source clique and a target clique
					Debugger.log("=== US/RS/TO    === Untyped subject: " + t.s + ", already represented; typed object: " + t.o);
					if ((sourceCliqueS == null) || (targetCliqueS == null)){
						throw new Error("Empty source or target clique for an untyped subject!"); 
					}
					if (sourceCliqueP != null){ // if p already had a source clique
						if (sourceCliqueP > sourceCliqueS){// then make sourceCliqueS the same as sourceCliqueP (keep the one created first, thus higher)
							this.fuseSourceCliques(sourceCliqueS, sourceCliqueP);
							this.n2sc.put(t.s, sourceCliqueP);
							// create new summary triple: 
							Long sourceSummaryNode = getOrCreateSummaryNode(sourceCliqueP, targetCliqueP); 
							rep.put(t.s, sourceSummaryNode);
							// add a new triple in the summary if needed
							Debugger.log("Creating summary triple 3"); 
							addTriple(sourceSummaryNode, t.p, classSetO); 
							// update the summary if needed
							replaceCliqueInSummary(sourceCliqueS, sourceCliqueP);
						}
						else {
							if (sourceCliqueP < sourceCliqueS){// then make sourceCliqueP the same as sourceCliqueS (keep the one created first, thus higher)
								// add properties of source clique of p, to those of the source clique of s
								this.fuseSourceCliques(sourceCliqueP, sourceCliqueS);
								// no need to update n2sc as the source clique of s has not changed
								// create new summary triple: 
								Long sourceSummaryNode = getOrCreateSummaryNode(sourceCliqueS, targetCliqueS); 
								rep.put(t.s, sourceSummaryNode);
								// add the new triple to the summary if needed
								Debugger.log("Creating summary triple 4"); 
								addTriple(sourceSummaryNode, t.p, classSetO); 
								// update the summary if needed
								replaceCliqueInSummary(sourceCliqueP, sourceCliqueS);
							}
							else { // sourceCliqueP was already sourceCliqueO; nothing to do
							}
						}
					}
				}
			}
			else{
				// # US, UO: untyped subject, untyped object
				Long repS = rep.get(t.s);
				Long repO = rep.get(t.o);

				Debugger.log(t.s + " represented by " + repS + " " + t.o + " represented by " + repO); 

				if (repS != null){
					if (repO != null){ 
						Debugger.log("=== US/RS/UO/RO === Untyped subject: " + t.s + ", already represented; untyped object: " + t.o + ", already represented");
						if ((sourceCliqueS == null) || (targetCliqueS == null) || (sourceCliqueO == null) || (targetCliqueO == null)){
							throw new Error("Empty source/target cliques for represented nodes!");
						}
						// # USR, UOR: both nodes are untyped and have been represented before
						// we need to see whether this edge changes the source clique of s or the target clique of p
						if (sourceCliqueP != sourceCliqueS){
							if (sourceCliqueP == null){ // p has not been seen before; it should join the source clique of S
								addPropertyToSourceClique(t.p, sourceCliqueS); 
							}
							else{// not null but different --> need to fuse
								if (sourceCliqueP != sourceCliqueS){
									fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
									//p2sc.put(t.p, new Long(Math.min(sourceCliqueS,  sourceCliqueP)));
								}
							}
							if (targetCliqueP != targetCliqueO){ // but p is not in the target clique of O
								if(targetCliqueP == null){
									addPropertyToTargetClique(t.p, targetCliqueO);
								}
								else{
									fuseCliquesIntoCreatedFirst(targetCliqueP, targetCliqueO, TARGET); 
									//p2tc.put(t.p, new Long(Math.min(targetCliqueP, targetCliqueO)));
								}
							}
						}
						else{// p is already in the source clique of s
							if (targetCliqueP != targetCliqueO){ // but p is not in the target clique of O
								if(targetCliqueP == null){
									addPropertyToTargetClique(t.p, targetCliqueO);
								}
								else{
									fuseCliquesIntoCreatedFirst(targetCliqueP, targetCliqueO, TARGET); 
									//p2tc.put(t.p, new Long(Math.min(targetCliqueP, targetCliqueO)));
								}
							}
							else{
								// p is already in the source clique of s and in the target clique of p, no clique fusion needed
								// thus this triple makes no change to the representation of s and o, and no change whatsoever
							}
						}
						addTriple(repS, t.p, repO); 
					}
					else{
						Debugger.log("=== US/RS/UO/NO === Untyped subject: " + t.s + ", already represented; untyped object: " + t.o + ", not already represented");
						// # USR, UON: both  nodes are untyped, s has been represented but not o
						// the source clique of o is the empty one so far
						sourceCliqueO = getEmptySourceCliqueID(); 
						n2sc.put(t.o, sourceCliqueO);
						// the target clique is either copied from that of p, or initialized 
						if (targetCliqueP == null){// p has never been seen before
							targetCliqueP = makeAndAddNewTargetClique(t.p); 
						}
						if (sourceCliqueP == null){
							sourceCliqueP = makeNewSourceClique(t.p);
						}
						Long fusedSourceClique;
						if(sourceCliqueS != this.emptySCCount){
							fusedSourceClique = fuseCliquesIntoCreatedFirst(sourceCliqueS, sourceCliqueP, SOURCE);
							if (!sourceCliqueP.equals(fusedSourceClique)){
								sourceCliqueP = fusedSourceClique;
								p2sc.put(t.p, fusedSourceClique);
							}
						}
						else{
							n2sc.put(t.o, sourceCliqueP);
						}
						targetCliqueO = targetCliqueP;
						n2tc.put(t.o, targetCliqueO);
						Long newTargetNode = getOrCreateSummaryNode(sourceCliqueO, targetCliqueO);
						Debugger.log("New node: " + newTargetNode);
						rep.put(t.o,  newTargetNode);
						Debugger.log("Creating summary triple 5"); 
						addTriple(repS, t.p, newTargetNode);
					}
				}
				else{ 
					if (repO != null){
						Debugger.log("=== US/NS/UO/RO === Untyped subject: " + t.s + ", not already represented; untyped object: " + t.o + ", already represented");
						Debugger.log("Node " + t.o + " represented by " + repO + " or " + rep.get(t.o) + "  target clique " + n2tc.get(t.o));

						// # USN, UOR: both nodes are untyped, s has not been represented, o has
						// the target clique of s is the empty one so far 
						targetCliqueS = getEmptyTargetCliqueID(); 
						n2tc.put(t.s,  targetCliqueS);

						// the source clique of s is either copied from that of p, or initialized 
						if (sourceCliqueP == null){// p has never been seen before
							sourceCliqueP = makeNewSourceClique(t.p); 
						}
						// s gets its source clique from P
						sourceCliqueS = sourceCliqueP; 
						n2sc.put(t.s, sourceCliqueS);

						// create p's target clique if needed
						if (targetCliqueP == null){
							targetCliqueP = makeAndAddNewTargetClique(t.p);
						}
						Debugger.log("Source clique o: " + sourceCliqueO + " target clique o: " + targetCliqueO + " source clique p: " + sourceCliqueP + " target clique p: " + targetCliqueP);

						Long targetFusedClique; 
						if (targetCliqueO == this.emptyTCCount){
							targetFusedClique = targetCliqueP; 
						}
						else{
							targetFusedClique = fuseCliquesIntoCreatedFirst(targetCliqueP, targetCliqueO, TARGET);
							if (!targetFusedClique.equals(targetCliqueP)){
								targetCliqueP = targetFusedClique; 
								p2tc.put(t.p, targetCliqueP);
							}
						}
						n2tc.put(t.s, targetCliqueS);

						Long newSourceNode = getOrCreateSummaryNode(sourceCliqueS, targetCliqueS);
						rep.put(t.s,  newSourceNode);
						Debugger.log("Creating summary triple 6"); 
						addTriple(newSourceNode, t.p, repO);
					}
					else{
						Debugger.log("=== US/NS/UO/NO === Untyped subject: " + t.s + ", not already represented; untyped object: " + t.o + ", not already represented");
						// # USN, UON: both nodes are untyped and none has been represented
						// we need to figure out if the source clique of P and the target clique of P are known
						if (sourceCliqueP == null){
							sourceCliqueP = makeNewSourceClique(t.p);
						}	
						sourceCliqueS = sourceCliqueP;
						targetCliqueS = getEmptyTargetCliqueID();
						n2sc.put(t.s, sourceCliqueS);
						n2tc.put(t.s, targetCliqueS);
						long newSourceNode = getOrCreateSummaryNode(sourceCliqueS, targetCliqueS);
						rep.put(t.s, newSourceNode);

						sourceCliqueO = getEmptySourceCliqueID();
						if (targetCliqueP == null){
							targetCliqueP = makeAndAddNewTargetClique(t.p);
						}
						targetCliqueO = targetCliqueP;
						n2sc.put(t.o, sourceCliqueO);
						n2tc.put(t.o, targetCliqueO);
						long newTargetNode = getOrCreateSummaryNode(sourceCliqueO, targetCliqueO);
						rep.put(t.o, newTargetNode);
						Debugger.log("Creating summary triple 7"); 
						addTriple(newSourceNode, t.p, newTargetNode); 
					}
				}
			}
		}
		this.numberOfDataTriplesRead++;

		this.cliqueSafetyCheck();
	}

	private void handleDataTriple_TS_UO_RO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TS --> no need to use cliques for s; UO, RO --> o is already represented; RP --> we already have cliques for P
		
	}

	private void handleDataTriple_US_NS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_NS_UO_RO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_NS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_RS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_RS_UO_RO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_RS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_TS_UO_NO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_TS_UO_RO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_NS_UO_RO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_TS_TO_NP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_NS_UO_NO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_NS_TO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_RS_UO_RO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_RS_UO_NO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_US_RS_TO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}

	private void handleDataTriple_TS_UO_NO_RP(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// TODO Auto-generated method stub
		
	}


	private void handleDataTriple_TS_TO(Triple t, Long classSetS, Long classSetO, Long sourceCliqueS,
			Long sourceCliqueO, Long targetCliqueS, Long targetCliqueO, Long sourceCliqueP, Long targetCliqueP) {
		// add the edge to the summary
		this.addTriple(classSetS, t.p, classSetO);
	}

	private void checkSymmetry(Long sourceCliqueS, Long targetCliqueS, Long sourceCliqueO, Long targetCliqueO,
			Long sourceCliqueP, Long targetCliqueP) {
		if ( (sourceCliqueS == null && targetCliqueS != null) || (sourceCliqueS != null && targetCliqueS == null)){
			throw new Error("Subject has only one of the two cliques"); 
		}
		if ( (sourceCliqueO == null && targetCliqueO != null) || (sourceCliqueO != null && targetCliqueO == null)){
			throw new Error("Object has only one of the two cliques"); 
		}
		if ( (sourceCliqueP == null && targetCliqueP != null) || (sourceCliqueP != null && targetCliqueP == null)){
			throw new Error("Property has only one of the two cliques"); 
		}
	}

	/**
	 * Creates a new (untyped) summary node and inserts it into untypedSummaryNodes
	 * @param sourceClique
	 * @param targetClique
	 * @return
	 */
	private Long getOrCreateSummaryNode(Long sourceClique, Long targetClique) {
		assert(sourceClique != null & targetClique != null); 
		HashMap<Long, Long> targetCliquesForThisSourceClique = this.untypedSummaryNodes.get(sourceClique); 
		if (targetCliquesForThisSourceClique == null){
			targetCliquesForThisSourceClique = new HashMap<Long, Long>();
			this.untypedSummaryNodes.put(sourceClique, targetCliquesForThisSourceClique); 
		}
		Long node = targetCliquesForThisSourceClique.get(targetClique);
		if (node == null){
			Debugger.log("Created " + this.maxSummaryNode + " for source clique " + sourceClique + " and target clique " + targetClique); 
			node = getNextSummaryNode(); // from the parent method
			this.untypedSummaryNodes.get(sourceClique).put(targetClique, node);
		}
		return node; 
	}

	private Long getEmptySourceCliqueID() {
		Long res; 
		if (this.emptySCCount == Long.MAX_VALUE){ // the empty source clique has not been created yet
			ArrayList<Long> emptySC = new ArrayList<Long>();
			res = minCliqueID; // we invent a new source clique
			Debugger.log("ooooo> Initialized the empty source clique at: " + res);
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

	private Long getEmptyTargetCliqueID() {
		Long res; 
		if (this.emptyTCCount == Long.MAX_VALUE){ // the empty source clique has not been created yet
			ArrayList<Long> emptyTC = new ArrayList<Long>();
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
	 */
	private Long fuseCliquesIntoCreatedFirst(Long c1, Long c2, char code) {
		if (code == SOURCE){
			if (c2 == this.emptySCCount){
				throw new Error("Do not replace with empty source clique!"); 
			}
			if (c1 > c2){
				// this method treats its first parameter as "old" and the second as "new" 
				fuseSourceCliques(c2, c1);
				return c1; 
			}
			else{
				if (c2 > c1){
					fuseSourceCliques(c1, c2);
					return c2; 
				}
			}
		}
		else{
			if (code == TARGET){
				if (c2 == this.emptyTCCount){
					throw new Error("Do not replace with empty target clique!"); 
				}
				if (c1 > c2){
					// this method treats its first parameter as "old" and the second as "new" 
					fuseTargetCliques(c2, c1);
					return c1; 
				}
				else{
					if (c2 > c1){
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

	/**
	 * Creates a new source clique with just p; updates sc
	 * @param p
	 * @return
	 */
	private Long makeNewSourceClique(Long p){
		Long res = new Long(this.minCliqueID);
		ArrayList<Long> actualSourceClique = new ArrayList<Long>(); 
		actualSourceClique.add(p);
		sc.put(res, actualSourceClique);
		p2sc.put(p,  res);
		Debugger.log("Added the new source clique for: " + res + " with property " + p);
		minCliqueID --;
		return res; 
	}
	/**
	 * Adds p to the source clique indicated by sourceCliqueID; updates p2sc and sc
	 * @param p
	 * @param sourceCliqueID
	 */
	private void addPropertyToSourceClique(Long p, Long sourceCliqueID) {
		p2sc.put(p, sourceCliqueID);
		if (!(sc.get(sourceCliqueID).contains(p))){
			sc.get(sourceCliqueID).add(p);
		}		
	}

	/**
	 * Initializes a target clique for property p
	 * Also records the association between p and this target clique in p2c and tc
	 * @param p
	 * @return
	 */
	private Long makeAndAddNewTargetClique(Long p){		
		Long targetCliqueID = new Long(this.minCliqueID);
		ArrayList<Long> actualTargetClique = new ArrayList<Long>(); 
		actualTargetClique.add(p);
		tc.put(targetCliqueID, actualTargetClique);
		p2tc.put(p,  targetCliqueID);
		Debugger.log("Added the new target clique for: " + targetCliqueID + " with property " + p);
		minCliqueID --;
		return targetCliqueID; 
	}

	/**
	 * Adds p to the target clique indicated by targetCliqueID
	 * @param p
	 * @param targetCliqueID
	 */
	private void addPropertyToTargetClique(Long p, Long targetCliqueID) {
		p2tc.put(p, targetCliqueID);
		if (!(tc.get(targetCliqueID).contains(p))){
			tc.get(targetCliqueID).add(p);
		}		
	}


	/**
	 * Replaces a clique ID with another cliqueID in the summary
	 * 
	 * Modifies untypedSummaryNodes and (indirectly) the summary edges
	 * 
	 * For simplicity (and in a somehow violent manner), it replaces either a source clique or a target clique
	 * Thus it is important that oldCliqueID is not allowed to match both a source clique and a target clique, because we usually only want to replace one.
	 * @param oldCliqueID
	 * @param newCliqueID
	 */
	private void replaceCliqueInSummary(Long oldCliqueID, Long newCliqueID) {		
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
				tcToNodesForNewSC = new HashMap<Long, Long>();
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

	public void typedStrongPostHandleTypeTriples() {
		for (Long classSetCode: this.cs.keys()){	
			for (Long thisClass: this.cs.get(classSetCode)){
				this.addTriple(classSetCode, TYPE, thisClass);
			}
		}
	}

	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile, String method) throws IOException{
		long start = System.currentTimeMillis(); 
		if (method.equals("typedstrong")){
			// First file: type triples
			BufferedReader br = new BufferedReader(
					new FileReader(
							new File(typeTriplesFile)));
			while (br.ready()){
				String spo = br.readLine();
				Triple t = readTriple(spo);
				//t.display();
				handleTypeTripleBeforeData(t);
				//System.out.println();
			}
			br.close();
			typedStrongPostHandleTypeTriples();
			//System.out.println("=== After typed strong type triple summarization: =================================== ");
			//display();
			//  Second file: data triples	
			br = new BufferedReader(
					new FileReader(
							new File(dataTriplesFile)));
			while (br.ready()){
				String spo = br.readLine();
				Triple t = readTriple(spo);
				//System.out.println("\nLooking at data triple:");
				//t.display();
				handleDataTriple(t);
				//display();
				//System.out.println();
			}
			br.close();
			//System.out.println("After typed strong data triple summarization: ");
			//display();
			long stop = System.currentTimeMillis();
			System.out.println("Typed strong summarization took: "+ (stop - start));
			System.out.println(this.toString());
		}
		if (method.equals("strong")){
			//  Second file: data triples	
			BufferedReader	br = new BufferedReader(
					new FileReader(
							new File(dataTriplesFile)));
			while (br.ready()){
				String spo = br.readLine();
				Triple t = readTriple(spo);
				//System.out.println("\n");
				//t.display();
				handleDataTriple(t);
				//display();
				//System.out.println();
			}
			br.close();
			//System.out.println("=== After strong data triple summarization of "+ dataTriplesFile + ": ==================================");
			//display();


			// First file: type triples
			br = new BufferedReader(
					new FileReader(
							new File(typeTriplesFile)));
			while (br.ready()){
				String spo = br.readLine();
				Triple t = readTriple(spo);
				//t.display();
				handleTypeTriplesAfterData(t);
				//System.out.println();
			}
			br.close();
			//System.out.println("=== After strong type triple summarization of " + typeTriplesFile + ": =================================== ");
			//display();
			long stop = System.currentTimeMillis();
			System.out.println("Strong summarization took: "+ (stop - start));
			System.out.println(this.toString());
		}
	}

}
