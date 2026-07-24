# Phase 14 — Offline sync (PowerSync), hardening, CI

- Status: **must-now Done** (backend/CI/docs); mobile client AC deferred to 11–12
- Lane(s): `@backend_agent` (primary — CI, RLS/smoke gates, PowerSync stubs, hardening docs); mobile lanes **deferred** for client wiring
- Skills needed: (none); `/token-discipline` at implement start
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) Phase 14
- Depends (master table): 11–12 — **override:** CI + hardening + PowerSync **server stubs** ship now; client offline queue/sync waits until management/customer mobile scaffolds exist (stubs OK if APIs incomplete)

## Goal

Ship backend/CI/docs-first hardening: GitHub Actions (lint/typecheck/migrate + smoke gate), Bugbot-on-`main` checklist, security hardening runbook, and PowerSync rules/schema stubs — without blocking on full iOS/Android apps.

## Acceptance criteria

### Must-now (backend / CI / docs)

- [x] `.github/workflows/` exists: PR CI runs `pnpm lint` + `pnpm typecheck` (and package tests where present); fails the job on non-zero exit
- [x] CI (or documented local-equivalent job) applies migrations via Supabase CLI / docker and runs a **smoke gate** (at least `phase2_rls_smoke.sql` + one recent domain smoke, or a thin `phase14_ci_smoke.sql` that asserts RLS enabled on public tables)
- [x] Exclusion grep job or step: no ZIMRA / payroll-tax / HTML5 QR strings introduced on PR paths (align with `.cursor/BUGBOT.md`)
- [x] Docs: Bugbot required check on `main` + security hardening checklist (secrets in Edge/CI env only; no inventing credential values; `WORKER_SHARED_SECRET` / payment env names cited, not values)
- [x] PowerSync **stubs**: checked-in sync rules / schema manifest for POS sale, dispatch/DN read models, cycle-count recon — no live client SDK wiring yet; no secrets in repo
- [x] If any new DB objects: migration(s) **after** `20260724100000` with RLS in the same file(s); types regen note for `packages/supabase-client/` — **N/A** (file-only stubs; no `20260724110000` migration)

### Deferred-to-mobile (follow-on when 11–12 exist)

- [ ] PowerSync client SDK in `@management_app_agent` (POS offline sale queue → sync) and optional customer apps
- [ ] End-to-end “offline sale queues and syncs” acceptance from master Phase 14
- [ ] Performance pass on device (cold sync / conflict UX)
- [ ] Bridge-First device smoke docs remain Phase 12; Phase 14 must not add browser/WebView QR or HTML5 geolocation

## Paths in scope

- `.github/workflows/ci.yml` — **landed**
- `docs/HARDENING.md` + `docs/CURSOR_BEST_PRACTICES.md` Bugbot/hardening pointers; master Phase 14 split Done
- `powersync/` — rules YAML + schema JSON stubs + README (POS / dispatch / recon)
- Optional migration `20260724110000` — **not added** (file-only stubs)
- `supabase/tests/phase14_ci_smoke.sql` — thin “all public tables have RLS” gate
- `package.json` `test:smoke:docs` pointer

## Out of scope

- Full iOS/Android PowerSync SDK integration and offline UI (defer to 11–12 follow-on)
- ZIMRA / FDMS / fiscal QR; payroll tax / NSSA / PAYE
- Browser/HTML5 QR or WebView hardware APIs
- Inventing ContiPay/Paynow/PowerSync cloud secrets or committing `.env` values
- Phase 15 parity audit; Phase 16 distributor extras
- Replacing Supabase; full performance profiling of storefront

## Smoke / CI expectations

| Gate | Expectation |
|------|-------------|
| Lint / typecheck | `pnpm lint` && `pnpm typecheck` green on PR |
| Migrate | Fresh apply through latest migration (`20260724100000`; no Phase 14 DB migration) |
| RLS / smoke | `phase2_rls_smoke.sql` + `phase14_ci_smoke.sql` in Actions `db-smoke` |
| Exclusions | Grep job in CI + Bugbot rules; `/verifier` after implement |
| Bugbot | Dashboard: require `Cursor Bugbot` on `main` (ops checklist in `docs/HARDENING.md`) |

Local parity: `docker exec … psql` — see `docs/HARDENING.md` §4.

## Migration naming (if any)

| Timestamp | Suggested name | When needed |
|-----------|----------------|-------------|
| `20260724110000` | `powersync_publication_stubs.sql` | Only if DB-side publication / sync metadata required |
| `20260724111000` | (reserved) | Guard/RLS follow-up if 110000 lands |

**Chose file-only** PowerSync stubs — no publication migration.

## Risks / exclusions

- Master “depends on 11–12” must not block CI; split Done criteria documented on parent
- PowerSync cloud URL/keys: env placeholders only; never commit
- Sync must respect RLS — stubs define staff-scoped buckets; no open sync of ledger mutations from client without server RPCs
- Ledger immutability: offline clients queue **RPC intents**, not direct JE edits
- Multi-currency: offline payloads carry explicit `USD`\|`ZIG` + rate fields

## Handoff

1. Implement must-now in `@backend_agent` — **done (must-now)**
2. `/security-reviewer` (workflows, secrets handling)
3. `/verifier` (no `/supabase-rls-auditor` — no new migration)
4. `/manager` done-gate for must-now; schedule mobile PowerSync client as 11–12 follow-on child plan
5. Then Phase 15 (or continue 14 deferred AC when mobile exists)
