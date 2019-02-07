#!/bin/bash

for DATASET in watdiv/watdiv100m.nt; do
	for SUMMARY_TYPE in typedweak typedstrong; do
		./scripts/summarize.sh $DATASET $SUMMARY_TYPE false foldleaves
	done
done
