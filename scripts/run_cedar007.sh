#!/bin/bash

for SUMMARY_TYPE in weak strong typedweak typedstrong 2pweak 2pstrong 2ptypedweak 2ptypedstrong 2pwuf onefb; do
	./summarize.sh bsbm/bsbm100m.nt $SUMMARY_TYPE false
done
