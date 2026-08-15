# Hosted Supabase cutover

Point clients at the **hosted** project instead of local Docker (`http://127.0.0.1:54321`).

**Project (from `docs/decisions/2026-07-23-remote-supabase-project.md`):**

| Field | Value |
|-------|--------|
| Ref | `gylrgwqyuiwkyykardwc` |
| API URL | `https://gylrgwqyuiwkyykardwc.supabase.co` |

**Never commit** anon, `service_role`, DB password, or OAuth client secrets. Paste them only into gitignored files / Dashboard / `supabase secrets`.

Related: [`docs/CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md), [`docs/LOCAL_DEVELOPMENT.md`](../LOCAL_DEVELOPMENT.md), root [`.env.example`](../../.env.example).

---

## 1. Get secrets from Dashboard

1. Open [Supabase Dashboard](https://supabase.com/dashboard) → project **gylrgwqyuiwkyykardwc**.
2. **Project Settings → API**:
   - **Project URL** → `SUPABASE_URL` / `NEXT_PUBLIC_SUPABASE_URL`
   - **anon** `public` key → client anon vars (browser + mobile)
   - **service_role** → server / Edge / root `.env` only — **never** mobile or `NEXT_PUBLIC_*`
3. **Project Settings → Database** (optional direct Postgres): copy password into `DATABASE_URL` (see root `.env.example`).

Local Docker JWT keys are **not** valid on hosted. Replacing URL alone without swapping keys will fail Auth.

---

## 2. Env vars per app

### Web (`apps/web/.env.local` — gitignored)

| Variable | Source |
|----------|--------|
| `NEXT_PUBLIC_SUPABASE_URL` | Dashboard → API → Project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Dashboard → API → anon |
| `NEXT_PUBLIC_SITE_URL` | Prod `https://nissangtrauto.co.zw` or local `http://127.0.0.1:3000` |

Restart `pnpm dev` after changes.

### Android customer (`apps/android-customer/local.properties` — gitignored)

| Variable | Source |
|----------|--------|
| `SUPABASE_URL` | Same Project URL |
| `SUPABASE_ANON_KEY` | anon key |
| `GOOGLE_WEB_CLIENT_ID` | Google Cloud **Web** OAuth client ID (optional until Google Sign-In) |

Do **not** set `rpc.forceFake=true` for Live. Rebuild debug after edits.

### iOS (`apps/ios/Secrets.xcconfig` — gitignored; copy from `Secrets.xcconfig.example`)

| Variable | Source |
|----------|--------|
| `SUPABASE_URL` | Project URL |
| `SUPABASE_ANON_KEY` | anon key |

Leave `STOREFRONT_FORCE_FAKE` unset/off for Live. See `apps/ios/README.md`.

### Root / tooling (gitignored `.env` at repo root)

| Variable | Source |
|----------|--------|
| `SUPABASE_URL` | Project URL |
| `SUPABASE_ANON_KEY` | anon |
| `SUPABASE_SERVICE_KEY` | service_role (server only) |
| `NEXT_PUBLIC_*` | Same as web if used by scripts |

### Edge Function secrets (Dashboard → Edge Functions → Secrets, or `supabase secrets set`)

Keep OTP / SMS / email / worker names from [`docs/HARDENING.md`](../HARDENING.md). After cutover, **redeploy** `auth-otp` (and any other functions you rely on) so they run against hosted:

```bash
npx supabase link --project-ref gylrgwqyuiwkyykardwc
npx supabase functions deploy auth-otp
```

Do **not** set `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` on hosted — stub OTP is refused for `*.supabase.co`.

#### ContiPay (storefront / customer pay)

Map ContiPay merchant credentials → Edge secrets only — never `NEXT_PUBLIC_*`, mobile `local.properties`, or git. Do **not** set `CONTIPAY_ALLOW_UNVERIFIED_LOCAL` on hosted.

| ContiPay credential | Edge secret |
|---------------------|-------------|
| API token / client id (Basic Auth username) | `CONTIPAY_API_KEY` |
| API secret (Basic Auth password) | `CONTIPAY_API_SECRET` |
| Merchant code | `CONTIPAY_MERCHANT_ID` |
| Webhook HMAC signing secret | `CONTIPAY_WEBHOOK_HMAC_SECRET` |
| Environment (`live` default; `uat`/`dev`/`test` → test API) | `CONTIPAY_MODE` (optional) |
| Custom API host override | `CONTIPAY_API_BASE_URL` (optional) |

If `CONTIPAY_API_SECRET` is unset, initiate falls back to `CONTIPAY_WEBHOOK_HMAC_SECRET` for Basic Auth — still set the webhook secret for settle. Confirm ContiPay’s webhook signature header with the merchant (Edge expects `x-contipay-signature`, with `x-signature` / `signature` fallbacks).

```bash
# Link once if needed, then set secrets (paste values in your shell — do not commit or paste into chat)
npx supabase link --project-ref gylrgwqyuiwkyykardwc
npx supabase secrets set CONTIPAY_API_KEY="<your-api-token>"
npx supabase secrets set CONTIPAY_API_SECRET="<your-api-secret>"
npx supabase secrets set CONTIPAY_MERCHANT_ID="<your-merchant-code>"
npx supabase secrets set CONTIPAY_WEBHOOK_HMAC_SECRET="<your-webhook-hmac-secret>"
npx supabase secrets set CONTIPAY_MODE="live"

# Redeploy so initiate + webhook pick up secrets
npx supabase functions deploy contipay-initiate
npx supabase functions deploy contipay-webhook
```

Or Dashboard → **Edge Functions → Secrets**, same names. Webhook URL ContiPay must call (Edge registers this automatically; do not send `result_url` from the client):

`https://gylrgwqyuiwkyykardwc.supabase.co/functions/v1/contipay-webhook`

Browser return/cancel: `/checkout/return?invoice=…` and `/checkout/cancel?invoice=…` — see [`docs/storefront-psp-return-urls.md`](../storefront-psp-return-urls.md). Initiate needs a customer **phone** (EcoCash cell) on the profile / request body.

#### Paynow (optional second rail)

Merchant **Integration ID** → `PAYNOW_INTEGRATION_ID`; merchant **Integration Key** → `PAYNOW_INTEGRATION_KEY`. Edge only — never `NEXT_PUBLIC_*`, mobile `local.properties`, or git. Do **not** set `PAYNOW_ALLOW_UNVERIFIED_LOCAL` on hosted.

```bash
# Link once if needed, then set secrets (paste values in your shell — do not commit)
npx supabase link --project-ref gylrgwqyuiwkyykardwc
npx supabase secrets set PAYNOW_INTEGRATION_ID="<your-integration-id>"
npx supabase secrets set PAYNOW_INTEGRATION_KEY="<your-integration-key>"

# Redeploy so initiate + webhook pick up secrets
npx supabase functions deploy paynow-initiate
npx supabase functions deploy paynow-webhook
```

Or Dashboard → **Edge Functions → Secrets**, same two names. Webhook / Paynow `resulturl` is always:

`https://gylrgwqyuiwkyykardwc.supabase.co/functions/v1/paynow-webhook`

(Browser return/cancel stay on the storefront — see [`docs/storefront-psp-return-urls.md`](../storefront-psp-return-urls.md).)

---

## 3. Migrations (push OAuth + pending)

Pending customer-OAuth-related migrations in-repo (apply if not yet on hosted):

- `20260806130000_customer_oauth_ensure_customer.sql`
- `20260806140000_customer_oauth_otp_mint_harden.sql`
- `20260806150000_revoke_provision_denied_authenticated.sql`

(Also nearby: `20260806120000_catalog_diagrams_allow_gif.sql`.)

**Preferred (CLI):**

```bash
# Login once if needed
npx supabase login

# Link this repo to hosted (writes supabase/.temp — gitignored)
npx supabase link --project-ref gylrgwqyuiwkyykardwc

# See local vs remote
npx supabase migration list

# Apply pending migrations only (non-destructive; do NOT db reset remote)
npx supabase db push
```

**Alternative:** Dashboard → SQL Editor — paste each migration file in order, or use the Migrations UI if you already manage remote that way.

**Do not** run `supabase db reset` against hosted. That destroys remote data.

After schema is current: `pnpm db:types:linked` if you need regenerated types from remote.

---

## 4. Auth on hosted (config.toml does not apply)

`supabase/config.toml` only configures **local** GoTrue. On hosted you must configure Dashboard:

### Providers

1. **Authentication → Providers → Google** — enable; Web Client ID + secret (+ native client IDs as needed).
2. **Authentication → Providers → Apple** — leave **disabled** (product: Google-only).

Details: [`docs/CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md).

Google Cloud **Authorized redirect URI** for hosted:

`https://gylrgwqyuiwkyykardwc.supabase.co/auth/v1/callback`

### URL configuration

**Authentication → URL configuration:**

- **Site URL:** `https://nissangtrauto.co.zw` (prod) — **not** localhost. Confirm-email links open Site URL / `redirect_to`; localhost here breaks Android signup confirms.
- Redirect allow-list (mirror `config.toml`):
  - `https://nissangtrauto.co.zw/auth/callback`
  - `https://www.nissangtrauto.co.zw/auth/callback`
  - `http://127.0.0.1:3000/auth/callback` (dev web against hosted)
  - `gtrcustomer://auth/callback`
  - `gtr-customer://auth/callback`

### Signup gate (ship blocker)

1. **Allow new users to sign up** = **ON** (first Google/Apple needs this).
2. Wire **Authentication → Hooks → Before user created** → Postgres `public.hook_before_user_created`.
3. Probe public email `/signup` — expect **HTTP 403** (see OAuth setup doc). A 200 means fail-open.

---

## 5. Verify checklist

After env switch + migrations + Auth config:

- [ ] Web: email/password sign-in against hosted (seeded local users will **not** exist unless you created them on hosted).
- [ ] Web: Google OAuth once provider enabled; lands on `/auth/callback`.
- [ ] After Google/Apple: retail `customers` row exists (`handle_new_user` / `ensure_own_customer`).
- [ ] Android Live: URL + anon in `local.properties`; sign-in works; optional Google ID token when `GOOGLE_WEB_CLIENT_ID` set.
- [ ] iOS Live: `Secrets.xcconfig` filled; scheme callbacks work.
- [ ] Public `/signup` returns 403 on hosted.
- [ ] `auth-otp` redeployed if you use phone/email OTP on hosted.
- [ ] ContiPay: `CONTIPAY_API_KEY` + `CONTIPAY_API_SECRET` + `CONTIPAY_MERCHANT_ID` + `CONTIPAY_WEBHOOK_HMAC_SECRET` set on Edge; `contipay-initiate` / `contipay-webhook` redeployed; **no** `CONTIPAY_ALLOW_UNVERIFIED_LOCAL` on hosted.
- [ ] Web cart/order: **Pay with ContiPay** (profile phone set) → hosted `checkout_url` (`stub: false`); after pay, land on `/checkout/return?invoice=…`; invoice settles only after `contipay-webhook` (not on return page alone).
- [ ] Paynow (if used): `PAYNOW_INTEGRATION_ID` + `PAYNOW_INTEGRATION_KEY` set on Edge; `paynow-initiate` / `paynow-webhook` redeployed; **no** `PAYNOW_ALLOW_UNVERIFIED_LOCAL` on hosted.
- [ ] No `service_role` in mobile or `NEXT_PUBLIC_*`.

---

## 6. Switching back to local

Restore gitignored env to:

```text
http://127.0.0.1:54321
```

plus the **local** anon/service keys from `supabase status` (after `pnpm db:start`). Local OAuth still uses `supabase/.env` + `config.toml` `[auth.external.*]` — see OAuth setup doc.
