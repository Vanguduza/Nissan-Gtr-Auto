# GTR Management — Android

Staff/management shell with feature modules for POS, warehouse, dispatch, and HR attendance.
Thin Compose scaffolds for **HR clock** and **logistics pick/DN** — not App Store polish.

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.management` | Launcher + route shell |
| `:core:rpc` | `…management.rpc` | `RpcClient` + `FakeRpcClient` + `SupabaseRpcClient` + `RpcNames` |
| `:feature:hr` | `…management.hr` | Clock in/out → `clock_attendance` |
| `:feature:dispatch` | `…management.dispatch` | Pick list + DN list/create/submit |
| `:feature:pos` | `…management.pos` | POS placeholder (empty) |
| `:feature:warehouse` | `…management.warehouse` | Warehouse placeholder (empty) |

## Screens (scaffolds)

| Screen | Module | RPCs |
|--------|--------|------|
| `ClockAttendanceScreen` | `:feature:hr` | `clock_attendance` |
| `DispatchScreen` | `:feature:dispatch` | `create_pick_list`, `confirm_pick_lines`, `create_delivery_note`, `submit_delivery_note` |

Also named (not yet on UI): `cancel_delivery_note`, `create_delivery_job`, `update_delivery_job_status`, `ingest_delivery_location` (bridge-only).

## RPC binding: Fake vs Live

`MainActivity` uses `RpcClientFactory`:

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

Live client installs GoTrue (`auth-kt`) with the **anon key** only. Staff RPCs need a session:

- When login UI exists: `supabase.auth.signInWith(...)` (or your auth screen stub).
- Until then: cast to `SupabaseRpcClient` and call `importAccessToken(accessToken)` from a secure sign-in flow — **never** commit JWTs or put them in BuildConfig.

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
```

## Shared client (no duplicated pricing)

- `@gtr/supabase-client` — typed staff RPCs later
- `@gtr/shared` — money / cart / ledger helpers — **no duplicate pricing in app modules**
- Hardware: `bridges/` contracts only

## Build status (this environment)

| Target | Status |
|--------|--------|
| `assembleDebug` | **Not run** — host has no JDK on `PATH` / `JAVA_HOME`. Source + Gradle deps landed; assemble with JDK 17+ and Android SDK. |
