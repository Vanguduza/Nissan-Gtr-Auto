# Megazip multivehicle EPC catalog pipeline

EPC-first, PCdb additive, **re-runnable without re-crawl**. Hierarchy:

```text
Maker → Models (A–Z) → Variants → Sections → Diagram + hotspots → Parts (+ stock overlay)
```

Search: `search_catalog`. Browse: `list_catalog_*`, `get_catalog_diagram`.

| Entry | Path |
|-------|------|
| Orchestrator | `python -m data_pipeline.megazip_catalog_orchestrator` |
| Wrapper | `python scripts/megazip_multivehicle_catalog.py` |
| Maker order | `config/megazip_makers.json` — **Nissan → Toyota → Honda → Mazda → …** |
| Decision (engines) | `docs/decisions/2026-08-11-megazip-engine-code-cascade.md` |

**Hard rule:** Supabase catalog must never store the vendor string `megazip` in `source`, URLs, or storage paths. Use `source=epc` and `epc/<maker_slug>/…` prefixes.

---

## Lessons from Nissan → required for every maker

These failures showed up on the Nissan live catalog. Treat them as **blocking** for Toyota/Honda/… imports.

| # | Issue | Prevention (code + ops) |
|---|--------|-------------------------|
| 1 | Cascade “No engine codes in catalog” | Parse `Engine`/`двигатель`; transform writes `vehicle_master.engine_code`; check `quality_report` / `megazip_post_import_verify.py` |
| 2 | `model_variant` mismatch after backfill | Always `{MakerName} {catalog_models.display_name}` |
| 3 | Diagram-before-variant SQLite order dropped engines | Transform sorts page types before build |
| 4 | Vendor URLs/`megazip/` paths in SoR | Transform scrub + **import** `sanitize_hierarchy_vendor_leakage` + REST scrub script |
| 5 | `catalog_makers.source` flipped to `megazip` | Force `epc` on transform/import/scrub |
| 6 | Column rename `megazip_*` → `external_*` not applied | Import dual-maps both; apply migration when `DATABASE_URL` available |
| 7 | MAIN idle after priority drain | Nissan two-phase `auto_start_remaining` / `--all-models` ensure |
| 8 | Huge PENDING backlog | Model-scoped workers (`megazip_crawl_worker`) with leases |
| 9 | Parser upgrade without re-parse | `--skip-crawl --phase parse,transform,…` or `extract_engines_from_cache.py` |
| 10 | Missing cache HTML ⇒ missing engines | Re-crawl that model; verify coverage before “done” |

---

## Phases (re-run without re-crawl)

| Phase | Network? | Re-runnable | Purpose |
|-------|----------|-------------|---------|
| `crawl` | Yes | Skip with `--skip-crawl` | Cache HTML under `out/megazip/<slug>/cache/` |
| `parse` | No | Yes | Re-parse cache → SQLite `parsed_pages` |
| `transform` | No | Yes | Hierarchy JSON; scrub vendor URLs; engines → `vehicle_master` |
| `pcdb` | No | Yes | Additive `pcdb_part_type_id` |
| `filter` | No | Yes | `--complete-only` + `quality_report.json` (includes engine coverage) |
| `upload` | Yes | Yes | PNGs → local `diagrams/` + Storage `catalog-diagrams` under `epc/` |
| `import` | Yes | Yes | Hierarchy + fitment; **vendor sanitize on upsert** |

```bash
cd data-pipeline

# Smoke one maker
python -m data_pipeline.megazip_catalog_orchestrator \
  --makers Toyota --max-pages 80 --no-nissan-two-phase

# Production maker (after crawl cache is warm)
python -m data_pipeline.megazip_catalog_orchestrator \
  --makers Toyota --skip-crawl \
  --phase parse,transform,pcdb,filter,upload,import \
  --live-import --complete-only

# All makers sequentially
python -m data_pipeline.megazip_catalog_orchestrator \
  --makers all --live-import --complete-only --prune-stale
```

---

## Standard per-maker checklist

Copy for **Toyota / Honda / Mazda / …**:

### A. Crawl

```powershell
# MAIN (or first process) — seed hub + drain
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Toyota --no-nissan-two-phase `
  --phase crawl --out-root out/megazip

# Optional parallel workers (lease-safe; one model_slug each)
python -m data_pipeline.megazip_crawl_worker `
  --maker Toyota --models camry-vista-aurion-42430 `
  --worker-id toyota-camry --out-root out/megazip --rate-limit 0.55
```

Nissan-only extras: default two-phase priority→remaining; `--all-models` underexplored ensure. Other makers use `--no-nissan-two-phase` (or omit Nissan-specific flags).

### B. Parse → transform → gate

```powershell
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Toyota --skip-crawl `
  --phase parse,transform,pcdb,filter `
  --out-root out/megazip
```

Inspect:

- `out/megazip/toyota/bundle/quality_report.json`
  - `publishable`, `variants_publishable`
  - `vehicle_master_with_engine`, `variant_chassis_missing_engine_count`
- `variant_quality.json`

### C. Upload + import

```powershell
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Toyota --skip-crawl `
  --phase upload,import --live-import --complete-only `
  --out-root out/megazip
```

Import always runs `sanitize_hierarchy_vendor_leakage` (null megazip URLs, `megazip/`→`epc/`, `source=epc`). Dual-writes `external_data_id` / `megazip_data_id` depending on live schema.

### D. Verify + scrub leftovers

```powershell
python scripts/megazip_post_import_verify.py --maker-slug toyota
# Optional hard fail on engine gaps:
python scripts/megazip_post_import_verify.py --maker-slug toyota --fail-on-engine-gaps

# If verify reports megazip URLs/paths (legacy rows):
python scripts/scrub_megazip_catalog_values.py
# DDL rename (needs DATABASE_URL):
python scripts/apply_megazip_scrub.py
# Or Dashboard SQL:
#   scripts/scrub_megazip_dashboard.sql
```

### E. Engine backfill while crawl workers hold SQLite

```powershell
python scripts/extract_engines_from_cache.py --maker Toyota --live-import
python scripts/megazip_post_import_verify.py --maker-slug toyota
```

---

## Production workflow

### Maker order

**Nissan → Toyota → Honda → Mazda → Suzuki → …** (`config/megazip_makers.json`)

### Nissan two-phase crawl (default)

When crawling Nissan without `--single-chassis`, `--priority-chassis`, or `--all-models`:

1. **Priority pass** — `config/priority_chassis.json` + model seeds  
2. **Auto remaining** — `prepare_remaining_crawl` when priority PENDING drains  
3. **Post-crawl** — `parse → transform → pcdb → filter → upload`

| Flag | Effect |
|------|--------|
| *(default)* | Nissan two-phase on |
| `--no-nissan-two-phase` | Single pass (use for non-Nissan or explicit single-pass Nissan) |
| `--priority-chassis` | Priority only (no auto remaining) |
| `--all-models` | No priority filter; underexplored ensure |
| `--max-pages N` | Cap per pass (smoke only) |

### Parallel workers

```text
python -m data_pipeline.megazip_crawl_worker --models <slug> --worker-id <id> --out-root out/megazip
```

Leases live in `megazip_state.db` (`worker_leases`). MAIN uses `exclude_leased=True`. Do not run long `backfill_megazip_engine_codes.py` reparse-writes against the same DB while workers crawl — prefer `extract_engines_from_cache.py` (read-only URI).

### Two-tier publish model

| Gate | Field | Drives import? |
|------|-------|----------------|
| **Variant-level** | `variant_quality.json` → `publishable` | **Yes** with `--complete-only` |
| **Maker-level** | `quality_report.json` → `maker_publishable` | Advisory |

`--strict-gate` checks variant-level `publishable`.

---

## Engine codes (vehicle cascade) — all makers

Storefront cascade reads **`vehicle_master.engine_code`**. Empty ⇒ UI “No engine codes in catalog”.

| Page | Attr | Typical |
|------|------|---------|
| Variant list | `Engine` / `двигатель` | Toyota-style tech rows |
| Diagram identity | `Engine` / `двигатель` | Nissan-style primary |

Transform: page order hub→variants→sections→diagrams; engines attach to **variant chassis** (generation), label `{Maker} {display_name}`.

### Pitfalls

- Never invent alternate `model_variant` strings on backfill  
- Multi-engine chassis → multiple `vehicle_master` rows; leave variant `engine_code` empty if ambiguous  
- Missing HTML on disk ⇒ no engine until re-fetch  

---

## Vendor scrub (no `megazip` in SoR)

| Layer | Behavior |
|-------|----------|
| Transform | `_public_catalog_url` nulls megazip hosts; storage prefix `epc/{slug}` |
| Import | `sanitize_hierarchy_vendor_leakage` before upsert |
| Live repair | `scripts/scrub_megazip_catalog_values.py` (URLs + `megazip/`→`epc/` paths) |
| DDL | Migration `20260809120000_scrub_megazip_from_catalog.sql` / `apply_megazip_scrub.py` / Dashboard SQL |

Columns: prefer `external_data_id` / `external_item_id`; import still maps legacy `megazip_data_id` / `megazip_item_id` until rename ships.

---

## Per-source chassis registry

`config/megazip_chassis_map.json` supplements `priority_chassis.json` (Nissan priority platforms). Non-Nissan makers usually crawl from maker hub without priority chassis.

---

## Diagram kinds and quality gates

| Kind | Quality gate |
|------|--------------|
| `exploded_diagram` | ≥5 hotspots + ≥3 OEM parts with bbox |
| `parts_list_raster` | Table OEM rows |
| `ambiguous` | Table required; `publish_diagram: false` |

---

## Output layout

```text
out/megazip/
  manifest.json
  nissan/   megazip_state.db  cache/  bundle/  diagrams/
  toyota/
  honda/
  ...
```

Bundle meta includes `vehicles` / `vehicles_with_engine`.

---

## Recommended overnight (Nissan)

```powershell
cd data-pipeline
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Nissan `
  --phase crawl,parse,transform,pcdb,filter,upload `
  --out-root out/megazip

python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Nissan --skip-crawl `
  --phase filter,import --live-import --complete-only

python scripts/megazip_post_import_verify.py --maker-slug nissan
```

## Recommended next maker (Toyota)

```powershell
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Toyota --no-nissan-two-phase `
  --phase crawl,parse,transform,pcdb,filter,upload,import `
  --live-import --complete-only --out-root out/megazip

python scripts/megazip_post_import_verify.py --maker-slug toyota
python scripts/scrub_megazip_catalog_values.py   # if verify finds leakage
```

---

## Helper scripts

| Script | Role |
|--------|------|
| `megazip_crawl_worker.py` | Lease-scoped parallel crawl |
| `extract_engines_from_cache.py` | Engine backfill without SQLite write lock |
| `backfill_megazip_engine_codes.py` | Reparse+transform (avoid vs busy workers) |
| `megazip_post_import_verify.py` | Post-import gate (vendor + engines + RPC) |
| `scrub_megazip_catalog_values.py` | Live REST scrub |
| `apply_megazip_scrub.py` / `scrub_megazip_dashboard.sql` | Path rewrite + column rename |
| `megazip_midcrawl_live_import.py` | Mid-crawl hierarchy import |

---

## Related

- [vehicle-cascade-and-epc-browse.md](./vehicle-cascade-and-epc-browse.md) — cascade contract  
- [partsouq-multimake-catalog-pipeline.md](./partsouq-multimake-catalog-pipeline.md) — crawl practice, ACES/PCdb  
- [erp-catalog-v1-load.md](./erp-catalog-v1-load.md) — Supabase import patterns  
