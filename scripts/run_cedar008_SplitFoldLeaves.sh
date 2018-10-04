#!/bin/bash

for SUMMARY_TYPE in weak strong typedweak typedstrong; do
	./scripts/loadAndSummarizeSplitFoldLeaves.sh dblp/dblp_large_uniq.nt $SUMMARY_TYPE
done
