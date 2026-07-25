# Auth OTP fail-closed (email | phone)

- Date: 2026-07-25
- Lane: `@backend_agent` (Edge/Auth + `profiles.phone_e164`); `@web_agent` (signup/login UI)
- Status: accepted

## Decision

Customer (and any non-staff) **signup/login** accepts **email, phone (E.164), or both**. Access is gated by **OTP verification** of each identifier supplied.

**Fail-closed without gateway secrets:** if SMS and/or email provider keys (or equivalent Supabase Auth provider config) are unset, OTP **request/send refuses** with a clear error — do not silently “succeed” or create verified sessions.

**Local/dev stub only** when **all** of:

- `AUTH_OTP_ALLOW_UNVERIFIED_LOCAL=1` (same pattern as ContiPay/Paynow/worker local stubs), and
- the relevant production secret is **unset**, and
- environment is local/CI (never default-on in production)

Stub may use a documented fixed test OTP returned/logged by Edge only. Clients must not hardcode stub codes as production defaults.

Add **`profiles.phone_e164`** (nullable, unique when set) maintained on verified phone signup/login. Prefer Supabase Auth phone/email OTP where it fits; custom Edge must still follow the same fail-closed + flag rules. Align naming with [HARDENING](../HARDENING.md) ContiPay/Paynow/worker local stubs.

## Why

Zimbabwe users often prefer phone; email remains required for many B2B accounts. Fail-closed prevents false “verified” accounts when gateways are misconfigured. Explicit local flag mirrors existing payment/receipt worker stubs.

## Consequences

- Document env names only in HARDENING / `.env.example` — never commit secret values.
- CI/smoke: without flag + without keys → OTP request fails; with flag → stub verify path works.
- No ZIMRA; no payroll-tax identity flows.
