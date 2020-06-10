#!/bin/bash

for DATASET in /data/datasets/panama-papers/*.nt; do
	DATASET="panama-papers/${DATASET##*/}"
	./scripts/load.sh $DATASET RDFS_SAT
done
