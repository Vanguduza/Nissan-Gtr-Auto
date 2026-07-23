# Phase 3 — Finance core + period, bank, naming

- Status: draft
- Lane(s): `@finance_agent` (shared logic / report shapes) → `@backend_agent` (migrations, RLS, RPCs)
- Skills needed: `/accounting-ledger`
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 3

## Goal

Add posting workflow, reversing helper, statements, opening balances, period lock, naming series, and bank recon on top of the existing append-only ledger — still no tax fields and no finance UI.

## Already shipped (do not rebuild)

| Asset | Location |
|-------|----------|
| CoA + `journal_entries` / `journal_entry_lines` | `supabase/migrations/20260723100100_chart_of_accounts_ledger.sql` |
| Append-only triggers (`forbid_ledger_mutation`) | same |
| `is_reversal` / `reverses_entry_id` columns | same |
| `currency` + `exchange_rate_applied` | same |
| Finance/admin RLS (select/insert) | same |
| CoA seed (1100–5300) | `20260723100400_seed_coa_warehouses.sql` |
| `assertBalanced`, `createJournalDraft`, `saleJournalLines` | `packages/shared/src/ledger/journal.ts` |
| `currency_code` / `account_type` enums | `20260723100000_foundation_roles.sql` |

**Gap note:** balance check is app-only today; triggers block *all* UPDATEs (Draft→Submit will need a controlled exception or draft staging).

## Acceptance criteria

- [ ] Unbalanced post rejected (RPC + shared assert)
- [ ] UPDATE/DELETE on posted `journal_*` still blocked
- [ ] Statements (TB, P&L, BS, CF) match sample fixtures (USD/ZiG via stored rates)
- [ ] Opening balances produce correct trial balance
- [ ] Locked period rejects new posts
- [ ] Naming series allocates unique numbers under concurrency
- [ ] Bank recon clears matched lines without tax fields
- [ ] No tax fields anywhere

## Paths in scope

- `supabase/migrations/` — new: journal status/post RPC, reverse RPC, fiscal periods + lock, naming series, bank statement/recon, opening-balance path; RLS in same files
- `supabase/tests/` — fixtures for balance reject, period lock, statements, naming race, bank clear
- `packages/shared/src/ledger/` — reverse helper, report types, opening-balance builder; keep posting validation shared
- `packages/supabase-client/` — regen types after migrations

## Deltas only (implementation order)

1. **Journal Draft → Submit** — add `status` (`draft`|`posted`) + allow limited draft UPDATE; `post_journal` SECURITY DEFINER RPC: balance check, set `posted_by`/`posted_at`, flip to `posted`; refuse unbalanced.
2. **Reversing entry helper** — `reverse_journal(entry_id)` swaps debit/credit, sets `is_reversal`/`reverses_entry_id`; Cancel path = reverse only (no DELETE). Shared mirror in `packages/shared/src/ledger/`.
3. **Opening balances** — RPC or typed post into equity + balance-sheet accounts for go-live date; fixture proves TB.
4. **Period lock** — `accounting_periods` (or equivalent) with `locked_at`/`closed_by`; `post_journal` rejects `entry_date` in locked period.
5. **Statements** — RPCs or SQL views: Trial Balance, P&L, Balance Sheet, Cash Flow; dual currency via `exchange_rate_applied` (no live FX).
6. **Document naming series** — `naming_series` + `next_series_value(prefix)` with row lock / atomic increment; prefixes configurable for later modules.
7. **Bank reconciliation** — statement header/lines + match table to cash/bank journal lines; `clear_bank_matches` RPC; dual currency; **no tax columns**.
8. **Gates** — `/security-reviewer` (RLS/RPC) → `/verifier` (fixtures + exclusion scan).

## Out of scope

- Finance / management / web UI (“close the books” wizard UI)
- ContiPay, payment allocation (Phase 13)
- Sales/POS journals beyond helpers already in shared (Phase 5 calls post RPC)
- CoA redesign, hierarchical accounts beyond seeded flat codes
- ZIMRA / payroll tax / any tax amount or rate fields

## Risks / exclusions

- Soften immutability carefully: posted rows remain append-only; drafts must not weaken posted protection.
- Cash Flow classification: keep heuristic by `account_type` + known cash codes (1100); document assumption in migration comments.
- Naming concurrency: use `SELECT … FOR UPDATE` or upsert-increment inside one transaction.
- **Hard exclusions:** no tax fields, no ZIMRA, no payroll statutory logic.

## Handoff

1. `@finance_agent` shapes shared helpers + fixture expectations; `@backend_agent` owns migrations/RPCs/RLS
2. `/security-reviewer`
3. `/verifier`
4. `/manager` done gate → Phase 4
