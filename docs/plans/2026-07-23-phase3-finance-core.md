# Phase 3 — Finance core + period, bank, naming

- Status: **done**
- Lane(s): `@finance_agent` (shared logic / report shapes) → `@backend_agent` (migrations, RLS, RPCs)
- Skills needed: `/accounting-ledger`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 3

## Goal

Add posting workflow, reversing helper, statements, opening balances, period lock, naming series, and bank recon on top of the existing append-only ledger — still no tax fields and no finance UI.

## Acceptance criteria

- [x] Unbalanced post rejected (RPC + shared assert)
- [x] UPDATE/DELETE on posted `journal_*` still blocked
- [x] Statements (TB, P&L, BS, CF) RPCs (USD/ZiG via stored rates)
- [x] Opening balances path (`post_opening_balances`)
- [x] Locked period rejects new posts
- [x] Naming series (`next_series_value`) with row lock
- [x] Bank recon tables + `clear_bank_matches` (no tax fields)
- [x] No tax fields anywhere

## Shipped

| Artifact | Notes |
|----------|-------|
| `20260723210000_finance_core.sql` | draft/post/reverse, periods, naming, reports, bank recon |
| `20260723211000_finance_report_auth.sql` | finance-only report RPCs |
| `packages/shared/src/ledger/reverse.ts` | reverse + opening helpers |
| `supabase/tests/phase3_finance_smoke.sql` | smoke fixture |

## Handoff

Phase 3 complete → open **Phase 4** inventory ops.
