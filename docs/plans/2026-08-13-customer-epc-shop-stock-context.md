# Customer EPC / shop / stock shared context

- Status: accepted (amended 2026-08-13 — shop in-stock+priced gate; staff PDP merch fields locked)
- Date: 2026-08-13
- Lane(s): `@web_agent` (customer surfaces) → `@management_app_agent` (add stock / item edit) → `@backend_agent` (RPCs/RLS) → `@data_pipeline_agent` (publish/fitment) → `@hardware_mobile_agent` (QR receive only)
- Skills: `/token-discipline`; `/parts-catalog-ingestion` when touching publish/fitment; `/qr-inventory-workflow` for receiving QR; `/ui-ux-pro-max` only if UI polish invoked
- Pointer ADR: [`docs/decisions/2026-08-13-customer-epc-shop-stock-context.md`](../decisions/2026-08-13-customer-epc-shop-stock-context.md)
- Extends (do not duplicate): [`2026-07-23-autodoc-shop-features.md`](../decisions/2026-07-23-autodoc-shop-features.md), [`2026-08-07-megazip-epc-browse-integration-prompt.md`](2026-08-07-megazip-epc-browse-integration-prompt.md), [`2026-08-07-epc-browse-wireframes.md`](2026-08-07-epc-browse-wireframes.md), [`docs/guides/megazip-multivehicle-catalog.md`](../guides/megazip-multivehicle-catalog.md), ADRs [`2026-07-24-search-index-interim-pg-fts.md`](../decisions/2026-07-24-search-index-interim-pg-fts.md) + [`2026-08-05-meilisearch-catalog-search.md`](../decisions/2026-08-05-meilisearch-catalog-search.md)

## Goal

One durable **shared catalog context model** so agents treat customer EPC search/browse, in-stock shop PLP, staff add-stock/receiving, and item-page (PDP/merch) edits as the **same product identity** with different ownership of fields — not four unrelated features.

## Problem / gap

- Route split is documented (`/catalog` EPC hierarchy vs `/shop` stock PLP; `/search` 4-way; `/parts/[oem]` PDP) but **stock-add and item-page update** are under-specified relative to what customers must see.
- Agents risk inventing parallel “catalog item” models, leaking Meili qty, editing pipeline-owned fitment/diagrams in staff UI, or receiving without OEM→`stock_items` linkage so EPC stock overlay and shop badges go stale/dishonest.
- Need a short SoT that maps **customer-visible fields ↔ staff must maintain ↔ pipeline-owned**.

## Shared context model

**Canonical identity:** OEM part number → `stock_items` (commerce) + `part_fitment` / hierarchy diagram parts (EPC). Meili/FTS are discovery only — **qty never invented** (Meili ADR).

| Concern | Customer sees | Staff maintains | Pipeline / SoR |
|---------|---------------|-----------------|----------------|
| OEM / OE cross-refs | Search + PDP OE tab | Link receive to existing OEM; do not invent OEM | `part_fitment`, `oe_cross_refs`, import |
| Fitment / chassis | Garage sticky; PLP/PDP fit filters | Do **not** hand-edit fitment rows in merch UI | Hierarchy + fitment publish (`--complete-only`) |
| Diagram / hotspot | EPC canvas → PDP `?from=epc` | N/A (view / back-link only) | `get_catalog_diagram`, Storage diagrams |
| Stock honesty | `StockBadge` (in stock / low / out / ask) | Qty via receive/transfer; never fake on PDP | `stock_levels` / saleable RPCs |
| Price USD\|ZiG | `PriceDual` / currency on money | Sell price on default/retail price list; FX at txn time | Ledger/POS rules elsewhere |
| Discount | PDP / PLP discount badge + description when set | `stock_item_shop_merch` discount + description | — |
| Product photos | Main + gallery on PDP | Staff upload via `stock_item_images` | EPC diagram remains pipeline |
| Title / details / fitment / OEM | PDP copy + fitment + part number | **Read-only** — catalog / pipeline | Fitment + OEM + diagram publish |
| Core charge | Parent/child cart lines | Deposit via price list when used | Cart schema; no tax |
| Garage sticky | Sitewide vehicle filters shop + EPC deep-links | N/A | `vehicle_master` / garage |
| Publish gate | Only complete variants/diagrams appear | N/A | Megazip/PartSouq `--complete-only` |
| Shop listability | `/shop` **only** qty &gt; 0 **and** priced (&gt; 0) | Price + receive/transfer into saleable WH | Unpriced / OOS stay off `/shop` |

**Surfaces that share this context**

| Surface | Route / app | Reads | Writes |
|---------|-------------|-------|--------|
| Search spares | `/search` | `search_catalog` / Meili proxy | — |
| EPC browse | `/catalog/…` | `list_catalog_*`, `get_catalog_diagram` + stock join | — |
| Shop stock | `/shop` | In-stock + priced only; optional garage/fitment filter | — |
| PDP | `/parts/[oem]` | Catalog title/fitment/OEM/diagram + staff price/discount/photos | Cart |
| Add stock | Management receive (WH1) | `lookupStockItemByOem` | Qty, bin, cost currency, QR via `bridges/` |
| Product pages | Web `/staff/crm/product-pages` | Catalog read-only fields | Price, discount+desc, main + extra images |

## Customer journeys (concise)

1. **Search spares (4-way)** — OEM | VIN | model | PNC/diagram → hits deep-link EPC or `/parts/[oem]`; stock from Postgres only; garage vehicle narrows when set.
2. **Hierarchy EPC** — Maker→…→Diagram → hotspot/row → PDP with stock overlay (`StockBadge` + `PriceDual` + ATC when saleable).
3. **Shop stock browse** — `/shop` PLP of in-stock/saleable items; when garage/fitment set, filter to fitting OEMs; link out to PDP / “browse diagrams” for chassis.

## Staff journeys (same context)

### Add stock / receiving

Capture so customer search + browse stay accurate:

- Resolve **OEM** → `stock_items` (`lookupStockItemByOem`); create item only when OEM missing (then merch fields required before shop list).
- Warehouse: receive into **WH1**; saleable shop qty only after transfer rules to storefloor (existing WH1/WH2); **quarantine never saleable**.
- Qty, bin/location, optional cost + **explicit currency** (USD|ZiG).
- QR label/scan via **`bridges/` only** — no HTML5 QR.
- Do not invent fitment/diagram at receive time; if OEM unknown to catalog, flag “no EPC overlay until pipeline/fitment exists.”

### Update product pages (staff dashboard)

| Staff-owned (editable) | Catalog-owned (read-only) |
|------------------------|---------------------------|
| Product price (`price_list_items`) | Product title / details |
| Discount + description (`stock_item_shop_merch`) | Fitment |
| Main product image + additional images (`stock_item_images`) | Part number (OEM) |
| | EPC diagram / hotspots |

Shop listability is **derived**: in stock + priced — no separate “publish to shop” toggle in v1.

PDP customer view = catalog identity/copy/fitment/diagram ∪ staff price/discount/photos ∪ live saleable qty.

## Feature backlog (small slices)

### P0 — shared context enforced

| # | Slice | Lane |
|---|-------|------|
| P0.1 | `/shop` (+ rails) gate: qty &gt; 0 and priced &gt; 0 only | `@web_agent` |
| P0.2 | Staff `/staff/crm/product-pages`: price, discount+desc, main+extra images; catalog fields read-only | `@web_agent` + `@backend_agent` |
| P0.3 | PDP shows staff photos + discount; title/fitment/OEM/diagram from catalog | `@web_agent` |
| P0.4 | Receiving always binds OEM→`stock_items`; Bridge QR only | `@management_app_agent` + `@hardware_mobile_agent` |
| P0.5 | `/shop` respects sticky garage/fitment when set | `@web_agent` |

### P1 — honesty + merch

| # | Slice | Lane |
|---|-------|------|
| P1.1 | Android management parity for product-pages merch (optional) | `@management_app_agent` |
| P1.2 | EPC diagram stocked rows + PDP back-link parity with wireframes | `@web_agent` |
| P1.3 | Dual currency display policy aligned with `PriceDual` / money fields | `@web_agent` + `@backend_agent` |
| P1.4 | Publish-complete-only: storefront never lists incomplete hierarchy variants | `@data_pipeline_agent` + `@web_agent` |

### P2 — polish / later

| # | Slice | Lane |
|---|-------|------|
| P2.1 | “No EPC overlay” staff banner when OEM lacks fitment | `@management_app_agent` |
| P2.2 | Wishlist / back-in-stock (AutoDoc later) | `@web_agent` |
| P2.3 | Alternatives strip on PDP | `@web_agent` |

## Lane ownership

| Lane | Owns |
|------|------|
| `@web_agent` | `/catalog`, `/shop`, `/search`, `/parts/[oem]`, garage sticky, badges/prices |
| `@management_app_agent` | Receive/add stock, item edit, POS cart core-charge split |
| `@backend_agent` | RPCs/RLS, saleable qty, search proxy, `stock_items` money fields |
| `@data_pipeline_agent` | Hierarchy/fitment/diagram publish, `--complete-only`, Meili sync |
| `@hardware_mobile_agent` | QR scan/print for receive/labels via `bridges/` |

## Out of scope / non-goals

- ZIMRA / FDMS / fiscal QR / payroll tax
- HTML5 / WebView QR, camera, or printer APIs
- Rebuilding Meili, hierarchy tables, or AutoDoc IA from scratch
- Hand-editing fitment/diagrams in staff merch UI
- RFQ-as-SoR, AI auto-PO / payable amounts
- New schema without citing existing migrations (e.g. `20260807120000_catalog_hierarchy_browse.sql`, inventory/sales phases)

## Adopt-first (Integrate, don’t rebuild)

Extend: `SearchFourWay`, `CatalogBrowse`, `part-detail` / `catalog-product`, `StockBadge`, `PriceDual`, `VehicleSelector` / garage, hierarchy RPCs + `packages/shared/catalog-navigation.ts`, `lookupStockItemByOem`, `get_catalog_diagram` stock join, Meili Edge + `search_catalog` fallback, Bridge QR receive path in `bridges/README.md`. Prefer adapters over new catalogs.

## Acceptance criteria

### This guidance doc is “done” when

- [ ] Agents can answer for any OEM: customer surfaces vs staff writes vs pipeline-owned
- [ ] Add-stock and item-edit checklists reference the same identity as `/shop` + EPC overlay
- [ ] Pointer ADR accepted; no conflicting “separate catalog product” plans without superseding this
- [ ] Hard exclusions restated; open questions listed below

### First implementation slice (P0.1–P0.3)

- [x] `/shop` lists only in-stock + priced items
- [x] Staff product-pages editor (price, discount+desc, images); catalog fields read-only
- [x] PDP consumes staff merch + catalog identity
- [ ] Garage/fitment narrows `/shop` when set (P0.5)
- [ ] `/verifier` + `/supabase-rls-auditor` on merch migration

## Risks / exclusions

- Meili dual-read must not show invented availability.
- Republish `--complete-only` prune vs staff merch/`stock_items` — keep merch rows; soft-hide from `/shop` when unpriced/OOS.
- Core charge parent/child must stay split at cart insert (global law).
- Do not let staff overwrite catalog title/fitment/OEM/diagram via product-pages UI.

## Handoff

1. `@web_agent` + `@backend_agent` for P0.1–P0.3 (this pass).
2. `/security-reviewer` after merch RLS/RPCs land.
3. `/verifier`
4. `/manager` for done gate across lanes.

## Open questions (remaining)

1. May staff create `stock_items` for OEMs **not** yet in pipeline fitment (shop-only SKUs)?
2. Discount semantics: percent-of-list vs fixed amount off — both supported in schema; default UX = percent or amount?
3. ZiG sell-price on PDP: keep USD-primary + converted ZiG display for now.
