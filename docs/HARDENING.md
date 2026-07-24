# Security hardening checklist — Nissan GTR Auto ERP

Phase 14 must-now runbook. Ops items that cannot live in git (dashboard toggles, secret values) are listed as checkboxes for humans.

## 1. Bugbot on `main`

- [ ] Cursor Dashboard → Bugbot → GitHub connected for this repo
- [ ] Branch protection on `main`: require status check **`Cursor Bugbot`**
- [ ] Confirm `.cursor/BUGBOT.md` rules are active (RLS, exclusions, ledger immutability, Bridge-First)

See also `docs/CURSOR_BEST_PRACTICES.md` §3 (Bugbot PR Review).

## 2. Secrets (Edge / CI / mobile only)

Never invent or commit credential values. Store only in Supabase Edge secrets, GitHub Actions secrets, or device keystores.

| Env name | Where | Notes |
|----------|--------|--------|
| `WORKER_SHARED_SECRET` | Edge (workers) | Header `x-worker-secret`; required outside local stub |
| `WORKER_ALLOW_UNVERIFIED_LOCAL` | Local only | `1` only when secret unset; never production |
| `CONTIPAY_API_KEY` | Edge | Initiate Basic Auth token |
| `CONTIPAY_API_SECRET` | Edge | Initiate Basic Auth password (falls back to webhook HMAC secret) |
| `CONTIPAY_MERCHANT_ID` | Edge | Initiate merchant code |
| `CONTIPAY_WEBHOOK_HMAC_SECRET` | Edge | Webhook HMAC-SHA256(raw body) |
| `CONTIPAY_MODE` / `CONTIPAY_API_BASE_URL` | Edge | live vs UAT base URL (optional) |
| `CONTIPAY_ALLOW_UNVERIFIED_LOCAL` | Local only | Stub without secrets |
| `PAYNOW_INTEGRATION_ID` | Edge | Initiate |
| `PAYNOW_INTEGRATION_KEY` | Edge | Initiate + webhook SHA512 field hash |
| `PAYNOW_ALLOW_UNVERIFIED_LOCAL` | Local only | Stub without secrets |
| `POWERSYNC_URL` | Mobile / connector | See `powersync/.env.example` |
| `POWERSYNC_PUBLIC_KEY` | Mobile / connector | Public client key only |
| `SUPABASE_SERVICE_ROLE_KEY` | Edge / PowerSync connector | **Never** in `apps/*` or client packages |

Client packages use anon key + user JWT only (`packages/supabase-client`).

## 3. RLS

- Every new `public` table: `ENABLE ROW LEVEL SECURITY` + policies in the **same** migration
- CI gate: `supabase/tests/phase14_ci_smoke.sql` asserts RLS on all public base tables
- Staff vs customer helpers: `is_staff()` / `has_staff_role()` — smoke in `phase2_rls_smoke.sql`
- After migrations: `/supabase-rls-auditor`; types regen `pnpm db:types` when schema changes

## 4. CI vs local smoke

### GitHub Actions (Linux runners — Docker available)

Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)

| Job | What |
|-----|------|
| `quality` | `pnpm lint` → `pnpm typecheck` → `pnpm test` |
| `exclusions` | Hard-exclusion grep (client/bridge/pipeline + edge SDK/URL patterns) |
| `db-smoke` | `supabase start` → `db reset` → `phase2_rls_smoke.sql` + `phase14_ci_smoke.sql` |

### Local (Windows / Docker Desktop)

Prefer apply without full reset when iterating on one migration:

```powershell
$env:PATH = "C:\Program Files\Docker\Docker\resources\bin;" + $env:PATH
# Discover container (project_id from supabase/config.toml):
docker ps --format "{{.Names}}"
# Example:
docker exec -i supabase_db_gylrgwqyuiwkyykardwc psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/phase2_rls_smoke.sql
docker exec -i supabase_db_gylrgwqyuiwkyykardwc psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/phase14_ci_smoke.sql
```

Fresh apply (CI parity): `pnpm db:start` then `pnpm db:reset`, then the same `docker exec … psql` smokes.

If Docker is unavailable: run `quality` + `exclusions` only; document smoke as blocked until Docker is up — do not skip RLS review on schema PRs.

## 5. Ledger & sync

- Journal entries are append-only; corrections via reversing entries
- Offline / PowerSync clients queue **RPC intents** — never upload `journal_entries` mutations (`powersync/`)
- Multi-currency: explicit `USD` \| `ZIG` + `exchange_rate_applied` on money rows and offline payloads

## 6. Exclusions (standing)

- No ZIMRA / FDMS / fiscalisation
- No payroll tax engines (PAYE, NSSA, statutory forms)
- No HTML5 / browser QR or WebView hardware APIs (Bridge-First under `bridges/`)
