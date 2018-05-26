package fr.inria.cedar.quotientSummary.weak;

import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import fr.inria.cedar.quotientSummary.util.Substitutions;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TypedWeakSummary extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(TypedWeakSummary.class.getName());

	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set
	Long2Long n2cs; // for each data node, its class set ID. This is also the rep function for typed nodes
	Long2LongSet n2c; // for each data node, the set of types we know so far for this node
	HashMap<TreeSet<Long>, Long> cs2csID; // for each set of types known so far, the ID of that set

	protected final static char TRS_RP_TRO = 9;
	protected final static char TRS_RP_RO = 10;
	protected final static char TRS_RP_UO = 11;
	protected final static char TRS_UP_TRO = 12; 
	protected final static char TRS_UP_RO = 13; 
	protected final static char TRS_UP_UO = 14; 
	protected final static char RS_RP_TRO = 15;
	protected final static char RS_UP_TRO = 16; 
	protected final static char US_UP_TRO = 17; 
	protected final static char US_RP_TRO = 18; 

	public TypedWeakSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		cs = new Long2LongSet();
		n2cs = new Long2Long();
		n2c = new Long2LongSet();
		cs2csID = new HashMap<>();
		this.summaryTablePrefix = TYPED_WEAK_SUMMARY_PREFIX;
		this.isTypeFirst = true;
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn) {
		long start = System.currentTimeMillis();
		try {
			conn.setAutoCommit(false);
		}
		catch (SQLException ex) {
			LOGGER.error(ex);
		}
		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn, dictionaryTableName);
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true;
			avoidCollisionsWhenAssigningSummaryNodes(conn);
		}
		String getTypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						this.handleTypeTripleBeforeData(t);
						storeSpecialNodesRepresentation(t, false);
						triplesSummarizedSoFar++;
						typeTriplesSummarizedSoFar++;
						if (checkConsistency) {
							consistencyChecks();
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		classSetCreationTime = System.currentTimeMillis() - start;
		LOGGER.info("Class sets created in " + classSetCreationTime + " ms");

		start = System.currentTimeMillis();
		this.representTypeTriples();
		typeTriplesSummarizationTime = System.currentTimeMillis() - start;
		LOGGER.info("Summarized " + typeTriplesSummarizedSoFar + " type triples in " + typeTriplesSummarizationTime + " ms");

		start = System.currentTimeMillis();
		// now all the non-type triples
		String getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getLong(1), rs.getLong(2), rs.getLong(3));
						if ((t.p == RDF2SQLEncoding.getSubClassCode())
						|| (t.p == RDF2SQLEncoding.getSubPropertyCode())
						|| (t.p == RDF2SQLEncoding.getDomainCode())
						|| (t.p == RDF2SQLEncoding.getRangeCode())) {
							edgesWithProv.addTriple(t.s, t.p, t.o);
							storeSpecialNodesRepresentation(t, true);
						}
						else
							handleDataTriple(t);
						triplesSummarizedSoFar++;
						dataTriplesSummarizedSoFar++;
						if (checkConsistency) {
							consistencyChecks();
						}
						//this.drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}
		dataTriplesSummarizationTime = System.currentTimeMillis() - start;
		LOGGER.info("Summarized " + dataTriplesSummarizedSoFar + " data triples in " + dataTriplesSummarizationTime + " ms");

		allTriplesSummarizationTime = classSetCreationTime + typeTriplesSummarizationTime + dataTriplesSummarizationTime;
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " triples overall in " + allTriplesSummarizationTime + " ms");
	}

	protected void handleDataTriple(Triple t) {
		//LOGGER.debug("### Data triple: " + t.toString());
		Long repS = rep.get(t.s);
		Long repO = rep.get(t.o);
		Long pSource = ps.get(t.p);
		Long pTarget = pt.get(t.p);
		// Properties appearing in triples where one node is typed and the other is not,
		// may have a source but lack a target, or the opposite.
		// Thus, is "represented" a property having a source OR a target. It doesn't have to have both.
		// If a property only occurs between typed nodes, it is considered non represented.
		boolean pRepresented = ((pSource != null) || (pTarget != null)); 
		boolean sRepresented = (repS != null);
		boolean sTyped = ((n2cs.get(t.s) != null));
		boolean oRepresented = (repO != null);
		boolean oTyped = ((n2cs.get(t.o) != null));

		char caseNumber = identifyTripleSummarizationCase(sRepresented, sTyped,
				pRepresented, oRepresented, oTyped);
//		LOGGER.debug("\nCase: " + this.caseName(caseNumber) + " " + t.toString() + " " + 
//				RDF2SQLEncoding.dictionaryDecode(t.s) + " " + 
//				RDF2SQLEncoding.dictionaryDecode(t.p) + " " +
//				RDF2SQLEncoding.dictionaryDecode(t.o));
		switch (caseNumber) {
		// six cases for TRS
		case TRS_UP_TRO:
			handleDataTriple_TRS_UP_TRO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_UP_RO:
			handleDataTriple_TRS_UP_RO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_UP_UO:
			handleDataTriple_TRS_UP_UO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_RP_TRO:
			handleDataTriple_TRS_RP_TRO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_RP_UO:
			handleDataTriple_TRS_RP_UO(t, repS, repO, pSource, pTarget);
			break;
		case TRS_RP_RO:
			handleDataTriple_TRS_RP_RO(t, repS, repO, pSource, pTarget);
			break;
		// six cases for RS: 
		case RS_UP_UO:
			handleDataTriple_RS_UP_UO(t);
			break;
		case RS_UP_RO:
			handleDataTriple_RS_UP_RO(t);
			break;
		case RS_UP_TRO:
			handleDataTriple_RS_UP_TRO(t, repS, repO, pSource, pTarget);
			break;
		case RS_RP_UO:
			handleDataTriple_RS_RP_UO(t);
			break;
		case RS_RP_RO:
			handleDataTriple_RS_RP_RO(t);
			break;
		case RS_RP_TRO:
			handleDataTriple_RS_RP_TRO(t, repS, repO, pSource, pTarget);
			break;
		// six cases for US:  
		case US_UP_UO: 
			handleDataTriple_US_UP_UO(t);
			break;
		case US_UP_RO:
			handleDataTriple_US_UP_RO(t);
			break;
		case US_UP_TRO:
			handleDataTriple_US_UP_TRO(t, repS, repO, pSource, pTarget);
			break;
		case US_RP_UO: 
			handleDataTriple_US_RP_UO(t);
			break;
		case US_RP_RO:
			handleDataTriple_US_RP_RO(t);
			break;
		case US_RP_TRO:
			handleDataTriple_US_RP_TRO(t, repS, repO, pSource, pTarget);
			break;
		default:
			throw new IllegalStateException("This case should not be encountered here");
		}

		//LOGGER.debug("After processing triple " + t.toString() + ", we have:\n" + this.toString()); 
		//safetyCheck(); 
	}

	/**
	 * In this case we need to: represent the subject by the property source if it exists, otherwise, create a new node and also register it as the source of p; 
	 * add a p edge between this and the typed object, if not already there
	 */
	private void handleDataTriple_US_RP_TRO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		Long addedTripleSource = sourceP; 
		Long addedTripleTarget = repO;

		if (sourceP != null){
			rep.put(t.s, addedTripleSource);
		}
		else{
			sourceP = this.getNextSummaryNode();
			rep.put(t.s, sourceP);
			ps.put(t.p, sourceP); 
		}

		addedTripleSource = sourceP; 
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget); 
	}

	/**
	 * In this case we need to represent o by the target of p, and add the edge from repS to that node.
	 * The source of p (if it exists) is not affected.
	 * The target of p, if it did not exist, may become the representative of o
	 */
	private void handleDataTriple_TRS_RP_UO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = targetP;

		if (targetP != null) {
			rep.put(t.o, addedTripleTarget);
		}
		else {
			targetP = this.getNextSummaryNode();
			rep.put(t.o, targetP);
			pt.put(t.p, targetP);  
		}

		addedTripleTarget = targetP;
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to: create the source of p; we don't create a target for it.
	 * We represent s by the source of p, and add an edge from that to repO. SEEN (5)
	 */
	private void handleDataTriple_US_UP_TRO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		sourceP = this.getNextSummaryNode();
		ps.put(t.p, sourceP);
		rep.put(t.s, sourceP);
		edgesWithProv.addTriple(sourceP, t.p, repO);
	}

	/**
	 * In this case we need to create the target of p and represent o by it.
	 * We do not create a source of p.  SEEN (6)
	 */
	private void handleDataTriple_TRS_UP_UO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		targetP = this.getNextSummaryNode();
		pt.put(t.p, targetP);
		rep.put(t.o, targetP);
		edgesWithProv.addTriple(repS, t.p,targetP);
	}

	/**
	 * In this case we may have to fuse things between repS and the source of P
	 * RepO remains unchanged.  SEEN (1)
	 */
	private void handleDataTriple_RS_RP_TRO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = repO;

		if (sourceP != null){
			Substitutions subs = new Substitutions(sourceP, repS);
			//LOGGER.debug("Substitutions: " + subs.toString());

			// update added triple source, if needed
			Long possibleNewAddedTripleSource = subs.get(addedTripleSource);
			if (possibleNewAddedTripleSource != null)
				addedTripleSource = possibleNewAddedTripleSource;

			// apply replacements, if any
			applySubstitutions(subs);
			// try to add the resulting triple
		}
		else{
			addedTripleSource = sourceP; 
			ps.put(t.p, sourceP); 
		}
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to possibly fuse the target of p with repO.
	 * The source of p (if it exists)  remains unchanged. SEEN (2)
	 */
	private void handleDataTriple_TRS_RP_RO(Triple t, Long repS, Long repO, Long sourceP, Long targetP) {
		Long addedTripleSource = repS;
		Long addedTripleTarget = repO;

		if (targetP != null){
			Substitutions subs = new Substitutions(targetP, repO);
			//LOGGER.debug("Substitutions: " + subs.toString());

			// update added triple  target, if needed
			Long possibleNewAddedTripleTarget = subs.get(addedTripleTarget);
			if (possibleNewAddedTripleTarget != null)
				addedTripleTarget = possibleNewAddedTripleTarget;
			// apply replacements, if any
			applySubstitutions(subs);
			// try to add the resulting triple
		}
		else{
			addedTripleTarget = targetP; 
			pt.put(t.p, targetP); 
		}
		edgesWithProv.addTriple(addedTripleSource, t.p, addedTripleTarget);
	}

	/**
	 * In this case we need to: use repS as the source of P; we don't know a target for p.
	 * We add the edge. SEEN (7)
	 */
	private void handleDataTriple_RS_UP_TRO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		ps.put(t.p, repS);
		edgesWithProv.addTriple(repS, t.p, repO);
	}
	/**
	 * In this case we need to use repO as the target of p, and do nothing about p's source.
	 * SEEN (8)
	 */
	private void handleDataTriple_TRS_UP_RO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		pt.put(t.p, repO);
		edgesWithProv.addTriple(repS, t.p, repO);
	}

	/**
	 * In this case we need to add a p triple (if not already there) between repS and repO. 
	 * The source of p (if it exists) is not affected.
	 * The target of p (if it exists) is not affected. 
	 * SEEN (9) 
	 */
	private void handleDataTriple_TRS_RP_TRO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		edgesWithProv.addTriple(repS, t.p, repO);
	}

	/**
	 * In this case we just add the triple; we do not modify its source nor its target
	 * SEEN (10)
	 */
	private void handleDataTriple_TRS_UP_TRO(Triple t, Long repS, Long repO, Long pSource, Long pTarget) {
		edgesWithProv.addTriple(repS, t.p, repO);
	}

	String caseName(char c) { 
		switch (c) {
		case TRS_UP_TRO: 
			return "TRS_UP_TRO"; 
		case TRS_UP_RO:
			return "TRS_UP_RO"; 
		case TRS_UP_UO:
			return "TRS_UP_UO"; 
		case TRS_RP_TRO:
			return "TRS_RP_TRO"; 
		case TRS_RP_UO:
			return "TRS_RP_UO"; 
		case TRS_RP_RO:
			return "TRS_RP_RO"; 
		case RS_UP_UO:
			return "RS_UP_UO"; 
		case RS_UP_RO:
			return "RS_UP_RO"; 
		case RS_UP_TRO:
			return "RS_UP_TRO"; 
		case RS_RP_UO:
			return "RS_RP_UO"; 
		case RS_RP_RO:
			return "RS_RP_RO"; 
		case RS_RP_TRO: 
			return "RS_RP_TRO";  
		case US_UP_UO: 
			return "US_UP_UO";
		case US_UP_RO:
			return "US_UP_RO";
		case US_UP_TRO:
			return "US_UP_TRO";
		case US_RP_UO:
			return "US_RP_UO";
		case US_RP_RO:
			return "US_RP_RO";
		}
		throw new IllegalStateException("Unrecognized case " + c);
	}

	protected char identifyTripleSummarizationCase(boolean sRepresented, boolean sTyped,
			boolean pRepresented, boolean oRepresented, boolean oTyped) {
		if (sRepresented){
			if (sTyped) {// in this case, the edge will not change the source of p, but it may change its target
				if (pRepresented){
					if (oRepresented){
						if (oTyped){
							return TRS_RP_TRO; 
						}
						return TRS_RP_RO; 
					}
					else{ // o unrepresented => o untyped
						return TRS_RP_UO; 
					}
				}
				else{ // s represented, typed, p unrepresented 
					if (oRepresented){
						if (oTyped){
							return TRS_UP_TRO; 
						}
						else{ // o unrepresented => untyped
							return TRS_UP_RO; 
						}
					}
					else{ // s represented, typed, p unrepresented, o unrepresented => untyped
						return TRS_UP_UO; 
					}
				}
			}
			else{ // s represented, untyped
				if (pRepresented){
					if (oRepresented){
						if (oTyped){
							return RS_RP_TRO; 
						}
						return RS_RP_RO; 
					}
					else{ // o unrepresented => o untyped
						return RS_RP_UO; 
					}
				}
				else{ // s represented, typed, p unrepresented 
					if (oRepresented){
						if (oTyped){
							return RS_UP_TRO; 
						}
						else{ // o unrepresented => untyped
							return RS_UP_RO; 
						}
					}
					else{ // s represented, typed, p unrepresented, o unrepresented => untyped
						return RS_UP_UO; 
					}
				}
			}
		}
		else{ // s unrepresented => untyped
			if (pRepresented){
				if (oRepresented){
					if (oTyped){
						return US_RP_TRO; 
					}
					return US_RP_RO; 
				}
				else{ // o unrepresented => o untyped
					return US_RP_UO; 
				}
			}
			else{ // s represented, typed, p unrepresented 
				if (oRepresented){
					if (oTyped){
						return US_UP_TRO; 
					}
					else{ // o unrepresented => untyped
						return US_UP_RO; 
					}
				}
				else{ // s represented, typed, p unrepresented, o unrepresented => untyped
					return US_UP_UO; 
				}
			}
		}
	}

	@Override
	public void handleTypeTripleBeforeData(Triple t) {
		//LOGGER.debug("@@@ Type triple: " + t.toString()); 

		TreeSet<Long> classSetOfThisNode = n2c.get(t.s); 
		if (classSetOfThisNode == null){ // this is the first time we encounter the node: create a class set with exactly this type
			Long newClassSetID = this.getNextSummaryNode(); 
			classSetOfThisNode = new TreeSet<>();
			classSetOfThisNode.add(t.o); 
			n2c.put(t.s, classSetOfThisNode);
			cs.put(newClassSetID, classSetOfThisNode);
			cs2csID.put(classSetOfThisNode, newClassSetID);
			n2cs.put(t.s, newClassSetID);
		}
		else{ // we already had some types for t.s
			if (classSetOfThisNode.contains(t.o)){
				// do nothing
			}
			else {
				// n is moving from classSetOfThisNode to newClassSetOfThisNode.
				// TODO Check if classSetOfThisNode is deserted and if yes, maybe remove it.
				// (We can also keep it there to reuse it later...)
				TreeSet<Long> newClassSetOfThisNode = new TreeSet<>();
				newClassSetOfThisNode.addAll(classSetOfThisNode);
				newClassSetOfThisNode.add(t.o); 

				Long newClassSetID = cs2csID.get(newClassSetOfThisNode);
				if (newClassSetID == null){
					// this class set was not already known. We create it.
					newClassSetID = this.getNextSummaryNode();
					cs.put(newClassSetID, newClassSetOfThisNode); // installs the new class set
					cs2csID.put(newClassSetOfThisNode, newClassSetID); // installs the new class set
				}
				// whether or not newClassSetID was known:
				n2cs.put(t.s, newClassSetID); // erases/replaces previously known class set ID
				n2c.put(t.s, newClassSetOfThisNode); // erases/replaces previously known class set
			}
		}
		//display(); 
	}

	/**
	 * This method adds the type triples in the summary, based on the structures previously filled in while traversing those triples.
	 * It is called only once and will output all the type triples of the summary.
	 */
	public void representTypeTriples() {
		//LOGGER.debug("POST HANDLE TYPE TRIPLES");
		for (Long node: this.n2cs.getKeys()){
			Long thisClassSetID = this.n2cs.get(node);
			TreeSet<Long> thisClassSet = this.cs.get(thisClassSetID);// the class set IS the representative
			for (Long thisClass: thisClassSet){
				edgesWithProv.addTriple(thisClassSetID, RDF2SQLEncoding.getTypeCode(), thisClass);
				rep.put(node, thisClassSetID);
			}
		}
	}

	@Override
	protected void consistencyChecks() {
		for (Long s: edgesWithProv.keySet()) { // s is a summary node 
			HashMap<Long, TreeSet<Long>> triplesOfThisSubject = edgesWithProv.get(s);
			if (triplesOfThisSubject == null)
				throw new IllegalStateException("No triples whose subject is " + s);
			for (Long p: triplesOfThisSubject.keySet()) {
				TreeSet<Long> objectsOfThisSandP = triplesOfThisSubject.get(p);
				if ((objectsOfThisSandP.size() > 1) && RDF2SQLEncoding.isDataProperty(p)
						&& (n2cs.get(s) == null)) // only check for untyped nodes 
					throw new IllegalStateException("Subject " + s + " has more than one edge with label " + p);
				for (Long o: objectsOfThisSandP) {
					if (RDF2SQLEncoding.isDataProperty(p)){
						Long sp = ps.get(p); 
						if (sp == null){
							if (n2cs.get(s) == null){
								throw new IllegalStateException("Null source for property " + p + " (" +
										RDF2SQLEncoding.dictionaryDecode(p) + ") of untyped node " + s + 
										 " (" +	RDF2SQLEncoding.dictionaryDecode(s) + ")"); 
							}
						}
						else{
							if (!sp.equals(s)){
								throw new IllegalStateException("Source of " + p + " is not " + s + " but " + sp);
							}
						}
						Long tp = pt.get(p); 
						if (tp == null){
							if (n2cs.get(o) == null){
								throw new IllegalStateException("Null target for property " + p + " (" +
										RDF2SQLEncoding.dictionaryDecode(p) + ") incoming untyped node " + o + 
										 " (" +	RDF2SQLEncoding.dictionaryDecode(o) + ")"); 
							}
						}
						else{
							if (!tp.equals(o)){
								throw new IllegalStateException("Target of " + p + " is not " + o + " but " + tp);
							}
						}
					}
				}
			}
		}
	}
}
