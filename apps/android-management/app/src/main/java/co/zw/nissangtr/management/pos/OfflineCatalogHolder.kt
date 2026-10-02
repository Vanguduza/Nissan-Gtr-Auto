package co.zw.nissangtr.management.pos

import android.content.Context
import co.zw.nissangtr.management.pos.offline.bundle.OfflineCatalogBundle
import co.zw.nissangtr.management.rpc.RpcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One offline catalogue per app process, so a download keeps going while staff move between the
 * POS and other screens. Files live in no-backup app storage (private to the app, never in cloud
 * backups).
 */
object OfflineCatalogHolder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bundle: OfflineCatalogBundle? = null
    private var job: Job? = null

    /** The device's validated connectivity, as the POS shell last saw it. */
    val online = AtomicBoolean(true)

    @Synchronized
    fun get(context: Context): OfflineCatalogBundle = bundle ?: OfflineCatalogBundle(
        File(context.applicationContext.noBackupFilesDir, "pos-offline-catalog"),
    ).also {
        bundle = it
        scope.launch { it.init() }
    }

    @Synchronized
    fun download(context: Context, rpc: RpcClient) {
        if (job?.isActive == true) return
        val b = get(context)
        job = scope.launch {
            // Failures are shown on the Settings row through the bundle's status.
            runCatching { b.download { rpc.catalogLive("offline-bundle") } }
        }
    }

    @Synchronized
    fun pause() {
        job?.cancel()
    }

    @Synchronized
    fun remove(context: Context) {
        job?.cancel()
        val b = get(context)
        scope.launch { b.remove() }
    }
}
