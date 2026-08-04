---
name: parts-catalog-ingestion
description: Hybrid catalog pipeline — FAST EPC + PartSouq diagram scraping, bounding boxes, search indexing, visual canvas. Use when the task touches catalog scraping, diagram assets, bounding boxes, Meilisearch/PG FTS, or the visual parts catalog.
---

# Parts Catalog Ingestion

> **Trigger:** Load only when the task touches catalog scraping, diagram asset extraction, bounding-box parsing, search indexing, or the visual parts catalog pipeline.

**Operator guide (SoT for PartSouq crawl):** [`docs/guides/partsouq-multimake-catalog-pipeline.md`](../../../docs/guides/partsouq-multimake-catalog-pipeline.md) §2b — competitive catalog-crawl practice.

## Hybrid Data Model

1. **Nissan FAST EPC** — VIN ranges, chassis codes, PNC codes, OEM part numbers (definitive fitment).
2. **Scraped diagram assets** — exploded-view images + X/Y bounding-box coordinates (visual navigation). PartSouq is the primary live scrape source (FlareSolverr); Amayama hierarchy remains for tests/legacy.

## Competitive crawl practice (agent must follow)

Align with common single-host catalog / EPC crawls (priority frontier + politeness), not naive BFS/DFS:

| Must | Detail |
|------|--------|
| **Priority** | Blessed `queue_mode: "hybrid"` — claim PENDING `/vehicle` (L2) before L5 parts; then deep-first. Do **not** switch long runs back to pure `deep_first` (identity starvation) without explicit operator ask. |
| **Politeness** | One concurrent FlareSolverr/worker; rate limit ≥1.5s + jitter; exponential backoff. Never raise concurrency on PartSouq without a CF plan. |
| **Cache** | Preserve VISITED HTML + `scraped_data` / `vehicle_identity`. Queue mode changes only reorder PENDING claims — never wipe DBs/cache to “fix” priority. |
| **Crawl ≠ parse** | Blessed: `amayama_catalog_auto --crawl-only` + `cache_parse_worker --watch`. Parse owns the bundle; do not dual-write checkpoints that race the watcher. |
| **Multi-make VIN** | Use `config/chassis_catalogs.json` + `--brand` / `allowed_brand`. Curated prefixes when present; else brand `epc_stub`. Never invent full ISO VINs. Orchestrator must pass brand into parse. |
| **Dedup** | Prefer one `/vehicle` URL per distinct `vid` when implementing claim improvements (`ssd=` variants). |
| **Exit criteria** | PENDING=0 is insufficient. Gate on: distinct vids visited ≈ enqueued; identities present; parse caught up; CF residuals accepted/cleared; bundle VIN policy; spot-check `search_catalog` modes. |
| **Isolation** | One maker per state DB / cache / bundle (`out/makers/<slug>/` or separate paths). |

Hard exclusions: no ZIMRA / fiscalisation; no payroll tax; no HTML5/browser QR. Bridge-First does not apply to this offline pipeline.

Config SoT: `data-pipeline/config/scrape.json` (`queue_mode`, rate limits, FlareSolverr session, `chassis_catalogs_path`). Scraping etiquette: `.cursor/rules/data_pipeline.mdc`.

## Pipeline Stages

1. **Scrape** PartSouq (FlareSolverr + hybrid queue) → HTML cache + crawl `queue`
2. **Parse watch** `cache_parse_worker` → `scraped_data`, `vehicle_identity`, mid-run bundle
3. **Parse FAST EPC** when in scope — see `/nissan-fast-parser`
4. **Merge & validate** against `data-pipeline/schemas/`
5. **Import** diagrams to Supabase Storage; spatial data to `part_fitment`
6. **Index** interim PG FTS `search_catalog` (Meilisearch deferred — ADR 2026-07-24)

## 4-Way Search Paths

1. Exact part number (with supersession)
2. VIN → prefix / My Garage (needs `vin_prefix` on `vehicle_master`)
3. Model/Year/Engine cascading dropdowns
4. Category/PNC via visual diagram bounding boxes

## Interactive Canvas

- Base diagram as canvas background
- Overlay bounding boxes (absolute / SVG)
- Hover highlight; click opens add-to-cart modal

## Scraping Rules

See `.cursor/rules/data_pipeline.mdc` for retry/backoff, proxy rotation, robots.txt respect, and catalog-crawl priority/politeness defaults.
