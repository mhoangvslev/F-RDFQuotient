#!/bin/bash

DATASET=$1
SUMMARY_TYPE=$2
SATURATE=$3

java -Xmx100g -cp ../target/quotientSummaryWithDependencies-1.5-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.BuilderCmd summarize /data/datasets/$DATASET $SUMMARY_TYPE $SATURATE true true true | tee -a /data/datasets/$DATASET.log
