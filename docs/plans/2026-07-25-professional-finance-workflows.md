# Professional finance workflows — multi-product research + roadmap

- Status: draft (revised — research-backed; **not** QuickBooks copy)
- Lane(s): `@backend_agent` → `@web_agent` (optional later: `@management_app_agent`); CoA/report shapes: `@finance_agent` if shared packages touched
- Skills: `/accounting-ledger`, `/token-discipline`
- Prior (shipped): [`2026-07-23-phase3-finance-core.md`](./2026-07-23-phase3-finance-core.md), [`2026-07-25-deepen-finance.md`](./2026-07-25-deepen-finance.md), [`2026-07-25-finance-requisitions-period-balances.md`](./2026-07-25-finance-requisitions-period-balances.md) (A–C **done**), [`2026-07-25-finance-requisition-lines-typed-rpcs.md`](./2026-07-25-finance-requisition-lines-typed-rpcs.md) (D **done**)
- Cite: `.cursor/rules/finance_ledger.mdc`, `.cursor/skills/accounting-ledger/SKILL.md` — **no** new ADR (1100→1110 already locked in prior plans; no durable CoA change)

## Inspired by, not copied from

Patterns below are distilled from **established retail / SME / mid-market finance products** (Xero, Sage 50/Intacct, NetSuite, Lightspeed Retail, Square cash mgmt, MYOB, Wave, Odoo Accounting, SAP Business One). We adopt **control workflows and register UX conventions** that fit a spare-parts distributor ERP (USD|ZiG, imprest petty cash, PO→AP, cash registers). We do **not** clone any vendor’s IA, screens, marketing features, bank-feed SaaS, or SuiteFlow-style approval engines.

## Goal

Keep professional requisitions & approvals, imprest petty cash with a clear funding GL, trade-period open/close, and **bank-statement-style** registers — audited against industry practice — and ship only what is still missing (procurement approve → AP pay runs), without rebuilding A–D.

---

## Research summary (product → pattern → GTR)

| Product | Relevant pattern | GTR decision |
|---------|------------------|--------------|
| **Sage 50 / textbook imprest** | Fixed float; fund from **bank/cash**; vouchers; replenish restores float; expenses recognized with control | **Adopt** imprest + bank funding. **Adapt** posting: GTR posts expense at **disburse** (Dr expense / Cr 1110) then replenish Dr 1110 / Cr 1100 — continuous GL trail (better for multi-currency audit) vs classic “expense only at replenish / 1110 untouched” |
| **MYOB** | Bank Register: Withdrawal \| Deposit (or Dr\|Cr) + **running balance**; Spend Money / Pay Bills | **Adopt** statement-style register columns (already shipped via `report_account_register`) |
| **Xero** | Bank feed match UI; statement opening anchor; Spend/Receive money; period lock light | **Adapt** recon already via `bank_statements` matches. **Skip** bank-feed auto-rules / SaaS matching UX as MVP |
| **Odoo Accounting** | Vendor bill confirm → Register Payment (batch optional) → bank reconcile; JE shows Dr/Cr | **Adopt** bill→pay→recon spine for Phase 2 (simplified). **Skip** full Odoo journal-per-payment-method sprawl |
| **NetSuite (retail/AP)** | PO / receive / **vendor bill approval** before pay; **payment batch** approval limits | **Adopt** approve-before-pay + batch pay run. **Skip** SuiteFlow multi-level amount hierarchies / ACH automation |
| **SAP Business One** | Payment Wizard (batch outgoing); optional interim clearing; BSP bank stmt | **Adopt** batch payment-run concept. **Skip** country payment files, interim-account choreography |
| **Lightspeed Retail** | Register open float / close count; Cash in-out vs Petty out (POS drawer) | **Adapt** daily open/close **control** to `account_period_balances` on 1110/1120. **Do not** fund imprest from sales till as canonical path; keep **1110 ≠ 1120** |
| **Square / Shop cash mgmt** | Drawer expected vs counted; session variance | **Skip** hardware drawer variance engine (already deferred); till sessions stay out |
| **Wave** | Simple income/expense; weak approval depth | **Skip** as approval model — too thin for distributor controls |
| **Sage Intacct** | Dimensional close, multi-entity, AP bill approval | **Skip** dimensions / multi-entity until needed; single-entity GTR |

### Transferable workflows (mapped)

1. **Cash & petty imprest + funding GL** — Industry: Bank/Cash → Petty Cash float. GTR: **1100 → 1110** (`finance.petty_cash_funding_account_code`). Till **1120** = sales drawer only (ops transfers optional, not canonical funder).
2. **Requisition → approve → disburse** — Sage/NetSuite/Odoo expense or payment request pattern. GTR: `finance_requisitions` + lines (**done**).
3. **PO / bill approval before payment** — NetSuite bill approve; Odoo confirm bill before pay. GTR: PO `approved` status **missing** (Phase 1); AP bill desk **missing** (Phase 2).
4. **Period open/close + opening/closing balances** — Lightspeed float open/close (ops) + accounting period lock (Xero/Sage/Odoo). GTR: `account_period_balances` + `accounting_periods` lock (**done**).
5. **Bank/cash register UX** — **Debit \| Credit** (or Withdrawal \| Deposit) + running balance: **MYOB Bank Register**, classic **Sage 50** account registers, GL inquiry in **SAP B1** / **Odoo** journal items. Xero recon UI is match-oriented (not the target). GTR already ships this shape.
6. **What NOT to adopt** — Bank-feed AI rules; NetSuite SuiteFlow limits; SAP payment-file/interim stacks; Lightspeed/Square drawer-as-GL; Wave’s no-approval simplicity; CoA dimension explosion; ContiPay as AP rail; ZIMRA / payroll tax.

**Funding decision (unchanged):** 1100 funds 1110. Split 1100 Bank vs Operating Cash = future CoA epic only.

---

## Gap analysis (EXISTS vs MISSING)

### EXISTS (do not re-implement)

| Capability | Pointers |
|------------|----------|
| CoA + cash assets | 1100; 1110/1120/1130 |
| Append-only JE, USD\|ZIG | finance_core |
| Calendar period lock | `accounting_periods` |
| Company opening JE | `post_opening_balances` |
| Bank recon (external stmt) | `bank_statements` / matches |
| Reports TB/P&L/BS/CF + CSV | finance_core + deepen-finance |
| Per-account trade open/close | `account_period_balances` |
| Statement-style GL register | `report_account_register` → Date \| Desc \| Debit \| Credit \| Balance \| Currency |
| Imprest funding + replenish | `petty_cash_funding_account_code`, `compute_petty_cash_replenish_amount` |
| Petty/payment requisitions | submit → approve → disburse |
| Staff web UI | `/staff/finance` — cash tabs, period strip, Requisitions |
| Smoke | period/requisition/line smoke SQL |

### MISSING (professional gaps)

| Gap | Notes | Priority |
|-----|-------|----------|
| **PO / MR approval** | `draft\|submitted\|cancelled` only; SMS `po_approved` unused | **Phase 1 (MVP)** — may be in progress |
| **AP payment runs / vendor desk** | No batch AP pay; AR `payment_entries` ≠ supplier pay | **Phase 2** |
| **Register / period discoverability polish** | Columns exist; strip/print/empty-states weak | **Phase 0 optional** (parallel, `@web_agent`) |
| Multi-level / threshold approvals | Single finance\|admin | Phase 1.5+ **skip** until abuse |
| Finance req ↔ PO link | Types = `petty_cash\|payment` only | Skip — keep procurement separate |
| Android Finance | Deferred | Stay deferred |
| Till variance engine | No drawer schema | Skip |
| Bank feed / register PDF | CSV + manual import | Skip MVP |
| CoA split of 1100 | Optional later | Skip |

**Feel “unprofessional” vs peers:** not missing imprest/register/requisitions — missing **procure-to-pay approve** and **AP payment batch**, plus register tab discoverability.

---

## Target UX (bank-statement style)

**Already the standard for cash GLs (1110/1120/1130) and filtered journals:**

| Date | Description / doc # | Debit | Credit | Running balance | Currency |

- Opening balance row (period strip) at top; closing when period closed.
- Separate Debit and Credit columns (never a single signed “Amount” as primary).
- Inspired by **MYOB Bank Register** + classic **Sage** account registers — not Xero’s feed-match pane.

**Polish (optional):** print/CSV of register; empty-state → Requisitions before ad-hoc expense JEs; Approvals entry from PO list once Phase 1 lands.

---

## Schema sketch (remaining work only)

### Phase 1 — Procurement approval (MVP)

```text
procurement_doc_status += 'approved'
approve_purchase_order / reject_…  -- finance|admin|purchasing TBD
optional: approve_material_request
Wire or defer SMS po_approved
RLS + RPC-only mutation (same migration); journals untouched
```

Web: thin approve on existing procurement UI (or finance Approvals linking PO ids). **No** second requisition system for parts.

### Phase 2 — AP payment run desk

```text
ap_payment_runs + lines → submit/approve/pay
pay → append-only JE Dr AP 2100 / Cr 1100 (currency explicit)
Do not reuse AR payment_entries for vendors
```

### Phase 0 (optional polish) — register discoverability only; no schema.

---

## Acceptance criteria (Phase 1 MVP)

- [ ] PO (optional MR): submitted → approved/rejected; approved gate before send/convert as product chooses
- [ ] `po_approved` SMS: wire or explicitly defer in PR
- [ ] RLS + RPC-only; append-only journals
- [ ] No ZIMRA / payroll tax; USD\|ZIG on new money fields
- [ ] Staff can approve/reject from web procurement or finance Approvals
- [ ] Android finance still deferred

## Paths in scope (Phase 1)

- `supabase/migrations/*procurement*` (or new `…_procurement_approve.sql`), tests
- Staff procurement / finance under `apps/web/`
- Optional: `packages/shared` status helpers; SMS wire

## Out of scope

- Rebuild period balances, register RPC, petty requisitions
- ZIMRA / payroll tax / HTML5 QR
- Multi-level approval matrices; bank-feed AI; SAP payment files
- Till hardware variance; Android Finance; CoA split 1100; ContiPay AP
- Textbook-only imprest (expense-only-at-replenish) — keep GTR continuous 1110 trail

## Risks / exclusions

- Do not mutate posted journals for close or PO approve
- Keep AR receipts out of vendor pay
- Do not duplicate `bank_statements` for petty daily close
- Phase 1 may already be mid-flight — plan only; no product code from Planner

## Priority changes vs prior MVP

| Prior plan | After research |
|------------|----------------|
| MVP = PO approve | **Unchanged** — still the largest gap vs NetSuite/Odoo/SAP P2P |
| AP payment runs = Phase 2 | **Unchanged** — still next; align to Odoo batch pay / SAP Payment Wizard **simplified** |
| QuickBooks-framed mapping | **Replaced** with multi-product table; QB is not the north star |
| Imprest “as QB” | **Clarified**: industry imprest + **adapt** continuous Dr/Cr on 1110 (keep current design) |
| Register as QB-like | **Reframed**: MYOB/Sage statement columns (already shipped); polish demoted optional Phase 0 |
| New funding ADR | **Not needed** — 1100→1110 stands |

## Phased checklist

1. **Done:** Ledger → deepen UX → period/register/imprest → requisition lines
2. **Optional Phase 0:** Register/period UX polish (`@web_agent`)
3. **Next MVP Phase 1:** PO/MR approve — `@backend_agent` then `@web_agent` (may be in progress)
4. **Phase 2:** AP payment runs — new gate after Phase 1
5. `/security-reviewer` + `/supabase-rls-auditor` → `/verifier` → `/manager`

## Handoff

1. Implement **Phase 1** in `@backend_agent` then `@web_agent` (skip if another agent already owns it)
2. `/security-reviewer` → `/verifier`
3. `/manager` done gate; open Phase 2 only after PO approve ships

### Return to manager

| Item | Value |
|------|--------|
| Plan path | `docs/plans/2026-07-25-professional-finance-workflows.md` |
| First invoke | **`@backend_agent`** — procurement `approved` + approve/reject RPCs (if not already underway) |
| Top adopted patterns | See manager return below |
| Petty funding | **1100 → 1110** (unchanged) |
| Android | Default **no** |
| ADR | None — plan update only |
