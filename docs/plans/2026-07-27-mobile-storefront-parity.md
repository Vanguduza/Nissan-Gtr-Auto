# Mobile storefront parity (Android + iOS)

**Date:** 2026-07-27  
**Source of truth:** `apps/web` storefront (`(storefront)/`, `lib/catalog-search.ts`, `lib/catalog-product.ts`, `lib/catalog-diagram.ts`, `lib/customer-storefront.ts`)

## Goal

Customer Android and iOS apps deliver the same **shopping** surface as web: discover parts, search, PDP, cart/checkout (USD display, ZiG settlement), garage/VIN, compare, wishlist, orders, pay intents, chat, delivery track. **B2B procurement portal remains web-only** (optional native defer).

## Feature matrix

| Capability | Web | Android (today → target) | iOS (today → target) |
|------------|-----|--------------------------|----------------------|
| Home / catalog entry | Yes | **Phase 1** | Phase 1 stub → Phase 2 |
| Four-way search (`search_catalog`) | Yes | **Phase 1** | Phase 2 |
| PLP / category browse | Yes | **Phase 1** (browse list) | Phase 2 |
| PDP `/parts/[oem]` | Yes | **Phase 1** | Phase 2 |
| Diagram canvas + hotspots | Yes | Phase 3 | Phase 3 |
| Add to cart (OEM → stock + UOM) | Yes | **Phase 1** | Phase 2 |
| Cart + checkout RPCs | Yes | Done (scaffold) | Done (scaffold) |
| ZiG currency + exchange on cart | Yes | Done | Done |
| Paynow / ContiPay intent | Yes | Done | Done |
| My Garage / VIN | Yes | Done | Done |
| Wishlist | Yes | Done | Done |
| Compare (guest + auth) | Yes | Done | Done |
| Reviews + bridge camera photos | Yes | Done | Done |
| Account orders + track | Yes | Done | Done |
| Live chat | Yes | Done | Done |
| B2B trade portal | Web only | **Non-goal** | **Non-goal** |
| ZIMRA / fiscal QR | Excluded | Excluded | Excluded |
| HTML5 QR / browser camera | Excluded | Bridge-First only | Bridge-First only |

## Shared RPC / API list (mobile ↔ web)

| Name | Params (subset) | Used for |
|------|-----------------|----------|
| `search_catalog` | `p_mode`, `p_query` | PLP search (part / vin / model / pnc) |
| PostgREST `stock_items` | `oem_part_number`, `base_uom_id` | PDP, browse, add-to-cart resolve |
| PostgREST `stock_levels` + `warehouses` | saleable qty | Stock badge |
| PostgREST `price_lists` + `price_list_items` | default list USD | PDP / PLP pricing |
| PostgREST `part_fitment`, `pnc_categories` | category facet | Browse filters (Phase 2+) |
| `create_customer_cart` | warehouse, currency, fulfillment, rate | Cart |
| `add_customer_cart_line` | cart, stock_item, uom, qty | Cart / PDP |
| `checkout_customer_cart` | cart id | Checkout |
| `get_customer_order` | invoice id | Orders |
| Wishlist / compare / reviews / garage / pay / chat / track RPCs | (existing) | Account features |

Canonical string names: `apps/android-customer/.../RpcNames.kt`, `apps/web/lib/*`, `packages/supabase-client`.

## UX notes

- **Native UI:** Jetpack Compose (Android), SwiftUI (iOS). No WebView storefront.
- **Pricing:** Show **USD** on catalog surfaces; ZiG at cart/checkout (match web).
- **Bridge-First:** Camera for review photos (and future VIN scan) via `bridges/` only — never HTML5 QR in WebView.
- **Auth:** Live mode requires GoTrue customer session + `customers.profile_id = auth.uid()` for AuthZ RPCs (same as web).
- **Navigation:** Android Phase 1 uses app shell route → `:feature:catalog` stack (home → search results → PDP). iOS Phase 1 tab shell only; stack in Phase 2.

## Phased roadmap

### Android

| Phase | Scope |
|-------|--------|
| **1** (this session) | `:feature:catalog` — home, browse, `search_catalog`, PLP, PDP, add-to-cart; `FakeRpcClient` + `SupabaseRpcClient` parity |
| 2 | Category facets, fitment lines on PDP, wishlist/compare from PDP, deep links `gtrcustomer://parts/{oem}` |
| 3 | Diagram image + hotspot canvas (native, Storage `catalog-diagrams`) |
| 4 | Checkout polish (ZiG rate UI, fulfillment), home merchandising parity |

### iOS (mirror Android phases)

| Phase | Scope |
|-------|--------|
| **1** (this session) | `CatalogScreen` shell + tab wiring; plan alignment |
| 2 | `StorefrontApi.searchCatalog` / PDP / add-to-cart (URLSession PostgREST) |
| 3 | Diagram canvas |
| 4 | Checkout + home parity |

## Non-goals

- B2B procurement portal on mobile (unless product explicitly re-scopes).
- ZIMRA, payroll tax, HTML5 QR scanning.
- Customer GPS delivery trail (last point + ETA only — already shipped).

## Verification

- Fake: `cd apps/android-customer && .\gradlew.bat assembleDebug` → Catalog → search → PDP → add to cart → Cart.
- Live: sign in as storefront customer → same flow against local Supabase.

## References

- ADR / decisions: `docs/decisions/2026-07-24-customer-self-pay.md`
- Android README: `apps/android-customer/README.md`
- iOS README: `apps/ios/README.md`
