# ERP catalog v1 — load into Supabase

Reload the curated test pack at `data-pipeline/out/erp_catalog_v1/` into Postgres (SoR), optionally seed `stock_items`, then sync Meilisearch.

## Bundle counts

| File | Rows | SoR target |
|------|------|------------|
| `vehicle_master.json` | 133 | `public.vehicle_master` |
| `pnc_categories.json` | 4142 | `public.pnc_categories` |
| `part_fitment.json` | 8079 | `public.part_fitment` |
| `diagram_assets.json` | 635 | Storage bucket `catalog-diagrams` (not a table) |

Chassis in pack: **B13, JJ10, K13, S14, Z33**. Sample OEM: **`28970-JD00A`**.

## Prerequisites

```bash
cd data-pipeline
pip install -e ".[supabase]"
# Set SUPABASE_URL + privileged server key in repo-root .env or data-pipeline/.env
# (role key env or SUPABASE_SERVICE_KEY alias — see .env.example)
```

## Commands

```bash
cd data-pipeline

# 1) Validate + dry-run (in-memory; no DB)
python -m data_pipeline.import_catalog out/erp_catalog_v1

# 2) Live idempotent upsert (+ stock_items by default)
python -m data_pipeline.import_catalog out/erp_catalog_v1 --live
# Skip stock seed:  ... --live --no-ensure-stock-items

# 3) Diagram images (Storage only — skip if paths already uploaded)
# Fixture PNGs:  node ../supabase/seed_catalog_diagrams.mjs --docker
# Scraped GIFs:  python -m data_pipeline.amayama_catalog_auto --transform-only --upload-diagrams --out-dir out/erp_catalog_v1

# 4) Meilisearch derived index (Docker Meili must be up)
# export MEILI_HOST=http://127.0.0.1:7700 MEILI_MASTER_KEY=...
python -m data_pipeline.meili_sync --full
```

## Notes

- **Idempotent:** natural keys match unique indexes from `20260724010000_catalog_search_fts.sql`. Live path batches inserts/updates (expression `COALESCE` indexes block PostgREST `on_conflict=<cols>`).
- **`stock_items`:** `--ensure-stock-items` (default on) upserts distinct OEMs for POS/receiving. `search_catalog` FTS works from `part_fitment` without them; Meili sync also reads `stock_items` for descriptions.
- **Diagrams:** JSON holds Storage paths only — no `diagram_assets` table.
