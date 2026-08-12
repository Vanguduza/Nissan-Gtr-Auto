# apps/web — Nissan GTR Auto storefront

- Domain (prod): https://nissangtrauto.co.zw
- Lane: `@web_agent`
- Design: AutoDoc-inspired spare-parts chrome + official logo (`public/brand/logo.png`); tokens from `@gtr/ui` (steel / silver / `#C8102E`)
- Decision: `docs/decisions/2026-07-23-storefront-autodoc-logo.md`
- **No browser QR libraries**

```bash
pnpm install
pnpm --filter @gtr/web dev
```

Open http://127.0.0.1:3000

Copy root `.env.example` values into `apps/web/.env.local`:

```
NEXT_PUBLIC_SITE_URL=https://nissangtrauto.co.zw
NEXT_PUBLIC_SUPABASE_URL=...
NEXT_PUBLIC_SUPABASE_ANON_KEY=...
```

### Search (Postgres FTS today; Meilisearch optional)

Production search remains Postgres FTS (`search_catalog`). Optional local Meilisearch CE:

```bash
docker compose -f docker-compose.satellites.yml --profile search up -d
```

Meili is Edge-proxied (`catalog-search-meili`); never `NEXT_PUBLIC_MEILI_*`. Sync via `python -m data_pipeline.meili_sync --full`. See `infra/satellites/README.md` and `docs/decisions/2026-08-05-meilisearch-catalog-search.md`.

Route groups: `(storefront)`, `(my-garage)`, `(b2b)`, `(supplier)`, `(auth)`, `(staff)`.

### Staff live delivery map

- Route: `/staff/logistics/tracking` (admin / warehouse / dispatcher via existing RLS)
- Library: **MapLibre GL JS** (`maplibre-gl`)
- Env: `NEXT_PUBLIC_MAP_STYLE_URL` — MapLibre style JSON URL. If unset, staff live map uses keyless CARTO Positron (`https://basemaps.cartocdn.com/gl/positron-gl-style/style.json`) centered on Harare, with inline CARTO raster fallback if the GL style fails.
- Behavior: **subscribe-only** to Supabase Realtime `delivery_locations` filtered by selected `delivery_job_id`. No `navigator.geolocation` / HTML5 GPS / browser QR.
- B7: responsive at **800px** (shell) / **640px** (geo grid + map height); ETA shows honest `eta_source=osrm` vs `eta_source=google_directions (deprecated)` — see `docs/plans/2026-08-12-epic-b7-staff-web-tracking-dod.md`.

Demo with Realtime:

1. Staff creates a delivery job under `/staff/logistics`.
2. Driver (management Android + GPS bridge) starts tracking → `ingest_delivery_location`.
3. Open `/staff/logistics/tracking`, select the job — marker/trail updates on INSERTs.

### Phase 8b RFQ / supplier quotations

Thin auth-gated portal (anon client + session; RLS enforces staff vs supplier):

| Role | Routes | Actions |
|------|--------|---------|
| Staff | `/procurement`, `/procurement/rfqs`, `/procurement/rfqs/new`, `/procurement/rfqs/[id]` | `create_rfq`, `submit_rfq`, `award_quotation_to_po` |
| Supplier | `/supplier`, `/supplier/rfqs`, `/supplier/rfqs/[id]` | list invited RFQs; `upsert_supplier_quotation`, `submit_supplier_quotation` |

Blanket PO UI is not in this slice.
