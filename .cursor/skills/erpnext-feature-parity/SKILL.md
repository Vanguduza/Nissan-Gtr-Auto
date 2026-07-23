---
name: erpnext-feature-parity
description: ERPNext feature parity checklist adapted to Supabase for Nissan GTR Auto ERP. Use when evaluating completeness, planning modules, or comparing against ERPNext concepts.
disable-model-invocation: true
---

# ERPNext Feature Parity Checklist

> **Trigger:** Load only when evaluating feature completeness, planning new ERP modules, or comparing implementation against ERPNext concepts. Invoke explicitly with `/erpnext-feature-parity`.
>
> Master plan: `docs/plans/2026-07-23-master-erp-development.md` (gap register + phases).

## Adapted to Supabase/Postgres Stack

Replicate these ERPNext concepts while respecting Section 0 exclusions (no ZIMRA, no payroll tax):

### Backend Structure
- [ ] Metadata-driven, API-first backend structure
- [ ] Role-based permissions per module
- [ ] Document naming series
- [ ] Draft → Submit → Cancel (submitted immutable)

### Accounting
- [ ] Chart of Accounts + Journal Entry double-entry ledger
- [ ] On-demand financial reports (P&L, Balance Sheet, Cash Flow, Trial Balance)
- [ ] Multi-currency (USD/ZiG) with exchange rate tracking
- [ ] Opening balances
- [ ] Period lock / close-the-books
- [ ] Bank reconciliation (dual currency)
- [ ] Payment Entry with multi-invoice allocation
- [ ] Store credit issue/redeem
- [ ] ~~Tax/GST modules~~ — **EXCLUDED per Section 0**

### Inventory
- [ ] Stock Ledger with FIFO/Moving Average valuation
- [ ] Warehouse-to-warehouse stock transfer with dual-authorization approval
- [ ] Serial number tracking for high-value/warranty-relevant assemblies
- [ ] Quarantine warehouse for returns (never direct exchange)
- [ ] UOM conversions
- [ ] Stock reconciliation / cycle count
- [ ] Landed cost into batch valuation
- [ ] Bin / location within warehouse (Phase 16)
- [ ] Kits / BOM sell (Phase 16)
- [ ] Consignment stock (Phase 16)

### Sales
- [ ] Sales Invoice / Credit Note / Return Against Invoice linkage
- [ ] Core charge parent-child cart schema
- [ ] Item variants and supersession (`superseded_by` on `part_fitment`)
- [ ] Price lists + customer-specific pricing
- [ ] Credit limit / customer hold
- [ ] Backorders / partial fulfill
- [ ] Warranty / serial claims
- [ ] Loyalty / points (Phase 16, optional)

### Procurement
- [ ] Supplier Portal for purchase order tracking and reconciliation
- [ ] Material Request → PO
- [ ] RFQ → supplier quotation → PO
- [ ] Blanket / contract POs
- [ ] Automated supplier requisitions (AI demand forecasting)

### HR (Simplified — No Tax)
- [ ] Biometric/QR staff identity
- [ ] Attendance → payroll hours (gross + manual deductions only)
- [ ] ~~PAYE/NSSA/statutory remittance~~ — **EXCLUDED**

### Logistics / E-Commerce / Mobile
- [ ] Pick list / pack
- [ ] Delivery Note linked to order/invoice policy
- [ ] GPS delivery tracking, ContiPay, visual catalog, My Garage, PowerSync offline, Bridge-First QR

## Verification at Each Milestone

- [ ] No ZIMRA references
- [ ] No payroll tax logic
- [ ] No HTML5/browser QR scanning
- [ ] Every new table has RLS
- [ ] Agents stayed in lane (or cross-cutting agent was invoked)
- [ ] Gap register must-haves either done or deferred by written decision
