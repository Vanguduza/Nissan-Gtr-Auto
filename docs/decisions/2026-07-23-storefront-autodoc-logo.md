# Decision: Storefront AutoDoc-inspired IA + official logo

- **Date:** 2026-07-23
- **Status:** Accepted
- **Lane:** `@web_agent`

## Context

Phase 6 originally shipped a brand-first full-bleed art landing. Product direction shifted to spare-parts e-commerce UX closer to [autodoc.co.uk](https://www.autodoc.co.uk) (layout/IA only): dense utility chrome, logo + search, category entry, vehicle finder, and a product-list mental model.

## Decision

1. **IA / chrome:** Sticky shop header with utility strip, official logo, four-way parts search, account/cart actions, and horizontal category nav. Home continues with vehicle entry, category tiles, SKU list preview, advanced search, and visual catalog stub — plus a trust/utility footer.
2. **Brand assets:** Official logo lives at `apps/web/public/brand/logo.png` (alt: “Nissan GTR Auto”); also used as favicon/icon and on auth pages.
3. **Palette:** Black/steel headers (`#12151C` / `#1E2430`), red CTAs matching logo (`#C8102E`), silver/gray secondary (`#C0C5CE`). Barlow Condensed + Source Sans 3 via `@gtr/ui`. Explicitly avoid Inter+purple, cream-serif terracotta, and broadsheet looks.
4. **Reference limit:** Study AutoDoc for header/search/categories pattern only — do not copy their assets, copy, or trademarks.

## Consequences

- Landing is shop-first, not a minimal marketing hero.
- `@gtr/ui` tokens include silver / chrome hover vars for shared surfaces.
- Phase 7 still owns live search index binding; header search routes to `/search` stubs today.
