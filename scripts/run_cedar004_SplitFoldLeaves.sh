#!/bin/bash

for DATASET in dblp/dblp_large_uniq.nt lubm/lubm100m.nt; do
	for SATURATED in "true" "false" do;
		for SUMMARY_TYPE in weak strong typedweak typedstrong onefb onefw; do
			./scripts/summarizeSplitFoldLeaves.sh $DATASET $SUMMARY_TYPE $SATURATED foldleaves
		done
	done
done
