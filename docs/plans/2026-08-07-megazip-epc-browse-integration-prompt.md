# Agent prompt — Integrate Megazip-style EPC browse into existing web storefront

> **Use this document as the primary instruction set** for `@web_agent` (with a small `@backend_agent` slice for dev seed data). The Megazip catalog pipeline is running in parallel — **do not wait for production import** to ship the UI integration. Build against dev seed data; production data is a swap at import time.

- **Date:** 2026-08-07
- **Lane:** `@web_agent` — `apps/web/**`, `packages/ui/**`; read `packages/shared/**`, `packages/supabase-client/**`
- **Backend slice:** `@backend_agent` — dev hierarchy seed migration only (no pipeline changes)
- **Skills:** `/token-discipline` always; invoke `/ui-ux-pro-max` explicitly for wireframes/visual polish
- **Pipeline reference:** `docs/guides/megazip-multivehicle-catalog.md`
- **Hard exclusions:** No ZIMRA, no payroll tax, no HTML5/browser QR scanning, no hardware bridge changes

---

## Your mission

Integrate **Megazip-style hierarchy-first catalog navigation** into the **existing** Nissan GTR web storefront. This is an **integration and restructuring** task — extend what exists, relocate what conflicts, reuse components where possible.

Target navigation flow:

```text
Maker → Models (A–Z) → Variants → Sections → Diagram + hotspots → Parts (+ stock overlay)
```

**Search remains second entry** via existing `search_catalog` / Meili (`SearchFourWay`, `/search`). Hierarchy browse uses Supabase RPCs already migrated. Do not merge the two query layers.

---

## What already exists (integrate with this — do not reinvent)

### Backend (done — consume, don't rebuild)

| Asset | Location |
|-------|----------|
| Hierarchy tables | `supabase/migrations/20260807120000_catalog_hierarchy_browse.sql` |
| Browse RPCs | `list_catalog_makers`, `list_catalog_models`, `list_catalog_variants`, `list_catalog_sections`, `get_catalog_diagram` |
| Import path (for later) | `data-pipeline/data_pipeline/import_hierarchy_catalog.py` |
| Bundle schema | `data-pipeline/schemas/catalog_hierarchy_bundle.schema.json` |
| Shared TS contract | `packages/shared/catalog-navigation.ts` |

`get_catalog_diagram` returns diagram metadata, hotspot bboxes, parts list, and joins `stock_items` when stocked.

### Web storefront (extend / rewire)

| Existing module | Role today | Integration action |
|-----------------|------------|-------------------|
| `apps/web/app/(storefront)/catalog/page.tsx` | Stock PLP (`CatalogBrowse`) | **Repurpose route** → EPC maker hub; **move PLP elsewhere** |
| `apps/web/components/catalog-browse.tsx` | Category/stock product list | Keep; point at new PLP route (`/shop`) |
| `apps/web/components/catalog-canvas-stub.tsx` | Diagram + hotspot overlays from `part_fitment` | **Refactor into** hierarchy-aware `EpcDiagramCanvas`; keep OEM loader for PDP |
| `apps/web/lib/catalog-diagram.ts` | Storage URL + `loadOemCatalogDiagram` for PDP | Extend with hierarchy diagram resolver; **do not break PDP** |
| `apps/web/lib/catalog-product.ts` | PLP + PDP product loading | Unchanged for `/shop` and `/parts/[oem]` |
| `apps/web/lib/catalog-search.ts` | Search modes, Meili, `partHref` | Add EPC deep-link helpers; **fix merge conflict first** |
| `apps/web/components/search-four-way.tsx` | 4-way search (part/VIN/model/PNC) | Cross-link model/chassis hits → EPC routes |
| `apps/web/components/vehicle-selector.tsx` | `vehicle_master` cascade + VIN | Add "Browse diagrams" when chassis maps to a variant |
| `apps/web/components/garage-panel.tsx` | Saved vehicles | Link saved chassis → EPC variant URL |
| `apps/web/components/part-detail.tsx` | PDP with `CatalogCanvasStub` | Add "Back to diagram" when EPC context present |
| `apps/web/components/site-header.tsx` | Category shortcuts → `/catalog?cat=…` | Update links after PLP relocation |
| `apps/web/components/site-menu.tsx` | "All categories" → `/catalog` | Split: EPC catalog vs shop stock |
| `supabase/seed_catalog_diagrams.mjs` | Diagram PNGs for Navara/X-Trail fixtures | Reuse Storage paths in dev hierarchy seed |

### Dev fixtures (use for seed data)

| Fixture | Path |
|---------|------|
| X-Trail T31 | `data-pipeline/fixtures/xtrail_t31_mr20/` |
| Navara D40 | `data-pipeline/fixtures/navara_d40_yd25/` |
| Storage prefixes | `xtrail-t31/…`, `navara-d40/…` |

There is **no** hierarchy seed in Supabase yet — you must add one so UI work is unblocked while the Megazip crawl finishes.

---

## Blockers to fix first

Before catalog integration, resolve **merge conflict markers** that break the web build:

- `apps/web/lib/catalog-search.ts` (critical)
- `apps/web/lib/staff-pos-realtime.ts`
- `apps/web/lib/staff-hr.ts`
- `apps/web/components/staff-credit-panel.tsx`

**Gate:** `pnpm --filter web build` passes cleanly.

---

## Route restructuring (integrate into existing app router)

The current `/catalog` URL is occupied by the stock PLP. Split responsibilities:

| URL | Purpose | Component source |
|-----|---------|-------------------|
| `/catalog` | **EPC maker hub** | New — `EpcMakerGrid` |
| `/catalog/[maker]` | Model grid (A–Z) | New — `EpcModelGrid` |
| `/catalog/[maker]/[model]` | Variant list | New — `EpcVariantList` |
| `/catalog/[maker]/[model]/[variant]` | Section grid | New — `EpcSectionGrid` |
| `/catalog/[maker]/[model]/[variant]/[section]` | Diagram + parts | New — `EpcDiagramPage` |
| `/shop` | **Relocated stock PLP** | Existing `CatalogBrowse` (moved page) |
| `/search` | Unchanged | Existing |
| `/parts/[oem]` | Unchanged PDP | Extend with EPC back-link |
| `/vehicle` | Unchanged | Extend `VehicleSelector` |

Update every inbound link that today points at `/catalog?cat=…` to `/shop?cat=…`. Update header/menu primary catalog link to `/catalog` (EPC). Add explicit "Shop stock" or equivalent → `/shop`.

Add a one-line comment at the top of the relocated PLP page documenting the move.

---

## Implementation instructions

### 1. Dev hierarchy seed (`@backend_agent`)

Add migration `supabase/migrations/20260807140000_seed_catalog_hierarchy_dev.sql` that inserts a **complete drill-down tree** for local dev:

- Maker: `nissan`
- Models: at least X-Trail and Navara (from fixture display names / slugs)
- Variants: T31 and D40 chassis rows aligned with existing `vehicle_master` / `part_fitment` seed data
- Sections: mirror the section slugs already used in fixture diagram paths (`section-brakes`, `section-engine`, etc.)
- Diagrams: `storage_path` values matching existing Storage prefixes (`xtrail-t31/…`, `navara-d40/…`)

RLS: seed data must remain readable under existing `catalog_*` SELECT policies for authenticated users. Run `/supabase-rls-auditor` on the migration.

After `supabase db reset`, a logged-in user must be able to drill Nissan → X-Trail → T31 → section → diagram with hotspots.

Optional fallback (only if migration is blocked): `NEXT_PUBLIC_CATALOG_FIXTURE=1` reads checked-in JSON under `apps/web/fixtures/epc-dev-bundle.json`. RPC path remains primary; fixture is dev-only.

---

### 2. Hierarchy RPC client (`apps/web/lib/catalog-hierarchy.ts`)

Create a typed client that wraps existing Supabase RPCs and maps responses to `packages/shared/catalog-navigation.ts` types:

```typescript
listCatalogMakers()
listCatalogModels(makerSlug)
listCatalogVariants(makerSlug, modelSlug)
listCatalogSections(makerSlug, modelSlug, variantSlug)
getCatalogDiagram(makerSlug, modelSlug, variantSlug, sectionSlug)
```

Follow the `{ ok: true, data } | { ok: false, error }` pattern used in `catalog-diagram.ts`.

Add slug/path helpers:

```typescript
catalogPath(ctx: CatalogBrowseContext): string
parseCatalogParams(params): CatalogBrowseContext
epcHref(ctx: CatalogBrowseContext): string
```

Export types from `@gtr/shared` — do not duplicate type definitions in web.

---

### 3. Diagram image resolution

Extend `apps/web/lib/catalog-diagram.ts` (or a thin sibling) with:

```typescript
resolveDiagramImageUrl(client, diagram: { storage_path, image_url })
```

Resolution order: `storage_path` → `catalog-diagrams` Storage public URL (reuse `catalogDiagramPublicUrl`) → `image_url` fallback.

Keep `loadOemCatalogDiagram` and `loadSampleCatalogDiagram` unchanged for PDP and PLP teaser use.

---

### 4. App router pages

Create under `apps/web/app/(storefront)/catalog/`:

```
page.tsx                                    → maker hub (replace current PLP)
[maker]/page.tsx
[maker]/[model]/page.tsx
[maker]/[model]/[variant]/page.tsx
[maker]/[model]/[variant]/[section]/page.tsx
```

Move current PLP to `apps/web/app/(storefront)/shop/page.tsx` — same `CatalogBrowse` props from searchParams.

Prefer **Server Components** for list pages (fetch RPC + set metadata). Client components only where interaction is required (hotspot hover, cart, table row selection).

Match existing auth gate behaviour from `CatalogBrowse` — authenticated users only unless product explicitly changes RLS.

---

### 5. EPC components (new namespace)

Create `apps/web/components/epc/` with CSS modules prefixed `epc-*.module.css`. Do not overload `page.module.css`.

| Component | Responsibility |
|-----------|----------------|
| `EpcBrowseLayout` | Shared layout, skeleton loading |
| `EpcBreadcrumb` | Maker › Model › Variant › Section from `CatalogBrowseContext` |
| `EpcAuthGate` | Same session check pattern as `CatalogBrowse` |
| `EpcEmptyState` | Graceful empty when RPC returns `[]` |
| `EpcMakerGrid` | Maker tiles with `model_count` |
| `EpcModelGrid` | Cards: `display_name`, `body_type`, year range; sorted by `sort_key` |
| `EpcVariantList` | Rows: `chassis_code`, `grade`, `sales_region`, `year_label`, `engine_code` |
| `EpcSectionGrid` | Thumbnail cards from `thumbnail_url`, `name`, `sort_order` |
| `EpcDiagramCanvas` | Refactor hotspot logic from `catalog-canvas-stub.tsx` |
| `EpcPartsTable` | Parts from `get_catalog_diagram.parts` |
| `EpcDiagramPage` | Composes canvas + table + breadcrumb |

**Megazip UX behaviours to implement:**

- Models sorted A–Z via `sort_key`
- Variant list dense but scannable (chassis prominent)
- Section grid with lazy-loaded thumbnails and skeleton placeholders
- Diagram page: desktop split ~60% canvas / 40% parts table; mobile stack (canvas top)
- Hotspot hover ↔ table row highlight (bidirectional)
- Hotspot/row click → `/parts/[oem]` via existing `partHref`
- Stocked parts: reuse `StockBadge`, `PriceDual`, `AddToCartButton`
- When `diagram: null` but parts exist: table-only view with explanatory banner (matches pipeline `publish_diagram: false`)

Leave `catalog-canvas-stub.tsx` as a thin re-export of `EpcDiagramCanvas` for PDP backward compatibility, or update `part-detail.tsx` imports directly — minimal diff.

Design for multiple diagrams per section (tabs by `diagram.slug`) even though current RPC returns the first diagram only.

---

### 6. Integrate with existing search flow

In `catalog-search.ts` (after conflict resolution):

- Model/chassis search hits should deep-link to EPC variant URLs where a unique match exists, not only `/search?q=…`
- Add chassis facet links in `search-results.tsx` → nearest hierarchy variant
- Optional: `/search?mode=model&q=x-trail+t31` redirects to variant page on unique match

Do not change Meili index schema or search RPC signatures.

---

### 7. Integrate with vehicle selector and garage

In `vehicle-selector.tsx` and `garage-panel.tsx`:

- After vehicle confirm / saved vehicle select, if `chassis_code` matches a row in `catalog_variants`, show **"Browse EPC diagrams"** linking to the variant URL
- Persist last EPC context in sessionStorage: key `gtr:epc-context` (`CatalogBrowseContext`)

Do **not** replace the `vehicle_master` cascade — augment it. `VehicleCascade` in `vehicle-catalog.ts` stays the source for VIN/cascade; hierarchy is the diagram browse tree.

---

### 8. Integrate with PDP

In `part-detail.tsx`:

- Accept EPC context via query `?from=epc` and/or sessionStorage `gtr:epc-context`
- When present, render breadcrumb **"Back to diagram"** linking to the section URL
- Keep existing `CatalogCanvasStub` / OEM diagram loader on PDP unchanged

---

### 9. Navigation and IA updates

Update these existing files consistently:

- `apps/web/components/site-header.tsx` — category shortcuts → `/shop?cat=…`; add EPC catalog entry
- `apps/web/components/site-menu.tsx` — rename/split "All categories" vs "Parts catalog (EPC)"
- `apps/web/components/site-footer.tsx` — any `/catalog` links
- `apps/web/components/home-merch.tsx`, `hero.tsx`, `product-rail.tsx` — "Browse by vehicle" → `/catalog`

Invoke `/ui-ux-pro-max` to produce wireframe notes in this doc's appendix or a sibling `2026-08-07-epc-browse-wireframes.md` before final CSS polish.

---

## Files to create

```
apps/web/lib/catalog-hierarchy.ts
apps/web/components/epc/epc-browse-layout.tsx
apps/web/components/epc/epc-breadcrumb.tsx
apps/web/components/epc/epc-auth-gate.tsx
apps/web/components/epc/epc-empty-state.tsx
apps/web/components/epc/epc-maker-grid.tsx
apps/web/components/epc/epc-model-grid.tsx
apps/web/components/epc/epc-variant-list.tsx
apps/web/components/epc/epc-section-grid.tsx
apps/web/components/epc/epc-diagram-canvas.tsx
apps/web/components/epc/epc-parts-table.tsx
apps/web/components/epc/epc-diagram-page.tsx
apps/web/components/epc/epc-*.module.css
apps/web/app/(storefront)/catalog/[maker]/page.tsx
apps/web/app/(storefront)/catalog/[maker]/[model]/page.tsx
apps/web/app/(storefront)/catalog/[maker]/[model]/[variant]/page.tsx
apps/web/app/(storefront)/catalog/[maker]/[model]/[variant]/[section]/page.tsx
apps/web/app/(storefront)/shop/page.tsx
supabase/migrations/20260807140000_seed_catalog_hierarchy_dev.sql   (@backend_agent)
```

## Files to modify

```
apps/web/app/(storefront)/catalog/page.tsx          → EPC maker hub
apps/web/lib/catalog-search.ts                      → conflicts + epcHref
apps/web/lib/catalog-diagram.ts                     → hierarchy image resolver
apps/web/components/site-header.tsx
apps/web/components/site-menu.tsx
apps/web/components/part-detail.tsx
apps/web/components/vehicle-selector.tsx
apps/web/components/garage-panel.tsx
apps/web/components/search-results.tsx
packages/shared/catalog-navigation.ts               → path helpers if needed
packages/shared/index.ts                            → re-export
```

## Files to preserve (minimal touch)

```
apps/web/components/catalog-browse.tsx              → /shop only
apps/web/lib/catalog-product.ts
apps/web/components/search-four-way.tsx
apps/web/lib/vehicle-catalog.ts
```

---

## Testing and acceptance

Run `/verifier` when integration is complete.

**Build gate:** no conflict markers; `pnpm --filter web build` passes.

**Manual acceptance (dev seed):**

- [ ] `/catalog` shows maker hub (not stock PLP)
- [ ] `/shop` serves relocated PLP with existing filters/sort
- [ ] Drill-down: Nissan → X-Trail → T31 → section → diagram with ≥1 hotspot
- [ ] Hotspot click → PDP; back link returns to diagram
- [ ] Stocked part shows price + add to cart on diagram page
- [ ] Unauthenticated user sees auth gate consistent with current catalog
- [ ] Search model/chassis hit can jump into EPC tree
- [ ] Vehicle selector / garage offers "Browse diagrams" for seeded chassis
- [ ] Mobile layout usable at 375px width
- [ ] No ZIMRA, no browser QR, no hardware bridge changes

**Automated (Vitest):**

- `catalog-hierarchy.ts` RPC mapping with mocked Supabase client
- `catalogPath` / `parseCatalogParams` slug round-trip
- `EpcDiagramCanvas` hotspot style (0–1 fraction vs absolute pixels — logic copied from `catalog-canvas-stub.tsx`)

---

## Production cutover (when Megazip pipeline completes — operator task)

This is **not** part of the web integration PR. Document only; execute when bundle quality is approved.

```powershell
python -m data_pipeline.megazip_catalog_orchestrator `
  --makers Nissan --skip-crawl `
  --phase parse,transform,pcdb,filter,import `
  --live-import --complete-only `
  --out-root out/megazip
```

Then upload `out/megazip/nissan/diagrams/` to Storage bucket `catalog-diagrams` with paths matching `catalog_diagrams.storage_path`. Disable any dev fixture flag. No route or component changes should be required — verify RPC counts and spot-check priority chassis (T30, T31, D40, etc.).

---

## Constraints (non-negotiable)

1. **Integrate, don't greenfield** — reuse `CatalogBrowse`, `CatalogCanvasStub` logic, `SearchFourWay`, `PartDetail`, auth patterns, and existing CSS tokens.
2. **Dual navigation** — hierarchy RPCs for browse; `search_catalog`/Meili for search.
3. **Shared contract** — types and route shape must match `packages/shared/catalog-navigation.ts` (mobile will mirror later).
4. **Auth** — match existing authenticated catalog access; do not open `catalog_*` to anon without explicit RLS change.
5. **Minimal scope** — relocate PLP, don't rebuild it; targeted diffs, not full-file rewrites of unrelated modules.
6. **No wait on pipeline** — ship UI against dev seed; production import is a data swap.
7. **Lane discipline** — stay in `@web_agent` paths; route backend seed to `@backend_agent`.

---

## Definition of done

Integration is complete when a developer can open `/catalog`, drill through the full Megazip-style tree on dev seed data, interact with diagram hotspots, reach PDP with stock/cart, and return to the diagram — while `/shop` continues to serve the existing stock PLP and search/garage entry points deep-link into the same tree. Production Megazip data plugs in via import with zero route changes.
