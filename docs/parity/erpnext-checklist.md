# ERPNext feature parity checklist (Phase 15 working copy)

- Source: `.cursor/skills/erpnext-feature-parity/SKILL.md`
- Gap register: `docs/plans/2026-07-23-master-erp-development.md` §§ Must-have / Phase 16
- Audit date: 2026-07-24
- Status legend: **Done** | **Gap** | **Deferred** | **Excluded**

Citations are migration / smoke / edge paths only — no blueprint dumps.

---

## Backend structure

| Item | Status | Evidence |
|------|--------|----------|
| Metadata-driven, API-first backend | Done | Supabase Postgres + RPCs; typed client `packages/supabase-client/` |
| Role-based permissions per module | Done | `…200000_auth_profiles_roles.sql`, `…201000_auth_is_staff_hardening.sql`; smoke `supabase/tests/phase2_rls_smoke.sql` |
| Document naming series | Done | `naming_series` / `next_series_value` in `…210000_finance_core.sql`; smoke `phase3_finance_smoke.sql` |
| Draft → Submit → Cancel (submitted immutable) | Done | Journal + sales/procurement/logistics/payment/recon mutation guards (`…210000`, `…230000`, `…40000`–`…91000`) |

---

## Accounting

| Item | Status | Evidence |
|------|--------|----------|
| Chart of Accounts + Journal Entry double-entry | Done | `…100100_chart_of_accounts_ledger.sql`, `…210000_finance_core.sql`; smoke `phase3_finance_smoke.sql` |
| On-demand reports (P&L, BS, CF, TB) | Done | `report_*` in `…210000` / `…211000_finance_report_auth.sql` |
| Multi-currency (USD/ZiG) + exchange rate | Done | `currency_code` + rate on journals/payments (`…210000`, `…900000_payment_entries_store_credit.sql`) |
| Opening balances | Done | `post_opening_balances` in `…210000`; smoke `phase3_finance_smoke.sql` |
| Period lock / close-the-books | Done | `accounting_periods`, `lock_accounting_period`, `is_period_locked` in `…210000` |
| Bank reconciliation (dual currency) | Done | `bank_statements` / `bank_recon_matches` in `…210000` |
| Payment Entry + multi-invoice allocation | Done | `…900000_payment_entries_store_credit.sql`, `…910000_payment_mutation_guards.sql`; smoke `phase13_payments_receipts_smoke.sql` |
| Store credit issue/redeem | Done | same Phase 13 migrations + smoke |
| Tax / GST modules | Excluded | Standing Section 0 — never schedule |

---

## Inventory

| Item | Status | Evidence |
|------|--------|----------|
| Stock Ledger + FIFO / Moving Average | Done | `valuation_method` on batches (`…100200_warehouses_inventory.sql`); ops `…220000_inventory_ops.sql`; smoke `phase4_inventory_smoke.sql` |
| Warehouse transfer + dual-auth | Done | `…220000_inventory_ops.sql`; smoke `phase4_inventory_smoke.sql` |
| Serial number tracking | Done | serials in `…220000`; warranty link `…30000_warranty_claims.sql` |
| Quarantine warehouse for returns | Done | Quarantine seed `…100400_seed_coa_warehouses.sql`; return path `…220000` / sales returns |
| UOM conversions | Done | `item_uom_conversions`, `convert_to_base_uom` in `…220000` |
| Stock reconciliation / cycle count | Done | `…20000_stock_reconciliation.sql`, `…40000_stock_recon_mutation_guards.sql`; smoke `phase4b_reconciliation_smoke.sql` |
| Landed cost into batch valuation | Done | `landed_cost_*` in `…50000_procurement.sql`; smoke `phase8_procurement_smoke.sql` |
| Bin / location within warehouse | Deferred | **Phase 16** |
| Kits / BOM sell | Deferred | **Phase 16** |
| Consignment stock | Deferred | **Phase 16** |

---

## Sales

| Item | Status | Evidence |
|------|--------|----------|
| Sales Invoice / Credit Note / Return Against Invoice | Done | `…230000_sales_pos.sql`; smoke `phase5_sales_smoke.sql` |
| Core charge parent-child cart | Done | `is_core_charge` / parent lines in `…230000` |
| Item variants + supersession | Done | Discrete SKUs + `superseded_by` on `part_fitment` (`…100300_vehicle_catalog.sql`, FTS `…010000_catalog_search_fts.sql`) |
| Price lists + customer-specific pricing | Done | price lists in `…230000`; smoke fixes `…51000_smoke_fixes_recon_price_supplier.sql` |
| Credit limit / customer hold | Done | `credit_limit`, `on_hold` in `…230000` |
| Backorders / partial fulfill | Done | `qty_fulfilled` on invoice lines (`…230000`) + pick/DN partials (`…80000_logistics_pick_pack_dn.sql`) |
| Warranty / serial claims | Done | `…30000_warranty_claims.sql`, `…41000_warranty_mutation_guards.sql`; smoke `phase5b_warranty_smoke.sql` |
| Loyalty / points | Done (backend) | `…123000_loyalty_points.sql`; CoA 2210 liability; smoke `phase16_loyalty_smoke.sql`; UI follow-on |

---

## Procurement

| Item | Status | Evidence |
|------|--------|----------|
| Supplier portal (PO tracking / recon) | Done | `current_supplier_id` + supplier self RLS on PO/GRN (`…50000_procurement.sql`); smoke `phase8_procurement_smoke.sql`. Dedicated rich UI is follow-on, not a must-have gap. |
| Material Request → PO | Done | `…50000` + `…63000_procurement_end_rpc.sql`; smoke `phase8_procurement_smoke.sql` |
| RFQ → quotation → PO | Done | `…60000_rfq_blanket.sql`, `…62000_rfq_quotation_mutation_guards.sql`; smoke `phase8b_rfq_blanket_smoke.sql` |
| Blanket / contract POs | Done | same 8b migrations + smoke |
| Automated supplier requisitions (forecast) | Done | `…94000_demand_forecast_suggestions.sql` + `supabase/functions/demand-forecast/` |

---

## HR (simplified — no tax)

| Item | Status | Evidence |
|------|--------|----------|
| Biometric / QR staff identity | Deferred | Attendance is manual clock today (`…70000_hr_gross_payroll.sql` comment: biometric → Phase 12 bridges). Not a master must-have for Phases 3–13. |
| Attendance → payroll hours (gross + manual deductions) | Done | `…70000`, `…71000_hr_attendance_hours_authz.sql`; smoke `phase9_hr_payroll_smoke.sql` |
| PAYE / NSSA / statutory remittance | Excluded | Standing Section 0 |

---

## Logistics / e-commerce / mobile

| Item | Status | Evidence |
|------|--------|----------|
| Pick list / pack | Done | `…80000_logistics_pick_pack_dn.sql`, `…81000_logistics_mutation_guards.sql`; smoke `phase10_logistics_smoke.sql` |
| Delivery Note linked to order/invoice policy | Done | same Phase 10 migrations + smoke |
| GPS delivery tracking | Done | `delivery_locations` + `ingest_delivery_location` in `…80000` (Realtime publication); native GPS bridge → Phase 12 |
| ContiPay (+ Paynow) | Done | `…92000_contipay_payment_intents.sql`, `…95000_paynow_payment_intents.sql`; edge `contipay-*` / `paynow-*` |
| Visual catalog + My Garage | Done | Web storefront/search/garage (`apps/web/`); FTS interim `…010000_catalog_search_fts.sql` + decision `docs/decisions/2026-07-24-search-index-interim-pg-fts.md` |
| PowerSync offline | Deferred | Server stubs `powersync/` (Phase 14 must-now Done); **client SDK / E2E offline sale → Phases 11–12** |
| Bridge-First QR | Deferred | Contracts `bridges/contracts/qr-inventory.ts`; **native implementations → Phase 12** |

---

## Phase 16 — explicitly deferred (not failed AC)

| Gap | Notes |
|-----|-------|
| Bin / location within warehouse | Shelf/bin pick path |
| Kits / BOM sell | Kit SKU and/or explode components |
| Consignment stock | Supplier-owned or customer-held |
| Loyalty / points | Optional after store credit |
| Attachments + doc timeline comments | Soft requirement if cheap |

Child plan: `docs/plans/2026-07-24-phase16-distributor-extras.md` (when opened).

---

## Gap register must-haves (Phases 3–13)

| Gap | Phase | Status |
|-----|------:|--------|
| Opening balances + period lock | 3 | Done |
| Bank reconciliation (USD/ZiG) | 3 | Done |
| Document naming series | 3 | Done |
| Draft → Submit → Cancel | 3–5+ | Done |
| UOM conversions | 4 | Done |
| Stock reconciliation / cycle count | 4b | Done |
| Price lists + customer pricing | 5 | Done |
| Credit limit / customer hold | 5 | Done |
| Backorders / partial fulfill | 5 / 10 | Done |
| Warranty / serial claims | 5b | Done |
| Landed cost into batch | 8 | Done |
| RFQ / supplier quotations | 8b | Done |
| Material Request → PO | 8 | Done |
| Blanket / contract POs | 8b | Done |
| Pick / pack / Delivery Note | 10 | Done |
| Payment Entry → invoice allocation | 13 | Done |
| Store credit (refund path) | 13 | Done |
| Customer receipt SMS + PDF | 5 emit / 13 send | Done — outbox `…230000` / `…93000_receipt_delivery_artifacts.sql`; edge `process-customer-receipts`; smoke `phase13_payments_receipts_smoke.sql` |

**Must-have counts:** Done **18** · Gap **0** · Deferred **0** · Excluded **0**  
(No new `docs/decisions/` deferral required.)

---

## Verification gates (this audit)

| Gate | Status | Evidence |
|------|--------|----------|
| No ZIMRA references (product paths) | Done | `docs/parity/exclusion-evidence.md` |
| No payroll tax logic (product paths) | Done | same |
| No HTML5 / browser QR | Done | same |
| Every new table has RLS | Done | Phase migrations + `phase14_ci_smoke.sql` / `phase2_rls_smoke.sql` |
| Agents stayed in lane | Done | Docs-only Phase 15 under `docs/parity/`, `docs/runbooks/`, plan status lines |
| Gap register must-haves done or deferred by decision | Done | All 18 Done above |

---

## Skill checklist summary counts

Working-copy rows (logistics composite expanded for clarity):

| Status | Count | Notes |
|--------|------:|-------|
| Done | 43 | Incl. verification gates |
| Gap | 0 | — |
| Deferred | 7 | bins, kits, consignment, loyalty; biometric staff (12); PowerSync client (11–12); native Bridge QR (12) |
| Excluded | 2 | Tax/GST; PAYE/NSSA (standing — never Done-as-feature) |

Phase 16 register also defers **attachments + doc timeline** (not a separate skill checkbox row above).
