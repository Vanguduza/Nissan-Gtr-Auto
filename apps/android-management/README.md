# GTR Management — Android (Phase 12 scaffold)

Staff/management shell with **placeholder** feature modules for POS, warehouse, and dispatch. No screens wired; no HR/payroll UI.

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.management` | Launcher shell |
| `:feature:pos` | `…management.pos` | POS placeholder (empty) |
| `:feature:warehouse` | `…management.warehouse` | Warehouse placeholder (empty) |
| `:feature:dispatch` | `…management.dispatch` | Dispatch placeholder (empty) |

Modules are included in the Gradle graph and dependable from `:app`, but expose no UI yet.

## Env placeholders

See `.env.example`: `SUPABASE_URL`, `SUPABASE_ANON_KEY` only.

## Run

```bash
cd apps/android-management
./gradlew assembleDebug          # macOS/Linux
.\gradlew.bat assembleDebug      # Windows
```

## Shared client (no duplicated pricing)

- `@gtr/supabase-client` — typed staff RPCs later
- `@gtr/shared` — money / cart / ledger helpers — **no duplicate pricing in app modules**
- Hardware: `bridges/` contracts only (QR, ESC/POS, biometric, GPS)

Management can later bind existing **staff** POS/logistics/recon RPCs — out of scope for this scaffold.

## Blockers (feature bind)

Customer-facing blockers from the Phase 11–12 plan do not block staff binding, but this scaffold still defers feature screens. Before shipping management features:

- Confirm staff RLS / role gates for each surface
- Native bridge implementations under `bridges/android/` (not browser QR)
- No ZIMRA / payroll tax screens

Customer storefront blockers (cart, invoice SELECT, payment intents) remain for customer apps — see plan.

## Build status (this environment)

| Target | Status |
|--------|--------|
| `assembleDebug` | **Stub-only here** — no JDK / Android SDK on scaffold host |
