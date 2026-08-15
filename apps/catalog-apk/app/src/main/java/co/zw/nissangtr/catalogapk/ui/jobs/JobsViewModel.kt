package co.zw.nissangtr.catalogapk.ui.jobs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.JobDesiredState
import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.JobStatus
import co.zw.nissangtr.catalogapk.domain.BundleReviewReader
import co.zw.nissangtr.catalogapk.worker.SupervisorScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class JobsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication

    val jobs: StateFlow<List<JobEntity>> = app.jobRepository.observeJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _deleteMessage = MutableStateFlow<String?>(null)
    val deleteMessage: StateFlow<String?> = _deleteMessage.asStateFlow()

    fun pause(jobId: String) {
        viewModelScope.launch {
            val job = app.jobRepository.getJob(jobId) ?: return@launch
            // Cooperative: flag file + desired_state; worker finishes current page.
            // Must not crash if outRoot parent is not created yet (race with PROCESSING).
            runCatching {
                val jobRoot = File(job.outRoot).parentFile ?: File(job.outRoot)
                jobRoot.mkdirs()
                File(jobRoot, "pause.flag").writeText("pause\n")
            }
            app.jobRepository.setDesiredState(jobId, JobDesiredState.PAUSE)
        }
    }

    fun resume(jobId: String) {
        viewModelScope.launch {
            val job = app.jobRepository.getJob(jobId) ?: return@launch
            runCatching {
                val jobRoot = File(job.outRoot).parentFile ?: File(job.outRoot)
                File(jobRoot, "pause.flag").delete()
            }
            app.jobRepository.setDesiredState(jobId, JobDesiredState.RUN)
            val latest = app.jobRepository.getJob(jobId) ?: return@launch
            if (latest.status == JobStatus.PAUSED.name ||
                latest.status == JobStatus.QUEUED.name ||
                latest.status == JobStatus.FAILED.name ||
                latest.status == JobStatus.COMPLETE.name
            ) {
                app.jobRepository.updateJob(
                    latest.copy(status = JobStatus.QUEUED.name, errorMessage = null),
                )
            }
            SupervisorScheduler.enqueue(getApplication(), jobId, replace = true)
            SupervisorScheduler.kickSupervisor(getApplication())
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            val job = app.jobRepository.requestCancelBeforeDelete(jobId)
            if (job == null) {
                _deleteMessage.value = "Job not found"
                return@launch
            }
            SupervisorScheduler.cancel(getApplication(), jobId)
            withContext(Dispatchers.IO) {
                app.jobRepository.deleteJobRecordAndFiles(job)
            }
            _deleteMessage.value = "Deleted ${job.maker} · ${job.chassisCodesCsv}"
        }
    }

    fun clearDeleteMessage() {
        _deleteMessage.value = null
    }

    fun canReview(job: JobEntity): Boolean =
        BundleReviewReader.isReviewable(job.outRoot, job.maker)

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
