#!/bin/bash

translate_summary_name() {
	if [ "$1" = "weak" ]; then
		return "w"
	elif [ "$1" = "strong" ]; then
		return "s"
	elif [ "$1" = "typedweak" ]; then
		return "tw"
	elif [ "$1" = "typedstrong" ]; then
		return "ts"
	elif [ "$1" = "2pweak" ]; then
		return "2pw"
	elif [ "$1" = "2pstrong" ]; then
		return "2ps"
	elif [ "$1" = "2ptypedweak" ]; then
		return "2ptw"
	elif [ "$1" = "2ptypedstrong" ]; then
		return "2pts"
	elif [ "$1" = "2pweakunionfind" ]; then
		return "2pwuf"
	elif [ "$1" = "onefb" ]; then
		return "1fb"
	fi
}

for SUMMARY_TYPE in weak strong typedweak typedstrong 2pweak 2pstrong 2ptypedweak 2ptypedstrong 2pweakunionfind onefb; do
	# SATURATE + SUMMARIZE
	# summarize saturated
	./scripts/summarize.sh bsbm/bsbm1m.nt $SUMMARY_TYPE true

	# SHORTCUT
	# summarize not saturated
	./scripts/summarize.sh bsbm/bsbm1m.nt $SUMMARY_TYPE true
	# load the summary with saturation
	./scripts/load.sh bsbm/bsbm1m_$(translate_summary_name($SUMMARY_TYPE)).nt true
	# summarize saturated
	./scripts/summarize.sh bsbm/bsbm1m_$(translate_summary_name($SUMMARY_TYPE)).nt $SUMMARY_TYPE true
done
