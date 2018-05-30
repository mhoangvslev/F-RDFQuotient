package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassStrongSummary extends StrongOrTypedStrongSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassStrongSummary.class.getName());
	//protected HashMap<Long, HashMap<Long, TreeSet<Long>>> edges;

	public TwoPassStrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_STRONG_SUMMARY_PREFIX;
		this.isTypeFirst = false;
		//this.edges = new HashMap<>();
	}

	/*private void addSummaryEdge(Long s, Long p, Long o) {
		if (edges.get(s) == null) {
			edges.put(s, new HashMap<>());
		}
		if (edges.get(s).get(p) == null) {
			edges.get(s).put(p, new TreeSet<>());
		}
		edges.get(s).get(p).add(o);
	}*/

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn) {
		// first pass
		long avoidCollisionsTimeStart;
		long avoidCollisionsTime = 0;
		long start = System.currentTimeMillis();
		collectSchemaNodes(conn);
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
			avoidCollisionsTimeStart = System.currentTimeMillis();
			avoidCollisionsWhenAssigningSummaryNodes(conn);
			avoidCollisionsTime += System.currentTimeMillis() - avoidCollisionsTimeStart;
		}
		String getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						if ((t.p == RDF2SQLEncoding.getSubClassCode())
						|| (t.p == RDF2SQLEncoding.getSubPropertyCode())
						|| (t.p == RDF2SQLEncoding.getDomainCode())
						|| (t.p == RDF2SQLEncoding.getRangeCode())) {
							//addSummaryEdge(t.s, t.p, t.o);
							edgesWithProv.addTriple(t.s, t.p, t.o);
							rep.put(t.s, t.s);
							rep.put(t.o, t.o);
						}
						else {
							handleDataTriple2P(t);
						}
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}

		// second pass
		getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode);
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						if ((t.p != RDF2SQLEncoding.getSubClassCode())
						&& (t.p != RDF2SQLEncoding.getSubPropertyCode())
						&& (t.p != RDF2SQLEncoding.getDomainCode())
						&& (t.p != RDF2SQLEncoding.getRangeCode())) {
							Long sourceCliqueS;
							Long targetCliqueS;
							Long sourceCliqueO;
							Long targetCliqueO;
							if (sn.contains(t.s)) {
								rep.put(t.s, t.s);
							}
							else {
								sourceCliqueS = n2sc.get(t.s) != null ? n2sc.get(t.s) : getEmptySourceCliqueID();
								targetCliqueS = n2tc.get(t.s) != null ? n2tc.get(t.s) : getEmptyTargetCliqueID();
								rep.put(t.s, getOrCreateSummaryNode(sourceCliqueS, targetCliqueS));
							}
							if (sn.contains(t.o)) {
								rep.put(t.o, t.o);
							}
							else {
								sourceCliqueO = n2sc.get(t.o) != null ? n2sc.get(t.o) : getEmptySourceCliqueID();
								targetCliqueO = n2tc.get(t.o) != null ? n2tc.get(t.o) : getEmptyTargetCliqueID();
								rep.put(t.o, getOrCreateSummaryNode(sourceCliqueO, targetCliqueO));
							}
							edgesWithProv.addTriple(rep.get(t.s), t.p, rep.get(t.o));
						}
						triplesSummarizedSoFar++;
						dataTriplesSummarizedSoFar++;
						if (checkConsistency) {
							consistencyChecks();
						}
						//drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}

		dataTriplesSummarizationTime = System.currentTimeMillis() - start - avoidCollisionsTime;
		LOGGER.info("Summarized " + dataTriplesSummarizedSoFar + " data triples in " + dataTriplesSummarizationTime + " ms");

		start = System.currentTimeMillis();
		String getTypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						this.handleTypeTripleAfterData(t);
						triplesSummarizedSoFar++;
						typeTriplesSummarizedSoFar++;
						if (checkConsistency) {
							consistencyChecks();
						}
						//drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		typeTriplesSummarizationTime = System.currentTimeMillis() - start;
		LOGGER.info("Summarized " + typeTriplesSummarizedSoFar + " type triples in " + typeTriplesSummarizationTime + " ms");

		allTriplesSummarizationTime = dataTriplesSummarizationTime + typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " overall triples in " + allTriplesSummarizationTime + " ms");
	}

	public void handleDataTriple2P(Triple t) {
		Long sourceCliqueS = n2sc.get(t.s);
		//Long targetCliqueS = n2tc.get(t.s);
		//Long sourceCliqueO = n2sc.get(t.o);
		Long targetCliqueO = n2tc.get(t.o);

		Long sourceCliqueP = p2sc.get(t.p);
		Long targetCliqueP = p2tc.get(t.p);

		boolean sSchemaNode = sn.contains(t.s);
		boolean oSchemaNode = sn.contains(t.o);

		Long newSourceClique;
		Long newTargetClique;
		if (!sSchemaNode) {
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
		if (!oSchemaNode) {
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

	/** This implementation should be shared by Weak and Strong
	 *
	 * @param t
	 */
	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		//LOGGER.debug("\nType triple " + t.toString());
		Long repS = rep.get(t.s);
		if (repS != null) {
			//LOGGER.debug("Source " + t.s + " already represented");
			//addSummaryEdge(repS, t.p, t.o);
			edgesWithProv.addTriple(repS, t.p, t.o);
		}
		else {
			//LOGGER.debug("Source " + t.s + " has no data properties");
			if (!typeOnlyNodeAlreadySeen) {
				//LOGGER.debug("Creating representative for type-only node"); 
				this.typeOnlyNodeID = getNextSummaryNode();
				typeOnlyNodeAlreadySeen = true;
			}
			//addSummaryEdge(typeOnlyNodeID, t.p, t.o);
			edgesWithProv.addTriple(typeOnlyNodeID, t.p, t.o);
			rep.put(t.s, typeOnlyNodeID);
		}
		rep.put(t.o, t.o);
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

	/*private void updateCliquesOutOf(Triple t) {
		updateSourceCliques(t);
		updateTargetCliques(t);
	}

	protected void updateSourceCliques(Triple t){
		Long ssc = n2sc.get(t.s);
		Long psc = p2sc.get(t.p); 
		if (ssc == null){// this is the first triple with subject s
			if (psc == null){ // and we've never seen this property before
				Long newSC = this.makeAndAddNewSourceClique(t.p);
				n2sc.put(t.s, newSC);
			}
			else{// we have seen this property before --> n should have its clique
				n2sc.put(t.s, psc);
			}
		}
		else{ // we have seen s before
			if (psc == null){ // but we have never seen this property --> add p to this source clique
				Long newPSC = this.makeAndAddNewSourceClique(t.p);
				this.fuseCliqueInto(newPSC, ssc, SOURCE); // fold newPSC into ssc which was not empty
			}
			else{ // both cliques exist
				if (!ssc.equals(psc)){
					Long newSC = this.cliqueFusionResult(ssc, psc, SOURCE); 
					this.fuseCliqueInto(ssc,  newSC, SOURCE);
					n2sc.put(t.s, newSC); 
					this.fuseCliqueInto(psc, newSC, SOURCE);
					p2sc.put(t.p, newSC); 
				}
			}
		}

		Long osc = n2sc.get(t.o);
		if (osc == null){
			Long emptySC = this.getEmptySourceCliqueID();
			n2sc.put(t.o, emptySC); 
		}
	}

	protected void updateTargetCliques(Triple t){
		Long otc = n2tc.get(t.o);
		Long ptc = p2tc.get(t.p); 
		if (otc == null){// this is the first triple with object o
			if (ptc == null){ // and we've never seen this property before
				Long newTC = this.makeAndAddNewTargetClique(t.p);
				n2tc.put(t.o, newTC);
			}
			else{// we have seen this property before --> n should have its clique
				n2tc.put(t.o, ptc);
			}
		}
		else{ // we have seen s before
			if (ptc == null){ // but we have never seen this property --> add p to this source clique
				Long newPTC = this.makeAndAddNewTargetClique(t.p);
				this.fuseCliqueInto(newPTC, ptc, TARGET); // fold newPSC into ssc which was not empty
			}
			else{ // both cliques exist
				if (!otc.equals(ptc)){
					Long newTC = this.cliqueFusionResult(otc, ptc, TARGET); 
					this.fuseCliqueInto(otc,  newTC, TARGET);
					n2tc.put(t.o, newTC); 
					this.fuseCliqueInto(ptc, newTC, TARGET);
					p2tc.put(t.p, newTC); 
				}
			}
		}
		Long stc = n2tc.get(t.s);
		if (stc == null){
			Long emptyTC = this.getEmptyTargetCliqueID();
			n2tc.put(t.o, emptyTC); 
		}
	}*/
}
