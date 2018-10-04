#!/bin/bash

DATASET=$1
SUMMARY_TYPE=$2
SATURATED=$3
DRAW=$4

java -Xmx90g -cp target/quotientSummaryWithDependencies-1.7-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.BuilderCmd summarize /data/datasets/$DATASET $SUMMARY_TYPE $SATURATED true true true $DRAW | tee -a /data/datasets/$DATASET.log
