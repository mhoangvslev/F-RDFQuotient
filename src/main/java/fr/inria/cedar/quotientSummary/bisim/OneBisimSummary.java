package fr.inria.cedar.quotientSummary.bisim;

import fr.inria.cedar.quotientSummary.Summary;
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

public class OneBisimSummary extends Summary{
	private static final Logger LOGGER = Logger.getLogger(OneBisimSummary.class.getName());

	HashMap<Long, TreeSet<Long>> n2ip; // node to incoming property set
	HashMap<Long, TreeSet<Long>> n2op; // node to outgoing property set
	HashMap<TreeSet<Long>, HashMap<TreeSet<Long>, Long>> ip2op2sn; // incoming property set to outgoing property set to summary node

	public OneBisimSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = ONEFB_SUMMARY_PREFIX;
		this.isTypeFirst = false;
		this.isTwoPass = true;
		this.n2ip = new HashMap<>();
		this.n2op = new HashMap<>(); 
		this.ip2op2sn = new HashMap<>(); 
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
		LOGGER.info("Classifying data nodes");
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
						else
							classifyDataTriple(t);
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
		LOGGER.info("Representing data nodes");
		representDataNodes(); 
		LOGGER.info("Representing data triples");
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
							// do nothing, it is already represented
						}
						else
							representDataTriple(t);
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
		LOGGER.info("Representing type triples");
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

		allTriplesSummarizationTime = nonTypeTriplesSummarizationTime + typeTriplesSummarizationTime;
		LOGGER.info("Summarized " + triplesSummarizedSoFar + " overall triples in " + allTriplesSummarizationTime + " ms");
	}

	@Override
	protected void handleTypeTripleBeforeData(Triple t) {
		throw new IllegalStateException("This method does not belong to " + this.getClass().getName());
	}

	private void representDataNodes() {
		// all nodes with outgoing edges and possibly incoming edges:
		for (Long n: n2op.keySet()){
			TreeSet<Long> nop = n2op.get(n);
			TreeSet<Long> nip = n2ip.get(n);
			Long summaryNode = getSummaryNode(nop, nip);
			if (summaryNode == null){
				summaryNode = createSummaryNode(nop, nip);
			}
			//LOGGER.info("REPRESENTED NODE (1) " + RDF2SQLEncoding.dictionaryDecode(n) + " BY " + sn); 
			rep.put(n, summaryNode);
		}
		// all nodes with incoming but not outgoing edges (those with both are covered above): 
		for (Long n: n2ip.keySet()){
			if (n2op.get(n) == null){
				TreeSet<Long> nop = n2op.get(n);
				TreeSet<Long> nip = n2ip.get(n);
				Long summaryNode = getSummaryNode(nop, nip);
				if (summaryNode == null){
					summaryNode = createSummaryNode(nop, nip);
				}
				//LOGGER.info("REPRESENTED NODE (2) " + RDF2SQLEncoding.dictionaryDecode(n) + " BY " + sn); 
				rep.put(n, summaryNode);
			}
		}
	}

	// creates the last data node representatives (those not already represented above)
	// and represents all data triples
	private void representDataTriple(Triple t) {
		//LOGGER.info("REPRESENTING DATA TRIPLE " + RDF2SQLEncoding.decode(t).toString()); 
		Long sRep = rep.get(t.s);
		if (sRep == null){
			TreeSet<Long> sop = n2op.get(t.s);
			TreeSet<Long> sip = n2ip.get(t.s);
			// probably both are null. We know rep doesn't exist, so we create it: 
			sRep = getSummaryNode(sop, sip);
			if (sRep == null){
				sRep = createSummaryNode(sop, sip);
			}
			rep.put(t.s, sRep); 
		}
		Long oRep = rep.get(t.o);
		if (oRep == null){
			TreeSet<Long> oop = n2op.get(t.s);
			TreeSet<Long> oip = n2ip.get(t.s);
			// probably both are null.  We know rep doesn't exist, so we create it: 
			oRep = getSummaryNode(oop, oip);
			if (oRep == null){
				oRep = createSummaryNode(oop, oip);
			}
			rep.put(t.o, oRep); 
		}
		this.edgesWithProv.addTriple(sRep, t.p, oRep);
	}

	private Long createSummaryNode(TreeSet<Long> nop, TreeSet<Long> nip) {
		Long n = this.getNextSummaryNode();
		HashMap<TreeSet<Long>, Long> o2n = this.ip2op2sn.get(nip);
		if (o2n == null){
			o2n = new HashMap<>();
			this.ip2op2sn.put(nip, o2n);
		}
		o2n.put(nop, n);
		return n; 
	}

	private Long getSummaryNode(TreeSet<Long> nop, TreeSet<Long> nip) {
		HashMap<TreeSet<Long>, Long> o2n = this.ip2op2sn.get(nip);
		if (o2n == null){
			return null;
		}
		return (o2n.get(nop));
	}

	@Override
	protected void handleTypeTripleAfterData(Triple t) {
		rep.put(t.o, t.o); 
		this.edgesWithProv.addTriple(rep.get(t.s), t.p, t.o);
	}

	private void classifyDataTriple(Triple t) {
		TreeSet<Long> previousSOP = n2op.get(t.s);
		if (previousSOP == null){
			previousSOP = new TreeSet<>();
			n2op.put(t.s, previousSOP);
		}
		previousSOP.add(t.p); 
		TreeSet<Long> previousOIP = n2ip.get(t.o);
		if (previousOIP == null){
			previousOIP = new TreeSet<>();
			n2ip.put(t.o, previousOIP);
		}
		previousOIP.add(t.p); 
	}

	@Override
	protected void consistencyChecks() {
		// TODO Auto-generated method stub
	}
}
