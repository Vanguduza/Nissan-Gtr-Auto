# Decision: Shared catalog context (customer EPC/shop ↔ staff stock/item)

- **Date:** 2026-08-13
- **Status:** Accepted (amended same day — shop gate + staff PDP merch fields)
- **Lane:** cross-cutting (`@web_agent`, `@management_app_agent`, `@backend_agent`, `@data_pipeline_agent`, `@hardware_mobile_agent`)
- **Plan:** [`docs/plans/2026-08-13-customer-epc-shop-stock-context.md`](../plans/2026-08-13-customer-epc-shop-stock-context.md)
- **Related:** AutoDoc shop IA; Megazip EPC browse plans; PG FTS + Meilisearch search ADRs; megazip/PartSouq publish guides

## Context

Customer EPC browse/search, in-stock `/shop`, receiving/add-stock, and item-page edits were planned in separate docs. Without one binding model, agents treat them as unrelated products.

## Decision

**One shared catalog context** governs customer EPC/search/shop and staff stock/item updates: canonical OEM → `stock_items` + fitment/diagram EPC data.

### `/shop` listing gate

`/shop` (and home stock rails that reuse the same list helper) show only items that are **saleable in stock** (qty &gt; 0 in active non-quarantine warehouses) **and priced** (default/retail `price_list_items.unit_price` set and &gt; 0). Unpriced or zero-qty OEMs remain findable via EPC/search/PDP when cataloged, but do not appear as shop stock cards.

### Staff PDP / product-page edits (web `/staff`)

Staff may edit only merchandising commerce fields:

| Editable | Source |
|----------|--------|
| Product price | `price_list_items` (default/retail list) |
| Discount + description | `stock_item_shop_merch` |
| Main product image + additional images | `stock_item_images` + `product-images` Storage |

Catalog-owned (read-only on staff product pages; never overwritten by merch UI):

- Product title / details (pipeline / `stock_items` + fitment copy)
- Fitment
- Part number (OEM)
- EPC diagram / hotspots

### Ownership summary

Staff own price, discount copy, and product photos; pipeline owns fitment/diagrams/publish gates; discovery indexes never invent qty. Qty honesty comes only from Postgres saleable stock.

## Consequences

- Implement against the plan’s field-ownership table and P0 backlog; do not scaffold a second catalog product model.
- Do not expose title/fitment/OEM/diagram editors on the staff product-pages surface.
- Hard exclusions unchanged (no ZIMRA, payroll tax, HTML5 QR; Bridge-First hardware).
