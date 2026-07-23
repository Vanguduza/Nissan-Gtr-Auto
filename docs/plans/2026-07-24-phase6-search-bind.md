# Phase 6 — Bind storefront search to `search_catalog`

- Status: draft
- Lane(s): `@web_agent` (primary); one-line `@backend_agent` only if types lack `search_catalog`
- Skills needed: (none)
- Parent: Immediate handoff #1 / Phase 7 follow-on ([`2026-07-24-phase7-data-pipeline-search.md`](./2026-07-24-phase7-data-pipeline-search.md))
- ADR: [`docs/decisions/2026-07-24-search-index-interim-pg-fts.md`](../decisions/2026-07-24-search-index-interim-pg-fts.md)
- RPC: `search_catalog(p_mode text, p_query text)` — modes `part|vin|model|pnc`; GRANT `authenticated`

## Goal

Wire storefront `/search` (and minimal PLP/PDP link-through if results need a destination) to live `search_catalog` instead of the Phase 6 stub copy.

## Acceptance criteria

- [ ] `/search` with `mode` ∈ `part|vin|model|pnc` + `q` calls `search_catalog` (authenticated session)
- [ ] Empty query / empty results render gracefully (no crash, clear empty state)
- [ ] RPC/network errors show a non-fatal error state
- [ ] Missing Supabase env (`createWebClient()` null) keeps stub fallback only — no fake success
- [ ] Result rows link to existing PLP/PDP routes when an OEM/id is present (minimal; no redesign)
- [ ] No HTML5/browser QR; no ZIMRA; no Meili client
- [ ] `/verifier` green (security light unless secrets introduced)

## Paths in scope

- `apps/web/app/(storefront)/search/page.tsx`
- `apps/web/components/search-four-way*` (as needed)
- `apps/web/lib/` — thin search helper using existing `createWebClient`
- Optional touch: `apps/web/app/(storefront)/parts/[oem]/page.tsx`, `catalog/page.tsx` (link targets only)
- Read-only: `packages/supabase-client/`, `packages/shared/`

## Ordered tasks (`@web_agent`)

1. Confirm typed client exposes `search_catalog`; if not → ask `@backend_agent` for `pnpm db:types` / types regen (no migrations).
2. Add a small server/client helper: call RPC with `p_mode` / `p_query`; map jsonb `{ mode, query, results }`.
3. Bind `search/page.tsx` to helper; replace “no index yet” stub when client + session available.
4. Wire empty/error/env-missing states; keep `SearchFourWay` mode/query URL contract.
5. Minimal result → PDP/PLP links if OEM present.
6. Smoke four modes locally; hand off `/verifier`.

## Out of scope

- Meilisearch / dual-write
- Full live stock/pricing beyond what RPC returns
- Android / management / bridges
- Schema/migration changes; RLS edits
- Search UI redesign / `/ui-ux-pro-max`

## Risks / exclusions

- RPC is `authenticated` only — anon users need sign-in or a clear “sign in to search” state (do not widen GRANT).
- Bridge-First: no browser QR scanning.

## Handoff

1. `@web_agent` implements this plan  
2. `/verifier` (security light unless secrets)  
3. `/manager` done gate
