# Data pipeline — Nissan GTR Auto ERP catalog

Independent Python batch pipeline: parse Nissan FAST-like exports, scrape reference catalogs (Amayama), validate against JSON Schema, and idempotently import into Supabase catalog tables.

**Operator guide (PartSouq multi-make):** [`docs/guides/partsouq-multimake-catalog-pipeline.md`](../docs/guides/partsouq-multimake-catalog-pipeline.md) — `python -m data_pipeline.partsouq_catalog_orchestrator`

**Megazip multivehicle EPC hierarchy (Maker → Model → Variant → Diagram):** [`docs/guides/megazip-multivehicle-catalog.md`](../docs/guides/megazip-multivehicle-catalog.md) — `python -m data_pipeline.megazip_catalog_orchestrator`

**Not coupled to client apps** — `apps/web` and mobile builds do not import this package at build time.

## Requirements

- Python ≥ 3.11
- `pip install -e ".[dev]"` from this directory

Optional extras:

```bash
pip install -e ".[supabase]"   # live import + diagram upload
pip install -e ".[scraping]"   # Amayama crawl (httpx + patchright)
pip install -e ".[forecast]"   # optional StatsForecast (Phase C; CI uses stub)
patchright install chromium
export SUPABASE_URL=...
export SUPABASE_SERVICE_ROLE_KEY=...
```

## Layout

| Path | Purpose |
|------|---------|
| `data_pipeline/validate.py` | JSON Schema validation (fail closed) |
| `data_pipeline/parse_fast.py` | FAST-like JSON → catalog tables |
| `data_pipeline/amayama_catalog_auto.py` | **Single automatic catalog pipeline** (PartSouq + FlareSolverr; Amayama hierarchy kept for tests) |
| `data_pipeline/partsouq_catalog_orchestrator.py` | **Multi-make orchestrator** — scrape + `cache_parse_worker` per maker under `out/makers/<slug>/` |
| `data_pipeline/cache_parse_worker.py` | Parallel HTML-cache parser (vid → chassis → vin_prefix; does not stop crawl) |
| `data_pipeline/parse_partsouq_html.py` | Parts hotspots + `/vehicle` identity HTML parsers |
| `data_pipeline/scrape_amayama.py` | Thin alias → `amayama_catalog_auto` |
| `data_pipeline/transform_amayama.py` | Re-exports from auto pipeline |
| `data_pipeline/scrape_etiquette.py` | Re-exports from auto pipeline |
| `data_pipeline/hierarchy.py` | Re-exports from auto pipeline |
| `data_pipeline/vin_decode.py` | Re-exports from auto pipeline |
| `data_pipeline/import_catalog.py` | Idempotent upsert (in-memory or Supabase) |
| `data_pipeline/search_index.py` | Offline search contract smoke tests |
| `data_pipeline/forecast_statsforecast.py` | Phase C AI forecast scaffold (stub → StatsForecast optional) |
| `config/scrape.json` | Scrape rate limits, start URL, proxies |
| `config/partsouq_makers.json` | Curated PartSouq brand list for the orchestrator |
| `schemas/` | JSON Schemas mirroring DB columns |
| `fixtures/navara_d40_yd25/` | Navara D40 / YD25 sample pack (storefront demo OEMs) |
| `fixtures/aces_pies/` | Synthetic ACES/PIES XML for enrichment importer tests |
| `data_pipeline/aces_pies/` | Thin PIES/ACES XML → catalog enrichment (not SandPIM SoR) |
| `tests/` | pytest suite |

## Idempotency keys

| Table | Natural key |
|-------|-------------|
| `vehicle_master` | `vin_prefix` + `chassis_code` + `engine_code` + `production_year` + `model_variant` |
| `pnc_categories` | `pnc_code` |
| `part_fitment` | `oem_part_number` + `chassis_code` + `engine_code` + `pnc_code` |
| `oe_cross_refs` | `oem_part_number` + `oe_number` + `brand` |

Matching unique indexes are in migration `20260724010000_catalog_search_fts.sql`.

## Commands

```bash
cd data-pipeline

# Install
pip install -e ".[dev]"

# Multi-make PartSouq orchestrator (scrape + parse watcher per maker)
python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota
python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota,Honda,Nissan
python -m data_pipeline.partsouq_catalog_orchestrator --makers all --dry-run

# Validate fixture pack (default) or explicit paths
python -m data_pipeline.validate
python -m data_pipeline.validate fixtures/navara_d40_yd25

# Dry-run import (in-memory; no Supabase required)
python -m data_pipeline.import_catalog
python -m data_pipeline.import_catalog out/erp_catalog_v1

# Live import (privileged server key; stock_items upsert on by default)
python -m data_pipeline.import_catalog out/erp_catalog_v1 --live
# python -m data_pipeline.import_catalog out/erp_catalog_v1 --live --no-ensure-stock-items

# Meilisearch sync after live import (requires MEILI_HOST + MEILI_MASTER_KEY)
python -m data_pipeline.meili_sync --full

# ACES/PIES enrichment (sample fixtures; see section below)
python -m data_pipeline.aces_pies_import \
  --pies fixtures/aces_pies/sample_pies.xml \
  --aces fixtures/aces_pies/sample_aces.xml \
  --out out/aces_pies_enrichment

# Tests
pytest

# Phase C forecast scaffold (stub reason JSON; no auto-PO)
python -m data_pipeline.forecast_statsforecast
pytest tests/test_forecast_statsforecast.py -q
```

## ACES / PIES enrichment (SandPIM-compatible XML)

Thin adapters under `data_pipeline/aces_pies/` consume **Auto Care ACES/PIES XML** (supplier drop or optional SandPIM export) and enrich GTR tables. **Supabase + EPC remain the system of record** — do not fork SandPIM PHP into this monorepo or rebase the catalog SoR onto MySQL/LAMP.

| Signal | Target | Notes |
|--------|--------|-------|
| PIES `PartTerminologyID` | `pnc_categories.pcdb_part_type_id` | Via OEM → PNC join on `part_fitment`; curated `config/epc_to_pcdb.json` runs first and is not overwritten |
| PIES descriptions / brand | `stock_items.description` + `oem_display_names` | Existing columns only |
| PIES attributes (weight, hazmat, …) | Sidecar `pies_attrs_by_oem.json` | No `attrs` jsonb on `stock_items` yet — deferred migration |
| ACES `App` rows | Parsed to `aces_apps.json` only | **Apply stubbed** until fitment has `source=aces\|epc` (EPC bbox / `diagram_path` stay authoritative) |

**Auto Care subscription:** Real VCdb / PCdb / PAdb / Brand Table files require an [Auto Care Association membership](https://www.autocare.org/data-standards/subscriptions). Fixture XML uses **synthetic** PartTerminologyIDs and BaseVehicleIDs for CI.

```bash
cd data-pipeline
pip install -e ".[dev]"

# Dry-run against sample PIES (+ optional ACES parse stub)
python -m data_pipeline.aces_pies_import \
  --pies fixtures/aces_pies/sample_pies.xml \
  --aces fixtures/aces_pies/sample_aces.xml \
  --out out/aces_pies_enrichment

# Enrich a catalog bundle (sets pcdb_part_type_id + display names)
python -m data_pipeline.aces_pies_import \
  --pies path/to/supplier_pies.xml \
  --bundle out/erp_catalog_v1 \
  --out out/aces_pies_enrichment

# Merge new PartTerminologyIDs into the curated EPC→PCdb map
python -m data_pipeline.aces_pies_import \
  --pies path/to/supplier_pies.xml \
  --write-mapping config/epc_to_pcdb.json

# Live upsert (needs SUPABASE_URL + service role; pip install -e ".[supabase]")
python -m data_pipeline.aces_pies_import \
  --pies path/to/supplier_pies.xml \
  --bundle out/erp_catalog_v1 \
  --live
```

Guide §10: `docs/guides/partsouq-multimake-catalog-pipeline.md`. Plans: `docs/plans/2026-08-10-sandpim-catalog-enrichment.md`.

## Phase C — StatsForecast / Prophet scaffold

Writes structured `reason` JSON for `forecast_suggestions` only. Does **not** fit models in CI, replace SQL `generate_forecast_suggestions`, or open purchase orders / journals.

| Piece | Location |
|-------|----------|
| Stub module | `data_pipeline/forecast_statsforecast.py` |
| Optional extra | `.[forecast]` → `statsforecast` (Prophet deferred — license check before prod) |
| Gorse compose | commented `recommend` profile in `docker-compose.satellites.yml` |
| Docs | `infra/satellites/PHASE2_PROPHET_GORSE.md` |

## Amayama scrape → bundle → import

Respects `robots.txt`, identifies as `GTR-Auto-CatalogBot/1.0`, rate-limits with jitter (see `config/scrape.json`), caches responses, claims crawl URLs atomically via **`queue_mode`** (blessed **`hybrid`**: `/vehicle` first, then deep-first parts — see operator guide §2b), auto-retries failures, and writes mid-crawl checkpoints.

**VIN / year linking (local-first):** `data_pipeline/vin_decode.py` uses ISO 3779 year + curated Nissan chassis→`vin_prefix` map (EPC-accurate). Optional NHTSA vPIC is enrichment only — it does not return chassis codes like `D40`.

```bash
pip install -e ".[scraping,dev]"
patchright install chromium

# Full catalogue (default): drain queue + retry rounds + dry-run import
python -m data_pipeline.amayama_catalog_auto --local-ip --until-complete --out-dir out/partsouq_bundle

# Explicit bounded crawl + checkpoints
python -m data_pipeline.amayama_catalog_auto --local-ip --max-pages 25 --out-dir out/partsouq_bundle --import-dry-run

# Resume + auto-retry permanent failures
python -m data_pipeline.amayama_catalog_auto --retry-failed --out-dir out/partsouq_bundle

# Transform previously captured SQLite JSON only
python -m data_pipeline.amayama_catalog_auto --transform-only --state-db crawler_state.db --out-dir out/partsouq_bundle

# Download diagrams locally (optional Supabase Storage upload)
python -m data_pipeline.amayama_catalog_auto --transform-only --download-diagrams
python -m data_pipeline.amayama_catalog_auto --transform-only --upload-diagrams

# Live table import
python -m data_pipeline.amayama_catalog_auto --transform-only --live-import
```

**Cloudflare via FlareSolverr (PartSouq, local IP)**

Primary source is **PartSouq** Nissan genuine catalog. FlareSolverr clears Cloudflare on your host IP — **no CapSolver / 2Captcha**.

Persistent FlareSolverr **sessions** keep CF cookies warm across requests (and restarts when `flaresolverr_persist_session` is true). Rate limiting includes randomised jitter; concurrency is capped by `flaresolverr_max_concurrent` / `--max-concurrent`.

```bash
# Start FlareSolverr satellite
docker compose -f docker-compose.satellites.yml --profile scrape up -d

cd data-pipeline
python -m data_pipeline.amayama_catalog_auto --local-ip --until-complete --out-dir out/partsouq_bundle --import-dry-run

# Tune pacing / concurrency (still runs to completion unless --max-pages is set)
python -m data_pipeline.amayama_catalog_auto --local-ip --until-complete --workers 1 --max-concurrent 1 --jitter-seconds 1.0 --jitter-ratio 0.4

# Single capped pass (disables until-complete)
python -m data_pipeline.amayama_catalog_auto --local-ip --max-pages 50 --out-dir out/partsouq_bundle --import-dry-run
```

Optional residential proxy still works (`config/proxies.json` / `--proxy`) when you omit `--local-ip`. Manual headed CF (`--cf-pass`) remains a fallback if FlareSolverr cannot solve.

Disable FlareSolverr: `--no-flaresolverr` or set `"use_flaresolverr": false` in `config/scrape.json`.

### Catalogue crawl watchdog (agent wake)

When a long `--until-complete` crawl dies unexpectedly, a durable watchdog writes an alert and can safely restart transient failures (FlareSolverr down → `docker start gtr-flaresolverr`; session lost → restart crawl). It never kills a healthy crawl.

| Artifact | Path |
|----------|------|
| Alert JSON | `out/catalogue_watchdog_alert.json` |
| Watchdog module | `python -m data_pipeline.catalogue_watchdog` |
| Cursor hook | `.cursor/hooks/catalogue-watchdog-alert.ps1` (`sessionStart` injects alert context) |
| Agent sentinel | stdout line `AGENT_LOOP_WAKE_catalogue_watchdog {...}` |

```bash
cd data-pipeline

# One-shot health (exit 0 if crawl running, 2 if not)
python -m data_pipeline.catalogue_watchdog --status

# Attach to running crawl; auto-fix FlareSolverr/session/crash if safe
python -m data_pipeline.catalogue_watchdog --attach-auto --restart-transient --interval 20

# Attach to a known PID
python -m data_pipeline.catalogue_watchdog --pid 25352 --restart-transient
```

**Cursor agent involvement**

1. **Session start** — if `out/catalogue_watchdog_alert.json` exists with a non-completed status, the `sessionStart` hook injects diagnose/fix context into the agent.
2. **Live wake (recommended while crawling)** — run the watchdog in a Cursor agent terminal with `notify_on_output` / loop monitoring on `^AGENT_LOOP_WAKE_catalogue_watchdog`. On fatal stop the sentinel fires and the agent should read the alert JSON, diagnose logs, and fix/restart if safe.
3. Cursor Automations (cloud cron) are optional; this Desktop path does not require them.

Output is the same four JSON files as fixture packs. Diagram provenance for scrapes is `scraped-reference` — review rights before production publish.

Proxy pool: set `proxy_list` in `config/scrape.json` or `PROXY_LIST` env (comma-separated).

## Search

**Primary (when synced):** Meilisearch derived index + Edge proxy `catalog-search-meili` — see `docs/decisions/2026-08-05-meilisearch-catalog-search.md` and `infra/satellites/README.md`.

**Fallback:** Supabase RPC `search_catalog(p_mode, p_query)` with modes `part | vin | model | pnc` (PostgreSQL FTS).

```bash
# Full sync (after import)
export SUPABASE_URL=... SUPABASE_SERVICE_KEY=... MEILI_HOST=http://127.0.0.1:7700 MEILI_MASTER_KEY=...
python -m data_pipeline.meili_sync --full
```

Offline tests use `data_pipeline.search_index.CatalogIndex` and `data_pipeline.meili_documents` to smoke-test response shapes.

## ERP catalog v1 reload

Curated pack: `out/erp_catalog_v1/` (133 vehicles / 4142 PNC / 8079 fitments / 635 diagram paths).  
Full reload steps: [`docs/guides/erp-catalog-v1-load.md`](../docs/guides/erp-catalog-v1-load.md).

Diagram Storage: `supabase/seed_catalog_diagrams.mjs` (fixtures) or `amayama_catalog_auto --upload-diagrams` (scraped assets). No Postgres `diagram_assets` table.

## Fixture OEMs (storefront demo alignment)

- `15208-65F0C` — oil filter
- `40206-EA00A` — front brake disc
- `21410-JF00A` — water pump (supersedes `21010-JF00A`)
- `16546-00Q0A` — air filter
- `28970-JD00A` — erp_catalog_v1 sample (JJ10 / washer motor family)
