package co.zw.nissangtr.catalogapk.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.R
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobStatus
import co.zw.nissangtr.catalogapk.discovery.FlareSolverrLifecycle
import co.zw.nissangtr.catalogapk.discovery.SiteHttpClient
import co.zw.nissangtr.catalogapk.domain.ChaquopyPipelineBridge
import co.zw.nissangtr.catalogapk.domain.OrchestratorArgBuilder
import co.zw.nissangtr.catalogapk.domain.PipelineResult
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Runs in process `:worker` via WorkManager multiprocess + Chaquopy.
 */
class CatalogCrawlWorker(
    appContext: Context,
    params: WorkerParameters,
) : RemoteCoroutineWorker(appContext, params) {

    override suspend fun doRemoteWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        setForegroundAsync(createForegroundInfo(jobId))

        val app = applicationContext as? CatalogApkApplication
            ?: return Result.failure()
        val jobDao = app.database.jobDao()
        var job = jobDao.getById(jobId) ?: return Result.failure()
        val profile = app.database.siteProfileDao().getById(job.profileId)
            ?: return Result.failure()

        val gate = CrawlGates.evaluate(applicationContext, app.appPreferences)
        if (!gate.allowed) {
            jobDao.update(
                job.copy(
                    status = JobStatus.QUEUED.name,
                    errorMessage = gate.reasons.joinToString("; "),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return Result.retry()
        }

        val active = jobDao.findByDesiredStateAndStatuses(
            JobDesiredState.RUN.name,
            listOf(JobStatus.PROCESSING.name),
        ).filter { it.id != jobId }
        if (active.size >= gate.effectiveMaxSlots) {
            return Result.retry()
        }

        val jobRoot = File(job.outRoot).parentFile
        val pauseFlag = jobRoot?.resolve("pause.flag")
        if (job.desiredState == JobDesiredState.RUN.name) {
            pauseFlag?.delete()
        }
        if (job.desiredState == JobDesiredState.PAUSE.name) {
            jobDao.update(
                job.copy(
                    status = JobStatus.PAUSED.name,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return Result.success()
        }

        val now = System.currentTimeMillis()
        job = job.copy(
            status = JobStatus.PROCESSING.name,
            lastHeartbeat = now,
            updatedAt = now,
            errorMessage = null,
        )
        jobDao.update(job)

        val flareLife = FlareSolverrLifecycle(applicationContext)
        var flareUrl = profile.flaresolverrUrl
        var forceFlare = profile.cloudflareMode.equals("always", ignoreCase = true)

        if (!profile.cloudflareMode.equals("off", ignoreCase = true)) {
            val ensure = flareLife.ensure(flareUrl)
            if (ensure.ok) {
                flareUrl = ensure.resolvedUrl
            } else if (forceFlare) {
                jobDao.update(
                    job.copy(
                        status = JobStatus.FAILED.name,
                        errorMessage = ensure.message,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                return Result.failure()
            }

            val probeClient = SiteHttpClient(
                cloudflareMode = profile.cloudflareMode,
                flaresolverrUrl = flareUrl,
            )
            val hub = profile.baseUrl.trimEnd('/') + "/"
            val probe = probeClient.fetch(hub)
            if (probe.viaFlareSolverr) forceFlare = true
            if (!probe.ok && !ensure.ok && profile.cloudflareMode.equals("auto", ignoreCase = true)) {
                val retry = flareLife.ensure(flareUrl)
                if (!retry.ok) {
                    jobDao.update(
                        job.copy(
                            status = JobStatus.FAILED.name,
                            errorMessage = "CF/probe failed; ${retry.message}",
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    return Result.failure()
                }
                flareUrl = retry.resolvedUrl
                forceFlare = true
            }
        }

        val maxPages = app.appPreferences.maxPagesDebug.first().takeIf { it > 0 }
        val argv = OrchestratorArgBuilder.buildArgv(
            job,
            profile,
            flaresolverrUrlOverride = flareUrl,
            forceFlareSolverr = forceFlare,
            maxPages = maxPages,
        )

        val pipeline = ChaquopyPipelineBridge(applicationContext)
        val result = pipeline.run(
            job = job,
            profile = profile,
            argv = argv,
            shouldPause = {
                jobDao.getById(jobId)?.desiredState == JobDesiredState.PAUSE.name
            },
            onHeartbeat = {
                val current = jobDao.getById(jobId) ?: return@run
                jobDao.update(
                    current.copy(
                        lastHeartbeat = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            },
        )

        val finalJob = jobDao.getById(jobId) ?: return Result.failure()
        val errFile = File(finalJob.outRoot, "pipeline_error.txt")
        val errText = errFile.takeIf { it.exists() }?.readText()?.take(500)
        val updated = when (result) {
            PipelineResult.COMPLETE -> finalJob.copy(
                status = JobStatus.COMPLETE.name,
                desiredState = JobDesiredState.RUN.name,
                errorMessage = null,
            )
            PipelineResult.PAUSED -> finalJob.copy(
                status = JobStatus.PAUSED.name,
                desiredState = JobDesiredState.PAUSE.name,
            )
            PipelineResult.FAILED -> finalJob.copy(
                status = JobStatus.FAILED.name,
                errorMessage = errText ?: "Pipeline failed",
            )
        }
        jobDao.update(updated.copy(updatedAt = System.currentTimeMillis()))

        return when (result) {
            PipelineResult.FAILED -> Result.failure()
            PipelineResult.PAUSED, PipelineResult.COMPLETE -> Result.success()
        }
    }

    private fun createForegroundInfo(jobId: String): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notification_crawl_title))
            .setContentText(
                applicationContext.getString(R.string.notification_crawl_text, jobId.take(8)),
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notification_channel_crawl),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        private const val CHANNEL_ID = "catalog_crawl"
        private const val NOTIFICATION_ID = 1001
    }
}
