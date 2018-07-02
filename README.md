Project: quotientSummary 1.6 (stable version released on June 30, 2018)

Authors: Ioana Manolescu, Paweł Guzewicz (CEDAR team at Inria Saclay)

This project is an implementation of the quotient summaries for RDF graphs. The following algorithms are available:
* global-weak (in the code referred to as TwoPassWeak and TwoPassWeakWithUnionFind - two variants for performance study)
* incremental-weak (Weak)
* global-strong (TwoPassStrong)
* incremental-strong (Strong)
* global-typed-weak (TwoPassTypedWeak)
* incremental-typed-weak (TypedWeak)
* global-typed-strong (TwoPassTypedStrong)
* incremental-typed-strong (TypedStrong)
* 1-forward-backward-bisimulation (OneBisim)

Framework's features:
* loading of RDF graphs to Postgres database (with/without saturation - using other packages developped at CEDAR team)
	* stores an RDF graph in Postgres and dictionary-encode it there (integers used in place of strings in order to save space)
* summarization of RDF graphs
	* output can be stored in Postgres and/or text file on disk

The framework can be used as a standalone command-line application. An interface is provided in the BuilderCmd class. For the sake of our own experimentation we have created running scripts that facilitate interations with the software. Namely, two main functionalities of the BuilderCmd can be accessed in the simmilar manner as in the scripts [load.sh](https://gitlab.inria.fr/cedar/quotientSummary/blob/master/scripts/load.sh) and [summarize.sh](https://gitlab.inria.fr/cedar/quotientSummary/blob/master/scripts/summarize.sh). The example usage of those promitives in the scheduled computation of the set of experiments for a given datset can be found e.g. in [run_cedar001.sh](https://gitlab.inria.fr/cedar/quotientSummary/blob/master/scripts/run_cedar001.sh) and [run_cedar001_shortcut.sh](https://gitlab.inria.fr/cedar/quotientSummary/blob/master/scripts/run_cedar001_shortcut.sh)


To start using it, you just need to download the jar of the project [here](http://files.inria.fr/cedar/quotientSummaryWithDependencies-1.6.jar) or build it from sources by cloning the repository.

Caution: the Postgres server has to be started before

Caution: the database name will be the one specified in conf/dataLoading.properties
