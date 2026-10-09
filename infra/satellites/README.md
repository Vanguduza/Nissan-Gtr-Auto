# Satellite services (Meilisearch, optional Traccar / OSRM / MapLibre tiles)

Compose file for OSS satellites recommended in
[`docs/plans/2026-08-02-open-source-erp-toolkit-audit.md`](../docs/plans/2026-08-02-open-source-erp-toolkit-audit.md).

**Does not replace Supabase.** Postgres + Auth + RLS remain the system of record.
No ERPNext, no ZIMRA, no payroll tax. MapLibre remains client render SoR — `maptiles` only hosts style/tiles.

## Quick start (Meilisearch only — default profile)

From repo root:

```bash
docker compose -f docker-compose.satellites.yml --profile search up -d
```

Dashboard / API: http://127.0.0.1:7700  
Default master key (dev only): see `MEILI_MASTER_KEY` below.

Stop:

```bash
docker compose -f docker-compose.satellites.yml --profile search down
```

## Profiles

| Profile | Services | When |
|---------|----------|------|
| `search` (default) | Meilisearch CE | Parts / SKU typo-tolerant search |
| `gps` | Traccar | Fleet live GPS (optional; heavy Java image) |
| `scrape` | FlareSolverr | Cloudflare solve for Amayama catalog crawl |
| `routing` | OSRM (`osrm-routed`) | After `bash infra/satellites/osrm/prepare.sh` — see OSRM section |
| `maptiles` | tileserver-gl | After `bash infra/satellites/maptiles/prepare.sh` — MapLibre style URL |
| `recommend` | Gorse (commented) | Phase C — see `PHASE2_PROPHET_GORSE.md` |

Enable multiple:

```bash
docker compose -f docker-compose.satellites.yml --profile search --profile gps up -d
docker compose -f docker-compose.satellites.yml --profile scrape up -d
docker compose -f docker-compose.satellites.yml --profile routing up -d
docker compose -f docker-compose.satellites.yml --profile maptiles up -d
```

FlareSolverr API: http://127.0.0.1:8191 — used by `python -m data_pipeline.amayama_catalog_auto`.  
OSRM API: http://127.0.0.1:5000 (after prepare) — set `OSRM_URL` for delivery clients.  
MapLibre style: http://127.0.0.1:8081/styles/basic-preview/style.json (after prepare) — set `NEXT_PUBLIC_MAP_STYLE_URL` / `MAPLIBRE_STYLE_URL`.

**Do not** run `gps` + `routing` together without remapping `OSRM_HOST_PORT` (Traccar binds host 5000–5150).

## Environment

Copy into root `.env` / `apps/web/.env.local` as needed (never commit real keys):

```bash
# Meilisearch (Community Edition — MIT)
MEILI_HOST=http://127.0.0.1:7700
MEILI_MASTER_KEY=gtr_dev_meili_master_change_me
# Browser-safe search key is created after first boot (see below); keep master key server-only
NEXT_PUBLIC_MEILI_HOST=http://127.0.0.1:7700
NEXT_PUBLIC_MEILI_SEARCH_KEY=

# Traccar (optional)
TRACCAR_URL=http://127.0.0.1:8082
# Admin UI on first boot — set password in Traccar UI; map devices to GTR fleet entities later

# OSRM (optional — not started by default; needs OSM PBF + preprocess)
OSRM_URL=http://127.0.0.1:5000

# MapLibre tiles (optional — needs Planetiler prepare; see maptiles/README.md)
NEXT_PUBLIC_MAP_STYLE_URL=http://127.0.0.1:8081/styles/basic-preview/style.json
# MAPLIBRE_STYLE_URL=http://10.0.2.2:8081/styles/basic-preview/style.json  # Android emulator
```

### Create a Meilisearch search-only key (after first boot)

```bash
curl -X POST "http://127.0.0.1:7700/keys" \
  -H "Authorization: Bearer gtr_dev_meili_master_change_me" \
  -H "Content-Type: application/json" \
  --data "{\"description\":\"web search\",\"actions\":[\"search\"],\"indexes\":[\"parts\"],\"expiresAt\":null}"
```

Put the returned `key` into `MEILI_SEARCH_KEY` for the Edge Function `catalog-search-meili` and local sync smoke tests. **Do not** expose via `NEXT_PUBLIC_*`. Master key is for index setup/sync only.

## Indexer (Meilisearch catalog sync)

Supabase catalog tables remain source of truth. Meili holds a derived `parts` index.

### 1. Start Meilisearch

```bash
docker compose -f docker-compose.satellites.yml --profile search up -d
```

### 2. Create search-only key (once per environment)

```bash
curl -s -X POST "http://127.0.0.1:7700/keys" \
  -H "Authorization: Bearer gtr_dev_meili_master_change_me" \
  -H "Content-Type: application/json" \
  --data '{"description":"edge search","actions":["search"],"indexes":["parts"],"expiresAt":null}'
```

Save the returned `key` as `MEILI_SEARCH_KEY` (Edge secrets + local `.env`).

### 3. Full sync from Supabase

Requires `pip install -e ".[supabase]"` in `data-pipeline/` and applied migration `20260805190000_catalog_meili_sync_state.sql`.

```bash
export SUPABASE_URL=http://127.0.0.1:54321
export SUPABASE_SERVICE_KEY=<service_role>
export MEILI_HOST=http://127.0.0.1:7700
export MEILI_MASTER_KEY=gtr_dev_meili_master_change_me

cd data-pipeline
python -m data_pipeline.meili_sync --full
# dry-run document counts only:
python -m data_pipeline.meili_sync --dry-run
```

Updates singleton `catalog_meili_sync_state` (staff-readable via RLS).

### 4. Search proxy (clients)

Edge Function `catalog-search-meili` — authenticated JWT in, Meili search-only key server-side.

```bash
curl -s -X POST "$SUPABASE_URL/functions/v1/catalog-search-meili" \
  -H "Authorization: Bearer $USER_JWT" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -d '{"mode":"part","query":"15208","limit":10}'
```

Set Edge secrets: `MEILI_HOST`, `MEILI_SEARCH_KEY`. Optional `CATALOG_SEARCH_BACKEND=fts` forces Postgres FTS fallback (`search_catalog` RPC).

Typed client: `searchCatalogMeili()` in `@gtr/supabase-client`. ADR: `docs/decisions/2026-08-05-meilisearch-catalog-search.md`.

### Rollout notes

- Run sync after each catalog import batch.
- FTS (`search_catalog`) stays fallback until Meili is populated and verified.
- Use **Community Edition** features only (no BUSL Enterprise).

## Traccar notes

- Apache-2.0 GPS platform. Wire delivery Android / fleet via REST/WS later; dispatch UI stays in GTR.
- Bridge-First still applies for on-device GPS in GTR apps (`bridges/`) — Traccar is the **server** that receives device positions.
- Default compose uses the official image with embedded H2 for local smoke tests only — use Postgres for anything shared.

## OSRM notes (H6 / B-OSRM-1)

OSRM is the **distance/route SoR** when `OSRM_URL` is configured. Clients already prefer it (`@gtr/delivery` `preferRoutingProvider`, Android `OsrmRouteFetcher`).

1. Prepare graph (Docker required; downloads Geofabrik Zimbabwe by default):

```bash
bash infra/satellites/osrm/prepare.sh
```

2. Start routed:

```bash
docker compose -f docker-compose.satellites.yml --profile routing up -d
```

3. Point apps: `OSRM_URL=http://127.0.0.1:5000` (emulator: `http://10.0.2.2:5000`).

Full detail: [`osrm/README.md`](./osrm/README.md). Until the graph exists, leave `OSRM_URL` unset — clients fall back to haversine / deprecated Google Directions. Do not use unlicensed public routing APIs in production.

## MapLibre tiles notes (basemap self-host)

MapLibre is the **client render SoR**. This satellite hosts style + vector tiles so apps are not stuck on demotiles / keyed cloud URLs only.

1. Prepare MBTiles (Docker + Planetiler for Zimbabwe, or `MAPTILES_SMOKE=1` for a small prebuilt sample):

```bash
MAPTILES_SMOKE=1 bash infra/satellites/maptiles/prepare.sh   # fast server smoke
# bash infra/satellites/maptiles/prepare.sh                 # Zimbabwe (large first download)
```

2. Start tileserver-gl:

```bash
docker compose -f docker-compose.satellites.yml --profile maptiles up -d
```

3. Point apps: `NEXT_PUBLIC_MAP_STYLE_URL=http://127.0.0.1:8081/styles/basic-preview/style.json` (emulator: `http://10.0.2.2:8081/...`).

Full detail: [`maptiles/README.md`](./maptiles/README.md) + Discovery [`docs/plans/2026-08-15-maptiles-satellite.md`](../../docs/plans/2026-08-15-maptiles-satellite.md). Until tiles exist, leave style env unset — web uses keyless CARTO Positron; native uses demotiles last resort.

## Phase-2 / Phase C libraries (not in default compose)

| Tool | Role | Status |
|------|------|--------|
| **Casbin** (`casbin` / `node-casbin`) | Fine-grained RBAC at API layer | Documented only — **do not** replace Supabase RLS. Optional future npm dep for edge/BFF policy checks that *complement* RLS. |
| **Gorse** | Cross-sell recommender (Apache-2.0) | Phase C scaffold — commented `recommend` profile; see `PHASE2_PROPHET_GORSE.md` + `PHASE2_CASBIN_GORSE.md`. |
| **StatsForecast / Prophet** | Tier-A demand forecast satellite | Phase C stub in `data-pipeline` (`forecast_statsforecast`); optional `.[forecast]` extra. |

See `PHASE2_CASBIN_GORSE.md` and `PHASE2_PROPHET_GORSE.md`.

## Security

- Change `MEILI_MASTER_KEY` before any shared/dev-team use.
- Do not expose Meili master key via `NEXT_PUBLIC_*`.
- Do not put service-role Supabase keys into satellite containers.
