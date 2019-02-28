#!/bin/bash

DATASET=$1
SATURATE=$2

# memory allocation in JVM set to 90 GiB
#java -Xmx90g -cp target/quotientSummaryWithDependencies-1.8-SNAPSHOT.jar fr.inria.cedar.quotientSummary.controller.Interface --load "dataset.filename=/data/datasets/$DATASET" | tee -a /data/datasets/$DATASET.log
java -Xmx90g -jar target/quotientSummaryWithDependencies-1.8-SNAPSHOT.jar --load "dataset.filename=/data/datasets/$DATASET" | tee -a /data/datasets/$DATASET.log