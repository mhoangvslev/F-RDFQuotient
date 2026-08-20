# Adding "authority" as a third equivalence component (federation support)

## Goal

Extend node equivalence so that, in addition to the existing criteria (type,
value/shape), two nodes can only be merged into the same summary node if they
also agree on **authority** — a value extracted from the node's URI, meant to
identify which data source/federation member the node came from.

## Authority extraction

- Regex: `^(https?://.*)/[^/]*$`, group 1.
- Greedy `.*`, confirmed intentional: this yields **path-level** granularity
  (everything up to the entity's last path segment), not just scheme+host.
  E.g. `https://example.org/dataset1/e123` → authority
  `https://example.org/dataset1`. Two entities under the same host but
  different path prefixes are treated as different authorities.
- Nodes with no match (blank nodes, non-HTTP(S) URIs) fall back to a shared
  sentinel `AUTHORITY_NONE` so they keep merging with each other as today,
  just as their own partition, separate from any real authority.
- **Literals inherit their subject's authority** — resolved. A literal has
  no URI to regex against, so there's nothing to compute standalone; use
  `authorityOf(s)` from whichever triple `(s, p, o)` introduced it.

  This has a real structural consequence, not just a lookup-source change:
  `RDF2SQLEncoding` dictionary-encodes literal *values*, not occurrences —
  the string `"1932"` has exactly one encoded ID shared by every triple in
  the graph using it, regardless of subject. So "inherit the subject's
  authority" means the *same* raw literal ID can now legitimately need
  **multiple different summary-node representations**, one per authority it
  was reached through — which conflicts with the existing `rd`/`dr`/`rep`
  machinery (`Summary.java`, mirrored as `rd`/`dr` in the paper's Section
  6.1), which assumes a data node's final summary representative is a pure
  function of its own ID. True for URIs/blank nodes (authority is intrinsic
  to them), not true for literals (authority is a property of the edge you
  reached them through, not of the literal itself).

  **Fix:** don't cache "the authority of literal X." Instead, at the point a
  triple `(s, p, o)` is classified and `o` is a literal, mint a synthetic
  *occurrence ID* — a fresh long, cached by the pair
  `(rawLiteralId, authorityOf(s))`, minted the first time that combination
  is seen (same style of cache as the authority-string minting below). Use
  that synthetic ID as the node's working identity everywhere downstream
  (`n2ip`/`n2op`, `rd`/`dr`, cliques, `cs2csID`, etc.) — completely
  unchanged otherwise. Only the triple-classification front door needs the
  extra "if object is a literal, remap through `(literalId, authorityOf(s))`
  first" step. At decode/export time, reverse-map the synthetic ID back to
  the raw literal ID to recover its text via `dictionaryDecode`.

  Subjects and URI/blank-node objects need none of this — their authority
  stays intrinsic and is computed once per ID as in the rest of this plan.

## Rollout scope

All 12 algorithm variants listed in README.md, at once (not just the typed
ones).

## Architecture

Every variant's equivalence key already reduces to "one or more `long`
signature components → summary node ID," via different structures:

| Variant family | Current key structure | Location |
|---|---|---|
| Type-based (`TypedSummary`) | `HashMap<TreeSet<Long>, Long>` (`cs2csID`) | `Summary.java:44-45` |
| Value/shape-based (`OneBisimSummary`, `InputOutputAndTypedSummary`) | `HashMap<TreeSet<Long>, HashMap<TreeSet<Long>, Long>>` (`ip2op2sn`) | `OneBisimSummary.java:21` |
| Strong bisimulation (all `strong/*`) | Custom `TwoLevelLongMap` keyed by (sourceCliqueID, targetCliqueID) (`untypedSummaryNodes`) | `StrongOrTypedStrongSummary.java:33` |
| Weak bisimulation (all `weak/*`) | `HashMap<Long,Long>` per-property source/target (`ps`/`pt`) feeding an analogous lookup | `WeakOrTypedWeakSummary.java:20-21` |

Design principle: authority is an **outer partition**, not folded into
existing sets/keys. Wrap each existing map with one more outer
`HashMap<Long /*authorityID*/, ExistingStructure>` level — the existing
matching logic inside each partition is untouched.

## Steps

1. **Authority extraction/caching, once, in the shared base
   (`Summary.java`).** New method `getOrComputeAuthorityId(long nodeId)`,
   backed by its own cache (`HashMap<Long,Long> n2authority`):
   - `RDF2SQLEncoding.dictionaryDecode(nodeId)` → URI string
   - apply the regex above
   - register the extracted authority string as a new dictionary entry via
     `RDF2SQLEncoding.addNewEntryToDictionary(...)`
     (`util/RDF2SQLEncoding.java:221`) so it gets a real, collision-free
     `long` code, persisted in the same Postgres `dictionary` table the rest
     of the pipeline already uses (see DB notes below) — no new storage
     needed.
   - no match / non-URI node → `AUTHORITY_NONE` sentinel.

2. **Wrap `cs2csID` in `Summary.java`** with the authority level; every
   read/write site (`cs2csID.get(cs)` / `.put(cs, id)`) becomes
   `cs2csID.computeIfAbsent(authorityId, k -> new HashMap<>())...`.

3. **Wrap `ip2op2sn` in `OneBisimSummary.java`** the same way (one more
   nesting level).

4. **Extend `TwoLevelLongMap`** (or wrap it) to a 3-key lookup for
   `untypedSummaryNodes` in `StrongOrTypedStrongSummary.java`, and the
   analogous structure in `WeakOrTypedWeakSummary.java`.

5. **Audit remaining bases** (`ForwardBackwardBisimulationSummary`,
   `TwoPassSourceCliqueSummary`, `TwoPassWeakSummaryWithUnionFind`) for their
   own signature maps and apply the same wrapper.

6. **Tests:** a fixture with entities that are structurally identical (same
   type/properties) but under ≥2 different authorities — assert they no
   longer merge; plus a same-authority regression case asserting existing
   merges are unaffected.

## DB notes

- Standard Postgres, not embedded. Defaults in
  `controller/LoadingProperties.java:35-58`: host `localhost:5432`, user/pass
  `postgres`/`postgres`, dictionary table name `dictionary`.
- `RDF2SQLEncoding.addNewEntryToDictionary` inserts into that same
  `dictionary` table — authority codes reuse the existing connection/table,
  nothing new to stand up.
- Confirmed acceptable to require a live DB connection for this (no
  in-memory-only path needed).

## Formal impact (checked against Čebirić, Goasdoué, Manolescu — BDA 2016)

Authority = intersecting the existing equivalence relation `≡` with a new
one, `≡A` (same authority). Intersection of equivalence relations is still
an equivalence relation, so the summary stays a well-defined quotient graph.

- **Representativeness (Prop. 1): preserved.** The proof is generic to any
  equivalence relation used to build the quotient — doesn't depend on which
  one is chosen.
- **Compactness: knowingly weakened — accepted.** Refining `≡` can only
  split classes, never merge further, so `|H'G| ≥ |HG|`. Concretely breaks
  the weak summary's tight bound in **Prop. 4 (Unique Data Properties)**:
  `dpSrc`/`dpTarg` are keyed by property alone today (`Algorithm 2`, one
  node per property globally); with authority the key becomes
  `(property, authority)`, so a property can now appear once per authority
  that uses it. Bound becomes `|WG|n ≤ 2·|{(p, authority) pairs}|`. This is
  the intended effect (keep sources apart) — tradeoff accepted.
- **Fixpoint (Prop. 2/6/9) and Accuracy (Prop. 3): fixed by construction.**
  Fixpoint requires `H(HG) = HG`. Naively minting fresh summary-node URIs
  under a fixed generic prefix (`http://rq.org/<id>`) breaks this, since
  re-extracting authority from that URI collapses every summary node to one
  authority (`http://rq.org`), merging what re-summarization should keep
  apart.
  **Fix:** mint summary-node URIs as `<authority>/<local-id>` instead of
  `<fixed-prefix>/<local-id>` — i.e., the authority URI itself becomes the
  prefix. Re-applying the same regex to a minted URI recovers exactly the
  authority its members had, so fixpoint holds. Nodes with no real authority
  (the `AUTHORITY_NONE` sentinel) fall back to the tool's own reserved
  prefix (`http://rq.org/`), which is self-consistent under the regex too
  (always resolves back to `http://rq.org`) and forms its own stable
  partition, assuming no real dataset uses that host.
- **Weak/strong completeness (Prop. 5/8): plausible, needs a fresh proof.**
  Authority is orthogonal to RDFS-entailment-driven clique saturation
  (Lemma 1) — saturation doesn't change which authority a resource belongs
  to — so the argument likely survives, but it should be re-derived
  explicitly rather than assumed inherited, especially once the URI-minting
  change above is in place.

## Parallelization / incremental computation (not covered by the paper)

- **Incremental algorithms generalize cleanly.** `GETSOURCE`/`GETTARGET`/
  `MERGEDATANODES` (Algorithm 1-3) only need `dpSrc`/`dpTarg` (and the
  analogous clique/type maps) keyed by `(x, authority)` instead of `x` alone
  — same single-pass, merge-as-you-go structure, no algorithmic redesign.
  Matches the map-wrapping approach above.
- **Parallelization: a net positive.** Since nodes with different
  authorities can never merge under any criterion once they disagree on
  authority, the whole computation is shardable by authority — process each
  source independently (even on separate machines) and concatenate results,
  since summary nodes from different authorities are guaranteed disjoint by
  construction. Natural fit for the paper's own stated future work
  (Section 9: "leveraging a massively parallel platform such as Spark") and
  for a real federated deployment where each authority may already be a
  separate endpoint.
- **Caveat:** the `AUTHORITY_NONE` bucket (blank nodes, literals-only nodes,
  non-matching URIs) can't be sharded this way — stays a central partition,
  potential bottleneck if large.

## Output: N-Quads for GRAPH-clause querying

Goal: downstream SPARQL against the exported summary should support
`GRAPH <authority> { ... }` / `GRAPH ?g { ... }`.

This falls out of the fixpoint fix almost for free — the authority URI
doubles as the named-graph identifier, no separate bookkeeping needed:

- Every summary node has exactly one authority by construction, so every
  summary edge's subject has one unambiguous authority.
- Rule: a summary triple's graph term = the authority of its **subject**
  (standard "one named graph per source" convention).
- `writeDecodedSummaryToNTFile` (`SummaryExport.java:206`, write loop at
  lines 230-296) currently emits
  `subject + " " + property + " " + object + " .\n"`. N-Quads variant adds
  a 4th term: `... + " " + graphTerm + " .\n"`, where `graphTerm` is looked
  up the same way as the subject's own URI authority.
- Schema triples (`SH`, unchanged from `G`) default to the **unnamed default
  graph** — they're carried-over schema, not per-source facts.
- **Statistics/support triples: no default-graph exception — same
  subject-authority rule as everything else, resolved.** A summary node can
  only ever represent original entities from one authority (that's the
  point of the refinement), so its support count is already inherently
  single-authority; there's nothing to aggregate away. Applying the same
  "graph = subject's authority" rule to statistics triples achieves this for
  free:
  - **Node statistics** (`<summaryNode> <...support> "N"`,
    `SummaryExport.java:122-157`): subject is the summary node, whose IRI is
    already authority-embedded — no special case needed.
  - **Edge statistics** (`SummaryExport.java:159-180`, reified via
    `reifEdgeURI`/subject/property/object/support at lines 319-329):
    `reifEdgeURI` is currently minted under a flat global prefix
    (`getSummaryNodeURI(reified_summary_edge_URI_prefix, reifiedEdgeNumber)`).
    **Change:** mint it under the *edge's subject's* authority instead
    (`<authority-of-ts.s>/reifiedEdge_N>`), same scheme as summary-node URIs,
    so its own reification triples land in the same graph as the edge they
    describe via the same uniform rule.
  - **Any future coarser rollup stat** (e.g. "total entities served by
    property P across the whole summary") must be computed as a
    `GROUP BY authority`, never a flattened cross-source total — otherwise
    it mixes sources in a way a federation consumer can't pull back apart.
    No such rollup exists in the code today (only per-node/per-edge counts),
    but the constraint applies to any that get added later.
- Add as a **new** export path (`summary.export_to_nq_file` /
  `summary.nq_file_prefix`) alongside the existing `.nt` export, rather than
  replacing it — existing consumers of the plain N-Triples file shouldn't
  break.
- No new dependency needed: RDFQuotient has no RDF library (confirmed no
  Jena/RDF4J in `pom.xml`) — it hand-writes triples as text today, and
  N-Quads is the same thing with one more term per line.

## Open questions

- Anything in `demo/conf/` / `demo/commands.txt` that pins a different DB
  config than the defaults above? (not yet checked)
