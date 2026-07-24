# Phase 9 — HR / attendance / gross payroll

- Status: backend done (API/schema); management UI follow-on
- Lane(s): `@backend_agent` (primary schema/RPCs); `@management_app_agent` (HR screens — follow-on after API)
- Skills needed: (none — do **not** load `/accounting-ledger` unless wage journals are explicitly added later)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 9
- Prior: Phase 2 auth (`profiles`, `staff_roles`, `hr` enum); SMS catalog `payroll_run_ready` / `staff_no_show`

## Goal

Ship staff identity, attendance (manual clock), and **gross-only** payroll runs with manual deduction lines and payslip export — no statutory tax.

## Acceptance criteria

- [x] Employee records link to `profiles` (optional auth); HR/admin CRUD via RLS
- [x] Attendance: clock-in/out (manual); period hours roll up for pay calc
- [x] Gross pay = hours × rate **or** salary structure; currency `USD`|`ZIG` + rate at run time
- [x] Manual deduction lines only; **net = gross − Σ manual**; no PAYE/NSSA/brackets/forms
- [x] Payroll run Draft→Submit→Cancel; submitted immutable; cancel = reverse/void run (no row edit)
- [x] Payslip export (PDF or storage object); employee/owner + HR/admin read; Storage `payslips` private
- [x] Emit `payroll_run_ready` (and optionally `staff_no_show`) via existing outbox — no new SMS gateway
- [x] RLS on every new table in same migration; smoke SQL asserts tax-free formula

## Paths in scope

- `supabase/migrations/20260724070000_hr_gross_payroll.sql` — schema + RPCs + RLS
- `supabase/tests/phase9_hr_payroll_smoke.sql`
- `packages/supabase-client/` — types regen / RPC stubs
- `packages/shared/` — pay/status types only if needed
- **Follow-on:** `apps/android-management/` HR UI (`@management_app_agent`)

## Tables / RPCs (sketch)

| Object | Notes |
|--------|--------|
| `employees` | identity, hire status, optional `user_id` → profiles |
| `salary_structures` / rates | hourly and/or fixed; explicit currency |
| `attendance_events` | clock_in / clock_out; manual source only |
| `payroll_runs` / `payroll_lines` | period, status, gross, currency, exchange_rate |
| `payroll_deduction_lines` | label + amount; **no tax_code / statutory type** |
| RPCs | `clock_attendance`, `compute_payroll_run`, `submit_payroll_run`, `cancel_payroll_run`, `export_payslip` |

## Out of scope

- PAYE, NSSA, POBS/APWCS/ZIMDEF, P4/P4A, tax brackets, statutory remittance
- Biometric / fingerprint clock (Phase 12 bridges)
- Wage → GL journal posting (defer; finance follow-on if needed)
- Leave/benefits/recruiting; ContiPay salary disbursement
- Full management Android polish this slice (API-first)

## Risks / exclusions

- **HARD:** no payroll tax fields, tables, or UI copy that implies statutory calc
- Bridge-First: no device camera/biometric APIs; attendance is app form / manual
- Multi-currency: never assume USD; store rate on run
- RLS: employees may self-read own attendance/payslips; mutate = `hr`|`admin` only
- Do not invent ZIMRA or fiscal payloads

## Ordered tasks

1. [x] Migration: `employees` + salary structure/rates + RLS (`hr`|`admin`; self-read own)
2. [x] Attendance events + `clock_attendance` RPC (manual in/out; period hour rollup helper)
3. [x] Payroll run/lines + manual deduction lines; `compute` / `submit` / `cancel` (gross − manual only)
4. [x] Payslip export + Storage policy; emit `payroll_run_ready`; smoke SQL (formula + RLS denial)
5. [ ] Regen types; brief handoff note for `@management_app_agent` HR screens
6. [ ] `/supabase-rls-auditor` → `/security-reviewer` → `/verifier` → `/manager` done gate

## Handoff

1. Implement tasks 1–4 in `@backend_agent` — **done** (migration + smoke PASS)
2. `/security-reviewer` (PII + payslip Storage)
3. `/verifier` (exclusion grep: PAYE|NSSA|tax bracket)
4. `/manager` done gate; then `@management_app_agent` UI slice

### Backend handoff notes (`@management_app_agent`)

RPCs: `create_employee`, `upsert_salary_structure`, `clock_attendance`, `create_payroll_run`, `compute_payroll_run`, `add_payroll_deduction`, `submit_payroll_run`, `cancel_payroll_run`, `export_payslip`, `emit_staff_no_show`, `attendance_hours_in_period`.
Payslip: `export_payslip` writes metadata + path `{run_id}/{employee_id}.pdf` in private `payslips` bucket; upload PDF bytes from app. Net formula enforced in DB checks + `add_payroll_deduction` rejects statutory labels.