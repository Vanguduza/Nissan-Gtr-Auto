# Payroll fund + PDF payslips (schedule & on-demand)

**Date:** 2026-08-16  
**Lane:** `@backend_agent` + `@web_agent` (HR desk)  
**Status:** Implemented  
**Prior:** Phase 9 gross payroll (`2026-07-24-phase9-hr-gross-payroll.md`); schedule stub `20260803180000_batch1_hr_leave_payslip_schedule.sql`

## Goal

HR/admin can **fund** submitted gross payroll from the cash/bank GL, post **append-only** journals, and generate **Nissan GTR branded PDF payslips** — either **on demand** (selected employees / run) or on a **configured schedule**.

## Hard exclusions

- No PAYE / NSSA / statutory tax / remittance forms  
- No ZIMRA / fiscal QR  
- No ContiPay/bank API auto-disbursement (GL cash credit only)  
- Ledger immutable; currency `USD`|`ZIG` + rate at post time  

## Accounting design

CoA:

| Code | Name | Role |
|------|------|------|
| **5200** | Payroll Expense | Already seeded |
| **2150** | Salaries Payable | **New** liability |
| **1100** | Operating Bank | Default cash/bank (configurable; 1110 petty allowed) |

**Two-step (preferred)** — amounts = **net** (gross − manual deductions only):

1. **Accrue:** Dr **5200** / Cr **2150**  
2. **Fund (pay):** Dr **2150** / Cr **1100** (or chosen cash account)

Posted via `_post_journal_entry_payroll` (DEFINER, same pattern as inventory helper) so HR can post without a `finance` staff_role. One accrual JE + one payment JE per funding batch (selected lines sharing currency/rate).

Idempotent: lines with `payment_journal_id` set are skipped.

## Who can configure / run

| Action | AuthZ |
|--------|--------|
| Configure `hr_payslip_schedules` | `admin` \| `hr` (+ `module_access` includes `hr` for nav) |
| On-demand fund + export | `admin` \| `hr` |
| Cron Edge worker | `service_role` + `x-worker-secret` |

## Schedule vs on-demand

| Mode | Flow |
|------|------|
| **On-demand** | Submitted `payroll_lines` → multi-select → `fund_payroll_lines` → journals + `export_payslip` → client or worker PDF → Storage `payslips/` |
| **Schedule** | Admin/HR sets `cron_expr` / `next_run_at`, cash account, currency · Edge `process-payroll-schedules` → `run_due_payroll_schedules` → create/compute/submit/fund/export → PDF upload |

Adopt-first: same worker pattern as `process-ai-reports` / `ai_worker_schedules` (registry + external/pg_cron), not Temporal.

## RPCs / objects

- CoA seed `2150`  
- Columns on `payroll_lines`: `accrual_journal_id`, `payment_journal_id`, `funded_at`, `funded_by`  
- Schedule columns: `cash_account_code`, `currency`, `default_exchange_rate`, `auto_fund`, `next_run_at`  
- `fund_payroll_lines`, `fund_payroll_run`, `upsert_hr_payslip_schedule`, `run_due_payroll_schedules`  
- Edge: `process-payroll-schedules`  
- Web: `/staff/hr?tab=payroll`  

## Out of scope

- ContiPay salary rails  
- Deduction → separate GL mapping  
- Android management payroll fund UI (web first; Phase 9 Android desk already exists for deductions)

## Acceptance

- [x] Plan in `docs/plans/`  
- [x] Migration + RLS + smoke (no tax columns; JE balanced; fund idempotent)  
- [x] Schedule Edge + HR desk UI  
- [x] CHANGELOG / ENHANCEMENTS  
- [x] Commit + push  
