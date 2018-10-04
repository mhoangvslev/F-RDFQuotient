#!/bin/bash

for SUMMARY_TYPE in weak strong typedweak typedstrong; do
	for DATASET in dbpedia/dbpedia_persondata_en_uniq.nt springer/conference.nt nobel/nobel.nt insee/insee_geo.nt; do
		./scripts/loadAndSummarizeSplitFoldLeaves.sh $DATASET $SUMMARY_TYPE
	done
done
