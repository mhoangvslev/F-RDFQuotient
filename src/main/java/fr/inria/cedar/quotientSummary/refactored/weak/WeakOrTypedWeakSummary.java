package fr.inria.cedar.quotientSummary.refactored.weak;

import java.util.ArrayList;
import java.util.HashMap;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.refactored.Summary;

public class WeakOrTypedWeakSummary extends Summary {
	HashMap<Long, Long> ps; // for each property, the property source
	HashMap<Long, Long> pt; // for each property, the property source	

	long minSummaryNode; 
	// below:
	// U means unrepresented (so far) 
	// R means represented (so far) 
	// TRS means typed (thus, already represented) represented so far
	protected final static char US_UP_UO = 1;
	protected final static char US_UP_RO = 2;
	protected final static char US_RP_UO = 3;
	protected final static char US_RP_RO = 4;
	protected final static char RS_UP_UO = 5;
	protected final static char RS_UP_RO = 6;
	protected final static char RS_RP_UO = 7;
	protected final static char RS_RP_RO = 8;
	

	// for debugging
	long globalTripleCount; 

	protected long numberOfDataTriplesRead; 
	protected long numberOfTypeTriplesRead; 
	
	public WeakOrTypedWeakSummary(){
		super(); 
		ps = new HashMap<Long, Long>(); 
		pt = new HashMap<Long, Long>(); 

		numberOfDataTriplesRead=0;
		numberOfTypeTriplesRead=0; 
		minSummaryNode = -1; 

	}

	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile, String method){
		summarizeFromTripleFiles(typeTriplesFile, dataTriplesFile); 
	}

	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile) {
		throw new Error("Not implemented at this level"); 
	}
	

	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean pRepresented, boolean oRepresented) {
		if (sRepresented){
			if (pRepresented){
				if (oRepresented){
					return RS_RP_RO; 
				}
				return RS_RP_UO; 
			}
			if (oRepresented){
				return RS_UP_RO;
			}
			return RS_UP_UO; 
		}
		if (pRepresented){
			if (oRepresented){
				return US_RP_RO; 
			}
			return US_RP_UO; 
		}
		if (oRepresented){
			return US_UP_RO; 
		}
		return US_UP_UO; 
	}
	
	

	protected void replaceAll(Long oldNode, Long newNode, Long forProperty){
		//Debugger.log("WTW REPLACE-ALL " + oldNode + " with " + newNode + " for property " + forProperty + " in: ");
		//Debugger.log(this.toString());
		replaceInSummary(oldNode, newNode);
		//Debugger.log("Representation was: "); 
		//showRep(); 
		rep.replaceValue(oldNode, newNode); 
		// now we need to replace oldNode with newNode in the property source and target. It does not suffice to do it for one property.
		if (ps.containsValue(oldNode)) {
			for (Long prop: ps.keySet()) {
				if (ps.get(prop).equals(oldNode)){
					ps.replace(prop, newNode); 
					Debugger.log("Now source of " + prop + " is " + ps.get(forProperty));
				}
			}
		}
		if (pt.containsValue(oldNode)) {
			for (Long prop: pt.keySet()) {
				if (pt.get(prop).equals(oldNode)){
					pt.replace(prop, newNode); 
					Debugger.log("Now target of " + prop + " is " + ps.get(forProperty));
				}
			}
		}
	}

	protected void handleDataTriple_RS_RP_RO(Triple t) {
		Debugger.log("============ RS_RP_RO " + t.toString());
		// everything has been represented. In this case we must:
		// - fuse the subject of p with the representative of s (keep the smallest)
		// - fuse the object of p with the representative of s (keep the smallest)
		Long sourceP = ps.get(t.p);
		Long addedTripleSource = sourceP; 
		Long targetP = pt.get(t.p); 
		Long addedTripleTarget = targetP; 
		Long repS = rep.get(t.s); 
		Long repO = rep.get(t.o); 

		if (sourceP < repS){ // addedTripleSource is sourceP
			replaceAll(repS, sourceP, t.p);
			if (targetP < repO){
				if (addedTripleSource == repO) {
					addedTripleSource = targetP; 
				}
				replaceAll(repO, targetP, t.p); // replace repO with targetP; addedTripleTarget is targetP
				this.addTripleAndCheck(addedTripleSource, t.p, addedTripleTarget);
			}
			else{//repO <= targetP
				if (targetP > repO){ // replace targetP with repO
					if (addedTripleSource == targetP) {
						addedTripleSource = repO; 
					}
					replaceAll(targetP, repO, t.p);
				}	
				this.addTripleAndCheck(addedTripleSource, t.p, addedTripleTarget);
			}
		}
		else{// sourceP >= repS
			if (sourceP > repS){
				if (addedTripleTarget == sourceP) {
					addedTripleTarget = repS; 
				}
				replaceAll(sourceP, repS, t.p);
			}
			if (targetP < repO){
				if (addedTripleSource == repO) {
					addedTripleSource = targetP; 
				}
				replaceAll(repO, targetP, t.p); 
				this.addTripleAndCheck(addedTripleSource, t.p, addedTripleTarget);
			}
			else{ // repO <= targetP
				if (targetP > repO){ // replace if not equal
					if (addedTripleSource == targetP) {
						addedTripleSource = repO; 
					}
					replaceAll(targetP, repO, t.p); 
				}
				// add this triple in any case
				this.addTripleAndCheck(addedTripleSource, t.p, addedTripleTarget);
			}
		}

	}

	protected void handleDataTriple_RS_RP_UO(Triple t) {
		//Debugger.log("================== RS_RP_UO on " + t.toString() + " starts on");
		//Debugger.log(this.toString()); 
		//safetyCheck(); 

		// the subject and property have been represented, not the object. In this case we must:
		// - represent the object by the target of the property 
		// - fuse the source of p with the representative of s. By convention, we will keep the *** smaller *** one. 
		// - the fusion may also impact the representative of o.
		
		Long targetP = pt.get(t.p); 
		rep.put(t.o, targetP);
		Long addedTripleTarget = targetP; // this may be a collateral damage of fusion and replacements
		// in this case it needs to change, to follow the fusion and replacement
		
		Long sourceP = ps.get(t.p);
		long repS = rep.get(t.s); 
		//Debugger.log("RS_RP_UO 1. repS: " + repS + " sourceP: " + sourceP + " we should keep the smaller"); 
		//Debugger.log("RS_RP_UO 2. targetP: " + targetP);
		if (repS < sourceP){ // we keep repS, replace sourceP with repS all over
			if (addedTripleTarget == sourceP) {
				addedTripleTarget = repS;
			}
			//Debugger.log("RS_RP_UO 3. Replacing " + sourceP + " with " + repS); 
			replaceAll(sourceP, repS, t.p); 
			//Debugger.log("RS_RP_UO 4. After replacement but before triple addition (1)\n" + this.toString());
			addTripleAndCheck(repS, t.p, addedTripleTarget); 
			//Debugger.log("RS_RP_UO 5. After replacement and triple addition (1)\n" + this.toString());
		}
		else{ 
			if (repS > sourceP ) { // we keep sourceP, replace repS with sourceP all over
				if (addedTripleTarget == repS) {
					addedTripleTarget = sourceP; 
				}
				//Debugger.log("RS_RP_UO 6. Replacing " + repS + " with " + sourceP); 
				replaceAll(repS, sourceP, t.p);
				//Debugger.log("RS_RP_UO 7. After replacement but before triple addition (2)\n" + this.toString());
			}
			// add the edge in any case
			addTripleAndCheck(sourceP, t.p, addedTripleTarget); 
			//Debugger.log("RS_RP_UO 8. After replacement and addition of " + sourceP + " " + t.p + " " + targetP);
			//Debugger.log(this.toString());
			//consistentyChecks();
		}
	}

	protected void consistencyChecks() {
		throw new IllegalStateException("This check is not defined here, define it in specialized classes"); 
	}

	protected void handleDataTriple_RS_UP_RO(Triple t) {
		Debugger.log("RS_UP_RO");
		// the subject and object have been represented, not the property
		// represent the property by the subject and object codes
		Long sourceP = rep.get(t.s);
		Long targetP = rep.get(t.o); 
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP);
		addTripleAndCheck(sourceP, t.p, targetP); 
	}


	protected void handleDataTriple_RS_UP_UO(Triple t) {
		Debugger.log("RS_UP_UO");
		// the subject has been represented, not the object nor the property
		// we need to create the property target, represent o by this
		Long sourceP = rep.get(t.s); 
		Long targetP = this.getNextSummaryNode(); 
		rep.put(t.o, targetP); 
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP); 
		addTripleAndCheck(sourceP, t.p, targetP); 
	}

	protected void handleDataTriple_US_RP_RO(Triple t) {
		Debugger.log("US_RP_RO");
		// the property and the object have been seen, not the subject. In this case we must:

		// - represent the subject by the source of the property	
		Long sourceP = ps.get(t.p); 
		rep.put(t.s, sourceP); 

		Long addedTripleSource = sourceP; 
		// this may change as collateral damage of fusions below
		
		// - fuse the target of p with the representative of o. By convention we will keep the *** smaller *** one. 
		Long targetP = pt.get(t.p); 
		Long repO = rep.get(t.o); 
		if (repO > targetP){ // we keep targetP, we need to replace repO  with targetP, all over the summary
			if (addedTripleSource == repO) {
				addedTripleSource = targetP; 
			}
			replaceAll(repO, targetP, t.p); 
			addTripleAndCheck(addedTripleSource, t.p, targetP); 
		}
		else{ 
			if (repO < targetP){
				if (addedTripleSource == targetP) {
					addedTripleSource = repO; 
				}
				// we keep repO, we need to replace targetP with repO all over in the summary
				replaceAll(targetP, repO, t.p); 
			}
			// add the edge in any case
			addTripleAndCheck(addedTripleSource, t.p, repO); 
			// and we represent o by repO: nothing needed, it was already the case
		}
	}

	protected void handleDataTriple_US_RP_UO(Triple t) {
		Debugger.log("US_RP_UO");
		// the property has been seen so far, not the subject nor the object
		// in this case we need to represent s by the source of p and o by the target of p
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p); 
		rep.put(t.s, pSource);
		rep.put(t.o, pTarget); 
		addTripleAndCheck(pSource, t.p, pTarget); 
	}

	protected void handleDataTriple_US_UP_RO(Triple t) {
		Debugger.log("US_UP_RO");
		// only the object has been seen so far: it must have been seen as the target of *another* property.  
		// We need to: mark the target of p as the target of that property: 
		Long pTarget = rep.get(t.o); 
		pt.put(t.p, pTarget); 

		// create source for p; represent the subject by that source; 
		Long pSource = this.getNextSummaryNode(); 
		ps.put(t.p, pSource);
		rep.put(t.s, pSource);
		addTripleAndCheck(pSource, t.p, pTarget); 
	}

	protected void handleDataTriple_US_UP_UO(Triple t) {
		Debugger.log("US_UP_UO");
		// nothing has been seen so far
		Long pSource = this.getNextSummaryNode(); 
		Long pTarget = this.getNextSummaryNode(); 
		ps.put(t.p, pSource);
		pt.put(t.p, pTarget);
		rep.put(t.s, pSource);
		rep.put(t.o, pTarget);
		addTripleAndCheck(pSource, t.p, pTarget); 
	}

	protected void addTripleAndCheck(Long s, long p, Long o) {
		addTriple(s, p, o); 
		if (this.checkConsistency) {
			consistencyChecks();
		}
	}

	// This methods overrides that of Summary. It has some safety checks specific to W and TW summarization.
	public ArrayList<Triple> getSummaryEdges() {
		ArrayList<Triple> res = new ArrayList<>();
		for (Long s: edges.keySet()){
			HashMap<Long, ArrayList<Long>> triplesOfThisSubject = edges.get(s); 
			if (triplesOfThisSubject == null){
				throw new Error("No triples whose subject is " + s); 
			}
			for (Long p: triplesOfThisSubject.keySet()){
				ArrayList<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if (isDataProperty(p)) {
					if (objectsOfThisSandP.size() > 1){
						throw new Error("Subject " + s + " has more than one edge with label " + p); //TODO this holds just for the weak.
					}
				}
				for (Long o: objectsOfThisSandP){
					Triple t = new Triple(s, p, o);
					res.add(t);
				}
			}
		}
		return res; 
	}
	// This method overrides that of Summary. Some of the printout is specific to W and TW.
	public String toString(){
		StringBuffer sb = new StringBuffer();
		for (Triple t: getSummaryEdges()){
			sb.append(t.toString());
			sb.append("\n");
		}
		//sb.append("rep:\n");
		//this.showRepInBuffer(sb);
//		sb.append("\nProperty sources:\n");
//		for (Long l: ps.keySet()) {
//			sb.append(l + ": " + ps.get(l));
//			sb.append(" "); 
//		}
//		sb.append("\nProperty targets:\n");
//		for (Long l: pt.keySet()) {
//			sb.append(l + ": " + pt.get(l));
//			sb.append(" "); 
//		}
		return new String(sb); 
	}

	
}
