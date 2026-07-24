# Phase 11–12 — Mobile stub / scaffold

- Status: scaffolded (2026-07-24) — project files landed; native `assembleDebug` / Xcode Simulator not verified on Windows scaffold host (no JDK/Android SDK/Xcode)
- Lane(s): `@ios_agent`, `@android_agent` (Phase 11); `@management_app_agent` (Phase 12 app shell); `@hardware_mobile_agent` (**bridge contracts only**)
- Skills needed: `/token-discipline`; `/qr-inventory-workflow` only if extending bridge contracts (not implementations)
- Parent: [`2026-07-23-master-erp-development.md`](./2026-07-23-master-erp-development.md) §§ Phase 11–12, Near-term sequencing (11–12 stub/scaffold)
- Related: Phase 14 PowerSync client deferred until these shells exist ([`…phase14-offline-ci-hardening.md`](./2026-07-24-phase14-offline-ci-hardening.md))

## Goal

Scaffold empty customer (iOS + Android) and management Android app shells with env stubs and shared typed client wiring — **not** full feature parity — while documenting which backend contracts are ready vs blocked.

## API readiness (assessed 2026-07-24)

| Surface | Exists today | Customer-thin OK? | Notes |
|---------|--------------|-------------------|-------|
| Catalog search | `search_catalog` RPC + web `catalog-search.ts` | **Yes** | PG FTS interim; enough for a later bind slice |
| Auth | Supabase Auth + `customers.profile_id` SELECT-own | **Partial** | Login/session OK; no rich customer profile/garage RPCs beyond web stubs |
| Cart / orders | `create_pos_cart` / `checkout_pos_cart` + invoices | **No** | Staff-only RLS/policies; no customer storefront cart or invoice SELECT |
| Payment intents | ContiPay/Paynow Edge + `create_*_intent` | **No** | Staff gate (`_require_payments_staff`); not customer self-checkout |
| Delivery job reads | `delivery_jobs` + status RPCs | **No** (customer) / **Yes** (staff) | Staff SELECT/write only; no customer tracking read model |
| HR | Gross payroll RPCs | N/A customer; defer mgmt UI | Not required for Phase 11 |

**Verdict:** Contracts are **insufficient for thin feature clients**. Deliver **scaffold-only** (empty shells + README). Feature screens wait for a backend “customer storefront API” slice + later 11/12 feature plans.

### Explicit blockers (before feature bind)

1. Customer-facing cart/checkout RPCs (or RLS + grants) distinct from staff POS, or documented reuse with AuthZ.
2. Customer SELECT on own invoices / order status (+ optional DN/job summary) without staff role.
3. Customer payment-intent path (or “pay at counter / web-only” product decision in `docs/decisions/`).
4. Optional: My Garage / wishlist live tables if product requires parity with web `/account` stubs.

Management app can later bind to **existing staff** POS/logistics/recon RPCs; still out of scope for this scaffold slice.

## Acceptance criteria

- [x] `apps/ios/` Xcode + SPM customer shell scaffolded; README lists run steps + env vars *(Simulator build requires macOS/Xcode — stub-only on Windows host)*
- [x] `apps/android-customer/` Gradle shell + README + `.env.example` (URL/anon key placeholders only) *(`assembleDebug` needs JDK 17 + Android SDK + `gradle-wrapper.jar` — not available on scaffold host)*
- [x] `apps/android-management/` Gradle shell with POS / warehouse / dispatch **placeholder** modules (no screens wired) *(same toolchain caveat as customer)*
- [x] Shared consumption path documented: `@gtr/supabase-client` types + `@gtr/shared` money/QR helpers — **no duplicated pricing logic** in apps (`apps/SHARED_CLIENT.md` + per-app READMEs)
- [x] Env stubs only (`SUPABASE_URL`, `SUPABASE_ANON_KEY`); no secrets committed
- [x] No HTML5 / browser QR libraries; customer apps do not call Camera/WebView QR APIs
- [x] `@hardware_mobile_agent`: extend `bridges/contracts/` (+ README path map) for QR / ESC/POS / biometric / GPS **interfaces only** — no CameraX/AVFoundation/Bluetooth impl yet
- [x] Each app README cites blockers above and defers feature parity to later child plans

## Paths in scope

- `apps/ios/**` (new)
- `apps/android-customer/**` (new)
- `apps/android-management/**` (new)
- `bridges/contracts/**`, `bridges/README.md` (contracts / path map only)
- Optional: monorepo root notes in `AGENTS.md` run-command stubs if missing
- Docs only: this plan + brief README per app

## Out of scope

- Full catalog UI, cart, checkout, ContiPay/Paynow in-app, order tracking, My Garage live data
- Native QR/printer/biometric/GPS implementations (Phase 12 follow-on)
- PowerSync client SDK (Phase 14 deferred-to-mobile)
- HR / payroll screens; ZIMRA; payroll tax; browser geolocation/QR
- New Supabase migrations (unless a later decision requires customer RLS — separate `@backend_agent` plan)

## Risks / exclusions

- Master risk “Mobile before APIs” — mitigated by scaffold-only AC
- Do not invent customer payment secrets or fiscal receipt QR
- Bridge-First: any future scan/print/GPS goes through `bridges/`, never WebView

## Handoff

1. `@ios_agent` + `@android_agent` (parallel) → customer shells
2. `@management_app_agent` → management shell
3. `@hardware_mobile_agent` → contract stubs only
4. `/verifier` (lane paths, exclusion grep, no HTML5 QR)
5. `/manager` done gate; schedule backend customer-API plan before feature bind
