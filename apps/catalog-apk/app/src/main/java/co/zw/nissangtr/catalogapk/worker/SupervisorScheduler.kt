package co.zw.nissangtr.catalogapk.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object SupervisorScheduler {
    const val WORK_PREFIX = "catalog_crawl_"

    // androidx.work.multiprocess.RemoteListenableWorker constants (stable string keys)
    private const val ARG_PACKAGE =
        "androidx.work.impl.workers.RemoteListenableWorker.ARGUMENT_PACKAGE_NAME"
    private const val ARG_CLASS =
        "androidx.work.impl.workers.RemoteListenableWorker.ARGUMENT_CLASS_NAME"
    private const val REMOTE_SERVICE =
        "androidx.work.multiprocess.RemoteWorkerService"

    fun enqueue(context: Context, jobId: String, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<CatalogCrawlWorker>()
            .setInputData(
                workDataOf(
                    CatalogCrawlWorker.KEY_JOB_ID to jobId,
                    ARG_PACKAGE to context.packageName,
                    ARG_CLASS to REMOTE_SERVICE,
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_PREFIX + jobId)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_PREFIX + jobId,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
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
