# Android GPS bridge — location-tracker

Implements `GpsBridge` from `bridges/contracts/gps.ts` using
**FusedLocationProviderClient**. Emits `GpsCoordinate` only — **no Supabase /
network** inside this module.

Designed for **`apps/android-delivery/`** continuous tracking (also usable from
management until that app is gated).

## Include from android-delivery (or management)

In `apps/android-delivery/settings.gradle.kts` (scaffold lane):

```kotlin
include(":location-tracker")
project(":location-tracker").projectDir =
    file("../../bridges/android/location-tracker")
```

```kotlin
implementation(project(":location-tracker"))
```

## API surface — start / stop / watch

```kotlin
val buffer = GpsPingBuffer(capacity = 64) // ephemeral only — see Offline below
val bridge = FusedLocationGpsBridge(context, pingBuffer = buffer)
bridge.attachActivity(activity) // required before requestLocationPermission()

// Optional: notification tap → job screen
bridge.notificationContentIntent = PendingIntent.getActivity(...)

// In Activity.onRequestPermissionsResult (or Activity Result API):
// if (requestCode == FusedLocationGpsBridge.REQUEST_LOCATION) bridge.onPermissionResult()

val status = bridge.getLocationPermissionStatus()
val after = bridge.requestLocationPermission()
// Later UX step (Play policy): bridge.requestBackgroundLocationPermission()

val once = bridge.getCurrentPosition()

val handle = bridge.watchPosition(
    onUpdate = { coord ->
        // App: persist offline if needed, then throttle ≥5s and call
        // ingest_delivery_location via packages/supabase-client helpers
        // (toDeliveryLocationIngest → RPC). Never from this bridge.
    },
    onError = { msg -> /* log */ },
    options = GpsWatchOptions(cadence = GpsWatchCadence.AUTO),
)
// When job completes / driver goes offline:
handle.stop() // removes Fused updates + stops FGS
```

Map to RPC args with `toDeliveryLocationIngest(jobId, coord)` then call
`ingest_delivery_location` from the **app** RPC layer — never from the bridge.

## Battery cadence

| Cadence | Priority | Interval | Min distance | When |
|---------|----------|----------|--------------|------|
| `MOVING` | HIGH_ACCURACY | ~5s | 0 m | En route |
| `IDLE` | BALANCED_POWER | ~30s | 25 m | Parked / slow |
| `AUTO` (default) | switches | — | — | Speed ≥ 1 m/s → MOVING, else IDLE |

Server still enforces ~5s rate limit per job; app should not ingest faster than that.

## Foreground service

`watchPosition` starts `DeliveryLocationTrackingService` (`foregroundServiceType=location`)
with a **persistent, silent, low-importance** notification. Stop via
`GpsWatchHandle.stop()`.

| Permission | Why |
|------------|-----|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Foreground fixes |
| `ACCESS_BACKGROUND_LOCATION` | Continue after app backgrounds (request **after** fine; separate UX) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Android 10+ / 14 FGS type |
| `POST_NOTIFICATIONS` | Android 13+ notification for FGS |

## Offline queue (app vs bridge)

| Layer | Responsibility |
|-------|----------------|
| **Bridge** | Optional `GpsPingBuffer` — **in-memory ring only** (lost on process death) |
| **App** | Durable offline queue (Room/SQLite) + flush on reconnect, respecting ~5s ingest limit |

Do not put Supabase or WorkManager inside this module.

## Hard rules

- Bridge-First only — no `navigator.geolocation`, no WebView GPS.
- No HTML5 / browser geolocation in this module.
- No ZIMRA / payroll tax.
