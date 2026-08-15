# GTR POS — standalone adaptive till

applicationId: `co.zw.nissangtr.pos`

Greenfield Compose till. Does **not** port management/CoolMall POS screens.
Theme: `GtrTheme` dark · tokens from `packages/android-ui` (`GtrColors`).

## Fake vs Live

| Mode | How |
|------|-----|
| **Fake** | `rpc.forceFake=true` in `apps/android-pos/local.properties`, or missing `SUPABASE_URL` / `SUPABASE_ANON_KEY` |
| **Live** | `rpc.forceFake=false` + URL + anon key → GoTrue staff auth (`resolve_staff_login_email`) + real PostgREST/RPC via `LivePosClient` |

BuildConfig fields (from `local.properties`): `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `RPC_FORCE_FAKE`.

## Hardware bridges

Rail Scan / Print / Drawer use `bridges/android/qr-scanner` (CameraX) and `bridges/android/escpos-printer` (Bluetooth ESC/POS + ESC p drawer). Fake bridges when forceFake. **No HTML5 camera.**

## Offline store

Production Live → `SqlCipherOfflineStore` (Keystore passphrase, `allowBackup=false`). Fake / CI → `InMemoryOfflineStore`. Sync order remains replay → pull.

## Kiosk Lock Task

Adapted from management tablet kiosk. Device Owner:

```bash
adb shell dpm set-device-owner co.zw.nissangtr.pos/.kiosk.KioskDeviceAdminReceiver
```

Ops pattern: [docs/guides/android-management-kiosk-device-owner.md](../../docs/guides/android-management-kiosk-device-owner.md) (same HOME / DO flow; package is POS). Idle lock (`IdleLockController`) returns to login chrome; Settings dismiss can exit Lock Task for maintenance.

## Till float (1120)

Open/close via `open_account_period` / `close_account_period` on **1120 Cash Sales Till** only. **1110 Petty Cash** is finance-only. Settings rail → float sheet. Migration `20260815230000_pos_till_float_1120_staff.sql` lets sales/warehouse open/close 1120.

## Returns → quarantine

Orders → **Return → quarantine** → `post_pos_refund` (invoice id + reason). Server routes stock to quarantine WH — never direct exchange / WH2 restock from the client.

## Chassis chips

Data-driven from `vehicle_master` (PostgREST) or catalog variants when Live; Fake list when Fake. Tap latches chassis and filters shop stock. Not a hard-coded R35-only production roster.

## Session handoff (management → POS)

Management **Open POS** passes Intent extras when a Live session exists: `HANDOFF=1`, staff display name, access + refresh tokens (never logged). POS imports session and skips login when valid; otherwise shows staff login.

## Build / test

```bash
cd apps/android-pos
.\gradlew.bat :pos-api:testDebugUnitTest :feature-till:testDebugUnitTest :feature-pay:testDebugUnitTest :sync:testDebugUnitTest :app:assembleDebug
```

## Manual / Fake paths

1. Sign in → till with R35 latch, Brakes tiles, ticket USD 212 (pad + core).
2. Chassis chips → latch + shop stock filter.
3. Rail Sync / Scan / Print / Drawer (Fake in CI).
4. Settings → open/close float 1120.
5. Orders → Return → quarantine sheet.
6. Idle → login chrome without process death.

Qty on tiles is always from `TillItem.saleableQty` (Postgres/snapshot) — never from Meili.
