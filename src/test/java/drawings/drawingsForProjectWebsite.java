package drawings;

import fr.inria.cedar.ontosql.db.UnsupportedDatabaseEngineException;
import fr.inria.cedar.quotientSummary.controller.Builder;
import java.io.IOException;
import java.sql.SQLException;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;
import summaries.biggerdata.BSBMTests;

public class drawingsForProjectWebsite {
	private static final Logger LOGGER = Logger.getLogger(BSBMTests.class.getName());

	@Test
	public void test1() {
		LOGGER.setLevel(Level.INFO);
		String inputFileName = "src/test/resources/summariesForProjectWebsite/dblp_large_uniq_w.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "strong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedWithoutSaturation"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedWithoutSaturation", "draw", "dblp_large_uniq_w_drawing.nt"};
				Builder.main(argsExport);
			}
			catch (UnsupportedDatabaseEngineException ex) {
				LOGGER.error(ex);
			}
			finally {
				String[] argsCloseConnection = {"closeConnection"};
				try {
					Builder.main(argsCloseConnection);
				}
				catch (UnsupportedDatabaseEngineException ex1) {
					LOGGER.error(ex1);
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files" + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	@Test
	public void test2() {
		LOGGER.setLevel(Level.INFO);
		String inputFileName = "src/test/resources/summariesForProjectWebsite/dblp_large_uniq_s.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "strong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedWithoutSaturation"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedWithoutSaturation", "draw", "dblp_large_uniq_s_drawing.nt"};
				Builder.main(argsExport);
			}
			catch (UnsupportedDatabaseEngineException ex) {
				LOGGER.error(ex);
			}
			finally {
				String[] argsCloseConnection = {"closeConnection"};
				try {
					Builder.main(argsCloseConnection);
				}
				catch (UnsupportedDatabaseEngineException ex1) {
					LOGGER.error(ex1);
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files" + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	@Test
	public void test3() {
		LOGGER.setLevel(Level.INFO);
		String inputFileName = "src/test/resources/summariesForProjectWebsite/dbpedia_persondata_en_uniq_w.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "strong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedWithoutSaturation"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedWithoutSaturation", "draw", "dbpedia_persondata_en_uniq_w_drawing.nt"};
				Builder.main(argsExport);
			}
			catch (UnsupportedDatabaseEngineException ex) {
				LOGGER.error(ex);
			}
			finally {
				String[] argsCloseConnection = {"closeConnection"};
				try {
					Builder.main(argsCloseConnection);
				}
				catch (UnsupportedDatabaseEngineException ex1) {
					LOGGER.error(ex1);
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files" + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}

	@Test
	public void test4() {
		LOGGER.setLevel(Level.INFO);
		String inputFileName = "src/test/resources/summariesForProjectWebsite/dbpedia_persondata_en_uniq_s.nt";
		try {
			String[] argsSum = {"loadAndSummarize", "strong", inputFileName};
			try {
				Builder.main(argsSum);
				String[] argsSave = {"saveSummaryComputedWithoutSaturation"};
				Builder.main(argsSave);
				String[] argsExport = {"exportSummaryComputedWithoutSaturation", "draw", "dbpedia_persondata_en_uniq_s_drawing.nt"};
				Builder.main(argsExport);
			}
			catch (UnsupportedDatabaseEngineException ex) {
				LOGGER.error(ex);
			}
			finally {
				String[] argsCloseConnection = {"closeConnection"};
				try {
					Builder.main(argsCloseConnection);
				}
				catch (UnsupportedDatabaseEngineException ex1) {
					LOGGER.error(ex1);
				}
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to open .nt files" + e.toString());
		}
		catch (SQLException e) {
			throw new IllegalStateException("SQL error while summarizing " + e.toString());
		}
	}
}