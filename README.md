# RDFQuotient

Authors: Ioana Manolescu, Paweł Guzewicz (CEDAR team at Inria Saclay, France), François Goasdoué (Université de Rennes 1 and CEDAR team)

Visit our [project website](https://project.inria.fr/rdfquotient/) for details about the concepts and algorithms. 

## Description
This software allows to build a **quotient summary of an RDF graph**, based on  an _equivalence relation_ between the graph nodes.  

An equivalence relation is a binary symmetric, transitive and reflexive relation.  

* The quotient of an RDF graph through a given equivalence relation is another graph, having one node for every equivalence class (group of equivalent nodes) of the input graph. 
* The edges of the quotient summary graph are derived from the input graph edges: whenever the input graph contained an edge a--p-->b, the quotient summary graph contains an edge rep(a)--p-->rep(b), where rep(a), rep(b) denote the summary nodes that correspond to a and to b in the original graph, respectively, and p denotes the edge of the label going from a to b (or, the value of the property connecting a to b in the input graph).

Our software allows to build six different summaries, four which we defined in our [paper](https://hal.inria.fr/hal-01325900v6) and two classical algorithms, namely 1-forward-and-backward bisimulation and 1-forward bisimulation quotients.

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
The code works by loading the RDF graph expected in **N-Triples format** with **no duplicates** into Postgres using OntoSQL/RDFDB library. Then, it reads it from there, stores the summary in Postgres (where subsequent applications can use it from) and in an output .nt file, as well as, writes vizualization of a summary into a DOT file. 
**Postgres** DBMS is used as a back-end and **must be installed**. 
To obtain summary visualizations, **[DOT](https://www.graphviz.org/)** needs to be installed as well.

Its two main functionalities can be understood by looking at our scripts:
 
* [load.sh](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/scripts/load.sh)
* [summarize.sh](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/scripts/summarize.sh)

The example usage of those primitives is the scheduled computation of the set of experiments for a given dataset, e.g.:

* [run_cedar001.sh](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/scripts/run_cedar001.sh)
* [run_cedar001_shortcut.sh](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/scripts/run_cedar001_shortcut.sh)

### IMPORTANT: before using our software
* read the [license](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/LICENSE.txt)
* read the [documentation](https://gitlab.inria.fr/cedar/RDFQuotient/blob/master/documentation.pdf), which contains detailed description of stand-alone and programmatic interfaces of RDFQuotient tool.
## Download
### Jar files
Click on a version number below to download stand-alone jar file.

* [1.8-SNAPSHOT](http://files.inria.fr/cedar/RDFQuotient/RDFQuotient-1.8-SNAPSHOT-with-dependencies.jar) (recommended) current, 1.8 pre-release version, still under development but rather stable, build from March 11, 2019
* [1.7](http://files.inria.fr/cedar/RDFQuotient/RDFQuotient-1.7-with-dependencies.jar) (legacy code) stable version released on October 19, 2018

### Building from sources
The loading functionality is provided by the OntoSQL/rdfDB package developed in CEDAR, on which this project depends.

In order to build the project:

1. Clone this git repository.
2. Download [OntoSQL jar file](http://files.inria.fr/cedar/RDFQuotient/ontosql-rdfdb-1.0.9-SNAPSHOT-with-dependencies.jar).
3. Download a modified [pom.xml file](http://files.inria.fr/cedar/RDFQuotient/pom.xml).
4. Replace the project pom.xml file with the modified pom.xml.
5. In line 25 of the modified pom.xml, replace "yourpath/ontosql-rdfdb-1.0.9-SNAPSHOT-with-dependencies.jar" with your local path to the OntoSQL jar file.
6. Run the mvn clean install command.