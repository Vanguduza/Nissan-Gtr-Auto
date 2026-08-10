# PartSouq → catalog pipeline (multi-make)

Operator and architecture guide for producing a full genuine-parts catalog with interactive diagram hotspots, then searching it via PostgreSQL FTS (`search_catalog`: `part | vin | model | pnc`). Meilisearch is deferred ([ADR](../decisions/2026-07-24-search-index-interim-pg-fts.md)).

**Lane:** `@data_pipeline_agent` · **Code:** `data-pipeline/` · **Docs home:** this file (workspace may be opened at `catalog/`; paths below are from the repo root).

**Hard exclusions (pipeline):** no ZIMRA / fiscalisation; no payroll tax; no HTML5/browser QR. Bridge-First hardware rules do not apply here — this is an offline batch pipeline.

---

## 1. Goal / outcomes

| Outcome | Meaning |
|---------|---------|
| Full catalog | Vehicles, PNCs, part–fitment rows, and diagram assets for the chosen manufacturer |
| Interactive hotspots | Normalized bbox on `part_fitment` + image under Storage `catalog-diagrams` |
| 4-way search | `search_catalog(p_mode, p_query)` on Supabase after import |
| Parallel scrape + parse | Crawl keeps writing HTML cache; a separate watcher parses cache into catalog data without stopping the scrape |
| Hybrid queue (default) | Prefer `/vehicle` pages while any remain PENDING, then deep-first parts — competitive priority frontier (§2b) |
| Multi-make ready | Scope by PartSouq `c=` brand + config; isolate outputs per maker (Toyota-only catalog, etc.) |
| Faceted fitment | Chassis, engine, EPC category, optional PCdb part type — filterable from first publish (§10) |
| Publish gates | `--complete-only` + `bundle_quality_report()` block live import until hotspots + categories pass (§2c) |

Default production path today is **English Nissan** on PartSouq. Other makes reuse the same tools with config/path changes; some VIN/OEM helpers are still Nissan-oriented (see §8).

**Industry alignment:** EPC scrape supplies diagram + hotspot geometry; **Auto Care ACES/PIES + VCdb/PCdb** supply cross-brand fitment taxonomy when licensed data is imported (§10). PartSouq remains the dev/integration-test source; licensed FAST/EPC or PCdb feeds are the production taxonomy path.

---

## 1b. One script: multi-make orchestrator (preferred)

**Module:** `python -m data_pipeline.partsouq_catalog_orchestrator`  
**Curated brands:** `data-pipeline/config/partsouq_makers.json`

This is the single entrypoint that runs **scrape + `cache_parse_worker --watch` together per maker** until catalogs are produced (hotspots, importable bundle, vid identity). There is **no separate mapping process** — `vehicle_identity`, stamp/backfill, and missed-cache catch-up already live inside `cache_parse_worker` (sample: vid `190773` → chassis `JJ10` → `vin_prefix` `SJNFBAJ10`; see `tests/test_vehicle_identity.py`).

The crawl (`amayama_catalog_auto --crawl-only`) is **not stopped for parse**; the watcher runs alongside until the crawl exits on its own, then the orchestrator does one final `--once` pass and optional transform/import. Blessed scrape config uses **`queue_mode: hybrid`** (§2b) so vehicle identity coverage is not starved by deep-first parts.

### Selection API

| Flag | Effect |
|------|--------|
| `--makers all` | Every brand in `--makers-file` (default curated list) |
| `--makers Toyota` | One maker only |
| `--makers Toyota,Honda,Nissan` | Selected group (comma-separated; case-insensitive) |
| `--makers-file PATH` | Override curated JSON/YAML list |
| `--discover-makers` | Best-effort PartSouq locate-page discovery via FlareSolverr; falls back to file on failure |
| `--list-makers` | Print resolved names and exit |
| `--dry-run` | Create per-maker dirs + `scrape.json` + `manifest.json` only (no network crawl) |

### Isolation layout

```text
out/makers/manifest.json          # top-level index of makers + paths
out/makers/<slug>/
  scrape.json                     # allowed_brand, start_url c=<Make>, diagram_storage_prefix
  crawler_state.db                # crawl queue + scraped_data
  cache/                          # HTML response cache
  bundle/                         # vehicle_master / part_fitment / … JSON
  cache_parse_state.db            # parse_queue + vehicle_identity
  diagrams/
  browser_session/
  crawl.pid / parse.pid           # owned PIDs only (never kill unrelated processes)
  crawl.log / parse.log / meta.json
```

Each maker gets its own `--state-db`, `--cache-dir`, `--out-dir`, `--parse-db`, `allowed_brand`, start URL `c=<Make>`, and `diagram_storage_prefix=partsouq/<slug>`. Import only that maker’s `bundle/` for a single-brand catalog.

### Real-time scrape + parse (per active make)

1. Start **parse watcher** first (`cache_parse_worker --watch`) so early HTML is not missed.
2. Start **crawl** (`amayama_catalog_auto --local-ip --until-complete --crawl-only`) and leave it running.
3. Watcher parses new cache, builds `vehicle_identity`, backfills, refreshes the bundle — **while scrape continues**. Blessed throughput is **3×** vs the prior runbook (`--batch-size 75`, `--poll-seconds 10`, `--write-bundle-every 17`; §6).
4. When crawl exits, stop **only** that maker’s watcher PID, run `--once` catch-up, then optional `--transform-only` / `--import-dry-run` / `--live-import` / diagram flags.

### Parallelism / resource cost

| Mode | Flag | Behavior | Cost |
|------|------|----------|------|
| **Default (recommended)** | `--parallel-makers 1` | Makers queued one-at-a-time; within each, scrape + parse concurrent | One FlareSolverr session family; steadier CF / disk |
| **All-makes-at-once** | `--parallel-makers N` | Up to N makers scrape+parse simultaneously | N× sessions, CPU, disk, CF risk — use sparingly |

Full `--makers all` is **long-running** (hours–days depending on PartSouq size and pacing). **VIN enrichment runs for every maker** via `config/chassis_catalogs.json` (curated platforms + per-brand EPC stubs — §3). Supabase import is optional (`--import-dry-run` / `--live-import`).

**Cloud, maker-by-maker, popularity queue:** single runbook → [`cloud-multi-make-catalog.md`](./cloud-multi-make-catalog.md) + `config/makers-by-popularity.json`.

### Command examples (from `data-pipeline/`)

```bash
# FlareSolverr must be up (checked at http://127.0.0.1:8191 unless --skip-flaresolverr-check)
docker compose -f ../docker-compose.satellites.yml --profile scrape up -d

# All curated makers (sequential queue; scrape+parse live per make)
python -m data_pipeline.partsouq_catalog_orchestrator --makers all

# Toyota only → out/makers/toyota/
python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota

# Selected group
python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota,Honda,Nissan

# Bounded smoke + dry-run import after transform
python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota --max-pages 30 --import-dry-run

# Live import (production — complete fitments only; auto diagram upload)
python -m data_pipeline.partsouq_catalog_orchestrator --makers Nissan --live-import
# Operator must verify §2c gates before live; prefer:
python scripts/republish_erp_catalog.py --skip-refresh --live-import --complete-only

# Prepare isolation layout without crawling
python -m data_pipeline.partsouq_catalog_orchestrator --makers all --dry-run

# Aggressive: two makers at once (expensive)
python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota,Honda --parallel-makers 2
```

Idempotent / resume-friendly: existing per-maker DBs and caches are reused; PID files block duplicate owned starts. Manual two-terminal runbook remains in §6 if you prefer not to use the orchestrator.

---

## 2. Tool stack

```
FlareSolverr (CF clear)
        ↓
amayama_catalog_auto  ──HTML──►  out/<maker>_cache
  (queue_mode=hybrid;             │
   crawler_state.db)               ▼
                        cache_parse_worker
                        (out/cache_parse_state.db)
                               │
                               ├─ vehicle_identity (vid → chassis → vin_prefix)
                               ├─ unmapped chassis discovery
                               └─ scraped_data (+ mid-run bundle)
                                      ↓
                               transform → out/<maker>_bundle/*.json
                                      ↓
                               import_catalog / --live-import
                                      ↓
                    Supabase tables + catalog-diagrams Storage
                                      ↓
                              search_catalog RPC
```

| Piece | Role |
|-------|------|
| **FlareSolverr** | Clears Cloudflare on the host IP; persistent session keeps cookies warm |
| **`amayama_catalog_auto`** | Hierarchy-aware crawl (`queue_mode`), robots/rate limits, HTML response cache, optional mid-crawl transform |
| **`out/…_cache`** | SHA-256–named `.html` files (default `out/partsouq_cache`) |
| **`crawler_state.db`** | Crawl `queue` + `scraped_data` (WAL). Parse worker reads queue read-only; writes only `scraped_data` |
| **`cache_parse_worker`** | Watches visited URLs + cache; parses parts + vehicle identity; refreshes JSON bundle |
| **`out/cache_parse_state.db`** | Parse queue + `vehicle_identity` (+ `unmapped_chassis` discovery) |
| **Transform** | `vehicle_identity` + `scraped_data` → `vehicle_master` / `pnc_categories` / `part_fitment` / `diagram_assets` (parts rows win on duplicate vehicle keys) |
| **Import** | Idempotent upsert; optional diagram download/upload |
| **Supabase FTS** | Interim index; Meili later when volume/typo/facets justify it |

Config defaults live in `data-pipeline/config/scrape.json` (includes `"queue_mode": "hybrid"`).

---

## 2b. Competitive catalog-crawl practice (blessed)

Single-host EPC/catalog crawls (PartSouq, Amayama-class sites) converge on the same patterns as production URL frontiers (Mercator-style **priority + politeness**), not naive BFS/DFS alone. This guide’s blessed path follows that.

### Principles → our knobs

| Practice | Why | Blessed setting / behavior |
|----------|-----|----------------------------|
| **Priority frontier** | High-value pages before leaf spam | `queue_mode: "hybrid"` — `/vehicle` (identity) before L5 parts |
| **Politeness** | One host, avoid CF bans | `max_concurrent_workers: 1`, `flaresolverr_max_concurrent: 1`, `rate_limit_seconds ≥ 1.5` + jitter |
| **Backoff** | Transient 5xx / CF | `max_retries` / `max_attempts: 5`, exponential backoff 2s→60s |
| **Session reuse** | Solve CF once; keep cookies warm | `use_flaresolverr: true`, `flaresolverr_persist_session: true`, keepalive ~240s |
| **Response cache** | Never re-fetch VISITED HTML | `out/…_cache` + parse watcher reads cache only |
| **Crawl ≠ parse** | Decouple download from extract (common multi-source parts pipelines) | `--crawl-only` + `cache_parse_worker --watch` |
| **Source isolation** | One maker/module per state DB | orchestrator `out/makers/<slug>/` or separate DBs |
| **Canonical URL / dedup** | Query variants waste budget | **Follow-on:** one `/vehicle` claim per distinct `vid` (ssd variants) |
| **Coverage exit criteria** | PENDING=0 ≠ complete catalog | See checklist below — not “queue empty” alone |
| **Session hygiene** | Long FlareSolverr sessions leak / 500 | Keepalive + watchdog; recycle session on repeated FS failures (**ops**) |

### Queue modes (`claim_next_url`)

PartSouq URLs carry a **hierarchy level** (locate/groups ≈ L0–L1, **`/vehicle` ≈ L2 / CHASSIS**, diagram **`/parts` ≈ L5**). Mode comes from `ScrapeConfig.resolved_queue_mode()`.

| `queue_mode` | Claim order | When to use |
|--------------|-------------|-------------|
| **`hybrid`** (default / blessed) | While any PENDING `/vehicle` (or `hierarchy_level = CHASSIS`) → claim those first. Else **deep-first** (highest level). | Production catalog builds: identities first, then efficient parts drain |
| **`deep_first`** | Highest `hierarchy_level` first | Finish parts for already-opened vehicles only — **starves** new vehicles |
| **`bfs`** | Lowest `hierarchy_level` first | Max breadth early; delays deep parts |

Compat: empty/unknown `queue_mode` → `queue_deep_first` true/false maps to `deep_first` / `bfs`.

**Why not pure deep-first or pure BFS?** Deep-first opens a few vehicles, enqueues hundreds of L5 URLs each, and can spend days on those parts while thousands of `/vehicle` URLs sit PENDING. Pure BFS visits all vehicles early but postpones hotspot/parts completion. Hybrid is the competitive middle: priority front for vehicles, then deep back-queue for parts — same *idea* as frontier priority buckets on a single host.

### What changing mode does *not* do

Restarting crawl to load `queue_mode` **only reorders PENDING claims**. It does **not** wipe `crawler_state.db`, delete VISITED cache HTML, or reset `vehicle_identity` / parsed `scraped_data`. New work is additive on top of the existing corpus.

### Blessed config snippet

```json
// data-pipeline/config/scrape.json
"rate_limit_seconds": 1.5,
"jitter_seconds": 0.75,
"max_concurrent_workers": 1,
"flaresolverr_max_concurrent": 1,
"flaresolverr_persist_session": true,
"flaresolverr_keepalive_seconds": 240,
"max_attempts": 5,
"run_until_complete": true,
"completion_max_rounds": 5,
"queue_deep_first": true,
"queue_mode": "hybrid"
```

Per-maker orchestrator copies inherit this unless a maker `scrape.json` overrides. Restart **crawl only** after mode/config changes; leave the parse watcher up; re-point `catalogue_watchdog --pid` at the new crawl PID.

### Exit criteria (“finished” = usable catalog)

Do **not** treat “PENDING≈0” alone as done. Competitive catalog jobs gate on coverage **and** publish-quality (§2c):

1. Crawl: `PENDING=0`, `PROCESSING=0`; `BLOCKED_CF` / hard fails **cleared or explicitly accepted**.
2. Vehicles: distinct visited `/vehicle` **vids** ≈ distinct enqueued vids (ssd-aware); every visited vehicle has `vehicle_identity`.
3. Parse: `parse_queue` caught up (no PENDING/ERROR backlog); catch-up backfill run.
4. VIN policy: every identity has `vin_prefix` (curated or epc_stub) for the maker; upgrade stubs via `chassis_discovery` when desired; final bundle refresh.
5. **Hotspots:** every fitment in import scope has bbox + `diagram_path`; diagram assets uploaded for those paths.
6. **Categories:** `uncategorized_pncs = 0` in import scope; EPC group names normalized (no vehicle prefix in `category_name`).
7. **Publish gate:** `filter_complete_bundle` → `fitments_out > 0`; live import uses **`--complete-only --prune-stale`**.
8. Bundle: spot-check `search_catalog` **part | vin | model | pnc**; facet filters return expected chassis/category chips.

### Ops follow-ons (align further with common practice)

| Item | Status | Notes |
|------|--------|--------|
| Hybrid priority | **Live** | `queue_mode: hybrid` |
| Politeness + FS session | **Live** | concurrency 1, persist + keepalive, watchdog |
| Crawl / parse split + cache | **Live** | `--crawl-only` + watcher |
| **Parse / bundle throughput (3×)** | **Live** | Watcher defaults: `--batch-size 75`, `--poll-seconds 10`, `--write-bundle-every 17` (~3× pages/pass, poll rate, bundle refresh vs prior 25 / 30 / 50) |
| **Publish gate `--complete-only`** | **Live** | `bundle_filter.filter_complete_bundle`; default for production `--live-import` (§2c) |
| **Category normalization** | **Live** | `normalize_epc_category_name()` at parse + transform; web `normalizeDisplayCategory()` |
| **ssd / vid dedup** | Follow-on | Prefer one PENDING `/vehicle` URL per distinct `vid` |
| **Orchestrator import gate** | Follow-on | Auto-fail `--live-import` when quality report fails (§2c) |
| **PCdb part type mapping** | Follow-on | Optional `pcdb_part_type_id` on `pnc_categories` (§10) |
| **ACES VCdb vehicle keys** | Follow-on | Map `vehicle_master` → Year/Make/Model/Engine for ACES export (§10) |
| **Per-vid COMPLETE tracking** | Follow-on | Explicit state in crawl/parse meta (§2c state machine) |
| **FS session recycle** | **Live** | Auto destroy+recreate after 3 consecutive CF blocks; stale `PROCESSING` reclaim (>15m); crawl waits/backoffs when FS down |
| Unmapped chassis | **Live** | `python -m data_pipeline.chassis_discovery --list` / `--export-stubs` — never invent prefixes |

### Priority chassis first (Nissan platforms)

Use when you need **parts coverage for a curated chassis list** before draining the rest of the maker catalog. Non-priority URLs stay **PENDING** in `crawler_state.db` — no wipe, no cache loss.

**Config:** `data-pipeline/config/priority_chassis.json` (33 Nissan platforms: Navara D22/D23/D40, X-Trail T30–T33, Qashqai J10/J11, Tiida C11/C12/SC11, AD0, Patrol Y60–Y62, Pathfinder R50–R52, etc.). Curated metadata merges from `config/chassis_catalogs.json` where present.

**How claiming works**

| PENDING row | Claimed in priority mode? |
|-------------|---------------------------|
| locate / filter / model (L0–L1) | Yes — bootstrap navigation |
| `/vehicle` without chassis yet | Yes — discovery |
| `vehicle_context.chassis_code` in priority set | Yes |
| `vid` mapped to priority chassis in `cache_parse_state.db` | Yes |
| Known non-priority chassis (e.g. R35) | **Skipped** — stays PENDING |
| Unknown deep parts URL, no vid/chassis hint | Skipped (strict default) |

PartSouq model codes normalize via `normalize_chassis_code` (e.g. `JJ10E` → `JJ10`, **`AD0NN` → `AD0`**). Aliases live in the JSON `chassis.*.aliases` field.

**Start priority crawl (Nissan, orchestrator — recommended)**

```powershell
cd data-pipeline
docker compose -f ../docker-compose.satellites.yml --profile scrape up -d

# Priority-only until eligible PENDING = 0 (parse watcher runs in parallel)
python -m data_pipeline.partsouq_catalog_orchestrator --makers Nissan --priority-chassis --local-ip
```

**Direct crawl module (same maker paths as orchestrator)**

```powershell
python -m data_pipeline.amayama_catalog_auto `
  --config out/makers/nissan/scrape.json `
  --state-db out/makers/nissan/crawler_state.db `
  --cache-dir out/makers/nissan/cache `
  --out-dir out/makers/nissan/bundle `
  --session-dir out/makers/nissan/browser_session `
  --priority-chassis-file config/priority_chassis.json `
  --until-complete --crawl-only --retry-failed --local-ip
```

**Resume full maker crawl** — restart **without** `--priority-chassis` / `--priority-chassis-file`. Existing VISITED rows, HTML cache, `vehicle_identity`, and parse progress are unchanged; only claim order widens to all PENDING URLs.

```powershell
python -m data_pipeline.partsouq_catalog_orchestrator --makers Nissan --local-ip
```

**Coverage report** (identity-only vs fitments per priority chassis):

```powershell
python -m data_pipeline.amayama_catalog_auto `
  --state-db out/makers/nissan/crawler_state.db `
  --parse-db out/makers/nissan/cache_parse_state.db `
  --out-dir out/makers/nissan/bundle `
  --priority-chassis-file config/priority_chassis.json `
  --chassis-coverage
```

Exit criteria for priority phase: log line `Priority chassis crawl complete — N non-priority URLs remain PENDING`. Then run parse catch-up (`cache_parse_worker --once`) and optional `--transform-only` if crawl-only was used.

---

## 2c. Publish-quality gates (PIM-style — required before live)

Industry catalog pipelines (dealer EPC, ACES/PIES PIM, shop-by-diagram vendors) **validate then publish**. This repo mirrors that: the JSON bundle is the PIM layer; Supabase is the published storefront SoR.

### Definition of “complete” (storefront-ready)

| Layer | Rule |
|-------|------|
| **Fitment** | `chassis_code` + normalized `bbox_x/y/width/height` + non-empty `diagram_path` |
| **Vehicle** | `vehicle_master` row only if that chassis has ≥1 complete fitment (`--complete-only`) |
| **Category** | `pnc_categories.category_name` = normalized EPC assembly group — not `UNCATEGORIZED`, not vehicle model slug, not part description |
| **Diagram asset** | Every `diagram_path` in fitments has a row in `diagram_assets` and a Storage object after upload |
| **Facets** | `chassis_code`, `engine_code`, `category_name` / `subcategory_name` populated on fitments; optional `pcdb_part_type_id` when mapped (§10) |

**Identity-only vehicles** (vid/chassis known, zero parts pages) are valid for crawl progress but **must not** appear in live import when `--complete-only` is set.

### `bundle_quality_report()` (ops + import gate)

Run before every `--live-import` / `--live`. Implemented via `filter_complete_bundle()` + `parse_bundle_meta.json` + chassis coverage CLI.

| Check | Source | Fail live import? |
|-------|--------|-------------------|
| Complete fitments > 0 for target chassis | `filter_complete_bundle` meta | **Yes** (production) |
| `uncategorized_pncs` = 0 or below threshold | `parse_bundle_meta.json` | **Yes** if any PNC in import scope |
| `excluded_identity_only_chassis` logged | filter meta | No (informational) |
| Diagram files on disk / Storage | `--download-diagrams` + upload | **Yes** if path missing |
| Parse queue PENDING/ERROR | `cache_parse_worker` stats | **Yes** for production maker complete |
| Priority chassis coverage | `--chassis-coverage` | **Yes** if priority phase claimed done but chassis has 0 fitments |

**Commands:**

```powershell
# Filter + metadata (no DB)
python scripts/build_erp_catalog.py --completed-only

# Live import (production default — prune stale rows)
python -m data_pipeline.import_catalog out/makers/nissan/bundle --live --complete-only

# Orchestrator / republish (frozen bundle, skip mid-crawl refresh)
python scripts/republish_erp_catalog.py --skip-refresh --live-import --complete-only
```

Orchestrator **`--live-import` must pass** the same gate: run `filter_complete_bundle` on the maker’s `bundle/`; abort if `fitments_out = 0` or target chassis list fails coverage (implement in orchestrator follow-on; **operators enforce manually until wired**).

### Per-`vid` completion state machine (crawl + parse)

Track progress per PartSouq `vid` (not only global PENDING=0):

```text
IDENTITY     → /vehicle visited; vehicle_identity row exists
CATEGORIES   → L2 category URLs for vid enqueued/visited
PARTS        → L5 /parts pages for vid visited
PARSED       → scraped_data payloads with bbox + category_name
COMPLETE     → all L5 pages for vid PARSED; fitments in bundle pass complete_fitments()
```

**Blessed behavior:**

| Stage | Module | Rule |
|-------|--------|------|
| Crawl | `amayama_catalog_auto` | Hybrid queue; priority chassis boosts eligible vids; reclaim stale PROCESSING |
| Parse | `cache_parse_worker` | Re-queue parts pages missing bbox or category; `normalize_epc_category_name()` at parse |
| Transform | `refresh_bundle` | Drop fitment rows without bbox at source when building storefront bundle |
| Import | `import_catalog --complete-only` | Scope PNCs/diagrams to kept fitments; prune stale `vehicle_master` / `part_fitment` / `stock_items` |

### Test pipeline before expensive EPC (mandatory order)

| Step | Source | Proves |
|------|--------|--------|
| 1 | FAST fixtures (`fixtures/navara_d40_yd25`, `fixtures/xtrail_t31_mr20`) | Schema, import, categories, **clean diagram art** (no watermark) |
| 2 | Bounded PartSouq (`--max-pages 30` or `--priority-chassis`) | Real EPC shape, bbox, hybrid queue — **staging only** |
| 3 | Quality report + `--import-dry-run` | Gates pass before cloud |
| 4 | `--live-import --complete-only` | Production SoR |
| 5 | Storefront QA | Facet filters, diagram canvas, VIN/model/PNC search |
| 6 | Licensed EPC / PCdb | Infomedia FAST export, 17vin API, or Auto Care ACES/PIES feed (§10) |

PartSouq GIFs carry **watermarks** — treat as `scraped-reference` provenance; production diagrams from **licensed-fast**, **original-fixture-art**, or **customer-supplied** assets.

### Facet layer (web + mobile parity)

Shared rules live in **`packages/shared/catalog-facets`** (follow-on module): normalize display category, chassis/engine facet keys, and “has interactive diagram” flag. Web (`normalizeDisplayCategory`), iOS, and Android must consume the same helpers — not duplicate string logic per app.

---

## 3. Per-manufacturer configuration

### Already brand-parameterized

| Knob | Where | Nissan default | Toyota example |
|------|--------|----------------|----------------|
| `start_url` | `scrape.json` / `--start-url` | `…/locate?c=Nissan` | `…/locate?c=Toyota` |
| `allowed_brand` | `scrape.json` | `"nissan"` | `"toyota"` |
| `allowed_locale` / `allowed_path_substring` | `scrape.json` | `en` / `/en/catalog/` | same |
| `diagram_storage_prefix` | `scrape.json` | `partsouq/nissan` | `partsouq/toyota` |
| `supabase_diagrams_bucket` | `scrape.json` | `catalog-diagrams` | same bucket, different prefix |
| `queue_mode` | `scrape.json` | `"hybrid"` | same (recommended) |
| `--priority-chassis` / `--priority-chassis-file` | crawl CLI / orchestrator | (unset) | Pass flag for priority phase only — **not** persisted in `scrape.json` |
| `--state-db`, `--cache-dir`, `--out-dir` | CLI | `crawler_state.db`, `out/partsouq_cache`, `out/partsouq_bundle` | Use **separate** paths per maker (recommended) |
| `--parse-db` (watcher) | CLI | `out/cache_parse_state.db` | e.g. `out/toyota_cache_parse_state.db` |

Scope gate: `is_in_scope_url()` keeps only English PartSouq URLs whose `c=` query matches `allowed_brand` (case-insensitive; `c=NISSAN201809` matches `nissan`). Out-of-scope queue rows are pruned at crawl start.

### Recommended isolation layout

```text
data-pipeline/
  config/scrape.nissan.json          # copy of scrape.json tuned for Nissan
  config/scrape.toyota.json          # start_url c=Toyota, allowed_brand toyota, …
  out/nissan_cache/
  out/nissan_bundle/
  out/toyota_cache/
  out/toyota_bundle/
  crawler_state.nissan.db
  crawler_state.toyota.db
  out/nissan_cache_parse_state.db
  out/toyota_cache_parse_state.db
```

Do **not** mix two brands in one `crawler_state.db` / cache dir — prune will drop the other brand’s URLs, and identity/VIN maps will collide.

### Still brand-scoped / follow-on

| Area | Behavior today |
|------|----------------|
| `normalize_oem` | Expects Nissan-style `XXXXX-XXXXX` (10 alnum); other OEM formats may be dropped |
| `classify_url` PartSouq branch | Defaults `model_slug` to `"nissan"` when brand not detected in query/text |
| `_hints_from_alt` in `parse_partsouq_html` | Only strips a leading `NISSAN` token from diagram `alt` |
| Module/docs naming | Nissan-centric wording in places; Amayama hierarchy kept for legacy tests |
| DB schema | No `manufacturer` / `make` column on `vehicle_master` — brand separation is by scrape scope + storage prefix, not SQL filter |

**VIN enrichment (multi-make — live):** `config/chassis_catalogs.json` covers all orchestrator makers. Curated `chassis.vin_prefixes` when known; otherwise brand **epc_stub** `{primary_wmi}{chassis}` garage keys. Orchestrator sets `allowed_brand` + parse `--brand`. Upgrade stubs via `chassis_discovery --export-stubs`. Never invent full ISO VINs.

**Aspirational:** denser curated chassis rows per make; OEM format plugins; `make` on catalog tables + import `--brand` filter; brand-aware alt/URL parsers.

---

## 4. Real-time parse + VIN / model / part mapping

### Why a separate watcher

The crawler’s job is to drain the queue and cache HTML (and it may also store some payloads inline). The **recommended** production pattern is:

1. Run the crawl **without** waiting for full parse to finish (`--crawl-only` or keep crawl running with checkpoints).
2. In a **second terminal**, run `cache_parse_worker --watch` so parsing stays caught up **while** scrape continues.

The watcher **never** changes the crawl `queue` table. It only:

- Reads `VISITED` URLs from `crawler_state.db`
- Reads HTML from the cache directory
- Upserts `scraped_data` rows for those URLs
- Maintains `vehicle_identity` in `out/cache_parse_state.db`
- Periodically rewrites the catalog JSON bundle (blessed: every **17** pages, **75** pages/batch, **10**s poll — §6). **`vehicle_master` merges `vehicle_identity` with parts-derived rows** so VIN/garage coverage tracks `/vehicle` pages even before L5 fitments exist; parts-derived rows win on duplicate natural keys.

### Bundle refresh (`vehicle_master` + fitments)

On each bundle write, `refresh_bundle`:

1. Transforms **`scraped_data`** (parts pages) → fitments, diagrams, PNCs, and any vehicle rows inferred from stamped parts context.
2. Expands **`vehicle_identity`** (`/vehicle` pages) → additional `vehicle_master` rows via the same `decode_from_chassis` / VIN enrichment path.
3. **Merges** both sets; when the same `(vin_prefix, chassis_code, engine_code, production_year, model_variant)` appears in both, the **parts-derived** row wins (hotspot context is authoritative for engine/year).

So **`vehicle_identity` count can exceed `vehicle_master` only after merge dedup** (many vids → fewer chassis/engine/year rows), not because identities were omitted. **`part_fitment` still requires visited parts pages** — identity-only vehicles appear in VIN/model search before hotspots land.

`parse_bundle_meta.json` includes `vehicles`, `vehicles_from_identity`, `vehicles_from_parts`, and `identities` for ops checks.

### Category + part-name enrichment (during parse, not pre-crawl)

EPC assembly labels and part display names **cannot** be inferred before HTML is fetched — they come from parsed diagram markup (`img alt`, URL `cname`/`uname`, hotspot table rows). Enrichment therefore runs **during parse**, alongside crawl, not before or during the HTTP fetch itself.

| Phase | Enrichment? | Why |
|-------|-------------|-----|
| Pre-crawl / URL queue | No | No HTML yet — only URLs and queued vehicle hints |
| During crawl (HTTP fetch) | No | Crawl caches HTML and may capture embedded JSON; it does not parse hotspots |
| **Parse watcher (`cache_parse_worker`)** | **Yes — primary path** | `parse_partsouq_html` writes `category_name`, `subcategory_name`, `diagram_title` into each payload; payloads land in `scraped_data` |
| Post-crawl transform | Uses enriched payloads | Orchestrator final step passes `--parse-db` so `refresh_bundle` merges identity + parts (never bare `run_transform`, which would drop enrichment) |

On each `refresh_bundle` write:

1. `transform_raw_records(scraped_data)` → `pnc_categories`, `part_fitment`, `_oem_display_names`
2. Merge with `vehicle_identity` → full bundle
3. Write table JSON + `oem_display_names.json`
4. Write `parse_bundle_meta.json` quality gates:

| Field | Healthy signal |
|-------|----------------|
| `uncategorized_pncs` | **0** in import scope — high means missing `diagram_title` / `cname` |
| `oem_display_names` | Grows with parsed hotspot descriptions |
| `pncs`, `fitments`, `vehicles` | Track catalog size |
| `fitments_out` / `excluded_identity_only_chassis` | After `filter_complete_bundle` — identity-only chassis must not reach live |

A warning is logged when `uncategorized_pncs > 0`. Re-enrich an existing crawl without re-scraping: `python scripts/republish_erp_catalog.py`.

### Identity path (`/vehicle` pages)

1. Detect vehicle URL/HTML (`…/vehicle`, cells `data-title="Model"` / `Name`).
2. Parse → `{ vid, chassis_code, model_variant, grade, market, year, engine_code, … }`.
3. `enrich_hints_with_vin` → add `vin_prefix` from brand catalog (curated) or **epc_stub**; note non-curated codes via **chassis discovery** for later upgrade (never invent full ISO VINs).
4. Upsert into `vehicle_identity` keyed by **vid**.
5. **Backfill** existing `scraped_data` rows for that vid (stamp chassis / vin_prefix onto `vehicle` + `vehicle_context`).

Under **`queue_mode: hybrid`**, the crawl prioritizes these `/vehicle` pages while any remain `PENDING`, so identity coverage can catch up without waiting for every L5 parts URL to finish first (see §2b).

### Parts / hotspot path (parts pages)

1. Parse diagram sections: `.lable-single` hotspots (`data-position`, `data-size`) + `part-search-tr` OEM rows.
2. Derive `category_name` / `subcategory_name` from URL `cname`, diagram `alt`, and page breadcrumbs; **`normalize_epc_category_name()`** strips vehicle model prefixes; store on each payload in `scraped_data`.
3. Optional follow-on: set `assembly_group_id` (PartSouq `cid=` / FAST subgroup), `catalog_section_path` (breadcrumb), and `pcdb_part_type_id` when a PCdb mapping table exists (§10).
3. Attach `vid` from query string; merge page meta + known `vehicle_identity`.
4. Replace `scraped_data` for that URL; `refresh_bundle` transform emits fitments with bbox, PNC categories, and `oem_display_names.json`.

### Mapping summary

| Source | Produces |
|--------|----------|
| `/vehicle?…&vid=` | vid → chassis → vin_prefix (VIN search key) |
| Parts page `vid` / `gid` | Links parts to the same vehicle identity |
| Hotspot labels + table | OEM, PNC/code-on-image, pixel bbox → normalized 0–1 bbox |
| Diagram `img` | `image_url` → Storage path under `diagram_storage_prefix` |

Order independence: parts may parse before the vehicle page. Catch-up backfill and re-queue logic fix incomplete rows once identity lands (see §6).

---

## 5. Hotspots + 4-way search wiring

### Hotspots

1. Parser extracts pixel `left/top/width/height` (+ image size).
2. Transform `extract_bbox` normalizes to `bbox_x`, `bbox_y`, `bbox_width`, `bbox_height` in **0–1** image space.
3. Those columns land on `part_fitment`; `diagram_path` points at the object in bucket `catalog-diagrams`.
4. Upload: `--download-diagrams` / `--upload-diagrams` on the auto pipeline (service role).

Storefront / apps consume bbox + `diagram_path` for the interactive canvas (UI is outside this pipeline).

### Search (interim PG FTS)

After import, authenticated clients call:

```sql
SELECT search_catalog('part',  '15208-65F0C');
SELECT search_catalog('vin',   'SJNFBAJ10…');   -- matched on vehicle_master.vin_prefix
SELECT search_catalog('model', 'Qashqai');
SELECT search_catalog('pnc',   '15208');
```

Migration: `supabase/migrations/20260724010000_catalog_search_fts.sql`.  
Decision: [2026-07-24-search-index-interim-pg-fts.md](../decisions/2026-07-24-search-index-interim-pg-fts.md).

VIN mode works when `vehicle_identity` has `vin_prefix` from the active maker’s catalog / epc_stub before import (`config/chassis_catalogs.json`).

---

## 6. Runbook (manual two-terminal — optional if not using §1b orchestrator)

All commands from `data-pipeline/` unless noted. Python ≥ 3.11. For most operators, prefer §1b (`partsouq_catalog_orchestrator`) which starts scrape + one watcher per make automatically.

Blessed crawl behavior comes from **`config/scrape.json`**: `"queue_mode": "hybrid"` (vehicles first, then deep-first parts), concurrency 1, FlareSolverr persist/keepalive, until-complete (§2b). Pass `--config` explicitly so the runbook matches the file on disk.

### One-time setup

```bash
cd data-pipeline
pip install -e ".[dev,scraping,supabase]"
patchright install chromium

# FlareSolverr (repo root) — required; crawl waits up to 1h if Docker is starting
docker compose -f docker-compose.satellites.yml --profile scrape up -d

export SUPABASE_URL=...
export SUPABASE_SERVICE_ROLE_KEY=...
```

### A. Start scrape (do not stop for parse)

**Nissan (blessed — hybrid queue via scrape.json):**

```bash
# Loads queue_mode=hybrid, politeness, FlareSolverr session from config/scrape.json
# Does NOT wipe crawler_state.db or cache — resume-safe
python -m data_pipeline.amayama_catalog_auto \
  --config config/scrape.json \
  --local-ip --until-complete --crawl-only \
  --out-dir out/partsouq_bundle \
  --cache-dir out/partsouq_cache \
  --state-db crawler_state.db
```

Confirm in the crawl log: `Until-complete mode ON (queue_mode=hybrid, …)`.

**Crawl stalled (visited count flat, no new cache):** check FlareSolverr at `http://127.0.0.1:8191/health`. If down, start Docker Desktop then `docker start gtr-flaresolverr` (or compose `up -d`). Restart crawl with `requeue_failed` (automatic on `--until-complete` rounds) — do **not** wipe `crawler_state.db`. The blessed crawl now waits for FS on startup and backs off in-worker when FS drops mid-run.

**Toyota (configure-for-other-makes — live crawl gate; VIN map still Nissan-centric):**

```bash
python -m data_pipeline.amayama_catalog_auto \
  --config config/scrape.toyota.json \
  --local-ip --until-complete --crawl-only \
  --start-url "https://partsouq.com/en/catalog/genuine/locate?c=Toyota" \
  --state-db crawler_state.toyota.db \
  --cache-dir out/toyota_cache \
  --out-dir out/toyota_bundle
```

Ensure `scrape.toyota.json` sets `"allowed_brand": "toyota"`, `"diagram_storage_prefix": "partsouq/toyota"`, and **`"queue_mode": "hybrid"`** (copy from `scrape.json` if missing).

Optional: attach `catalogue_watchdog` for FlareSolverr/session crash recovery:

```bash
# After crawl starts — use the crawl PID from Task Manager / Get-Process
python -m data_pipeline.catalogue_watchdog --pid <CRAWL_PID> --restart-transient --interval 20
```

### B. Start cache parse watcher (alongside scrape)

Start **before or alongside** crawl so new `/vehicle` HTML is mapped promptly under hybrid claim order:

```bash
# Catch-up on every pass is ON by default (re-stamp scraped_data from vehicle_identity)
# Blessed 3× bundle throughput: larger batches, faster poll, more frequent bundle refresh
python -m data_pipeline.cache_parse_worker --watch \
  --poll-seconds 10 --batch-size 75 --write-bundle-every 17 \
  --state-db crawler_state.db \
  --cache-dir out/partsouq_cache \
  --parse-db out/cache_parse_state.db \
  --out-dir out/partsouq_bundle
```

| Flag | Meaning |
|------|---------|
| `--once` | Single pass (default if neither `--once` nor `--watch`) |
| `--watch` | Loop forever; poll for new/changed cache |
| `--poll-seconds` | Sleep between passes (blessed: **10**; prior runbook: 30) |
| `--batch-size` | Pages parsed per inner loop (blessed: **75**; prior: 25) |
| `--no-catch-up` | Skip start-of-pass `backfill_all_identities` |
| `--limit N` | Cap pages parsed this pass |
| `--write-bundle-every N` | Rewrite JSON every N pages (blessed: **17** ≈3× refresh rate vs 50; `0` = end of pass only) |

**Bundle meta:** `out/…_bundle/parse_bundle_meta.json` reports `vehicles`, `vehicles_from_identity`, `vehicles_from_parts`, `identities`, and fitment/diagram counts after each refresh.

**Restart parse only** to pick up new watcher flags — leave crawl and `catalogue_watchdog` untouched.

### C. How catch-up avoids missed cache after watcher restart

On every `run_pass` (including watcher start):

1. **`enqueue_new_from_cache`** — for each crawl `VISITED` URL with HTML on disk:
   - new URL → `PENDING`
   - digest change / `ERROR` / `EMPTY` → re-queue
   - vehicle page marked `DONE` but no `vehicle_identity` row → re-queue
   - parts page incomplete vs known identity → prefer in-place backfill; re-queue only if still incomplete
2. **`backfill_all_identities`** (unless `--no-catch-up`) — re-stamp all `scraped_data` rows that match known vids
3. Process `PENDING` batch; refresh bundle when work happened

Restarting the watcher therefore re-scans the whole visited set and does not depend on “only files created while watching.”

### D. Backfill / re-parse already-parsed pages

- **Identity stamp only:** run one pass with catch-up (default):  
  `python -m data_pipeline.cache_parse_worker --once`
- **Force re-parse after HTML change:** replace cache file (digest changes) or delete/reset the `parse_queue` row for that URL (or wipe `out/cache_parse_state.db` to rebuild the parse queue — crawl DB untouched).
- **Transform without crawl:**  
  `python -m data_pipeline.amayama_catalog_auto --transform-only --state-db crawler_state.db --out-dir out/partsouq_bundle`

### E. Import dry-run / live

**Production default:** always filter to complete fitments before live import (§2c).

```bash
# From fixture or scrape bundle directory
python -m data_pipeline.import_catalog out/partsouq_bundle
python -m data_pipeline.import_catalog out/partsouq_bundle --live --complete-only

# Build filtered erp_catalog_v1 pack + quality meta
python scripts/build_erp_catalog.py --completed-only

# Or via auto pipeline after transform
python -m data_pipeline.amayama_catalog_auto --transform-only --import-dry-run
python -m data_pipeline.amayama_catalog_auto --transform-only --live-import \
  --download-diagrams --upload-diagrams
# Prefer republish with frozen bundle:
python scripts/republish_erp_catalog.py --skip-refresh --live-import --complete-only
```

Idempotency keys: see `data-pipeline/README.md` (vehicle / PNC / fitment / OE cross-ref). **`--complete-only --live`** prunes stale `vehicle_master`, `part_fitment`, and `stock_items` not in the filtered bundle.

### F. SQLite artifacts cheat-sheet

| File | Owner | Contents |
|------|--------|----------|
| `crawler_state.db` | Crawl (+ parse writes `scraped_data`) | `queue`, `scraped_data` |
| `out/cache_parse_state.db` | Parse worker only | `parse_queue`, `vehicle_identity`, `unmapped_chassis` |
| `out/…_cache/*.html` | Crawl (`ResponseCache`) | Raw PartSouq HTML |
| `out/…_bundle/*.json` | Transform / watcher | Importable catalog pack |

---

## 7. Separating maker catalogs

**Preferred:** use the orchestrator (§1b) — it enforces isolation under `out/makers/<slug>/` and writes `out/makers/manifest.json`.

**Live today (operational separation):**

1. Separate `start_url` + `allowed_brand`.
2. Separate `--state-db`, `--cache-dir`, `--out-dir`, `--parse-db`.
3. Separate `diagram_storage_prefix` so Storage objects do not overwrite each other in `catalog-diagrams`.
4. Import only the maker’s bundle directory.

**Not live yet (follow-on):**

- SQL `WHERE manufacturer = …` (column absent).
- Import CLI `--brand` filter.
- Denser curated chassis rows for every PartSouq platform (stubs already cover all makers).
- OEM normalizer that accepts non-Nissan part-number shapes.

A “Toyota-only catalog” means: scrape Toyota into `out/makers/toyota/`, import that bundle (VIN enrichment already brand-scoped).

---

## 8. Current gaps / follow-ons (honest)

| Status | Item |
|--------|------|
| **Live** | FlareSolverr crawl, brand-scoped queue, HTML cache, parallel `cache_parse_worker` (vid → chassis → vin_prefix + backfill + catch-up inside watcher), hotspot bbox → `part_fitment`, bundle refresh, dry-run/live import, **`--complete-only` publish gate**, PG FTS `search_catalog`, diagram Storage upload path, **multi-make orchestrator** (`partsouq_catalog_orchestrator`), **`queue_mode: hybrid`** (§2b), **multi-make VIN maps** (`config/chassis_catalogs.json` + epc_stub), **category normalization** |
| **Live for Nissan** | Dense curated `CHASSIS_CATALOG` platforms; chassis auto-discovery for upgrades; fixture packs (e.g. Navara D40) |
| **Live for other makes** | Brand WMI + epc_stub garage keys for every chassis seen; curated seeds for Toyota/Honda/BMW platforms; denser curation over time |
| **Configure now, incomplete enrichment** | OEM normalizer still Nissan-shaped; DB has no `manufacturer` column — isolate by maker paths |
| **Follow-on** | Prefer one `/vehicle` URL per distinct `vid` (ssd-variant dedup); orchestrator auto quality gate on `--live-import`; **ACES/VCdb/PCdb** mapping (§10); per-vid COMPLETE state in meta; `packages/shared/catalog-facets`; stronger automated coverage reports |
| **Aspirational** | OEM format plugins; DB `manufacturer`; import filters; Meilisearch dual-write with facet attributes (`chassis`, `engine`, `pcdb_part_type_id`, `has_diagram`) |
| **Out of scope** | ZIMRA, payroll tax, HTML5 QR, browser hardware bridges |

**Vid-mapping code:** present when this guide was written — `parse_partsouq_vehicle_html` / `vid_from_url` in `data_pipeline/parse_partsouq_html.py`, and `vehicle_identity` + backfill in `data_pipeline/cache_parse_worker.py` (covered by `tests/test_vehicle_identity.py`). Hybrid claim tests: `tests/test_until_complete.py` (`test_claim_hybrid_*`).

---

## 10. ACES / VCdb / PCdb fitment taxonomy

[Auto Care Association](https://www.autocare.org/data-standards) data standards are the **aftermarket industry baseline** for fitment and part terminology. This pipeline is **EPC-first** (OEM diagram groups + Nissan FAST PNC); ACES/PIES layers sit **alongside** scrape output for cross-brand search, Amazon-style feeds, and validated facets.

### Standards map

| Standard | Database | What it carries | This repo today |
|----------|----------|-----------------|-----------------|
| **ACES** | **VCdb** (Vehicle Configuration) | Year / Make / Model / Submodel / Engine / qualifiers → **application** fitment | Partial — `vehicle_master` has chassis, engine, year, model_variant; no ACES XML export yet |
| **PIES** | Product + media attributes | Descriptions, weights, hazmat, digital assets | Partial — `stock_items`, `oem_display_names`, diagram Storage paths |
| **Part terminology** | **PCdb** (Part Terminology) | Stable **PartTerminologyID** + part type name (e.g. “Brake Pad Set”) | **Follow-on** — optional `pcdb_part_type_id` on `pnc_categories` (schema live) |
| **Brand / label** | **PAdb** | Brand codes, label images | Out of scope until PIES import |
| **Qualifier** | **Qdb** | Drive type, body, bed length, etc. | Partial — engine_code, model_variant, production_year act as qualifiers |

**Paid data:** VCdb/PCdb/Qdb/PAdb reference files require an [Auto Care subscription](https://www.autocare.org/join-us/membership). Design tables and bundle fields to **accept** ACES/PIES imports without rebasing the SoR onto a third-party PIM.

### Dual taxonomy model (recommended)

```text
EPC layer (diagram UX)          Aftermarket layer (faceted search / feeds)
─────────────────────          ─────────────────────────────────────────
category_name                  pcdb_part_type_id  → PCdb PartTerminology
subcategory_name               PIES attributes (when imported)
assembly_group_id              ACES BaseVehicle + EngineConfig refs
catalog_section_path           ACES application qualifiers (Qdb)
pnc_code (FAST / code-on-image)
bbox + diagram_path
```

- **Shop-by-diagram** PLP/PDP uses **EPC `category_name`** and chassis/engine facets (matches dealer EPC and Black Dot–style UX).
- **Cross-brand category browse** and marketplace feeds use **`pcdb_part_type_id`** when mapped.
- Never replace EPC assembly names with PCdb labels on diagram pages — users expect OEM group names on exploded views.

### `pcdb_part_type_id` on `pnc_categories`

Bundle schema (`pnc_categories.schema.json`) supports optional fields:

| Field | Purpose |
|-------|---------|
| `pcdb_part_type_id` | Integer **PartTerminologyID** from PCdb |
| `assembly_group_id` | Stable EPC section key (PartSouq `cid=`, FAST subgroup) |
| `catalog_section_path` | Breadcrumb for hierarchical facet UI |

**Mapping strategies (pick one per deployment):**

1. **Curated lookup table** — `data-pipeline/config/epc_to_pcdb.json`: EPC normalized group name → PCdb ID (start with top 50 assembly groups per make).
2. **PIES supplier file** — if a supplier sends PIES with PartTerminologyID + OEM, join on normalized OEM via `python -m data_pipeline.aces_pies_import` (`data_pipeline/aces_pies/`; sample XML under `fixtures/aces_pies/`). See `data-pipeline/README.md` § ACES/PIES.
3. **Manual curation** — ops tool for unmapped groups; block `--live-import` only if `uncategorized_pncs > 0`, not if PCdb unmapped (PCdb is optional enhancement).

Parse/transform **preserves** EPC names; PCdb IDs are **additive** — set in transform or a post-process `enrich_pcdb.py` / `aces_pies_import` before import. ACES **application** upsert is stubbed until `part_fitment` has provenance (`source=aces|epc`); EPC bbox/`diagram_path` stay authoritative.

### ACES export (follow-on)

When VCdb is licensed, emit ACES 4.x XML applications from:

```text
vehicle_master  → BaseVehicleID / EngineConfigID (VCdb lookup)
part_fitment    → Part (+ OEM) + application rows per chassis/engine/year
pnc_categories  → PartTerminologyID when pcdb_part_type_id set
```

Keep **ledger/catalog SoR in Supabase**; ACES XML is an **export satellite**, not the crawl source of truth.

### Industry comparison (why gates matter)

| Pattern | Examples | Lesson for this pipeline |
|---------|----------|---------------------------|
| Dealer EPC | Infomedia, Partslink24 | No section goes live without illustration + callout list |
| Shop-by-diagram SaaS | Black Dot | All hotspots pre-mapped before publish |
| Aftermarket PIM | ACES/PIES validators | Block rows missing required fitment fields |
| Retail text catalog | RockAuto | YMM tree only — **no** interactive OEM diagrams |

This guide’s **§2c gates** implement dealer-EPC + PIM discipline on top of PartSouq scrape geometry.

---

## 11. Multi-vehicle agent prompt (Cursor / `@data_pipeline_agent`)

Copy or invoke when running orchestrator work, cloud multi-make crawls, or catalog import. **Skill mirror:** `.cursor/skills/parts-catalog-ingestion/SKILL.md`.

```text
You are building a multi-vehicle parts catalog for the GTR ERP data pipeline.

READ FIRST: docs/guides/partsouq-multimake-catalog-pipeline.md (§2b crawl, §2c gates, §10 ACES/PCdb)
Cloud runbook: docs/guides/cloud-multi-make-catalog.md

HARD RULES
- NO ZIMRA, NO payroll tax, NO HTML5/browser QR scanning
- One maker per state DB / cache / bundle (out/makers/<slug>/)
- queue_mode: hybrid — /vehicle before L5 parts; concurrency 1; never wipe VISITED cache/DBs to fix priority
- Crawl ≠ parse: amayama_catalog_auto --crawl-only + cache_parse_worker --watch
- Never invent full ISO VINs — chassis_catalogs.json curated prefix or epc_stub only
- Production live import: ALWAYS --complete-only --prune-stale (chassis + bbox + diagram_path)
- PartSouq = dev/staging scrape; watermarked GIFs are scraped-reference, not production art

PUBLISH DEFINITION OF DONE (all required for --live-import)
1. Every imported fitment: chassis_code + bbox_x/y/width/height + diagram_path
2. vehicle_master: only chassis with ≥1 complete fitment (no identity-only vehicles live)
3. pnc_categories: normalized EPC category_name (not UNCATEGORIZED, not model slug)
4. diagram_assets uploaded to Storage for every diagram_path in fitments
5. parse_bundle_meta: uncategorized_pncs = 0 in import scope
6. filter_complete_bundle: fitments_out > 0; log excluded_identity_only_chassis
7. search_catalog spot-check: part | vin | model | pnc on sample OEM/chassis

TAXONOMY
- EPC category_name/subcategory_name from parse (cname, diagram alt, breadcrumbs)
- Optional pcdb_part_type_id on pnc_categories when mapping table exists (§10)
- Facets: chassis_code, engine_code, category_name — shared via packages/shared/catalog-facets (follow-on)

TEST ORDER (before full PartSouq / licensed EPC spend)
1. FAST fixtures → import --live --complete-only
2. Bounded orchestrator (--max-pages 30 or --priority-chassis)
3. bundle quality report + --import-dry-run
4. --live-import --complete-only
5. Storefront diagram + facet QA

ORCHESTRATOR DEFAULTS
- --parallel-makers 1 (one maker until complete, then next)
- Popularity order: config/makers-by-popularity.json
- --live-import only after §2c gates pass; prefer republish_erp_catalog.py --skip-refresh --complete-only

WHEN STUCK
- Priority crawl 0 throughput: claim_next_url must scan beyond first batch for priority chassis
- Wrong categories live: re-run normalize + re-import pnc_categories
- Identity-only vehicles in search: re-import with --complete-only --prune-stale
```

---

## 12. Related docs

- [Megazip multivehicle EPC hierarchy pipeline](./megazip-multivehicle-catalog.md) — `megazip_catalog_orchestrator`, browse RPCs, re-run without re-crawl
- [Phase 7 plan](../plans/2026-07-24-phase7-data-pipeline-search.md)
- [Interim PG FTS ADR](../decisions/2026-07-24-search-index-interim-pg-fts.md)
- [data-pipeline/README.md](../../data-pipeline/README.md) — install, scrape flags, watchdog
- Repo laws: `AGENTS.md` / `.cursorrules` (exclusions; pipeline independence from client builds)
