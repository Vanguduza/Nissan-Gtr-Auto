package co.zw.nissangtr.catalogapk.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * ADB smoke helper:
 * ```
 * adb shell am broadcast -a co.zw.nissangtr.catalogapk.DEBUG_ENQUEUE \
 *   -n co.zw.nissangtr.catalogapk/.worker.DebugEnqueueReceiver \
 *   --es profile_id megazip --es maker Nissan \
 *   --es model_slug patrol-y61 --es model_name "Patrol Y61" \
 *   --es chassis Y61 --ei max_pages 12
 * ```
 */
class DebugEnqueueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val pending = goAsync()
        val app = context.applicationContext as? CatalogApkApplication
        if (app == null) {
            Log.e(TAG, "Application not CatalogApkApplication")
            pending.finish()
            return
        }
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)?.trim().orEmpty()
        val maker = intent.getStringExtra(EXTRA_MAKER)?.trim().orEmpty()
        val modelSlug = intent.getStringExtra(EXTRA_MODEL_SLUG)?.trim().orEmpty()
        val modelName = intent.getStringExtra(EXTRA_MODEL_NAME)?.trim()
            ?.ifBlank { null } ?: modelSlug
        val chassis = intent.getStringExtra(EXTRA_CHASSIS)?.trim().orEmpty()
        val maxPages = intent.getIntExtra(EXTRA_MAX_PAGES, -1)
        val maxConcurrent = intent.getIntExtra(EXTRA_MAX_CONCURRENT, -1)
        val resumeJobId = intent.getStringExtra(EXTRA_RESUME_JOB_ID)?.trim().orEmpty()
        val pauseOthers = intent.getBooleanExtra(EXTRA_PAUSE_OTHERS, true)

        Thread {
            try {
                runBlocking {
                    if (maxPages >= 0) {
                        app.appPreferences.setMaxPagesDebug(maxPages)
                    }
                    if (maxConcurrent >= 1) {
                        app.appPreferences.setMaxConcurrentJobs(maxConcurrent)
                    }
                    app.appPreferences.setDebugBypassGates(true)

                    if (resumeJobId.isNotEmpty()) {
                        val job = app.jobRepository.getJob(resumeJobId)
                        if (job == null) {
                            Log.e(TAG, "resume job not found: $resumeJobId")
                            return@runBlocking
                        }
                        app.jobRepository.updateJob(
                            job.copy(
                                desiredState = JobDesiredState.RUN.name,
                                status = JobStatus.QUEUED.name,
                                errorMessage = null,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        SupervisorScheduler.enqueue(app, resumeJobId, replace = true)
                        SupervisorScheduler.kickSupervisor(app)
                        Log.i(TAG, "resumed job=$resumeJobId")
                        return@runBlocking
                    }

                    if (profileId.isEmpty() || maker.isEmpty() || modelSlug.isEmpty() || chassis.isEmpty()) {
                        Log.e(
                            TAG,
                            "missing extras: profile_id/maker/model_slug/chassis required " +
                                "(or resume_job_id)",
                        )
                        return@runBlocking
                    }
                    val profile = app.profileRepository.getProfile(profileId)
                    if (profile == null) {
                        Log.e(TAG, "unknown profile_id=$profileId")
                        return@runBlocking
                    }
                    // Pause active RUN jobs only when requested (default) so smoke can claim a slot.
                    if (pauseOthers) {
                        val active = app.database.jobDao().findByDesiredStateAndStatuses(
                            JobDesiredState.RUN.name,
                            listOf(JobStatus.PROCESSING.name, JobStatus.QUEUED.name),
                        )
                        active.forEach { j ->
                            app.jobRepository.setDesiredState(j.id, JobDesiredState.PAUSE)
                        }
                    }

                    val jobs = app.jobRepository.createJobs(
                        profileId = profileId,
                        maker = maker,
                        modelSlug = modelSlug,
                        modelDisplayName = modelName,
                        chassisCodes = listOf(chassis),
                    )
                    jobs.forEach { job ->
                        SupervisorScheduler.enqueue(app, job.id, replace = true)
                        Log.i(TAG, "enqueued job=${job.id} profile=$profileId chassis=$chassis")
                    }
                    SupervisorScheduler.kickSupervisor(app)
                    // Confirm prefs readable
                    val pages = app.appPreferences.maxPagesDebug.first()
                    Log.i(TAG, "debug enqueue done; max_pages_debug=$pages jobs=${jobs.map { it.id }}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "debug enqueue failed", t)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION = "co.zw.nissangtr.catalogapk.DEBUG_ENQUEUE"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_MAKER = "maker"
        const val EXTRA_MODEL_SLUG = "model_slug"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_CHASSIS = "chassis"
        const val EXTRA_MAX_PAGES = "max_pages"
        const val EXTRA_MAX_CONCURRENT = "max_concurrent"
        const val EXTRA_PAUSE_OTHERS = "pause_others"
        const val EXTRA_RESUME_JOB_ID = "resume_job_id"
        private const val TAG = "DebugEnqueue"
    }
}
