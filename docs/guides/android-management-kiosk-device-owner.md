# Android Management — Tablet Kiosk Device Owner Runbook

**APK split (locked L1):**

| Flavor | `applicationId` | Owns kiosk? |
|--------|-----------------|-------------|
| `phone` | `co.zw.nissangtr.management` | No — portable management; **no** Device Owner / Lock Task / Magisk |
| `tablet` | `co.zw.nissangtr.management.tablet` | Yes — Device Owner, Lock Task, HOME launcher, Path B primary |

**Build:**

```bash
cd apps/android-management
./gradlew :app:assemblePhoneDebug
./gradlew :app:assembleTabletDebug
```

Windows: `gradlew.bat :app:assemblePhoneDebug` / `assembleTabletDebug`.

Device Admin receiver (tablet only):

`co.zw.nissangtr.management.kiosk.KioskDeviceAdminReceiver`

---

## Path B — Magisk + Lock Task (**primary** for rooted CN tablets)

Chosen fleet path when the bootloader is unlockable and the shop accepts root ops.

### Steps

1. **Backup** device data; confirm unlockable CN tablet SKU (exact allow-list still open in plan §11.4).
2. **Unlock bootloader** (factory reset):
   ```bash
   fastboot flashing unlock
   ```
   Use this reset as the clean slate before Device Owner.
3. **Patch boot with Magisk:**
   - Extract stock `boot.img` for the SKU.
   - `adb push boot.img /sdcard/Download/`
   - Magisk app → **Select and Patch a File** → pull `magisk_patched_*.img`
   - `fastboot flash boot magisk_patched.img` → reboot
4. **Custom `bootanimation.zip` (ops — no binary in repo this phase):**
   - Generate frames via [bootanimation-creator](https://github.com/search?q=bootanimation-creator) or equivalent (`desc.txt` + `part0/` PNGs).
   - Remount system writable as needed for the OEM.
   - Push:
     ```bash
     adb root
     adb remount   # or OEM equivalent
     adb push bootanimation.zip /system/media/bootanimation.zip
     adb shell chmod 644 /system/media/bootanimation.zip
     adb reboot
     ```
   - OEM zip **audio** is best-effort / often ignored. **Authoritative audio** = app splash + Device Admin kill switch (**default OFF**).
5. **Provision Device Owner** on the **tablet** package (no Google accounts on device):
   ```bash
   adb shell dpm set-device-owner \
     co.zw.nissangtr.management.tablet/co.zw.nissangtr.management.kiosk.KioskDeviceAdminReceiver
   ```
6. **Lock Task:** app calls `setLockTaskPackages` + `startLockTask` when DO is active. Verify:
   - Power on → Magisk/bootanimation → immersive app → login
   - Home / Recents / notifications blocked for ordinary staff
   - No flash to stock launcher after crash or reboot (`BOOT_COMPLETED` + HOME category)
7. **Device Admin console** (admin role on tablet): confirm engine audio **OFF**, idle **3 min** (override 1–15), Wi‑Fi/BT intents, printer/scanner diagnostic text, Path B vs Path A status, audited Exit Lock Task.

### Risks (accepted for primary CN SKUs)

- Play Integrity / SafetyNet may fail — do not rely on Play-only APIs for POS fleet.
- OTA can wipe Magisk / bootanimation — pin OEM builds; re-run this runbook after updates.
- Root increases attack surface — compensate with DO allow-list, no staff ADB, audited maintenance.

Magisk does **not** replace Lock Task. Path B = **both** layers.

**This app never auto-flashes Magisk.** Flashing is ops/ADB only. Device Admin shows heuristic Path B / Path A status only.

---

## Path A — AOSP Device Owner + Lock Task (**fallback**)

Use when the OEM cannot unlock/root or the SKU is Path A–only.

1. Factory-reset tablet; remove Google accounts.
2. Install tablet APK (`co.zw.nissangtr.management.tablet`).
3. Same `dpm set-device-owner …KioskDeviceAdminReceiver` as above.
4. Confirm Lock Task + immersive app splash (no firmware logo branding).
5. Same idle / engine-audio defaults as Path B.

Meets operational kiosk Must **without** firmware boot logo.

---

## Phone APK (non-goals)

- Do **not** set Device Owner on `co.zw.nissangtr.management`.
- Do **not** register HOME launcher category or Magisk ownership.
- Dashboard-first hub; optional POS via existing role/`module_access` gates.
- Companion scan remains available; tablet stays SoR for counter Lock Task.

---

## Idle lock (locked L5)

| Setting | Value |
|---------|--------|
| Default | **3 minutes** inactivity |
| Device Admin override | **1–15 minutes** (DataStore on device) |
| Behavior | Clear in-memory session UI → branded in-app reauth; **stay in Lock Task**; never drop to launcher |

Logout returns to in-app staff login and does **not** replay full splash by default.

---

## Verification checklist

- [ ] Phone + tablet APKs install side-by-side (distinct applicationIds)
- [ ] Phone has no Device Admin receiver / HOME category
- [ ] Tablet: `dpm` succeeds; Lock Task enters on launch when DO
- [ ] Boot / crash recovery returns to GTR app, not stock launcher
- [ ] Path B: bootanimation then app; Path A: app splash only
- [ ] Idle 3 min → lock overlay; Exit Lock Task audited locally
- [ ] Engine audio toggle defaults OFF
- [ ] Sales-only → POS; finance/hr/dispatcher/admin/warehouse → hub; empty roles → deny
