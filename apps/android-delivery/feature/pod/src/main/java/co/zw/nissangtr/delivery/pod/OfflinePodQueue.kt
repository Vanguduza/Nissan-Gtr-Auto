package co.zw.nissangtr.delivery.pod

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Durable offline queue for POD submit payloads (photo/signature paths + OTP).
 * Flush on reconnect after Storage upload + submit_delivery_pod.
 */
data class QueuedPodSubmission(
    val deliveryJobId: String,
    val localPhotoPath: String,
    val photoMime: String,
    val localSignaturePath: String,
    val signatureMime: String,
    val otpCode: String,
    val notes: String?,
    val photoObjectKey: String,
    val signatureObjectKey: String,
)

class OfflinePodQueue(
    context: Context,
    private val fileName: String = "offline_pod_queue.json",
) {
    private val file = File(context.applicationContext.filesDir, fileName)
    private val lock = Any()

    fun enqueue(item: QueuedPodSubmission) = synchronized(lock) {
        val list = loadUnlocked().toMutableList()
        list.removeAll { it.deliveryJobId == item.deliveryJobId }
        list.add(item)
        while (list.size > MAX_SIZE) list.removeAt(0)
        saveUnlocked(list)
    }

    fun peek(): List<QueuedPodSubmission> = synchronized(lock) { loadUnlocked() }

    fun size(): Int = synchronized(lock) { loadUnlocked().size }

    fun removeJob(jobId: String) = synchronized(lock) {
        saveUnlocked(loadUnlocked().filterNot { it.deliveryJobId == jobId })
    }

    fun dropFirst(count: Int) = synchronized(lock) {
        if (count <= 0) return
        val list = loadUnlocked().toMutableList()
        repeat(count.coerceAtMost(list.size)) { list.removeAt(0) }
        saveUnlocked(list)
    }

    private fun loadUnlocked(): List<QueuedPodSubmission> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        QueuedPodSubmission(
                            deliveryJobId = o.getString("jobId"),
                            localPhotoPath = o.getString("photoPath"),
                            photoMime = o.getString("photoMime"),
                            localSignaturePath = o.getString("sigPath"),
                            signatureMime = o.getString("sigMime"),
                            otpCode = o.getString("otp"),
                            notes = o.optString("notes", null).takeIf { it.isNotBlank() },
                            photoObjectKey = o.getString("photoKey"),
                            signatureObjectKey = o.getString("sigKey"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveUnlocked(list: List<QueuedPodSubmission>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("jobId", p.deliveryJobId)
                    put("photoPath", p.localPhotoPath)
                    put("photoMime", p.photoMime)
                    put("sigPath", p.localSignaturePath)
                    put("sigMime", p.signatureMime)
                    put("otp", p.otpCode)
                    put("notes", p.notes ?: JSONObject.NULL)
                    put("photoKey", p.photoObjectKey)
                    put("sigKey", p.signatureObjectKey)
                },
            )
        }
        file.writeText(arr.toString())
    }

    companion object {
        const val MAX_SIZE: Int = 50
    }
}

fun deliveryPodPhotoObjectKey(jobId: String): String = "$jobId/photo.jpg"
fun deliveryPodSignatureObjectKey(jobId: String): String = "$jobId/signature.png"
