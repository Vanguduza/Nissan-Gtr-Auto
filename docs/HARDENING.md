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
| `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL` | Local only | `1` only when SMS/email OTP secrets unset; **never** production (`ENVIRONMENT=production` / hosted `*.supabase.co` refuse stub even if set) |
| `AUTH_OTP_FROM_EMAIL` | Edge (optional) | From address fallback for auth OTP email |
| `AUTH_OTP_PROOF_SECRET` | Edge (optional) | HMAC secret for OTP proof tokens; falls back to `SUPABASE_SERVICE_ROLE_KEY` |
| `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_ID` | Auth (local `config.toml` / hosted Dashboard) | Google OAuth Client ID(s); never commit values |
| `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET` | Auth | Google **Web** client secret |
| `SUPABASE_AUTH_EXTERNAL_APPLE_CLIENT_ID` | Auth | Apple Services ID / client id |
| `SUPABASE_AUTH_EXTERNAL_APPLE_SECRET` | Auth | Apple secret JWT from Sign in with Apple key |
| `SMS_GATEWAY_API_KEY` / `SMS_GATEWAY_BASE_URL` / `SMS_GATEWAY_SENDER` | Edge | Real SMS OTP + receipt/manager SMS |
| `EMAIL_API_KEY` / `RESEND_API_KEY` / `EMAIL_FROM` / `RECEIPT_FROM_EMAIL` | Edge | Real email OTP + receipt email |
| `CONTIPAY_API_KEY` | Edge | Initiate Basic Auth token |
| `CONTIPAY_API_SECRET` | Edge | Initiate Basic Auth password (falls back to webhook HMAC secret) |
| `CONTIPAY_MERCHANT_ID` | Edge | Initiate merchant code |
| `CONTIPAY_WEBHOOK_HMAC_SECRET` | Edge | Webhook HMAC-SHA256(raw body) |
| `CONTIPAY_MODE` / `CONTIPAY_API_BASE_URL` | Edge | live vs UAT base URL (optional) |
| `CONTIPAY_ALLOW_UNVERIFIED_LOCAL` | Local only | Stub without secrets |
| `PAYNOW_INTEGRATION_ID` | Edge | Initiate |
| `PAYNOW_INTEGRATION_KEY` | Edge | Initiate + webhook SHA512 field hash |
| `PAYNOW_ALLOW_UNVERIFIED_LOCAL` | Local only | Stub without secrets |
| `POWERSYNC_URL` | Mobile / connector | See `powersync/.env.example` — **infra ready — awaiting keys** for cloud E2E |
| `POWERSYNC_PUBLIC_KEY` | Mobile / connector | Public client key only |
| `BREVO_API_KEY` / `BREVO_FROM_EMAIL` / `BREVO_FROM_NAME` | Edge (CRM promos) | Promo channel; Resend fallback still active until Brevo set (`B-EMAIL-1`) |
| `NEXT_PUBLIC_MAP_STYLE_URL` | Web | MapLibre style JSON; prefer self-host `infra/satellites/maptiles/` (`http://127.0.0.1:8081/styles/basic-preview/style.json`); unset → keyless CARTO |
| `MAPLIBRE_STYLE_URL` | iOS / Android | Native MapLibre; same satellite (emulator `10.0.2.2:8081`); unset → demotiles last resort |
| `TEMPORAL_ADDRESS` / `TEMPORAL_NAMESPACE` / `TEMPORAL_TASK_QUEUE` | Worker host | `@gtr/delivery-dispatch-worker` — fail-closed without address + service role |
| `SUPABASE_SERVICE_ROLE_KEY` | Edge / PowerSync / Temporal worker | **Never** in `apps/*` or client packages |
| `PROMPTFOO_PROVIDER` / `OPENAI_API_KEY` / `GEMINI_API_KEY` | CI optional | Offline gate always; real-provider job **waiting on keys** (`promptfoo/.env.example`) |

**Label:** ContiPay, Paynow, WhatsApp Cloud, SMS, Resend, `WORKER_SHARED_SECRET`, PowerSync, Temporal — **infra ready — awaiting secrets** (or Mac host for H5-iOS `xcodebuild`). Map tiles have a **self-host path** (`infra/satellites/maptiles/`); keyed cloud style URL is optional. Do not invent production values.

Client packages use anon key + user JWT only (`packages/supabase-client`).

Customer Google/Apple OAuth human checklist (redirect URIs, Dashboard toggles, signup gate): [`docs/CUSTOMER_OAUTH_SETUP.md`](./CUSTOMER_OAUTH_SETUP.md).

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

## 7. DIAL-aligned AppSec baseline (2026-08-12)

Adopt habits from DIAL D-47 / D-48 without importing DIAL agency locks:

| Control | Nissan practice |
| --- | --- |
| AuthN ≠ AuthZ | After login, `has_staff_role` / organogram module_access on every staff route (`staff-auth.ts`) |
| No body identity | Never trust `userId` / `role` / `email` from request body — JWT/`auth.uid()` only |
| Worker fail-closed | `WORKER_SHARED_SECRET` required outside local stub (`worker_auth.ts`) |
| Webhooks | ContiPay HMAC / Paynow SHA512 **before** mutate; settle RPCs service_role only |
| Secrets | No `service_role` / PSP keys in `apps/*` or `NEXT_PUBLIC_*` |
| Money | Prefer `amountMinor` path in `@gtr/shared`; AI never writes payable amounts |
| SAST/IaC | **Landed:** `.github/workflows/semgrep.yml` job `semgrep-gtr` (hard-fail on `semgrep/rules/*`); community packs advisory. `.github/workflows/checkov.yml` job `checkov` hard-fail HIGH/CRITICAL. Local: root `semgrep.yml`. Keep Epic A RLS smokes (`epic_a_procurement_wh_smoke.sql`) green. |

Procurement fund releases and preferred-supplier RPCs are SECURITY DEFINER — keep mutation guards and role checks intact; do not open table writes from clients.

## 8. Supabase Database Advisors (2026-08-16)

Hardening pass: migration `20260816040000_advisor_security_performance_harden.sql` (applied hosted).

| Finding | Status |
| --- | --- |
| `security_definer_view` (`v_master_stock`) | **Fixed** — `security_invoker=true`; no authenticated SELECT |
| `function_search_path_mutable` | **Fixed** — `SET search_path = public, pg_temp` |
| `anon_security_definer_function_executable` | **Mostly fixed** — PUBLIC/anon revoked; **7 intentional** anon RPCs remain (storefront rails, Zig rate, delivery track, review stats, staff login lockout helpers) |
| `authenticated_security_definer_function_executable` | **Helpers fixed**; **~300 client RPCs remain** — accepted: PostgREST calls SECURITY DEFINER with authz inside the function |
| `auth_rls_initplan` | **Fixed** — `(select auth.uid())` / `(select auth.role())` pattern |
| `multiple_permissive_policies` | **Accepted** — overlapping staff SELECT + role-scoped write policies are intentional; merging would widen grants |
| `auth_leaked_password_protection` | **Blocked on plan** — Management API returns 402 (Pro+ HIBP). Enable in Dashboard Auth when on Pro |

Do not “fix” remaining authenticated SD EXECUTE by converting RPCs to SECURITY INVOKER without an RLS rewrite — that weakens the current authz model.
