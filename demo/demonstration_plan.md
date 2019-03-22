# EDBT Demo plan

## 1. Main concepts
* RDF framework, RDF graphs
* schema nodes and edges, and type properties on the poster
* weak and strong equivalence relations on the poster

## 2. Scenario 0: loading
* load dataset1.nt  
$ `./RDFQuotient.sh --load "dataset.filename=dataset1.nt" -lp "conf/loading.properties"`

* load dataset2.nt  
$ `./RDFQuotient.sh --load "dataset.filename=dataset2.nt" -lp "conf/loading.properties"`

* load dataset3.nt  
$ `./RDFQuotient.sh --load "dataset.filename=dataset3.nt" -lp "conf/loading.properties"`

## 3. Scenario 1: weak and strong summarization with plain, step-by-step drawing
* weak summarization  
$ `./RDFQuotient.sh --summarize "dataset.filename=dataset1.nt" -sp "conf/scenario1w_summarization.properties"`

* strong summarization  
$ `./RDFQuotient.sh --summarize "dataset.filename=dataset1.nt" -sp "conf/scenario1s_summarization.properties"`

Observation: weak and strong summaries differ.

## 4. Type-first summarization
* example on the poster

## 5. Scenario 2: typed weak and typed strong summarization with plain, step-by-step drawing
* typed weak summarization  
$ `./RDFQuotient.sh --summarize "dataset.filename=dataset2.nt" -sp "conf/scenario2tw_summarization.properties"`

* typed strong summarization  
$ `./RDFQuotient.sh --summarize "dataset.filename=dataset2.nt" -sp "conf/scenario2ts_summarization.properties"`

Observation: typed weak and typed strong summaries differ.

## 6. Scenario 3: typed strong summarization with plain, when-changes drawing
* typed strong summarization  
$ `./RDFQuotient.sh --summarize "dataset.filename=dataset3.nt" -sp "conf/scenario3_summarization.properties"`

## 6. Scenario 4: typed strong summarization with split-and-fold-leaves, direct drawing
* typed strong summarization  
$ `./RDFQuotient.sh --summarize "dataset.filename=dataset3.nt" -sp "conf/scenario4_summarization.properties"`

## 7. Scenario 5: typed strong summarization with typed generalization and split-and-fold-leaves, direct drawing
* typed strong summarization  
$ `./RDFQuotient.sh --summarize "dataset.filename=dataset3.nt" -sp "conf/scenario5_summarization.properties"`

## 8. Summaries from the gallery
* [bsbm1m](https://project.inria.fr/rdfquotient/summaries-of-bsbm1m-dataset/)
* [bsbm10m](https://project.inria.fr/rdfquotient/summaries-of-bsbm10m-dataset/)
* [bsbm100m](https://project.inria.fr/rdfquotient/summaries-of-bsbm100m-dataset/)
* [foodista](https://project.inria.fr/rdfquotient/summaries-of-foodista-dataset/)

## 9. Scenario 6: incremental strong summarization with user input
* prepare a text editor from where the user can copy the triple

* strong summarization  
$ `./RDFQuotient.sh --summarize "dataset.filename=dataset2.nt" -sp "conf/scenario6_summarization.properties"`