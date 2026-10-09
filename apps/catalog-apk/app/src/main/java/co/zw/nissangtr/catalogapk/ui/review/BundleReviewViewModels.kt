package co.zw.nissangtr.catalogapk.ui.review

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.domain.BundleDiagramRow
import co.zw.nissangtr.catalogapk.domain.BundleFitmentRow
import co.zw.nissangtr.catalogapk.domain.BundleReviewModel
import co.zw.nissangtr.catalogapk.domain.BundleReviewReader
import co.zw.nissangtr.catalogapk.domain.BundleSectionRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BundleReviewUiState(
    val loading: Boolean = true,
    val model: BundleReviewModel? = null,
    val error: String? = null,
)

data class SectionDiagramsUiState(
    val loading: Boolean = true,
    val section: BundleSectionRow? = null,
    val diagrams: List<BundleDiagramRow> = emptyList(),
    val error: String? = null,
)

data class DiagramDetailUiState(
    val loading: Boolean = true,
    val diagram: BundleDiagramRow? = null,
    val fitments: List<BundleFitmentRow> = emptyList(),
    val error: String? = null,
)

class BundleReviewViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication
    private val jobId: String = checkNotNull(savedStateHandle["jobId"])

    private val _ui = MutableStateFlow(BundleReviewUiState())
    val uiState: StateFlow<BundleReviewUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        _ui.update { it.copy(loading = true, error = null) }
        val result = withContext(Dispatchers.IO) {
            val job = app.jobRepository.getJob(jobId)
                ?: return@withContext Result.failure(IllegalStateException("Job not found"))
            runCatching { BundleReviewReader.read(job.outRoot, job.maker) }
        }
        _ui.update {
            result.fold(
                onSuccess = { model -> BundleReviewUiState(loading = false, model = model) },
                onFailure = { err ->
                    BundleReviewUiState(loading = false, error = err.message ?: "Failed to read bundle")
                },
            )
        }
    }
}

class SectionDiagramsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication
    private val jobId: String = checkNotNull(savedStateHandle["jobId"])
    private val sectionKey: String = Uri.decode(checkNotNull(savedStateHandle["sectionKey"]))

    private val _ui = MutableStateFlow(SectionDiagramsUiState())
    val uiState: StateFlow<SectionDiagramsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val result = withContext(Dispatchers.IO) {
                val job = app.jobRepository.getJob(jobId)
                    ?: return@withContext Result.failure(IllegalStateException("Job not found"))
                runCatching {
                    val model = BundleReviewReader.read(job.outRoot, job.maker)
                    val section = model.sections.firstOrNull { it.key == sectionKey }
                    section to BundleReviewReader.diagramsForSection(model, sectionKey)
                }
            }
            _ui.update {
                result.fold(
                    onSuccess = { (section, diagrams) ->
                        SectionDiagramsUiState(
                            loading = false,
                            section = section,
                            diagrams = diagrams,
                            error = if (section == null && diagrams.isEmpty()) "Section not found" else null,
                        )
                    },
                    onFailure = { err ->
                        SectionDiagramsUiState(loading = false, error = err.message)
                    },
                )
            }
        }
    }
}

class DiagramDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication
    private val jobId: String = checkNotNull(savedStateHandle["jobId"])
    private val diagramKey: String = Uri.decode(checkNotNull(savedStateHandle["diagramKey"]))

    private val _ui = MutableStateFlow(DiagramDetailUiState())
    val uiState: StateFlow<DiagramDetailUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val result = withContext(Dispatchers.IO) {
                val job = app.jobRepository.getJob(jobId)
                    ?: return@withContext Result.failure(IllegalStateException("Job not found"))
                runCatching {
                    val model = BundleReviewReader.read(job.outRoot, job.maker)
                    val diagram = BundleReviewReader.findDiagram(model, diagramKey)
                        ?: return@runCatching null to emptyList<BundleFitmentRow>()
                    val fitments = BundleReviewReader.fitmentsForDiagram(job.outRoot, job.maker, diagram)
                    diagram to fitments
                }
            }
            _ui.update {
                result.fold(
                    onSuccess = { pair ->
                        val (diagram, fitments) = pair
                        DiagramDetailUiState(
                            loading = false,
                            diagram = diagram,
                            fitments = fitments,
                            error = if (diagram == null) "Diagram not found" else null,
                        )
                    },
                    onFailure = { err ->
                        DiagramDetailUiState(loading = false, error = err.message)
                    },
                )
            }
        }
    }
}
