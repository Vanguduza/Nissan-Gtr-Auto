# GTR POS — standalone adaptive till

applicationId: `co.zw.nissangtr.pos`

Greenfield Compose till. Does **not** port management/CoolMall POS screens.
Theme: `GtrTheme` dark · tokens from `packages/android-ui` (`GtrColors`).

## Fake vs Live

| Mode | How |
|------|-----|
| **Fake** (default when unset) | Missing `SUPABASE_URL` and/or `SUPABASE_ANON_KEY`, **or** `rpc.forceFake=true` |
| **Live** | Both URL + anon key set and `rpc.forceFake` ≠ `true` → `LivePosClient` + GoTrue staff auth (`resolve_staff_login_email` → password) |

Same pattern as `apps/android-management` / `apps/android-delivery`: BuildConfig from gitignored `local.properties` via `PosClientFactory`.

### `local.properties` keys (required for Live)

Copy from [`local.properties.example`](./local.properties.example) into **`apps/android-pos/local.properties`** (gitignored):

```properties
sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk
SUPABASE_URL=https://gylrgwqyuiwkyykardwc.supabase.co
SUPABASE_ANON_KEY=paste-dashboard-anon-or-publishable-key-here
# Optional — force Fake even when URL+key are set:
# rpc.forceFake=true
```

| Property | BuildConfig | Notes |
|----------|-------------|-------|
| `SUPABASE_URL` | `SUPABASE_URL` | Hosted: `https://gylrgwqyuiwkyykardwc.supabase.co` |
| `SUPABASE_ANON_KEY` | `SUPABASE_ANON_KEY` | Dashboard → Project Settings → API → **anon** / publishable only — never service_role |
| `rpc.forceFake` | `RPC_FORCE_FAKE` | Optional; `true` forces Fake |

Never commit real keys. Staff login Live path: emp# / email / phone → `resolve_staff_login_email` → GoTrue email/password (see `StaffLoginShell`).

**Get the anon key:** Supabase Dashboard → project **gylrgwqyuiwkyykardwc** → **Project Settings → API**, or after `npx supabase login`:

```powershell
npx supabase projects api-keys --project-ref gylrgwqyuiwkyykardwc
```

## Hardware bridges

Rail Scan / Print / Drawer use `bridges/android/qr-scanner` (CameraX) and `bridges/android/escpos-printer` (Bluetooth ESC/POS + ESC p drawer). Fake bridges when forceFake. **No HTML5 camera / Web Bluetooth.**

### Connect printer (utilities)

Settings gear on the utility rail → **Till utilities**:

1. Pair the thermal printer in **Android Bluetooth settings** (system bond).
2. Tap **List bonded printers**, select a device (MAC is persisted in bridge prefs `gtr_escpos_printer` / `printer_mac`).
3. Tap **Connect printer** — status shows `Connected · <MAC>` or last error.
4. Rail Print / Drawer then use that session (auto-reconnect on print when MAC is set).

Fake/CI: `FakePosPrintBridge` lists a stub device and records receipts without RFCOMM.

## Offline store

Production Live → `SqlCipherOfflineStore` (Keystore passphrase, `allowBackup=false`). Fake / CI → `InMemoryOfflineStore`. Sync order remains replay → pull.

## Kiosk Lock Task

Adapted from management tablet kiosk. Device Owner:

```bash
adb shell dpm set-device-owner co.zw.nissangtr.pos/.kiosk.KioskDeviceAdminReceiver
```

Ops pattern: [docs/guides/android-management-kiosk-device-owner.md](../../docs/guides/android-management-kiosk-device-owner.md) (same HOME / DO flow; package is POS). Idle lock (`IdleLockController`) returns to login chrome; Settings dismiss can exit Lock Task for maintenance.

## Till float (1120)

Open/close via `open_account_period` / `close_account_period` on **1120 Cash Sales Till** only. **1110 Petty Cash** is finance-only. Settings → utilities → **Open / close float (1120)**. Migration `20260815230000_pos_till_float_1120_staff.sql` lets sales/warehouse open/close 1120.

## Returns → quarantine

Orders → **Return → quarantine** → `post_pos_refund` (invoice id + reason). Server routes stock to quarantine WH — never direct exchange / WH2 restock from the client.

## Chassis chips

Data-driven from `vehicle_master` (PostgREST) or catalog variants when Live; Fake list when Fake. Tap latches chassis and filters shop stock. Not a hard-coded R35-only production roster.

## Session handoff (management → POS)

Management **Open POS** passes Intent extras when a Live session exists: `HANDOFF=1`, staff display name, access + refresh tokens (never logged). POS imports session and skips login when valid; otherwise shows staff login.

## Build / test / run Live

```powershell
cd "C:\Nissan GTR auto\apps\android-pos"
# After pasting SUPABASE_ANON_KEY into local.properties:
.\gradlew.bat :pos-api:testDebugUnitTest :feature-till:testDebugUnitTest :feature-pay:testDebugUnitTest :sync:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Without anon key the APK still builds and runs in **Fake**. With URL+key, staff login uses Live GoTrue.

## Manual / Fake paths

1. Sign in → till with R35 latch, Brakes tiles, ticket USD 212 (pad + core).
2. Chassis chips → latch + shop stock filter.
3. Rail Sync / Scan / Print / Drawer (Fake in CI).
4. Settings → **Connect printer** (bonded list) · Open/close float 1120.
5. Orders → Return → quarantine sheet.
6. Idle → login chrome without process death.

Qty on tiles is always from `TillItem.saleableQty` (Postgres/snapshot) — never from Meili.
