package co.zw.nissangtr.delivery.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.bridges.maps.DeliveryRouteMap
import co.zw.nissangtr.bridges.maps.KeylessMapStyles
import co.zw.nissangtr.bridges.maps.MapLatLng
import co.zw.nissangtr.bridges.maps.MapStop
import co.zw.nissangtr.delivery.design.Slopes

/** What a driver map shows; rendering is swappable so screenshot tests need no native map. */
data class DeliveryMapSpec(
    val destination: MapLatLng?,
    val driver: MapLatLng?,
    val routePoints: List<MapLatLng> = emptyList(),
    val otherStops: List<MapStop> = emptyList(),
    /** Optional self-hosted style; blank → keyless light/dark style matching the theme. */
    val styleUrl: String = "",
    /** Height in px covered by the resting sheet, so pins stay in the visible part. */
    val obscuredBottomPx: Int = 0,
)

val LocalDeliveryMap = staticCompositionLocalOf<@Composable (DeliveryMapSpec, Modifier) -> Unit> {
    { spec, modifier -> BridgeDeliveryMap(spec, modifier) }
}

@Composable
fun DeliveryMap(spec: DeliveryMapSpec, modifier: Modifier = Modifier) {
    LocalDeliveryMap.current(spec, modifier)
}

/** Default renderer: the keyless MapLibre bridge (Bridge-First, display only). */
@Composable
private fun BridgeDeliveryMap(spec: DeliveryMapSpec, modifier: Modifier) {
    val c = Slopes.colors
    if (spec.destination == null) {
        Box(modifier.fillMaxSize().background(c.fill), contentAlignment = Alignment.Center) {
            Text(
                "No stops on the map yet",
                style = MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
                modifier = Modifier.padding(bottom = 200.dp),
            )
        }
        return
    }
    val style = spec.styleUrl.ifBlank { if (c.isDark) KeylessMapStyles.DARK else KeylessMapStyles.LIGHT }
    // A style change needs a fresh MapView; key on it so light/dark switches apply at once.
    key(style) {
        DeliveryRouteMap(
            destination = spec.destination,
            driver = spec.driver,
            routePoints = spec.routePoints,
            otherStops = spec.otherStops,
            height = null,
            modifier = modifier.fillMaxSize(),
            styleUrl = style,
            routeColor = c.route.toArgb(),
            cameraPadding = intArrayOf(90, 140, 90, spec.obscuredBottomPx + 70),
        )
    }
}
