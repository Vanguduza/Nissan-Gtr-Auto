# Decision: AutoDoc shop-feature adoption

- **Date:** 2026-07-23 (amended same day — full soon+later committed to storefront IA)
- **Status:** Accepted
- **Lane:** `@web_agent` (primary); `@data_pipeline_agent` (fitment/search); Phase 10/13 lanes for fulfillment & receipts
- **Related:** `2026-07-23-storefront-autodoc-logo.md`

## Context

AutoDoc-style spare-parts shop UX is the reference for customer-facing capabilities. **Adopt soon** and **adopt later** are both scheduled into the storefront / account module (UI shells now; live data as phases land). Skip list unchanged.

## Decision

### My Account module

**My Garage** lives under **My Account** (`/account/garage`), not a top-level nav peer. Account hub: `/account`.

### Adopt soon — storefront IA

| Capability | Route / surface |
|------------|-----------------|
| Fitment-aware browse + sticky garage vehicle | Sticky bar sitewide; filters PLP/PDP |
| Make / model / engine + VIN | `/vehicle` + header search modes |
| OEM search + OE cross-refs | `/search`, PDP OE tab |
| PDP: photos, specs, OE, fitment, core charge, stock, USD\|ZiG | `/parts/[oem]` |
| Honest stock states | Stock badge component |
| Brand / category facets | `/catalog` PLP |
| Click & collect vs dispatch | Checkout / `/account/orders` fulfillment choice |
| Order status / tracking | `/account/orders`, `/account/orders/[id]` |
| WhatsApp / ask-counter CTA | Shared CTA on PDP + account |

### Adopt later — under My Account (or shop)

| Capability | Route |
|------------|-------|
| Wishlist / back-in-stock | `/account/wishlist` |
| Returns portal | `/account/returns` |
| Garage service reminders | `/account/garage` (reminders panel) |
| Compare | `/account/compare` |
| Kits | `/kits` |
| Loyalty | `/account/loyalty` |
| Reviews | On PDP (stub) + `/account/reviews` |
| Alternatives strip | On PDP |
| Customer apps | Phase 11 (link from account) |

### Skip (never)

UK plate · marketplace · pan-EU branding · DIY Club · HTML5 QR · ZIMRA

### Fonts & styles

- **Display / chrome:** Titillium Web via `next/font` → `--font-display-loaded`
- **Body:** Source Sans 3 via `next/font` → `--font-body-loaded`
- **Wiring:** CSS uses `var(--font-*-loaded)` first; never rely on an unloaded `"Titillium Web"` string alone
- **Palette:** steel `#12151C`, red `#C8102E`, silver `#C0C5CE`, chalk content ground
- Avoid Inter / purple / cream-serif / broadsheet

### Account personal data

- `/account/profile` — name + contact details
- `/account/addresses` — delivery / billing addresses
- My Garage remains `/account/garage`

### Vehicle selector

Sequential cascading selects: maker → model → generation → engine (each list filtered by prior choice). VIN alternate path. No UK plate lookup.

## Consequences

- Agents implement account + shop routes above; do not re-litigate Garage as top-level-only.
- Backend binding follows Phases 7 / 10 / 13 / 16; UI may ship stubbed.
