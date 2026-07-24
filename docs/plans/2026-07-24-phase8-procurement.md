# Phase 8 — Procurement, suppliers, landed cost

- Status: **draft**
- Lane(s): `@backend_agent` (primary); `@web_agent` (thin supplier portal pages — **follow-on slice**)
- Skills needed: `/accounting-ledger` (valuation journals); `/qr-inventory-workflow` (receipt→QR via Phase 4)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 8
- Prior: Phase 4 [`2026-07-23-phase4-inventory-ops.md`](./2026-07-23-phase4-inventory-ops.md) (`post_stock_receipt`); Phase 3 [`2026-07-23-phase3-finance-core.md`](./2026-07-23-phase3-finance-core.md) (journals, `naming_series`); SMS codes in `docs/decisions/2026-07-23-manager-sms-key-events.md` (Procurement)

## Goal

Ship suppliers + PO/GRN linked to Phase 4 receipt→batch→QR, Material Request→PO conversion, and landed-cost vouchers that revalue batches and post inventory valuation journals — Draft→Submit→Cancel, multi-currency USD|ZIG.

## Acceptance criteria

- [ ] PO → receipt → stock/QR (`post_stock_receipt` / stock entry linkage; QR payload unchanged)
- [ ] Landed cost updates batch `unit_cost` + balanced inventory valuation journals (`exchange_rate_applied`)
- [ ] Material Request converts to PO without orphan lines (all MR lines map or stay on MR; no dangling PO lines)
- [ ] Supplier sees own POs only (RLS)
- [ ] Draft → Submit → Cancel; submitted immutable; cancel = reverse/cancel linkage
- [ ] Naming series (`PO-`, `MR-`, `LCV-`, `GRN-` or equiv.) via `next_series_value`
- [ ] Money fields: explicit `USD`|`ZIG` + rate at transaction time
- [ ] RLS on every new table in the **same** migration; no ZIMRA / payroll tax / HTML5 QR

## Reuse (do not reinvent)

| Existing | Use for |
|----------|---------|
| `post_stock_receipt`, `stock_batches`, `inventory_qr_codes` | GRN receive → batch + QR |
| `post_journal_entry` / `reverse_journal`, COA inventory | Landed-cost / GRN valuation journals |
| `naming_series` / `next_series_value` | Doc numbers |
| `stock_entry_status` / Draft→Submit→Cancel pattern | PO, MR, LCV, GRN lifecycle |
| `emit_domain_event` + procurement SMS codes | `po_created`, `po_received`, `supplier_mismatch` (emit only) |

## Paths in scope

- `supabase/migrations/` — new `*_procurement.sql` (+ smoke SQL)
- `packages/supabase-client/` — regen types; thin helpers if pattern exists
- `packages/shared/` — status/currency types only if needed (no UI)

**Follow-on (`@web_agent`):** thin read-only supplier portal pages (own POs / receipts) — separate slice after RLS RPCs land.

## Tables / RPCs (sketch)

- `suppliers` (+ auth link / `supplier_user` claim for RLS)
- `purchase_orders` / `purchase_order_lines` — currency, rate, status, series
- `goods_receipts` (GRN) — links PO lines → calls/wraps Phase 4 receipt
- `material_requests` / lines — convert→PO RPC (atomic; no orphans)
- `landed_cost_vouchers` / charges / allocation lines → update `stock_batches.unit_cost` + JE

## Out of scope

- **Phase 8b:** RFQ, supplier quotations, blanket/contract POs
- Full supplier portal UI polish / B2B design (`/ui-ux-pro-max` not in this slice)
- Forecasting → MR suggestions (Phase 13); consignment stock (later)
- Hardware bridges / management Android PO UI; ContiPay / AP payment runs
- Tax/ZIMRA fiscal payloads; payroll statutory anything

## Risks / exclusions

- NO ZIMRA, NO payroll tax; Bridge-First — QR only via Phase 4 + `bridges/`
- Ledger append-only — landed-cost cancel = reverse JE + restore prior batch cost snapshot (store pre-cost on voucher lines)
- Period lock: refuse submit/cancel into locked periods
- Over-receipt / price variance: emit `supplier_mismatch`; policy = reject or tolerance flag (document in migration)

## Ordered tasks (`@backend_agent`)

1. Migration: `suppliers` + RLS (staff full; supplier self-read) + grants
2. PO header/lines + `PO-` series + Draft/Submit/Cancel RPCs + supplier-scoped RLS
3. GRN: receive against PO → `post_stock_receipt` / stock entry link → stock/QR; emit `po_received`
4. Material Request + convert-to-PO RPC (transactional; assert no orphan PO/MR lines)
5. Landed cost voucher: allocate freight/duty/other → batch `unit_cost` + valuation JE (USD|ZIG)
6. Smoke SQL: PO→receipt→QR; LCV revalue+journal; MR→PO integrity; supplier RLS denial
7. Regen `database.types.ts`; brief RPC note for `@web_agent` portal follow-on

## Gate

`/supabase-rls-auditor` → `/security-reviewer` → `/verifier` → `/manager` done gate

## Handoff

1. Implement in `@backend_agent`
2. Gate above
3. Optional: `@web_agent` supplier portal read pages
4. `/manager` for Phase 8 done; Phase 8b separate plan
