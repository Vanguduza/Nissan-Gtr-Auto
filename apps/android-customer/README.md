# GTR Customer — Android

Customer shell with thin Compose scaffolds for **cart**, **orders**, **My Garage**, and
**ContiPay / Paynow intent create** — mirroring web AuthZ RPCs in `apps/web/lib/customer-storefront.ts`.

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.customer` | Launcher + route shell |
| `:core:rpc` | `…customer.rpc` | `RpcClient` + `FakeRpcClient` + `RpcNames` |
| `:feature:cart` | `…customer.cart` | Create / add line / checkout |
| `:feature:orders` | `…customer.orders` | Invoice list + `get_customer_order` |
| `:feature:garage` | `…customer.garage` | Upsert / delete / list vehicles |
| `:feature:pay` | `…customer.pay` | ContiPay + Paynow intent create |

## Screens (scaffolds)

| Screen | Module | RPCs |
|--------|--------|------|
| `CartScreen` | `:feature:cart` | `create_customer_cart`, `add_customer_cart_line`, `checkout_customer_cart` |
| `OrdersScreen` | `:feature:orders` | `get_customer_order` (+ own-invoice SELECT) |
| `GarageScreen` | `:feature:garage` | `upsert_customer_garage_vehicle`, `delete_customer_garage_vehicle` |
| `PayIntentScreen` | `:feature:pay` | `create_customer_contipay_intent`, `create_customer_paynow_intent` |

## RPC binding: stub vs live

**Current (stub):** `MainActivity` injects `FakeRpcClient` — in-memory UUIDs / lists so screens
compile and exercise flows without the Supabase Kotlin SDK.

**Live (TODO):** implement `RpcClient` with supabase-kt:

```kotlin
client.postgrest.rpc(RpcNames.CREATE_CUSTOMER_CART, mapOf(
  "p_warehouse_id" to warehouseId,
  "p_currency" to currency.rpcValue,
  "p_fulfillment_mode" to fulfillmentMode.rpcValue,
  "p_exchange_rate" to exchangeRate,
))
```

Wire from `BuildConfig.SUPABASE_URL` / `SUPABASE_ANON_KEY`. Cart-line / invoice / garage **lists**
use PostgREST + RLS (same as web), not mutation RPCs.

Canonical names live in `core/rpc/.../RpcNames.kt` — keep in sync with web +
`supabase/migrations/20260724130000_customer_storefront_authz.sql`.

Payment intents: **create only** (intent UUID). No PSP crypto/secrets in the app; settle stays
webhook / service_role.

## Exclusions

- No ZIMRA / fiscal QR
- No HTML5 / WebView QR — Bridge-First (`bridges/`) when camera scanning is added
- No payroll tax (customer app)
- No real ContiPay / Paynow HMAC or private keys

## Env placeholders

See `.env.example`: `SUPABASE_URL`, `SUPABASE_ANON_KEY` only.

## Run

```bash
cd apps/android-customer
./gradlew assembleDebug          # macOS/Linux
.\gradlew.bat assembleDebug      # Windows
```

## Shared client (no duplicated pricing)

- `@gtr/supabase-client` — typed customer RPCs later
- `@gtr/shared` — money / cart / core-charge helpers — **no duplicate pricing in app modules**
- Hardware: `bridges/` contracts only

## Build status (this environment)

| Target | Status |
|--------|--------|
| `assembleDebug` | **Source-ready** — host may lack JDK / Android SDK; assemble when toolchain is present |
