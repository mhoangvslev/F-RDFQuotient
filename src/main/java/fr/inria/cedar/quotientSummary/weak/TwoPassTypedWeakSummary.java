package fr.inria.cedar.quotientSummary.weak;

import fr.inria.cedar.quotientSummary.datastructures.Long2Long;
import fr.inria.cedar.quotientSummary.datastructures.Long2LongSet;
import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.TreeSet;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassTypedWeakSummary extends WeakOrTypedWeakSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassWeakSummary.class.getName());

	HashSet<Long> nodes;
	HashMap<Long, TreeSet<Long>> n2i;
	HashMap<Long, TreeSet<Long>> n2o;

	// The following three attribute serve to identify and store the class sets for RDF resources
	Long2LongSet cs; // for each class set ID, a class set
	Long2Long n2cs; // for each data node, its class set ID. This is also the rep function for typed nodes
	Long2LongSet n2c; // for each data node, the set of types we know so far for this node
	HashMap<TreeSet<Long>, Long> cs2csID; // for each set of types known so far, the ID of that set

	public TwoPassTypedWeakSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_TYPED_WEAK_SUMMARY_PREFIX;
		this.isTypeFirst = true;
		n2i = new HashMap<>();
		n2o = new HashMap<>();
		nodes = new HashSet<>();
		cs = new Long2LongSet();
		n2cs = new Long2Long();
		n2c = new Long2LongSet();
		cs2csID = new HashMap<>();
	}

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
		classSetCreationTime = System.currentTimeMillis() - start - avoidCollisionsTime;
		LOGGER.info("Class sets created in " + classSetCreationTime + " ms");


		start = System.currentTimeMillis();
		this.representTypeTriples();
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

		findSummaryNodesAndEdges();

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
							Long repS = ps.get(t.p);
							Long repO = pt.get(t.p);
							Long classSetS = n2cs.get(t.s);
							Long classSetO = n2cs.get(t.o);
							boolean sTyped = (classSetS != null);
							boolean oTyped = (classSetO != null);
							if (!sTyped) {
								if (sn.contains(t.s)) {
									repS = t.s;
								}
								rep.put(t.s, repS);
							}
							else {
								repS = rep.get(t.s);
							}
							if (!oTyped) {
								if (sn.contains(t.o)) {
									repO = t.o;
								}
								rep.put(t.o, repO);
							}
							else {
								repO = rep.get(t.o);
							}
							edgesWithProv.addTriple(repS, t.p, repO);
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

		allTriplesSummarizationTime = classSetCreationTime + typeTriplesSummarizationTime + dataTriplesSummarizationTime;
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " triples overall in " + allTriplesSummarizationTime + " ms");
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
	protected void handleTypeTripleAfterData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	private Long getMin(ArrayList<Long> list) {
		Long min = list.get(0);
		for (Long i : list) {
			min = min < i ? min : i;
		}
		return min;
	}

	public void handleDataTriple2P(Triple t) {
		Long classSetS = n2cs.get(t.s);
		Long classSetO = n2cs.get(t.o);
		boolean sTyped = (classSetS != null);
		boolean oTyped = (classSetO != null);

		if (!sn.contains(t.s) && !sTyped) {
			if (n2o.get(t.s) == null) {
				n2o.put(t.s, new TreeSet<>());
			}
			n2o.get(t.s).add(t.p);
		}
		if (!sn.contains(t.o) && !oTyped) {
			if (n2i.get(t.o) == null) {
				n2i.put(t.o, new TreeSet<>());
			}
			n2i.get(t.o).add(t.p);
		}
		nodes.add(t.s);
		nodes.add(t.o);
	}

	private void findSummaryNodesAndEdges() {
		for (Long n: nodes) {
			ArrayList<Long> outgoing = new ArrayList<>();
			if (n2o.containsKey(n)) {
				for (Long p: n2o.get(n)) {
					if (ps.get(p) != null) {
						outgoing.add(ps.get(p));
					}
				}
			}
			Long minOutgoing = (!outgoing.isEmpty()) ? getMin(outgoing) : getNextSummaryNode();

			ArrayList<Long> incoming = new ArrayList<>();
			if (n2i.containsKey(n)) {
				for (Long p: n2i.get(n)) {
					if (pt.get(p) != null) {
						incoming.add(pt.get(p));
					}
				}
			}
			Long minIncoming = (!incoming.isEmpty()) ? getMin(incoming) : getNextSummaryNode();

			Long min = (minOutgoing < minIncoming) ? minOutgoing : minIncoming;

			if (n2o.containsKey(n)) {
				for (Long p: n2o.get(n)) {
					if (ps.get(p) != null) { // apply source-target replacements
						pt.replaceAll((k, v) -> (Objects.equals(v, ps.get(p))) ? min : v);
					}
					ps.put(p, min);
				}
			}
			if (n2i.containsKey(n)) {
				for (Long p: n2i.get(n)) {
					if (pt.get(p) != null) { // apply source-target replacements
						ps.replaceAll((k, v) -> (Objects.equals(v, pt.get(p))) ? min : v);
					}
					pt.put(p, min);
				}
			}
		}
	}
}