#!/bin/bash

DATASET=$1
SUMMARY_TYPE=$2
SATURATED=$3
DRAW=$4

# timeout set to 1 hour and memory allocation in JVM set to 90 GiB
#timeout 1h java -Xmx90g -cp target/RDFQuotient-1.8-SNAPSHOT-with-dependencies.jar fr.inria.cedar.RDFQuotient.controller.Interface --summarize "dataset.filename=/data/datasets/$DATASET,summary.type=$SUMMARY_TYPE,summary.summarize_saturated_graph=$SATURATED,drawing.style=$DRAW" | tee -a /data/datasets/$DATASET.log
timeout 1h java -Xmx90g -jar target/RDFQuotient-1.8-SNAPSHOT-with-dependencies.jar --summarize "dataset.filename=/data/datasets/$DATASET,summary.type=$SUMMARY_TYPE,summary.summarize_saturated_graph=$SATURATED,drawing.style=$DRAW" | tee -a /data/datasets/$DATASET.log