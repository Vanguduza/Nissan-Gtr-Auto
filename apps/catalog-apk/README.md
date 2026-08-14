# Catalog APK (Megazip / PartSouq / custom satellite)

Standalone Android operator tool for chassis-scoped catalog crawls. **Not** part of the GTR storefront/POS/management apps.

## Plan

[`docs/plans/2026-08-14-megazip-catalog-apk.md`](../../docs/plans/2026-08-14-megazip-catalog-apk.md)

## Build

JDK 17, Android SDK 34, **Python 3.12 on PATH** (Chaquopy).

```powershell
cd apps/catalog-apk
.\gradlew.bat :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

`preBuild` vendors `data-pipeline` modules + config into `src/main/python`.

## Implemented

- Chaquopy embedded worker (`:worker` process via WorkManager multiprocess)
- Megazip / PartSouq / custom path-template engines
- Live FlareSolverr health + auto-route (LAN/emulator; companion best-effort)
- Cooperative pause/resume (`desired_state` + `pause.flag`)
- Supervisor reclaim (stale heartbeat) + concurrent slots (1–2)
- Charge / unmetered Wi‑Fi / thermal gates (debug bypass on Projects)
- Bundle picker → JWT Edge `catalog-hierarchy-import`
- Projects: anon key + email/password session + `catalogapk://auth-callback`

## Ops notes

- CF targets need FlareSolverr on `127.0.0.1:8191` or LAN (`10.0.2.2` from emulator)
- Enable **Debug bypass gates** on desk builds without charging/Wi‑Fi
- Deploy Edge function `catalog-hierarchy-import` before import
- No service-role keys in the APK; no ZIMRA

## Lane

`@catalog_apk_agent`
