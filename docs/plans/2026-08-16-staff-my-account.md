# Staff My Account (web MVP)

- **Date:** 2026-08-16
- **Branch:** `cursor/management-oss-shell-rebuild-ad25`
- **Lanes:** `@backend_agent` → `@web_agent` (Android CoolMall Account tab later)
- **Parent:** [2026-08-03-erp-batch1](./2026-08-03-erp-batch1-commerce-pos-hr-finance.md) § Phase 8 Profile; [management OSS shell](./2026-08-14-management-oss-shell-rebuild.md)

## Scope (MVP)

| Surface | Behavior |
|---------|----------|
| `/staff/account` | Any staff (`STAFF_NAV_TREE` link `roles: "any"`) |
| Identity | Emp#, role/grade read-only; editable address/email/phone; **self photo upload** (web file input → `employee-photos`) |
| Payslips | List **own** history (submitted/cancelled payroll lines + payslip metadata); download gross PDF (USD|ZIG); no tax |
| Security | Link to `/staff/change-password`; sign out (chrome also has sign-out) |
| Access | Read-only `module_access` chips from `my_module_access` / profile RPC |
| Cards | ID card + **business card** PDF via `render-branded-doc` when employee row exists |

## Adopt-first

- **Read:** `employees` SELECT already self-or-HR; `payslips` / storage SELECT already self-or-HR; `export_payslip` / `payslip_render_payload` already allow own line.
- **Write gap:** `employees` UPDATE is HR-only → add `update_my_staff_profile` (phone, address, email, photo path only; never grade/role/wages/`staff_roles`).
- **Email:** Client `auth.updateUser({ email })` first; RPC syncs `employees.email` + `profiles.phone_e164` for phone. Do not invent admin secrets.
- **Address / photo:** Columns on `employees`; Storage bucket `employee-photos`; backfill from completed drafts; persist on `complete_hr_onboarding`.

## Hard exclusions

No ZIMRA/fiscal QR, no PAYE/NSSA/tax brackets, no catalog-apk, no editing organogram wages/roles from self-service.

## Acceptance

- [x] Nav leaf + route for any staff
- [x] Self profile update RPC + RLS/grants
- [x] Own payslip history list + download
- [x] Self photo upload + business card PDF
- [x] Smoke SQL + CHANGELOG
- [ ] Hosted migrate when token available
