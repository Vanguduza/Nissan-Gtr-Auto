# Phase 1 — Monorepo + Supabase foundation

- Status: **done** (in-repo; apply DB locally)
- Lane(s): @backend_agent (primary), packages scaffolding
- Skills: /accounting-ledger (schema only)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 1

## Goal

Stand up a real workspace and the core Supabase schema so later lanes have tables and shared packages to build against.

## Acceptance criteria

- [x] Root `package.json` + `pnpm-workspace.yaml` with `packages/*`
- [x] Stubs: `packages/shared`, `packages/supabase-client`, `packages/ui`
- [x] `supabase/config.toml` present
- [x] Migration(s) for: chart of accounts, journal entries (immutable), warehouses (incl. Quarantine), basic inventory, `vehicle_master`, `pnc_categories`, `part_fitment`
- [x] RLS enabled + policies on every new table
- [x] Seed: default CoA accounts + Quarantine warehouse
- [x] No ZIMRA / payroll-tax fields

## Status

Completed in-repo. Apply locally with Docker + Supabase CLI:

```powershell
supabase start
supabase db reset
pnpm db:types
```

Next phase options: scaffold `apps/web` (Next.js) or deepen finance posting APIs.
