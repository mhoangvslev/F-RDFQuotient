# RDFQuotient

Authors: Ioana Manolescu, Paweł Guzewicz (Ecole Polytechnique, Institut Polytechnique de Paris, France and Inria, France), François Goasdoué (Université de Rennes 1, France, and CNRS, France, and IRISA, France).

Please visit our [project website](https://rdfquotient.inria.fr/) for more details about the concepts and algorithms.

## Description
This software enables building a **quotient summary of an RDF graph** based on an _equivalence relation_ between the graph nodes.  

* The quotient of an RDF graph through a given equivalence relation is another graph, having one node for every equivalence class (group of equivalent nodes) of the input graph. 
* The edges of the quotient summary graph are derived from the input graph edges: whenever the input graph contained an edge a--p-->b, the quotient summary graph contains an edge rep(a)--p-->rep(b), where rep(a), rep(b) denote the summary nodes that correspond to a and to b in the original graph, respectively, and p denotes the edge of the label going from a to b (or, the value of the property connecting a to b in the input graph).

## Implementation
Our RDFQuotient software is written in **Java** and compiled using Apache **Maven** build automation tool. It relies on the **Postgres** DBMS for data storage, and the **[DOT](https://www.graphviz.org/)** tool for visualizations. The code design focuses on two basic operations: `load` and `summarize`. An RDF graph in **N-Triples format, with no duplicates**, can be loaded into Postgres using `load` operation. The RDF graph that has been loaded in the database can be summarized using the `summarize` operation. The summary of the RDF graph is stored in Postgres (where subsequent applications can use it from) and in an output NT file. A visualization of a summary is written into a DOT file and drawn using DOT into a PNG file.

### Example

1. The following command can be used to load an RDF graph:
`java -jar target/RDFQuotient-1.9-with-dependencies.jar --load "dataset.filename=yourpath"`
2. The following command can be used to summarize the loaded RDF graph:
`java -jar target/RDFQuotient-1.9-with-dependencies.jar --summarize "dataset.filename=yourpath"`

where `yourpath` needs to be replaced with the path to the RDF graph file.


### Demo
A demonstration of the usage of our software can be found under the [demo](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/demo) directory, where scenarios we used are explained in the [demonstration plan](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/demo/demonstration_plan.md), and for convenience, command line calls are also listed in the [commands](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/demo/commands.txt) file.

## IMPORTANT INFORMATION

* Software [license](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt)
* Software [documentation](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/documentation.pdf), which contains a detailed description of standalone and programmatic interfaces of the RDFQuotient tool

### Software prerequisites
* Java >=1.8
* Maven >=3.0.5 if you compile the code from sources
* Postgres (in any [officially supported](https://www.postgresql.org/support/versioning/) version) 
* [DOT](https://www.graphviz.org/) if you wish to draw the visualization into a PNG file

### Running RDFQuotient
* Make sure the input RDF graph is written in **N-Triples format** with **no duplicates**
* Make sure the Postgres server is running
* Assert that the local configuration of your machine is passed to the software via command-line arguments or properties file:
	* check the value of the `dataset.filename` parameter
	* check the database configuration specified in the `database.host`, `database.port`, `database.user`, `database.password` parameters
	* for the details about setting the parameters see the [documentation](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/documentation.pdf)

## Download
### Versions
* Version 1.9: current, stable
* Version 2.0-SNAPSHOT: latest, experimental

See the detail below.

### Jar files (recommended)
Click on the version number below to download the standalone jar file.

* [Version 1.9](https://files.inria.fr/cedar/RDFQuotient/RDFQuotient-1.9-with-dependencies.jar): the current stable release built on June 18, 2021.

### Building from sources
The loading process is provided by the OntoSQL/RDFDB library developed in the CEDAR team. This library is a dependency in our project that is stored in a private repository. However, it does not prevent you from using our code as we provide a compiled jar file.

To build the project:

1. Clone this git repository.
2. The latest code corresponds to the experimental version 2.0-SNAPSHOT, which has been only released as a jar to the CEDAR team private Maven repository. (You may skip steps 3 and 4 if you have access to the CEDAR repositories and simply add RDFQuotient as a dependency.) The jar of the stable version 1.9 was compiled from sources at the commit marked with the [v1.9 git tag](https://gitlab.inria.fr/cedar/RDFQuotient/-/tags/v1.9).
3. Download [OntoSQL jar file v1.0.13-SNAPSHOT](https://files.inria.fr/cedar/RDFQuotient/ontosql-rdfdb-1.0.13-SNAPSHOT-with-dependencies.jar).
4. Run `mvn install:install-file -Dfile=yourpath/ontosql-rdfdb-1.0.13-SNAPSHOT-with-dependencies.jar -DgroupId=fr.inria.cedar.ontosql -DartifactId=ontosql-rdfdb -Dversion=1.0.13-SNAPSHOT -Dpackaging=jar` command to install our library in your local maven repository, where `yourpath/ontosql-rdfdb-1.0.13-SNAPSHOT-with-dependencies.jar` needs to be replaced with your local path to the download OntoSQL jar file.
5. Run `mvn clean install -DskipTests` command.

In step 4, `mvn install` or `mvn clean install` part of the command attempts to first compile the code, and then execute the tests. We use the tests as an automatic means to assert the basic correctness of our software. They are run in a controlled environment, where the parameters for a database connection are fixed. If you wish to run the tests, and you use a non-standard configuration of Postgres or DOT installation, make sure you adjust the configuration used for testing.

### As a Maven dependency (only if you can access the CEDAR repository)
		<dependency>
			<groupId>fr.inria.cedar</groupId>
			<artifactId>RDFQuotient</artifactId>
			<version>2.0-SNAPSHOT</version>
		</dependency>

### Docker configuration file
To install RDFQuotient in version 2.0-SNAPSHOT with all the dependencies and prerequisites in a Docker container, use the [rdfquotient.dockerfile](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/rdfquotient.dockerfile).
Credits for the Docker file go to Matteo Lissandrini.

## Troubleshooting
Thank you for trying out our software! If you encounter any problem using it, please file an issue in this GitLab project.

## More technical details
Our software enables building eight different summaries, four defined in our [paper](https://hal.inria.fr/hal-02530206v2), and four classical algorithms, namely typed, input-output and typed, 1-forward bisimulation, and 1-forward-and-backwar bisimulation quotients.

For each of our four summaries, two implementations are available: one "global" (or "two-pass") that needs to read the whole RDF graph before summarizing it, and one "incremental" that summarizes the graph while traversing it and continuously updates the summary.

This leads to a total of 12 algorithms:

* typed (TypedSummary or typed/t)
* input-output and typed (InputOutputAndTypedSummary or 2pinputoutput/2pioat)
* 1-forward-bisimulation (OneFWSummary or 2ponefw/2p1fw)
* 1-forward-backward-bisimulation (OneBisimSummary or 2ponefb/2p1fb)
* global-strong (TwoPassStrongSummary or 2pstrong/2ps)
* global-weak (TwoPassWeakSummary or 2pweak/2pw)
* global-typed-strong (TwoPassTypedStrongSummary or 2ptypedstrong/2pts)
* global-typed-weak (TwoPassTypedWeakSummary or 2ptypedweak/2ptw)
* incremental-strong (StrongSummary or strong/s)
* incremental-weak (WeakSummary or weak/w)
* incremental-typed-strong (TypedStrongSummary or typedstrong/ts)
* incremental-typed-weak (TypedWeakSummary or typedweak/tw)
