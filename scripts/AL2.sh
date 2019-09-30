#!/bin/bash

logfile=$1"_AL_2".log

mvn exec:java -Dexec.mainClass=fr.inria.cedar.RDFQuotient.analysis.Accuracy -Dexec.args="$*" | tee -a $logfile
