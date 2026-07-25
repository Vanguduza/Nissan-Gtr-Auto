# GTR Management — Android

Staff/management shell with feature modules for POS, warehouse, dispatch, HR attendance,
and staff live chat.
Thin Compose scaffolds for **POS**, **warehouse**, **HR clock**, **logistics pick/DN/assignment**,
and **staff chat inbox** — not App Store polish.

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.management` | Launcher + route shell + auth gate |
| `:core:rpc` | `…management.rpc` | `RpcClient` + `FakeRpcClient` + `SupabaseRpcClient` + `RpcNames` |
| `:feature:auth` | `…management.auth` | `SignInScreen` + `AuthGate` (GoTrue email/password) |
| `:feature:hr` | `…management.hr` | Clock in/out → `clock_attendance` |
| `:feature:dispatch` | `…management.dispatch` | Pick/DN + assign/route/panic + staff live view (no GPS producer) |
| `:feature:pos` | `…management.pos` | Cart / QR add line / checkout + ESC/POS receipt |
| `:feature:warehouse` | `…management.warehouse` | Receive, dual-auth transfer, cycle-count + QR fill |
| `:feature:chat` | `…management.chat` | Staff inbox — open/mine/closed, claim/reply/close |
| `:qr-scanner` | `…bridges.qr` | Included from `bridges/android/qr-scanner` |
| `:escpos-printer` | `…bridges.escpos` | Included from `bridges/android/escpos-printer` |

## Screens (scaffolds)

| Screen | Module | RPCs / role |
|--------|--------|-------------|
| `SignInScreen` / `AuthGate` | `:feature:auth` | GoTrue `signInWith(Email)` — session gate when Live |
| `PosScreen` | `:feature:pos` | `create_pos_cart`, `add_cart_line`, `add_cart_line_from_qr`, `checkout_pos_cart` |
| `WarehouseScreen` | `:feature:warehouse` | receive / transfer / recon RPCs + Bridge QR → `lookupStockItemByOem` |
| `ClockAttendanceScreen` | `:feature:hr` | `clock_attendance` |
| `DispatchScreen` | `:feature:dispatch` | pick/DN + `suggest_delivery_assignees` / `assign_delivery_job` / `optimize_driver_stops` / `get_delivery_track_point` / `mint_delivery_track_token` / `generate_delivery_pod_otp` + panic inbox |
| `ChatScreen` | `:feature:chat` | `claim_chat_thread`, `post_chat_message`, `close_chat_thread`, `mark_chat_thread_read`, `chat_unread_count` + PostgREST lists |

Also named: `cancel_delivery_note` (RPC wired; not a dedicated button).

Home hub buttons: **POS**, **Warehouse**, **HR**, **Logistics**, **Chat** (RBAC: admin|sales|warehouse when Live).

## Staff chat

1. Sign in as staff with role `admin`, `sales`, or `warehouse` (Fake mode always shows Chat).
2. Home → **Chat — Staff inbox**.
3. Filter **Open** / **Mine** / **Closed**; open a thread; **Claim** → reply → **Close**.
4. Mutations use the same RPCs as web `/staff/chat`. Lists via PostgREST + RLS.
5. **Polling ~5s** — Realtime plugin is not installed on the Android supabase-kt client yet.

## Dispatch / delivery (staff — no GPS producer)

1. Open **Logistics — Pick / DN / Dispatch**.
2. Pick/DN flow → **Create job** → **Suggest** assignees → **Assign** or **Override assign**.
3. Paste driver UUID → **Optimize stops** (`optimize_driver_stops` writes `route_sequence`).
4. **Refresh live track** → `get_delivery_track_point` (last point + ETA). Job must be **dispatched**; pings come from **`apps/android-delivery`** only.
5. **Panic inbox** lists open `panic_events` (poll ~5s); **Mark handled** sets `acknowledged_at` / `acknowledged_by`. Optional **Dial support** when `DELIVERY_SUPPORT_PHONE` is set in `local.properties`.

**GPS gate:** `DispatchViewModel.ALLOW_DRIVER_GPS_PRODUCER = false`. Start/Stop tracking UI removed; `startTracking()` sets an error. Management does **not** depend on `:location-tracker` or call `ingest_delivery_location` from UI. Sole producer: delivery app FGS → ingest.

## POS QR + ESC/POS (Bridge-First)

1. `MainActivity` owns `CameraxQrScannerBridge` + `BluetoothEscPosPrinterBridge`, attaches in
   `onResume`, forwards camera/Bluetooth permission results and `REQUEST_SCAN` Activity results.
2. **POS — Scan QR → add** → `scanOnce()` → `add_cart_line_from_qr` with full `gtr://part/…` payload.
3. Optional: enter bonded printer MAC → **Connect printer** → on **Checkout**, best-effort
   `printReceiptLines` (checkout still succeeds if print fails).
4. **Warehouse — Scan QR** on receive / cycle-count → parse OEM → `lookupStockItemByOem` → fill UUIDs.

Compose never calls CameraX / BluetoothAdapter directly — only ViewModel → bridge.

## RPC binding: Fake vs Live

**Prefer Live** when env is set. `MainActivity` uses `RpcClientFactory`:

| Mode | When | Implementation |
|------|------|----------------|
| **Live** | `SUPABASE_URL` + `SUPABASE_ANON_KEY` both non-empty **and** `rpc.forceFake` ≠ `true` | `SupabaseRpcClient` (supabase-kt BOM **3.1.1**: postgrest-kt + auth-kt) |
| **Fake** | URL/key missing, or `rpc.forceFake=true` | `FakeRpcClient` (in-memory) |

### Coverage (dispatch-related)

| RPC / API | Fake | Live |
|-----------|------|------|
| `suggest_delivery_assignees` / `assign_delivery_job` | Seeded drivers; override assign | Live RPC |
| `optimize_driver_stops` | Demo stop list | Live RPC (persists `route_sequence`) |
| `get_delivery_track_point` | Fake point when dispatched | Live RPC (staff view) |
| `listOpenPanicEvents` / `acknowledgePanicEvent` | In-memory panic rows | PostgREST `panic_events` |
| `ingest_delivery_location` | Contract only — **not** called from dispatch UI | Live RPC |
| `create_delivery_job` / `update_delivery_job_status` | In-memory | Live RPC |

### Switch / env

1. Copy `.env.example` values into **`local.properties`** (gitignored) at this project root
   (`apps/android-management/local.properties`):

```properties
sdk.dir=C\:\\Android\\sdk
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=your-anon-key
# Optional panic dial target:
# DELIVERY_SUPPORT_PHONE=+263771234567
# Optional debug override — always use Fake even when URL+key are set:
# rpc.forceFake=true
```

When **both** `SUPABASE_URL` and `SUPABASE_ANON_KEY` are non-empty (and `rpc.forceFake` is not
`true`), `RpcClientFactory` selects **Live** `SupabaseRpcClient`. Otherwise **Fake** remains so
the app compiles and runs without keys.

2. Rebuild so BuildConfig picks up properties: `.\gradlew.bat assembleDebug`

### Auth (no hardcoded JWTs)

Live client installs GoTrue (`auth-kt`) with the **anon key** only. Session is persisted by the
SDK session manager — never put passwords or JWTs in BuildConfig.

## Exclusions

- No ZIMRA / fiscal QR
- No payroll tax UI (PAYE, NSSA, etc.) — clock only
- No HTML5 / WebView QR or geolocation — Bridge-First (`bridges/`) for QR, ESC/POS
- No driver GPS producer in management — `apps/android-delivery` sole FGS → `ingest_delivery_location`

## Env placeholders

See `.env.example`: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, optional `DELIVERY_SUPPORT_PHONE`.
Optional: `rpc.forceFake=true` in `local.properties`.

## Run

```bash
cd apps/android-management
./gradlew assembleDebug          # macOS/Linux
.\gradlew.bat assembleDebug      # Windows

# Dispatch unit tests (throttle + GPS gate + assign/panic Fake RPCs)
.\gradlew.bat :feature:dispatch:testDebugUnitTest
```

## Shared client (no duplicated pricing)

- `@gtr/supabase-client` — typed staff RPCs later
- `@gtr/shared` — money / cart / ledger helpers — **no duplicate pricing in app modules**
- Hardware: `bridges/` only (QR `:qr-scanner`, print `:escpos-printer`). Driver GPS: delivery app.

## Build status (this environment)

| Target | Status |
|--------|--------|
| Unit tests | Prefer `:feature:dispatch:testDebugUnitTest` |
| Full assemble | Requires local Android SDK |
