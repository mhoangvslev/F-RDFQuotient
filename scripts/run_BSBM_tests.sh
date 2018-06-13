for arg do
	mvn clean test -Dtest=summaries.biggerdata.BSBMTests#BSBMTest$arg | tee -a src/test/resources/BSBMTest$arg.log
done
