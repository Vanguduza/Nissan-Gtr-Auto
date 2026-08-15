# Android ESC/POS printer bridge — escpos-printer

Implements `EscPosPrinterBridge` from `bridges/contracts/qr-inventory.ts` using
**BluetoothAdapter RFCOMM (SPP)**. Sends ESC/POS bytes only — **no Supabase /
network** inside this module.

## Include from management app

In `apps/android-management/settings.gradle.kts`:

```kotlin
include(":escpos-printer")
project(":escpos-printer").projectDir =
    file("../../bridges/android/escpos-printer")
```

In the POS (or app) module `build.gradle.kts`:

```kotlin
implementation(project(":escpos-printer"))
```

## API surface

```kotlin
val bridge = BluetoothEscPosPrinterBridge(context)
bridge.attachActivity(activity) // required before requestBluetoothPermission()
bridge.setPrinterAddress("00:11:22:33:44:55") // bonded MAC; persisted

// In Activity.onRequestPermissionsResult:
// if (requestCode == BluetoothEscPosPrinterBridge.REQUEST_BLUETOOTH)
//     bridge.onPermissionResult()

val status = bridge.requestBluetoothPermission()
bridge.connect()
bridge.printInventoryLabel(
    EscPosPrintJob(
        qrPayload = "gtr://part/21410-JF00A?batch=RCV-1&valuation=FIFO",
        oemPartNumber = "21410-JF00A",
        batchCode = "RCV-1",
        valuation = InventoryQrValuation.FIFO,
    ),
)
// Best-effort POS checkout receipt:
bridge.printReceiptLines(
    listOf(
        EscPosReceiptLine("GTR Auto", emphasis = true),
        EscPosReceiptLine("Invoice: $invoiceId"),
    ),
)
bridge.printRaw(customEscPosBytes)

// Cash drawer (RJ11 on printer) — ESC p pulse; requires connect()
bridge.openCashDrawer() // pin 2 default
// or narrow surface:
val drawer: CashDrawerBridge = BluetoothCashDrawerBridge(bridge)
drawer.openDrawer(CashDrawerPin.PIN_2)
// tests/debug only:
// FakeCashDrawerBridge().openDrawer()

bridge.disconnect()
```

## Cash drawer

| API | Bytes | Notes |
|-----|-------|-------|
| `EscPosCommands.cashDrawerPulse` / `openCashDrawer` | `ESC p m t1 t2` (`1B 70 …`) | Default for Bluetooth thermal + drawer |
| `EscPosCommands.cashDrawerPulseDleDc4` | `DLE DC4 1 m t` (`10 14 01 …`) | Alternate real-time form; send via `printRaw` if needed |

Fake: `FakeCashDrawerBridge` — unit tests / debug only. No Web Bluetooth.

## Permissions

| Permission | Why |
|------------|-----|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (maxSdk 30) | Pre-Android 12 connect |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` (neverForLocation) | Android 12+ |

Printer must already be **bonded** in system Bluetooth settings; this bridge
opens an RFCOMM socket to the configured MAC (does not run a discovery UX).

## Hard rules

- Bridge-First only — no Web Bluetooth.
- Inventory QR payloads only (`gtr://part/…`) — no ZIMRA / fiscal glyphs.
- No network or Supabase inside this module.
