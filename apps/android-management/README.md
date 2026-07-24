# GTR Management — Android

Staff/management shell with feature modules for POS, warehouse, dispatch, and HR attendance.
Thin Compose scaffolds for **HR clock** and **logistics pick/DN/delivery tracking** — not App Store polish.

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.management` | Launcher + route shell + auth gate + GPS Activity attach |
| `:core:rpc` | `…management.rpc` | `RpcClient` + `FakeRpcClient` + `SupabaseRpcClient` + `RpcNames` |
| `:feature:auth` | `…management.auth` | `SignInScreen` + `AuthGate` (GoTrue email/password) |
| `:feature:hr` | `…management.hr` | Clock in/out → `clock_attendance` |
| `:feature:dispatch` | `…management.dispatch` | Pick/DN + delivery job Start/Stop GPS |
| `:feature:pos` | `…management.pos` | POS placeholder (empty) |
| `:feature:warehouse` | `…management.warehouse` | Warehouse placeholder (empty) |
| `:location-tracker` | `…bridges.location` | Included from `bridges/android/location-tracker` (consume only) |

## Screens (scaffolds)

| Screen | Module | RPCs / role |
|--------|--------|-------------|
| `SignInScreen` / `AuthGate` | `:feature:auth` | GoTrue `signInWith(Email)` — session gate when Live |
| `ClockAttendanceScreen` | `:feature:hr` | `clock_attendance` |
| `DispatchScreen` | `:feature:dispatch` | pick/DN + `create_delivery_job`, `update_delivery_job_status`, `ingest_delivery_location` |

Also named: `cancel_delivery_note` (RPC wired; not a dedicated button).

## Delivery GPS (Bridge-First)

1. `MainActivity` owns `FusedLocationGpsBridge`, calls `attachActivity` in `onResume`, and forwards
   `onRequestPermissionsResult` for `REQUEST_LOCATION`.
2. Driver opens **Logistics — Pick / DN / Track**.
3. Select submitted DN → **Create job** → **Mark dispatched** (or paste an existing job UUID).
4. **Start tracking** → bridge `requestLocationPermission` + `watchPosition` → client throttle ≥5s →
   `toDeliveryLocationIngest` → `ingest_delivery_location` (Fake or Live RPC).
5. **Stop tracking** → `GpsWatchHandle.stop()` (stops FGS).

Compose never calls FusedLocation / `LocationManager` directly — only ViewModel → bridge.

## RPC binding: Fake vs Live

`MainActivity` uses `RpcClientFactory`:

| Mode | When | Implementation |
|------|------|----------------|
| **Live** | `SUPABASE_URL` + `SUPABASE_ANON_KEY` both non-empty **and** `rpc.forceFake` ≠ `true` | `SupabaseRpcClient` (supabase-kt BOM **3.1.1**: postgrest-kt + auth-kt) |
| **Fake** | URL/key missing, or `rpc.forceFake=true` | `FakeRpcClient` (in-memory) |

### Ingest coverage

| RPC | Fake | Live |
|-----|------|------|
| `ingest_delivery_location` | Validates lat/lng; increments `ingestedLocationCount`; returns UUID | `postgrest.rpc` with `p_delivery_job_id`, `p_lat`, `p_lng`, `p_recorded_at?`, `p_accuracy_m?` |
| `create_delivery_job` | In-memory job map (allows draft DN for scaffold) | Live RPC (requires submitted DN) |
| `update_delivery_job_status` | Updates in-memory status | Live RPC (`dispatched` / `completed` / `failed`) |

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

#### Local test users (staff)

From [`docs/LOCAL_DEVELOPMENT.md`](../../docs/LOCAL_DEVELOPMENT.md) §9 / `supabase/seed.sql`
(after `pnpm db:reset`):

| Email | Password | Role |
|-------|----------|------|
| `admin@gtr.local` | `local-dev-admin` | admin |
| `finance@gtr.local` | `local-dev-finance` | finance |
| `warehouse@gtr.local` | `local-dev-warehouse` | warehouse |

Dev-only passwords — never use in production. `seed.sql` has no separate customer accounts.

List reads use PostgREST / PowerSync bucket `by_staff_dispatch`, not mutation RPCs.

Canonical names: `core/rpc/.../RpcNames.kt`.

## Exclusions

- No ZIMRA / fiscal QR
- No payroll tax UI (PAYE, NSSA, etc.) — clock only
- No HTML5 / WebView QR or geolocation — Bridge-First (`bridges/`) for QR, ESC/POS, GPS (`ingest_delivery_location`)

## Env placeholders

See `.env.example`: `SUPABASE_URL`, `SUPABASE_ANON_KEY` only. Optional: `rpc.forceFake=true` in `local.properties`.

## Run

```bash
cd apps/android-management
./gradlew assembleDebug          # macOS/Linux
.\gradlew.bat assembleDebug      # Windows

# Throttle unit test
.\gradlew.bat :feature:dispatch:testDebugUnitTest
```

## Shared client (no duplicated pricing)

- `@gtr/supabase-client` — typed staff RPCs later
- `@gtr/shared` — money / cart / ledger helpers — **no duplicate pricing in app modules**
- Hardware: `bridges/` contracts only (GPS via `:location-tracker`)

## Build status (this environment)

| Target | Status |
|--------|--------|
| `assembleDebug` | Run with JDK 17+ and Android SDK after wiring. |
