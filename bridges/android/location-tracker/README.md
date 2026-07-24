# Android GPS bridge — location-tracker

Implements `GpsBridge` from `bridges/contracts/gps.ts` using
**FusedLocationProviderClient**. Emits `GpsCoordinate` only — **no Supabase /
network** inside this module.

## Include from management app

In `apps/android-management/settings.gradle.kts`:

```kotlin
include(":location-tracker")
project(":location-tracker").projectDir =
    file("../../bridges/android/location-tracker")
```

In the dispatch (or app) module `build.gradle.kts`:

```kotlin
implementation(project(":location-tracker"))
```

Root `apps/android-management/build.gradle.kts` already applies
`com.android.library` / Kotlin Android plugins — this module uses those.

## API surface

```kotlin
val bridge = FusedLocationGpsBridge(context)
bridge.attachActivity(activity) // required before requestLocationPermission()

// In Activity.onRequestPermissionsResult (or Activity Result API):
// if (requestCode == FusedLocationGpsBridge.REQUEST_LOCATION) bridge.onPermissionResult()

val status = bridge.getLocationPermissionStatus()
val after = bridge.requestLocationPermission() // suspends until onPermissionResult()

val once = bridge.getCurrentPosition()

val handle = bridge.watchPosition(
    onUpdate = { coord -> /* throttle ≥5s then ingest_delivery_location */ },
    onError = { msg -> /* log */ },
)
// ...
handle.stop()
```

Map to RPC args with `toDeliveryLocationIngest(jobId, coord)` then call
`ingest_delivery_location` from the management RPC layer — never from the bridge.

## Permissions & foreground service

| Permission | Why |
|------------|-----|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Foreground fixes |
| `ACCESS_BACKGROUND_LOCATION` | Continue after app backgrounds (request **after** fine; separate UX) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Android 10+ / 14 FGS type |
| `POST_NOTIFICATIONS` | Android 13+ notification for FGS |

`watchPosition` starts `DeliveryLocationTrackingService` (`foregroundServiceType=location`).
Stop via `GpsWatchHandle.stop()`.

Host Activity should forward `onRequestPermissionsResult` (or use Activity Result API)
and re-read `getLocationPermissionStatus()` after the user responds. Optional: call
`requestBackgroundLocationPermission()` from an “Allow all the time” step for
true background delivery.

## Hard rules

- Bridge-First only — no `navigator.geolocation`, no WebView GPS.
- No HTML5 / browser geolocation in this module.
- No ZIMRA / payroll tax.
