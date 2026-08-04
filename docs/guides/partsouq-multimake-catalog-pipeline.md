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

Default production path today is **English Nissan** on PartSouq. Other makes reuse the same tools with config/path changes; some VIN/OEM helpers are still Nissan-oriented (see §8).

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
3. Watcher parses new cache, builds `vehicle_identity`, backfills, refreshes the bundle — **while scrape continues**.
4. When crawl exits, stop **only** that maker’s watcher PID, run `--once` catch-up, then optional `--transform-only` / `--import-dry-run` / `--live-import` / diagram flags.

### Parallelism / resource cost

| Mode | Flag | Behavior | Cost |
|------|------|----------|------|
| **Default (recommended)** | `--parallel-makers 1` | Makers queued one-at-a-time; within each, scrape + parse concurrent | One FlareSolverr session family; steadier CF / disk |
| **All-makes-at-once** | `--parallel-makers N` | Up to N makers scrape+parse simultaneously | N× sessions, CPU, disk, CF risk — use sparingly |

Full `--makers all` is **long-running** (hours–days depending on PartSouq size and pacing). **VIN enrichment runs for every maker** via `config/chassis_catalogs.json` (curated platforms + per-brand EPC stubs — §3). Supabase import is optional (`--import-dry-run` / `--live-import`).

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
| **Transform** | `scraped_data` → `vehicle_master` / `pnc_categories` / `part_fitment` / `diagram_assets` |
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

Do **not** treat “PENDING≈0” alone as done. Competitive catalog jobs gate on coverage:

1. Crawl: `PENDING=0`, `PROCESSING=0`; `BLOCKED_CF` / hard fails **cleared or explicitly accepted**.
2. Vehicles: distinct visited `/vehicle` **vids** ≈ distinct enqueued vids (ssd-aware); every visited vehicle has `vehicle_identity`.
3. Parse: `parse_queue` caught up (no PENDING/ERROR backlog); catch-up backfill run.
4. VIN policy: every identity has `vin_prefix` (curated or epc_stub) for the maker; upgrade stubs via `chassis_discovery` when desired; final bundle refresh.
5. Bundle: fitments have bbox + `diagram_path` for imported scope; spot-check `search_catalog` **part | vin | model | pnc**.

### Ops follow-ons (align further with common practice)

| Item | Status | Notes |
|------|--------|--------|
| Hybrid priority | **Live** | `queue_mode: hybrid` |
| Politeness + FS session | **Live** | concurrency 1, persist + keepalive, watchdog |
| Crawl / parse split + cache | **Live** | `--crawl-only` + watcher |
| **ssd / vid dedup** | Follow-on | Prefer one PENDING `/vehicle` URL per distinct `vid` |
| **FS session recycle** | Ops / follow-on | Destroy+recreate session after N requests or repeated 500s (watchdog already covers some crashes) |
| Unmapped chassis | **Live** | `python -m data_pipeline.chassis_discovery --list` / `--export-stubs` — never invent prefixes |

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
- Periodically rewrites the catalog JSON bundle

### Identity path (`/vehicle` pages)

1. Detect vehicle URL/HTML (`…/vehicle`, cells `data-title="Model"` / `Name`).
2. Parse → `{ vid, chassis_code, model_variant, grade, market, year, engine_code, … }`.
3. `enrich_hints_with_vin` → add `vin_prefix` from brand catalog (curated) or **epc_stub**; note non-curated codes via **chassis discovery** for later upgrade (never invent full ISO VINs).
4. Upsert into `vehicle_identity` keyed by **vid**.
5. **Backfill** existing `scraped_data` rows for that vid (stamp chassis / vin_prefix onto `vehicle` + `vehicle_context`).

Under **`queue_mode: hybrid`**, the crawl prioritizes these `/vehicle` pages while any remain `PENDING`, so identity coverage can catch up without waiting for every L5 parts URL to finish first (see §2b).

### Parts / hotspot path (parts pages)

1. Parse diagram sections: `.lable-single` hotspots (`data-position`, `data-size`) + `part-search-tr` OEM rows.
2. Attach `vid` from query string; merge page meta + known `vehicle_identity`.
3. Replace `scraped_data` for that URL; later transform emits fitments with bbox + `diagram_path`.

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

# FlareSolverr (repo root)
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
python -m data_pipeline.cache_parse_worker --watch --poll-seconds 30 --write-bundle-every 50 \
  --state-db crawler_state.db \
  --cache-dir out/partsouq_cache \
  --parse-db out/cache_parse_state.db \
  --out-dir out/partsouq_bundle
```

| Flag | Meaning |
|------|---------|
| `--once` | Single pass (default if neither `--once` nor `--watch`) |
| `--watch` | Loop forever; poll for new/changed cache |
| `--poll-seconds` | Sleep between passes (blessed runbook: 30) |
| `--no-catch-up` | Skip start-of-pass `backfill_all_identities` |
| `--limit N` | Cap pages parsed this pass |
| `--write-bundle-every N` | Rewrite JSON every N pages (blessed: 50; `0` = end of pass only) |

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

```bash
# From fixture or scrape bundle directory
python -m data_pipeline.import_catalog out/partsouq_bundle
python -m data_pipeline.import_catalog out/partsouq_bundle --live

# Or via auto pipeline after transform
python -m data_pipeline.amayama_catalog_auto --transform-only --import-dry-run
python -m data_pipeline.amayama_catalog_auto --transform-only --live-import \
  --download-diagrams --upload-diagrams
```

Idempotency keys: see `data-pipeline/README.md` (vehicle / PNC / fitment / OE cross-ref).

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
| **Live** | FlareSolverr crawl, brand-scoped queue, HTML cache, parallel `cache_parse_worker` (vid → chassis → vin_prefix + backfill + catch-up inside watcher), hotspot bbox → `part_fitment`, bundle refresh, dry-run/live import, PG FTS `search_catalog`, diagram Storage upload path, **multi-make orchestrator** (`partsouq_catalog_orchestrator`), **`queue_mode: hybrid`** (§2b), **multi-make VIN maps** (`config/chassis_catalogs.json` + epc_stub) |
| **Live for Nissan** | Dense curated `CHASSIS_CATALOG` platforms; chassis auto-discovery for upgrades; fixture packs (e.g. Navara D40) |
| **Live for other makes** | Brand WMI + epc_stub garage keys for every chassis seen; curated seeds for Toyota/Honda/BMW platforms; denser curation over time |
| **Configure now, incomplete enrichment** | OEM normalizer still Nissan-shaped; DB has no `manufacturer` column — isolate by maker paths |
| **Follow-on** | Prefer one `/vehicle` URL per distinct `vid` (ssd-variant dedup); FlareSolverr session recycle on repeated 500s; stronger automated coverage reports (§2b ops table); expand curated chassis rows per make |
| **Aspirational** | OEM format plugins; DB `manufacturer`; import filters; Meilisearch dual-write |
| **Out of scope** | ZIMRA, payroll tax, HTML5 QR, browser hardware bridges |

**Vid-mapping code:** present when this guide was written — `parse_partsouq_vehicle_html` / `vid_from_url` in `data_pipeline/parse_partsouq_html.py`, and `vehicle_identity` + backfill in `data_pipeline/cache_parse_worker.py` (covered by `tests/test_vehicle_identity.py`). Hybrid claim tests: `tests/test_until_complete.py` (`test_claim_hybrid_*`).

---

## 9. Related docs

- [Phase 7 plan](../plans/2026-07-24-phase7-data-pipeline-search.md)
- [Interim PG FTS ADR](../decisions/2026-07-24-search-index-interim-pg-fts.md)
- [data-pipeline/README.md](../../data-pipeline/README.md) — install, scrape flags, watchdog
- Repo laws: `AGENTS.md` / `.cursorrules` (exclusions; pipeline independence from client builds)
