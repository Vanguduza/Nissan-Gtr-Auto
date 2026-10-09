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
        runCatching { setForegroundAsync(createForegroundInfo(jobId)) }

        val app = applicationContext as? CatalogApkApplication
        if (app == null) {
            return Result.failure()
        }
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
            // Success (not retry): unique work completes so supervisor can re-enqueue
            // without being stuck behind exponential backoff.
            return Result.success()
        }

        val active = jobDao.findByDesiredStateAndStatuses(
            JobDesiredState.RUN.name,
            listOf(JobStatus.PROCESSING.name),
        ).filter { it.id != jobId }
        if (active.size >= gate.effectiveMaxSlots) {
            // Defer without WorkManager backoff — supervisor reclaim fills free slots.
            return Result.success()
        }

        val outDir = File(job.outRoot)
        val jobRoot = outDir.parentFile ?: outDir
        outDir.mkdirs()
        jobRoot.mkdirs()
        val pauseFlag = File(jobRoot, "pause.flag")
        if (job.desiredState == JobDesiredState.RUN.name) {
            pauseFlag.delete()
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

        val flareLife = FlareSolverrLifecycle(applicationContext, app.appPreferences)
        var flareUrl = FlareSolverrLifecycle.normalizeApi(
            profile.flaresolverrUrl.ifBlank { FlareSolverrLifecycle.DEFAULT_FLARE_API },
        )
        var forceFlare = profile.cloudflareMode.equals("always", ignoreCase = true)
        val cfMode = profile.cloudflareMode.lowercase().ifBlank { "auto" }

        // Re-read desired state before slow CF work (user may have paused).
        job = jobDao.getById(jobId) ?: return Result.failure()
        if (job.desiredState == JobDesiredState.PAUSE.name) {
            jobDao.update(
                job.copy(
                    status = JobStatus.PAUSED.name,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return Result.success()
        }

        if (cfMode == "always") {
            val ensure = flareLife.ensure(flareUrl)
            jobDao.update(
                job.copy(lastHeartbeat = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()),
            )
            if (!ensure.ok) {
                jobDao.update(
                    job.copy(
                        status = JobStatus.FAILED.name,
                        errorMessage = ensure.message,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                return Result.failure()
            }
            flareUrl = ensure.resolvedUrl
        } else if (cfMode != "off") {
            // Probe direct first; SiteHttpClient auto-ensures sidecar only on CF challenge.
            val probeClient = SiteHttpClient(
                cloudflareMode = "auto",
                flaresolverrUrl = flareUrl,
                lifecycle = flareLife,
            )
            val hub = profile.baseUrl.trimEnd('/') + "/"
            val probe = probeClient.fetch(hub)
            jobDao.update(
                job.copy(lastHeartbeat = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()),
            )
            probe.flaresolverrUrlUsed?.let { flareUrl = it }
            if (probe.ok && probe.viaFlareSolverr) {
                forceFlare = true
            } else if (!probe.ok && probe.viaFlareSolverr) {
                // Sidecar down: still crawl direct. Python can use FlareSolverr if it comes up.
                forceFlare = false
                jobDao.update(
                    job.copy(
                        errorMessage = "FlareSolverr offline — crawling direct. ${probe.error ?: ""}".take(400),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        job = jobDao.getById(jobId) ?: return Result.failure()
        if (job.desiredState == JobDesiredState.PAUSE.name) {
            jobDao.update(
                job.copy(
                    status = JobStatus.PAUSED.name,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return Result.success()
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
        val result = try {
            pipeline.run(
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
        } catch (t: Throwable) {
            val err = File(job.outRoot, "pipeline_error.txt")
            err.parentFile?.mkdirs()
            err.writeText(t.stackTraceToString().take(4000))
            jobDao.update(
                job.copy(
                    status = JobStatus.FAILED.name,
                    errorMessage = (t.message ?: t.javaClass.simpleName).take(500),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return Result.failure()
        }

        val finalJob = jobDao.getById(jobId) ?: return Result.failure()
        val errFile = File(finalJob.outRoot, "pipeline_error.txt")
        val errText = errFile.takeIf { it.exists() }?.readText()?.take(500)
        val qualityFile = File(finalJob.outRoot, "bundle_quality.txt")
        val qualityText = qualityFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
        val qualityPass = qualityText.lineSequence().any { it.trim() == "ok=true" }
        val qualityFailReason = qualityText.lineSequence()
            .filter { it.startsWith("FAIL:") }
            .joinToString("; ")
            .ifBlank { qualityText.take(400) }

        val updated = when {
            result == PipelineResult.COMPLETE && !qualityPass -> finalJob.copy(
                status = JobStatus.FAILED.name,
                errorMessage = (
                    "Quality gate FAIL: " + (
                        qualityFailReason.ifBlank {
                            errText ?: "hierarchy-only or empty diagrams (see bundle_quality.txt)"
                        }
                        )
                    ).take(500),
            )
            result == PipelineResult.COMPLETE -> finalJob.copy(
                status = JobStatus.COMPLETE.name,
                desiredState = JobDesiredState.RUN.name,
                errorMessage = null,
            )
            result == PipelineResult.PAUSED -> finalJob.copy(
                status = JobStatus.PAUSED.name,
                desiredState = JobDesiredState.PAUSE.name,
            )
            else -> finalJob.copy(
                status = JobStatus.FAILED.name,
                errorMessage = errText
                    ?: qualityFailReason.takeIf { it.isNotBlank() }
                    ?: "Pipeline failed",
            )
        }
        jobDao.update(updated.copy(updatedAt = System.currentTimeMillis()))

        return when {
            updated.status == JobStatus.FAILED.name -> Result.failure()
            else -> Result.success()
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
