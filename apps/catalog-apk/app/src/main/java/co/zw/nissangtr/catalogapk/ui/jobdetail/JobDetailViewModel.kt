package co.zw.nissangtr.catalogapk.ui.jobdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import co.zw.nissangtr.catalogapk.domain.DiskHud
import co.zw.nissangtr.catalogapk.domain.DiskHudReader
import co.zw.nissangtr.catalogapk.domain.QualityReportReader
import co.zw.nissangtr.catalogapk.domain.QualitySnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class JobDetailUiState(
    val job: JobEntity? = null,
    val profile: SiteProfileEntity? = null,
    val quality: QualitySnapshot = QualitySnapshot(),
    val diskHud: DiskHud = DiskHud(0, 0, 0, 0),
)

class JobDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication
    private val jobId: String = checkNotNull(savedStateHandle["jobId"])

    val uiState: StateFlow<JobDetailUiState> = combine(
        app.jobRepository.observeJob(jobId),
        app.profileRepository.observeProfiles(),
    ) { job, profiles ->
        val profile = job?.let { j -> profiles.find { it.id == j.profileId } }
        val quality = job?.let {
            QualityReportReader.readFromOutRoot(it.outRoot, it.maker)
        } ?: QualitySnapshot()
        val disk = job?.let {
            DiskHudReader.read(app.filesDir, it.htmlDropped, it.htmlRetained)
        } ?: DiskHudReader.read(app.filesDir, 0, 0)
        JobDetailUiState(job, profile, quality, disk)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JobDetailUiState())
}
