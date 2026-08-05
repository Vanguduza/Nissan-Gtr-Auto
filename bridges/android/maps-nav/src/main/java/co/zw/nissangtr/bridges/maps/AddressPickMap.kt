package co.zw.nissangtr.bridges.maps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * Customer address map pick — Shopping-By-KMP MapComponent pattern via Bridge-First maps-nav.
 * Tap map to set pin. Display only; no GPS ingest (use `:location-tracker` if needed).
 *
 * Default center: Harare CBD when no selection yet.
 */
@Composable
fun AddressPickMap(
    selected: MapLatLng?,
    onPick: (MapLatLng) -> Unit,
    mapsKeyPresent: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    defaultCenter: MapLatLng = MapLatLng(-17.8292, 31.0522),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        if (!mapsKeyPresent) {
            Text(
                "Set GOOGLE_MAPS_API_KEY in local.properties for live map pick",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

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
}
