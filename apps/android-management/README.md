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
| `:core:rpc` | `…management.rpc` | `RpcClient` + `FakeRpcClient` + `RpcNames` |
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

## RPC binding: stub vs live

**Current (stub):** `MainActivity` injects `FakeRpcClient` — in-memory UUIDs / lists so screens compile and exercise flows without the Supabase Kotlin SDK.

**Live (TODO):** implement `RpcClient` with supabase-kt:

```kotlin
client.postgrest.rpc(RpcNames.CLOCK_ATTENDANCE, mapOf(
  "p_employee_id" to employeeId,
  "p_event_type" to eventType.rpcValue,
  "p_notes" to notes,
))
```

Wire from `BuildConfig.SUPABASE_URL` / `SUPABASE_ANON_KEY`. List reads use PostgREST / PowerSync bucket `by_staff_dispatch`, not mutation RPCs.

Canonical names live in `core/rpc/.../RpcNames.kt`.

## Exclusions

- No ZIMRA / fiscal QR
- No payroll tax UI (PAYE, NSSA, etc.) — clock only
- No HTML5 / WebView QR or geolocation — Bridge-First (`bridges/`) for QR, ESC/POS, GPS (`ingest_delivery_location`)

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
- Hardware: `bridges/` contracts only

## Build status (this environment)

| Target | Status |
|--------|--------|
| `assembleDebug` | **Source-ready** — host may lack JDK / Android SDK; assemble when toolchain is present |
