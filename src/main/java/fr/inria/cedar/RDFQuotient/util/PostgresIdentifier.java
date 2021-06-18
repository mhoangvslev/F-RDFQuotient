//Initial software, [Manolescu-Goujot, Goasdoué, Guzewicz], Copyright C Inria and Rennes 1 University, see the license available at https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt

package fr.inria.cedar.RDFQuotient.util;

public class PostgresIdentifier {
	public static String escapeQuotes(String unescapedIdentifier) {
		StringBuilder newTableName = new StringBuilder();
		for (int i = 0; i < unescapedIdentifier.length(); i++) {
			if (unescapedIdentifier.charAt(i) == '"')
				newTableName.append("\"");
			newTableName.append(unescapedIdentifier.charAt(i));
		}
		return newTableName.toString();
	}

	/*
		The whole name of the database is written as a quoted identifier. No
		need to escape any other characters than a double quote. This relies on
		standard_conforming_strings parameter being set to on (default as of
		Postgres 9.1).
		Reference: https://www.postgresql.org/docs/current/static/sql-syntax-lexical.html#SQL-SYNTAX-IDENTIFIERS
	 */
	public static String escapedQuotedId(String unescapedIdentifier) {
		return "\"" + escapeQuotes(unescapedIdentifier) + "\"";
	}

	/*
		Uses Postgres feature of dollar-quoted identifiers
	 */
	public static String escapedDollarQuotedId(String unescapedIdentifier) {
		return "$name$" + unescapedIdentifier + "$name$";
	}
}
