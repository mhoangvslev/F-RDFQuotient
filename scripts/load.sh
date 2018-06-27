#!/bin/bash

DATASET=$1
SATURATE=$2

java -Xmx80g -cp target/quotientSummaryWithDependencies-1.5-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.BuilderCmd load /data/datasets/$DATASET $SATURATE true | tee -a /data/datasets/$DATASET.log
