#!/bin/bash

for DATASET in dblp/dblp_large_uniq.nt lubm/lubm100m.nt; do
	for SATURATED in "true" "false"; do
		for SUMMARY_TYPE in weak strong typedweak typedstrong onefb onefw; do
			./scripts/summarize.sh $DATASET $SUMMARY_TYPE $SATURATED splitleaves
		done
	done
done
