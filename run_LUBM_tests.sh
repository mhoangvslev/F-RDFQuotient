for arg do
	mvn clean test -Dtest=summaries.biggerdata.LUBMTests#LUBMTest$arg | tee -a src/test/resources/LUBMTest$arg.log
done
