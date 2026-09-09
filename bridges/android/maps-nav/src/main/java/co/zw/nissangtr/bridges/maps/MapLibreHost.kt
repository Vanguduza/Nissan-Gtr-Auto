package co.zw.nissangtr.bridges.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

internal const val DEFAULT_MAP_STYLE_URL =
    "https://tiles.openfreemap.org/styles/liberty"

/**
 * Compose lifecycle wrapper around MapLibre Native.
 * No API key is required; the host apps only need INTERNET permission.
 */
@Composable
internal fun KeylessMapHost(
    modifier: Modifier = Modifier,
    styleUrl: String = DEFAULT_MAP_STYLE_URL,
    onReady: (MapLibreMap, MapView) -> Unit = { _, _ -> },
    onUpdate: (MapLibreMap, MapView) -> Unit,
) {
    val context = LocalContext.current
    val latestReady by rememberUpdatedState(onReady)
    val latestUpdate by rememberUpdatedState(onUpdate)
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapLibre.getInstance(ctx.applicationContext)
            MapView(ctx).also { view ->
                mapView = view
                view.onCreate(null)
                view.onStart()
                view.onResume()
                view.getMapAsync { readyMap ->
                    readyMap.setStyle(Style.Builder().fromUri(styleUrl)) {
                        map = readyMap
                        latestReady(readyMap, view)
                        latestUpdate(readyMap, view)
                    }
                }
            }
        },
        update = { view -> map?.let { latestUpdate(it, view) } },
    )

    DisposableEffect(mapView) {
        val view = mapView
        onDispose {
            view?.onPause()
            view?.onStop()
            view?.onDestroy()
            map = null
            mapView = null
        }
    }
}
