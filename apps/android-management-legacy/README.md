# ARCHIVED — 2026-08-14

Pre-CoolMall management app. **Do not port UI/IA from here.**

Behavioral SoT for the new app: **`apps/web` staff** (`lib/staff-auth.ts`, staff desks).  
UX shell: [`vendor/coolmall-gtr/`](../../vendor/coolmall-gtr/).  
This tree may only help discover RPCs if web libs are unclear.

Plan: [`docs/plans/2026-08-14-management-oss-shell-rebuild.md`](../../docs/plans/2026-08-14-management-oss-shell-rebuild.md).

---
# GTR Management â€” Android

Staff/management shell with feature modules for POS, warehouse (bins/consignment), procurement
blankets, B2B credit, dispatch, HR attendance, and staff live chat.
Thin Compose scaffolds â€” not App Store polish.

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.management` | Launcher + route shell + auth gate; **phone** / **tablet** flavors |
| `:core:rpc` | `â€¦management.rpc` | `RpcClient` + `FakeRpcClient` + `SupabaseRpcClient` + `RpcNames` |
| `:feature:auth` | `â€¦management.auth` | `SignInScreen` + `AuthGate` (GoTrue email/password) |
| `:feature:kiosk` | `â€¦management.kiosk` | Idle lock, Device Admin console, Lock Task / DO hooks (tablet owns DO) |
| `:feature:hr` | `â€¦management.hr` | Clock in/out â†’ `clock_attendance` |
| `:feature:dispatch` | `â€¦management.dispatch` | Pick/DN + assign/route/panic + staff live view (no GPS producer) |
| `:feature:pos` | `â€¦management.pos` | Standalone sales till (search/catalog/checkout) + optional companion |
| `:feature:warehouse` | `â€¦management.warehouse` | Receive/transfer/cycle + bins/pick-path/labels + consignment |
| `:feature:procurement` | `â€¦management.procurement` | Blanket POs + releases + expiry/remaining alerts |
| `:feature:credit` | `â€¦management.credit` | B2B `set_customer_credit` (admin\|sales\|finance) |
| `:feature:chat` | `â€¦management.chat` | Staff inbox â€” open/mine/closed, claim/reply/close |
| `:qr-scanner` | `â€¦bridges.qr` | Included from `bridges/android/qr-scanner` |
| `:escpos-printer` | `â€¦bridges.escpos` | Included from `bridges/android/escpos-printer` |

## Screens (scaffolds)

| Screen | Module | RPCs / role |
|--------|--------|-------------|
| `SignInScreen` / `AuthGate` | `:feature:auth` | GoTrue `signInWith(Email)` â€” session gate when Live |
| `PosScreen` | `:feature:pos` | Sales till + named-customer search â†’ `create_pos_cart(p_customer_id)`. Standalone catalog + companion QR. |
| `WarehouseScreen` | `:feature:warehouse` | receive / transfer / recon RPCs + Bridge QR â†’ `lookupStockItemByOem` |
| `BinsScreen` | `:feature:warehouse` | `create_warehouse_bin` / deactivate / `set_stock_level_bin` / `get_pick_path_hints` + ESC/POS bin labels |
| `ConsignmentScreen` | `:feature:warehouse` | draft / add line / submit / cancel consignment RPCs |
| `BlanketsScreen` | `:feature:procurement` | `create_blanket_purchase_order` / submit / release + expiry/remaining alerts |
| `PreferredPoScreen` | `:feature:procurement` | H2 preferred roster PO â€” `create_purchase_order` / `submit_purchase_order` + Bridge QR OEM |
| `CreditScreen` | `:feature:credit` | `set_customer_credit` + PostgREST credit snapshot (explicit currency) |
| `ClockAttendanceScreen` | `:feature:hr` | `clock_attendance` |
| `DispatchScreen` | `:feature:dispatch` | pick/DN + assign/route/panic + staff live view |
| `ChatScreen` | `:feature:chat` | claim/reply/close + unread |

Also named: `cancel_delivery_note` (RPC wired; not a dedicated button).

**Role home:** sales-only â†’ POS till as default (hub via â€œAll modulesâ€). Admin / warehouse / finance / HR / dispatcher â†’ staff dashboard gated by `module_access`. Fake roles include admin so hub is the Fake landing. Live with no staff role â†’ access denied (fail closed).

**Flavors (separate `applicationId`s):**

| Flavor | applicationId | Owns |
|--------|---------------|------|
| `phone` | `co.zw.nissangtr.management` | Portable management; no Device Owner / Magisk |
| `tablet` | `co.zw.nissangtr.management.tablet` | Kiosk: DO receiver, HOME category, Lock Task, Path B hooks |

Idle lock default **3 min** (Device Admin override 1â€“15); engine audio default **OFF**. Ops: [`docs/guides/android-management-kiosk-device-owner.md`](../../docs/guides/android-management-kiosk-device-owner.md).

Home hub: **POS**, **Warehouse**, **Bins**, **Consignment**, **Blankets**, **Credit** (admin\|sales\|finance), **HR**, **Logistics**, **Chat**.

### Fake shop-ops smoke (no Supabase)

1. Launch with empty `SUPABASE_URL` / `SUPABASE_ANON_KEY` (or `rpc.forceFake=true`).
2. Hub â†’ **Procurement â€” Blanket POs** â†’ see seeded BPO with expiry/remaining alerts â†’ call-off release.
3. Hub â†’ **Bins** â†’ refresh seeded bins â†’ **Get pick-path hints** â†’ optional MAC + Connect + Print label (fails soft if unpaired).
4. Hub â†’ **Consignment** â†’ create draft / add line (use Fake stock/UOM UUIDs) / submit.
5. Hub â†’ **CRM â€” B2B credit** â†’ search â€œAcmeâ€ â†’ save limit/hold (currency on snapshot).
6. Hub â†’ **POS** â†’ named customer search â€œAcmeâ€ â†’ select â†’ Open cart.

## Standalone POS demo (no pairing)

1. Open POS from hub (or sales-only Live lands on POS).
2. Pick warehouse â†’ named-customer search (optional) â†’ **Open cart**.
3. Search mode `part` (or vin/model/pnc) â†’ **Search** â†’ **Add** on a hit (resolves OEM â†’ stock item).
4. Optionally **Scan QR â†’ add** on till (Bridge-First; no session required).
5. Enter receipt email and/or WhatsApp E.164 â†’ **Checkout**.
6. UI shows bind vs walk-in messaging from invoice `customer_id`.

## Optional companion path

1. On till: open cart â†’ **Show pairing code** (`create_pos_scan_session`).
2. On phone (same staff account): **Scan companion** tab â†’ enter code â†’ **Claim session**.
3. **Scan inventory QR â†’ add line** â†’ till cart refreshes via poll (~4s).
4. Same checkout RPC as standalone. **Revoke** closes companion rights.

## Staff chat

1. Sign in as staff with role `admin`, `sales`, or `warehouse` (Fake mode always shows Chat).
2. Home â†’ **Chat â€” Staff inbox**.
3. Filter **Open** / **Mine** / **Closed**; open a thread; **Claim** â†’ reply â†’ **Close**.
4. Mutations use the same RPCs as web `/staff/chat`. Lists via PostgREST + RLS.
5. **Polling ~5s** â€” Realtime plugin is not installed on the Android supabase-kt client yet.

## Dispatch / delivery (staff â€” no GPS producer)

1. Open **Logistics â€” Pick / DN / Dispatch**.
2. Pick/DN flow â†’ **Create job** (Fake auto-seeds Harare pickup/dropoff; optional **Save coords**).
3. **Suggest** assignees â†’ ranked list (dist / open / capacity) â†’ tap row â†’ **Assign** or **Override assign**.
4. **Mark dispatched** â†’ `update_delivery_job_status` returns `{ delivery_job_id, track_token }` â€” share plaintext shown (**do not** remint after dispatch). **Rotate share token** only for intentional remint.
5. Optional **Generate POD OTP** (job must be dispatched) â€” read 6-digit code to customer.
6. Paste driver UUID â†’ **Optimize stops** (`optimize_driver_stops` writes `route_sequence`).
7. **Refresh live track** â†’ `get_delivery_track_point` (last point + ETA). Job must be **dispatched**; pings come from **`apps/android-delivery`** only.
8. **Panic inbox** lists open `panic_events` (poll ~5s); **Mark handled** sets `acknowledged_at` / `acknowledged_by`. Optional **Dial support** when `DELIVERY_SUPPORT_PHONE` is set in `local.properties`.

**GPS gate:** `DispatchViewModel.ALLOW_DRIVER_GPS_PRODUCER = false`. Start/Stop tracking UI removed; `startTracking()` sets an error pointing drivers to `apps/android-delivery`. Management does **not** depend on `:location-tracker` or call `ingest_delivery_location` from UI. Sole producer: delivery app FGS â†’ ingest. (Bridge-First GPS remains only in the delivery app / `bridges/android/location-tracker` â€” management is staff view/subscribe.)

**Coords:** **Save coords** / create-job best-effort call `set_delivery_job_geo` (Fake + Live). Lat/lng pairs must both be set or both null.

## POS QR + ESC/POS (Bridge-First)

1. `MainActivity` owns `CameraxQrScannerBridge` + `BluetoothEscPosPrinterBridge`, attaches in
   `onResume`, forwards camera/Bluetooth permission results and `REQUEST_SCAN` Activity results.
2. **Till â€” Scan QR â†’ add** or **Companion â€” Scan inventory QR** â†’ `scanOnce()` â†’
   `add_cart_line_from_qr` with full `gtr://part/â€¦` payload (staff standalone needs **no** scan session).
3. Optional: enter bonded printer MAC â†’ **Connect printer** â†’ on **Checkout**, best-effort
   `printReceiptLines` (checkout still succeeds if print fails).
4. **Warehouse â€” Scan QR** on receive / cycle-count â†’ parse OEM â†’ `lookupStockItemByOem` â†’ fill UUIDs.

Compose never calls CameraX / BluetoothAdapter directly â€” only ViewModel â†’ bridge.

**Hardware handoff:** pairing-code QR *display* (not inventory scan) is text-only today. If a
native â€œshow pairing code as QRâ€ helper is needed beyond CameraX scan, route to
`@hardware_mobile_agent` â€” do not add HTML5/browser QR.

## RPC binding: Fake vs Live

**Prefer Live** when env is set. `MainActivity` uses `RpcClientFactory`:

| Mode | When | Implementation |
|------|------|----------------|
| **Live** | `SUPABASE_URL` + `SUPABASE_ANON_KEY` both non-empty **and** `rpc.forceFake` â‰  `true` | `SupabaseRpcClient` (supabase-kt BOM **3.1.1**: postgrest-kt + auth-kt) |
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
| `ingest_delivery_location` | Contract only â€” **not** called from dispatch UI | Live RPC |
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
# Optional debug override â€” always use Fake even when URL+key are set:
# rpc.forceFake=true
```

When **both** `SUPABASE_URL` and `SUPABASE_ANON_KEY` are non-empty (and `rpc.forceFake` is not
`true`), `RpcClientFactory` selects **Live** `SupabaseRpcClient`. Otherwise **Fake** remains so
the app compiles and runs without keys.

2. Rebuild so BuildConfig picks up properties: `.\gradlew.bat assembleDebug`

### Auth (no hardcoded JWTs)

Live client installs GoTrue (`auth-kt`) with the **anon key** only. Session is persisted by the
SDK session manager â€” never put passwords or JWTs in BuildConfig.

## Exclusions

- No ZIMRA / fiscal QR
- No payroll tax UI (PAYE, NSSA, etc.) â€” clock only
- No HTML5 / WebView QR or geolocation â€” Bridge-First (`bridges/`) for QR, ESC/POS
- No driver GPS producer in management â€” `apps/android-delivery` sole FGS â†’ `ingest_delivery_location`

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

- `@gtr/supabase-client` â€” typed staff RPCs later
- `@gtr/shared` â€” money / cart / ledger helpers â€” **no duplicate pricing in app modules**
- Hardware: `bridges/` only (QR `:qr-scanner`, print `:escpos-printer`). Driver GPS: delivery app.

## Build status (this environment)

| Target | Status |
|--------|--------|
| Unit tests | Prefer `:feature:dispatch:testDebugUnitTest` |
| Full assemble | Requires local Android SDK |

