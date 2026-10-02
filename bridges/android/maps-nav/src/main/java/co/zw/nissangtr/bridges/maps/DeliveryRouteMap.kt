package co.zw.nissangtr.bridges.maps

import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

/**
 * Keyless in-app delivery map backed by MapLibre Native + OpenFreeMap/OSM.
 * GPS ingest remains in the location-tracker bridge; this surface is display-only.
 */
@Composable
fun DeliveryRouteMap(
    destination: MapLatLng?,
    driver: MapLatLng?,
    routePoints: List<MapLatLng>,
    otherStops: List<MapStop> = emptyList(),
    modifier: Modifier = Modifier,
    /** Fixed height; `null` lets [modifier] size the map (e.g. full-bleed behind a sheet). */
    height: Dp? = 220.dp,
    @Suppress("UNUSED_PARAMETER") myLocationEnabled: Boolean = false,
    /** Blank → keyless default style. See [KeylessMapStyles]. */
    styleUrl: String = DEFAULT_MAP_STYLE_URL,
    routeColor: Int = Color.rgb(200, 16, 46),
    /** Extra camera padding in px (left, top, right, bottom), e.g. for a sheet covering the bottom. */
    cameraPadding: IntArray = intArrayOf(64, 64, 64, 64),
) {
    val sized = if (height != null) modifier.fillMaxWidth().height(height) else modifier
    if (destination == null) {
        Box(
            modifier = sized,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Dropoff coordinates missing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var renderedFingerprint by remember { mutableStateOf<String?>(null) }
    val fingerprint = buildString {
        append(destination.latitude).append(',').append(destination.longitude).append('|')
        driver?.let { append(it.latitude).append(',').append(it.longitude) }
        append('|').append(routePoints.hashCode()).append('|').append(otherStops.hashCode())
        append('|').append(routeColor).append('|').append(cameraPadding.contentHashCode())
    }

    KeylessMapHost(
        modifier = sized,
        styleUrl = styleUrl.ifBlank { DEFAULT_MAP_STYLE_URL },
        onReady = { map, _ ->
            map.uiSettings.setCompassEnabled(true)
            map.uiSettings.setRotateGesturesEnabled(false)
        },
        onUpdate = { map, _ ->
            if (fingerprint == renderedFingerprint) return@KeylessMapHost
            renderedFingerprint = fingerprint
            map.clear()

            val allPoints = mutableListOf<MapLatLng>()
            allPoints += destination
            driver?.let { allPoints += it }
            allPoints += routePoints
            allPoints += otherStops.map { it.position }

            map.addMarker(
                MarkerOptions()
                    .position(LatLng(destination.latitude, destination.longitude))
                    .title("Dropoff"),
            )
            driver?.let { point ->
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(point.latitude, point.longitude))
                        .title("Driver"),
                )
            }
            otherStops.forEach { stop ->
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(stop.position.latitude, stop.position.longitude))
                        .title(stop.label ?: "Stop ${stop.sequence ?: ""}".trim()),
                )
            }
            if (routePoints.size >= 2) {
                map.addPolyline(
                    PolylineOptions()
                        .addAll(routePoints.map { LatLng(it.latitude, it.longitude) })
                        .color(routeColor)
                        .width(7f),
                )
            }

            if (allPoints.size == 1) {
                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(destination.latitude, destination.longitude))
                    .zoom(14.0)
                    .build()
            } else {
                val bounds = LatLngBounds.Builder()
                allPoints.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
                map.getCameraForLatLngBounds(bounds.build(), cameraPadding)?.let {
                    map.cameraPosition = it
                }
            }
        },
    )
}
