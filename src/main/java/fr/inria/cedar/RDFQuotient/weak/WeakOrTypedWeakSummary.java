//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.weak;

import fr.inria.cedar.RDFQuotient.Summary;
import fr.inria.cedar.RDFQuotient.datastructures.Triple;
import fr.inria.cedar.RDFQuotient.util.RDF2SQLEncoding;
import fr.inria.cedar.RDFQuotient.util.Substitutions;
import java.util.HashMap;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class WeakOrTypedWeakSummary extends Summary {
	private static final Logger LOGGER = Logger.getLogger(WeakOrTypedWeakSummary.class.getName());
	protected final HashMap<Long, Long> ps; // for each property, the property source
	protected final HashMap<Long, Long> pt; // for each property, the property target

	protected Long repS;
	protected Long repO;
	protected Long sourceP;
	protected Long targetP;

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

	public WeakOrTypedWeakSummary() {
		super();
		LOGGER.setLevel(Level.INFO);
		ps = new HashMap<>();
		pt = new HashMap<>();
	}

	// -->
	// Common handlers
	// -->

	protected void handleDataTriple_RS_RP_RO(Triple t) {
		// everything has been represented. In this case we must:
		// - fuse the subject of p with the representative of s (keep the smallest)
		// - fuse the object of p with the representative of s (keep the smallest)

		Long addedTripleSource = repS;
		Long addedTripleTarget = repO;

		if (sourceP != null) {
			if (targetP != null) {
				Substitutions subs = new Substitutions(sourceP, repS, targetP, repO);
				//LOGGER.debug("Substitutions: " + subs.toString());
				// update added triple source and target, if needed
				Long possibleNewAddedTripleSource = subs.get(addedTripleSource);
				if (possibleNewAddedTripleSource != null) {
					addedTripleSource = possibleNewAddedTripleSource;
				}
				Long possibleNewAddedTripleTarget = subs.get(addedTripleTarget);
				if (possibleNewAddedTripleTarget != null) {
					addedTripleTarget = possibleNewAddedTripleTarget;
				}
				// apply replacements, if any
				applySubstitutions(subs);
			}
			else { // sourceP not null, targetP is null
				Substitutions subs = new Substitutions(sourceP, repS);
				//LOGGER.debug("Substitutions: " + subs.toString());
				// update added triple source, if needed
				Long possibleNewAddedTripleSource = subs.get(addedTripleSource);
				if (possibleNewAddedTripleSource != null) {
					addedTripleSource = possibleNewAddedTripleSource;
				}
				// apply replacements, if any
				applySubstitutions(subs);
			}
		}
		else { // sourceP is null
			if (targetP != null) { //sourceP is null, targetP is not null
				Substitutions subs = new Substitutions(targetP, repO);
				//LOGGER.debug("Substitutions: " + subs.toString());
				// update added triple target, if needed
				Long possibleNewAddedTripleTarget = subs.get(addedTripleTarget);
				if (possibleNewAddedTripleTarget != null) {
					addedTripleTarget = possibleNewAddedTripleTarget;
				}
				// apply replacements, if any
				applySubstitutions(subs);
			}
			else {
				throw new IllegalStateException("Both source and target are null for represented property " + t.p);
			}
		}
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	// here, we inform the property from the source and object
	protected void handleDataTriple_RS_UP_RO(Triple t) {
		// the subject and object have been represented, not the property
		// represent the property by the subject and object codes
		sourceP = rep.get(t.s);
		targetP = rep.get(t.o);
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP);
		edgesWithProv.addTriple(sourceP, t.p, targetP);
	}

	// here, we inform the property source from the subject, and create new representative for the object
	protected void handleDataTriple_RS_UP_UO(Triple t) {
		// the subject has been represented, not the object nor the property
		// we need to create the property target, represent o by this
		sourceP = rep.get(t.s);
		targetP = getNextSummaryNode();
		rep.put(t.o, targetP);
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP);
		edgesWithProv.addTriple(sourceP, t.p, targetP);
	}

	protected void handleDataTriple_US_UP_RO(Triple t) {
		// only the object has been seen so far: it must have been seen as the target of *another* property.  
		// We need to: mark the target of p as the target of that property: 
		targetP = rep.get(t.o);
		// create source for p; represent the subject by that source; 
		sourceP = getNextSummaryNode();
		rep.put(t.s, sourceP);
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP);
		edgesWithProv.addTriple(sourceP, t.p, targetP);
	}

	// the property and the object have been seen, not the subject. 
	// In this case we must:
	// - represent the subject by the source of the property (if not empty)
	// - fuse the target of p (if not empty) with the representative of o. 
	protected void handleDataTriple_US_RP_RO(Triple t) {
		if (sourceP == null) {
			sourceP = getNextSummaryNode();
			ps.put(t.p, sourceP);
		}
		Long addedTripleSource = sourceP;
		rep.put(t.s, addedTripleSource);

		Long addedTripleTarget = repO; // initialize with any of them

		if (targetP != null) { // in this case we need to fuse repO with targetP
			Substitutions subs = new Substitutions(sourceP, sourceP, repO, targetP);
			Long possibleNewTripleSource = subs.get(addedTripleSource);
			if (possibleNewTripleSource != null) {
				addedTripleSource = possibleNewTripleSource;
			}
			Long possibleNewTripleTarget = subs.get(addedTripleTarget);
			if (possibleNewTripleTarget != null) {
				addedTripleTarget = possibleNewTripleTarget;
			}
			applySubstitutions(subs);
		}
		else { // target of p was null, just take repO as target 
			pt.put(t.p, repO);
		}
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	// the subject and property have been represented, not the object. In this case we must:
	// - fuse the source of p (if not null) with the representative of s. 
	// - represent the object by the target of the property (if not null), otherwise create a new node for this
	protected void handleDataTriple_RS_RP_UO(Triple t) {
		Long addedTripleSource = repS;

		if (targetP == null) { // fixing the triple target if not already there
			targetP = getNextSummaryNode();
			pt.put(t.p, targetP);
		}
		Long addedTripleTarget = targetP;
		rep.put(t.o, addedTripleTarget);

		// if repS needs to change through a substitution, do it
		if (sourceP != null) {
			Substitutions subs = new Substitutions(repS, sourceP, targetP, targetP);
			//LOGGER.debug("RS_RP_UO: source substitution: " + subs.toString());
			Long possibleNewTripleSubject = subs.get(addedTripleSource);
			if (possibleNewTripleSubject != null) {
				addedTripleSource = possibleNewTripleSubject;
			}
			Long possibleNewTripleObject = subs.get(addedTripleTarget);
			if (possibleNewTripleObject != null) {
				addedTripleTarget = possibleNewTripleObject;
			}
			applySubstitutions(subs);
		}
		else { // p may have empty source if so far we only found it on typed nodes
			// here, s is represented and untyped. Thus, we put p's source on s' representative.
			//LOGGER.debug("RS_RP_UO: source of " +  t.p + " is: " + repS); 
			ps.put(t.p, repS);
		}
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	protected void handleDataTriple_US_RP_UO(Triple t) {
		// the property has been seen so far, not the subject nor the object
		// in this case we need to represent s by the source of p and o by the target of p
		if (sourceP == null) {
			sourceP = getNextSummaryNode();
			ps.put(t.p, sourceP);
		}
		if (targetP == null) {
			targetP = sourceP;
			if (t.s != t.o) { // if it's not a self loop
				targetP = getNextSummaryNode();
			}
			pt.put(t.p, targetP);
		}
		rep.put(t.s, sourceP);
		rep.put(t.o, targetP);
		edgesWithProv.addTriple(sourceP, t.p, targetP);
	}

	protected void handleDataTriple_US_UP_UO(Triple t) {
		// nothing has been seen so far
		//LOGGER.debug("US_UP_UO: " + RDF2SQLEncoding.dictionaryDecode(t.s) + " " + RDF2SQLEncoding.dictionaryDecode(t.p) + " " + RDF2SQLEncoding.dictionaryDecode(t.o));
		sourceP = getNextSummaryNode();
		targetP = sourceP;
		if (t.s != t.o) { // if it's not a self loop
			targetP = getNextSummaryNode();
		}
		ps.put(t.p, sourceP);
		pt.put(t.p, targetP);
		rep.put(t.s, sourceP);
		rep.put(t.o, targetP);
		edgesWithProv.addTriple(sourceP, t.p, targetP);
	}

	protected void handleDataTriple_SELF_SELF(Triple t) {
		edgesWithProv.addTriple(repS, t.p, repO);
	}

	/**
	 * In this case we need to: represent the subject by the property source if it exists, otherwise, create a new node and also register it as the source of p;
	 * add a p edge between this and the typed object, if not already there
	 *
	 * @param t
	 */
	protected void handleDataTriple_US_RP_TRO(Triple t) {
		Long addedTripleSource = sourceP;
		Long addedTripleTarget = repO;

		if (sourceP == null) {
			addedTripleSource = getNextSummaryNode();
		}
		rep.put(t.s, addedTripleSource);
		ps.put(t.p, addedTripleSource);

		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to represent o by the target of p, and add the edge from repS to that node.
	 * The source of p (if it exists) is not affected.
	 * The target of p, if it did not exist, may become the representative of o
	 *
	 * @param t
	 */
	protected void handleDataTriple_TRS_RP_UO(Triple t) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = targetP;

		if (targetP == null) {
			addedTripleTarget = getNextSummaryNode();
		}
		rep.put(t.o, addedTripleTarget);
		pt.put(t.p, addedTripleTarget);

		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to: create the source of p; we don't create a target for it.
	 * We represent s by the source of p, and add an edge from that to repO.
	 *
	 * @param t
	 */
	protected void handleDataTriple_US_UP_TRO(Triple t) {
		sourceP = getNextSummaryNode();
		ps.put(t.p, sourceP);
		rep.put(t.s, sourceP);
		edgesWithProv.addTriple(sourceP, t.p, repO);
	}

	/**
	 * In this case we need to create the target of p and represent o by it.
	 * We do not create a source of p.
	 *
	 * @param t
	 */
	protected void handleDataTriple_TRS_UP_UO(Triple t) {
		targetP = getNextSummaryNode();
		pt.put(t.p, targetP);
		rep.put(t.o, targetP);
		edgesWithProv.addTriple(repS, t.p, targetP);
	}

	/**
	 * In this case we may have to fuse things between repS and the source of P
	 * RepO remains unchanged.
	 *
	 * @param t
	 */
	protected void handleDataTriple_RS_RP_TRO(Triple t) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = repO;

		if (sourceP != null) {
			Substitutions subs = new Substitutions(sourceP, repS);
			//LOGGER.debug("Substitutions: " + subs.toString());

			// update added triple source, if needed
			Long possibleNewAddedTripleSource = subs.get(addedTripleSource);
			if (possibleNewAddedTripleSource != null) {
				addedTripleSource = possibleNewAddedTripleSource;
			}

			// apply replacements, if any
			applySubstitutions(subs);
		}
		else {
			ps.put(t.p, addedTripleSource);
		}
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to possibly fuse the target of p with repO.
	 * The source of p (if it exists) remains unchanged.
	 *
	 * @param t
	 */
	protected void handleDataTriple_TRS_RP_RO(Triple t) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = repO;

		if (targetP != null) {
			Substitutions subs = new Substitutions(targetP, repO);
			//LOGGER.debug("Substitutions: " + subs.toString());

			// update added triple  target, if needed
			Long possibleNewAddedTripleTarget = subs.get(addedTripleTarget);
			if (possibleNewAddedTripleTarget != null) {
				addedTripleTarget = possibleNewAddedTripleTarget;
			}
			// apply replacements, if any
			applySubstitutions(subs);
		}
		else {
			pt.put(t.p, addedTripleTarget);
		}
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to: use repS as the source of P; we don't know a target for p.
	 * We add the edge.
	 *
	 * @param t
	 */
	protected void handleDataTriple_RS_UP_TRO(Triple t) {
		ps.put(t.p, repS);
		edgesWithProv.addTriple(repS, t.p, repO);
	}

	/**
	 * In this case we need to use repO as the target of p, and do nothing about p's source.
	 *
	 * @param t
	 */
	protected void handleDataTriple_TRS_UP_RO(Triple t) {
		pt.put(t.p, repO);
		edgesWithProv.addTriple(repS, t.p, repO);
	}

	// -->
	// Auxiliary methods
	// -->
	/**
	 * Replaces oldNode with newNode in all the data structures that this summary has (or inherits).
	 *
	 * @param oldNode
	 * @param newNode
	 */
	protected void replaceAll(Long oldNode, Long newNode) {
		edgesWithProv.replaceNodeInSummaryEdges(oldNode, newNode);
		rep.replaceValue(oldNode, newNode);
		// now we need to replace oldNode with newNode in the property source and target. It does not suffice to do it for one property.
		if (ps.containsValue(oldNode)) {
			for (Long prop: ps.keySet()) {
				if (ps.get(prop).equals(oldNode)) {
					ps.replace(prop, newNode);
				}
			}
		}
		if (pt.containsValue(oldNode)) {
			for (Long prop: pt.keySet()) {
				if (pt.get(prop).equals(oldNode)) {
					pt.replace(prop, newNode);
				}
			}
		}
	}

	protected void applySubstitutions(Substitutions subs) {
		for (Long n: subs.getNodesToBeReplaced()) {
			replaceAll(n, subs.get(n));
		}
	}

	// -->
	// Debug methods
	// -->

	@Override
	protected void consistencyChecks() {
		throw new IllegalStateException("This check is not defined here, define it in specialized classes");
	}

	protected String showCaseName(char caseNumber) {
		throw new IllegalStateException("This check is not defined here, define it in specialized classes");
	}

	@Override
	public void display() {
		System.out.println("SUMMARY " + this.getClass().getName());
		edgesWithProv.display();
		System.out.println("REPRESENTATION: " + rep.toString()); 
		System.out.println("PROPERTY SOURCES: ");
		for (Long p: ps.keySet()){
			System.out.println(p + " (" + RDF2SQLEncoding.dictionaryDecode(p) + ") => " + ps.get(p));
		}
		System.out.println("PROPERTY TARGETS: ");
		for (Long p: pt.keySet()){
			System.out.println(p + " (" + RDF2SQLEncoding.dictionaryDecode(p) + ") => " + pt.get(p));
		}
		System.out.println("=======");
	}
}
