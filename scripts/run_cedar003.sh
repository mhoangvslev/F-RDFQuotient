#!/bin/bash

for DATASET in bsbm/bsbm1m.nt bsbm/bsbm10m.nt dbpedia/dbpedia_persondata_en_uniq.nt lubm/lubm1m.nt lubm/lubm10m.nt watdiv/watdiv10m_onto.nt; do
	for SATURATED in "true" "false"; do
		for SUMMARY_TYPE in weak strong typedweak typedstrong 2pweak 2pstrong 2ptypedweak 2ptypedstrong 2pweakunionfind onefb onefw; do
			./scripts/summarize.sh $DATASET $SUMMARY_TYPE $SATURATED false split_and_fold_leaves
		done
	done
done
