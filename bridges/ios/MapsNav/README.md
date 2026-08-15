# iOS maps-nav bridge — MapsNav

Bridge-First map **display** helpers for the GTR Customer iOS app (B-MAP-1 / H5-iOS).
**No GPS ingest** here — that stays in `bridges/ios/LocationTracker` (CoreLocation).

## Render SoR (B-MAP-1)

| Surface | SoR | Deprecated fallback |
|---------|-----|---------------------|
| Customer address pick | **MapLibre** (`MapLibreAddressPickMap` via `AddressPickMap`) | MapKit when `USE_MAPLIBRE=false` or MapLibre style load fails |
| Live delivery last-point | **MapLibre** (`MapLibreTrackMap` via `TrackPointMap`) | MapKit single annotation |

Google tiles are **not** used on iOS. No Fleetbase. No WebView / HTML5 maps.

## Adopt-first note

| Option | Decision |
|--------|----------|
| MapLibre Native (BSD) via SPM `maplibre-gl-native-distribution` | **Integrate** — render SoR |
| MapKit | **Deprecated fallback** only |
| HTML5 / WKWebView map | **Never** |
| Fleetbase | **Never** |

## Include

Swift Package Manager — local package:

```
bridges/ios/MapsNav
```

Product: `MapsNav` (depends on remote `MapLibre` binary XCFramework).

Xcode app target (`apps/ios/GTRCustomer.xcodeproj`) links this local package.

```swift
import MapsNav

AddressPickMap(
    latitude: $lat,
    longitude: $lng,
    useMapLibre: resolveUseMapLibre()
)

TrackPointMap(
    point: MapLatLng(latitude: lat, longitude: lng),
    useMapLibre: resolveUseMapLibre()
)
```

### Env / Info.plist

| Variable | Purpose |
|----------|---------|
| `USE_MAPLIBRE` | Default on. Set `false` / `0` / `no` / `off` only for deprecated MapKit |
| `MAPLIBRE_STYLE_URL` | Optional self-hosted style (app reads via `AppEnv`; falls back to demotiles). Prefer `infra/satellites/maptiles/` → `http://127.0.0.1:8081/styles/basic-preview/style.json` |

## Hard rules

- Bridge-First: map **render** SDK OK; device GPS still via `LocationTracker` — never `navigator.geolocation` / WKWebView GPS.
- No ZIMRA / fiscal payloads.
- No Fleetbase.

## Verify (macOS + Xcode — not available on Windows coding hosts)

```bash
# Package unit tests (caption / USE_MAPLIBRE resolve)
cd bridges/ios/MapsNav
xcodebuild -scheme MapsNav -destination 'platform=iOS Simulator,name=iPhone 16' test

# Customer app build (resolves MapLibre SPM + local MapsNav)
cd apps/ios
xcodebuild -scheme GTRCustomer \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -project GTRCustomer.xcodeproj \
  build
```

Windows: implement/edit sources only; treat the commands above as the Mac verification gate.
