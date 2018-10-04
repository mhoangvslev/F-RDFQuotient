#!/bin/bash

for SUMMARY_TYPE in weak strong typedweak typedstrong; do
	./scripts/loadAndSummarizeSplitFoldLeaves.sh lubm/lubm1m.nt $SUMMARY_TYPE
done
