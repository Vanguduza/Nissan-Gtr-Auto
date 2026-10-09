# 2026-10-10 — Catalogue data is staff-only on customer surfaces

**Decision (owner):** Customers never see catalogue data: no EPC browsing,
diagrams, part numbers (OEM), PNC codes, cross-references or raw fitment
lists. This holds on every customer platform (web storefront, Android
customer app, iOS). The catalogue is still used in the background to match
search terms and to check fitment against the customer's vehicle.

## What customers get

- Vehicle selection (maker → model → generation → engine, or VIN) from the
  catalogue's vehicle list, used to filter the shop to parts that fit.
- Product pages show name, photos, price, stock and a "fits your vehicle"
  check against the primary garage vehicle.
- Search is plain product search (`/shop?q=`); matches against part numbers
  happen server-side and are not shown.
- Product links use the stock item id, never the part number.

## What stays staff-only

- `/catalog` EPC browse (diagrams, part tables) — middleware sends non-staff
  to sign-in.
- POS / tablet EPC, staff catalogue tools.

## Home page

The "Find the right part" hero is replaced by a card carousel of items
picked in **CRM → Product pages → Home page carousel** (position 1–12 and an
optional caption). Backed by `stock_item_shop_merch.home_carousel_rank` /
`home_carousel_caption`, `set_storefront_home_carousel` (admin/sales) and the
anon-readable `list_storefront_home_carousel`, which returns no part numbers.
