To store an RDF graph in postgres, dictionary-encode it there, summarize it,
and store the (encoded) summary in Postgres, call Builder.

Caution: the database name will be the one specified in conf/dataLoading.properties

Caution: the Postgres server has to be started before