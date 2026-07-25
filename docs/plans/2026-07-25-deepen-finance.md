# Deepen finance (staff UX + Phase 13 payment gaps)

- Status: **done** (web slice; [Security](69b32b2b-da75-4404-bb8c-a1096fc80724) PASS + CSV harden; [Verifier](1766d658-c95f-40a6-9d1c-411ac2cd6b28) PASS)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) (Phase 3 Done; Phase 13 payment ACs still open)
- Prior: [`2026-07-23-phase3-finance-core.md`](./2026-07-23-phase3-finance-core.md)
- Lane(s): `@backend_agent` (RPCs **only if needed**) → `@web_agent` (primary) → `@finance_agent` (shared shapes if touched) → `@management_app_agent` **deferred** → `/security-reviewer` → `/verifier`
- Skills: `/accounting-ledger`, `/token-discipline`
- Cite (no finance ADRs): `.cursor/rules/finance_ledger.mdc`; Paynow / customer-self-pay ADRs only if payment rails touched

## Goal

Close real staff-finance gaps on existing Phase 3 + Phase 13 RPCs — register quick-ops, multi-invoice allocation UX, period-close guidance, statement CSV, ZiG rate clarity, credit bind — without greenfield till schema or Android Finance.

## Validated gaps (code)

| Area | Today | Decision |
|------|--------|----------|
| 1110/1120/1130 tabs | Line list + “use Journals” | **Thin UX**: quick journal templates (float / drop / till transfer) via `create_journal_draft` — **no** till-session tables |
| `allocate_payment` | JSONB multi-invoice OK; UI sends **one** row; same-currency enforced | **Polish UI** multi-row; keep same-currency (cross-FX allocate = deferred) |
| Store credit journals | `issue_store_credit` / post path already posts balanced JE + ledger | Surface in Payments if missing; no new schema |
| Reports | P&L/BS/CF RPCs wired; `report_trial_balance` exists, **not** in staff UI; no CSV/PDF | Wire TB + **client CSV** export (no fiscal QR) |
| Periods | Create + `lock_accounting_period` only | Guided close wizard (checklist → TB sanity → lock) |
| Reverse | Basic `reverse_journal` form | Polish (confirm, link reversal doc) |
| ZiG rate | Hidden `NEXT_PUBLIC_ZIG_EXCHANGE_RATE` via `zigExchangeRate()` | Explicit editable rate field on journal/payment forms |
| B2B credit | `/staff/crm/credit` + link from finance page | **Bind** (link + optional read-only AR aging via `kpi_ar_aging_snapshot`) — **do not** duplicate credit editor |
| Android | Credit screen only; no Finance screen | **Defer** |

Migrations already in play: `…210000_finance_core`, `…211000_finance_report_auth`, `…170000_finance_bank_recon_grants`, `…900000_payment_entries_store_credit` (+ guards).

## Acceptance criteria

- [x] Cash tabs (1110/1120/1130): quick-post float / drop-to-bank / inter-till transfer create balanced drafts (or post) using existing journal RPCs; line list remains
- [x] Payments: allocate **multiple** invoices on one draft in one submit; over-allocate still denied by RPC
- [x] Payments: currency shown as USD|ZIG; rate visible when ZIG; same-currency allocate rule unchanged
- [x] Reports: Trial Balance runnable; CSV download for TB/P&L/BS/CF (no ZIMRA/fiscal QR)
- [x] Periods: guided close flow (open period → optional TB check → lock); locked period still rejects posts
- [x] Reverse: confirm + show reversal document number / link after success
- [x] Finance page binds credit desk (link + optional aging snapshot); no second credit mutator
- [x] No ZIMRA / payroll tax; ledger append-only; any new table (unlikely) ships with RLS

## Paths in scope

- `apps/web/components/staff-finance-panel.tsx`, `apps/web/lib/staff-finance.ts`, `apps/web/app/(staff)/staff/finance/page.tsx`
- Optional thin: `packages/shared/src/ledger/**` (allocation/report row helpers)
- Optional backend (only if grant/RPC gap found during implement): `supabase/migrations/*`, smoke under `supabase/tests/`
- Read-only reuse: `kpi_ar_aging_snapshot` (AI analytics migration), existing credit link

## Out of scope

- ZIMRA / fiscal QR / tax on statements
- Payroll tax / PAYE / NSSA
- OTP / auth changes
- Greenfield till sessions, float variance workflows, hardware cash drawers
- Cross-currency payment→invoice FX conversion in `allocate_payment`
- Duplicating `StaffCreditPanel` inside finance
- Android management Finance screen
- PDF statement generation (CSV first; PDF deferred)
- Unlock / reopen locked periods

## Deferred

- Dedicated till/cash-register schema + session close variance
- Cross-currency multi-invoice allocation
- Statement PDF; bank-feed auto-import
- AP aging / supplier payment desk
- `@management_app_agent` finance parity
- Phase 13 non-finance ACs (SMS, receipts, forecasting)

## Risks / exclusions

- Do not invent till RPCs — prefer journal templates against CoA codes already seeded
- Phase 13 “multi-currency” AC = explicit USD|ZIG + `exchange_rate_applied` at payment time, **not** FX cross-allocate (document in handoff if master checklist stays unchecked for that bullet)
- Aging bind must respect existing finance/admin RPC grants — no open RLS

## Phased implementation

### Phase A — `@backend_agent` (skip if nothing missing)

1. Confirm grants for `report_trial_balance` + `kpi_ar_aging_snapshot` usable by finance staff; add **thin** migration only if EXECUTE/RLS gap.
   - **Checked (web slice):** both already `GRANT EXECUTE … TO authenticated`; finance role passes RPC asserts. No migration needed.
2. Do **not** add till tables. Do **not** loosen same-currency allocate unless product explicitly reopens that decision.

### Phase B — `@web_agent` (primary)

1. Register tabs: template actions → prefilled `createJournalDraft` (1110↔1100 drop, float in, 1120↔1110 transfer, etc.) + refresh line list. **Done**
2. Payments: multi-row allocation editor calling existing `allocatePayment` array API; show open balance hints if cheap. **Done**
3. Visible ZiG rate input (default from `zigExchangeRate()`, override per txn). **Done**
4. Reports: wire TB; CSV export buttons; currency label always on. **Done**
5. Periods: short guided close (steps + lock). **Done**
6. Reverse polish; credit bind (link + optional aging read-only). **Done**

### Phase C — `@finance_agent` (if shared)

1. Only if allocation/report CSV helpers belong in `packages/shared` rather than web-only.

### Phase D — Android

**Skip** unless product overturns deferral.

### Phase E — gates

1. `/security-reviewer` (authz on any new RPC/grant; no secret leakage in CSV)
2. `/verifier` (exclusions, lane, smoke if migration landed)

## Handoff

1. Implement: start **`@web_agent`** (no schema gap expected); invoke `@backend_agent` first only if Phase A finds a grant/RPC hole
2. `/security-reviewer` → `/verifier`
3. `/manager` done gate

### Shipped vs deferred (fill at exit)

| Item | Shipped | Deferred |
|------|---------|----------|
| Register quick-ops (journal templates) | Web (`staff-finance-panel`) | |
| Multi-invoice allocate UI | Web | |
| ZiG rate field | Web (journal / payment / quick-ops) | |
| TB + CSV exports | Web | |
| Period close wizard | Web | |
| Reverse polish | Web | |
| Credit / aging bind | Web (link + `kpi_ar_aging_snapshot`) | |
| Till session schema | | N/A / deferred |
| Cross-currency allocate | | Deferred |
| Statement PDF | | Deferred |
| Android Finance | | Deferred |
