package co.zw.nissangtr.bridges.maps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * In-app Google Map showing driver → destination route polyline and stop markers.
 * **DEPRECATED** as courier map SoR — prefer `MapLibreJobMap` (android-delivery tracking).
 * Keep for explicit `useMapLibre=false` / missing-coords fallback only (B-MAP-1).
 * Display only — does not perform GPS ingest (use `:location-tracker` FGS for that).
 *
 * When [mapsKeyPresent] is false, shows a placeholder (no Maps SDK tile load).
 */
@Composable
fun DeliveryRouteMap(
    destination: MapLatLng?,
    driver: MapLatLng?,
    routePoints: List<MapLatLng>,
    otherStops: List<MapStop> = emptyList(),
    mapsKeyPresent: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    myLocationEnabled: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        if (!mapsKeyPresent || destination == null) {
            Text(
                when {
                    !mapsKeyPresent ->
                        "Set GOOGLE_MAPS_API_KEY in local.properties for live map"
                    else -> "Dropoff coordinates missing"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        val destLatLng = remember(destination) {
            LatLng(destination.latitude, destination.longitude)
        }
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(destLatLng, 14f)
        }

        LaunchedEffect(destination, driver, routePoints, otherStops) {
            val builder = LatLngBounds.builder()
            var any = false
            fun include(p: MapLatLng) {
                builder.include(LatLng(p.latitude, p.longitude))
                any = true
            }
            include(destination)
            driver?.let { include(it) }
            routePoints.forEach { include(it) }
            otherStops.forEach { include(it.position) }
            if (any) {
                runCatching {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngBounds(builder.build(), 80),
                    )
                }
            }
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = myLocationEnabled),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                compassEnabled = true,
                myLocationButtonEnabled = myLocationEnabled,
                mapToolbarEnabled = false,
            ),
        ) {
            Marker(
                state = MarkerState(position = destLatLng),
                title = "Dropoff",
            )
            driver?.let { d ->
                Marker(
                    state = MarkerState(position = LatLng(d.latitude, d.longitude)),
                    title = "You",
                )
            }
            otherStops.forEach { stop ->
                Marker(
                    state = MarkerState(
                        position = LatLng(stop.position.latitude, stop.position.longitude),
                    ),
                    title = stop.label ?: "Stop ${stop.sequence ?: ""}".trim(),
                    snippet = stop.id.take(8),
                )
            }
            if (routePoints.size >= 2) {
                Polyline(
                    points = routePoints.map { LatLng(it.latitude, it.longitude) },
                    color = Color(0xFFC8102E),
                    width = 10f,
                )
            }
        }
    }
}
