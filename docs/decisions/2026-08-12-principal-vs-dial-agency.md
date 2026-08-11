# ADR: Principal distributor model vs DIAL Dial-a-Spare agency

- **Status:** Accepted
- **Date:** 2026-08-12
- **Related:** `docs/DIAL_SPARE_ADOPTION_PLAN.md`, DIAL D-49 / D-58 / D-57 / D-44 / D-45

## Context

DIAL Dial a Spare is a multi-supplier **agency marketplace**. Nissan GTR Auto is a single-company Zimbabwe spare-parts **distributor** with owned (+ consignment) inventory, one CoA, and no third-party seller marketplace (see `docs/decisions/2026-07-23-autodoc-shop-features.md`).

## Decision

1. Adopt Dial-a-Spare **engineering patterns** (integer money, MapLibre+OSRM delivery SoR, Temporal dispatch contracts, Resend/Brevo split, AI never writes payable amounts, D-57 FX display habits).
2. **Do not** force agency marketplace product locks (D-49 informal→B2B hide, “Sold by {Supplier}”, SUPPLIER_COOP, Mercur multi-vendor cart as primary UX, DIAL_OWNED semantics).
3. Keep Nissan ZIMRA/FDMS **hard exclusion** until an explicit counsel ticket reopens it (differs from DIAL D-40a/D-59).
4. Pattern-copy into `@gtr/*` packages — no fourth money/delivery stack; do not submodule unfinished DIAL packages.

## Consequences

- Stock/WMS SoR remains in-repo Postgres.
- Delivery job tables remain; maps/routing/dispatch SM align to DIAL D-44/D-45.
- Commercial copy and fiscal design stay principal/dealer-style.
