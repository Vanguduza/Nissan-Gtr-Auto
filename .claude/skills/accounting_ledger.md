# Accounting Ledger

> **Trigger:** Load only when the task touches ledger schema, journal entries, Chart of Accounts, financial statements, or the finance module.

## Double-Entry Principles

Every financial event creates balanced journal entries. Debits must equal credits.

## Chart of Accounts Template (Nissan Parts Distributor)

| Code | Name | Type |
|------|------|------|
| 1100 | Cash & Bank | Asset |
| 1200 | Accounts Receivable | Asset |
| 1300 | Inventory (Main Store) | Asset |
| 1310 | Inventory (Quarantine) | Asset |
| 2100 | Accounts Payable | Liability |
| 2200 | Customer Deposits / Store Credit | Liability |
| 3100 | Owner's Equity | Equity |
| 4100 | Parts Sales Revenue | Income |
| 4110 | Sales Returns & Allowances | Income (contra) |
| 4200 | Core Charge Revenue | Income |
| 5100 | Cost of Goods Sold | Expense |
| 5200 | Payroll Expense | Expense |
| 5300 | Operating Expenses | Expense |

## Journal Entry Schema

```sql
journal_entries (
  id UUID PRIMARY KEY,
  entry_date DATE NOT NULL,
  description TEXT,
  currency VARCHAR(3) NOT NULL,  -- 'USD' | 'ZIG'
  exchange_rate_applied DECIMAL,
  posted_by UUID REFERENCES auth.users,
  posted_at TIMESTAMPTZ DEFAULT now(),
  is_reversal BOOLEAN DEFAULT false,
  reverses_entry_id UUID REFERENCES journal_entries
)

journal_entry_lines (
  id UUID PRIMARY KEY,
  journal_entry_id UUID REFERENCES journal_entries,
  account_code VARCHAR(10) NOT NULL,
  debit DECIMAL(18,2) DEFAULT 0,
  credit DECIMAL(18,2) DEFAULT 0,
  currency VARCHAR(3) NOT NULL
)
```

## Transaction Patterns

### Sale
- Debit: Cash/AR (1100/1200)
- Credit: Parts Sales Revenue (4100)
- Debit: COGS (5100)
- Credit: Inventory (1300)

### Return (Quarantine Protocol)
- Debit: Sales Returns & Allowances (4110)
- Credit: AR / Store Credit (1200/2200)
- Debit: Inventory Quarantine (1310)
- Credit: Inventory Main Store (1300)

### Stock Receipt
- Debit: Inventory (1300)
- Credit: Accounts Payable (2100)

### Core Charge
- Separate line items: physical part price + core charge deposit (4200).
- Core return: reverse the deposit when customer returns core.

## Financial Statements

Generate on-demand for any date range:
1. **Trial Balance** — all accounts with debit/credit totals
2. **Income Statement** — revenue - expenses = net income
3. **Balance Sheet** — assets = liabilities + equity
4. **Cash Flow** — operating/investing/financing activities

Multi-currency: consolidate using `exchange_rate_applied` at transaction time.

## Immutability

Entries are append-only. To correct:
1. Create a reversal entry (`is_reversal = true`, `reverses_entry_id` set).
2. Create the correct entry.
Never UPDATE or DELETE journal entries.

## Exclusions

NO tax computation. NO ZIMRA. NO tax-authority integration.
