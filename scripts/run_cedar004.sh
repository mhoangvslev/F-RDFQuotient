#!/bin/bash

for DATASET in lubm/lubm100m.nt watdiv/watdiv100m_onto.nt; do
	for SATURATED in "true" "false"; do
		for SUMMARY_TYPE in weak strong typedweak typedstrong 2pweak 2pstrong 2ptypedweak 2ptypedstrong 2pweakunionfind 2ponefb 2ponefw typed 2pinputoutput 2pbisim; do
			./scripts/summarize.sh $DATASET $SUMMARY_TYPE $SATURATED false split_and_fold_leaves
		done
	done
done
