# Finance — requisitions, period balances, statement-style register

- Status: **done** (Phases A–C thin slice; security PASS WITH NOTES + harden; RLS PASS; verifier PASS WITH NOTES)
- Lane(s): `@backend_agent` → `@web_agent` (Android: **no** unless product reopens)
- Skills: `/accounting-ledger`, `/token-discipline`
- Prior: [`2026-07-23-phase3-finance-core.md`](./2026-07-23-phase3-finance-core.md), [`2026-07-25-deepen-finance.md`](./2026-07-25-deepen-finance.md) (**done**), CoA tabs in [`2026-07-25-staff-module-tabs-and-dispatch-auto-assign.md`](./2026-07-25-staff-module-tabs-and-dispatch-auto-assign.md)
- Cite: `.cursor/rules/finance_ledger.mdc`; no finance ADRs in `docs/decisions/` for petty cash / requisitions

## Goal

Ship a thin vertical slice on `/staff/finance`: per-account trade-period open/close balances, bank-statement-style GL registers (debit | credit columns + running balance), imprest petty cash funded from Cash & Bank (1100→1110), then requisition→approval→disbursement for petty cash & payments — without boiling the ocean on PO approvals.

## Industry mapping (imprest + QuickBooks-like)

| Practice | Mapping here |
|----------|----------------|
| **Imprest petty cash** | Fixed float held in **1110 Petty Cash** (asset). Funded from **1100 Cash & Bank** (operating / cash-at-bank). Industry default: bank cheque/transfer → petty cash box; not from till sales (1120) as primary funder. |
| **Fund float** | Dr 1110 / Cr 1100 (existing quick-op “Float into 1110 from bank”). |
| **Spend from box** | Dr expense (e.g. 5300) / Cr 1110 — only after approved requisition (Phase C). |
| **Replenish** | Sum of approved spends since last replenish → Dr 1110 / Cr 1100 (restore float); optional “write check” UX = same JE. |
| **Daily open/close** | Snapshot opening + closing cash-on-hand for 1110 (and optionally 1120) per trade day; variance noted; **no** mutation of posted journals — close records are control docs, JE remain append-only. |
| **QB Write Checks** | Payment / disbursement JE against AP or expense + bank (1100). |
| **QB Expenses** | Expense JE (5300…) + credit cash/bank/petty. |
| **QB Reconcile** | Existing `bank_statements` / lines / matches (keep; do not reinvent). |
| **QB Account Register** | New **GL register** view: date, memo/doc #, debit col, credit col, running balance, currency — not a single “amount” signed column. |

**Funding account decision (v1):** `1100` is the canonical funding GL for petty cash imprest. Optional later: split 1100 into Bank vs Operating Cash subcodes — out of scope until CoA redesign epic.

## What exists vs gaps

| Area | Exists | Gap |
|------|--------|-----|
| CoA | 1100 Cash & Bank; 1110/1120/1130 seeded (`…seed_coa…`, `…25210000_staff_ops…`) | No formal “funding_account_code” metadata on 1110 |
| Ledger | Append-only JE + lines; draft/post/`reverse_journal`; USD\|ZIG + rate | — |
| Opening balances | `post_opening_balances` = company-wide opening JE | Not per-account trade-period open/close |
| Periods | `accounting_periods` date-range + `lock_accounting_period` | Calendar lock only — **no** per-account period balance rows |
| Bank stmt | `bank_statements.opening_balance` / `closing_balance` + recon | External bank import, not GL register / petty daily close |
| Register UI | Petty/till tabs + Dr/Cr columns; quick float/drop | Last 40 lines by `id`; no date/memo/running bal; no period open/close strip |
| Reports | TB / P&L / BS / CF + CSV (deepen-finance) | No `report_account_register` RPC |
| Requisitions | **None** | No tables/RPCs for petty/payment/PO approval chain |
| Procurement PO | `draft` → `submitted` → `cancelled`; SMS code `po_approved` unused | No `approved` status / approve RPC — **heavy**; prefer separate epic |
| Android finance | Deferred in deepen-finance | Stay deferred |

**Schema blockers for Phase A:** no `account_period_balances` (or equiv.); `listJournalLinesForAccount` omits `entry_date` / description / ordered running balance — needs RPC or join, not client-only guesswork.

## Phased MVP

### Phase A — Period open/close + statement-style register *(ship first)*

**Backend (`@backend_agent`)**

1. Migration: `account_period_balances` (or `gl_trade_periods` + balance rows) — `account_code`, `currency`, `period_start`/`period_end` (daily allowed), `opening_balance`, `closing_balance` (nullable until closed), `status` open|closed, `opened_by`/`closed_by`, `notes`; **RLS finance|admin in same file**.
2. RPCs: `open_account_period`, `close_account_period` (closing = opening + Σ posted Dr − Cr in range; refuse if already closed; **do not** UPDATE journal rows). Optional variance field if physical count provided.
3. RPC: `report_account_register(account, from, to, currency?)` → date, document_number/description, debit, credit, running_balance, currency, journal_entry_id — finance|admin only.
4. Smoke SQL covering open → post JE → close math + register order.

**Web (`@web_agent`)**

1. Register tabs (1110/1120/1130 + Journals account filter): statement table (Date | Description | Debit | Credit | Balance | Currency); open/close period controls + opening/closing strip.
2. Prefer professional `/staff/finance` polish over new pages.

### Phase B — Petty cash imprest + funding + daily open/close

1. Document/config: `1110.funding_account_code = 1100` (CoA metadata column or `finance_settings` key — thin).
2. Daily open/close UX defaulted to 1110 (reuse Phase A RPCs); float/replenish templates stay Dr/Cr via existing journal RPCs.
3. Replenish helper: sum credits to 1110 (spends) since last replenish → draft JE Dr 1110 / Cr 1100 (post still finance-gated).
4. Still **no** till-session hardware / cash-drawer variance engine (deepen-finance deferral stands).

### Phase C — Requisition / approval (petty + payments)

1. Tables: `finance_requisitions` (+ lines) — type `petty_cash` | `payment`; amount, currency, rate, payee/memo, status `draft|submitted|approved|rejected|disbursed|cancelled`; approver, timestamps; link `journal_entry_id` / `payment_entry_id` when disbursed; **RLS** staff create/read own + finance|admin approve.
2. RPCs: submit / approve / reject / disburse (disburse posts balanced JE or payment entry — append-only).
3. Web: Requisitions tab on `/staff/finance` — list + approve queue + “disburse” for finance.
4. **Procurement PO approvals:** stub only (link to existing PO submit) **or** separate epic — do **not** expand `procurement_doc_status` in this MVP unless trivial; SMS `po_approved` remains unused until that epic.

## Acceptance criteria

### Phase A
- [ ] Open/close per account+currency+date range; closed row stores opening & closing; reopen only via new period (no edit of closed balances)
- [ ] Closing balance = opening + posted activity (Dr−Cr for assets); locked calendar period still blocks new JE posts
- [ ] Register RPC/UI: separate Debit and Credit columns + running balance; USD|ZIG explicit
- [ ] RLS on new tables; no ZIMRA / payroll tax; journals remain append-only

### Phase B
- [ ] 1110 funded from 1100 in product copy + float/replenish templates
- [ ] Daily open/close for 1110 works end-to-end in staff UI
- [ ] Replenish draft JE balanced and reversible via existing reverse path

### Phase C
- [ ] Petty + payment requisitions: submit → approve/reject → disburse posts JE/payment
- [ ] Disbursement cannot skip approval; reject/cancel leave no silent ledger edits (reverse if needed)
- [ ] PO approval not required for Phase C done gate (stub or out-of-scope epic)

## Paths in scope

- `supabase/migrations/*` (period balances + register + requisitions), `supabase/tests/*`
- `apps/web/components/staff-finance-panel.tsx`, `apps/web/lib/staff-finance.ts`, `apps/web/app/(staff)/staff/finance/page.tsx`
- Optional: `packages/shared/src/ledger/**` register/requisition types
- Read-only reuse: Phase 3 JE RPCs, CoA 1100/1110, bank recon, deepen-finance tabs

## Out of scope (v1)

- ZIMRA / fiscal QR / tax on any doc
- Payroll tax / PAYE / NSSA
- Full PO / MR multi-level approval engine (separate epic)
- Till hardware drawers, float variance sessions, Android Finance screen
- Bank-feed auto-import; statement PDF; unlock locked calendar periods
- CoA redesign (split 1100); cross-currency allocate; AP payment runs desk
- AI forecasting → requisitions

## Risks / exclusions

- Do not store period balances by mutating JE lines — control table + computed close only
- Requisition approve must be finance|admin (or explicit approver role); RLS fail-closed
- Multi-currency: one currency per period row; converted txns store `exchange_rate_applied` on JE
- Avoid duplicating bank_statements for petty daily close — different purpose (control vs external recon)

## Handoff

1. **`@backend_agent` Phase A schema + register RPC first** (blocking)
2. `@web_agent` Phase A UI slice on `/staff/finance`
3. Repeat B → C with same lane order
4. `/security-reviewer` (RLS + approve/disburse RPCs) → `/supabase-rls-auditor` on new tables → `/verifier`
5. `/manager` done gate per phase
6. **Follow-on Phase D (done):** [`2026-07-25-finance-requisition-lines-typed-rpcs.md`](./2026-07-25-finance-requisition-lines-typed-rpcs.md) — line items + typed RPCs; `payment_entry_id` still reserved for AP desk

### Return to manager

| Item | Value |
|------|--------|
| Plan path | `docs/plans/2026-07-25-finance-requisitions-period-balances.md` |
| First invoke | **`@backend_agent` Phase A** (period balances + `report_account_register`) |
| Android | Default **no** |
