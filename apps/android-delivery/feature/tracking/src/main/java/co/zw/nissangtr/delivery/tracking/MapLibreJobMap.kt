package co.zw.nissangtr.delivery.tracking

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

/**
 * Courier job map — MapLibre render SoR (DIAL D-44).
 * Distance/ETA remain OSRM (OsrmRouteFetcher). Never Google Maps as SoR.
 *
 * Default style is public demo tiles; ops should set a self-hosted/style URL via [styleUrl].
 */
@Composable
fun MapLibreJobMap(
    latitude: Double,
    longitude: Double,
    zoom: Double = 14.0,
    modifier: Modifier = Modifier,
    styleUrl: String = "https://demotiles.maplibre.org/style.json",
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        ensureMapLibre(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxSize()
            .heightIn(min = 220.dp),
        update = { view ->
            view.getMapAsync { map ->
                map.setStyle(styleUrl) {
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(latitude, longitude),
                            zoom,
                        ),
                    )
                }
            }
        },
    )
}

private fun ensureMapLibre(context: Context) {
    try {
        MapLibre.getInstance(context.applicationContext)
    } catch (_: Exception) {
        // Already initialized
    }
}
