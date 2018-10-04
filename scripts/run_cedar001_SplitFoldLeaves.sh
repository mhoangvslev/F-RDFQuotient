#!/bin/bash

for DATASET in abuse/abuse.nt clean_energy/clean_energy_data.nt ctgovdata/ctgovdata.nt enelshops/enelshops.nt foodista/foodista.nt insee/insee_geo.nt nasa/nasa.nt nobel/nobel.nt nobelprizes/nobelprizes.nt springer/conference.nt; do
	for SATURATED in "true" "false" do;
		for SUMMARY_TYPE in weak strong typedweak typedstrong onefb onefw; do
			./scripts/summarizeSplitFoldLeaves.sh $DATASET $SUMMARY_TYPE $SATURATED foldleaves
		done
	done
done
