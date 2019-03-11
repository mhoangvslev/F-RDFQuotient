#!/bin/bash

DATASET=$1
SATURATE=$2

# memory allocation in JVM set to 90 GiB
#java -Xmx90g -cp target/RDFQuotient-1.8-SNAPSHOT-with-dependencies.jar fr.inria.cedar.RDFQuotient.controller.Interface --load "dataset.filename=/data/datasets/$DATASET" | tee -a /data/datasets/$DATASET.log
java -Xmx90g -jar target/RDFQuotient-1.8-SNAPSHOT-with-dependencies.jar --load "dataset.filename=/data/datasets/$DATASET" | tee -a /data/datasets/$DATASET.log