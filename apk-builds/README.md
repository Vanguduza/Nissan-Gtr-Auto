# APK builds

Debug-signed APKs for direct install (Settings → allow installing from this source). They talk to
the live Supabase project. Not for Play Store distribution (no release signing key yet).

| App | File | Package | Launcher name |
|---|---|---|---|
| Shopping (customer) | `GTR-Shopping-c50adb33-debug.apk` | `co.zw.nissangtr.customer` | GTR Customer |
| POS (tablet kiosk) | `GTR-POS-Kiosk-tablet-c50adb33-debug.apk` | `co.zw.nissangtr.management.tablet` | GTR POS Kiosk |
| Management (phone) | `GTR-Management-phone-c50adb33-debug.apk` | `co.zw.nissangtr.management` | GTR Management |

Provenance: repository `Vanguduza/Nissan-Gtr-Auto`, branch `ccr-8c44a0d0-msyynq`, commit
`c50adb33c0105c2fb08cad36e594e22798041cdb` (app code identical to `42aa0f64`; later commits only
changed CI workflows). Built 2026-10-09 with `assembleDebug` / `assembleTabletDebug` /
`assemblePhoneDebug` after `release-check` passed for customer-android and pos-android; the 266
tablet POS unit tests passed on the same build.

This branch only carries these files on top of that commit; it is not meant to be merged.
