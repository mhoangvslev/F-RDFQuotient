# RDFQuotient

Authors: Ioana Manolescu, Paweł Guzewicz (CEDAR team at Inria Saclay, France), François Goasdoué (Université de Rennes 1 and CEDAR team).

Visit our [project website](https://project.inria.fr/rdfquotient/) for details about the concepts and algorithms.

## Description
This software allows to build a **quotient summary of an RDF graph**, based on  an _equivalence relation_ between the graph nodes.  

An equivalence relation is a binary symmetric, transitive and reflexive relation.  

* The quotient of an RDF graph through a given equivalence relation is another graph, having one node for every equivalence class (group of equivalent nodes) of the input graph. 
* The edges of the quotient summary graph are derived from the input graph edges: whenever the input graph contained an edge a--p-->b, the quotient summary graph contains an edge rep(a)--p-->rep(b), where rep(a), rep(b) denote the summary nodes that correspond to a and to b in the original graph, respectively, and p denotes the edge of the label going from a to b (or, the value of the property connecting a to b in the input graph).

Our software allows to build six different summaries, four defined in our [paper](https://hal.inria.fr/hal-01325900v6) and two classical algorithms, namely 1-forward-and-backward bisimulation and 1-forward bisimulation quotients.

For each of our four summaries, two implementations are available: one "global" (or "two-pass") that needs to read the whole RDF graph before summarizing it, and one "incremental" that summarizes the graph while traversing it and continuously updates the summary.

This leads to a total of 10 algorithms:

* global-weak (in the code referred to as TwoPassWeak or 2pweak/2pw)
* incremental-weak (Weak or weak/w)
* global-strong (TwoPassStrong or 2pstrong/2ps)
* incremental-strong (Strong or strong/s)
* global-typed-weak (TwoPassTypedWeak or 2ptypedweak/2ptw)
* incremental-typed-weak (TypedWeak or typedweak/tw)
* global-typed-strong (TwoPassTypedStrong or 2ptypedstrong/2pts)
* incremental-typed-strong (TypedStrong or typedstrong/ts)
* 1-forward-backward-bisimulation (OneBisim or onefb/1fb)
* 1-forward-bisimulation (OneFW or onefw/1fw)

## Implementation
Our software is written in **Java** and compiled using Apache **Maven** build automation tool. It uses **Postgres** DBMS for data storage, and **[DOT](https://www.graphviz.org/)** tool for visualizations. The code design focuses on two basic operations: `load` and `summarize`. An RDF graph in **N-Triples format, with no duplicates** can be loaded into Postgres using `load` operation. The RDF graph that has been loaded in the database can be summarized using the `summarize` operation. The summary of the RDF graph is stored in Postgres (where subsequent applications can use it from), and in an output NT file. A visualization of a summary is written into a DOT file, and drawn using DOT into a PNG file.

### Example

1. The following command can be used to load an RDF graph:
`java -jar target/RDFQuotient-1.8-SNAPSHOT-with-dependencies.jar --load "dataset.filename=yourpath"`
2. The following command can be used to summarize the loaded RDF graph:
`java -jar target/RDFQuotient-1.8-SNAPSHOT-with-dependencies.jar --summarize "dataset.filename=yourpath"`

where `yourpath` needs to be replaced with the path to the RDF graph file.

## IMPORTANT NOTICE
### Before using our software
* read the [license](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt)
* read the [documentation](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/documentation.pdf), which contains a detailed description of standalone and programmatic interfaces of RDFQuotient tool

### Software prerequisites
* Java >=1.8 must be installed
* Maven >=3.0.5 must be installed if you compile the code from sources
* Postgres (in any [officially supported](https://www.postgresql.org/support/versioning/) version) must be installed
* [DOT](https://www.graphviz.org/) must be installed if you wish to enable drawing of the visualization into PNG file

### Running RDFQuotient
* Make sure the input RDF graph is written in **N-Triples format** with **no duplicates**
* Make sure the Postgres server is running
* Assert that the local configuration of your machine is passed to the software via command line arguments or properties file:
	* check the value of `dataset.filename` parameter
	* check the database configuration specified in `database.host`, `database.port`, `database.user`, `database.password` parameters
	* for the details about setting the parameters see the [documentation](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/documentation.pdf)

## Download
### Jar files (recommended)
Click on a version number below to download standalone jar file.

* [1.8-SNAPSHOT](http://files.inria.fr/cedar/RDFQuotient/RDFQuotient-1.8-SNAPSHOT-with-dependencies.jar) (recommended) current, 1.8 pre-release version, still under development but rather stable, build from March 24, 2019

### Building from sources
The loading process is provided by the OntoSQL/RDFDB library developped in CEDAR team. This library is a dependency in our project that is stored in a private repository. However, it does not prevent you from using our code as we provide a compiled jar file.

In order to build the project:

1. Clone this git repository.
2. Download [OntoSQL jar file](http://files.inria.fr/cedar/RDFQuotient/ontosql-rdfdb-1.0.10-SNAPSHOT-with-dependencies.jar).
3. Download a [modified pom.xml file](http://files.inria.fr/cedar/RDFQuotient/pom.xml).
4. Replace the project pom.xml file with the modified pom.xml.
5. In line 25 of the modified pom.xml, replace `yourpath/ontosql-rdfdb-1.0.10-SNAPSHOT-with-dependencies.jar` with your local path to the OntoSQL jar file.
6. Run the `mvn clean install -DskipTests` command.

In step 6, `mvn install` or `mvn clean install` part of the command attempts to first compile the code, and then execute the tests. We use the tests as an automatic means to assert basic correctness of our software. They are run in a controlled environment, where the parameters for a database connection are fixed. If you wish to run the tests and you use non-standard configuration of Postgres or DOT installation, make sure you adjust the configuration used for testing.

## Troubleshooting
Thank you for trying out our software! If you encounter any problem using it, please file an issue in this GitLab project.
