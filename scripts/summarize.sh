#!/bin/bash

DATASET=$1
SUMMARY_TYPE=$2
SATURATED=$3
DRAW=$4

timeout 1h java -Xmx90g -cp target/quotientSummaryWithDependencies-1.8-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.Interface --summarize "dataset.filename=/data/datasets/$DATASET,summary.type=$SUMMARY_TYPE,summary.summarize_saturated_graph=$SATURATED,drawing.style=$DRAW" | tee -a /data/datasets/$DATASET.log
