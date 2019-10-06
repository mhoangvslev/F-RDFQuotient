-- number of distinct nodes
select count(distinct n) from (
    select s as n from encoded_triples
    union
    select o as n from encoded_triples
) as q;

-- number of schema triples
select count(*) from triples where p = '<http://www.w3.org/2000/01/rdf-schema#domain>' or p = '<http://www.w3.org/2000/01/rdf-schema#range>' or p = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>' or p = '<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>';

-- number of distinct data properties
select count(distinct p) from triples where p <> '<http://www.w3.org/2000/01/rdf-schema#domain>' and p <> '<http://www.w3.org/2000/01/rdf-schema#range>' and p <> '<http://www.w3.org/2000/01/rdf-schema#subClassOf>' and p <> '<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>' and p <> '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>';

-- number of distinct classes
select count(distinct n) from (select o as n from triples where p = '<http://www.w3.org/2000/01/rdf-schema#domain>' or p = '<http://www.w3.org/2000/01/rdf-schema#range>' or p = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>' or p = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' union select s as n from triples where p = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>') as q;

-- ratio of untyped nodes wrt. all nodes: good version
with distinct_typed_nodes as (select distinct s as n from triples where triples.p = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>'), distinct_nodes as (select distinct s as n from triples	union select distinct o as n from triples) select 1 - cast((select count(*) from distinct_typed_nodes) as float) / cast((select count(*) from distinct_nodes) as float) as result;

-- ratio of untyped nodes wrt. all nodes: good version (pretty)
with distinct_typed_nodes as (
	select distinct s as n
	from triples
	where triples.p = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>'
),
distinct_nodes as (
	select distinct s as n
	from triples
	union
	select distinct o as n
	from triples
)
select 1 -
	cast(
		(select count(*)
		from distinct_typed_nodes)
	as float)
	/ cast(
		(select count(*)
		from distinct_nodes)
	as float)
as result;

-- ratio of untyped nodes wrt. all nodes: less efficient version
select (cast((with typed as (select distinct s as n from triples where triples.p = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>') select count(*) from (select distinct n from (select s as n from typed right outer join triples on typed.n = triples.s where typed.n is null union select o as n from typed right outer join triples on typed.n = triples.o where typed.n is null) as untyped) as distinct_untyped) as float) / cast((select count(*) from (select distinct n from (select s as n from triples union select o as n from triples) as subsubquery) as subquery) as float)) as result;

-- number of only data triples in the summary (pretty)
with schema_nodes as (
	select distinct s as sn
	from summary_edges
	where p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>')
		or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>')
		or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#domain>')
		or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#range>')
	union
	select distinct o as sn
	from summary_edges
	where p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>')
		or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>')
		or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#domain>')
		or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#range>')
		or p = (select key from dictionary where value = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>')
	union
	select distinct s as sn
	from summary_edges
	where p = (select key from dictionary where value = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>')
		and (o = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#Class>')
			or o = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#Property>'))
)
select count(*)
from summary_edges
where s not in (select * from schema_nodes)
	and o not in (select * from schema_nodes);

-- number of only data triples in the summary
with schema_nodes as (select distinct s as sn from summary_edges where p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>') or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>') or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#domain>') or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#range>') union select distinct o as sn from summary_edges where p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>') or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>') or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#domain>') or p = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#range>') or p = (select key from dictionary where value = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>') union select distinct s as sn from summary_edges where p = (select key from dictionary where value = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>') and (o = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#Class>') or o = (select key from dictionary where value = '<http://www.w3.org/2000/01/rdf-schema#Property>'))) select count(*) from summary_edges where s not in (select * from schema_nodes) and o not in (select * from schema_nodes);

-- number of summary edges after inlining
SELECT count(*) FROM (
	SELECT DISTINCT e1.s, e1.p, e1.o
	FROM summary_edges e1
	WHERE e1.o in (
		SELECT e2.s
		FROM summary_edges e2
	)
) AS q;

-- AL_2 accuracy (slow)
WITH input_graph_patterns AS (
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
	FROM summary_edges e1
	JOIN summary_edges e2 ON e1.o = e2.s
		UNION
	SELECT e1.p AS p1, e2.p AS p2, 'oo' AS connection_type
	FROM summary_edges e1
	JOIN summary_edges e2 ON e1.o = e2.o
		UNION
	SELECT e1.p AS p1, e2.p AS p2, 'ss' AS connection_type
	FROM summary_edges e1
	JOIN summary_edges e2 ON e1.s = e2.s
)
-- fix count(*): should be count distinct in the denominator
SELECT CAST(count(*) AS float) / CAST((SELECT count(*) FROM summary_patterns) AS float) AS AL_2
FROM (
	SELECT p1, p2 FROM input_graph_patterns GROUP BY p1, p2, connection_type
		INTERSECT
	SELECT p1, p2 FROM summary_patterns GROUP BY p1, p2, connection_type
) AS q;