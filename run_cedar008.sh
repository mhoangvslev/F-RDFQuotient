#!/bin/bash

for SUMMARY_TYPE in weak strong typedweak typedstrong 2pweak 2pstrong 2ptypedweak 2ptypedstrong; do
	./summarize.sh dblp/dblp.nt $SUMMARY_TYPE false
done
