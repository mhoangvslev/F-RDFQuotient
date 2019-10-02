CREATE INDEX encoded_saturated_triples_i_ops ON encoded_saturated_triples(o, p, s);
CREATE INDEX encoded_saturated_triples_i_osp ON encoded_saturated_triples(o, s, p);
CREATE INDEX encoded_saturated_triples_i_pos ON encoded_saturated_triples(p, o, s);
CREATE INDEX encoded_saturated_triples_i_pso ON encoded_saturated_triples(p, s, o);
CREATE INDEX encoded_saturated_triples_i_sop ON encoded_saturated_triples(s, o, p);
CREATE INDEX encoded_saturated_triples_i_spo ON encoded_saturated_triples(s, p, o);