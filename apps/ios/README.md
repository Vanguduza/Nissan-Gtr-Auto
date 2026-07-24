# GTR Customer — iOS (AuthZ feature screens)

SwiftUI customer shell with thin Cart / Orders / Garage / Pay screens bound to the same storefront AuthZ RPCs as web (`apps/web/lib/customer-storefront.ts`).

## Stub vs live

| Mode | When | Behavior |
|------|------|----------|
| **Fake** (`FakeStorefrontApi`) | `SUPABASE_URL` / `SUPABASE_ANON_KEY` unset (default on Windows / scaffold) | In-memory demo cart, orders, garage, pay intents — no network |
| **Live** (`LiveStorefrontApi`) | Both env vars set | Documents exact RPC / edge names; throws until supabase-swift is wired on macOS |

Toolbar badge shows **Fake** or **Live**. Pay shows intent id + stub redirect message only — **no ContiPay/Paynow crypto or secrets in the app**.

## Screens

| Tab | Actions |
|-----|---------|
| Cart | Create cart, add demo line, checkout |
| Orders | List + detail (`get_customer_order`) |
| Garage | Upsert / delete vehicles |
| Pay | ContiPay or Paynow create-intent → intent id / stub deep link |

## AuthZ / RPC map (match web)

Requires **authenticated** session and a `customers` row with `profile_id = auth.uid()`. Peer invoices/carts denied. Settle remains **service_role / webhook only**.

| Client method | Supabase RPC / edge | Notes |
|---------------|---------------------|--------|
| `createCart` | `create_customer_cart` | `p_warehouse_id`, `p_currency`, `p_fulfillment_mode`, `p_exchange_rate` |
| `addCartLine` | `add_customer_cart_line` | `p_cart_id`, `p_stock_item_id`, `p_uom_id`, `p_qty` |
| `checkoutCart` | `checkout_customer_cart` | → invoice UUID |
| `loadOpenCart` | RLS `pos_carts` | `channel=storefront`, `status=open`, own rows |
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
  Package.swift                          # SPM: GTRCustomerCore
  Sources/GTRCustomerCore/               # AppEnv, StorefrontApi, models
  GTRCustomer/                           # SwiftUI app + Features/*
  GTRCustomer.xcodeproj/
```

## Prerequisites

- macOS with Xcode 16+ (iOS Simulator) to build the app target
- This host may lack Xcode — scaffold stays conceptually buildable; Fake mode needs no backend

## Env placeholders

Copy `.env.example` into Xcode scheme environment variables or a local `Secrets.xcconfig` (gitignored):

| Variable | Purpose |
|----------|---------|
| `SUPABASE_URL` | Supabase project URL |
| `SUPABASE_ANON_KEY` | Public anon key only — never service role |

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
| Xcode / Simulator | **Stub-only on Windows** — requires macOS + Xcode |
| SPM `swift build` | Requires Swift toolchain (typically macOS) |
| Feature UI | Present; Fake API runnable conceptually without network |
