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
| `:app` | `co.zw.nissangtr.customer` | Launcher + route shell + auth gate |
| `:core:rpc` | `…customer.rpc` | `RpcClient` + `FakeRpcClient` + `SupabaseRpcClient` + `RpcNames` |
| `:feature:auth` | `…customer.auth` | `SignInScreen` + `AuthGate` (GoTrue email/password) |
| `:feature:cart` | `…customer.cart` | Create / add line / checkout |
| `:feature:orders` | `…customer.orders` | Invoice list + `get_customer_order` |
| `:feature:garage` | `…customer.garage` | Upsert / delete / list vehicles |
| `:feature:pay` | `…customer.pay` | ContiPay + Paynow intent create |

## Screens (scaffolds)

| Screen | Module | RPCs / role |
|--------|--------|-------------|
| `SignInScreen` / `AuthGate` | `:feature:auth` | GoTrue `signInWith(Email)` — session gate when Live |
| `CartScreen` | `:feature:cart` | `create_customer_cart`, `add_customer_cart_line`, `checkout_customer_cart` |
| `OrdersScreen` | `:feature:orders` | `get_customer_order` (+ own-invoice SELECT) |
| `GarageScreen` | `:feature:garage` | `upsert_customer_garage_vehicle`, `delete_customer_garage_vehicle` |
| `PayIntentScreen` | `:feature:pay` | `create_customer_contipay_intent`, `create_customer_paynow_intent` |

## RPC binding: Fake vs Live

**Prefer Live** when env is set. `MainActivity` uses `RpcClientFactory`:

| Mode | When | Implementation |
|------|------|----------------|
| **Live** | `SUPABASE_URL` + `SUPABASE_ANON_KEY` both non-empty **and** `rpc.forceFake` ≠ `true` | `SupabaseRpcClient` (supabase-kt BOM **3.1.1**: postgrest-kt + auth-kt) |
| **Fake** | URL/key missing, or `rpc.forceFake=true` | `FakeRpcClient` (in-memory) |

### Switch / env

1. Copy `.env.example` values into **`local.properties`** (gitignored) at this project root:

```properties
sdk.dir=C\:\\Android\\sdk
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=your-anon-key
# Optional debug override — always use Fake even when URL+key are set:
# rpc.forceFake=true
```

2. Same names as root `.env.example` / web `NEXT_PUBLIC_SUPABASE_URL` + `NEXT_PUBLIC_SUPABASE_ANON_KEY`
   (Android BuildConfig fields are `SUPABASE_URL` / `SUPABASE_ANON_KEY` without the `NEXT_PUBLIC_` prefix).

3. Rebuild so BuildConfig picks up properties: `.\gradlew.bat assembleDebug`

### Auth (no hardcoded JWTs)

Live client installs GoTrue (`auth-kt`) with the **anon key** only. Session is persisted by the
SDK session manager (Android Settings / SharedPreferences) — never put passwords or JWTs in BuildConfig.

| Mode | Behaviour |
|------|-----------|
| **Live** | `AuthGate` blocks until `signInWith(Email)`; JWT attaches to PostgREST/RPC automatically. Sign-out calls `auth.signOut()` and clears storage. |
| **Fake** | Auth gate bypasses by default (`Continue without signing in` if optional login is shown). |

Preferred API: `SupabaseRpcClient.signInWithEmail(email, password)` → `auth.signInWith(Email) { … }`.  
Fallback only: `importAccessToken(accessToken)` if a custom flow cannot use Email sign-in.

#### Local test users

Staff seeds from [`docs/LOCAL_DEVELOPMENT.md`](../../docs/LOCAL_DEVELOPMENT.md) §9 / `supabase/seed.sql`
(after `pnpm db:reset`). Useful for management; customer storefront RPCs need a **customer**
(non-staff) account — seed.sql has **no** customer users today (sign up via web `/signup` or Auth admin).

| Email | Password | Notes |
|-------|----------|-------|
| `admin@gtr.local` | `local-dev-admin` | staff admin (seed) |
| `finance@gtr.local` | `local-dev-finance` | staff finance (seed) |
| `warehouse@gtr.local` | `local-dev-warehouse` | staff warehouse (seed) |

Dev-only passwords — never use in production.

### Pay

Intent **create only** (RPC → intent UUID). No ContiPay / Paynow HMAC, private keys, or PSP crypto in the app.

Canonical names: `core/rpc/.../RpcNames.kt` — keep in sync with web +
`supabase/migrations/20260724130000_customer_storefront_authz.sql`.

## Exclusions

- No ZIMRA / fiscal QR
- No HTML5 / WebView QR — Bridge-First (`bridges/`) when camera scanning is added
- No payroll tax (customer app)
- No real ContiPay / Paynow HMAC or private keys

## Env placeholders

See `.env.example`: `SUPABASE_URL`, `SUPABASE_ANON_KEY` only. Optional: `rpc.forceFake=true` in `local.properties`.

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
| `assembleDebug` | **Not run** — host has no JDK on `PATH` / `JAVA_HOME`. Source + Gradle deps landed; assemble with JDK 17+ and Android SDK. |
