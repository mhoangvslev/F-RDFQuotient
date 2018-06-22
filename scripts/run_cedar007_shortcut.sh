#!/bin/bash

translate_summary_name() {
	if [ "$1" = "weak" ]; then
		return "w"
	elif [ "$1" = "strong" ]; then
		return "s"
	elif [ "$1" = "2pweak" ]; then
		return "2pw"
	elif [ "$1" = "2pstrong" ]; then
		return "2ps"
	elif [ "$1" = "2pweakunionfind" ]; then
		return "2pwuf"
	fi
}

for SUMMARY_TYPE in weak strong 2pweak 2pstrong 2pweakunionfind; do
	# SATURATE + SUMMARIZE
	# summarize saturated
	./scripts/summarize.sh bsbm/bsbm100m.nt $SUMMARY_TYPE true

	# SHORTCUT
	# summarize not saturated
	#./scripts/summarize.sh bsbm/bsbm100m.nt $SUMMARY_TYPE false
	# load the summary with saturation
	./scripts/load.sh bsbm/bsbm100m_$(translate_summary_name $SUMMARY_TYPE).nt true
	# summarize saturated
	./scripts/summarize.sh bsbm/bsbm100m_$(translate_summary_name $SUMMARY_TYPE).nt $SUMMARY_TYPE true
done
