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
		String getUntypedTriplesString = ("select *  from " + encodedTriplesTableName + " where p <> " + typeConstantCode); 
		try {
			try (Statement getUntypedTriples = conn.createStatement()) {
				getUntypedTriples.setFetchSize(10000);
				try (ResultSet rs = getUntypedTriples.executeQuery(getUntypedTriplesString)) {
					while (rs.next()) {
						Triple t = new Triple(rs.getInt(1), rs.getInt(2), rs.getInt(3));
						updateCliquesOutOf(t);
						triplesSummarizedSoFar++;
						dataTriplesSummarizedSoFar++;
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

		// TODO: second pass here

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
						//this.drawSummaryAndGraph(conn, "after-" + triplesSummarizedSoFar + "-" + t.s + "-" + t.p + "-" + t.o);
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
