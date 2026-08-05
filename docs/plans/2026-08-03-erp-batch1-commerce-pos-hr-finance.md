# ERP Batch 1 — Commerce/POS · HR · Finance

- Status: **Batch 1 complete (A–I) + follow-ups landed** — multi-approver requisitions, HR onboarding wizard UI, branded pdf-lib docs (statement/payslip/ID/biz card), AI worker schedules + marketing opt-in UI. Phase 0–2 + split-bill web done earlier; companion Realtime, module_access nav gate, Android split-bill, finance refunds/audit/Accounts nav, `@gtr/documents`, HR onboarding + password reset Edges, leave + payslip schedule (gross only), park cart + pickup/delivery UX.
- Date: **2026-08-03**
- Brief: [`erp-batch1-commerce-pos-hr-finance-prompt.md`](../../erp-batch1-commerce-pos-hr-finance-prompt.md) (repo root)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md)
- Prior (reuse, do not rebuild): Phase 3 finance core, deepen-finance, requisitions A–D, Phase 5 POS, Phase 9 gross payroll, EcoCash direct C2B ([`2026-08-03-ecocash-direct-c2b-cross-platform.md`](./2026-08-03-ecocash-direct-c2b-cross-platform.md)), mobile storefront parity ([`2026-07-27-mobile-storefront-parity.md`](./2026-07-27-mobile-storefront-parity.md))
- Decisions: [`docs/decisions/2026-08-03-ecocash-direct-c2b.md`](../decisions/2026-08-03-ecocash-direct-c2b.md), storefront logo ADR, Paynow / ContiPay / customer-self-pay ADRs
- Lane sequencing (per phase): `/manager` → one coding lane → `/security-reviewer` → `/verifier`; migrations also `/supabase-rls-auditor`
- Skills (on trigger only): `/accounting-ledger` (Phases 0, 4–5, 7), `/token-discipline`, `/qr-inventory-workflow` (Phase 1 Bridge QR)

## Goal

Ship Batch 1 from the redesign brief: Finance CoA plain-English + per-tender GL accounts (unlocking split-bill), HR organogram → module access, unified POS (auto `create_pos_cart`), shared branded documents, then onboarding/credentials, requisition thresholds, payroll automation, and mobile parity — without violating standing exclusions.

## Hard constraints (every phase)

| Constraint | Implication |
|------------|-------------|
| **No ZIMRA / fiscalisation** | No FDMS, fiscal QR, tax-authority payloads on invoices/receipts/statements |
| **No payroll tax** | Gross + manual deductions only; no PAYE/NSSA/brackets/P4 |
| **Bridge-First** | Camera / QR scan / biometric / GPS / ESC/POS only via `bridges/`; web may *display* QR (e.g. pairing) but never capture camera |
| **RLS** | Every new table gets policies in the **same** migration |
| **Append-only ledger** | Corrections via reversing/contra JEs only; never `UPDATE`/`DELETE` posted lines |
| **Staff IA = sidebar submenu only** | Extend `STAFF_NAV_TREE` children; no new in-page sibling tab strips (unified POS panels are OK) |
| **RPC + RLS authority** | Thin clients; no client-side permission invention |
| **Multi-currency** | Money fields carry `USD` \| `ZIG` + `exchange_rate_applied` at transaction time |

If a brief item conflicts with a constraint, **flag the conflict** — do not silently break the constraint.

## What already exists (adopt-first)

| Area | Pointers |
|------|----------|
| CoA | `chart_of_accounts` (`code`, `name`, `account_type`, `is_active`) — `20260723100100_chart_of_accounts_ledger.sql`; seed `1100` Cash & Bank; later `1110` Petty Cash, `1120` Cash Sales Till, `1130` Online Payment Clearing (`20260725210000_staff_ops_dispatch_auto_assign.sql`) |
| Payment settle | `create_payment_entry` / post JE currently debits hardcoded **`1100`** for non–store-credit tenders (`20260724090000_payment_entries_store_credit.sql`) — **no per-method mapping yet** |
| EcoCash | FastAPI: `services/whatsapp-flows/app/services/ecocash_*.py`, `…/api/v1/ecocash_webhook.py`; Edge: `supabase/functions/ecocash-initiate`, `ecocash-webhook`; SoR: `20260803030000_ecocash_cross_platform_intents.sql` (`mark_ecocash_settled` → `create_payment_entry(…, 'ecocash', …)`) |
| Requisitions | `finance_requisitions` + lines; enum `petty_cash` \| `payment`; draft→submit→approve→disburse — `20260725250000_*`, `20260725260000_*` |
| POS | RPCs `create_pos_cart`, `checkout_pos_cart`, `create_pos_scan_session`; web `staff-pos-*.tsx` / `staff-pos.ts` (manual **Create cart** + companion **polling**); Android `feature/pos` + `RpcNames` |
| Staff nav | `STAFF_NAV_TREE` in `apps/web/lib/staff-auth.ts` (Finance tabs: petty-cash, cash-sales, online-sales, …; HR desk leaf) |
| HR/payroll | `employees`, `salary_structures`, `attendance_events`, `payroll_*` — `20260724070000_hr_gross_payroll.sql` (no organogram / grades / leave / forced password) |
| Receipts PDF | `supabase/functions/process-customer-receipts` + `_shared/receipt_pdf.ts` |
| Bridges | QR `bridges/android/qr-scanner`; biometric contract stub `bridges/contracts/biometric.ts`; POD signature `bridges/android/pod-signature` |
| Auth | Email-or-phone login helpers in `apps/web/lib/auth-otp.ts`; `auth-otp` Edge is signup/OTP — **not** forgot-password |

## Suggested build sequence → numbered phases

Maps 1:1 to the brief’s suggested order, with Phase 0 / Phase 1 called out explicitly.

| Phase | Brief refs | Summary |
|-------|------------|---------|
| **0** | **3.3, 3.4** | `display_name` + per-method accounts; EcoCash settle → EcoCash GL |
| **1** | **1.2** (layout + auto-cart) | Unified POS; invisible `create_pos_cart` |
| **2** | 2.1, 2.2 | HR organogram + role/contract templates |
| **3** | 1.4, 1.5 (+ remaining 1.2 polish) | Bridge QR→cart; companion Realtime pairing |
| **4** | 2.4 + 3.2 | Shared branded document service + statement PDFs |
| **5** | 1.3 | Split-bill / multi-tender checkout (depends on Phase 0) |
| **6** | 2.3, 2.5, 2.6 | Onboarding wizard, credentials, password reset |
| **7** | 3.1, 3.5–3.10 | Finance nav/Accounts, refunds, requisition types/thresholds, audit, registers, human refs |
| **8** | 2.7, 2.8 | My Profile, leave, payslip automation |
| **9** | 1.6–1.8 | POS role gating from organogram; WhatsApp **audit**; mobile parity workstream |

**Early parallel (cheap):** Phase 9’s WhatsApp commerce **audit** (brief §1.7) may run any time after Phase 0 awareness — confirm gaps only; no greenfield WA shop.

---

## Phase 0 — Finance foundation: `display_name` + per-method accounts + EcoCash GL

**Brief:** §3.3 Chart of accounts plain English; §3.4 per-payment-method multi-currency accounts; EcoCash settlements post to EcoCash’s own GL from the direct integration (not Paynow/ContiPay payloads).

**Lanes:** `@backend_agent` → `@finance_agent` (shared shapes if needed) → `@web_agent` (labels in finance UI) → `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`

### Work

1. **`display_name` on CoA** — add column (backfill from current `name`); UI/reports/registers/dropdowns show `display_name` first; `code` secondary. Keep `name` as technical/legacy or fold carefully without breaking FKs.
2. **Seed / split per-method asset accounts** (USD|ZiG dual-currency pattern already on ledger lines) — dedicated GLs for:
   - Cash (till / physical — align with today’s `1120` or clearer child under operating cash)
   - EcoCash
   - Paynow
   - ContiPay  
   Keep `1100` as main operating / sweep target; `1110` imprest relationship unchanged (`petty_cash_funding_account_code`).
3. **Tender → account map** — replace hardcoded `v_cash_acct := '1100'` in payment-entry post path with a lookup (config table or RPC helper keyed by `payment_tender`: `cash` | `ecocash` | `paynow` | `contipay` | …).
4. **EcoCash settle → EcoCash GL** — `mark_ecocash_settled` already creates PE with tender `ecocash`; ensure post JE debits the **EcoCash** account from step 2/3. Same for Paynow/ContiPay webhook settle paths. Do **not** derive EcoCash balances from Paynow/ContiPay intents.
5. **Sweep / imprest-style** — document + thin journal templates (or RPC helpers) to sweep method accounts → `1100` periodically (same idea as 1100→1110).

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Migration(s) | New `supabase/migrations/20260803*_coa_display_name_payment_method_accounts.sql` (ALTER `chart_of_accounts`, seed EcoCash/Paynow/ContiPay codes, tender→account helper, update `create_payment_entry` / post JE cash debit; RLS if new map table) |
| Existing migrations to read | `20260723100100_chart_of_accounts_ledger.sql`, `20260723100400_seed_coa_warehouses.sql`, `20260725210000_staff_ops_dispatch_auto_assign.sql` (1110/1120/1130), `20260724090000_payment_entries_store_credit.sql`, `20260724095000_paynow_payment_intents.sql`, ContiPay settle sibling, `20260803030000_ecocash_cross_platform_intents.sql` |
| Edge / satellite (settle only — no second SoR) | `supabase/functions/ecocash-webhook/index.ts`, `supabase/functions/ecocash-initiate/index.ts`; WhatsApp adapter `services/whatsapp-flows/app/services/ecocash_*.py`, `…/api/v1/ecocash_webhook.py` |
| Web finance UI | `apps/web/components/staff-finance-panel.tsx`, `apps/web/lib/staff-finance.ts`, `apps/web/app/(staff)/staff/finance/page.tsx` — primary label = `display_name` |
| Shared | `packages/shared/src/ledger/**` if account helpers live there; regen `packages/supabase-client/src/database.types.ts` |
| Tests | Extend `supabase/tests/phase3_finance_smoke.sql`, payment/EcoCash smokes; assert EcoCash PE → EcoCash account_code |

### Acceptance (Phase 0)

- [ ] Every staff-facing CoA label prefers `display_name` over bare numeric code
- [ ] Cash / EcoCash / Paynow / ContiPay each have distinct active CoA rows (multi-currency via existing line currency)
- [ ] `mark_ecocash_settled` → Payment Entry → JE debit lands on **EcoCash** GL, not generic `1100` / Paynow / ContiPay
- [ ] No ZIMRA; ledger remains append-only; new tables (if any) ship with RLS

---

## Phase 1 — POS unified layout + auto-create cart

**Brief:** §1.2 — one screen with search + catalog + cart zones; call `create_pos_cart` automatically on mount (or first add); do **not** delete the RPC.

**Lanes:** `@web_agent` (primary) → `@management_app_agent` (Android parity) → `/verifier` (Bridge grep on web)

### Work

1. Redesign `apps/web/app/(staff)/staff/pos/` into three **panels** (not tab strips): Search/filter (`search_catalog` + SKU quick-add), Catalog (join `stock_levels` for live qty), Cart (Checkout / Clear / Save order stub if park not ready — park may land with Phase 5 or thin Phase 1 “hold” if RPC exists; otherwise stub UI + follow Phase 5).
2. On POS mount (or first line add): invisibly call existing `create_pos_cart` via `createPosCart` in `staff-pos.ts`; hide **Create cart** CTA.
3. Preserve prep/online flows as **sidebar** submenu leaves (`STAFF_NAV_TREE` POS children), not in-page tabs that violate IA — if current `?tab=cart|prep` remains, treat prep as separate route/leaf, not a third sibling strip on the unified sale page.
4. Android management: same auto-cart on `PosScreen` / `PosViewModel` using `RpcNames.CREATE_POS_CART`.

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Web | `apps/web/app/(staff)/staff/pos/page.tsx`, `apps/web/components/staff-pos-panel.tsx`, `apps/web/components/staff-pos-shell.tsx`, `apps/web/lib/staff-pos.ts` |
| Nav (if href/default tab tweak) | `apps/web/lib/staff-auth.ts` (`STAFF_NAV_TREE` POS module) |
| Android | `apps/android-management/feature/pos/**` (`PosScreen.kt`, `PosViewModel.kt`), `…/rpc/RpcNames.kt`, `RpcClient.kt` / `SupabaseRpcClient.kt` |
| Backend | Prefer **no** migration; keep `create_pos_cart` signatures from `20260723230000_sales_pos.sql` / later overlays (`20260724080000_logistics_pick_pack_dn.sql`) |
| Catalog/stock | Existing `search_catalog` RPC + `stock_levels` queries (warehouse-aware for staff) |
| Tests | `supabase/tests/phase5_sales_smoke.sql` still valid; add web/component or smoke notes for “cart exists without button” |

### Acceptance (Phase 1)

- [ ] POS loads into search+catalog+cart with **zero** manual cart-creation clicks
- [ ] `create_pos_cart` still used under the hood (not removed)
- [ ] No HTML5 camera / browser QR in `apps/web`
- [ ] Sidebar-submenu IA preserved

---

## Phase 2 — HR foundation: organogram + role/contracts

**Brief:** §2.1, §2.2 — grade table, `hr_roles` tree, `module_access` → future `STAFF_NAV_TREE` gates; clause templates → contract PDF (PDF render may wait for Phase 4).

**Lanes:** `@backend_agent` → `@web_agent` (HR desk under existing `/staff/hr`) → `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Migration | New `…_hr_organogram_grades_roles.sql` — `hr_grades` (admin-editable), `hr_roles` (self-FK `parent_role_id`, `grade`, `module_access` jsonb, `pay_frequency`, …), clause templates; RLS; high-level-official create/delete gated (reuse `admin` or explicit tag — decide in migration + ADR if durable) |
| Existing HR | Extend `employees` (`20260724070000_hr_gross_payroll.sql`) with `hr_role_id` / grade link; do not orphan on role delete |
| Web | `apps/web/app/(staff)/staff/hr/page.tsx`, new HR components under `apps/web/components/`; **`STAFF_NAV_TREE`** HR children for Organogram / Roles (submenu leaves only) |
| Types | `packages/supabase-client/src/database.types.ts` |
| Tests | New `supabase/tests/hr_organogram_smoke.sql`; payroll smoke still tax-free |

### Acceptance

- [ ] Tree CRUD for roles; delete blocked when employees assigned (or soft-archive)
- [ ] Grades editable without code change
- [ ] Role captures duties/remuneration/clauses/module_access/comms prefs
- [ ] No PAYE/NSSA

---

## Phase 3 — POS Bridge QR + companion Realtime

**Brief:** §1.4, §1.5 (extends Phase 1 layout).

**Lanes:** `@hardware_mobile_agent` (bridges) → `@management_app_agent` → `@web_agent` (pairing QR **display** + Realtime) → `/verifier`

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Bridges | `bridges/android/qr-scanner/**`, `bridges/contracts/qr-inventory.ts` — scan OEM/SKU → add line; **no** web MediaDevices |
| Sessions | `create_pos_scan_session` / claim / revoke — `20260725190000_pos_scan_sessions_checkout_receipt_bind.sql`; upgrade web poll in `staff-pos-panel.tsx` → Supabase Realtime (pattern: delivery map / `delivery_locations`) |
| Web display QR | `node-qrcode` (approved) for pairing code render only |
| Android | POS companion join via Bridge scan |
| Tests | `supabase/tests/pos_scan_companion_otp_smoke.sql` |

### Acceptance

- [ ] Scan-to-cart only via native Bridge
- [ ] Tablet updates without polling lag; paired/unpaired + revoke visible

---

## Phase 4 — Shared branded documents + finance statement PDFs

**Brief:** §2.4 + §3.2 — one `packages/documents/` (or equivalent) for ID/business cards, contracts, payslips, statements; reuse receipt PDF stack where possible; `@react-pdf/renderer` only if physical mm sizes (CR80 85.6×54; business 90×50) need it.

**Lanes:** shared package owner → `@backend_agent` (storage/RPC) → `@web_agent` / `@finance_agent` → `/verifier`

### Likely touchpoints

| Kind | Paths |
|------|--------|
| New package | `packages/documents/` — logo from storefront ADR asset; palette via `colorthief`; QR via `node-qrcode` |
| Receipt baseline | `supabase/functions/_shared/receipt_pdf.ts`, `process-customer-receipts` |
| Storage | Existing `payslips`, `staff-ids` buckets (backend rules); card/statement buckets as needed + RLS |
| Finance UI | Wire PDF export beside CSV in `staff-finance-panel.tsx` / report RPCs (`report_*` from finance_core) |
| Templates | Optional `id_card_templates` table (RLS same migration) after 3–4 mockup review |
| Verify URL | Opaque `…/staff/verify/{token}` — name/photo/role only |

### Acceptance

- [ ] Branded P&L/BS/CF/TB and customer AR statement PDFs (no fiscal QR)
- [ ] Card mockups before multi-theme lock-in
- [ ] Logo too-low-res flagged, not silently swapped

---

## Phase 5 — Split-bill / multi-tender checkout

**Brief:** §1.3 — depends on Phase 0 per-method accounts.

**Lanes:** `@backend_agent` → `@web_agent` + `@management_app_agent` → `/security-reviewer` → `/verifier`

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Migration | Extend `checkout_pos_cart` (`20260725190000_…` latest overlay) to accept `p_tenders jsonb` `[{ method, amount, currency }, …]`; validate sum (FX via existing ZiG rate); post PE/JE **per tender** to Phase 0 accounts |
| Web/Android | `staff-pos.ts` `checkoutPosCart`; POS checkout UI; Android `CHECKOUT_POS_CART` |
| Park/hold | If still missing: `pos_carts` status or park RPC in same epic |
| Tests | New POS split-tender smoke; ledger balance asserts |

### Acceptance

- [ ] Mixed cash+EcoCash+Paynow+ContiPay posts to correct GLs
- [ ] Append-only; currencies explicit

---

## Phase 6 — Onboarding, credentials, password reset

**Brief:** §2.3, §2.5, §2.6.

**Lanes:** `@backend_agent` → `@hardware_mobile_agent` (biometric photo capture — Android first, `bridges/contracts/biometric.ts`) → `@web_agent` → `/supabase-rls-auditor` → `/security-reviewer` → `/verifier`

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Migration | Onboarding draft tables / stages; sensitive banking/health columns with tight RLS; `must_change_password` on profiles; employee number `GTR`+grade+3-digit sequence |
| Bridges | Implement biometric **photo** capture (not auth matching); optional later POD-signature pattern for mobile signing |
| Documents | Phase 4 service for contract / ID / business card on completion |
| Auth | Confirm staff phone-or-email via `signInWithEmailOrPhone`; force change gate on `/staff`; new Edge `request-password-reset` / `verify-password-reset` mirroring `auth-otp` + Resend / SMS / WA outbox |
| Outbox | Reuse existing notification outbox (no new SMS gateway) |
| Web | HR onboarding wizard under `/staff/hr` submenu leaves |

### Acceptance

- [ ] Five-stage resumable onboarding; banking/health hidden from non-HR
- [ ] Completion → employee number, cards, login + forced password change
- [ ] Reset via email and SMS (WA if credentials present)
- [ ] No payroll tax; Bridge-First for camera

---

## Phase 7 — Finance nav, refunds, requisitions, audit, registers, refs

**Brief:** §3.1, §3.5–3.10 (account admin §3.6 folds here).

**Lanes:** `@backend_agent` → `@web_agent` → `@finance_agent` → auditors

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Nav | `STAFF_NAV_TREE` Finance children → **Accounts** (per-GL register) + **Statements**; retire/relabel petty-cash / cash-sales / online-sales leaves as account picks — **submenu only** |
| Requisitions | Extend `finance_requisition_type` beyond `petty_cash`/`payment` (salary, refund, asset/capex, vendor, …); threshold multi-approver using organogram reporting line (Phase 2) |
| Refunds | Reversing JE linked to original sale/ref — never edit posted lines |
| Account admin | Create/archive CoA; hard-delete only zero-history |
| Audit log | Human-readable actor/action/entity before/after for requisition + direct posts |
| Register | `report_account_register` + opening/closing headers + Phase 4 PDF |
| Human refs | Naming series (`next_series_value` from finance_core) for sale/order/refund/ledger-affecting docs; search surfaces in POS/finance/CRM |
| Files | `staff-finance-panel.tsx`, `staff-finance.ts`, migrations on `finance_requisitions*`, `chart_of_accounts` |

### Acceptance

- [ ] Per-method registers + branded PDF
- [ ] Refunds = reversing entries + original ID link
- [ ] Outgoings through requisition + threshold approval
- [ ] Searchable human reference numbers

---

## Phase 8 — My Profile + payroll automation

**Brief:** §2.7, §2.8 — schedule + documents only; **no** statutory tax.

**Lanes:** `@backend_agent` → `@web_agent` → `/verifier`

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Leave | New `hr_leave_types` / `hr_leave_balances` / `hr_leave_requests` (draft→submit→approve) — **not** reuse `finance_requisitions` |
| Profile | Staff self-service under HR or `/staff` submenu; payslip list via existing `export_payslip` / storage |
| Cadence | Mirror analytics subscription cron pattern (`20260725090000_ai_analytics_reports.sql`) driven by `hr_roles.pay_frequency`; call `compute_payroll_run` / notify — still gross − manual deductions (`20260724070000_hr_gross_payroll.sql`) |
| Documents | Phase 4 payslip templates |

### Acceptance

- [ ] Leave request/approve works; payslip history downloads
- [ ] Scheduled runs produce payslips + notify
- [ ] Grep clean: no PAYE/NSSA/tax brackets

---

## Phase 9 — Role gating, WhatsApp audit, mobile parity

**Brief:** §1.6, §1.7 (audit), §1.8.

**Lanes:** `@web_agent` / `@management_app_agent` / `@ios_agent` / `@android_agent`; WhatsApp audit readonly-first

### Likely touchpoints

| Kind | Paths |
|------|--------|
| Gating | Drive visible modules from `hr_roles.module_access` → filter `STAFF_NAV_TREE` + Android management module list; consider `node-casbin` only if tag roles insufficient ([OSS audit](./2026-08-02-open-source-erp-toolkit-audit.md)) |
| WhatsApp audit | `whatsapp-webhook`, `services/whatsapp-flows/` — confirm browse/cart RPCs (`create_customer_cart` / `add_customer_cart_line`), pay (ContiPay/Paynow link + EcoCash direct), fulfillment, receipt outbox; **code only gaps** |
| Fulfillment | Existing `fulfillment_mode` on carts/invoices; add explicit delivery \| pickup UX everywhere; pickup “ready for collection” workflow if missing |
| Mobile parity | Follow [`2026-07-27-mobile-storefront-parity.md`](./2026-07-27-mobile-storefront-parity.md); brand tokens from Phase 4 kit |

### Acceptance

- [ ] Organogram module access gates POS on web **and** Android
- [ ] WA E2E gap list closed or explicitly deferred
- [ ] Checkout surfaces capture fulfillment choice
- [ ] Side-by-side visual parity progress tracked against existing plan

---

## Cross-cutting quality gates

After each non-trivial phase:

1. `/security-reviewer` (diff-scoped)
2. `/verifier` (tests + exclusion grep: ZIMRA, payroll tax, HTML5 QR)
3. Migrations: `/supabase-rls-auditor`
4. Regen types when RPCs/tables change
5. Prefer one coding lane per PR; do not dual-run Ruflo + Cursor manager on a dirty tree

## Out of scope (this batch / standing)

- ZIMRA / FDMS / fiscal QR / tax-authority payloads
- PAYE, NSSA, statutory remittance, tax brackets
- HTML5/WebView camera or GPS on staff web
- Replacing SoR with ERPNext/Odoo
- Proprietary “Buy” SaaS recommendations unless user asks
- Full greenfield WhatsApp commerce (audit + gap-fix only)

## Open decisions (flag during implement; ADR if durable)

1. Exact CoA **codes** for EcoCash / Paynow / ContiPay (vs renaming `1130`)
2. `org_admin` vs reuse `admin` for organogram mutate
3. Global vs per-grade employee number sequence (brief defaults per-grade)
4. Casbin adoption timing vs extending `staff_roles` + `module_access`
5. Park/hold cart: status enum vs side table

## Handoff

Phase 0 unblocks Phase 5 (split-bill) and much of Phase 7. Phase 2 unblocks Phase 9 gating and Phase 7 approval escalation. Phase 4 unblocks polished Phase 6/8 artifacts. Implementers: re-read `AGENTS.md`, `.cursorrules`, `.cursor/rules/`, and this plan before each Part — the brief **adds** requirements; it does not replace standing laws.

### Batch 1 completion notes (2026-08-03)

| Item | Landed |
|------|--------|
| A Realtime | `staff-pos-realtime.ts` + panel; pub `20260803140000_*` |
| B module_access | `filterNavTreeForModuleAccess` + `my_module_access` RPC; Android hub gate |
| C Android split-bill | `CHECKOUT_POS_CART_WITH_TENDERS` + PosScreen tenders |
| D Finance | `20260803160000_*` refunds/thresholds/audit; Accounts/Statements nav |
| E Documents | `packages/documents` stubs + finance export hook |
| F Onboarding/reset | `20260803170000_*`; Edges `request-password-reset` / `verify-password-reset`; `/staff/change-password` |
| G Leave/payslip | `20260803180000_*` leave + `run_scheduled_payroll_for_frequency` (gross only) |
| H Park/fulfillment | `20260803151000_*` park/resume; pickup/delivery labels |
| I Verify | Exclusion greps clean (comment-only mentions). |

### Follow-ups (2026-08-03 — shipped)

| Item | Landed |
|------|--------|
| Multi-approver | `20260803200000_*` — submit sets `required_approvals` from thresholds; approve appends `finance_requisition_approvals`; organogram reporting line may approve; UI shows N/M |
| HR onboarding wizard | `/staff/hr?tab=onboarding` + `save_hr_onboarding_stage` / `complete_hr_onboarding` (`20260803210000_*`); emp# `GTR{grade}{seq}` |
| Documents PDFs | `packages/documents` + Edge `_shared/branded_docs_pdf.ts` + `render-branded-doc`; finance statement export + HR payslip download wired |
| AI ops | `ai_worker_schedules` (`20260803220000_*`); README cron for `process-crm-promos` + report cadences; customer profile + staff CRM credit opt-in UI |
| Verify | Apply with `npx supabase db push --local --yes`; regen types; exclusion grep |

**Remaining gaps:** Auth user create/link on onboarding completion still needs Edge Admin API when `user_id` absent (RPC returns `temp_password_hint`); Android Bridge biometric photo capture deferred to `@hardware_mobile_agent`; Phase C Prophet/Gorse scaffold only if queued separately.

