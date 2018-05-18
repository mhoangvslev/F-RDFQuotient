package fr.inria.cedar.quotientSummary.strong;

import fr.inria.cedar.quotientSummary.datastructures.Triple;
import fr.inria.cedar.quotientSummary.util.RDF2SQLEncoding;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class TwoPassStrongSummary extends StrongOrTypedStrongSummary {
	private static final Logger LOGGER = Logger.getLogger(TwoPassStrongSummary.class.getName());

	public TwoPassStrongSummary(String triplesFileName, String triplesTableName, String encodedTriplesTableName, String dictionaryTableName) {
		super();
		LOGGER.setLevel(Level.INFO);
		this.triplesFileName = triplesFileName;
		this.triplesTableName = triplesTableName;
		this.encodedTriplesTableName = encodedTriplesTableName;
		this.dictionaryTableName = dictionaryTableName;
		this.summaryTablePrefix = TWO_PASS_STRONG_SUMMARY_PREFIX;
		this.isTypeFirst = false;
	}

	/**
	 * This must be used to read a S summary from Postgres.
	 *
	 * @param conn
	 */
	public TwoPassStrongSummary(Connection conn) {
		this.conn = conn; 
		this.summaryTablePrefix = TWO_PASS_STRONG_SUMMARY_PREFIX; 
		LOGGER.info("Reading Two-pass Strong summary from Postgres, setting up special URIs from the dictionary");
		RDF2SQLEncoding.setUp(conn, "dictionary");
		String getSummaryTriples = getSummaryTriplesSQLQuery();
		try {
			Statement getTriples = conn.createStatement();
			// LOGGER.debug("Created statement");
			ResultSet rs = getTriples.executeQuery(getSummaryTriples);
			// LOGGER.debug("Asking for summary triples")
			while (rs.next()) {
				Long s = rs.getLong(1);
				Long p = rs.getLong(2);
				Long o = rs.getLong(3);
				edgesWithProv.addTriple(s, p, o);
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Unable to read Two-Pass Strong summary from Postgres: " + e.toString());
		}
		System.out.println("Read Two-Pass Strong summary from Postgres");
	}

	/**
	 * this must be called after the constructor as the summary needs to ask more queries
	 * for patching itself up during summarization.
	 * 
	 * @param conn
	 */
	public void setConn(Connection conn){
		this.conn = conn; 
	}

	/**
	 * Summarizes an RDF graph assuming the data triples are in Postgres
	 *
	 * @param conn
	 */
	@Override
	public void summarizeFromPostgres(Connection conn){
		this.setConn(conn);
		long start = System.currentTimeMillis();

		String tableName = ""; 
		String dataTriplesFileName =""; 

		// this is needed to find the constants associated to special RDF properties
		RDF2SQLEncoding.setUp(conn, ""); 
		long typeConstantCode = RDF2SQLEncoding.getTypeCode();
		if (typeConstantCode != -1) {
			this.typeTriplesExist = true;
			avoidCollisionsWhenAssigningSummaryNodes(conn);
		}
		triplesSummarizedSoFar = 0;
		String getUntypedTriplesString = ("select *  from " + tableName + " where p <> " + typeConstantCode); 
		try {
			conn.setAutoCommit(false);
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						updateCliquesOutOf(t);
						//System.out.println("Summary has become: " + this.toString());
						triplesSummarizedSoFar++;
						//this.drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing data triples " + e.toString());
		}

		// TODO: second pass here.

		dataTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + triplesSummarizedSoFar + " data triples in " + dataTriplesSummarizationTime + " ms");

		String getTypedTriplesString = ("select *  from " + tableName + " where p = " + typeConstantCode);
		try {
			try (Statement getTypedTriples = conn.createStatement()) {
				getTypedTriples.setFetchSize(1000);
				try (ResultSet rs = getTypedTriples.executeQuery(getTypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						//System.out.println("#### Type triple " + t.toString());
						this.handleTypeTripleAfterData(t);
						triplesSummarizedSoFar++;
						//this.drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
						//System.out.println("Summary now has " + getSummaryEdges().size() + " triples");

					}
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Postgres error encountered while summarizing type triples: " + e.toString());
		}

		allTriplesSummarizationTime = System.currentTimeMillis() - start;
		System.out.println("Summarized " + triplesSummarizedSoFar + " triples in " + allTriplesSummarizationTime + " ms");
		display();
		this.writeToFileAndDraw();
	}

	private void updateCliquesOutOf(Triple t) {
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
	}
}
