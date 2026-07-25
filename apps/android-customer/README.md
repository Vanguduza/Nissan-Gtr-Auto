# GTR Customer — Android

Customer shell with thin Compose scaffolds for **cart**, **orders**, **My Garage**,
**ContiPay / Paynow intent create**, **live chat**, and **active delivery track** —
mirroring web AuthZ RPCs in `apps/web/lib/customer-storefront.ts`, chat helpers in
`apps/web/lib/chat.ts`, and privacy-safe track in `apps/web/lib/customer-delivery-track.ts`.

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
| `:feature:chat` | `…customer.chat` | Live chat threads / messages / composer |
| `:feature:track` | `…customer.track` | Active delivery last-point + ETA (`get_delivery_track_point`) |

## Screens (scaffolds)

| Screen | Module | RPCs / role |
|--------|--------|-------------|
| `SignInScreen` / `AuthGate` | `:feature:auth` | GoTrue `signInWith(Email)` — session gate when Live |
| `CartScreen` | `:feature:cart` | `create_customer_cart`, `add_customer_cart_line`, `checkout_customer_cart` |
| `OrdersScreen` | `:feature:orders` | `get_customer_order` (+ own-invoice SELECT) |
| `GarageScreen` | `:feature:garage` | `upsert_customer_garage_vehicle`, `delete_customer_garage_vehicle` |
| `PayIntentScreen` | `:feature:pay` | `create_customer_contipay_intent`, `create_customer_paynow_intent` |
| `ChatScreen` | `:feature:chat` | `start_chat_thread`, `post_chat_message`, `mark_chat_thread_read`, `chat_unread_count` (+ thread/message SELECT) |
| `DeliveryTrackScreen` | `:feature:track` | `get_delivery_track_point` (job id and/or share token) — last point + ETA only |

## Active delivery track (privacy)

Home → **Track delivery**, or Orders → **Track delivery** / **Track with share token**.

- Calls `get_delivery_track_point` only — **never** SELECT on `delivery_locations`, **never** a GPS trail UI.
- Inputs: share **token** (SMS `/track/{token}`) and/or owned **delivery job id** (signed-in customer).
- Customers **do not** mint tokens (`mint_delivery_track_token` is staff/dispatch only).
- Polls ~15s while tracking (no realtime-kt trail subscription).
- No map SDK in this app — shows coordinates + ETA text (Bridge-First: no WebView/browser geo).
- Empty when job is not `dispatched`, token expired/revoked, or no pings yet.

Fake seed: invoice `INV-SEED-DISPATCH` → job `…dj` + token `FakeRpcClient.SEED_TRACK_TOKEN`.

Optional intent extras: `track_token`, `track_job_id` (open track on launch).

## Live chat

Home → **Live chat**. Same tables/RPCs as web `/account/chat`:

- List: `chat_threads` / `chat_messages` (PostgREST + RLS)
- Mutations: prefer RPCs above (no direct INSERT)
- Start support or parts thread with optional subject / first message
- Message bubbles + composer; closed threads are read-only
- **WhatsApp CTA** opens `wa.me` (digits from `WHATSAPP_E164` in `local.properties`, default `263770000000`)

### Realtime gap → poll

Live `SupabaseRpcClient` installs **Auth + Postgrest only** (no `realtime-kt`). While a
thread is open, `ChatViewModel` **polls messages every 3s**. To match web Realtime later,
add `realtime-kt` to `:core:rpc` and subscribe to `chat_messages` INSERT.

## RPC binding: Fake vs Live

**Prefer Live** when env is set. `MainActivity` uses `RpcClientFactory`:

| Mode | When | Implementation |
|------|------|----------------|
| **Live** | `SUPABASE_URL` + `SUPABASE_ANON_KEY` both non-empty **and** `rpc.forceFake` ≠ `true` | `SupabaseRpcClient` (supabase-kt BOM **3.1.1**: postgrest-kt + auth-kt) |
| **Fake** | URL/key missing, or `rpc.forceFake=true` | `FakeRpcClient` (in-memory, including chat) |

### Switch / env

1. Copy `.env.example` values into **`local.properties`** (gitignored) at this project root:

```properties
sdk.dir=C\:\\Android\\sdk
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=your-anon-key
# Optional debug override — always use Fake even when URL+key are set:
# rpc.forceFake=true
# Optional WhatsApp CTA digits (no +):
# WHATSAPP_E164=263770000000
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
`supabase/migrations/20260724130000_customer_storefront_authz.sql` and live-chat migration.

## Exclusions

- No ZIMRA / fiscal QR
- No HTML5 / WebView QR — Bridge-First (`bridges/`) when camera scanning is added
- No payroll tax (customer app)
- No real ContiPay / Paynow HMAC or private keys

## Env placeholders

See `.env.example`: `SUPABASE_URL`, `SUPABASE_ANON_KEY` only. Optional: `rpc.forceFake=true`,
`WHATSAPP_E164` in `local.properties`.

## Run / test chat

```bash
cd apps/android-customer
./gradlew assembleDebug          # macOS/Linux
.\gradlew.bat assembleDebug      # Windows
```

1. **Fake (no Supabase):** leave URL/key unset → Home → Live chat → Start thread → send messages.
2. **Live:** set `SUPABASE_URL` + `SUPABASE_ANON_KEY`, sign in as a **customer** user, open Live chat.
3. Start support/parts thread; open it; send; confirm WhatsApp CTA launches `wa.me`.
4. Optional: reply as staff on web staff chat; Android poll should pick up within ~3s.

## Shared client (no duplicated pricing)

- `@gtr/supabase-client` — typed customer RPCs later
- `@gtr/shared` — money / cart / core-charge helpers — **no duplicate pricing in app modules**
- Hardware: `bridges/` contracts only

## Build status (this environment)

| Target | Status |
|--------|--------|
| `assembleDebug` | **Not run** — host may lack JDK on `PATH` / `JAVA_HOME`. Source + Gradle deps landed; assemble with JDK 17+ and Android SDK. |
