#!/bin/bash

for SUMMARY_TYPE in weak strong typedweak typedstrong 2pweak 2pstrong 2ptypedweak 2ptypedstrong 2pweakunionfind onefb; do
	./scripts/summarize.sh bsbm/bsbm1m.nt $SUMMARY_TYPE false
done
