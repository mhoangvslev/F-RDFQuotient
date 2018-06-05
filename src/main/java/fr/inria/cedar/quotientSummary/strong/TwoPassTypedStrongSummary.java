package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassTypedStrongSummary extends StrongOrTypedStrongSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassTypedStrongSummary.class.getName());
	//protected HashMap<Long, HashMap<Long, TreeSet<Long>>> edges;

	public TwoPassTypedStrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_TYPED_STRONG_SUMMARY_PREFIX;
		this.isTypeFirst = true;
		this.isTwoPass = true;
		//this.edges = new HashMap<>();
		cs = new Long2LongSet();
		n2sc = new Long2Long();
		n2cs = new Long2Long();
		n2c = new Long2LongSet();
		cs2csID = new HashMap<>();
		minCliqueID = -1;
		emptySCCount = Long.MAX_VALUE;
		emptyTCCount = Long.MAX_VALUE;
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
		long avoidCollisionsTimeStart;
		long avoidCollisionsTime = 0;
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
			avoidCollisionsTimeStart = System.currentTimeMillis();
			avoidCollisionsWhenAssigningSummaryNodes(conn);
			avoidCollisionsTime += System.currentTimeMillis() - avoidCollisionsTimeStart;
		}
		String getTypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						this.handleTypeTripleBeforeData(t);
						rep.put(t.o, t.o);
						triplesSummarizedSoFar++;
						typeTriplesSummarizedSoFar++;
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}
		classSetCreationTime = System.currentTimeMillis() - start - avoidCollisionsTime;
		LOGGER.info("Class sets created in " + classSetCreationTime + " ms");

		start = System.currentTimeMillis();
		this.representTypeTriples();
		if (checkConsistency) {
			consistencyChecks();
		}
		typeTriplesSummarizationTime = System.currentTimeMillis() - start;
		LOGGER.info("Summarized " + typeTriplesSummarizedSoFar + " type triples in " + typeTriplesSummarizationTime + " ms");

		start = System.currentTimeMillis();
		collectSchemaNodes(conn);
		// now all the non-type triples

		// first pass
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
							Long classSetS = n2cs.get(t.s);
							Long classSetO = n2cs.get(t.o);
							boolean sTyped = (classSetS != null);
							boolean oTyped = (classSetO != null);
							if (!sTyped) {
								if (sn.contains(t.s)) {
									rep.put(t.s, t.s);
								}
								else {
									sourceCliqueS = n2sc.get(t.s) != null ? n2sc.get(t.s) : getEmptySourceCliqueID();
									targetCliqueS = n2tc.get(t.s) != null ? n2tc.get(t.s) : getEmptyTargetCliqueID();
									rep.put(t.s, getOrCreateSummaryNode(sourceCliqueS, targetCliqueS));
								}
							}
							if (!oTyped) {
								if (sn.contains(t.o)) {
									rep.put(t.o, t.o);
								}
								else {
									sourceCliqueO = n2sc.get(t.o) != null ? n2sc.get(t.o) : getEmptySourceCliqueID();
									targetCliqueO = n2tc.get(t.o) != null ? n2tc.get(t.o) : getEmptyTargetCliqueID();
									rep.put(t.o, getOrCreateSummaryNode(sourceCliqueO, targetCliqueO));
								}
							}
							edgesWithProv.addTriple(rep.get(t.s), t.p, rep.get(t.o));
						}
						triplesSummarizedSoFar++;
						nonTypeTriplesSummarizedSoFar++;
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

		nonTypeTriplesSummarizationTime = System.currentTimeMillis() - start - avoidCollisionsTime;
		LOGGER.info("Summarized " + nonTypeTriplesSummarizedSoFar + " data triples in " + nonTypeTriplesSummarizationTime + " ms");

		allTriplesSummarizationTime = classSetCreationTime + typeTriplesSummarizationTime + nonTypeTriplesSummarizationTime;
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " triples overall in " + allTriplesSummarizationTime + " ms");
	}

	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	public void handleDataTriple2P(Triple t) {
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);
		boolean sTyped = (classSetS != null);
		boolean oTyped = (classSetO != null);

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
		if (!sSchemaNode && !sTyped) {
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
		if (!oSchemaNode && !oTyped) {
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
}
