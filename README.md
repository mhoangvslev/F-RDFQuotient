To store an RDF file in postgres, dictionary-encode it there, summarize it (weak and typed weak summary for now) and 
store the (encoded) summary in Postgres, call SummaryBuilder with e.g.

loadsummarize pathToNTfile

Caution: the database name will be the one specified in conf/dataLoading.properties

Caution: the Postgres server has to be started before

