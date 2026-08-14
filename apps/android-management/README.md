# GTR Management — Android

Staff/management shell with feature modules for POS, warehouse (bins/consignment), procurement
blankets, B2B credit, dispatch, HR attendance, and staff live chat.
Thin Compose scaffolds — not App Store polish.

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.management` | Launcher + route shell + auth gate; **phone** / **tablet** flavors |
| `:core:rpc` | `…management.rpc` | `RpcClient` + `FakeRpcClient` + `SupabaseRpcClient` + `RpcNames` |
| `:feature:auth` | `…management.auth` | `SignInScreen` + `AuthGate` (GoTrue email/password) |
| `:feature:kiosk` | `…management.kiosk` | Idle lock, Device Admin console, Lock Task / DO hooks (tablet owns DO) |
| `:feature:hr` | `…management.hr` | Clock in/out → `clock_attendance` |
| `:feature:dispatch` | `…management.dispatch` | Pick/DN + assign/route/panic + staff live view (no GPS producer) |
| `:feature:pos` | `…management.pos` | Standalone sales till (search/catalog/checkout) + optional companion |
| `:feature:warehouse` | `…management.warehouse` | Receive/transfer/cycle + bins/pick-path/labels + consignment |
| `:feature:procurement` | `…management.procurement` | Blanket POs + releases + expiry/remaining alerts |
| `:feature:credit` | `…management.credit` | B2B `set_customer_credit` (admin\|sales\|finance) |
| `:feature:chat` | `…management.chat` | Staff inbox — open/mine/closed, claim/reply/close |
| `:qr-scanner` | `…bridges.qr` | Included from `bridges/android/qr-scanner` |
| `:escpos-printer` | `…bridges.escpos` | Included from `bridges/android/escpos-printer` |

## Screens (scaffolds)

| Screen | Module | RPCs / role |
|--------|--------|-------------|
| `SignInScreen` / `AuthGate` | `:feature:auth` | GoTrue `signInWith(Email)` — session gate when Live |
| `PosScreen` | `:feature:pos` | Sales till + named-customer search → `create_pos_cart(p_customer_id)`. Standalone catalog + companion QR. |
| `WarehouseScreen` | `:feature:warehouse` | receive / transfer / recon RPCs + Bridge QR → `lookupStockItemByOem` |
| `BinsScreen` | `:feature:warehouse` | `create_warehouse_bin` / deactivate / `set_stock_level_bin` / `get_pick_path_hints` + ESC/POS bin labels |
| `ConsignmentScreen` | `:feature:warehouse` | draft / add line / submit / cancel consignment RPCs |
| `BlanketsScreen` | `:feature:procurement` | `create_blanket_purchase_order` / submit / release + expiry/remaining alerts |
| `PreferredPoScreen` | `:feature:procurement` | H2 preferred roster PO — `create_purchase_order` / `submit_purchase_order` + Bridge QR OEM |
| `CreditScreen` | `:feature:credit` | `set_customer_credit` + PostgREST credit snapshot (explicit currency) |
| `ClockAttendanceScreen` | `:feature:hr` | `clock_attendance` |
| `DispatchScreen` | `:feature:dispatch` | pick/DN + assign/route/panic + staff live view |
| `ChatScreen` | `:feature:chat` | claim/reply/close + unread |

Also named: `cancel_delivery_note` (RPC wired; not a dedicated button).

**Role home:** sales-only → POS till as default (hub via “All modules”). Admin / warehouse / finance / HR / dispatcher → staff dashboard gated by `module_access`. Fake roles include admin so hub is the Fake landing. Live with no staff role → access denied (fail closed).

**Flavors (separate `applicationId`s):**

| Flavor | applicationId | Owns |
|--------|---------------|------|
| `phone` | `co.zw.nissangtr.management` | Portable management; no Device Owner / Magisk |
| `tablet` | `co.zw.nissangtr.management.tablet` | Kiosk: DO receiver, HOME category, Lock Task, Path B hooks |

Idle lock default **3 min** (Device Admin override 1–15); engine audio default **OFF**. Ops: [`docs/guides/android-management-kiosk-device-owner.md`](../../docs/guides/android-management-kiosk-device-owner.md).

Home hub: **POS**, **Warehouse**, **Bins**, **Consignment**, **Blankets**, **Credit** (admin\|sales\|finance), **HR**, **Logistics**, **Chat**.

### Fake shop-ops smoke (no Supabase)

1. Launch with empty `SUPABASE_URL` / `SUPABASE_ANON_KEY` (or `rpc.forceFake=true`).
2. Hub → **Procurement — Blanket POs** → see seeded BPO with expiry/remaining alerts → call-off release.
3. Hub → **Bins** → refresh seeded bins → **Get pick-path hints** → optional MAC + Connect + Print label (fails soft if unpaired).
4. Hub → **Consignment** → create draft / add line (use Fake stock/UOM UUIDs) / submit.
5. Hub → **CRM — B2B credit** → search “Acme” → save limit/hold (currency on snapshot).
6. Hub → **POS** → named customer search “Acme” → select → Open cart.

## Standalone POS demo (no pairing)

1. Open POS from hub (or sales-only Live lands on POS).
2. Pick warehouse → named-customer search (optional) → **Open cart**.
3. Search mode `part` (or vin/model/pnc) → **Search** → **Add** on a hit (resolves OEM → stock item).
4. Optionally **Scan QR → add** on till (Bridge-First; no session required).
5. Enter receipt email and/or WhatsApp E.164 → **Checkout**.
6. UI shows bind vs walk-in messaging from invoice `customer_id`.

## Optional companion path

1. On till: open cart → **Show pairing code** (`create_pos_scan_session`).
2. On phone (same staff account): **Scan companion** tab → enter code → **Claim session**.
3. **Scan inventory QR → add line** → till cart refreshes via poll (~4s).
4. Same checkout RPC as standalone. **Revoke** closes companion rights.

## Staff chat

1. Sign in as staff with role `admin`, `sales`, or `warehouse` (Fake mode always shows Chat).
2. Home → **Chat — Staff inbox**.
3. Filter **Open** / **Mine** / **Closed**; open a thread; **Claim** → reply → **Close**.
4. Mutations use the same RPCs as web `/staff/chat`. Lists via PostgREST + RLS.
5. **Polling ~5s** — Realtime plugin is not installed on the Android supabase-kt client yet.

## Dispatch / delivery (staff — no GPS producer)

1. Open **Logistics — Pick / DN / Dispatch**.
2. Pick/DN flow → **Create job** (Fake auto-seeds Harare pickup/dropoff; optional **Save coords**).
3. **Suggest** assignees → ranked list (dist / open / capacity) → tap row → **Assign** or **Override assign**.
4. **Mark dispatched** → `update_delivery_job_status` returns `{ delivery_job_id, track_token }` — share plaintext shown (**do not** remint after dispatch). **Rotate share token** only for intentional remint.
5. Optional **Generate POD OTP** (job must be dispatched) — read 6-digit code to customer.
6. Paste driver UUID → **Optimize stops** (`optimize_driver_stops` writes `route_sequence`).
7. **Refresh live track** → `get_delivery_track_point` (last point + ETA). Job must be **dispatched**; pings come from **`apps/android-delivery`** only.
8. **Panic inbox** lists open `panic_events` (poll ~5s); **Mark handled** sets `acknowledged_at` / `acknowledged_by`. Optional **Dial support** when `DELIVERY_SUPPORT_PHONE` is set in `local.properties`.

**GPS gate:** `DispatchViewModel.ALLOW_DRIVER_GPS_PRODUCER = false`. Start/Stop tracking UI removed; `startTracking()` sets an error pointing drivers to `apps/android-delivery`. Management does **not** depend on `:location-tracker` or call `ingest_delivery_location` from UI. Sole producer: delivery app FGS → ingest. (Bridge-First GPS remains only in the delivery app / `bridges/android/location-tracker` — management is staff view/subscribe.)

**Coords:** **Save coords** / create-job best-effort call `set_delivery_job_geo` (Fake + Live). Lat/lng pairs must both be set or both null.

## POS QR + ESC/POS (Bridge-First)

1. `MainActivity` owns `CameraxQrScannerBridge` + `BluetoothEscPosPrinterBridge`, attaches in
   `onResume`, forwards camera/Bluetooth permission results and `REQUEST_SCAN` Activity results.
2. **Till — Scan QR → add** or **Companion — Scan inventory QR** → `scanOnce()` →
   `add_cart_line_from_qr` with full `gtr://part/…` payload (staff standalone needs **no** scan session).
3. Optional: enter bonded printer MAC → **Connect printer** → on **Checkout**, best-effort
   `printReceiptLines` (checkout still succeeds if print fails).
4. **Warehouse — Scan QR** on receive / cycle-count → parse OEM → `lookupStockItemByOem` → fill UUIDs.

Compose never calls CameraX / BluetoothAdapter directly — only ViewModel → bridge.

**Hardware handoff:** pairing-code QR *display* (not inventory scan) is text-only today. If a
native “show pairing code as QR” helper is needed beyond CameraX scan, route to
`@hardware_mobile_agent` — do not add HTML5/browser QR.

## RPC binding: Fake vs Live

**Prefer Live** when env is set. `MainActivity` uses `RpcClientFactory`:

| Mode | When | Implementation |
|------|------|----------------|
| **Live** | `SUPABASE_URL` + `SUPABASE_ANON_KEY` both non-empty **and** `rpc.forceFake` ≠ `true` | `SupabaseRpcClient` (supabase-kt BOM **3.1.1**: postgrest-kt + auth-kt) |
| **Fake** | URL/key missing, or `rpc.forceFake=true` | `FakeRpcClient` (in-memory) |

### Coverage (dispatch-related)

| RPC / API | Fake | Live |
|-----------|------|------|
| `suggest_delivery_assignees` / `assign_delivery_job` | Exception override (unassigned/stuck); `p_override` | Live RPC |
| `optimize_driver_stops` | Demo stop list | Live RPC (persists `route_sequence`) |
| `get_delivery_track_point` | Fake point when dispatched | Live RPC (staff view) |
| `update_delivery_job_status` | jsonb + `track_token` on dispatch | Live jsonb (single mint) |
| `mint_delivery_track_token` | Remint/rotate only | Live remint (revokes prior) |
| `generate_delivery_pod_otp` | `042891` when dispatched | Live RPC |
| `set_delivery_job_geo` | In-memory | Live RPC |
| `listOpenPanicEvents` / `acknowledgePanicEvent` | In-memory panic rows | PostgREST `panic_events` |
| `ingest_delivery_location` | Contract only — **not** called from dispatch UI | Live RPC |
| `create_delivery_job` | In-memory (+ default Harare coords) | Live RPC |

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
./gradlew :app:assemblePhoneDebug    # portable management
./gradlew :app:assembleTabletDebug   # kiosk (DO / Path B hooks)
.\gradlew.bat :app:assemblePhoneDebug
.\gradlew.bat :app:assembleTabletDebug

# Unit tests
.\gradlew.bat :feature:kiosk:testDebugUnitTest
.\gradlew.bat :core:rpc:testDebugUnitTest
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
