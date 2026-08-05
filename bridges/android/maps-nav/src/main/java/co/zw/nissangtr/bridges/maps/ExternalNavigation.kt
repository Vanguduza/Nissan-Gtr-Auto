package co.zw.nissangtr.bridges.maps

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens Google Maps turn-by-turn (external app) as a fallback / deep guidance.
 * Display route remains in-app via [DeliveryRouteMap]; GPS ingest stays FGS.
 */
object ExternalNavigation {
    fun openTurnByTurn(context: Context, destination: MapLatLng): Boolean {
        val lat = destination.latitude
        val lng = destination.longitude
        val nav = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$lat,$lng"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.google.android.apps.maps")
        }
        return runCatching {
            context.startActivity(nav)
            true
        }.recoverCatching {
            val geo = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:$lat,$lng?q=$lat,$lng"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(geo)
            true
        }.getOrDefault(false)
    }
}
