# Android maps/navigation helper — maps-nav

In-app **MapLibre Native** maps using the keyless OpenFreeMap style, plus an
**OSRM-compatible** route fetcher for driver guidance. Display/navigation only:
GPS ingest remains Bridge-First through `:location-tracker`.

## Locked architecture

- Map renderer: MapLibre Native.
- Default style: `https://tiles.openfreemap.org/styles/liberty`.
- Routing: OSRM-compatible `/route/v1/driving` endpoint.
- External fallback: platform `geo:` intent; no hard dependency on Google Maps.
- No Google Maps SDK, Directions API, API key, or WebView geolocation.

## Include from Android apps

```kotlin
include(":maps-nav")
project(":maps-nav").projectDir = file("../../bridges/android/maps-nav")
implementation(project(":maps-nav"))
```

## API surface

```kotlin
val fetcher = DirectionsRouteFetcher(routingBaseUrl)
when (val r = fetcher.fetchDrivingRoute(origin, destination, waypoints)) {
    is RouteFetchResult.Ok -> r.route.points
    is RouteFetchResult.Failed -> /* show message */
}

DeliveryRouteMap(
    destination = MapLatLng(lat, lng),
    driver = MapLatLng(driverLat, driverLng),
    routePoints = points,
    otherStops = stops,
)

ExternalNavigation.openTurnByTurn(context, destination)
```

## Hard rules

- Bridge-First: no WebView GPS; do not call `ingest_delivery_location` from this module.
- Keep maps keyless/open-source-compatible; do not reintroduce Google Maps credentials.
- A custom routing endpoint is configuration, not a client secret.
- No ZIMRA / fiscal payloads.
