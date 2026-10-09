package co.zw.nissangtr.catalogapk.ui.bundles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.auth.SupabaseSessionManager
import co.zw.nissangtr.catalogapk.data.model.SupabaseProjectEntity
import co.zw.nissangtr.catalogapk.domain.BundlePickerReader
import co.zw.nissangtr.catalogapk.domain.BundleSnapshot
import co.zw.nissangtr.catalogapk.domain.BundleVariantRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class BundlePickerUiState(
    val jobId: String = "",
    val snapshot: BundleSnapshot? = null,
    val selected: Set<String> = emptySet(),
    val selectedProjectId: String? = null,
    val message: String? = null,
    val importing: Boolean = false,
)

class BundlePickerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication
    private val jobId: String = checkNotNull(savedStateHandle["jobId"])

    val projects: StateFlow<List<SupabaseProjectEntity>> = app.projectRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(BundlePickerUiState(jobId = jobId))
    val uiState: StateFlow<BundlePickerUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    suspend fun reload() {
        val job = app.jobRepository.getJob(jobId) ?: return
        val snap = BundlePickerReader.read(job.outRoot, job.maker)
        val publishable = snap.variants.filter { it.publishable }
        _ui.update {
            it.copy(
                snapshot = snap,
                selected = publishable.map { v -> key(v) }.toSet(),
                message = if (publishable.isEmpty()) {
                    "No gate-passing variants yet — finish crawl/filter first."
                } else {
                    null
                },
            )
        }
    }

    fun toggle(row: BundleVariantRow) {
        val k = key(row)
        _ui.update {
            val next = it.selected.toMutableSet()
            if (!next.add(k)) next.remove(k)
            it.copy(selected = next)
        }
    }

    fun selectProject(id: String) {
        _ui.update { it.copy(selectedProjectId = id) }
    }

    fun selectAllPublishable() {
        val rows = _ui.value.snapshot?.variants.orEmpty().filter { it.publishable }
        _ui.update { it.copy(selected = rows.map { v -> key(v) }.toSet()) }
    }

    fun importSelected() {
        viewModelScope.launch {
            val state = _ui.value
            val snap = state.snapshot ?: return@launch
            val projectId = state.selectedProjectId
                ?: return@launch _ui.update { it.copy(message = "Pick a Supabase project") }
            val project = projects.value.firstOrNull { it.id == projectId }
                ?: return@launch _ui.update { it.copy(message = "Project not found") }
            val anon = app.securePrefs.getAnonKey(projectId) ?: project.anonKeyEncrypted
            if (anon.isBlank()) {
                _ui.update { it.copy(message = "Missing anon key for project") }
                return@launch
            }
            if (!app.supabaseSession.isSignedIn(projectId)) {
                _ui.update { it.copy(message = "Sign in on Projects tab first") }
                return@launch
            }
            val keys = state.selected.mapNotNull { token ->
                val parts = token.split("::", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            if (keys.isEmpty()) {
                _ui.update { it.copy(message = "Select at least one publishable variant") }
                return@launch
            }
            val bundle = snap.bundleJson ?: JsonObject(emptyMap())
            _ui.update { it.copy(importing = true, message = "Importing…") }
            val payload = SupabaseSessionManager.buildImportPayload(
                jobId = jobId,
                maker = snap.maker,
                selectedVariantKeys = keys,
                bundleJson = bundle,
            )
            val result = app.supabaseSession.invokeHierarchyImport(
                projectId,
                project.url,
                anon,
                payload,
            )
            _ui.update {
                it.copy(
                    importing = false,
                    message = result.fold(
                        onSuccess = { "Import OK: $it" },
                        onFailure = { err -> "Import failed: ${err.message}" },
                    ),
                )
            }
        }
    }

    companion object {
        fun key(row: BundleVariantRow): String = "${row.modelSlug}::${row.variantSlug}"
    }
}
