# Decision: Interim PostgreSQL FTS for catalog search

- **Date:** 2026-07-24
- **Status:** Accepted
- **Lane:** `@data_pipeline_agent` (pipeline); `@backend_agent` (FTS migration/RPC)
- **Related:** [`2026-07-24-phase7-data-pipeline-search.md`](../plans/2026-07-24-phase7-data-pipeline-search.md)

## Context

Phase 6 storefront ships a 4-way search UI (`part | vin | model | pnc`) against stub routes. Phase 7 must provide validated catalog data and a search backend so the storefront can bind live results without blocking on new infrastructure.

The long-term target in `/parts-catalog-ingestion` is Meilisearch for typo tolerance, facets, and ranking at scale.

## Decision

Ship **PostgreSQL full-text search (FTS)** as the interim catalog index for Phase 7 first slice:

1. Generated `tsvector` columns + GIN indexes on `vehicle_master`, `pnc_categories`, and `part_fitment`.
2. RPC `search_catalog(p_mode text, p_query text)` — `SECURITY INVOKER`, granted to `authenticated` (matches existing catalog RLS).
3. `oe_cross_refs` table for aftermarket/legacy OE numbers in part search.
4. `catalog-diagrams` Storage bucket for diagram assets (public read; staff write).

**Meilisearch is explicitly deferred** until catalog volume, typo tolerance, or facet ranking justify the ops cost of a dual-write/sync job.

## Consequences

### Positive

- Zero new search infra; unblocks Phase 6 search unstub quickly.
- Search runs with caller RLS — no service-role bypass for storefront queries.
- Pipeline remains independent; import is batch/offline via service role.

### Negative / follow-on

- Weaker fuzzy matching and facet UX vs Meilisearch.
- Ranking is basic (`plainto_tsquery` + `LIKE` fallbacks).
- Future Meili adoption needs a sync job from Supabase tables (or pipeline dual-write).

## Migration

`supabase/migrations/20260724010000_catalog_search_fts.sql`

## Revisit when

- Catalog row count or query latency degrades FTS performance.
- Storefront needs typo-tolerant OEM search or rich brand/category facets.
- Diagram canvas browse requires ranked multi-field faceting beyond PG capabilities.
