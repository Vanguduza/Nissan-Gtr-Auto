package co.zw.nissangtr.catalogapk.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic supervisor: reclaim stale PROCESSING, enforce slots, enqueue RUN work.
 */
class SupervisorReclaimWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CatalogApkApplication ?: return Result.success()
        val gate = CrawlGates.evaluate(applicationContext, app.appPreferences)
        val staleMs = app.appPreferences.staleHeartbeatMs.first()
        val now = System.currentTimeMillis()
        val dao = app.database.jobDao()

        // Reclaim zombie PROCESSING (no heartbeat).
        val processing = dao.findByDesiredStateAndStatuses(
            desiredState = JobDesiredState.RUN.name,
            statuses = listOf(JobStatus.PROCESSING.name),
        ) + dao.findByDesiredStateAndStatuses(
            desiredState = JobDesiredState.PAUSE.name,
            statuses = listOf(JobStatus.PROCESSING.name),
        )
        for (job in processing.distinctBy { it.id }) {
            if (now - job.lastHeartbeat > staleMs) {
                dao.update(
                    job.copy(
                        status = if (job.desiredState == JobDesiredState.PAUSE.name) {
                            JobStatus.PAUSED.name
                        } else {
                            JobStatus.QUEUED.name
                        },
                        errorMessage = "Reclaimed stale heartbeat",
                        updatedAt = now,
                    ),
                )
            }
        }

        if (!gate.allowed || gate.effectiveMaxSlots <= 0) {
            return Result.success()
        }

        val active = dao.findByDesiredStateAndStatuses(
            desiredState = JobDesiredState.RUN.name,
            statuses = listOf(JobStatus.PROCESSING.name),
        )
        var free = gate.effectiveMaxSlots - active.size
        if (free <= 0) return Result.success()

        val candidates = dao.findByDesiredStateAndStatuses(
            desiredState = JobDesiredState.RUN.name,
            statuses = listOf(JobStatus.QUEUED.name, JobStatus.PAUSED.name),
        ).sortedBy { it.createdAt }

        for (job in candidates) {
            if (free <= 0) break
            // REPLACE clears stuck ENQUEUED/backoff unique work from prior slot deferrals.
            SupervisorScheduler.enqueue(applicationContext, job.id, replace = true)
            free--
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE = "catalog_supervisor_reclaim"

        fun ensureScheduled(context: Context) {
            val req = PeriodicWorkRequestBuilder<SupervisorReclaimWorker>(15, TimeUnit.MINUTES)
                .addTag(UNIQUE)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}
