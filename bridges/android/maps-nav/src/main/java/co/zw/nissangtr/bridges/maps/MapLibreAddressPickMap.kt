package co.zw.nissangtr.bridges.maps

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Customer address map pick — MapLibre render SoR (B-MAP-1 / DIAL D-44).
 * Tap to set pin. Display only; no GPS ingest (use `:location-tracker` if needed).
 *
 * Default center: Harare CBD when no selection yet.
 * Default style is public demo tiles; ops should set a self-hosted style via [styleUrl].
 */
@Composable
fun MapLibreAddressPickMap(
    selected: MapLatLng?,
    onPick: (MapLatLng) -> Unit,
    modifier: Modifier = Modifier,
    defaultCenter: MapLatLng = MapLatLng(-17.8292, 31.0522),
    styleUrl: String = DEFAULT_MAPLIBRE_STYLE_URL,
    onInitFailed: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onPickLatest = rememberUpdatedState(onPick)
    val selectedLatest = rememberUpdatedState(selected)
    val onInitFailedLatest = rememberUpdatedState(onInitFailed)

    val mapView = remember {
        try {
            ensureMapLibre(context)
            MapView(context).apply {
                onCreate(null)
                getMapAsync { map ->
                    map.setStyle(styleUrl) { style ->
                        ensurePickPinLayer(style)
                        updatePickPin(style, selectedLatest.value)
                        val center = selectedLatest.value ?: defaultCenter
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(center.latitude, center.longitude),
                                14.0,
                            ),
                        )
                        map.addOnMapClickListener { latLng ->
                            onPickLatest.value(MapLatLng(latLng.latitude, latLng.longitude))
                            true
                        }
                    }
                }
            }
        } catch (_: Exception) {
            onInitFailedLatest.value?.invoke()
            null
        }
    }

    if (mapView == null) {
        return
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
        modifier = modifier.fillMaxSize(),
        update = { view ->
            view.getMapAsync { map ->
                val style = map.style ?: return@getMapAsync
                updatePickPin(style, selected)
                val center = selected ?: defaultCenter
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(center.latitude, center.longitude),
                        map.cameraPosition.zoom.takeIf { it > 0 } ?: 14.0,
                    ),
                )
            }
        },
    )
}

internal const val DEFAULT_MAPLIBRE_STYLE_URL = "https://demotiles.maplibre.org/style.json"

private const val PIN_SOURCE = "gtr-address-pick-pin"
private const val PIN_LAYER = "gtr-address-pick-pin-layer"

internal fun ensureMapLibre(context: Context) {
    try {
        MapLibre.getInstance(context.applicationContext)
    } catch (_: Exception) {
        // Already initialized
    }
}

private fun ensurePickPinLayer(style: Style) {
    if (style.getSource(PIN_SOURCE) != null) return
    style.addSource(GeoJsonSource(PIN_SOURCE))
    style.addLayer(
        CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
            circleRadius(9f),
            circleColor("#C62828"),
            circleStrokeWidth(2f),
            circleStrokeColor("#FFFFFF"),
        ),
    )
}

private fun updatePickPin(style: Style, selected: MapLatLng?) {
    val source = style.getSource(PIN_SOURCE) as? GeoJsonSource ?: return
    if (selected == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        return
    }
    source.setGeoJson(
        Feature.fromGeometry(
            Point.fromLngLat(selected.longitude, selected.latitude),
        ),
    )
}
