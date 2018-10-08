-- number of schema triples
select count(*) from triples where p = '<http://www.w3.org/2000/01/rdf-schema#domain>' or p = '<http://www.w3.org/2000/01/rdf-schema#range>' or p = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>' or p = '<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>';

-- number of distinct data properties
select count(distinct p) from triples where p <> '<http://www.w3.org/2000/01/rdf-schema#domain>' and p <> '<http://www.w3.org/2000/01/rdf-schema#range>' and p <> '<http://www.w3.org/2000/01/rdf-schema#subClassOf>' and p <> '<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>' and p <> '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>';

-- number of distinct classes
select count(distinct n) from (select o as n from triples where p = '<http://www.w3.org/2000/01/rdf-schema#domain>' or p = '<http://www.w3.org/2000/01/rdf-schema#range>' or p = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>' or p = '<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>' union select s as n from triples where p = '<http://www.w3.org/2000/01/rdf-schema#subClassOf>') as q;
