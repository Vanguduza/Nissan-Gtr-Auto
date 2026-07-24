# Phase 8b — RFQ, quotations, blanket POs

- Status: draft
- Lane(s): `@backend_agent` (primary); `@web_agent` (thin RFQ/quote compare + blanket release UI — **follow-on**)
- Skills needed: (none) — reuse Phase 8 procurement patterns; `/accounting-ledger` only if release posts valuation (prefer defer to existing PO→GRN)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 8b
- Prior: Phase 8 [`2026-07-24-phase8-procurement.md`](./2026-07-24-phase8-procurement.md) (`suppliers`, `purchase_orders` / lines, Draft→Submit→Cancel, supplier RLS)

## Goal

Staff RFQ → invite suppliers → collect quotations → compare and award → create PO; plus blanket/contract POs with call-off releases that cannot exceed remaining qty/value.

## Acceptance criteria

- [ ] Quotation compare selects a winner → creates PO (lines/prices from winning quote; currency + `exchange_rate_applied`)
- [ ] Blanket release cannot exceed remaining qty **or** remaining value (atomic check; reject over-release)
- [ ] RLS: suppliers see only their RFQ invite + own quotation rows (not peers’)
- [ ] RFQ / quotation / blanket docs use Draft→Submit→Cancel; naming series (`RFQ-`, `SQ-`, `BPO-` or equiv.)
- [ ] Money fields: explicit `USD`|`ZIG` + rate at transaction time
- [ ] RLS on every new table in the **same** migration; no ZIMRA / payroll tax / HTML5 QR

## Reuse (do not reinvent)

| Existing | Use for |
|----------|---------|
| `suppliers` + `profile_id` / supplier RLS helper | Invitees; quote ownership |
| `purchase_orders` / `purchase_order_lines` | Award→PO; call-off release → child PO (or typed release lines linked to blanket) |
| `procurement_doc_status`, `next_series_value` | Lifecycle + doc numbers |
| Phase 8 GRN / receipt path | Releases receive via existing PO→GRN (no new receive path) |

## Paths in scope

- `supabase/migrations/20260724060000_rfq_blanket.sql` — schema + RPCs + RLS (timestamp may bump)
- `supabase/tests/phase8b_rfq_blanket_smoke.sql` — award→PO; over-release deny; supplier RLS isolation
- `packages/supabase-client/` — types regen / RPC stubs after reset
- `packages/shared/` — status/enums only if needed

**Follow-on (`@web_agent`):** staff compare UI + supplier quote submit/read; blanket remaining qty/value display.

## Tables / RPCs (sketch)

- `rfqs` / `rfq_lines` / `rfq_suppliers` (invites)
- `supplier_quotations` / lines — one per invited supplier; link to RFQ
- `award_quotation_to_po(quotation_id, …)` — compare winner → PO
- Blanket: `purchase_orders.is_blanket` (or `blanket_orders` header) + remaining qty/value; `create_blanket_release(…)` → child PO/release lines with remaining enforcement

## Out of scope

- Re-doing Phase 8 PO/GRN/MR/landed cost; forecasting → RFQ auto-create
- Multi-winner / split-award across suppliers (single winner per award call)
- AP payment / ContiPay; management Android UI; full portal design (`/ui-ux-pro-max`)
- Tax/ZIMRA; payroll tax; browser QR

## Risks / exclusions

- NO ZIMRA, NO payroll tax; Bridge-First unchanged
- Supplier must not read peer quotes (RLS + deny in smoke)
- Blanket remaining: enforce **both** qty and value ceilings; cancel release restores remaining
- Prefer extending PO model over parallel “fake PO” tables for releases (keeps GRN path)

## Ordered tasks (`@backend_agent`)

1. [ ] Migration: `rfqs` + lines + supplier invites + series + staff/supplier RLS
2. [ ] Supplier quotations header/lines + submit RPC; supplier-scoped RLS (own rows only)
3. [ ] `award_quotation_to_po` — winner → PO lines/prices; smoke: compare→PO
4. [ ] Blanket PO flag/header + remaining qty/value + `create_blanket_release` with over-release reject
5. [ ] Smoke SQL: peer-quote denial; over-release deny; cancel restore remaining
6. [ ] Regen types; brief RPC note for `@web_agent`

## Gate

`/supabase-rls-auditor` → `/security-reviewer` → `/verifier` → `/manager` done gate

## Handoff

1. Implement in `@backend_agent`
2. Gate above
3. Optional: `@web_agent` RFQ compare + supplier quote pages
4. `/manager` for Phase 8b done
