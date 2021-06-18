#!/bin/bash

DATASET=$1
SUMMARY_TYPE=$2
SATURATED=$3
TYPE_GENERALIZATION=$4
DRAW=$5

# timeout set to 1 hour and memory allocation in JVM set to 90 GiB
#timeout 1h java -Xmx90g -cp target/RDFQuotient-1.9.jar fr.inria.cedar.RDFQuotient.controller.Interface --summarize "dataset.filename=/data/datasets/$DATASET,summary.type=$SUMMARY_TYPE,summary.summarize_saturated_graph=$SATURATED,summary.replace_type_with_most_general_type=$TYPE_GENERALIZATION,drawing.style=$DRAW" | tee -a /data/datasets/$DATASET.log
timeout 1h java -Xmx90g -jar target/RDFQuotient-1.9.jar --summarize "dataset.filename=/data/datasets/$DATASET,summary.type=$SUMMARY_TYPE,summary.summarize_saturated_graph=$SATURATED,summary.replace_type_with_most_general_type=$TYPE_GENERALIZATION,drawing.style=$DRAW" | tee -a /data/datasets/$DATASET.log
