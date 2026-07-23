# Phase 2 — Auth, roles, typed client

- Status: draft
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

**Gaps:** no `auth.users` → `profiles` trigger; no `is_staff` sync / anti-escalation; no admin assign RPC; no auth user seed; `database.types.ts` missing; no RLS smoke tests; types process under-documented for commit workflow.

## Acceptance criteria

- [ ] Signup (or auth user insert) creates a `profiles` row automatically
- [ ] Non-admin cannot set own `is_staff` or insert `staff_roles`
- [ ] Admin path assigns/revokes roles and keeps `profiles.is_staff` consistent
- [ ] `has_staff_role` / `is_staff` pass RLS smoke tests (staff vs customer)
- [ ] `packages/supabase-client/src/database.types.ts` generated and committed
- [ ] Types generation documented (`pnpm db:types` / remote gen); no `service_role` in client packages
- [ ] Dev-only seed: local admin, finance, warehouse users with matching roles
- [ ] No ZIMRA / payroll-tax / browser QR

## Paths in scope

- `supabase/migrations/` — new migration only (trigger + hardening + assign RPC)
- `supabase/seed.sql` (and `config.toml` seed enable if needed) — **dev auth users**
- `packages/supabase-client/` — generate types; keep anon-only client
- `docs/` — short types/seed note (e.g. extend `docs/LOCAL_DEVELOPMENT.md` or `docs/SUPABASE_REMOTE.md`)
- Optional: `supabase/tests/` or `packages/supabase-client` smoke SQL/pgTAP for RLS helpers

## Out of scope

- UI login / signup screens (Phases 6 / 11)
- Edge Functions for auth
- OAuth providers, MFA, magic links beyond default email/password
- Changing existing role enum values or re-deriving CoA/inventory schema
- Management/web apps consuming auth UI

## Risks / exclusions

- **Chicken-and-egg admin:** first admin must come from seed / SQL as superuser, not via RLS self-grant
- **`profiles_update_own`:** today allows updating any own column including `is_staff` — restrict so clients cannot escalate
- Seed passwords only for local/dev; never commit production secrets
- Hard exclusions: no ZIMRA, no payroll tax, Bridge-First, RLS on every new table

## Implementation deltas (ordered)

1. Migration: `handle_new_user` trigger on `auth.users` → insert `profiles` (`id`, optional `full_name` from metadata)
2. Migration: column privilege / trigger so only admin (or SECURITY DEFINER RPC) mutates `is_staff`; sync `is_staff` when `staff_roles` change
3. Migration: `assign_staff_role` / `revoke_staff_role` (or single RPC) for admin; grant execute to `authenticated` with `has_staff_role(['admin'])` check inside
4. `supabase/seed.sql`: create three local users + `profiles` + `staff_roles` (admin, finance, warehouse)
5. `supabase start && db reset` → `pnpm db:types` → commit `database.types.ts`
6. Document types + seed in existing docs; add RLS smoke coverage for helpers

## Handoff

1. Implement in `@backend_agent`
2. `/supabase-rls-auditor` (new migration + seed)
3. `/security-reviewer` (auth trigger, privilege escalation, no service_role in clients)
4. `/verifier`
5. `/manager` marks Phase 2 exit criteria and advances to Phase 3
