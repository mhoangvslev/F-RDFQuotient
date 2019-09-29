#!/bin/bash

# w, s, tw, ts, 2p1fb
dataset=$1
summaries=$2" "$3" "$4" "$5" "$6

for summary_edges in $summaries; do
	query="WITH input_graph_patterns AS (
		SELECT e1.p AS p1, e2.p AS p2, 'os' AS connection_type
		FROM encoded_triples e1
		JOIN encoded_triples e2 ON e1.o = e2.s
			UNION
		SELECT e1.p AS p1, e2.p AS p2, 'oo' AS connection_type
		FROM encoded_triples e1
		JOIN encoded_triples e2 ON e1.o = e2.o
			UNION
		SELECT e1.p AS p1, e2.p AS p2, 'ss' AS connection_type
		FROM encoded_triples e1
		JOIN encoded_triples e2 ON e1.s = e2.s
	),
	summary_patterns AS (
		SELECT e1.p AS p1, e2.p AS p2, 'os' AS connection_type
		FROM $summary_edges e1
		JOIN $summary_edges e2 ON e1.o = e2.s
			UNION
		SELECT e1.p AS p1, e2.p AS p2, 'oo' AS connection_type
		FROM $summary_edges e1
		JOIN $summary_edges e2 ON e1.o = e2.o
			UNION
		SELECT e1.p AS p1, e2.p AS p2, 'ss' AS connection_type
		FROM $summary_edges e1
		JOIN $summary_edges e2 ON e1.s = e2.s
	)
	SELECT CAST(count(*) AS float) / CAST((SELECT count(*) FROM summary_patterns) AS float) AS AL_2
	FROM (
		SELECT p1, p2 FROM input_graph_patterns GROUP BY p1, p2, connection_type
			INTERSECT
		SELECT p1, p2 FROM summary_patterns GROUP BY p1, p2, connection_type
	) AS q;"
	echo $query
	psql -U postgres -d $dataset -c $query | tee -a $dataset"AL_2".log
done