#!/bin/bash

for DATASET in bsbm/bsbm1m.nt bsbm/bsbm10m.nt watdiv/watdiv10m.nt; do
	for SUMMARY_TYPE in typedweak typedstrong; do
		./scripts/summarize.sh $DATASET $SUMMARY_TYPE false foldleaves
	done
done
