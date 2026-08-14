package co.zw.nissangtr.management.pos.offline

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Background drain of the offline sale queue when connectivity returns.
 * Requires [OfflinePosRpcHolder] to expose the signed-in [co.zw.nissangtr.management.rpc.RpcClient].
 */
class OfflinePosSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val rpc = OfflinePosRpcHolder.get() ?: return Result.retry()
        val store = SqlCipherOfflinePosStore.open(applicationContext)
        return try {
            val engine = OfflinePosSyncEngine(
                rpc = rpc,
                store = store,
                deviceId = android.provider.Settings.Secure.getString(
                    applicationContext.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID,
                ) ?: "tablet",
            )
            val result = engine.drainQueue()
            if (result.remaining > 0 && result.synced == 0) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        } finally {
            store.close()
        }
    }

    companion object {
        private const val UNIQUE = "gtr_offline_pos_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<OfflinePosSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, req)
        }
    }
}
