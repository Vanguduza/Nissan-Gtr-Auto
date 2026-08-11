package co.zw.nissangtr.bridges.maps

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a driving route via self-hosted **OSRM** (Dial-a-Spare / DIAL D-44 SoR).
 * Prefer this over [DirectionsRouteFetcher] (Google) when `OSRM_URL` is configured.
 *
 * Example base: `http://10.0.2.2:5000` (emulator → host) or LAN OSRM.
 * GET /route/v1/driving/{lon},{lat};...?...&overview=full&geometries=geojson
 */
class OsrmRouteFetcher(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 12_000,
    private val readTimeoutMs: Int = 12_000,
) {
    suspend fun fetchDrivingRoute(
        origin: MapLatLng,
        destination: MapLatLng,
        waypoints: List<MapLatLng> = emptyList(),
    ): RouteFetchResult = withContext(Dispatchers.IO) {
        val root = baseUrl.trim().trimEnd('/')
        if (root.isBlank()) {
            return@withContext RouteFetchResult.Failed(
                "OSRM_URL missing — set in local.properties",
            )
        }
        try {
            val coords = buildList {
                add(origin)
                addAll(waypoints)
                add(destination)
            }.joinToString(";") { "${it.longitude},${it.latitude}" }
            val url = URL(
                "$root/route/v1/driving/$coords?overview=full&geometries=geojson",
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
            parseOsrmJson(body)
        } catch (e: Exception) {
            RouteFetchResult.Failed(e.message ?: "OSRM request failed")
        }
    }

    companion object {
        fun parseOsrmJson(body: String): RouteFetchResult {
            val code = jsonStringField(body, "code") ?: ""
            if (!code.equals("Ok", ignoreCase = true)) {
                return RouteFetchResult.Failed("OSRM: ${code.ifBlank { "unknown" }}")
            }
            val coordsBlock = regexGroup(
                """"coordinates"\s*:\s*\[(.*?)]\s*}""".toRegex(RegexOption.DOT_MATCHES_ALL),
                body,
            ) ?: return RouteFetchResult.Failed("OSRM: empty geometry")

            val points = mutableListOf<MapLatLng>()
            """\[\s*([-0-9.]+)\s*,\s*([-0-9.]+)\s*]""".toRegex()
                .findAll(coordsBlock)
                .forEach { m ->
                    val lng = m.groupValues[1].toDoubleOrNull() ?: return@forEach
                    val lat = m.groupValues[2].toDoubleOrNull() ?: return@forEach
                    points.add(MapLatLng(lat, lng))
                }
            if (points.isEmpty()) {
                return RouteFetchResult.Failed("OSRM: empty coordinates")
            }

            val distance = regexGroup(
                """"distance"\s*:\s*([-0-9.]+)""".toRegex(),
                body,
            )?.toDoubleOrNull()?.let { Math.round(it).toInt() }
            val duration = regexGroup(
                """"duration"\s*:\s*([-0-9.]+)""".toRegex(),
                body,
            )?.toDoubleOrNull()?.let { Math.round(it).toInt() }

            return RouteFetchResult.Ok(
                DrivingRoute(
                    points = points,
                    distanceMeters = distance?.takeIf { it > 0 },
                    durationSeconds = duration?.takeIf { it > 0 },
                    summary = "OSRM",
                ),
            )
        }

        private fun jsonStringField(body: String, key: String): String? =
            regexGroup(""""$key"\s*:\s*"([^"]*)"""".toRegex(), body)

        private fun regexGroup(re: Regex, body: String): String? =
            re.find(body)?.groupValues?.getOrNull(1)
    }
}
