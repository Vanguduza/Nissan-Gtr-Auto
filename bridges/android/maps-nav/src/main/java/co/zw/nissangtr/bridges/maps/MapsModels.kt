package co.zw.nissangtr.bridges.maps

/**
 * Display / route helpers for delivery navigation UI.
 * Does **not** replace Bridge-First GPS ingest (`:location-tracker`).
 * No Supabase / WebView geolocation inside this module.
 */
data class MapLatLng(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range" }
        require(longitude in -180.0..180.0) { "longitude out of range" }
    }
}

data class MapStop(
    val id: String,
    val label: String?,
    val position: MapLatLng,
    val sequence: Int? = null,
)

data class DrivingRoute(
    val points: List<MapLatLng>,
    val distanceMeters: Int? = null,
    val durationSeconds: Int? = null,
    val summary: String? = null,
)

sealed class RouteFetchResult {
    data class Ok(val route: DrivingRoute) : RouteFetchResult()
    data class Failed(val message: String) : RouteFetchResult()
}
