package co.zw.nissangtr.bridges.maps

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

/**
 * Keyless customer address picker backed by MapLibre Native + OpenFreeMap/OSM.
 * Tap the map to set the delivery coordinate. No proprietary Maps API key is required.
 */
@Composable
fun AddressPickMap(
    selected: MapLatLng?,
    onPick: (MapLatLng) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    defaultCenter: MapLatLng = MapLatLng(-17.8292, 31.0522),
) {
    val latestOnPick by rememberUpdatedState(onPick)
    var renderedFingerprint by remember { mutableStateOf<String?>(null) }

    KeylessMapHost(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        onReady = { map, _ ->
            map.uiSettings.setCompassEnabled(false)
            map.uiSettings.setRotateGesturesEnabled(false)
            map.addOnMapClickListener { point ->
                latestOnPick(MapLatLng(point.latitude, point.longitude))
                true
            }
        },
        onUpdate = { map, _ ->
            val fingerprint = selected?.let { "${it.latitude},${it.longitude}" } ?: "unset"
            if (fingerprint != renderedFingerprint) {
                renderedFingerprint = fingerprint
                map.clear()
                val center = selected ?: defaultCenter
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(center.latitude, center.longitude))
                    .zoom(if (selected == null) 12.0 else 14.0)
                    .build()
                selected?.let { point ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(point.latitude, point.longitude))
                            .title("Delivery point"),
                    )
                }
            }
        },
    )
}
