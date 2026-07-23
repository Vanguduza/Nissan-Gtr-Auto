---
name: erpnext-feature-parity
description: ERPNext feature parity checklist adapted to Supabase for Nissan GTR Auto ERP. Use when evaluating completeness, planning modules, or comparing against ERPNext concepts.
disable-model-invocation: true
---

# ERPNext Feature Parity Checklist

> **Trigger:** Load only when evaluating feature completeness, planning new ERP modules, or comparing implementation against ERPNext concepts. Invoke explicitly with `/erpnext-feature-parity`.

## Adapted to Supabase/Postgres Stack

Replicate these ERPNext concepts while respecting Section 0 exclusions (no ZIMRA, no payroll tax):

### Backend Structure
- [ ] Metadata-driven, API-first backend structure
- [ ] Role-based permissions per module

### Accounting
- [ ] Chart of Accounts + Journal Entry double-entry ledger
- [ ] On-demand financial reports (P&L, Balance Sheet, Cash Flow, Trial Balance)
- [ ] Multi-currency (USD/ZiG) with exchange rate tracking
- [ ] ~~Tax/GST modules~~ — **EXCLUDED per Section 0**

### Inventory
- [ ] Stock Ledger with FIFO/Moving Average valuation
- [ ] Warehouse-to-warehouse stock transfer with dual-authorization approval
- [ ] Serial number tracking for high-value/warranty-relevant assemblies
- [ ] Quarantine warehouse for returns (never direct exchange)

### Sales
- [ ] Sales Invoice / Credit Note / Return Against Invoice linkage
- [ ] Core charge parent-child cart schema
- [ ] Item variants and supersession (`superseded_by` on `part_fitment`)

### Procurement
- [ ] Supplier Portal for purchase order tracking and reconciliation
- [ ] Automated supplier requisitions (AI demand forecasting)

### HR (Simplified — No Tax)
- [ ] Biometric/QR staff identity
- [ ] Attendance → payroll hours (gross + manual deductions only)
- [ ] ~~PAYE/NSSA/statutory remittance~~ — **EXCLUDED**

### Logistics / E-Commerce / Mobile
- [ ] GPS delivery tracking, ContiPay, visual catalog, My Garage, PowerSync offline, Bridge-First QR

## Verification at Each Milestone

- [ ] No ZIMRA references
- [ ] No payroll tax logic
- [ ] No HTML5/browser QR scanning
- [ ] Every new table has RLS
- [ ] Agents stayed in lane (or cross-cutting agent was invoked)
