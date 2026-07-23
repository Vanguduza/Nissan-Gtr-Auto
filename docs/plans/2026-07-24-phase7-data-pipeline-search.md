# Phase 7 — Data pipeline + search index

- Status: **in progress** (first slice scaffold landed)
- Lane(s): `@data_pipeline_agent` (primary); thin `@backend_agent` only for schema/Storage/search API gaps
- Skills needed: `/nissan-fast-parser`, `/parts-catalog-ingestion`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 7 (L246–261)
- Domain decision: [`docs/decisions/2026-07-23-autodoc-shop-features.md`](../decisions/2026-07-23-autodoc-shop-features.md) (Phase 7 shop slice)
- Schema source: `supabase/migrations/20260723100300_vehicle_catalog.sql` (`vehicle_master`, `pnc_categories`, `part_fitment`)

## Goal

Greenfield `data-pipeline/`: parse/validate FAST (+ fixture diagrams) → idempotent import into existing catalog tables + Storage → expose 4-way search (part / VIN / model / PNC) so Phase 6 storefront can leave stubs.

## Acceptance criteria

- [ ] Python project under `data-pipeline/` runs independently (no client-app build dependency)
- [ ] JSON Schema–validated outputs for `vehicle_master`, `pnc_categories`, `part_fitment`, `diagram_assets`
- [ ] Idempotent import into Supabase tables (+ diagram objects to Storage)
- [ ] Search supports part / VIN / model / PNC paths (contracts match Phase 6 `/search` modes)
- [ ] Shop Phase 7 slice data available: fitment browse payloads, VIN + make/model/engine, OEM + supersession/OE refs, PDP fitment/specs-shaped rows, brand/category (PNC) facets
- [ ] pytest covers schema validate + import idempotency + search path smoke
- [ ] `/verifier` green; no ZIMRA / payroll tax / HTML5 QR

## Search decision (recommend)

**Use PostgreSQL FTS (interim) for this slice — not Meilisearch yet.**

| Option | Pros | Cons |
|--------|------|------|
| **PG FTS + SQL RPCs** (chosen) | Zero new infra; tables already in Supabase; fast path to unstub Phase 6; natural join to fitment | Weaker typo/ranking/facet UX at scale |
| Meilisearch now | Matches long-term skill target; strong facets | Ops + sync job + deploy before any search ships |

**ADR to file during implement:** `docs/decisions/2026-07-24-search-index-interim-pg-fts.md`  
(Document interim FTS; Meilisearch as follow-on when volume/typo/facet ranking justify it. Do not block Phase 7 on Meili.)

## Target tables / import contract

Existing (no re-derive from blueprints):

| Table | Key fields | Pipeline role |
|-------|------------|---------------|
| `vehicle_master` | `vin_prefix`, `chassis_code`, `engine_code`, `production_year`, `model_variant` | VIN + model/engine cascade |
| `pnc_categories` | `pnc_code`, `category_name`, `subcategory_name` | Category / PNC facets |
| `part_fitment` | `oem_part_number`, `pnc_code`, `chassis_code`, `engine_code`, `superseded_by`, bbox_*, `diagram_path` | Fitment ledger + canvas bbox |

Schemas (new under `data-pipeline/schemas/`): `vehicle_master`, `pnc_categories`, `part_fitment`, `diagram_assets` — mirror columns above; validate before import.

**Idempotency keys (suggested):** chassis+vin_prefix+engine+year+variant; `pnc_code`; OEM+chassis+engine+pnc (+ diagram_path when present). Prefer upsert / natural keys over blind insert.

**Storage:** `catalog-diagrams` bucket; `part_fitment.diagram_path` = object path after upload.

**OE cross-refs:** first slice = OEM search + `superseded_by` chain. If aftermarket OE numbers are required for PDP OE tab, thin migration `oe_cross_refs` (oem ↔ oe_number, brand) via `@backend_agent`.

## `@backend_agent` in-slice?

**Yes, thin — only if gaps block import/search:**

1. Storage bucket `catalog-diagrams` (+ RLS/policies) if missing
2. FTS indexes / generated `tsvector` + RPC(s) for 4-way search (anon/authenticated read per existing catalog RLS)
3. Optional `oe_cross_refs` (+ RLS) for non-OEM OE numbers
4. Regenerate `packages/supabase-client` types after migrations

**Not in-slice:** storefront UI rewrites (`@web_agent`), live unbounded scrapers, Meilisearch deploy.

## Paths in scope

- `data-pipeline/` — pyproject, schemas, parse, validate, import, search fixtures, pytest
- `supabase/migrations/` — only thin search/Storage/OE as above
- `packages/supabase-client/` — types regen if migrations land
- `docs/decisions/2026-07-24-search-index-interim-pg-fts.md` — ADR during implement

## Out of scope (first slice)

- Production scrapers without rate limits / robots respect (fixtures + offline FAST samples OK)
- Meilisearch cluster / dual-write
- Phase 6 UI polish or live stock/pricing binding (Phase 5 already owns stock)
- Interactive canvas beyond importing bbox + `diagram_path` (consume later)
- UK plate, marketplace, pan-EU branding, DIY Club
- ZIMRA / fiscal QR; payroll tax; HTML5/browser QR
- Client apps depending on pipeline at build time

## Risks / exclusions

- **Bridge-First:** no browser QR in pipeline or search API
- **RLS:** import uses service role offline; public search must respect existing catalog SELECT policies
- **Independence:** pipeline is batch → Supabase; never import from `apps/web` at build time
- **Exclusions:** ZIMRA, payroll tax, HTML5 QR, UK plate lookup, marketplace

## Ordered implementation tasks

1. **Scaffold** `data-pipeline/` (Python ≥3.11, pytest, jsonschema, supabase client) + README run instructions  
2. **JSON Schemas** matching `20260723100300_vehicle_catalog.sql` + `diagram_assets`  
3. **FAST parse stubs** → schema-valid JSON (fixtures first; `/nissan-fast-parser` hierarchy)  
4. **Validate CLI** — fail closed on schema errors  
5. **Import job** — idempotent upsert into three tables; upload diagrams → set `diagram_path`  
6. **`@backend_agent` (thin):** Storage bucket; PG FTS + search RPC(s); optional `oe_cross_refs`; types  
7. **Wire search contracts** — part / VIN / model / PNC (document response shape for Phase 6 unstub)  
8. **pytest** — validate, double-import idempotency, four search path smokes  
9. **ADR** interim PG FTS; note Meili follow-on  
10. **`/security-reviewer`** (if migrations/RPCs) → **`/verifier`** → **`/manager`** done gate  

## Test plan

| Gate | What |
|------|------|
| pytest | Schema validate; import twice → same row counts / keys; search fixture hits for part, VIN prefix, model/engine, PNC |
| Manual | `supabase db reset` (if migrations); import sample pack; call search RPC |
| `/verifier` | Exclusions (ZIMRA, payroll tax, HTML5 QR); lane paths (`data-pipeline/**` + thin supabase); no client build coupling |
| `/supabase-rls-auditor` | Only if new tables/policies |

## Handoff

1. Implement in `@data_pipeline_agent` (pull thin `@backend_agent` for step 6 only)  
2. `/security-reviewer` if auth/schema/Storage/RPC  
3. `/verifier`  
4. `/manager` for done gate → Phase 6 can bind live search; Meili deferred  
