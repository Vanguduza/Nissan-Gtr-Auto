# Professional finance workflows — audit + next slice

- Status: draft
- Lane(s): `@backend_agent` → `@web_agent` (optional later: `@management_app_agent`); CoA/report shapes: `@finance_agent` if shared packages touched
- Skills: `/accounting-ledger`, `/token-discipline`
- Prior (shipped): [`2026-07-23-phase3-finance-core.md`](./2026-07-23-phase3-finance-core.md), [`2026-07-25-deepen-finance.md`](./2026-07-25-deepen-finance.md), [`2026-07-25-finance-requisitions-period-balances.md`](./2026-07-25-finance-requisitions-period-balances.md) (A–C **done**), [`2026-07-25-finance-requisition-lines-typed-rpcs.md`](./2026-07-25-finance-requisition-lines-typed-rpcs.md) (D **done**)
- Cite: `.cursor/rules/finance_ledger.mdc`, `.cursor/skills/accounting-ledger/SKILL.md` — **no** finance ADRs in `docs/decisions/` for petty cash / requisitions / PO approval

## Goal

Audit finance management against QuickBooks-style imprest, requisitions, period open/close, and bank-register UX; document what already ships vs what remains; scope the next professional MVP without re-building A–D.

## Industry mapping (QuickBooks-like)

| Practice | Industry norm | Mapping here |
|----------|---------------|--------------|
| **Imprest petty cash** | Fixed float in a **Petty Cash asset**; funded from **Cash/Bank** (cheque/transfer), not from sales till as primary funder | **1110 Petty Cash** funded from **1100 Cash & Bank** (`app_settings` `finance.petty_cash_funding_account_code` → `1100`) |
| **Fund / replenish** | Dr Petty Cash / Cr Bank; replenish = sum of vouchers since last float | Float + `compute_petty_cash_replenish_amount` → draft JE Dr 1110 / Cr 1100 |
| **Spend** | Dr expense / Cr Petty Cash after approval | `disburse_finance_requisition` posts multi-line expense Dr / cash Cr |
| **Purchase orders** | Requisition → approve → PO → receive → bill → pay | Procurement: MR/PO exist; **approval status missing** (see gaps) |
| **Payment runs** | Batch AP bills → approve → write checks / ACH | **Missing** — AR `payment_entries` only; `payment_entry_id` on requisitions reserved |
| **Period open/close** | Day/month control counts; GL stays immutable | `account_period_balances` + calendar `accounting_periods` lock |
| **Account register** | Date, memo, **Debit \| Credit**, running balance | `report_account_register` + staff tables on `/staff/finance` |

**Funding decision (confirmed):** 1100 funds 1110. Do not fund imprest from 1120 Till as canonical path (till transfers remain optional ops only). Split 1100 into Bank vs Operating Cash = future CoA epic.

## Gap analysis (EXISTS vs MISSING)

### EXISTS (do not re-implement)

| Capability | Pointers |
|------------|----------|
| CoA + cash assets | `…seed_coa…` 1100; `…25210000…` 1110/1120/1130 |
| Append-only JE draft/post/reverse, USD\|ZIG | `…23210000_finance_core.sql`; skill schema |
| Calendar period lock | `accounting_periods` + `lock_accounting_period` |
| Company opening JE | `post_opening_balances` |
| Bank recon (external stmt) | `bank_statements` / lines / matches |
| Reports TB/P&L/BS/CF + CSV | finance_core + deepen-finance UI |
| Per-account trade open/close | `account_period_balances`, `open_account_period`, `close_account_period` — `…25250000…` |
| Statement-style GL register | `report_account_register` → Date \| Desc \| Debit \| Credit \| Balance \| Currency |
| Imprest funding + replenish helper | `petty_cash_funding_account_code()`, `compute_petty_cash_replenish_amount` |
| Petty/payment requisitions + lines | `finance_requisitions`, `finance_requisition_lines`; submit/approve/reject/cancel/disburse |
| Staff web UI | `apps/web/components/staff-finance-panel.tsx`, `lib/staff-finance.ts` — Petty/Till/Online tabs, period strip, Requisitions tab |
| Smoke | `supabase/tests/finance_period_balances_requisitions_smoke.sql`, `finance_requisition_lines_smoke.sql` |

### MISSING (professional gaps)

| Gap | Notes |
|-----|--------|
| **PO / MR approval chain** | `procurement_doc_status` = `draft\|submitted\|cancelled` only — no `approved`; SMS `po_approved` unused |
| **AP payment runs / vendor desk** | No AP voucher batch; requisition `payment_entry_id` reserved; AR receipts ≠ supplier pay |
| **Multi-level / amount-threshold approvals** | Single finance\|admin approve on requisitions |
| **Requisition ↔ PO link** | Finance req types = `petty_cash\|payment` only — not procurement |
| **Android Finance screen** | Credit module only; deepen-finance deferred |
| **Till session variance engine** | Explicitly deferred (no drawer hardware schema) |
| **Register PDF / bank feed** | CSV + manual bank import only |
| **CoA split of 1100** | Optional later |

**User suspicion (“not set up”):** core imprest + register + requisitions **are** implemented on web `/staff/finance`. Gaps that still feel “unprofessional” vs QuickBooks are mainly **procurement approvals** and **AP payment runs**, plus discoverability of the new tabs.

## Schema + RPC sketch (next work only)

### Phase 1 — Procurement approval (MVP first)

```text
procurement_doc_status += 'approved'  -- or parallel approve flag if enum churn is risky
approve_purchase_order(id) / reject_…  -- finance|admin|purchasing role TBD
approve_material_request(id)           -- optional same pattern
Trigger/notify: existing SMS code po_approved
RLS: same migration file; mutation via RPC guards (match finance_requisitions pattern)
```

Web: thin approve queue on existing staff procurement UI (or finance “Approvals” sub-tab linking PO ids). **Do not** invent a second requisition system for parts.

### Phase 2 — AP payment run desk

```text
ap_payment_runs (header: currency, status draft|submitted|approved|paid|cancelled, …)
ap_payment_run_lines (supplier_id, ap_bill_or_grn_ref, amount, …)
RPCs: create/submit/approve/pay_run
pay_run → append-only JE: Dr AP 2100 / Cr 1100 (per line or batched); optional link from finance_requisitions.payment_entry_id successor table
RLS finance|admin; no ZIMRA fields
```

### Already sketched / shipped (reference only)

- Period balances + register + requisitions: `…25250000…`, `…25260000…` — **done**

## UX (bank-statement register)

**Shipped:** petty/till/online + journals account filter use Debit \| Credit \| running Balance columns via `report_account_register`.

**Polish (optional, `@web_agent`):** opening/closing strip prominence; print/CSV of register; empty-state copy pointing staff to Requisitions before ad-hoc expense JEs; link from PO list to approval actions once Phase 1 lands.

## Acceptance criteria (next MVP = Phase 1)

- [ ] PO (and optionally MR): submitted → approved/rejected; approved required before “send to supplier” / convert paths product chooses
- [ ] `po_approved` SMS event can fire on approve (wire or document if deferred)
- [ ] RLS + RPC-only mutation; journals unchanged (append-only)
- [ ] No ZIMRA / payroll tax; USD\|ZIG explicit on any new money fields
- [ ] Web staff can approve/reject from procurement or finance Approvals UI
- [ ] Android finance still deferred unless product reopens

## Paths in scope (Phase 1)

- `supabase/migrations/*procurement*` (or new `…_procurement_approve.sql`), `supabase/tests/*`
- Staff procurement / finance web components under `apps/web/` (locate existing PO UI at implement time)
- Optional: `packages/shared` status helpers; SMS event wire

## Out of scope / non-goals (v1 of this plan)

- Rebuilding period balances, register RPC, or petty requisitions (already shipped)
- ZIMRA / fiscal QR / tax on docs
- Payroll tax / PAYE / NSSA
- Full multi-level approval matrix / amount thresholds (Phase 1.5+)
- AP payment runs (Phase 2 — separate gate)
- Till hardware / float variance sessions
- Android Finance screen
- CoA redesign (split 1100); bank-feed auto-import; statement PDF
- ContiPay as AP rail

## Risks / exclusions

- Do not mutate posted journals for period close or PO approve — control docs + status only
- Keep AR `payment_entries` out of vendor pay — new AP tables when Phase 2 starts
- Avoid duplicating `bank_statements` for petty daily close (already separate `account_period_balances`)
- Lane: procurement approve may be `@backend_agent` + `@web_agent`; do not spill into Android without reopen

## Phased checklist

1. **Done (baseline):** Ledger → deepen UX → period/register/imprest → requisition lines — see prior plans
2. **Next MVP:** Phase 1 PO/MR approve — `@backend_agent` then `@web_agent`
3. Phase 2 AP payment runs — new plan gate after Phase 1
4. Optional: register UX polish; Android finance parity
5. `/security-reviewer` + `/supabase-rls-auditor` on new tables → `/verifier` → `/manager`

## Handoff

1. Implement **Phase 1** in `@backend_agent` then `@web_agent`
2. `/security-reviewer` (approve RPCs + RLS) → `/verifier`
3. `/manager` done gate; open Phase 2 AP desk only after PO approve ships

### Return to manager

| Item | Value |
|------|--------|
| Plan path | `docs/plans/2026-07-25-professional-finance-workflows.md` |
| First invoke | **`@backend_agent`** — procurement `approved` status + approve/reject RPCs |
| Petty funding | **1100 → 1110** (imprest; already configured) |
| Android | Default **no** |
