package co.zw.nissangtr.catalogapk.ui.jobs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.JobStatus
import co.zw.nissangtr.catalogapk.worker.SupervisorScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class JobsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication

    val jobs: StateFlow<List<JobEntity>> = app.jobRepository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause(jobId: String) {
        viewModelScope.launch {
            val job = app.jobRepository.getJob(jobId) ?: return@launch
            // Cooperative: flag file + desired_state; worker finishes current page.
            File(job.outRoot).parentFile?.resolve("pause.flag")?.writeText("pause\n")
            app.jobRepository.setDesiredState(jobId, JobDesiredState.PAUSE)
            // Soft-cancel WorkManager only after flag is set — worker exits as PAUSED.
        }
    }

    fun resume(jobId: String) {
        viewModelScope.launch {
            val job = app.jobRepository.getJob(jobId) ?: return@launch
            File(job.outRoot).parentFile?.resolve("pause.flag")?.delete()
            app.jobRepository.setDesiredState(jobId, JobDesiredState.RUN)
            val latest = app.jobRepository.getJob(jobId) ?: return@launch
            if (latest.status == JobStatus.PAUSED.name ||
                latest.status == JobStatus.QUEUED.name ||
                latest.status == JobStatus.FAILED.name
            ) {
                app.jobRepository.updateJob(
                    latest.copy(status = JobStatus.QUEUED.name, errorMessage = null),
                )
            }
            SupervisorScheduler.enqueue(getApplication(), jobId)
        }
    }

    fun statusLabel(job: JobEntity): String {
        val desired = if (job.desiredState == JobDesiredState.PAUSE.name &&
            job.status == JobStatus.PROCESSING.name
        ) {
            "pausing…"
        } else {
            job.status
        }
        return "$desired · ${job.chassisCodesCsv}"
    }
}
