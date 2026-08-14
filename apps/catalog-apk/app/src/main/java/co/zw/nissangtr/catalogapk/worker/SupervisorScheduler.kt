package co.zw.nissangtr.catalogapk.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.multiprocess.RemoteCoroutineWorker
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object SupervisorScheduler {
    const val WORK_PREFIX = "catalog_crawl_"

    fun enqueue(context: Context, jobId: String) {
        val request = OneTimeWorkRequestBuilder<CatalogCrawlWorker>()
            .setInputData(
                workDataOf(
                    CatalogCrawlWorker.KEY_JOB_ID to jobId,
                    RemoteCoroutineWorker.ARGUMENT_PACKAGE_NAME to context.packageName,
                    RemoteCoroutineWorker.ARGUMENT_CLASS_NAME to
                        "androidx.work.multiprocess.RemoteWorkerService",
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_PREFIX + jobId)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_PREFIX + jobId,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context, jobId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + jobId)
    }

    fun kickSupervisor(context: Context) {
        val request = OneTimeWorkRequestBuilder<SupervisorReclaimWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
