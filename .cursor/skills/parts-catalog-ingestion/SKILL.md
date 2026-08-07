---
name: parts-catalog-ingestion
description: Hybrid catalog pipeline — FAST EPC + PartSouq diagram scraping, bounding boxes, ACES/PCdb taxonomy, search indexing, visual canvas. Use when the task touches catalog scraping, diagram assets, bounding boxes, Meilisearch/PG FTS, fitment facets, or the visual parts catalog.
---

# Parts Catalog Ingestion

> **Trigger:** Load only when the task touches catalog scraping, diagram asset extraction, bounding-box parsing, search indexing, fitment taxonomy (ACES/PCdb), or the visual parts catalog pipeline.

**Operator guide (SoT):** [`docs/guides/partsouq-multimake-catalog-pipeline.md`](../../../docs/guides/partsouq-multimake-catalog-pipeline.md) — §2b crawl, **§2c publish gates**, **§10 ACES/PCdb**, **§11 multi-vehicle agent prompt**.

**Cloud multi-make:** [`docs/guides/cloud-multi-make-catalog.md`](../../../docs/guides/cloud-multi-make-catalog.md)

## Hybrid Data Model

1. **Nissan FAST EPC** — VIN ranges, chassis codes, PNC codes, OEM part numbers (definitive fitment).
2. **Scraped diagram assets** — exploded-view images + X/Y bounding-box coordinates (visual navigation). PartSouq is the primary live scrape source (FlareSolverr); Amayama hierarchy remains for tests/legacy.
3. **Auto Care ACES/PIES + VCdb/PCdb** (follow-on / licensed) — cross-brand part terminology (`pcdb_part_type_id`), validated application fitment for feeds. EPC assembly names remain authoritative for shop-by-diagram UX.

## Multi-vehicle agent rules (must follow)

Align with dealer EPC + PIM validate-then-publish — not “import everything, filter in UI.”

| Must | Detail |
|------|--------|
| **Priority** | Blessed `queue_mode: "hybrid"` — claim PENDING `/vehicle` (L2) before L5 parts; then deep-first. Do **not** switch long runs back to pure `deep_first` without explicit operator ask. |
| **Politeness** | One concurrent FlareSolverr/worker; rate limit ≥1.5s + jitter; exponential backoff. Never raise concurrency on PartSouq without a CF plan. |
| **Cache** | Preserve VISITED HTML + `scraped_data` / `vehicle_identity`. Queue mode changes only reorder PENDING claims — never wipe DBs/cache to “fix” priority. |
| **Crawl ≠ parse** | Blessed: `amayama_catalog_auto --crawl-only` + `cache_parse_worker --watch` (`--batch-size 75`, `--poll-seconds 10`, `--write-bundle-every 17`). Parse owns the bundle; do not dual-write checkpoints that race the watcher. |
| **Multi-make VIN** | Use `config/chassis_catalogs.json` + `--brand` / `allowed_brand`. Curated prefixes when present; else brand `epc_stub`. Never invent full ISO VINs. Orchestrator must pass brand into parse. |
| **Isolation** | One maker per state DB / cache / bundle (`out/makers/<slug>/` or separate paths). `--parallel-makers 1` for cloud (one maker until complete). |
| **Publish gate** | Production **`--live-import --complete-only --prune-stale`**. Fitment = chassis + bbox + `diagram_path`. Drop identity-only `vehicle_master` from live. |
| **Categories** | `normalize_epc_category_name()` at parse/transform; `uncategorized_pncs = 0` in import scope. Part description → `oem_display_names`, not `category_name`. |
| **Hotspots** | Every live fitment has normalized 0–1 bbox + diagram in Storage. Re-queue parse for parts pages missing bbox. |
| **PCdb (optional)** | Set `pcdb_part_type_id` on `pnc_categories` when mapping table exists; never replace EPC group names on diagram pages. |
| **Test order** | Fixtures → bounded PartSouq → quality report → dry-run → live complete-only → storefront QA. |
| **Provenance** | PartSouq GIFs = `scraped-reference` (watermarks). Production art: licensed FAST, fixtures, customer-supplied. |
| **Dedup** | Prefer one `/vehicle` URL per distinct `vid` when implementing claim improvements (`ssd=` variants). |
| **Exit criteria** | PENDING=0 is insufficient. Gate on §2c checklist: identities, parse caught up, complete fitments, categories, diagram upload, `search_catalog` spot-check. |

Hard exclusions: no ZIMRA / fiscalisation; no payroll tax; no HTML5/browser QR. Bridge-First does not apply to this offline pipeline.

Config SoT: `data-pipeline/config/scrape.json` (`queue_mode`, rate limits, FlareSolverr session, `chassis_catalogs_path`). Scraping etiquette: `.cursor/rules/data_pipeline.mdc`.

## Pipeline Stages (PIM-style)

```text
Scrape (hybrid + per-vid completion)
  → Parse watch (bbox + EPC category + identity; re-queue incomplete)
  → Transform (normalize categories; filter_complete_bundle for storefront)
  → bundle_quality_report / parse_bundle_meta.json  ← HARD GATE
  → validate JSON schema
  → upload diagrams (complete chassis only)
  → import_catalog --live --complete-only [--prune-stale]
  → meili_sync / facet cache (optional)
  → Storefront + mobile (shared catalog-facets helpers)
```

1. **Scrape** PartSouq (FlareSolverr + hybrid queue) → HTML cache + crawl `queue`
2. **Parse watch** `cache_parse_worker` → `scraped_data`, `vehicle_identity`, mid-run bundle
3. **Parse FAST EPC** when in scope — see `/nissan-fast-parser`
4. **Filter complete** — `data_pipeline.bundle_filter.filter_complete_bundle`
5. **Merge & validate** against `data-pipeline/schemas/` (incl. optional `pcdb_part_type_id`)
6. **Import** diagrams to Supabase Storage; spatial data to `part_fitment`
7. **Index** interim PG FTS `search_catalog` (Meilisearch deferred — ADR 2026-07-24)

## 4-Way Search + Facets

1. Exact part number (with supersession)
2. VIN → prefix / My Garage (needs `vin_prefix` on `vehicle_master`)
3. Model / Year / Engine cascading dropdowns (ACES VCdb-aligned keys follow-on)
4. Category / PNC via visual diagram bounding boxes — **EPC `category_name`** + optional **PCdb part type** facet

## Interactive Canvas

- Base diagram as canvas background
- Overlay bounding boxes (absolute / SVG) — normalized 0–1 coords on `part_fitment`
- Hover highlight; click opens add-to-cart modal
- Mobile: native ZStack/Box overlays (Bridge-First does not block touch hotspots)

## Key commands

```bash
# Orchestrator (one maker, scrape + parse)
python -m data_pipeline.partsouq_catalog_orchestrator --makers Nissan --priority-chassis

# Quality-filtered live import
python -m data_pipeline.import_catalog out/makers/nissan/bundle --live --complete-only
python scripts/republish_erp_catalog.py --skip-refresh --live-import --complete-only

# Chassis coverage
python -m data_pipeline.amayama_catalog_auto --chassis-coverage \
  --state-db out/makers/nissan/crawler_state.db \
  --parse-db out/makers/nissan/cache_parse_state.db \
  --out-dir out/makers/nissan/bundle \
  --priority-chassis-file config/priority_chassis.json
```

## Scraping Rules

See `.cursor/rules/data_pipeline.mdc` for retry/backoff, proxy rotation, robots.txt respect, catalog-crawl priority/politeness defaults, and `--complete-only` import mandate.
