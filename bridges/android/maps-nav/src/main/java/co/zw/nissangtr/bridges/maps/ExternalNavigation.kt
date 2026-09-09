package co.zw.nissangtr.bridges.maps

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the user's installed navigation provider through the platform geo intent.
 * No Google package or proprietary Maps SDK is required.
 */
object ExternalNavigation {
    fun openTurnByTurn(context: Context, destination: MapLatLng): Boolean {
        val lat = destination.latitude
        val lng = destination.longitude
        val geo = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:$lat,$lng?q=$lat,$lng"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(geo)
            true
        }.getOrDefault(false)
    }
}
