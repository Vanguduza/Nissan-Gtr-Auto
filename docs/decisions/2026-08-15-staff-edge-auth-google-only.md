# Staff portal edge auth + Google-only customer OAuth

- Date: 2026-08-15
- Lane: `@web_agent` (middleware / cookie session); `@backend_agent` (Auth hooks); `@ios_agent` (Apple UI removal)
- Status: accepted

## Decision

1. **Staff / procurement UI** is gated in Next.js middleware using cookie-backed Supabase sessions (`@supabase/ssr`). Anonymous visitors and non-staff customers cannot load `/staff/*` or `/procurement/*` HTML — they are redirected to login or `/account?notice=staff-only`. Role fine-graining stays in `StaffGate`.
2. **Customer social login is Google only.** Apple Sign In is removed from web + iOS product surfaces; `hook_before_user_created` / `handle_new_user` allow `google` only; Apple provider stays off in Dashboard/`config.toml`.
3. **Password recovery** for customers, staff, and drivers uses Edge `request-password-reset` / `verify-password-reset` with web UI at `/forgot-password`.
4. **Drivers** continue email/password on Android delivery with role `driver`|`admin`; no storefront Google on that app.

## Why

Client-only `StaffGate` left the staff shell reachable by URL. Cookie sessions make edge checks possible. Apple adds App Store / Services ID cost without a product requirement once Google + OTP cover storefront signup.

## Consequences

- Browser client must use `@supabase/ssr` `createBrowserClient` (cookies), not localStorage-only supabase-js.
- Hosted: enable Google provider + Before-user-created hook; keep Apple disabled.
- Do not re-add Apple without an explicit product decision + migration.
- Checklist: [`docs/CUSTOMER_OAUTH_SETUP.md`](../CUSTOMER_OAUTH_SETUP.md).
