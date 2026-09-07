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
   - OEM zip **audio** is best-effort / often ignored. The POS app itself intentionally has **no startup splash or startup audio**; it proceeds directly to the branded staff login surface.
5. **Provision Device Owner** on the **tablet** package (no Google accounts on device):
   ```bash
   adb shell dpm set-device-owner \
     co.zw.nissangtr.management.tablet/co.zw.nissangtr.management.kiosk.KioskDeviceAdminReceiver
   ```
6. **Lock Task + persistent HOME:** app reasserts the tablet package as persistent HOME, allowlists only the POS for Lock Task, disables status-bar/keyguard escape surfaces where Device Owner permits, and starts Lock Task. Verify:
   - Power on → optional firmware/Magisk bootanimation → Chalk starting window → **staff login** (no app splash)
   - No authenticated POS frame is restored before a fresh login on cold process start
   - Home / Recents / notifications blocked for ordinary staff
   - No flash to stock launcher after crash or reboot (`BOOT_COMPLETED` + persistent HOME)
7. **Kiosk settings** are reached only from **POS → Settings → Kiosk & device** by an authorized admin. Confirm idle **3 min** (override 1–15), Wi‑Fi/BT maintenance, Bluetooth/Wi-Fi printer diagnostics, scanner diagnostics, hardening status, policy reassert, audited Exit Lock Task and reboot.

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
4. Confirm Lock Task + persistent HOME + login-matched starting window (no app splash).
5. Same idle-lock and kiosk-settings behavior as Path B.

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

Logout returns directly to the in-app staff login. Cold process start also forces a fresh staff-auth boundary before POS content is eligible to render.

---

## Verification checklist

- [ ] Phone + tablet APKs install side-by-side (distinct applicationIds)
- [ ] Phone has no Device Admin receiver / HOME category
- [ ] Tablet: `dpm` succeeds; Lock Task enters on launch when DO
- [ ] Boot / crash recovery returns to GTR app, not stock launcher
- [ ] Path B: optional firmware bootanimation then login; Path A: login-matched Android starting window then login
- [ ] No app splash, launcher flash, Recents flash, or previously-authenticated POS frame on cold start
- [ ] Idle 3 min → lock overlay; all kiosk maintenance is under POS Settings; Exit Lock Task audited locally
- [ ] Bluetooth SPP and Wi-Fi/LAN ESC/POS printer endpoints can be configured from POS Settings
- [ ] Sales-only → POS; finance/hr/dispatcher/admin/warehouse → hub; empty roles → deny
