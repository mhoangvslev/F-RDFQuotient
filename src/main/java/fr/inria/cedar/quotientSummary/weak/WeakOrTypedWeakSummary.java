package fr.inria.cedar.quotientSummary.weak;

import java.util.HashMap;

import fr.inria.cedar.commons.miscellaneous.Debugger;
import fr.inria.cedar.quotientSummary.Summary;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import fr.inria.cedar.quotientSummary.util.Substitutions;

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
	protected long numberOfDataTriplesRead;
	protected long numberOfTypeTriplesRead;

	public WeakOrTypedWeakSummary() {
		super();
		ps = new HashMap<>();
		pt = new HashMap<>();

		numberOfDataTriplesRead = 0;
		numberOfTypeTriplesRead = 0;
		minSummaryNode = -1;

	}

	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile, String method) {
		summarizeFromTripleFiles(typeTriplesFile, dataTriplesFile);
	}

	public void summarizeFromTripleFiles(String typeTriplesFile, String dataTriplesFile) {
		throw new Error("Not implemented at this level");
	}

	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean pRepresented, boolean oRepresented) {
		if (sRepresented) {
			if (pRepresented) {
				if (oRepresented)
					return RS_RP_RO;
				return RS_RP_UO;
			}
			if (oRepresented)
				return RS_UP_RO;
			return RS_UP_UO;
		}
		if (pRepresented) {
			if (oRepresented)
				return US_RP_RO;
			return US_RP_UO;
		}
		if (oRepresented)
			return US_UP_RO;
		return US_UP_UO;
	}

	protected void replaceAll(Long oldNode, Long newNode, Long forProperty) {
		//Debugger.log("WTW REPLACE-ALL " + oldNode + " with " + newNode + " for property " + forProperty + " in: ");
		//Debugger.log(this.toString());
		edgesWithProv.replaceNodeInSummaryEdges(oldNode, newNode);
		//Debugger.log("Representation was: "); 
		//showRep(); 
		rep.replaceValue(oldNode, newNode);
		// now we need to replace oldNode with newNode in the property source and target. It does not suffice to do it for one property.
		if (ps.containsValue(oldNode))
			for (Long prop: ps.keySet())
				if (ps.get(prop).equals(oldNode)) {
					ps.replace(prop, newNode);
					Debugger.log("Now source of " + prop + " is " + ps.get(forProperty));
				}
		if (pt.containsValue(oldNode))
			for (Long prop: pt.keySet())
				if (pt.get(prop).equals(oldNode)) {
					pt.replace(prop, newNode);
					Debugger.log("Now target of " + prop + " is " + ps.get(forProperty));
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

		Substitutions subs = new Substitutions(sourceP, repS, targetP, repO);
		//System.out.println("Substitutions: " + subs.toString());

		// update added triple source and target, if needed
		Long possibleNewAddedTripleSource = subs.get(addedTripleSource);
		if (possibleNewAddedTripleSource != null)
			addedTripleSource = possibleNewAddedTripleSource;
		Long possibleNewAddedTripleTarget = subs.get(addedTripleTarget);
		if (possibleNewAddedTripleTarget != null)
			addedTripleTarget = possibleNewAddedTripleTarget;
		// apply replacements, if any
		applySubstitutions(subs, t.p);
		// try to add the resulting triple
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);

	}

	protected void applySubstitutions(Substitutions subs, Long p) {
		for (Long n: subs.getNodesToBeReplaced())
			replaceAll(n, subs.get(n), p);
	}

	// the subject and property have been represented, not the object. In this case we must:
	// - fuse the source of p with the representative of s. 
	// By convention, we will keep the *** smaller *** one. 
	// - represent the object by the target of the property
	protected void handleDataTriple_RS_RP_UO(Triple t) {
		//System.out.println("================== RS_RP_UO on " + t.toString() + " starts on");
		//display();
		Long sourceP = ps.get(t.p);
		long repS = rep.get(t.s);
		Long addedTripleSubject = repS;

		Long targetP = pt.get(t.p); // we have no repO
		Long addedTripleTarget = targetP;

		// if repS and/or addedTripleTarget have been impacted by a substitution, do it
		if (sourceP != null){
			Substitutions subs = new Substitutions(repS, sourceP);
			Long possibleNewTripleSubject = subs.get(addedTripleSubject);
			if (possibleNewTripleSubject != null)
				addedTripleSubject = possibleNewTripleSubject;
			Long possibleNewTripleTarget = subs.get(addedTripleTarget);
			if (possibleNewTripleTarget != null)
				addedTripleTarget = possibleNewTripleTarget;
			applySubstitutions(subs, t.p);
		}
		else{ // p may have empty source if so far we only found it on typed nodes
			// here, s is represented and untyped. Thus, we put p's source on s' representative.
			ps.put(t.p, repS);
			// addedTripleSubject remains repS
			// addedTripleTarget remains targetP, that should not be empty
		}
		edgesWithProv.addTriple(addedTripleSubject, t.p, addedTripleTarget);
		rep.put(t.o, addedTripleTarget);

	}

	protected void consistencyChecks() {
		throw new IllegalStateException("This check is not defined here, define it in specialized classes");
	}

	protected void handleDataTriple_RS_UP_RO(Triple t) {
		//Debugger.log("RS_UP_RO");
		// the subject and object have been represented, not the property
		// represent the property by the subject and object codes
		Long sourceP = rep.get(t.s);
		Long targetP = rep.get(t.o);
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP);
		edgesWithProv.addTriple(sourceP, t.p, targetP);
	}

	protected void handleDataTriple_RS_UP_UO(Triple t) {
		//Debugger.log("RS_UP_UO");
		// the subject has been represented, not the object nor the property
		// we need to create the property target, represent o by this
		Long sourceP = rep.get(t.s);
		Long targetP = this.getNextSummaryNode();
		rep.put(t.o, targetP);
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP);
		edgesWithProv.addTriple(sourceP, t.p, targetP);
	}

	// the property and the object have been seen, not the subject. 
	// In this case we must:
	// - represent the subject by the source of the property
	// - fuse the target of p with the representative of o. 
	// By convention we will keep the *** smaller *** one. 
	protected void handleDataTriple_US_RP_RO(Triple t) {
		//Debugger.log("US_RP_RO");

		Long sourceP = ps.get(t.p);
		if (sourceP == null){
			sourceP = this.getNextSummaryNode();
			ps.put(t.p, sourceP); 
		}
		Long addedTripleSource = sourceP;
		rep.put(t.s, addedTripleSource);

		Long repO = rep.get(t.o);
		Long targetP = pt.get(t.p);
		Long addedTripleTarget = targetP; // initialize with any of them
		if (targetP != null){ // in this case we need to fuse repO with targetP
			Substitutions subs = new Substitutions(repO, targetP);
			Long possibleNewTripleSource = subs.get(addedTripleSource);
			if (possibleNewTripleSource != null)
				addedTripleSource = possibleNewTripleSource;
			Long possibleNewTripleTarget = subs.get(addedTripleTarget);
			if (possibleNewTripleTarget != null)
				addedTripleTarget = possibleNewTripleTarget;
			applySubstitutions(subs, t.p);
		}
		else{ // target of p was null, just take repO as target 
			targetP = repO;
			pt.put(t.p, repO); 
		}
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	protected void handleDataTriple_US_RP_UO(Triple t) {
		//Debugger.log("US_RP_UO");
		// the property has been seen so far, not the subject nor the object
		// in this case we need to represent s by the source of p and o by the target of p
		Long sourceP = ps.get(t.p);
		if (sourceP == null){
			sourceP = this.getNextSummaryNode();
			ps.put(t.p, sourceP); 
		}
		Long targetP = pt.get(t.p);
		if (targetP == null){
			targetP = this.getNextSummaryNode();
			pt.put(t.p, targetP); 
		}
		rep.put(t.s, sourceP);
		rep.put(t.o, targetP);
		edgesWithProv.addTriple(sourceP, t.p, targetP);
	}

	protected void handleDataTriple_US_UP_RO(Triple t) {
		//Debugger.log("US_UP_RO");
		// only the object has been seen so far: it must have been seen as the target of *another* property.  
		// We need to: mark the target of p as the target of that property: 
		Long pTarget = rep.get(t.o);
		pt.put(t.p, pTarget);

		// create source for p; represent the subject by that source; 
		Long pSource = this.getNextSummaryNode();
		ps.put(t.p, pSource);
		rep.put(t.s, pSource);
		edgesWithProv.addTriple(pSource, t.p, pTarget);
	}

	protected void handleDataTriple_US_UP_UO(Triple t) {
		//Debugger.log("US_UP_UO");
		// nothing has been seen so far
		Long pSource = this.getNextSummaryNode();
		Long pTarget = this.getNextSummaryNode();
		ps.put(t.p, pSource);
		pt.put(t.p, pTarget);
		rep.put(t.s, pSource);
		rep.put(t.o, pTarget);
		edgesWithProv.addTriple(pSource, t.p, pTarget);
		//display();
	}

	public void display() {
		System.out.println("SUMMARY " + this.getClass().getName());
		edgesWithProv.display();
		System.out.println("REPRESENTATION: " + rep.toString()); 
		System.out.println("PROPERTY SOURCES: ");
		for (Long p: ps.keySet()){
			System.out.println(p + " (" + RDF2SQLEncoding.dictionaryDecode(p)
			+ ") => " + ps.get(p)); 
		}
		System.out.println("PROPERTY TARGETS: ");
		for (Long p: pt.keySet()){
			System.out.println(p + " (" + RDF2SQLEncoding.dictionaryDecode(p)
			+ ") => " + pt.get(p)); 
		}
		System.out.println("=======");
	}
}
