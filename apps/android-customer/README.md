# GTR Customer — Android (Phase 11 scaffold)

Minimal Kotlin Compose shell. **No feature screens** until customer storefront APIs exist.

## Prerequisites

- JDK 17+
- Android SDK (API 34) + Android Studio or cmdline-tools
- First-time wrapper (if `gradle/wrapper/gradle-wrapper.jar` missing):

```bash
cd apps/android-customer
gradle wrapper --gradle-version 8.7
```

## Env placeholders

Copy `.env.example` → local `.env` (gitignored) or set in `local.properties` / BuildConfig later:

| Variable | Purpose |
|----------|---------|
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | Public anon key only |

## Run

```bash
cd apps/android-customer
./gradlew assembleDebug          # macOS/Linux
.\gradlew.bat assembleDebug      # Windows
```

Install debug APK via Android Studio **Run**, or `adb install app/build/outputs/apk/debug/app-debug.apk`.

## Shared client (no duplicated pricing)

- Types / query helpers: `@gtr/supabase-client` (`packages/supabase-client`)
- Money, cart, QR helpers: `@gtr/shared` (`packages/shared`)
- Do **not** reimplement pricing or core-charge split in Kotlin — port or call shared logic later.

QR / camera: Bridge-First via `bridges/` only. **No** HTML5 / WebView / ML Kit-in-app QR without a `bridges/android/` implementation.

## Blockers (before feature bind)

1. Customer-facing cart/checkout RPCs (or RLS + grants) distinct from staff POS, or documented reuse with AuthZ.
2. Customer SELECT on own invoices / order status (+ optional DN/job summary) without staff role.
3. Customer payment-intent path (or “pay at counter / web-only” decision in `docs/decisions/`).
4. Optional: My Garage / wishlist live tables if parity with web `/account` stubs is required.

See [`docs/plans/2026-07-24-phase11-12-mobile-scaffold.md`](../../docs/plans/2026-07-24-phase11-12-mobile-scaffold.md).

## Build status (this environment)

| Target | Status |
|--------|--------|
| `assembleDebug` | **Stub-only here** — no JDK / Android SDK on scaffold host; project files + wrapper props committed for when toolchain exists |
