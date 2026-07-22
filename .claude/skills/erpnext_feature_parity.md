# ERPNext Feature Parity Checklist

> **Trigger:** Load only when evaluating feature completeness, planning new ERP modules, or comparing implementation against ERPNext concepts.

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
- [ ] Biometric/QR staff identity (facial hash, fingerprint, TOTP QR)
- [ ] Attendance capture feeding payroll hours
- [ ] Gross pay from attendance/hours/salary structure
- [ ] Manual/custom deduction line items
- [ ] Payslip export
- [ ] ~~PAYE/NSSA/statutory remittance~~ — **EXCLUDED per Section 0**

### Logistics
- [ ] Live GPS delivery tracking (5-second polling, Supabase Realtime)
- [ ] Tamper-evident inventory transfers (dual-signature approval)

### E-Commerce
- [ ] Visual parts catalog with 4-way search
- [ ] "My Garage" vehicle profiles
- [ ] Multi-currency payments via ContiPay (EcoCash, Visa 3DS, ZimSwitch)
- [ ] Targeted SMS marketing matched to garage vehicles

### Mobile
- [ ] Offline-first sync (PowerSync + Supabase)
- [ ] Native QR scanning (Bridge-First — no HTML5)
- [ ] Bluetooth thermal printer (ESC/POS bridge)

## Verification at Each Milestone

Before marking any module done, re-verify:
- [ ] No ZIMRA references anywhere in codebase
- [ ] No payroll tax logic anywhere in codebase
- [ ] No HTML5/browser-based QR scanning
- [ ] Every new table has RLS policies
- [ ] No agent operated outside its lane without explicit routing
