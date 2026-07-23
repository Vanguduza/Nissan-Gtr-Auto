# Phase 1 — Monorepo + Supabase foundation

- Status: in progress
- Lane(s): @backend_agent (primary), packages scaffolding
- Skills: /accounting-ledger (schema only)

## Goal

Stand up a real workspace and the core Supabase schema so later lanes have tables and shared packages to build against.

## Acceptance criteria

- [ ] Root `package.json` + `pnpm-workspace.yaml` with `apps/*` and `packages/*`
- [ ] Stubs: `packages/shared`, `packages/supabase-client`, `packages/ui`
- [ ] `supabase/config.toml` present
- [ ] Migration(s) for: chart of accounts, journal entries (immutable), warehouses (incl. Quarantine), basic inventory, `vehicle_master`, `pnc_categories`, `part_fitment`
- [ ] RLS enabled + policies on every new table
- [ ] Seed: default CoA accounts + Quarantine warehouse
- [ ] No ZIMRA / payroll-tax fields

## Paths in scope

- `package.json`, `pnpm-workspace.yaml`, `.npmrc`
- `packages/shared/**`, `packages/supabase-client/**`, `packages/ui/**`
- `supabase/**`

## Out of scope

- Next.js / mobile app UI
- ContiPay, GPS, SMS, Meilisearch
- Hardware bridges
- Data pipeline scraping
- n8n workflows

## Handoff

1. Implement (this session)
2. `/security-reviewer` on migrations
3. `/verifier` when local `supabase` CLI available
4. Next phase: web scaffold or finance module UI/API
