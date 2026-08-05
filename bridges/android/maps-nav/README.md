# Android delivery maps helper — maps-nav

In-app **Google Maps Compose** + **Directions API** route polyline for driver
guidance to delivery job dropoffs. **Display only** — GPS ingest stays Bridge-First
via `:location-tracker` FGS (`ingest_delivery_location`). No WebView geolocation.

## Adopt-first note

| Option | Decision |
|--------|----------|
| Maps SDK + maps-compose (Apache-2.0) + Directions REST | **Integrate** — free-tier Maps/Directions keys |
| Navigation SDK | **Defer** — requires Google Navigation SDK partnership / enterprise |

## Include from android-delivery

```kotlin
include(":maps-nav")
project(":maps-nav").projectDir =
    file("../../bridges/android/maps-nav")
```

```kotlin
implementation(project(":maps-nav"))
```

Host app must put the API key in **`local.properties`** (gitignored):

```properties
GOOGLE_MAPS_API_KEY=your-maps-key
```

Wire into the application manifest:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${GOOGLE_MAPS_API_KEY}" />
```

Enable **Maps SDK for Android** and **Directions API** for that key in Google Cloud Console.
Restrict by package `co.zw.nissangtr.delivery` + SHA-1 when shipping.

## API surface

```kotlin
// Fetch route (IO)
val fetcher = DirectionsRouteFetcher(apiKey)
when (val r = fetcher.fetchDrivingRoute(origin, destination, waypoints)) {
    is RouteFetchResult.Ok -> r.route.points
    is RouteFetchResult.Failed -> /* show message */
}

// Compose map (display)
DeliveryRouteMap(
    destination = MapLatLng(lat, lng),
    driver = MapLatLng(driverLat, driverLng),
    routePoints = points,
    otherStops = stops,
    mapsKeyPresent = apiKey.isNotBlank(),
)

// External turn-by-turn fallback
ExternalNavigation.openTurnByTurn(context, destination)
```

## Hard rules

- Bridge-First: no WebView GPS; do not call `ingest_delivery_location` from this module.
- Never commit API keys — `local.properties` / CI secrets only.
- No ZIMRA / fiscal payloads.
