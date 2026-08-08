# Vehicle cascade + Megazip-style EPC browse

Implementation-grade reference for cloning the Nissan GTR Auto ERP **Select Vehicle** cascade and **Browse EPC** hierarchy in another spares shop system. Describes the **designed** infrastructure (schema, APIs, UI contracts, ingest), not a live DB snapshot.

**Related:** [`erp-catalog-v1-load.md`](./erp-catalog-v1-load.md), [`megazip-multivehicle-catalog.md`](./megazip-multivehicle-catalog.md), plan [`docs/plans/2026-08-07-megazip-epc-browse-integration-prompt.md`](../plans/2026-08-07-megazip-epc-browse-integration-prompt.md).

---

## Dual-entry mental model

| Entry | SoR | Purpose |
|-------|-----|---------|
| **Select Vehicle** | Flat `vehicle_master` | Cascade Maker → Model → Generation → Engine (or VIN). Confirms a fitment vehicle, then drives **search** / stock browse. |
| **Browse EPC** | Hierarchy `catalog_*` | Megazip-style drill-down Maker → Models → Variants → Sections → Diagram + hotspots + parts. |

Do **not** merge the two query layers. Search stays on `search_catalog` / Meili / `part_fitment`; hierarchy browse stays on `list_catalog_*` / `get_catalog_diagram`. They join at **chassis_code** (and optionally OEM / `diagram_path`).

---

## A. Flat SoR / cascade data model

### A.1 Table: `public.vehicle_master`

**Created:** `supabase/migrations/20260723100300_vehicle_catalog.sql`  
**Natural key / FTS:** `supabase/migrations/20260724010000_catalog_search_fts.sql`

| Column | Type | Meaning in cascade |
|--------|------|--------------------|
| `id` | UUID PK | Row id |
| `vin_prefix` | VARCHAR(12) nullable | WMI / VIN stem for identify-by-VIN (match first ≥11 chars of user VIN) |
| `chassis_code` | VARCHAR(32) NOT NULL | **Generation** option (e.g. `T31`, `D40`) |
| `engine_code` | VARCHAR(32) nullable | **Engine** option (e.g. `MR20`, `YD25`) |
| `production_year` | INT nullable | Stored for identity / FTS; **not** a cascade dropdown level |
| `model_variant` | TEXT NOT NULL | **Model** option (often includes brand prefix, e.g. `Nissan X-Trail`, or bare `NAVARA`) |
| `search_vector` | tsvector (generated) | FTS over model/chassis/engine/vin_prefix |
| `created_at` | timestamptz | Audit |

**Natural unique index** (idempotent ingest):

`(COALESCE(vin_prefix,''), chassis_code, COALESCE(engine_code,''), COALESCE(production_year,0), model_variant)`

**RLS:** `SELECT` for `authenticated`; staff write for `admin` / `warehouse` (sales also on write policy for some catalog tables — see migration).

**Related fitment tables (same foundation migration):**

| Table | Role |
|-------|------|
| `pnc_categories` | PNC → category/subcategory (+ later `assembly_group_id`, `catalog_section_path`, `pcdb_part_type_id`) |
| `part_fitment` | OEM ↔ chassis/engine/PNC + optional bbox + `diagram_path` (Storage object path) |
| `stock_items` | Sellable SKUs keyed by `oem_part_number` (POS / price overlay) |

`part_fitment` natural key: `(oem_part_number, COALESCE(chassis_code,''), COALESCE(engine_code,''), COALESCE(pnc_code,''))`.

### A.2 Cascade level mapping (exact)

```text
UI level     → Column / derivation
───────────    ───────────────────
1 Maker      → deriveMaker(row) — NOT a DB column
2 Model      → model_variant (exact trim match)
3 Generation → chassis_code
4 Engine     → engine_code (optional if none exist for that generation)
+ VIN        → user input maxLength 17; resolve needs length ≥ 11 against vin_prefix
```

Years live on hierarchy variants/models (`year_start` / `year_end` / `year_label`), not as Select Vehicle steps.

### A.3 Maker derivation (`deriveMaker`)

**Canonical / fullest implementation (multi-make):** `apps/web/lib/vehicle-catalog.ts` → `VehicleCascade.deriveMaker`.

Order of resolution:

1. **Brand prefix on `model_variant`** — longest-first list `VARIANT_BRAND_PREFIXES` (Nissan, Toyota, Honda, … Mercedes-Benz, etc.), case-insensitive `startsWith`.
2. Else **VIN WMI on `vin_prefix`** — longest-first `VIN_WMI_MAKERS` (e.g. `SJN`/`MNT`/`MDH`/`VSK`/`ADN`/`JN*` → Nissan; `JNK`/`5N3` → Infiniti before shared `JN*`; Toyota/Honda/Mazda/…).
3. Else **bare Nissan model token** regex `NISSAN_MODEL_TOKEN` (e.g. `NAVARA`, `X-TRAIL`, `MICRA`, …) → `"Nissan"`.
4. Else `null` — row does not appear under any maker.

**Parity note:** Android (`apps/android-customer/.../VehicleCatalogModels.kt`) and iOS (`VehicleCatalogModels.swift`) currently only recognize Datsun / Nissan / Infiniti brand prefixes and WMI `JNK` / `JN*`. Web is the multi-make reference; clone shops with multi-make data should port the **web** WMI + brand lists to all clients.

Rows where `deriveMaker` returns `null` are excluded from Maker dropdowns (UI may show: “no identifiable makers”).

### A.4 Shared cascade logic (API names)

| Surface | Load rows | Cascade object | Confirm |
|---------|-----------|----------------|---------|
| Web | `listVehicleMaster(client)` in `apps/web/lib/vehicle-catalog.ts` — `from("vehicle_master").select(...).order(model_variant).limit(500)` | `VehicleCascade` same file | `fromCascade` / `resolveVin` |
| Web UI fields | — | `VehicleCascadeFields` in `apps/web/components/vehicle-cascade-fields.tsx` | `vehicleCascadeCanSubmit` |
| Web shell | — | `VehicleSelector` (`vehicle-selector.tsx`), also `garage-panel.tsx` | Navigate to `/search` |
| Android | `CatalogRpcLive.listVehicleMaster` → same columns, limit 500 | `VehicleCascade` in `VehicleCatalogModels.kt` | Garage / Catalog VMs |
| iOS | `LiveStorefrontApi.listVehicleMaster` / `StorefrontApi` | `VehicleCascade` in `VehicleCatalogModels.swift` | `CatalogScreen` / `GarageScreen` |

**No dedicated cascade RPC** — clients load flat rows once and filter in memory.

### A.5 Filtering rules (options at each level)

Given prior selections, options are **distinct sorted sets from live rows only** — never invent placeholders.

| Level | Filter predicate | Option values |
|-------|------------------|---------------|
| Maker | — | `deriveMaker(r)` non-null |
| Model | `deriveMaker == maker` | `model_variant.trim()` |
| Generation | maker + exact `model_variant` | `chassis_code.trim()` |
| Engine | maker + model + exact `chassis_code` | non-empty `engine_code` |

**Clear-downstream on change:** picking Maker clears Model/Generation/Engine; Model clears Generation/Engine; Generation clears Engine (`pickMaker` / `pickModel` / `pickGeneration` in `vehicle-cascade-fields.tsx`).

**Submit gate (`vehicleCascadeCanSubmit`):**

- VIN path: `vin.trim().length >= 11` (does not require cascade filled).
- Cascade path: maker + model + generation required; if any engines exist for that generation, engine required; if `engines.length === 0`, engine optional (step 4 treated done).

**Confirm (`fromCascade`):** find first matching row; return `SelectedFitmentVehicle` `{ make, model, generation, engine, vinPrefix }`. Miss → form error `"Selection not found in live catalog."`

**VIN (`resolveVin`):** uppercase trim; need ≥11 chars; match row where `vin_prefix` is non-empty and needle (first 11 of VIN) `startsWith(vp)` or `vp.startsWith(needle prefix)`; miss → `"VIN not found in live catalog..."`.

### A.6 UI states (Select Vehicle)

| State | Behavior |
|-------|----------|
| Loading | “Loading vehicles from catalog…” |
| Unauthenticated | Auth gate + link to `/login?next=…` (RLS requires authenticated) |
| Config / fetch error | “Catalog unavailable: {message}” |
| Ready, 0 rows | Note: “No vehicles loaded from catalog yet.” Submit disabled |
| Ready, makers empty | Note about VIN prefixes / brands |
| Form validation error | Inline `role="alert"` under VIN / cascade |
| Disabled selects | Model until maker; generation until model; engine until generation or if no engines |

**Web route:** `/vehicle` (`apps/web/app/(storefront)/vehicle/page.tsx`) — title “Select vehicle”. Homepage focuses four-way search + visual catalog stub; vehicle cascade is a dedicated page (and garage).

**After confirm (web `navigateForVehicle`):**

1. Optionally `lookupVariantByChassis(generation)` → sessionStorage EPC context + “Browse EPC diagrams” link.
2. Navigate search:
   - Full VIN (≥11) → `/search?mode=vin&q=…`
   - Else if `vinPrefix` → `/search?mode=vin&q={prefix}`
   - Else → `/search?mode=model&q={model generation engine}` via `searchQueryForVehicle`

**Android after confirm:** `listCatalogForVehicle(chassis, engine?)` queries `part_fitment` by `chassis_code` (+ optional `engine_code`), then joins `stock_items` / prices / qty (`CatalogRpcLive.listCatalogForVehicle`). Compact label: `model · chassis · engine`.

### A.7 How cascade feeds parts search / fitment

```text
SelectedFitmentVehicle.generation  ==  chassis_code
SelectedFitmentVehicle.engine      ==  engine_code (optional)
         │
         ▼
part_fitment (chassis_code [, engine_code]) → oem_part_number, pnc_code, bbox_*, diagram_path
         │
         ├─► stock_items (oem) → POS / shop PLP / price lists
         ├─► search_catalog / Meili (part | vin | model | pnc modes)
         └─► Storage catalog-diagrams via diagram_path (PDP canvas / EPC hotspots)
```

PNC taxonomy: `pnc_categories.pnc_code`. Supersession: `part_fitment.superseded_by`.

---

## B. Hierarchy / Megazip browse (EPC)

### B.1 Schema (drill-down tree)

**Migrations:**

- `20260807120000_catalog_hierarchy_browse.sql` — tables + browse RPCs
- `20260807130000_catalog_diagram_parts.sql` — companion parts + `diagram_kind` / `hotspot_count`
- `20260807140000_seed_catalog_hierarchy_dev.sql` — Nissan X-Trail T31 + Navara D40 fixture tree

```text
catalog_makers
  └─ catalog_models          (maker_slug, slug) UNIQUE
       └─ catalog_variants   (maker_slug, model_slug, slug) UNIQUE  — chassis_code indexed
            └─ catalog_sections
                 └─ catalog_diagrams
                      └─ catalog_diagram_parts (HTML items-list rows)
```

| Table | Key columns |
|-------|-------------|
| `catalog_makers` | `slug` PK, `name`, `sort_order`, `source` |
| `catalog_models` | `maker_slug`, `slug`, `display_name`, `body_type`, `sort_key`, `year_start`/`year_end`, `source_url` |
| `catalog_variants` | `chassis_code`, `frame`, `grade`, `sales_region`, `year_*` / `year_label`, `engine_code`, `megazip_data_id`, `source_url` |
| `catalog_sections` | `name`, `thumbnail_url`, `sort_order`, `assembly_group_id` |
| `catalog_diagrams` | `title`, `image_url`, `image_width`/`height`, `storage_path`, `diagram_kind`, `hotspot_count` |
| `catalog_diagram_parts` | `itemslist_id`, `callout_ref`, `oem_part_number`, `description`, `quantity`, bbox_*, `diagram_path` |

**RLS:** authenticated `SELECT`; staff `admin`/`warehouse` write — all hierarchy tables.

**Storage:** bucket `catalog-diagrams` (created in `20260724010000_catalog_search_fts.sql`; GIF allowed in `20260806120000`). Paths like `xtrail-t31/15208-oil-filter.png`, `navara-d40/…`. Seed helper: `supabase/seed_catalog_diagrams.mjs`.

### B.2 Browse RPCs (contract)

All `SECURITY INVOKER`, grant `authenticated` + `service_role`:

| RPC | Args | Returns |
|-----|------|---------|
| `list_catalog_makers()` | — | `[{ slug, name, sort_order, model_count }]` |
| `list_catalog_models(p_maker_slug)` | maker | models ordered by `sort_key` |
| `list_catalog_variants(p_maker_slug, p_model_slug)` | | variants by chassis/slug |
| `list_catalog_sections(…, p_variant_slug)` | | sections by `sort_order` |
| `get_catalog_diagram(…, p_section_slug)` | | see below |

**`get_catalog_diagram` payload (after `20260807130000`):**

- `diagram`: null if missing; else slug, title, storage_path, image_url, width/height, diagram_kind, hotspot_count  
  — first diagram for section (`ORDER BY slug LIMIT 1`)
- `hotspots`: from `part_fitment` where `diagram_path = storage_path` AND `bbox_x IS NOT NULL` (normalized 0–1 boxes); may join companion `itemslist_id` / `callout_ref`
- `parts`: fitment rows for that `diagram_path` + PNC names + optional `stock_item_id` / description (limit 500)
- `companion_parts`: `catalog_diagram_parts` for section (Megazip HTML table)
- Empty miss: `{ diagram: null, hotspots: [], parts: [], companion_parts: [] }`

**Typed client (web):** `apps/web/lib/catalog-hierarchy.ts` wrapping RPCs + `lookupVariantByChassis` (direct `catalog_variants` select by `chassis_code`).  
**Shared path helpers:** `packages/shared/src/catalog-navigation.ts` — `catalogPath`, `epcHref`, `parseCatalogParams`, `EPC_CONTEXT_STORAGE_KEY = "gtr:epc-context"`.

### B.3 Megazip UX mapping

Target flow (from guide + integration plan):

```text
Maker → Models (A–Z) → Variants → Sections → Diagram + hotspots → Parts (+ stock overlay)
```

| Megazip concept | GTR implementation |
|-----------------|--------------------|
| Maker list | `list_catalog_makers` → maker grid/list |
| Models A–Z | `sort_key` / `display_name` |
| Variant (chassis/grade/years) | `catalog_variants` cards (chassis + grade + year_label + engine) |
| Assembly / section groups | `catalog_sections` grid |
| Exploded diagram canvas | `EpcDiagramCanvas` / Android Box overlays — **normalized bbox**, not camera/QR |
| Clickable callouts | Hotspots ↔ table row hover sync (`activeOem`) |
| Parts list under diagram | Fitment `parts` + optional companion table; click → PDP `/parts/[oem]` |
| Stock / price | Join `stock_items` in RPC; web adds RETAIL `price_list_items` overlay in `EpcDiagramHub` |
| Back navigation | Breadcrumb (`EpcBreadcrumb`) or stack pop (mobile `goBack`) |

Diagram kinds (pipeline quality): `exploded_diagram`, `parts_list_raster`, `ambiguous` — see `docs/guides/megazip-multivehicle-catalog.md`. Unpublished rasters may yield parts-list-only UI (“Diagram not published…”).

### B.4 Load / pipeline

| Piece | Path |
|-------|------|
| Orchestrator | `python -m data_pipeline.megazip_catalog_orchestrator` |
| Wrapper | `python scripts/megazip_multivehicle_catalog.py` |
| Maker order | `data-pipeline/config/megazip_makers.json` |
| Chassis map | `config/megazip_chassis_map.json` + `priority_chassis.json` |
| Hierarchy import | `data_pipeline/import_hierarchy_catalog.py` → upserts `catalog_*` + legacy `vehicle_master` / `pnc_categories` / `part_fitment` / `diagram_assets` |
| Flat-only pack | `python -m data_pipeline.import_catalog out/erp_catalog_v1` ([erp-catalog-v1-load.md](./erp-catalog-v1-load.md)) |
| Bundle schema | `data-pipeline/schemas/catalog_hierarchy_bundle.schema.json` |

Phases: crawl → parse → transform → pcdb → filter → upload diagrams → import. Re-runnable without re-crawl via `--skip-crawl`.

### B.5 Client surfaces

| Client | Navigation | Screens / modules |
|--------|------------|-------------------|
| **Web** | `/catalog` → `/catalog/[maker]` → `…/[model]` → `…/[variant]` → `…/[section]` | `epc-maker-hub`, `epc-model-hub`, `epc-variant-hub`, `epc-section-hub`, `epc-diagram-hub` + grids/canvas/parts table. Stock PLP moved to `/shop`. Auth gate: `EpcAuthGate`. |
| **Android customer** | Hamburger / catalog “Browse EPC” overlay | `EpcBrowseScreen.kt` — sealed `EpcLevel` stack; RPC via `RpcClient` / `CatalogRpcLive` |
| **Android management** | POS EPC | `PosEpcBrowseScreen.kt` (same stack pattern) |
| **iOS** | Sheet / feature | `EpcBrowseScreen.swift` + `EpcDiagramView`; RPC names in `LiveStorefrontApi` |

**UI states (all clients, typical):** loading → auth (web) → error empty (“EPC unavailable”) → list/diagram. Diagram image missing: canvas caption + parts table still usable. Section with no diagram row: RPC returns null diagram + empty arrays.

### B.6 UX parity goals vs Megazip

Clone checklist for display parity:

1. One level per screen (or clear breadcrumb on web).
2. Models sorted A–Z (`sort_key`).
3. Variant line shows chassis + grade/years/engine.
4. Section grid before diagram (not jumping straight to image).
5. Split layout: diagram left/top + parts table; hotspot ↔ row highlight.
6. OEM click opens part detail; “back to diagram” via stored EPC context when available.
7. Honest empty states when hierarchy or Storage is empty (dev seed exists for T31/D40).

---

## C. Relationship: flat cascade ↔ hierarchy EPC

| Concern | Flat cascade | Hierarchy EPC |
|---------|--------------|---------------|
| Homepage / Select Vehicle | Primary | Link-out when chassis maps |
| Browse EPC / `/catalog` | Not used | Primary |
| Confirmed vehicle search | Yes (`/search`, `listCatalogForVehicle`) | Secondary deep-link |
| Join key | `chassis_code` as Generation | `catalog_variants.chassis_code` |
| Diagram pixels | `part_fitment.diagram_path` | `catalog_diagrams.storage_path` **must equal** fitment path for hotspots |
| Maker source | Derived from rows | Explicit `catalog_makers.slug` (`nissan`, …) |

**Designed bridge (web):** after cascade selection, `lookupVariantByChassis` → `saveEpcContext` → link `catalogPath(ctx)` (“Browse EPC diagrams”). Garage can deep-link the same way.

**Hosted state:** remote DB may be empty or wiped; **dev seed** (`20260807140000`) plus diagram PNG seed scripts recreate a minimal identical browse tree. Production fill is Megazip orchestrator `--live-import`, not hand SQL.

---

## D. Reproducibility checklist (another shop)

### Schema

- [ ] Apply vehicle foundation: `vehicle_master`, `pnc_categories`, `part_fitment` + RLS + natural keys + FTS (`…100300`, `…010000`).
- [ ] Create Storage bucket `catalog-diagrams` + policies; allow needed MIME types (PNG/GIF).
- [ ] Apply hierarchy: `catalog_makers` → `…_models` → `…_variants` → `…_sections` → `…_diagrams` → `…_diagram_parts` + RLS (`…120000`, `…130000`).
- [ ] Deploy RPCs: `list_catalog_*`, `get_catalog_diagram` (with companion_parts).
- [ ] Optional: `stock_items`, price lists for stock overlay.

### Seed / ingest

- [ ] Flat pack: `vehicle_master` / `pnc_categories` / `part_fitment` JSON → `import_catalog --live` (or Megazip transform output).
- [ ] Upload diagram objects so `storage_path` / `diagram_path` match.
- [ ] Hierarchy: Megazip orchestrator phases or `import_hierarchy_catalog`; for smoke, apply `seed_catalog_hierarchy_dev.sql` + `seed_catalog_diagrams.mjs`.
- [ ] Ensure hotspot rows: `part_fitment.diagram_path = catalog_diagrams.storage_path` AND bboxes set.
- [ ] Align variant `chassis_code` with `vehicle_master.chassis_code` used in cascade.

### APIs / clients

- [ ] `listVehicleMaster`: select 6 columns, order by model, **limit 500**, authenticated.
- [ ] Port `VehicleCascade` (prefer **web** multi-make `deriveMaker`) + cascade clear-downstream + VIN ≥11.
- [ ] Wrap hierarchy RPCs; share path helpers (`/catalog/...` or equivalent).
- [ ] Bridge: lookup variant by chassis for post-confirm EPC link.

### UI cascade contract

- [ ] Four dependent selects + optional VIN (max 17).
- [ ] States: loading / auth / error / empty / no makers / form errors.
- [ ] Submit only when `vehicleCascadeCanSubmit` equivalent.
- [ ] Confirmed vehicle → search by VIN prefix or model/chassis/engine query; optional stock-by-chassis list.
- [ ] Display confirmed vehicle as `model · chassis · engine`.

### EPC browse screens

- [ ] Routes or stack: Maker → Model → Variant → Section → Diagram.
- [ ] Diagram split: image + hotspot overlays (0–1 bbox) + parts table; hover sync; OEM → PDP.
- [ ] Breadcrumb / back; session EPC context for return-from-PDP.
- [ ] Empty/error: no makers, no diagram image, parts-only section.

### Verification SQL (smoke)

```sql
SELECT count(*) FROM vehicle_master;
SELECT count(*) FROM catalog_makers;
SELECT * FROM list_catalog_makers();
SELECT * FROM get_catalog_diagram('nissan','x-trail','t31-mr20','section-filters');
```

---

## Key file index

| Area | Paths |
|------|-------|
| Flat schema | `supabase/migrations/20260723100300_vehicle_catalog.sql`, `…010000_catalog_search_fts.sql` |
| Hierarchy schema | `…120000_catalog_hierarchy_browse.sql`, `…130000_catalog_diagram_parts.sql`, `…140000_seed_catalog_hierarchy_dev.sql` |
| Web cascade | `apps/web/lib/vehicle-catalog.ts`, `components/vehicle-cascade-fields.tsx`, `vehicle-selector.tsx` |
| Web EPC | `apps/web/lib/catalog-hierarchy.ts`, `components/epc/*`, `app/(storefront)/catalog/**` |
| Shared nav | `packages/shared/src/catalog-navigation.ts` |
| Android | `VehicleCatalogModels.kt`, `EpcBrowseScreen.kt`, `CatalogRpcLive.kt` |
| iOS | `VehicleCatalogModels.swift`, `EpcBrowseScreen.swift`, `LiveStorefrontApi.swift` |
| Pipeline | `docs/guides/megazip-multivehicle-catalog.md`, `import_hierarchy_catalog.py`, `import_catalog` |
