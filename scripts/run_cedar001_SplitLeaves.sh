#!/bin/bash

for DATASET in abuse/abuse.nt clean_energy/clean_energy_data_uniq.nt ctgovdata/ctgovdata.nt enelshops/enelshops.nt foodista/foodista.nt insee/insee_geo.nt nasa/nasa.nt nobel/nobel.nt nobelprizes/nobelprizes_uniq.nt springer/conference.nt; do
	for SATURATED in "true" "false"; do
		for SUMMARY_TYPE in weak strong typedweak typedstrong onefb onefw; do
			./scripts/summarize.sh $DATASET $SUMMARY_TYPE $SATURATED splitleaves
		done
	done
done
