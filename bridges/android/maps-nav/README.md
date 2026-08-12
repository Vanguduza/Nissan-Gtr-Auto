# Android maps-nav bridge

Bridge-First map **display** helpers for delivery + customer Android.
**No GPS ingest** here — that stays in `:location-tracker` FGS.

## Render SoR (B-MAP-1)

| Surface | SoR | Deprecated fallback |
|---------|-----|---------------------|
| Customer address pick | **MapLibre** (`MapLibreAddressPickMap` via `AddressPickMap`) | Google Maps Compose when `useMapLibre=false` or MapLibre init fails + `GOOGLE_MAPS_API_KEY` |
| Courier job map | **MapLibre** (`MapLibreJobMap` in android-delivery) | Google `DeliveryRouteMap` |

## Distance / ETA SoR

**OSRM** via `OsrmRouteFetcher` when `OSRM_URL` is set (delivery prefer-path). Google Directions remains deprecated HTTP fallback only — do not reintroduce Google as distance SoR.

## Adopt-first note

| Option | Decision |
|--------|----------|
| MapLibre Native (BSD) + optional self-hosted style | **Integrate** — render SoR |
| OSRM HTTP | **Integrate** — distance SoR when configured |
| Maps SDK + maps-compose + Directions REST | **Deprecated fallback** only |
| Navigation SDK | **Defer** — Google partnership |
| Fleetbase | **Never** |

## Include

```kotlin
include(":maps-nav")
project(":maps-nav").projectDir =
    file("../../bridges/android/maps-nav")
```

```kotlin
implementation(project(":maps-nav"))
```

### Customer app (`local.properties`)

```properties
# MapLibre SoR (default). Set false only for deprecated Google tiles.
# useMapLibre=false
# Deprecated Google fallback key (optional):
# GOOGLE_MAPS_API_KEY=your-maps-key
```

### Delivery app

```properties
# useMapLibre=false   # deprecated Google DeliveryRouteMap only
OSRM_URL=http://127.0.0.1:5000
# GOOGLE_MAPS_API_KEY=...  # Directions fallback only when OSRM unset
```

Wire Google key into the application manifest **only if** using the deprecated fallback:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${GOOGLE_MAPS_API_KEY}" />
```

## API surface

```kotlin
// Customer address pick — MapLibre SoR by default
AddressPickMap(
    selected = pin,
    onPick = { /* lat/lng */ },
    mapsKeyPresent = googleKey.isNotBlank(), // only for deprecated Google path
    useMapLibre = true,
)

// Courier route (deprecated Google tiles) — prefer MapLibreJobMap in delivery tracking
DeliveryRouteMap(...)

// Distance SoR when OSRM_URL set
val osrm = OsrmRouteFetcher(osrmUrl)
osrm.fetchDrivingRoute(origin, destination)

// Deprecated Directions fallback
val fetcher = DirectionsRouteFetcher(apiKey)

// External turn-by-turn
ExternalNavigation.openTurnByTurn(context, destination)
```

## Hard rules

- Bridge-First: no WebView GPS; do not call `ingest_delivery_location` from this module.
- Never commit API keys — `local.properties` / CI secrets only.
- No ZIMRA / fiscal payloads.
- No Fleetbase.
