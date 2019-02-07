#!/bin/bash

for DATASET in dblp/dblp_large_uniq.nt; do
	for SUMMARY_TYPE in typedweak typedstrong; do
		./scripts/summarize.sh $DATASET $SUMMARY_TYPE false foldleaves
	done
done
