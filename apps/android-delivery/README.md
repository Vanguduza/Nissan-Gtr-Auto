# GTR Delivery — Android (driver-only)

Dedicated driver app for assigned delivery jobs: always-on Bridge-First GPS, presence,
navigate, POD (photo + signature + OTP), geofence suggestions, fail/reattempt, multi-stop
order, panic, and offline queues.

Not a flavor of management — no POS / warehouse / HR / finance.

Plan: [`docs/plans/2026-07-25-dedicated-delivery-app.md`](../../docs/plans/2026-07-25-dedicated-delivery-app.md)  
ADR: [`docs/decisions/2026-07-25-dedicated-delivery-app.md`](../../docs/decisions/2026-07-25-dedicated-delivery-app.md)

## Prerequisites

- JDK 17+
- Android SDK (API 34)
- Wrapper jar (if missing): `gradle wrapper --gradle-version 8.7`

## Module layout

| Module | Package | Role |
|--------|---------|------|
| `:app` | `co.zw.nissangtr.delivery` | Launcher + auth gate + bridge Activity attach |
| `:core:rpc` | `…delivery.rpc` | `RpcClient` + Fake/Live + delivery RPC names |
| `:feature:auth` | `…delivery.auth` | GoTrue sign-in; gate role `driver` \| `admin` |
| `:feature:jobs` | `…delivery.jobs` | Job list/detail, presence, MapLibre live map, fail, stops, panic, geofence UI |
| `:feature:tracking` | `…delivery.tracking` | FGS GPS via location-tracker; throttle; offline location queue; `MapLibreJobMap` |
| `:feature:pod` | `…delivery.pod` | Camera evidence photo + Compose Canvas signature; OTP; offline POD queue; Storage `delivery-pods` + `submit_delivery_pod` |
| `:location-tracker` | `…bridges.location` | From `bridges/android/location-tracker` |
| `:pod-camera` | `…bridges.podcamera` | From `bridges/android/pod-camera` |
| `:pod-signature` | `…bridges.podsignature` | From `bridges/android/pod-signature` |
| `:maps-nav` | `…bridges.maps` | From `bridges/android/maps-nav` — OSRM + deprecated Google Directions/Maps fallback |

## Features → RPCs

| Feature | RPC / path |
|---------|------------|
| Auth | GoTrue **email + password** only (role gate: `driver` \| `admin`). No Google/Apple on this app. Password reset: web `/forgot-password` (same Edge as storefront). |
| Presence | `set_driver_presence` |
| Job list | PostgREST `delivery_jobs` (assignee = me) |
| GPS ingest | `ingest_delivery_location` (≥5s client throttle) |
| Geofence suggestion | `delivery_geofence_suggestion` (confirm only — never auto) |
| POD OTP | `generate_delivery_pod_otp` / `verify_delivery_pod_otp` |
| POD complete | Storage `delivery-pods` + `submit_delivery_pod` |
| Fail + reattempt | `fail_delivery_job` |
| Multi-stop order | `optimize_driver_stops` |
| Panic | `raise_delivery_panic` + dial `SUPPORT_PHONE` |

## Env / local.properties

Copy `.env.example` values into **`local.properties`** (gitignored):

```properties
sdk.dir=C\:\\Android\\sdk
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=your-anon-key
SUPPORT_PHONE=+263771234567
# Preferred route SoR (DIAL D-44). When set, in-app polyline + ETA label use OSRM (eta_source=osrm).
OSRM_URL=http://10.0.2.2:5000
# MapLibre courier map SoR (default on). Set false only for deprecated Google Maps fallback.
# useMapLibre=false
# Deprecated — Google Maps tiles + Directions only when useMapLibre=false or coords missing / OSRM unset
GOOGLE_MAPS_API_KEY=your-maps-key
# rpc.forceFake=true
```

Never commit real keys. Fake mode runs when URL/key missing or `rpc.forceFake=true`.

**Maps (Epic B / D-44):** **`MapLibreJobMap`** is the courier map SoR on `JobDetailScreen`. Google `DeliveryRouteMap` is an **explicit deprecated fallback** only (`useMapLibre=false` or missing coords) — never silent SoR.

**Routing:** set **`OSRM_URL`** to a self-hosted OSRM base (see `infra/satellites/README.md`). When configured, distance/ETA prefer OSRM and the UI shows `eta_source=osrm`. Google Directions is deprecated fallback only when `OSRM_URL` is blank. Full OSRM compose infra / Temporal worker = deferred (B-OSRM-1).

GPS ingest always uses `:location-tracker` FGS — the map is display-only.

## Build APK

```bash
cd apps/android-delivery

# Debug APK
./gradlew assembleDebug          # macOS/Linux
.\gradlew.bat assembleDebug      # Windows

# Output
# app/build/outputs/apk/debug/app-debug.apk

# Install on connected device/emulator
.\gradlew.bat installDebug

# Unit tests (throttle / POD keys / failure enums)
.\gradlew.bat :feature:tracking:testDebugUnitTest
.\gradlew.bat :feature:pod:testDebugUnitTest
.\gradlew.bat :feature:jobs:testDebugUnitTest
```

## Driver flow (scaffold)

1. Sign in as staff with role **`driver`** (Fake mode: Continue without signing in; Sign out returns to gate).
2. Home → **My jobs** (Active / Done / Failed tabs). Tap any row → job detail.
3. Job detail shows **Receipt copy** banners (document + **items bought** + white **notes**) and **Delivery address**; Done/Failed are read-only for complete.
4. Set presence (`available` / `on_duty` / `break` / `offline`) on Me tab.
5. **Route** tab → MapLibre live map with delivery pins + driver GPS (Start live GPS / On duty).
6. Active job → **Complete job** → existing POD (CameraX photo + touch signature + OTP) → Storage `delivery-pods` + `submit_delivery_pod` (Active→Done). Stays Active until signature.
7. **Mark job Failed** → `fail_delivery_job` (+ optional reattempt).
8. Geofence suggestions confirm-only; **Optimize stops**; **PANIC**.

## Exclusions

- No ZIMRA / fiscal QR
- No payroll tax
- No HTML5 / WebView geo or camera — Bridge-First only
- No POS / warehouse / HR / finance modules
- No Fleetbase runtime

## Shared client

- `@gtr/supabase-client` `delivery.ts` — canonical RPC names
- Hardware under `bridges/android/` only
