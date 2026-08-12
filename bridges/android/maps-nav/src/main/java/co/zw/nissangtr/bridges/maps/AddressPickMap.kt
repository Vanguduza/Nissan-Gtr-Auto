package co.zw.nissangtr.bridges.maps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Customer address map pick — Bridge-First maps-nav (B-MAP-1).
 *
 * **MapLibre** is the render SoR (default). Google Maps Compose is a **deprecated
 * fallback** only when [useMapLibre] is false or MapLibre fails to initialize and a
 * Google key is present. Distance/ETA prefer OSRM via [OsrmRouteFetcher] when configured
 * (delivery lane); this surface is pin-pick display only.
 *
 * Tap map to set pin. Default center: Harare CBD when no selection yet.
 */
@Composable
fun AddressPickMap(
    selected: MapLatLng?,
    onPick: (MapLatLng) -> Unit,
    mapsKeyPresent: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    defaultCenter: MapLatLng = MapLatLng(-17.8292, 31.0522),
    /** MapLibre SoR (B-MAP-1). Set false only for deprecated Google Maps tiles. */
    useMapLibre: Boolean = true,
    styleUrl: String = DEFAULT_MAPLIBRE_STYLE_URL,
) {
    var mapLibreFailed by remember { mutableStateOf(false) }
    val showMapLibre = useMapLibre && !mapLibreFailed
    val showGoogleFallback = !showMapLibre && mapsKeyPresent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        when {
            showMapLibre -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        runCatching {
                            MapLibreAddressPickMap(
                                selected = selected,
                                onPick = onPick,
                                modifier = Modifier.fillMaxSize(),
                                defaultCenter = defaultCenter,
                                styleUrl = styleUrl,
                            )
                        }.onFailure {
                            mapLibreFailed = true
                        }
                    }
                    Text(
                        addressPickMapCaption(showingMapLibre = true, mapsKeyPresent = mapsKeyPresent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            showGoogleFallback -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        GoogleAddressPickMap(
                            selected = selected,
                            onPick = onPick,
                            defaultCenter = defaultCenter,
                        )
                    }
                    Text(
                        addressPickMapCaption(showingMapLibre = false, mapsKeyPresent = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            else -> {
                Text(
                    "Map unavailable — MapLibre SoR failed and no GOOGLE_MAPS_API_KEY " +
                        "for deprecated Google fallback. Enter lat/lng manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/** Caption for address-pick SoR honesty (unit-tested). */
fun addressPickMapCaption(showingMapLibre: Boolean, mapsKeyPresent: Boolean): String = when {
    showingMapLibre -> "MapLibre SoR"
    mapsKeyPresent -> "DEPRECATED Google Maps fallback"
    else -> "Map unavailable"
}

@Composable
private fun GoogleAddressPickMap(
    selected: MapLatLng?,
    onPick: (MapLatLng) -> Unit,
    defaultCenter: MapLatLng,
) {
    val center = selected ?: defaultCenter
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(center.latitude, center.longitude),
            14f,
        )
    }
    val markerState = remember(selected) {
        MarkerState(
            position = LatLng(
                (selected ?: defaultCenter).latitude,
                (selected ?: defaultCenter).longitude,
            ),
        )
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = true,
            compassEnabled = false,
            mapToolbarEnabled = false,
        ),
        onMapClick = { latLng ->
            onPick(MapLatLng(latLng.latitude, latLng.longitude))
        },
    ) {
        if (selected != null) {
            Marker(
                state = markerState,
                title = "Delivery point",
            )
        }
    }
}
