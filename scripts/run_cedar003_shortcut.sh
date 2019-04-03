#!/bin/bash

translate_summary_name() {
	if [ "$1" = "weak" ]; then
		echo "w"
	elif [ "$1" = "strong" ]; then
		echo "s"
	elif [ "$1" = "2pweak" ]; then
		echo "2pw"
	elif [ "$1" = "2pstrong" ]; then
		echo "2ps"
	elif [ "$1" = "2pweakunionfind" ]; then
		echo "2pwuf"
	elif [ "$1" = "onefb" ]; then
		echo "1fb"
	fi
}

for SUMMARY_TYPE in weak strong 2pweak 2pstrong 2pweakunionfind onefb; do
	for DATASET in bsbm/bsbm1m.nt bsbm/bsbm10m.nt; do
		# SATURATE + SUMMARIZE
		# summarize saturated
		./scripts/summarize.sh $DATASET $SUMMARY_TYPE true false false

		# SHORTCUT
		# summarize not saturated
		#./scripts/summarize.sh $DATASET $SUMMARY_TYPE false false false
		DATASET=${DATASET%.*}\_$(translate_summary_name $SUMMARY_TYPE).nt
		# load the summary with saturation
		./scripts/load.sh $DATASET true
		# summarize saturated
		./scripts/summarize.sh $DATASET $SUMMARY_TYPE true false split_and_fold_leaves
	done
done
