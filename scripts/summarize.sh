#!/bin/bash

DATASET=$1
SUMMARY_TYPE=$2
SATURATED=$3

java -Xmx80g -cp target/quotientSummaryWithDependencies-1.5-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.BuilderCmd summarize /data/datasets/$DATASET $SUMMARY_TYPE $SATURATED true true true | tee -a /data/datasets/$DATASET.log
