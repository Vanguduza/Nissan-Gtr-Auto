# Decision: Home merch rails — algorithm + manual pins

- **Date:** 2026-08-16
- **Status:** Accepted

## Context

Storefront home rails (Featured / Fast movers / Newest) were purely algorithmic via `list_storefront_home_rails`. Staff asked for CRM control without discarding auto ranking.

## Decision

1. **Keep algorithms** as the default fill for each rail (featured: discount then qty; movers: qty; newest: `created_at`).
2. **Add optional manual pins** on `stock_item_shop_merch` (`pin_featured`, `pin_movers`, `pin_newest`, `pin_sort`).
3. **Pins first**, then algorithm fills remaining slots up to the rail limit.
4. **Shop gate unchanged** — pinned items still require in-stock + priced to appear.
5. **Staff UI:** CRM → Product pages (`/staff/crm/product-pages`).

## Consequences

- Merchandisers can force hero SKUs without turning off auto rails.
- CoolMall CRM (Phase D3) must expose the same pin fields.
- No separate “merch rails” nav leaf required for MVP.

## Refs

- Migration `20260816050000_home_rail_manual_pins.sql`
- Plan `docs/plans/2026-08-16-coolmall-web-feature-injection.md`
