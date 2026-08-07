# Customer OAuth setup — Google + Apple (Supabase Auth)

Human + Dashboard checklist for [`docs/plans/2026-08-06-customer-oauth-google-apple.md`](./plans/2026-08-06-customer-oauth-google-apple.md).  
**Never commit secret values** — names only. Clients keep `SUPABASE_URL` + anon key.

**Pointing apps at hosted Supabase** (URL/keys, `db push`, Edge redeploy): [`docs/guides/hosted-supabase-cutover.md`](./guides/hosted-supabase-cutover.md).

## What the backend already does

| Piece | Behavior |
|-------|----------|
| `enable_signup = true` | Required so **first** Google/Apple login can create an Auth user |
| `hook_before_user_created` | Blocks public GoTrue **email** `/signup`; allows `google` / `apple` and Edge Admin creates with `app_metadata.gtr_provisioned_via` = `auth_otp` \| `hr_onboarding` |
| `handle_new_user` | Creates `profiles`; for **Google/Apple only** inserts a retail `customers` row. Does **not** mint customers for OTP Admin creates (custom `app_metadata` arrives after AFTER INSERT). |
| `ensure_customer_for_user(p_uid)` | **service_role only** — `auth-otp` `complete_signup` calls this after Admin `createUser` to mint the retail `customers` row |
| `ensure_own_customer()` | Idempotent RPC for clients if `_current_customer_id()` is null (legacy / race). Denies staff (`is_staff`, `staff_roles`, `employees`, `gtr_provisioned_via=hr_onboarding`) |
| Redirect allow-list | Web `/auth/callback` + `gtrcustomer://auth/callback` + `gtr-customer://auth/callback` in `supabase/config.toml` (mirror on hosted) |

Email/password + OTP `complete_signup` remain SoR for email/phone. Staff auth unchanged (no OAuth requirement).

---

## 1. Google Cloud Console

1. Open [Google Auth Platform / Credentials](https://console.cloud.google.com/auth/clients).
2. Create OAuth clients as needed:
   - **Web** — authorized JS origins: site URL(s); **Authorized redirect URIs** must include Supabase callback:
     - Hosted: `https://<PROJECT_REF>.supabase.co/auth/v1/callback`
     - Local: `http://127.0.0.1:54321/auth/v1/callback`
   - **Android** — package `co.zw.nissangtr.customer` + SHA-1 (debug + release).
   - **iOS** — bundle id for GTR Customer (see `apps/ios`).
3. Note **Client IDs** (Web + native). Web client also has a **Client secret** for Supabase Google provider.
4. Scopes: `openid`, `…/userinfo.email`, `…/userinfo.profile`.

**Env / secret names (local `config.toml` / CI — values never in git):**

| Name | Use |
|------|-----|
| `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_ID` | Web (or comma-separated Client IDs per Supabase docs for native) |
| `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET` | Web client secret |

---

## 2. Apple Developer

1. Enable **Sign in with Apple** on the App ID used by the customer iOS app.
2. If web Apple: create a **Services ID**; return URL = Supabase callback  
   `https://<PROJECT_REF>.supabase.co/auth/v1/callback` (and local equivalent if testing).
3. Create a **Key** (Sign in with Apple). Note: **Key ID**, **Team ID**, **Services ID** / client id, and the `.p8` private key.
4. In Supabase Dashboard Apple provider, paste fields as prompted (Dashboard builds the JWT secret from key material). Locally, `SUPABASE_AUTH_EXTERNAL_APPLE_SECRET` is the generated secret JWT (see [Supabase Apple docs](https://supabase.com/docs/guides/auth/social-login/auth-apple)) — **do not commit**.

**Env / secret names:**

| Name | Use |
|------|-----|
| `SUPABASE_AUTH_EXTERNAL_APPLE_CLIENT_ID` | Services ID / client id |
| `SUPABASE_AUTH_EXTERNAL_APPLE_SECRET` | Apple secret JWT (from key) |

---

## 3. Supabase Dashboard (hosted) + local mirror

### Providers

1. **Authentication → Providers → Google** — enable; paste Client ID(s) + Web secret.
2. **Authentication → Providers → Apple** — enable; paste client id + key fields / secret.
3. Local (this repo’s web `.env.local` points at `127.0.0.1:54321` when developing locally):
   - Copy `supabase/.env.example` → `supabase/.env` and fill `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_ID` + `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET` (Apple names if needed).
   - Set `[auth.external.google].enabled = true` (and Apple if used) in `supabase/config.toml`.
   - Restart: `supabase stop` then `supabase start`.
   - Until those steps are done, GoTrue returns HTTP 400 **provider is not enabled** — expected.

### URL config

**Authentication → URL configuration** — add (match `config.toml`):

- **Site URL (hosted — fix confirm-email localhost bugs):** `https://nissangtrauto.co.zw`  
  Do **not** leave Site URL as `http://localhost:3000` / `http://127.0.0.1:3000` on the hosted project — signup confirm links use Site URL when no `redirect_to` is accepted.  
  Android signup sends `gtrcustomer://auth/callback` as `redirectUrl` (must be allow-listed).
- Redirect URLs:
  - `https://nissangtrauto.co.zw/auth/callback`
  - `https://www.nissangtrauto.co.zw/auth/callback`
  - `http://127.0.0.1:3000/auth/callback`
  - `gtrcustomer://auth/callback` (Android + iOS preferred scheme)
  - `gtr-customer://auth/callback` (legacy iOS)

### Signup gate (critical — hard prod gate)

**Local `config.toml` alone is not enough for hosted.** If the Before-user-created hook is missing on the project, public GoTrue `/signup` **fail-opens** while `enable_signup = true`.

1. Hosted **Allow new users to sign up** must be **ON** (same as local `enable_signup = true`) or first OAuth fails with `signup_disabled`.
2. Do **not** rely on disabling that flag to block email signup.
3. **Required:** Wire **Authentication → Hooks → Before user created** to Postgres function  
   `public.hook_before_user_created` (local already: `[auth.hook.before_user_created]` in `config.toml`). Treat an unwired hook as a **ship blocker**.
4. Confirm Edge secrets still include OTP / SMS / email gateway names from [`HARDENING.md`](./HARDENING.md); `auth-otp` and `hr-onboarding-create-auth` set `gtr_provisioned_via`.

#### Post-deploy check (must 403)

After every hosted deploy / Auth config change, probe public email signup. **Expect HTTP 403** (hook reject). A 200/201 means the gate is fail-open — **do not ship**.

```powershell
# Replace URL + anon key from Dashboard (never commit secrets).
$anon = $env:SUPABASE_ANON_KEY
$url = "$env:SUPABASE_URL/auth/v1/signup"
$body = '{"email":"signup-gate-probe@invalid.local","password":"ProbePass1!"}'
curl.exe -s -o NUL -w "%{http_code}" -X POST $url `
  -H "apikey: $anon" -H "Authorization: Bearer $anon" `
  -H "Content-Type: application/json" -d $body
# Expect: 403
```

### Account linking

Prefer Supabase default linking for same email (Google + password). Do not invent a custom linker.

---

## 4. Smoke (backend)

After `db reset` / migration apply:

```powershell
docker exec -i supabase_db_gylrgwqyuiwkyykardwc psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/customer_oauth_ensure_smoke.sql
```

Expect: Google-simulated insert creates `customers`; Admin-shaped OTP insert does **not** until `ensure_customer_for_user`; `ensure_own_customer` idempotent + staff/employee deny; plain email Auth user does not auto-create until RPC.

Also run the **post-deploy `/signup` 403 check** above on hosted (SQL smoke cannot verify the GoTrue hook wiring).

---

## 5. Client lane handoff (not this doc’s implementation)

| Lane | Gap |
|------|-----|
| `@android_agent` | Google ID token → `signInWithIdToken`; call `ensure_own_customer` if needed; show social UI when providers live |
| `@ios_agent` | Sign in with Apple (+ Google if feasible); URL scheme / entitlements for `…://auth/callback` |
| `@web_agent` | OAuth buttons on `app/(auth)/login`; `/auth/callback` route; staff tab unchanged |

Gates: `/security-reviewer` → `/verifier`.
