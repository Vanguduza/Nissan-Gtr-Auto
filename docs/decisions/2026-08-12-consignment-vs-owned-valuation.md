# ADR: Consignment vs owned inventory valuation

- **Status:** Accepted
- **Date:** 2026-08-12
- **Related:** Phase 16 `…122000_consignment_stock.sql`, Stock/WMS DoD (`docs/DIAL_SPARE_ADOPTION_PLAN.md` §8), principal SoR ADR

## Context

Nissan holds both **company-owned** stock (`stock_levels` / MAIN warehouses) and **consignment** balances (`consignment_stock_levels`). Mixing valuation rules would invent BS assets or premature revenue.

## Decision

| Kind | Qty SoR | Balance-sheet valuation | Revenue |
|------|---------|-------------------------|---------|
| **Owned (MAIN)** | `stock_levels` | Inventory asset (e.g. 1300) at FIFO/AVG cost | On POS/invoice sale |
| **Supplier-owned consignment** | `consignment_stock_levels` (`supplier_owned`) | **Memo qty only** — not on BS until `take_ownership` (Dr 1300 / Cr AP) | Never on receive/return |
| **Customer-held consignment** | `consignment_stock_levels` (`customer_held`) | Reclass to 1320 Inventory (Customer Consignment) on `place_at_customer` | **Only** `recognize_sale` credits 4100 + COGS |

1. Principal stock SoR remains Postgres WMS — Meili / search indexes never carry saleable qty.
2. Do not roll consignment qty into owned on-hand for availability UIs without an explicit purpose filter.
3. Ledger stays append-only; corrections via reversing entries.

## Consequences

- Storefront/POS availability joins owned saleable qty (and optional labeled consignment views) — never Meili hit fields.
- Finance reports treat supplier-owned as off-BS until ownership transfer.
