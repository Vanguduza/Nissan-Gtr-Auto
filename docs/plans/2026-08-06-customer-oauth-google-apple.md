# Customer OAuth — Google + Apple

- Status: backend done (config + ensure customer + docs); client lanes pending
- Lane(s): `@backend_agent` → `@android_agent` → `@ios_agent` → optional `@web_agent`
- Skills needed: none (auth SoR = Supabase Auth)
- Related: [`2026-07-24-customer-storefront-authz`](./2026-07-24-customer-storefront-authz.md), [`2026-07-23-phase2-auth-roles`](./2026-07-23-phase2-auth-roles.md), [`auth-otp-fail-closed`](../decisions/2026-07-25-auth-otp-fail-closed.md), setup: [`../CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md)

## Goal

Customers sign in with **Google** (Android + iOS + web) and **Apple ID** (iOS required; web if storefront login exists) via **Supabase Auth only** — keep email/password; no parallel auth stack.

## Discoveries (adopt-first)

| Surface | Today | Gap |
|---------|--------|-----|
| Android customer | `SupabaseRpcClient.signInWithEmail` (supabase-kt); `SignInScreen` notes social UI “hidden until providers configured” | No `signInWithIdToken` / Credential Manager |
| iOS | Minimal `GoTrueAuthClient` password grant only (no supabase-swift) | No Apple/Google ID-token path |
| Web | `app/(auth)/login` + `lib/auth-otp.ts` — password login; OTP signup | No OAuth UI; **customer auth UI exists** → web in scope |
| Supabase | `enable_signup = false`; `handle_new_user` → `profiles`; redirects = web only | No `[auth.external.*]`; OAuth first-login + **`customers` row** not provisioned (AuthZ needs `profile_id`) |

## Providers & flows

| Provider | Android | iOS | Web |
|----------|---------|-----|-----|
| Google | Native ID token → GoTrue `signInWithIdToken` (preferred) | Same if feasible; else OAuth redirect + deep link | `signInWithOAuth({ provider: 'google' })` + callback |
| Apple | N/A (optional later) | **Sign in with Apple** → ID token → GoTrue | Same OAuth if enabling web Apple |
| Email/password | Keep | Keep | Keep |

Deep links already reserved: Android/iOS `gtrcustomer` / `gtr-customer` (checkout). Reuse or add auth callback path (e.g. `…://auth/callback`) — document exact URI in backend env docs.

## Human Dashboard steps (not code)

Do these before / alongside `@backend_agent`; secrets stay in Dashboards / CI secrets — **names only in repo**.

### Google Cloud Console
1. Create/select OAuth client(s): **Web**, **Android** (package + SHA-1), **iOS** (bundle id) as needed.
2. Note Client IDs (and Web client secret for Supabase Google provider).
3. Authorized redirect URIs: Supabase callback  
   `https://<PROJECT_REF>.supabase.co/auth/v1/callback`  
   (+ local if using hosted Auth against local apps).

### Apple Developer
1. Enable **Sign in with Apple** on App ID (`co.zw.nissangtr.customer` or current bundle).
2. Create Services ID for web (if web Apple); configure return URL → Supabase callback.
3. Create Key (Sign in with Apple); note Key ID, Team ID, Services ID, private key → Supabase Apple provider fields.

### Supabase Dashboard (hosted) + local `config.toml` mirror
1. Auth → Providers: enable **Google**, **Apple**; paste Client IDs / secrets / Apple key material (Dashboard only).
2. Auth → URL config: add mobile redirect URIs (`gtrcustomer://…`, `gtr-customer://…`) and existing web URLs.
3. **Signup gate (resolved):** global `enable_signup = true` so first OAuth can create users. Public email `/signup` blocked by Postgres Auth hook `hook_before_user_created`. OTP `complete_signup` / HR onboarding set `app_metadata.gtr_provisioned_via`. Full checklist: [`docs/CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md).
4. Env / secrets names only (examples): `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_ID`, `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET`, `SUPABASE_AUTH_EXTERNAL_APPLE_CLIENT_ID`, `SUPABASE_AUTH_EXTERNAL_APPLE_SECRET` — never commit values. Clients keep existing `SUPABASE_URL` / anon key only.

## Acceptance criteria

### `@backend_agent`
- [x] Google + Apple enabled in Auth config docs + `config.toml` external stubs (no secrets in git)
- [x] Redirect allow-list includes web + mobile auth callbacks
- [x] First OAuth login creates/links `profiles` (existing trigger) **and** ensures `customers` row for `auth.uid()` (migration only if needed; RLS intact)
- [x] Email/password + OTP signup paths unchanged; no staff OAuth requirement
- [x] Smoke: OAuth-minted JWT passes `_current_customer_id()` / one storefront RPC
  - Script: `supabase/tests/customer_oauth_ensure_smoke.sql` (trigger + `ensure_own_customer`; full live Google/Apple needs Dashboard secrets)

### `@android_agent`
- [ ] Google → Supabase session; email path unchanged
- [ ] Social buttons only when live; FakeRpc path still works
- [ ] No secrets in source; `packages/android-ui` untouched

### `@ios_agent`
- [ ] Sign in with Apple → Supabase session (required)
- [ ] Google if feasible without large SDK churn
- [ ] Email path unchanged; entitlements / URL scheme for callback if used

### `@web_agent` (customer login exists → do this)
- [ ] Google (+ Apple optional) on `app/(auth)/login`; staff tab unchanged
- [ ] Callback route completes session; no product secrets in repo

### Gates
- [ ] `/security-reviewer` (providers, redirects, signup gate, no secret leak)
- [ ] `/verifier` (exclusions, lane, smoke)

## Paths in scope

- `supabase/config.toml`, optional migration for customer ensure-on-auth, `docs/HARDENING.md` / `.env.example` names only
- `apps/android-customer/feature/auth/`, `…/rpc/SupabaseRpcClient.kt`
- `apps/ios/…/GoTrueAuthClient.swift`, `SignInScreen.swift`, entitlements/Info.plist
- `apps/web/app/(auth)/`, `apps/web/lib/auth-otp.ts` (or thin OAuth helper)

## Out of scope

- Staff / management / delivery app OAuth (shared Dashboard config OK)
- Facebook or other providers; passwordless OTP-on-login change
- ZIMRA / payroll tax; committing secrets; freezing breach of `packages/android-ui`
- Commits unless user asks later

## Risks / exclusions

- **`enable_signup`:** must be **true** for first-time OAuth; public email signup blocked by `hook_before_user_created` (not by disabling global signup)
- Missing **`customers`** row → AuthZ empty even with valid JWT — mitigated by `handle_new_user` (OAuth) + `ensure_customer_for_user` (OTP Edge) + `ensure_own_customer()` (client backfill)
- Apple nonce / audience must match Supabase docs for `signInWithIdToken`
- Account linking (same email Google + password) — prefer Supabase default; document behavior, don’t invent custom linker

## Handoff

1. **Human:** Google Cloud + Apple Developer + Supabase provider toggles (above)
2. **`@backend_agent`** — config, signup/customer ensure, redirect list, smoke
3. **`@android_agent`** — Google ID token
4. **`@ios_agent`** — Apple (+ Google if feasible)
5. **`@web_agent`** — login OAuth buttons + callback
6. `/security-reviewer` → `/verifier` → `/manager` done gate
