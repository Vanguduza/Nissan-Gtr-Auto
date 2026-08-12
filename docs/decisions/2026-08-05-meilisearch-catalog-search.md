# Decision: Meilisearch catalog search (promoted from interim PG FTS)

- **Date:** 2026-08-05
- **Status:** Accepted
- **Lane:** `@backend_agent` (Edge proxy, migration); `@data_pipeline_agent` (sync script)
- **Supersedes in part:** [`2026-07-24-search-index-interim-pg-fts.md`](2026-07-24-search-index-interim-pg-fts.md) — FTS remains **fallback**, not removed.

## Context

Phase 7 shipped PostgreSQL FTS (`search_catalog`) as the interim index. Storefront and mobile typeahead bind to that RPC. Product now needs typo tolerance, facet hints (category / model / PNC), and headroom at scale without replacing Supabase as system of record.

Meilisearch CE is already scaffolded in `docker-compose.satellites.yml`.

## Decision

1. **Supabase remains SoR** — catalog tables unchanged; Meili is a **derived discovery index**.
2. **Meili never invents qty** (Stock/WMS §8) — documents and search hits carry catalog identity + facets only. Saleable / on-hand qty comes from Postgres (`stock_levels` / saleable RPCs). Client `stripInventedAvailabilityFromResults` removes any leaked inventory keys; Edge `mapHit` does not project qty fields.
3. **Sync** — `python -m data_pipeline.meili_sync --full` (batch; run after import or on schedule). Metadata in `catalog_meili_sync_state` (RLS).
4. **Search proxy** — Edge Function `catalog-search-meili`:
   - Caller JWT required (same audience as `search_catalog`).
   - `MEILI_HOST` + **search-only** `MEILI_SEARCH_KEY` in Edge secrets (master key dev-only fallback).
   - Returns `SearchCatalogResponse`-compatible JSON + optional `facetDistribution`.
   - **Dual-read:** `CATALOG_SEARCH_BACKEND=fts` or Meili error → `search_catalog` RPC (SECURITY INVOKER / RLS).
5. **Clients** — prefer `searchCatalog()` / `searchCatalogMeili()` from `@gtr/supabase-client`; direct Meili from browser/mobile **forbidden**. Storefront/POS must join stock SoR for availability.

## Consequences

### Positive

- Typo-tolerant OEM / model search; facet distribution for typeahead chips.
- No admin key in clients; JWT-scoped Edge proxy.
- FTS fallback avoids hard dependency on Meili uptime during rollout.

### Negative / follow-on

- Dual index ops: run sync after catalog imports.
- Meili hits omit nested `fitments` on vehicle/PNC rows (typeahead scope); full fitment drill-down stays PDP / FTS if needed.
- `@web_agent` / mobile UI may switch from raw RPC to Edge invoke when product enables Meili path.

## Artifacts

| Artifact | Path |
|----------|------|
| Sync script | `data-pipeline/data_pipeline/meili_sync.py` |
| Edge proxy | `supabase/functions/catalog-search-meili/` |
| Typed client | `packages/supabase-client/src/catalog-search.ts` |
| Migration | `supabase/migrations/20260805190000_catalog_meili_sync_state.sql` |
| Ops | `infra/satellites/README.md` |

## Revisit when

- Incremental sync (CDC / cron) needed beyond full batch.
- Staff POS requires Meili (today: FTS RPC is sufficient for staff lane).
