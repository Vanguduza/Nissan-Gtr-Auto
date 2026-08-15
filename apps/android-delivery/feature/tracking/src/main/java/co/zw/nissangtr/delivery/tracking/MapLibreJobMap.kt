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
import co.zw.nissangtr.bridges.maps.MapLatLng
import co.zw.nissangtr.bridges.maps.MapStop
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
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
 * Courier job map — MapLibre render SoR (DIAL D-44 / Epic B).
 * Pins delivery stops + optional live driver location. Display only — GPS ingest is FGS.
 *
 * Default style is public demo tiles; prefer self-host via [styleUrl]
 * (`MAPLIBRE_STYLE_URL` → infra/satellites/maptiles/).
 */
@Composable
fun MapLibreJobMap(
    latitude: Double,
    longitude: Double,
    zoom: Double = 14.0,
    modifier: Modifier = Modifier,
    styleUrl: String = DEFAULT_MAPLIBRE_STYLE_URL,
    stops: List<MapStop> = emptyList(),
    driver: MapLatLng? = null,
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
            val resolvedStyle = resolveMapLibreStyleUrl(styleUrl)
            view.getMapAsync { map ->
                map.setStyle(resolvedStyle) { style ->
                    ensureStopLayers(style)
                    updateStopPins(style, stops)
                    updateDriverPin(style, driver)
                    fitCamera(
                        map = map,
                        centerLat = latitude,
                        centerLng = longitude,
                        zoom = zoom,
                        stops = stops,
                        driver = driver,
                    )
                }
            }
        },
    )
}

/** Public demotiles — last resort when MAPLIBRE_STYLE_URL unset. */
const val DEFAULT_MAPLIBRE_STYLE_URL = "https://demotiles.maplibre.org/style.json"

fun resolveMapLibreStyleUrl(configured: String): String =
    configured.trim().ifBlank { DEFAULT_MAPLIBRE_STYLE_URL }

private const val STOPS_SOURCE = "gtr-delivery-stops"
private const val STOPS_LAYER = "gtr-delivery-stops-layer"
private const val DRIVER_SOURCE = "gtr-delivery-driver"
private const val DRIVER_LAYER = "gtr-delivery-driver-layer"

private fun ensureMapLibre(context: Context) {
    try {
        MapLibre.getInstance(context.applicationContext)
    } catch (_: Exception) {
        // Already initialized
    }
}

private fun ensureStopLayers(style: Style) {
    if (style.getSource(STOPS_SOURCE) == null) {
        style.addSource(GeoJsonSource(STOPS_SOURCE))
        style.addLayer(
            CircleLayer(STOPS_LAYER, STOPS_SOURCE).withProperties(
                circleRadius(8f),
                circleColor("#C62828"),
                circleStrokeWidth(2f),
                circleStrokeColor("#FFFFFF"),
            ),
        )
    }
    if (style.getSource(DRIVER_SOURCE) == null) {
        style.addSource(GeoJsonSource(DRIVER_SOURCE))
        style.addLayer(
            CircleLayer(DRIVER_LAYER, DRIVER_SOURCE).withProperties(
                circleRadius(9f),
                circleColor("#1565C0"),
                circleStrokeWidth(2f),
                circleStrokeColor("#FFFFFF"),
            ),
        )
    }
}

private fun updateStopPins(style: Style, stops: List<MapStop>) {
    val source = style.getSource(STOPS_SOURCE) as? GeoJsonSource ?: return
    val features = stops.map { stop ->
        Feature.fromGeometry(
            Point.fromLngLat(stop.position.longitude, stop.position.latitude),
        ).also { f ->
            stop.label?.let { f.addStringProperty("label", it) }
        }
    }
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun updateDriverPin(style: Style, driver: MapLatLng?) {
    val source = style.getSource(DRIVER_SOURCE) as? GeoJsonSource ?: return
    if (driver == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        return
    }
    source.setGeoJson(
        Feature.fromGeometry(Point.fromLngLat(driver.longitude, driver.latitude)),
    )
}

private fun fitCamera(
    map: org.maplibre.android.maps.MapLibreMap,
    centerLat: Double,
    centerLng: Double,
    zoom: Double,
    stops: List<MapStop>,
    driver: MapLatLng?,
) {
    if (stops.isEmpty() && driver == null) {
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLng), zoom),
        )
        return
    }
    val builder = LatLngBounds.Builder()
    builder.include(LatLng(centerLat, centerLng))
    stops.forEach { builder.include(LatLng(it.position.latitude, it.position.longitude)) }
    driver?.let { builder.include(LatLng(it.latitude, it.longitude)) }
    runCatching {
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 72))
    }.onFailure {
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLng), zoom),
        )
    }
}
