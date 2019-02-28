Project: quotientSummary
Version: 1.7 (stable version released on October 19, 2018), 1.8-SNAPSHOT (under development)

Authors: Ioana Manolescu, Paweł Guzewicz (CEDAR team at Inria Saclay)

This software allows to build quotient summaries of RDF graphs. 

An equivalence relation between the nodes of an RDF graph 
is a symmetric, transitive and reflexive relation over the nodes. 

* Given an equivalence relation, the quotient of an RDF graph
through that relation is another graph, having one node for every equivalence class (group of equivalent nodes) of the input 
graph. 
* The edges of the quotient summary graph are derived from the input graph edges: 
whenever the input graph contained an edge a--p-->b, the quotient summary graph contains an edge rep(a)--p-->rep(b), 
where rep(a), rep(b) denote, respectively, the summary nodes that correspond to a, respectively, to b in the original graph,
and p denotes the edge of the label going from a to b (or, the value of the property connecting a to be in the input graph).

Our software allows to build five different summaries, four which we defined  
(https://hal.inria.fr/hal-01325900v6) and a classical one, namely 
1-forward-and-backward bisimulation quotient. 

For each of our four summaries, two implementations are available: one "global"
(or "two-pass", that needs to read the whole RDF graph before summarizing it), 
and one "incremental" (that summarizes the graph while traversing it and 
continuously updates the summary). 

This leads to a total of nine algorithms: 

* global-weak (in the code referred to as TwoPassWeak)
* incremental-weak (Weak)
* global-strong (TwoPassStrong)
* incremental-strong (Strong)
* global-typed-weak (TwoPassTypedWeak)
* incremental-typed-weak (TypedWeak)
* global-typed-strong (TwoPassTypedStrong)
* incremental-typed-strong (TypedStrong)
* 1-forward-backward-bisimulation (OneBisim)

The code works by first, loading the RDF graph **expected in .nt format** into 
Postgres, then reading it from there, storing the summary in an output .nt file
(and also in Postgres, where subsequent applications can use it from).

The loading functionality is provided by the OntoSQL/rdfDB package developed in
CEDAR, on which this project depends. 


The framework can be used as a standalone command-line application. 
An interface is provided in the BuilderCmd class. 
For our own experimentation we have created running scripts that facilitate 
interations with the software. Namely, two main functionalities of the BuilderCmd 
can be accessed in the similar manner as in the scripts 
[load.sh](https://gitlab.inria.fr/cedar/quotientSummary/blob/master/scripts/load.sh) and [summarize.sh](https://gitlab.inria.fr/cedar/quotientSummary/blob/master/scripts/summarize.sh). The example usage of those promitives in the scheduled computation of the set of experiments for a given datset can be found e.g. in [run_cedar001.sh](https://gitlab.inria.fr/cedar/quotientSummary/blob/master/scripts/run_cedar001.sh) and [run_cedar001_shortcut.sh](https://gitlab.inria.fr/cedar/quotientSummary/blob/master/scripts/run_cedar001_shortcut.sh)


To start using it, download the jar of the project 
[here](http://files.inria.fr/cedar/quotientSummaryWithDependencies-1.8.jar) 
or build it from sources by cloning the repository.

Caution: the Postgres server has to be started before the project can run.

Good to know: the database name will be the one specified in conf/dataLoading.properties