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
# Local Supabase must be up (Docker). Prefer repo script:
pnpm db:start
# Windows: npm `npx supabase` has no win32 binary — install CLI via Scoop
#   scoop bucket add supabase https://github.com/supabase/scoop-bucket.git && scoop install supabase
# or download supabase_windows_amd64.zip from GitHub releases, then:
#   supabase start   # or: docker start existing supabase_* containers
#   supabase status -o json   # → API_URL + privileged key

cd data-pipeline
pip install -e ".[supabase]"
# Gitignored repo-root .env and/or data-pipeline/.env:
#   SUPABASE_URL=http://127.0.0.1:54321
#   SUPABASE_SERVICE_KEY=…   # alias OK; or SUPABASE_SERVICE_ROLE_KEY
# import_catalog loads those files automatically; meili_sync does NOT — export into the shell.
```

## Commands

```bash
cd data-pipeline

# 1) Validate + dry-run (in-memory; no DB)
python -m data_pipeline.import_catalog out/erp_catalog_v1

# 2) Live idempotent upsert (+ stock_items by default)
python -m data_pipeline.import_catalog out/erp_catalog_v1 --live --complete-only
# Skip stock seed:  ... --live --complete-only --no-ensure-stock-items

# 3) Diagram images (Storage only — skip if paths already uploaded)
# Apply migrations first (PartSouq GIFs need image/gif on catalog-diagrams bucket):
#   cd .. && npx supabase migration up
# Fixture PNGs:  node ../supabase/seed_catalog_diagrams.mjs --docker
# Scraped GIFs:  python scripts/republish_erp_catalog.py --upload-diagrams
# Or:           python -m data_pipeline.amayama_catalog_auto --transform-only --download-diagrams --upload-diagrams --out-dir out/erp_catalog_v1

# 4) Meilisearch derived index (Docker Meili must be up — e.g. gtr-meilisearch :7700)
# Bash:  export $(grep -v '^#' .env | xargs)   # or set SUPABASE_* + MEILI_* explicitly
# PowerShell: load .env into process env, then:
python -m data_pipeline.meili_sync --full
# Index UID is `parts`. If Meili is down, skip — production search remains PG FTS `search_catalog`.

# 5) Edge function (Meili search proxy — optional for local; storefront falls back to FTS)
#   cd .. && npx supabase login
#   npx supabase functions deploy catalog-search-meili
```

## Verify

```sql
SELECT 'vehicle_master' t, count(*) FROM vehicle_master
UNION ALL SELECT 'pnc_categories', count(*) FROM pnc_categories
UNION ALL SELECT 'part_fitment', count(*) FROM part_fitment
UNION ALL SELECT 'stock_items', count(*) FROM stock_items;

SELECT * FROM search_catalog('part', '28970-JD00A');
-- POS/capture path: stock_items by oem_part_number (lookupStockItemByOem)
SELECT id, oem_part_number, description FROM stock_items WHERE oem_part_number = '28970-JD00A';
```

## Notes

- **Idempotent:** natural keys match unique indexes from `20260724010000_catalog_search_fts.sql`. Live path batches inserts/updates (expression `COALESCE` indexes block PostgREST `on_conflict=<cols>`).
- **`stock_items`:** `--ensure-stock-items` (default on) upserts distinct OEMs for POS/receiving. `search_catalog` FTS works from `part_fitment` without them; Meili sync also reads `stock_items` for descriptions.
- **Diagrams:** JSON holds Storage paths only — no `diagram_assets` table.
- **Meili sync state:** `catalog_meili_sync_state` may be missing locally (PGRST205 warning); documents still index. Document `id`s use `_` separators (Meili rejects `:`). PG FTS `search_catalog` remains SoT for search until dual-read.
