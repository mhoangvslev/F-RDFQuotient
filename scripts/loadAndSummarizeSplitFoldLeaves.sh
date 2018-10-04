#!/bin/bash

DATASET=$1
SUMMARIZATION_METHOD=$2

java -Xmx90g -cp target/quotientSummaryWithDependencies-1.7-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.CustomSummarization /data/datasets/$DATASET $SUMMARIZATION_METHOD | tee -a /data/datasets/$DATASET.log
