#!/bin/bash

DATASET=$1
SATURATION_TYPE=$2

# memory allocation in JVM set to 90 GiB
#java -Xmx90g -cp target/RDFQuotient-1.9.jar fr.inria.cedar.RDFQuotient.controller.Interface --load "dataset.filename=/data/datasets/$DATASET,saturation.enable=$SATURATE" | tee -a /data/datasets/$DATASET.log
java -Xmx90g -jar target/RDFQuotient-1.9.jar --load "dataset.filename=/data/datasets/$DATASET,saturation.type=$SATURATION_TYPE" | tee -a /data/datasets/$DATASET.log
