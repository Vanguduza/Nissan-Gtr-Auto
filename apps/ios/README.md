# GTR Customer — iOS (Phase 11 scaffold)

Minimal SwiftUI customer shell. **No feature screens** (catalog bind, cart, checkout, tracking deferred).

## Prerequisites

- macOS with Xcode 16+ (iOS Simulator)
- This scaffold does **not** build on Windows; open the project on a Mac.

## Env placeholders

Copy `.env.example` values into Xcode scheme environment variables or a local `Secrets.xcconfig` (gitignored):

| Variable | Purpose |
|----------|---------|
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | Public anon key only — never service role |

No secrets are committed. See `.env.example`.

## Run (when toolchain exists)

```bash
cd apps/ios
open GTRCustomer.xcodeproj
# Product → Destination → iPhone 16 Simulator → Run

# Or CLI:
xcodebuild -scheme GTRCustomer \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -project GTRCustomer.xcodeproj \
  build
```

SPM package check (library target only):

```bash
cd apps/ios
swift build
```

## Shared client (no duplicated pricing)

- Typed DB / RPC shapes: monorepo `@gtr/supabase-client` (`packages/supabase-client`)
- Money, cart math, QR payload helpers: `@gtr/shared` (`packages/shared`)
- Native apps consume these via generated/OpenAPI or hand-ported thin wrappers later — **do not** reimplement price/core-charge logic in Swift.

Hardware (QR / print / biometric / GPS) goes through `bridges/` contracts only — never HTML5 or WebView camera QR.

## Blockers (before feature bind)

From [`docs/plans/2026-07-24-phase11-12-mobile-scaffold.md`](../../docs/plans/2026-07-24-phase11-12-mobile-scaffold.md):

1. Customer-facing cart/checkout RPCs (or RLS + grants) distinct from staff POS, or documented reuse with AuthZ.
2. Customer SELECT on own invoices / order status (+ optional DN/job summary) without staff role.
3. Customer payment-intent path (or “pay at counter / web-only” product decision in `docs/decisions/`).
4. Optional: My Garage / wishlist live tables if product requires parity with web `/account` stubs.

Feature parity waits for a backend “customer storefront API” slice + later Phase 11/12 feature plans.

## Build status (this environment)

| Target | Status |
|--------|--------|
| Xcode / Simulator | **Stub-only on Windows** — requires macOS + Xcode |
| SPM `swift build` | Requires Swift toolchain (typically macOS) |
