#!/bin/bash

for DATASET in /data/datasets/panama-papers/*.nt; do
	DATASET="panama-papers/${DATASET##*/}"
	./scripts/load.sh $DATASET ASSERTION_SAT
done
