#!/bin/bash

for DATASET in /data/datasets/panama-papers/*.nt; do
	DATASET="panama-papers/${DATASET##*/}"
	for SATURATED in "true" "false"; do
		for SUMMARY_TYPE in weak strong 2ponefb 2ponefw; do
			./scripts/summarize.sh $DATASET $SUMMARY_TYPE $SATURATED false split_and_fold_leaves
		done
		for SUMMARY_TYPE in typedweak typedstrong; do
			./scripts/summarize.sh $DATASET $SUMMARY_TYPE $SATURATED true split_and_fold_leaves	
		done
	done
done
