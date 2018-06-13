#!/bin/bash

for SUMMARY_TYPE in weak strong typedweak typedstrong 2pweak 2pstrong 2ptypedweak 2ptypedstrong onefb; do
	./summarize.sh dblp/dblp_large.nt $SUMMARY_TYPE false
done
