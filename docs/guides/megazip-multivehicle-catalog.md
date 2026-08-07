# Megazip multivehicle EPC catalog pipeline

EPC-first, PCdb additive, **re-runnable without re-crawl**. Implements Megazip-style hierarchy:

```text
Maker → Models (A–Z) → Variants → Sections → Diagram + hotspots → Parts (+ stock overlay)
```

Search remains via `search_catalog`; hierarchy browse via new RPCs (`list_catalog_*`, `get_catalog_diagram`).

**Module:** `python -m data_pipeline.megazip_catalog_orchestrator`  
**Wrapper:** `python scripts/megazip_multivehicle_catalog.py`  
**Maker order:** `config/megazip_makers.json` — **Nissan → Toyota → Honda → Mazda → …**

---

## Phases (re-run without re-crawl)

| Phase | Network? | Re-runnable | Purpose |
|-------|----------|-------------|---------|
| `crawl` | Yes | Skip with `--skip-crawl` | Cache HTML under `out/megazip/<slug>/cache/` |
| `parse` | No | Yes | Re-parse cache → SQLite `parsed_pages` |
| `transform` | No | Yes | Build hierarchy JSON bundle |
| `pcdb` | No | Yes | Additive `pcdb_part_type_id` from `config/epc_to_pcdb.json` |
| `filter` | No | Yes | `--complete-only` + `quality_report.json` |
| `upload` | Yes | Yes | Download **all** diagram PNGs to `diagrams/` |
| `import` | Yes (Supabase) | Yes | Hierarchy tables + fitment + `stock_items` |

```bash
cd data-pipeline

# Production Nissan (two-phase crawl, no page cap — run overnight)
python -m data_pipeline.megazip_catalog_orchestrator \
  --makers Nissan \
  --phase crawl,parse,transform,pcdb,filter,upload

# Smoke: priority chassis only, bounded pages
python -m data_pipeline.megazip_catalog_orchestrator \
  --makers Nissan --priority-chassis --no-nissan-two-phase --max-pages 50

# Re-run enrich + import from cache (no Megazip fetch)
python -m data_pipeline.megazip_catalog_orchestrator \
  --makers Nissan --skip-crawl \
  --phase parse,transform,pcdb,filter,import \
  --live-import --complete-only

# All makers sequentially (production VM)
python -m data_pipeline.megazip_catalog_orchestrator \
  --makers all --live-import --complete-only --prune-stale
```

---

## Production workflow

### Maker order

Orchestrator processes makers top-to-bottom from `config/megazip_makers.json`:

**Nissan → Toyota → Honda → Mazda → Suzuki → …**

### Nissan two-phase crawl (default)

When crawling Nissan without `--single-chassis`, `--priority-chassis`, or `--all-models`:

1. **Phase A (`nissan-priority`)** — all codes in `config/priority_chassis.json` + model seeds
2. **Phase B (`nissan-remaining`)** — drain queue for remaining Nissan models (no priority filter)
3. **Post-crawl** — single `parse → transform → pcdb → filter → upload` pass from shared cache

Flags:

| Flag | Effect |
|------|--------|
| *(default)* | Nissan two-phase on |
| `--no-nissan-two-phase` | Single pass, no automatic priority-then-all |
| `--priority-chassis` | Phase A only (priority codes) |
| `--all-models` | Phase B only (no priority filter) |
| `--max-pages N` | Cap pages **per crawl pass** (smoke only; omit for production) |

### Two-tier publish model

| Gate | Field | Drives import? |
|------|-------|----------------|
| **Variant-level** | `variant_quality.json` → `publishable` | **Yes** — `--live-import --complete-only` imports variant-complete records |
| **Maker-level** | `quality_report.json` → `maker_publishable` | **Advisory** — strict all-diagrams score; does not block import |

`--strict-gate` checks variant-level `publishable` (≥1 publishable variant, zero uncategorized PNCs).

---

## Per-source chassis registry

`config/megazip_chassis_map.json` supplements `priority_chassis.json`:

```json
{
  "chassis": {
    "T31": {
      "megazip_available": true,
      "model_seed": "https://www.megazip.net/zapchasti-dlya-avtomobilej/nissan/x-trail-2064"
    },
    "T32": {
      "megazip_available": false,
      "megazip_proxy": "T31",
      "primary_source": "partsouq"
    }
  }
}
```

- **`megazip_available: false`** — orchestrator warns/skips crawl (`--single-chassis T32`)
- **`megazip_proxy`** — documented fallback chassis on Megazip
- **`model_seed`** — merged with `priority_chassis.json` `model_seed_urls`

Also loaded via `--chassis-map-file`.

---

## Nissan priority chassis

Uses `config/priority_chassis.json` (same 33 platforms as PartSouq pipeline).

**Direct model seeds** (bypass hub BFS for slow-to-reach models):

```json
"model_seed_urls": {
  "nissan": {
    "T32": ["https://www.megazip.net/.../nissan/x-trail-2064"],
    "T31": ["https://www.megazip.net/.../nissan/x-trail-2064"]
  }
}
```

**Single chassis filter:** `--single-chassis T32` must appear in `priority_chassis.json`. **R35/GT-R is not a priority chassis.**

**Megazip catalog note:** X-Trail page lists **T30 and T31 only**; T32 has `megazip_available: false` in chassis map.

---

## Diagram kinds and quality gates

| Kind | Heuristic | Quality gate |
|------|-----------|--------------|
| `exploded_diagram` | Hotspot y-span > 150 | ≥5 hotspots + ≥3 OEM parts with bbox |
| `parts_list_raster` | y-span < 80, x-span > 200 | HTML table rows with OEM (bbox optional) |
| `ambiguous` | Else | **Table required, hotspots optional**; `publish_diagram: false`, `needs_review: true` |

Ambiguous diagrams with complete companion tables **do not fail** variant or import gates.

---

## Upload phase

Downloads **all** diagram PNGs from the filtered bundle:

- Sources: `diagram_assets` + `catalog_diagrams` (`image_url`)
- Dedupe by filename and content hash under `out/megazip/<maker>/diagrams/`

---

## Output layout

```text
out/megazip/
  manifest.json
  nissan/
    megazip_state.db
    cache/*.html
    bundle/
      quality_report.json      # maker_publishable (advisory) + publishable (import)
      variant_quality.json     # per-variant publishable
      ...
    diagrams/
    meta.json
  toyota/
  ...
```

---

## Publish gates (§2c)

Live import (`--live-import --complete-only`, default `--strict-gate`):

- **Variant gate:** ≥1 passing `exploded_diagram` per imported variant
- Fitments: diagram-kind aware (exploded needs bbox; raster/ambiguous need table OEM)
- `parts_list_raster` / `ambiguous`: `publish_diagram: false` in bundle
- `uncategorized_pncs = 0`
- `quality_report.json`: `publishable` (variant-level), `maker_publishable` (advisory)

---

## Recommended overnight command

```powershell
cd data-pipeline

# Full Nissan priority + remaining, all phases, no page cap
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Nissan `
  --phase crawl,parse,transform,pcdb,filter,upload `
  --out-root out/megazip

# Then review variant_quality.json before live import:
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Nissan --skip-crawl `
  --phase filter,import --live-import --complete-only `
  --out-root out/megazip
```

Multimaker production (after Nissan validates):

```powershell
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers all `
  --phase crawl,parse,transform,pcdb,filter,upload `
  --out-root out/megazip
```

---

## Related

- [partsouq-multimake-catalog-pipeline.md](./partsouq-multimake-catalog-pipeline.md) — crawl practice, ACES/PCdb
- [erp-catalog-v1-load.md](./erp-catalog-v1-load.md) — Supabase import patterns
