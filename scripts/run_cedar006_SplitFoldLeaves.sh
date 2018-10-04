#!/bin/bash

for DATASET in bsbm/bsbm100m.nt; do
	for SATURATED in "true" "false"; do
		for SUMMARY_TYPE in weak strong typedweak typedstrong onefb onefw; do
			./scripts/summarize.sh $DATASET $SUMMARY_TYPE $SATURATED foldleaves
		done
	done
done
