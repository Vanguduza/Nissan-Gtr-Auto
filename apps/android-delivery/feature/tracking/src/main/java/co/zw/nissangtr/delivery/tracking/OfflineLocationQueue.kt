package co.zw.nissangtr.delivery.tracking

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Durable offline queue for location pings (survives process death).
 * Flush via ingest RPC on reconnect — respect [DeliveryLocationIngestThrottle] (~5s).
 */
data class QueuedLocationPing(
    val deliveryJobId: String,
    val lat: Double,
    val lng: Double,
    val recordedAt: String?,
    val accuracyM: Double?,
)

class OfflineLocationQueue(
    context: Context,
    private val fileName: String = "offline_location_queue.json",
) {
    private val file = File(context.applicationContext.filesDir, fileName)
    private val lock = Any()

    fun enqueue(ping: QueuedLocationPing) = synchronized(lock) {
        val list = loadUnlocked().toMutableList()
        list.add(ping)
        // Cap growth — drop oldest
        while (list.size > MAX_SIZE) list.removeAt(0)
        saveUnlocked(list)
    }

    fun peek(): List<QueuedLocationPing> = synchronized(lock) { loadUnlocked() }

    fun size(): Int = synchronized(lock) { loadUnlocked().size }

    /** Remove successfully flushed prefix [count]. */
    fun dropFirst(count: Int) = synchronized(lock) {
        if (count <= 0) return
        val list = loadUnlocked().toMutableList()
        repeat(count.coerceAtMost(list.size)) { list.removeAt(0) }
        saveUnlocked(list)
    }

    fun clear() = synchronized(lock) {
        if (file.exists()) file.delete()
    }

    private fun loadUnlocked(): List<QueuedLocationPing> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        QueuedLocationPing(
                            deliveryJobId = o.getString("jobId"),
                            lat = o.getDouble("lat"),
                            lng = o.getDouble("lng"),
                            recordedAt = o.optString("recordedAt", null).takeIf { it.isNotBlank() },
                            accuracyM = if (o.has("accuracyM") && !o.isNull("accuracyM")) {
                                o.getDouble("accuracyM")
                            } else null,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveUnlocked(list: List<QueuedLocationPing>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("jobId", p.deliveryJobId)
                    put("lat", p.lat)
                    put("lng", p.lng)
                    put("recordedAt", p.recordedAt ?: JSONObject.NULL)
                    put("accuracyM", p.accuracyM ?: JSONObject.NULL)
                },
            )
        }
        file.writeText(arr.toString())
    }

    companion object {
        const val MAX_SIZE: Int = 500
    }
}
