# Mobile parity — storefront home rails + CRM product pages / kits

- Status: web + mobile customer wiring landed (shop gate / home rails / product pages); CRM kits + management CRM module remain WIP
- Date: 2026-08-13 (web shop slice commit 2026-08-14)
- Lanes: `@android_agent`, `@ios_agent`, `@management_app_agent`, `@web_agent`
- Extends: [`2026-08-13-customer-epc-shop-stock-context.md`](2026-08-13-customer-epc-shop-stock-context.md), [`2026-08-13-crm-kits-create.md`](2026-08-13-crm-kits-create.md)
- Web SoT: `list_storefront_home_rails`, staff product pages / kits panels

## Checklist

### android-customer

- [x] Remove Flash deals / Most sale home rails
- [x] Featured products · Newest arrivals · Top movers via `list_storefront_home_rails` (Fake + Live soft-fail to browse slices)
- [x] Shop browse gate: qty > 0 and priced > 0 (`applyCatalogFilterSort` + Live browse filter)
- [ ] Visual catalog / four-way search page removal — N/A (inline typeahead + EPC browse retained; no separate flash/visual-catalog surface)
- [ ] Wishlist from home-rail rows when RPC omits `stock_item_id` (synthetic `oem:…` id) — open PDP for real UUID

### ios (GTRCustomer)

- [x] Featured / Newest / Top movers home rails + `listStorefrontHomeRails`
- [x] Shop stock gate on browse + PLP filter
- [ ] No dedicated Flash deals UI existed — skipped
- [ ] Four-way search remains as inline OEM/VIN/model/PNC typeahead (not a separate home “four-way” block to remove)
- [ ] Home-rail synthetic UUID when RPC omits stock id (same gap as Android)

### android-management

- [ ] CRM → Product pages: price, discount+description, gallery pick upload / Storage path register / set primary — **code present, uncommitted** (defer with kits module)
- [ ] CRM → Kits: create (title, manual OEM, optional chassis, ≥2 component pickers) + list/toggle active — **WIP uncommitted**
- [ ] RPCs Fake seeds for unsigned/local smoke — with management CRM WIP
- [ ] Kit edit (add/remove components beyond create + active toggle) — deferred; web has fuller edit
- [ ] Camera capture for product photos — gallery Intent only (no HTML5; Bridge camera not wired for merch)

### web (landed this slice)

- [x] Home: drop flash + four-way + visual catalog blocks; Featured / Newest / Movers via anon RPC
- [x] `/shop` in-stock + priced gate + merch discount on list/PDP
- [x] Staff Product pages panel + nav
- [ ] Staff Kits panel — leave unstaged (see `2026-08-13-crm-kits-create.md`)

## Residual gaps

| Gap | Notes |
|-----|--------|
| iOS / Android home-rail `stock_item_id` | RPC returns OEM only; synthetic ids for list identity |
| Management kit + product-pages Android module | Uncommitted WIP |
| Web kits staff UI + `20260813120000` | Uncommitted WIP |
| Storage ACL / bucket policies | Assume `product-images` staff write already migrated with web |

## Smoke (manual; do not require CI builds here)

1. Customer Fake: home shows three rails with seed parts.
2. Management Fake: CRM → Product pages save price; Kits create with 2 search picks.
3. Live: apply migrations `20260813100000`, `20260813200000` (kits `20260813120000` when kits land).
