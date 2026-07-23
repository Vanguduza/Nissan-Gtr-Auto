# Decision: AutoDoc shop-feature adoption

- **Date:** 2026-07-23
- **Status:** Accepted
- **Lane:** `@web_agent` (primary); `@data_pipeline_agent` (fitment/search); Phase 10/13 lanes for fulfillment & receipts
- **Related:** `2026-07-23-storefront-autodoc-logo.md` (IA/chrome only)

## Context

AutoDoc-style spare-parts shop UX is the reference for *which customer-facing capabilities to prioritize*, not a mandate to copy their product. Scaffold chrome already exists (Phase 6); live catalog/search and fulfillment close the gap. Agents must not re-derive adopt / later / skip from competitor research.

## Decision

Prioritize shop capabilities as below. Do not implement UK-market, marketplace, or excluded hardware/fiscal patterns.

### Adopt soon (mostly Phase 7; then 10 / 13)

| Capability | Notes / phase |
|------------|---------------|
| Fitment-aware browse + sticky garage vehicle | Phase 7 index + Phase 6 garage UX |
| Make / model / engine + VIN entry | Phase 7 search paths |
| OEM / part search + OE cross-refs | Phase 7 |
| PDP: photos, specs, OE, fitment | Phase 7 data + `@web_agent` PDP |
| Honest stock states | Inventory read APIs; no fake “always in stock” |
| Brand / category facets | Phase 7 facets |
| Core-charge on PDP | Parent-child cart; Phase 5 contract |
| USD \| ZiG prices | Explicit currency; rate at transaction |
| Click & collect vs dispatch | Phase 10 logistics modes |
| Order status / tracking | Phase 10 / 13 |
| WhatsApp / ask-counter CTA | Align with customer-receipt WhatsApp path (Phase 13) |

### Later (Phases 11 / 15 / 16 as noted)

| Capability | Phase hint |
|------------|------------|
| Alternatives strip | 15 polish or post-7 enrichment |
| Wishlist / back-in-stock notify | 15 / 16 |
| Returns portal (customer) | 15; Quarantine protocol still applies |
| Garage service reminders | 15 / 16 |
| Compare | 15 |
| Kits | 16 |
| Loyalty | 16 |
| Reviews | 15 / 16 |
| Customer apps (iOS / Android) | 11 |

### Skip (never schedule from AutoDoc parity)

| Capability | Why |
|------------|-----|
| UK plate lookup | Wrong market; VIN + make/model/engine only |
| Marketplace (3P sellers) | Single-distributor ERP |
| Pan-EU logistics branding | Local click & collect / dispatch |
| Huge DIY Club / content hub | Out of scope for ERP storefront |
| Browser / HTML5 QR | Bridge-First only |
| ZIMRA / fiscalisation | Hard exclusion |

## Phase mapping (summary)

| Bucket | Phases |
|--------|--------|
| Adopt soon | **7** (catalog/search/fitment/PDP data), then **10** (fulfillment modes, tracking), **13** (payments/receipts/WhatsApp CTA) |
| Later | **11** (customer apps), **15** (parity polish), **16** (kits, loyalty, extras) |
| Skip | Global out of scope — do not add to gap register |

## Consequences

- Storefront work after Phase 6 scaffold follows this list; do not reopen “should we build marketplace / plate lookup / DIY Club?”
- `@web_agent` binds UI to Phase 7 search/fitment APIs; does not invent browser QR or ZIMRA.
- Phase 16 kits/loyalty already cover the overlapping “later” AutoDoc extras — no duplicate gap rows required beyond this decision.
