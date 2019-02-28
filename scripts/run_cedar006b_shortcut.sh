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
	# SATURATE + SUMMARIZE
	# summarize saturated
	./scripts/summarize.sh bsbm/bsbm138m.nt $SUMMARY_TYPE true

	# SHORTCUT
	# summarize not saturated
	#./scripts/summarize.sh bsbm/bsbm138m.nt $SUMMARY_TYPE false
	# load the summary with saturation
	./scripts/load.sh bsbm/bsbm138m_$(translate_summary_name $SUMMARY_TYPE).nt true
	# summarize saturated
	./scripts/summarize.sh bsbm/bsbm138m_$(translate_summary_name $SUMMARY_TYPE).nt $SUMMARY_TYPE true
done