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
	elif [ "$1" = "2ponefb" ]; then
		echo "1fb"
	fi
}

for SUMMARY_TYPE in weak strong 2pweak 2pstrong 2pweakunionfind 2ponefb; do
	for DATASET in lubm/lubm1m_uniq_onto.nt lubm/lubm10m_uniq_onto.nt lubm/lubm100m_uniq_onto.nt; do
		# SATURATE + SUMMARIZE
		# summarize saturated
		#./scripts/summarize.sh $DATASET $SUMMARY_TYPE true false false

		# SHORTCUT
		# summarize not saturated
		#./scripts/summarize.sh $DATASET $SUMMARY_TYPE false false false
		DATASET=${DATASET%/*}\/summariesNT\/${DATASET##*/}
		DATASET=${DATASET%.*}\_$(translate_summary_name $SUMMARY_TYPE).nt
		# load the summary with saturation
		./scripts/load.sh $DATASET ASSERTION_SAT
		# summarize saturated
		./scripts/summarize.sh $DATASET $SUMMARY_TYPE true false split_and_fold_leaves
	done
done
