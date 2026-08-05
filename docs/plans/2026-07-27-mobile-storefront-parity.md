# Mobile storefront parity (Android + iOS)

**Date:** 2026-07-27  
**Updated:** 2026-08-04 — Android customer Shop tab merchandised home (promo strip, vehicle card, popular part types, deals placeholder) + `feature/catalog` repository/use-case layer  
**Updated:** 2026-08-03 — OSS Discover + customer shell redesign (Jetsnack Integrate); cart/ZiG; iOS deep link + review camera  
**Discovery:** [`2026-08-03-mobile-ui-oss-discovery.md`](2026-08-03-mobile-ui-oss-discovery.md)  
**Source of truth:** `apps/web` storefront + brand ADR `docs/decisions/2026-07-23-storefront-autodoc-logo.md` + IA spec `docs/decisions/2026-08-04-gsf-ux-behaviour-specification.md`

## Goal

Customer Android and iOS apps deliver the same **shopping** surface as web: discover parts, search, PDP, cart/checkout (USD display, ZiG settlement), garage/VIN, compare, wishlist, orders, pay intents, chat, delivery track. **B2B procurement portal remains web-only**.

**Visual goal:** steel header + chalk ground + red CTAs from `packages/ui/brand-tokens.json` — not behavior-only parity.

## OSS base (2026-08-03)

| Surface | Strategy | Repo | License |
|---------|----------|------|---------|
| Customer Android + iOS | **Integrate** patterns | [android/compose-samples · Jetsnack](https://github.com/android/compose-samples/tree/main/Jetsnack) | Apache-2.0 |
| Management phone/tablet | **Integrate** density cues | [compose-samples · Reply](https://github.com/android/compose-samples/tree/main/Reply) | Apache-2.0 |
| iOS review camera | **Build** thin bridge | `bridges/ios/ReviewCamera` | project |
| Android customer `feature/catalog` layering | **Structural pattern only** (no code/assets) | [3wiida/OmniCart](https://github.com/3wiida/OmniCart) | **None** (`license: null` via GitHub API — all-rights-reserved, more restrictive than AGPL/GPL) |

Not forked into the monorepo — GTR tokens + live RPC clients stay SoR. OmniCart's repo/use-case Clean-Architecture *shape* (generic, non-copyrightable — same pattern as Google's Apache-2.0 "Now in Android" sample) was applied to `feature/catalog` (`domain/`, `data/`, `domain/usecase/`); zero OmniCart code, resources, or strings were copied.

## Brand tokens (shared)

| Artifact | Path |
|----------|------|
| Canonical JSON | `packages/ui/brand-tokens.json` |
| Android Material3 | `packages/android-ui` → `GtrTheme` / `GtrBrandBar` / `GtrFeatureBody` |
| iOS SwiftUI | `apps/ios/GTRCustomer/Theme/GTRTheme.swift` |

### Visual acceptance checklist

| Check | Web | Android customer | Android management | Android delivery | iOS |
|-------|-----|------------------|--------------------|------------------|-----|
| CTA / tint `#C8102E` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Steel chrome `#12151C` | ✅ | ✅ | ✅ hub | ✅ | ✅ |
| Chalk page `#F4F5F7` | ✅ | ✅ | ✅ | ✅ | ✅ |
| Shop / Cart / Account IA | ✅ | ✅ bottom nav | n/a (staff hub) | Jobs-first shell | ✅ 3 tabs |
| Dense search + categories | ✅ | ✅ | n/a | n/a | ✅ |
| Cart lines (no UUID stubs) | ✅ | ✅ | n/a | n/a | ✅ |
| `fetchZigExchangeRate` | ✅ | ✅ | staff finance | n/a | ✅ |
| No Material purple seed | ✅ | ✅ | ✅ | ✅ | ✅ |

### Remaining visual gaps

| Gap | Notes |
|-----|-------|
| Full AutoDoc utility strip | Web has dual-row header; mobile uses steel brand bar + in-body four-way search — acceptable density tradeoff |
| Diagram canvas polish | Phase 3 — **Later** |
| Management adaptive rail (Reply full) | Hub restyled with dense module rows; full NavigationSuiteScaffold tablet rail **Later** (POS/kiosk modules preserved) |
| iOS PostScript font verify | Confirm Titillium / Source Sans on device |
| Deals & Promotions section (Android customer) | UI ships (`DealsSection`) but always renders "Coming soon" — no customer-facing "browse active deals" RPC exists yet (`ai_promo_*` tables are staff-only CRM outreach, not a public feed). Backend TODO before `GetActiveDealsUseCase` can return real data. |
| Popular Part Types tile imagery | Uses Material icon tiles (brand red accent bar), not photography — no product-image pipeline wired into `feature/catalog` yet. Honest icon placeholder, not a GSF asset. |
| iOS home merchandising parity | Not in this pass (Android-customer-lane only) — port promo strip / vehicle card / popular-types / deals to `apps/ios` **Later**. |

## Feature matrix

| Capability | Web | Android customer | iOS |
|------------|-----|------------------|-----|
| Merchandising home + categories | Yes | ✅ promo strip + vehicle card + category chips + popular part types + browse | ✅ (category chips + browse; no promo/vehicle-card/popular-types yet) |
| Deals & Promotions | Partial (staff CRM only) | Placeholder ("Coming soon" — no customer deals RPC yet) | **Later** |
| Four-way search | Yes | ✅ | ✅ |
| PLP / PDP + add-to-cart | Yes | ✅ | ✅ |
| Diagram canvas | Yes | **Later** | **Later** |
| Cart lines + checkout UX | Yes | ✅ (ensureOpenCart + lines) | ✅ |
| Fulfillment Click & collect / Nationwide | Yes | ✅ | ✅ |
| ZiG via `get_zig_exchange_rate` | Yes | ✅ | ✅ |
| Pay intents | Yes | ✅ Account → Pay | ✅ |
| Garage / wishlist / compare / reviews | Yes | ✅ Account hub | ✅ |
| Review camera (Bridge-First) | Bridge | ✅ pod-camera | ✅ ReviewCamera |
| Deep link `gtrcustomer://parts/{oem}` | path | ✅ | ✅ (+ legacy `gtr-customer`) |
| Live chat / track | Yes | ✅ | ✅ |
| B2B trade portal | Web only | **Non-goal** | **Non-goal** |
| ZIMRA / HTML5 QR | Excluded | Excluded | Excluded |

## Shared RPC additions (2026-08-03)

| Name | Mobile |
|------|--------|
| `get_zig_exchange_rate` | Android `RpcClient.fetchZigExchangeRate` · iOS `StorefrontApi.fetchZigExchangeRate` |
| `ensureOpenCart` / `resolveMainWarehouseId` | Both — mirrors web cart helpers |

## UX notes

- **Native UI:** Compose / SwiftUI. No WebView storefront.
- **Navigation:** Customer = Shop · Cart · Account (Jetsnack-shaped). Secondary features nest under Account. GSF's 5-tab pattern was reviewed and rejected (`docs/decisions/2026-08-04-gsf-ux-behaviour-specification.md`) — 3-tab kept.
- **Vehicle card:** Shop-tab home reuses `feature/garage`'s own vehicle (free-text make/model/generation/engine, not cascading selects) + `SearchMode.VIN` search. No UK number-plate lookup (explicitly excluded).
- **Pricing:** USD on catalog; ZiG rate at cart/checkout.
- **Bridge-First:** Review / POD camera via `bridges/` only.
- **Management:** Role hub denser module rows; POS / kiosk / offline / tablet flavors **not gutted**.
- **Delivery:** Lands on Jobs (not button hub); Account is secondary.

## Phased roadmap (honest)

| Phase | Status |
|-------|--------|
| Brand chrome + fonts | Done |
| Catalog search / PDP / add-to-cart | Done |
| Customer shell redesign (Shop/Cart/Account) | Done 2026-08-03 |
| Cart UX (lines + ZiG RPC) | Done 2026-08-03 |
| iOS parts deep link + review camera | Done 2026-08-03 |
| Android customer Shop-tab home merchandising (promo strip, vehicle card, popular part types) | Done 2026-08-04 |
| `feature/catalog` repository/use-case layer (decouples `CatalogViewModel` from `RpcClient`) | Done 2026-08-04 |
| Customer-facing Deals/Promotions RPC (`@backend_agent`) | **Later** — UI placeholder ships now |
| Category facets (server-driven) | Later |
| Diagram canvas | Later |
| Full Reply adaptive tablet rail for management | Later |
| Staff feature density vs every web staff panel | Partial — only modules with existing RPC |

## Non-goals

- B2B procurement on mobile.
- ZIMRA, payroll tax, HTML5 QR.
- Dual SoR (Shopify / ERPNext / Odoo rebase).

## Verification

1. Web: `pnpm dev` — compare IA.
2. Android customer: Shop tab search → PDP → Cart → Checkout; deep link `gtrcustomer://parts/…`.
3. iOS: Shop / Cart / Account tabs; `gtrcustomer://parts/{oem}`; Reviews → camera bridge.
4. Management: hub module rows; open POS / kiosk unchanged.
5. Delivery: opens on Jobs.

## References

- Discovery: `docs/plans/2026-08-03-mobile-ui-oss-discovery.md`
- Brand: `packages/ui/BRAND_TOKENS.md`
