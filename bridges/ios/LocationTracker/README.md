# iOS GPS bridge — LocationTracker

Implements `GpsBridge` from `bridges/contracts/gps.ts` using **CoreLocation**.
Emits `GpsCoordinate` only — **no Supabase / network** inside this module.

> Management driver surface is Android-first this session; this package is ready
> for a future iOS management / driver app.

## Include

Swift Package Manager — add local package:

```
bridges/ios/LocationTracker
```

Product: `LocationTracker`.

```swift
import LocationTracker

let bridge = CoreLocationGpsBridge()
let status = await bridge.getLocationPermissionStatus()
_ = await bridge.requestLocationPermission()

let once = try await bridge.getCurrentPosition()

let handle = try await bridge.watchPosition(
    onUpdate: { coord in /* throttle ≥5s then ingest_delivery_location */ },
    onError: { msg in /* log */ }
)
await handle.stop()
```

Map with `toDeliveryLocationIngest(deliveryJobId:coord:)` then call RPC
`ingest_delivery_location` from the app/service layer.

## Info.plist / capabilities

| Key / capability | Why |
|------------------|-----|
| `NSLocationWhenInUseUsageDescription` | Foreground / when-in-use tracking |
| `NSLocationAlwaysAndWhenInUseUsageDescription` | Background delivery trail |
| Background Modes → **Location updates** | Continue while app suspended |
| (optional) `UIBackgroundModes` = `location` | Same as Background Modes |

Flow: request When In Use first → then `requestAlwaysAuthorization()` from an
explicit “Allow Always” UX for reliable background updates. Background location
indicator is enabled when Always is granted.

## Hard rules

- Bridge-First only — no `navigator.geolocation`, no WebView / WKWebView GPS.
- No HTML5 / browser geolocation.
- No ZIMRA / payroll tax.

## Device testing

- Simulator can inject custom location; Always / background behavior needs a
  physical device.
- Verify trail spacing with client-side ≥~5s throttle against server rate limit.
