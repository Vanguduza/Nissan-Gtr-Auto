# Storefront Android ↔ Web feature parity (audit gaps)

- Status: done (2026-08-15) — Android phone auth, loyalty ledger, EcoCash saved/other, reviews hub, contact; web address MapLibre pin-pick. App `:app:compileDebugKotlin` OK.
- Date: 2026-08-15
- Lane(s): `@android_agent` (gaps 1–5) → `@web_agent` (gap 6; optional thin adopt)
- Skills needed: none (Bridge-First maps already in-repo; no `/ui-ux-pro-max`)
- Extends: audit gaps only — not `2026-08-13-mobile-storefront-crm-parity.md` rails/CRM

## Goal

Close six known storefront parity gaps between `apps/android-customer` and `apps/web` without inventing new backend SoR.

## Exclusions

- No ZIMRA / FDMS / payroll tax
- Bridge-First: no HTML5/WebView QR; web map = MapLibre JS (existing pattern), not browser GPS as SoR
- No iOS parity in this plan
- No ContiPay/Paynow redesign, no loyalty earn/redeem UX beyond ledger list

## RPC / Edge reuse (adopt-first)

| Capability | Reuse | Do not invent |
|---|---|---|
| Phone OTP signup + phone/password login | Edge `auth-otp`; web `apps/web/lib/auth-otp.ts` (`signInWithEmailOrPhone`) | New auth tables or client-side phone→email mapping |
| Loyalty balance | RPC `get_loyalty_balance` (Android already) | New balance RPC |
| Loyalty ledger | Direct select `loyalty_ledger` (web `listLoyaltyLedger`); RLS own-row | New ledger RPC unless select blocked |
| EcoCash payer modes | `create_customer_ecocash_intent` / `ecocash-initiate` with `saved`\|`other`\|`profile` | New payment RPCs |
| Own reviews hub | Existing `listOwnReviews` (PDP-only UI today) | New review schema |
| Contact / WhatsApp | Static + deep link; chat WhatsApp helper / web `WhatsAppCta` | New contact tickets table |
| Address geo | Android MapLibre + upsert; web form-only | HTML5 geolocation SoR |

## Acceptance criteria (per gap)

1. **Phone auth (Android)** — Phone E.164 + password via Edge; phone OTP signup + password complete; email path unchanged; Fake/stub codes work.
2. **Loyalty ledger (Android)** — Recent ledger under balance; honest empty/error; Fake ≥1 earn + 1 redeem.
3. **EcoCash payer modes (Android)** — saved/profile/other; saved|profile use profile phone; correct `payerMode` (not hard-coded `"other"`).
4. **Account reviews hub (Android)** — Account entry lists own reviews; PDP reviews remain; reuses `listOwnReviews`.
5. **Contact / WhatsApp (Android)** — Lightweight Contact (counter, chat link, WhatsApp); hamburger not stub-only.
6. **Address map pin-pick (Web)** — MapLibre click-to-pin on addresses **or** defer with adopt path from delivery maps.

## Ordered implementation slices

1. `@android_agent` — Phone auth
2. `@android_agent` — Loyalty ledger
3. `@android_agent` — EcoCash payer modes
4. `@android_agent` — Reviews hub
5. `@android_agent` — Contact / WhatsApp
6. `@web_agent` — Address pin-pick or defer note

Handoff: code → `/security-reviewer` (auth/pay) → `/verifier` → `/manager` done gate.
