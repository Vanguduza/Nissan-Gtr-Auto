# Finance — requisition lines + typed RPCs (Phase D)

- Status: **done** (MVP implemented; security/RLS/verifier recommended next)
- Prior: [`2026-07-25-finance-requisitions-period-balances.md`](./2026-07-25-finance-requisitions-period-balances.md) (Phases A–C **done**)
- Lane(s): `@backend_agent` → `@web_agent`
- Skills: `/accounting-ledger`, `/token-discipline`

## Goal

Make `/staff/finance` requisitions feel professional: multi-line expense splits, typed Supabase client (drop untyped bridges), clearer statement/requisition UX. Do **not** start PO/MR approval epic.

## Decisions

| Topic | Decision |
|-------|----------|
| Line items | `finance_requisition_lines` — description, expense GL, amount; header `amount` = Σ lines; header `expense_account_code` = first line (compat) |
| Disburse JE | One posted JE: Dr each line expense / Cr cash once for total (append-only) |
| `payment_entry_id` | **Remain reserved.** Existing `payment_entries` are AR customer receipts (`customer_id` required) — wrong model for vendor/petty disbursements. Disburse sets `journal_entry_id` only until AP payment desk epic |
| Types | Hand-patch `packages/supabase-client/src/database.types.ts` for period + requisition tables/RPCs/enums (full `supabase gen types` when local stack available) |
| PO/MR | Out of scope — separate epic |
| Android | Deferred |

## Acceptance criteria

- [x] Draft requisitions support 1+ lines via `set_finance_requisition_lines`; create seeds one line from header args
- [x] Submit refuses empty lines / amount mismatch; disburse posts multi-line balanced JE
- [x] RLS on lines (same visibility as parent); mutation via RPC guard only
- [x] `staff-finance.ts` uses typed `client.rpc` / `.from` — no `financeRpc` / `financeFrom`
- [x] Staff UI: add/remove lines on create; list shows line count + JE link after disburse
- [x] Smoke SQL covers multi-line disburse; no ZIMRA / payroll tax; journals append-only

## Paths

- `supabase/migrations/20260725260000_finance_requisition_lines.sql`
- `supabase/tests/finance_requisition_lines_smoke.sql`
- `packages/supabase-client/src/database.types.ts`
- `apps/web/lib/staff-finance.ts`, `apps/web/components/staff-finance-panel.tsx`

## Out of scope

- PO / MR approval engine
- AP payment voucher / supplier payment desk (would own `payment_entry_id` or successor)
- Android finance; ZIMRA; payroll tax
