# GTR Customer — iOS (AuthZ feature screens)

SwiftUI customer shell with thin Cart / Orders / Garage / Pay screens bound to the same storefront AuthZ RPCs as web (`apps/web/lib/customer-storefront.ts`).

## Fake vs Live switch

| Mode | When | Behavior |
|------|------|----------|
| **Fake** (`FakeStorefrontApi`) | `SUPABASE_URL` / `SUPABASE_ANON_KEY` unset **or** `STOREFRONT_FORCE_FAKE=1` | In-memory demo cart, orders, garage, pay intents — no network |
| **Live** (`LiveStorefrontApi`) | Both URL + anon set and force-fake off | Real HTTP: `POST {url}/rest/v1/rpc/{name}` + table selects + edge pay-initiate |

`StorefrontApiFactory.make()` picks the implementation. Toolbar badge shows **Fake** or **Live**.

```text
# Live (scheme env / Secrets.xcconfig — never commit secrets)
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=your-anon-key

# Optional force Fake while keeping URL configured
STOREFRONT_FORCE_FAKE=1
```

Pay shows intent id + checkout URL when the edge returns one — **no ContiPay/Paynow crypto or secrets in the app**.

### Session / AuthZ

Live sends `apikey` + `Authorization: Bearer {token}` on every call.

| Token | Source | Notes |
|-------|--------|-------|
| Anon (default) | `SUPABASE_ANON_KEY` | Scaffold default; customer AuthZ RPCs need a real user JWT |
| Customer JWT | `SUPABASE_ACCESS_TOKEN` or `LiveStorefrontApi.setAccessToken` | Required for `create_customer_cart` etc. (`customers.profile_id = auth.uid()`) |

Sign-in UI is not wired yet — use a short-lived access token from GoTrue for device testing, then replace with proper auth.

Transport is **URLSession PostgREST**, not supabase-swift SPM (avoids package resolve on Windows). RPC / edge names match web; swap the transport later on macOS if desired (`Package.swift` notes the optional dependency).

## Screens

| Tab | Actions |
|-----|---------|
| Cart | Create cart, add demo line, checkout |
| Orders | List + detail (`get_customer_order`) |
| Garage | Upsert / delete vehicles |
| Pay | ContiPay or Paynow create-intent → intent id / checkout URL |

## AuthZ / RPC map (match web)

Requires **authenticated** customer session and a `customers` row with `profile_id = auth.uid()`. Peer invoices/carts denied. Settle remains **service_role / webhook only**.

| Client method | Supabase RPC / edge | Notes |
|---------------|---------------------|--------|
| `createCart` | `create_customer_cart` | `p_warehouse_id`, `p_currency`, `p_fulfillment_mode`, `p_exchange_rate` |
| `addCartLine` | `add_customer_cart_line` | `p_cart_id`, `p_stock_item_id`, `p_uom_id`, `p_qty` |
| `checkoutCart` | `checkout_customer_cart` | → invoice UUID |
| `loadOpenCart` | RLS `pos_carts` + `pos_cart_lines` | `channel=storefront`, `status=open`, own rows |
| `listOrders` | RLS `sales_invoices` | Own invoices; detail via RPC |
| `getOrder` | `get_customer_order` | `p_invoice_id` → customer-safe JSONB |
| `listGarage` | RLS `customer_garage_vehicles` | Own rows |
| `upsertGarage` | `upsert_customer_garage_vehicle` | Make/model/VIN/primary |
| `deleteGarage` | `delete_customer_garage_vehicle` | `p_id` |
| `createContipayIntent` | Edge `contipay-initiate` → RPC `create_customer_contipay_intent` | Prefer edge for `checkout_url`; no client HMAC |
| `createPaynowIntent` | Edge `paynow-initiate` → RPC `create_customer_paynow_intent` | Prefer edge; no client hash |

Migration: `supabase/migrations/20260724130000_customer_storefront_authz.sql`. Decision: [`docs/decisions/2026-07-24-customer-self-pay.md`](../../docs/decisions/2026-07-24-customer-self-pay.md).

## Layout

```
apps/ios/
  Package.swift                          # SPM: GTRCustomerCore (no supabase-swift required)
  Sources/GTRCustomerCore/               # AppEnv, StorefrontApi, PostgrestClient, Live…
  GTRCustomer/                           # SwiftUI app + Features/*
  GTRCustomer.xcodeproj/
```

## Prerequisites

- macOS with Xcode 16+ (iOS Simulator) to build the app target
- This host may lack Xcode — core sources stay conceptually buildable; Fake mode needs no backend

## Env placeholders

Copy `.env.example` into Xcode scheme environment variables or a local `Secrets.xcconfig` (gitignored):

| Variable | Purpose |
|----------|---------|
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | Public anon key only — never service role |
| `SUPABASE_ACCESS_TOKEN` | Optional customer JWT for AuthZ (until sign-in UI) |
| `STOREFRONT_FORCE_FAKE` | `1` / `true` → Fake even when URL+anon set |

No PSP keys in the client. ContiPay / Paynow secrets stay in Edge Function env only.

## Run (when toolchain exists)

```bash
cd apps/ios
open GTRCustomer.xcodeproj
# Product → Destination → iPhone 16 Simulator → Run

xcodebuild -scheme GTRCustomer \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -project GTRCustomer.xcodeproj \
  build
```

SPM library check:

```bash
cd apps/ios
swift build
```

## Shared client (no duplicated pricing)

- Typed DB / RPC shapes: `@gtr/supabase-client`
- Money / cart math / QR helpers: `@gtr/shared` — do not reimplement core-charge logic in Swift
- Hardware (QR / print / biometric / GPS) via `bridges/` only — never HTML5 / WebView camera QR
- No ZIMRA / fiscal fields

## Build status (this environment)

| Target | Status |
|--------|--------|
| Xcode / Simulator | Requires macOS + Xcode |
| SPM `swift build` | Requires Swift toolchain (typically macOS) |
| Live HTTP | Implemented via URLSession PostgREST; needs URL+anon (+ customer JWT for AuthZ) |
| Feature UI | Present; Fake API runnable without network |
