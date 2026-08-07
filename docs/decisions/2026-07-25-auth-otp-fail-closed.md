# Auth OTP fail-closed (email | phone)

- Date: 2026-07-25
- Lane: `@backend_agent` (Edge/Auth + `profiles.phone_e164`); `@web_agent` (signup/login UI)
- Status: accepted (amended 2026-07-25 — OTP not used for returning login)

## Decision

**OTP is only for:**

1. **Signup** — verify email and/or phone before creating the Auth user and setting a password.
2. **Confirming contacts** — verify an email address or phone number (e.g. add/change on profile).

**Returning logins** use the **email and/or phone saved at registration** plus the **password set at signup**. No OTP on the login path.

Customer signup accepts **email, phone (E.164), or both**. Each identifier supplied at signup is gated by OTP verification before `complete_signup`.

**Fail-closed without gateway secrets:** if SMS and/or email provider keys (or equivalent Supabase Auth provider config) are unset, OTP **request/send refuses** with a clear error — do not silently “succeed” or create verified sessions.

**Local/dev stub only** when **all** of:

- `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` (same pattern as ContiPay/Paynow/worker local stubs), and
- the relevant production secret is **unset**, and
- environment is local/CI (never default-on in production)

Stub may use a documented fixed test OTP returned/logged by Edge only. Clients must not hardcode stub codes as production defaults.

Add **`profiles.phone_e164`** (nullable, unique when set) maintained on verified phone signup. Prefer Supabase Auth phone/email OTP where it fits; custom Edge must still follow the same fail-closed + flag rules. Align naming with [HARDENING](../HARDENING.md) ContiPay/Paynow/worker local stubs.

## Why

Zimbabwe users often prefer phone; email remains required for many B2B accounts. OTP proves ownership of the contact at registration; day-to-day login should stay password-based for a normal retail UX. Fail-closed prevents false “verified” accounts when gateways are misconfigured. Explicit local flag mirrors existing payment/receipt worker stubs.

## Consequences

- Document env names only in HARDENING / `.env.example` — never commit secret values.
- CI/smoke: without flag + without keys → OTP request fails; with flag → stub verify path works for **signup**.
- **Server gate:** `verify` returns a short-lived HMAC `proof_token`; **`complete_signup` only** consumes it before minting the account session. **`complete_login` is password-only** (email and/or phone → GoTrue password grant; phone resolved via `profiles.phone_e164`). Public GoTrue **email** signup is blocked by Auth hook `hook_before_user_created` while `enable_signup = true` (required for first-time Google/Apple). OTP Admin `createUser` sets `app_metadata.gtr_provisioned_via=auth_otp` and Edge calls `ensure_customer_for_user` to mint the retail `customers` row (do not rely on `handle_new_user` for Admin creates). Hosted must wire the Before-user-created hook — local `config.toml` alone is fail-open on Dashboard. See [`CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md).
- No ZIMRA; no payroll-tax identity flows.
