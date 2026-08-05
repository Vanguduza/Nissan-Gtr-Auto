package co.zw.nissangtr.bridges.maps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Fetches a driving route via Google Directions API (HTTP JSON).
 * Requires a Maps/Directions-enabled API key from the host app — never hardcode secrets.
 *
 * Adopt-first: Directions REST + Maps Compose instead of Navigation SDK
 * (Navigation SDK needs a Google enterprise agreement).
 *
 * JSON parsing avoids Android [org.json.JSONObject] so unit tests run on JVM without mocks.
 */
class DirectionsRouteFetcher(
    private val apiKey: String,
    private val connectTimeoutMs: Int = 12_000,
    private val readTimeoutMs: Int = 12_000,
) {
    suspend fun fetchDrivingRoute(
        origin: MapLatLng,
        destination: MapLatLng,
        waypoints: List<MapLatLng> = emptyList(),
    ): RouteFetchResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext RouteFetchResult.Failed(
                "GOOGLE_MAPS_API_KEY missing — set in local.properties",
            )
        }
        try {
            val originStr = "${origin.latitude},${origin.longitude}"
            val destStr = "${destination.latitude},${destination.longitude}"
            val wp = if (waypoints.isEmpty()) {
                ""
            } else {
                "&waypoints=" + URLEncoder.encode(
                    waypoints.joinToString("|") { "${it.latitude},${it.longitude}" },
                    StandardCharsets.UTF_8.name(),
                )
            }
            val url = URL(
                "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${URLEncoder.encode(originStr, StandardCharsets.UTF_8.name())}" +
                    "&destination=${URLEncoder.encode(destStr, StandardCharsets.UTF_8.name())}" +
                    wp +
                    "&mode=driving" +
                    "&key=${URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())}",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
            }
            val body = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            parseDirectionsJson(body)
        } catch (e: Exception) {
            RouteFetchResult.Failed(e.message ?: "Directions request failed")
        }
    }

    companion object {
        fun parseDirectionsJson(body: String): RouteFetchResult {
            val status = jsonStringField(body, "status") ?: ""
            if (status != "OK") {
                val err = jsonStringField(body, "error_message")?.takeIf { it.isNotBlank() }
                    ?: status.ifBlank { "UNKNOWN" }
                return RouteFetchResult.Failed("Directions: $err")
            }
            val encoded = regexGroup(
                """"overview_polyline"\s*:\s*\{[^}]*"points"\s*:\s*"([^"]+)"""".toRegex(),
                body,
            )
            if (encoded.isNullOrBlank()) {
                return RouteFetchResult.Failed("Directions: empty polyline")
            }
            val points = PolylineDecoder.decode(encoded)
            if (points.isEmpty()) {
                return RouteFetchResult.Failed("Directions: empty polyline")
            }
            var distance = 0
            var duration = 0
            """"distance"\s*:\s*\{[^}]*"value"\s*:\s*(\d+)""".toRegex()
                .findAll(body)
                .forEach { distance += it.groupValues[1].toIntOrNull() ?: 0 }
            """"duration"\s*:\s*\{[^}]*"value"\s*:\s*(\d+)""".toRegex()
                .findAll(body)
                .forEach { duration += it.groupValues[1].toIntOrNull() ?: 0 }
            val summary = regexGroup(""""summary"\s*:\s*"([^"]*)"""".toRegex(), body)
            return RouteFetchResult.Ok(
                DrivingRoute(
                    points = points,
                    distanceMeters = distance.takeIf { it > 0 },
                    durationSeconds = duration.takeIf { it > 0 },
                    summary = summary?.ifBlank { null },
                ),
            )
        }

        private fun jsonStringField(body: String, key: String): String? =
            regexGroup(""""$key"\s*:\s*"([^"]*)"""".toRegex(), body)

        private fun regexGroup(re: Regex, body: String): String? =
            re.find(body)?.groupValues?.getOrNull(1)
    }
}
