package co.zw.nissangtr.bridges.maps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keyless driving-route client for an OSRM-compatible endpoint.
 * Defaults to the public OSRM router and can be pointed at a self-hosted service.
 */
class DirectionsRouteFetcher(
    private val baseUrl: String = DEFAULT_ROUTING_BASE_URL,
    private val connectTimeoutMs: Int = 12_000,
    private val readTimeoutMs: Int = 12_000,
) {
    suspend fun fetchDrivingRoute(
        origin: MapLatLng,
        destination: MapLatLng,
        waypoints: List<MapLatLng> = emptyList(),
    ): RouteFetchResult = withContext(Dispatchers.IO) {
        try {
            val points = listOf(origin) + waypoints + destination
            val coordinates = points.joinToString(";") { "${it.longitude},${it.latitude}" }
            val root = baseUrl.trim().trimEnd('/')
            val url = URL(
                "$root/$coordinates?overview=full&geometries=geojson&steps=false",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NissanGTRAuto-Android/1.0")
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = try {
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } finally {
                conn.disconnect()
            }
            if (status !in 200..299) {
                return@withContext RouteFetchResult.Failed("Routing HTTP $status")
            }
            parseDirectionsJson(body)
        } catch (e: Exception) {
            RouteFetchResult.Failed(e.message ?: "Routing request failed")
        }
    }

    companion object {
        const val DEFAULT_ROUTING_BASE_URL =
            "https://router.project-osrm.org/route/v1/driving"

        /** Parse OSRM route JSON without adding a JSON dependency to this bridge. */
        fun parseDirectionsJson(body: String): RouteFetchResult {
            val code = stringField(body, "code") ?: ""
            if (code != "Ok") {
                val message = stringField(body, "message") ?: code.ifBlank { "UNKNOWN" }
                return RouteFetchResult.Failed("Routing: $message")
            }
            val routesIndex = body.indexOf("\"routes\"")
            if (routesIndex < 0) return RouteFetchResult.Failed("Routing: routes missing")
            val routeBody = body.substring(routesIndex)
            val distance = numberField(routeBody, "distance")?.toInt()
            val duration = numberField(routeBody, "duration")?.toInt()
            val geometryIndex = routeBody.indexOf("\"geometry\"")
            if (geometryIndex < 0) return RouteFetchResult.Failed("Routing: geometry missing")
            val coordinatesJson = extractArrayAfterKey(routeBody.substring(geometryIndex), "coordinates")
                ?: return RouteFetchResult.Failed("Routing: coordinates missing")
            val pairRegex = Regex(
                """\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*]""",
            )
            val points = pairRegex.findAll(coordinatesJson).mapNotNull { match ->
                val lon = match.groupValues[1].toDoubleOrNull()
                val lat = match.groupValues[2].toDoubleOrNull()
                if (lat == null || lon == null) null else runCatching { MapLatLng(lat, lon) }.getOrNull()
            }.toList()
            if (points.size < 2) return RouteFetchResult.Failed("Routing: empty geometry")

            return RouteFetchResult.Ok(
                DrivingRoute(
                    points = points,
                    distanceMeters = distance,
                    durationSeconds = duration,
                    summary = "OSRM",
                ),
            )
        }

        private fun stringField(body: String, key: String): String? =
            Regex(""""${Regex.escape(key)}"\s*:\s*"([^"]*)"""")
                .find(body)?.groupValues?.getOrNull(1)

        private fun numberField(body: String, key: String): Double? =
            Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+(?:\.\d+)?)""")
                .find(body)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        private fun extractArrayAfterKey(body: String, key: String): String? {
            val keyIndex = body.indexOf("\"$key\"")
            if (keyIndex < 0) return null
            val start = body.indexOf('[', keyIndex)
            if (start < 0) return null
            var depth = 0
            for (i in start until body.length) {
                when (body[i]) {
                    '[' -> depth += 1
                    ']' -> {
                        depth -= 1
                        if (depth == 0) return body.substring(start, i + 1)
                    }
                }
            }
            return null
        }
    }
}
