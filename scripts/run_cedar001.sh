#!/bin/bash

for SUMMARY_TYPE in weak strong typedweak typedstrong 2pweak 2pstrong 2ptypedweak 2ptypedstrong 2pweakunionfind onefb; do
	for DATASET in dbpedia/dbpedia_persondata_en_uniq.nt springer/conference.nt nobel/nobel.nt insee/insee_geo.nt; do
		./scripts/summarize.sh $DATASET $SUMMARY_TYPE false
	done
done
