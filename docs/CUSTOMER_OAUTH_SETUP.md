# Customer OAuth setup — Google (Supabase Auth)

Human + Dashboard checklist for Google storefront sign-in.  
**Apple Sign In is not used** — do not enable the Apple provider.  
**Never commit secret values** — names only. Clients keep `SUPABASE_URL` + anon key.

**Pointing apps at hosted Supabase** (URL/keys, `db push`, Edge redeploy): [`docs/guides/hosted-supabase-cutover.md`](./guides/hosted-supabase-cutover.md).

## Auth surfaces (quick map)

| Who | How they authenticate |
|-----|------------------------|
| **Customers (web)** | Signup OTP (`auth-otp`) + password; login email/phone + password; Google OAuth; password reset OTP (`/forgot-password`) |
| **Customers (Android / iOS)** | Same SoR: Edge `auth-otp` signup; GoTrue email/password login; Google; Edge password-reset OTP (in-app). No Apple. |
| **Staff portal** | Employee # / email + password; middleware requires `profiles.is_staff` on `/staff` + `/procurement` (cookie session via `@supabase/ssr`) |
| **Delivery drivers** | Android delivery app: email + password; must have staff role `driver` (or `admin` for QA). Same password-reset Edge as web. No Google on driver app. |

## What the backend already does

| Piece | Behavior |
|-------|----------|
| `enable_signup = true` | Required so **first** Google login can create an Auth user |
| `hook_before_user_created` | Blocks public GoTrue **email** `/signup`; allows `google` and Edge Admin creates with `app_metadata.gtr_provisioned_via` = `auth_otp` \| `hr_onboarding` |
| `handle_new_user` | Creates `profiles`; for **Google only** inserts a retail `customers` row. Does **not** mint customers for OTP Admin creates (custom `app_metadata` arrives after AFTER INSERT). |
| `ensure_customer_for_user(p_uid)` | **service_role only** — `auth-otp` `complete_signup` calls this after Admin `createUser` to mint the retail `customers` row |
| `ensure_own_customer()` | Idempotent RPC for clients if `_current_customer_id()` is null (legacy / race). Denies staff (`is_staff`, `staff_roles`, `employees`, `gtr_provisioned_via=hr_onboarding`) |
| Redirect allow-list | Web `/auth/callback` + `gtrcustomer://auth/callback` + `gtr-customer://auth/callback` in `supabase/config.toml` (mirror on hosted) |
| Email confirmations | `enable_confirmations = false` — ownership via Edge OTP / password-reset OTP, not GoTrue confirm links |

Email/password + OTP `complete_signup` remain SoR for email/phone. Staff auth unchanged (no OAuth).

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

## 2. Supabase Dashboard (hosted) + local mirror

### Providers

1. **Authentication → Providers → Google** — enable; paste Client ID(s) + Web secret.
2. **Authentication → Providers → Apple** — leave **disabled** (product decision).
3. Local (this repo’s web `.env.local` points at `127.0.0.1:54321` when developing locally):
   - Copy `supabase/.env.example` → `supabase/.env` and fill `SUPABASE_AUTH_EXTERNAL_GOOGLE_CLIENT_ID` + `SUPABASE_AUTH_EXTERNAL_GOOGLE_SECRET`.
   - Set `[auth.external.google].enabled = true` in `supabase/config.toml`.
   - Restart: `supabase stop` then `supabase start`.
   - Until those steps are done, GoTrue returns HTTP 400 **provider is not enabled** — expected.

### URL config

**Authentication → URL configuration** — add (match `config.toml`):

- **Site URL (hosted — fix confirm-email localhost bugs):** `https://nissangtrauto.co.zw`  
  Do **not** leave Site URL as `http://localhost:3000` / `http://127.0.0.1:3000` on the hosted project.
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

## 3. Staff portal edge gate

Web `middleware.ts` + cookie sessions (`@supabase/ssr`):

1. Unauthenticated `/staff/*` or `/procurement/*` → `/login?next=…`
2. Authenticated but `profiles.is_staff = false` → `/account?notice=staff-only`
3. Role fine-graining remains in `StaffGate` (`canAccessPath`)

RLS still protects data; middleware stops casual link-sharing of the staff UI shell.

---

## 4. Password recovery

| Step | Edge / UI |
|------|-----------|
| Request OTP | `request-password-reset` — email and/or phone |
| Verify + set password | `verify-password-reset` |
| Web UI | `/forgot-password` |

Same Edge pair works for customers, staff, and drivers (account must exist).

---

## 5. Delivery driver auth

- App: `apps/android-delivery` — GoTrue email/password only.
- Gate: `staff_roles` includes `driver` (or `admin`).
- Provision via HR onboarding (staff user + role), not storefront Google/OTP signup.
- Password reset: web `/forgot-password` or call the same Edge functions.

---

## 6. Smoke (backend)

After `db reset` / migration apply:

1. Public email `/signup` → **403**.
2. Google provider disabled → client shows friendly “not enabled” message.
3. With Google enabled + redirect allow-list → first Google login creates `profiles` + `customers`.
4. Hit `/staff` logged out → redirect to login.
5. Customer session on `/staff` → redirect to `/account?notice=staff-only`.
