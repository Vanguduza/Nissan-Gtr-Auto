# Phase 2 — Auth, roles, typed client

- Status: **done**
- Lane(s): `@backend_agent`
- Skills needed: none
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 2

## Goal

Wire signup → `profiles`, harden staff-role admin assignment, regenerate typed client, and seed local staff users — without building login UI.

## Already shipped (Phase 1 — do not rebuild)

| Asset | Location |
|-------|----------|
| `staff_role` enum, `profiles`, `staff_roles` | `supabase/migrations/20260723100000_foundation_roles.sql` |
| `is_staff()`, `has_staff_role(roles[])` (SECURITY DEFINER) | same |
| RLS on `profiles` / `staff_roles` (own + admin) | same |
| Anon browser client stub (no service_role) | `packages/supabase-client/src/index.ts` |
| `pnpm db:types` script | root `package.json` |
| CoA / Quarantine seed | `20260723100400_seed_coa_warehouses.sql` |

## Acceptance criteria

- [x] Signup (or auth user insert) creates a `profiles` row automatically
- [x] Non-admin cannot set own `is_staff` or insert `staff_roles`
- [x] Admin path assigns/revokes roles and keeps `profiles.is_staff` consistent
- [x] `has_staff_role` / `is_staff` pass RLS smoke tests (staff vs customer) — `supabase/tests/phase2_rls_smoke.sql`
- [x] `packages/supabase-client/src/database.types.ts` generated and committed
- [x] Types generation documented (`pnpm db:types` / `db:types:linked`); no `service_role` in client packages
- [x] Dev-only seed: local admin, finance, warehouse users with matching roles
- [x] No ZIMRA / payroll-tax / browser QR

## Shipped

| Artifact | Notes |
|----------|-------|
| `20260723200000_auth_profiles_roles.sql` | trigger, sync, RPCs |
| `20260723201000_auth_is_staff_hardening.sql` | closed GUC escalation; column grants; fail-closed RPCs |
| `supabase/seed.sql` | local staff only |
| `packages/supabase-client` | types + `assignStaffRoleArgs` / `revokeStaffRoleArgs` |

## Handoff

Phase 2 complete → open **Phase 3** finance core.
