# Catalog APK (Megazip / PartSouq / multi-target satellite)

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

## Hardcoded scrape targets

Presets in `app/src/main/assets/site_profiles/` (seeded at `PRESET_VERSION = 3`):

| ID | Engine | Live hub (verified) | Notes |
|----|--------|---------------------|-------|
| `megazip` | megazip | `megazip.net` + `/parts/{maker}` | `/parts` hub 404; use catalog/home |
| `partsouq` | partsouq | partsouq.com | CF → FlareSolverr |
| `7zap` | 7zap → custom crawl | `7zap.com/en/catalog/cars/` | JSON-LD brands; `/…/{maker}/europe/{model}-parts-catalog/` |
| `catcar` | catcar → custom | `catcar.info` | Maker `/{slug}/?lang=en`; deep links use `l=` tokens |
| `japancats` | japancats → custom | `japancats.ru` | Maker `/{Maker}/`; regions `?Region=` |
| `japan_parts` | japan_parts → custom | **`japan-parts.eu`** | **`japan-parts.ru` is for-sale** — not a catalog |

Re-verify live hubs:

```powershell
python apps/catalog-apk/scripts/verify_site_profiles.py
```

## Implemented

- Chaquopy embedded worker (`:worker` process via WorkManager multiprocess)
- Megazip / PartSouq / 7zap / CatCar / JapanCats / Japan Parts EU / custom engines
- Live FlareSolverr health + auto-route (LAN/emulator; companion best-effort)
- Cooperative pause/resume (`desired_state` + `pause.flag`)
- Supervisor reclaim (stale heartbeat) + concurrent slots (1–2)
- Charge / unmetered Wi‑Fi / thermal gates (debug bypass on Projects)
- Bundle picker → JWT Edge `catalog-hierarchy-import`
- Projects: anon key + email/password session + `catalogapk://auth-callback`

## Ops notes

- **FlareSolverr sidecar (zero in-app config):** leave `apps/catalog-apk/sidecar/start.ps1 -Agent` running on the host (+ `adb-reverse.ps1` for USB). The APK auto-discovers `127.0.0.1` / `10.0.2.2` and only ensures/routes FlareSolverr when Cloudflare challenges.
- CF targets need FlareSolverr on `127.0.0.1:8191` (or `10.0.2.2` from emulator / LAN IP)
- Enable **Debug bypass gates** on desk builds without charging/Wi‑Fi
- Deploy Edge function `catalog-hierarchy-import` before import
- No service-role keys in the APK; no ZIMRA
- Operator is responsible for ToS/robots compliance per target

## Lane

`@catalog_apk_agent`
