package co.zw.nissangtr.catalogapk.ui.session

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import co.zw.nissangtr.catalogapk.discovery.CatalogDiscoveryService
import co.zw.nissangtr.catalogapk.discovery.DiscoveredChassis
import co.zw.nissangtr.catalogapk.discovery.DiscoveredMaker
import co.zw.nissangtr.catalogapk.discovery.DiscoveredModel
import co.zw.nissangtr.catalogapk.discovery.FlareSolverrLifecycle
import co.zw.nissangtr.catalogapk.worker.SupervisorScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionUiState(
    val selectedProfileId: String? = null,
    val makers: List<DiscoveredMaker> = emptyList(),
    val models: List<DiscoveredModel> = emptyList(),
    val chassis: List<DiscoveredChassis> = emptyList(),
    val selectedMaker: DiscoveredMaker? = null,
    val selectedModel: DiscoveredModel? = null,
    val selectedChassis: Set<String> = emptySet(),
    val loadingMakers: Boolean = false,
    val loadingModels: Boolean = false,
    val loadingChassis: Boolean = false,
    val error: String? = null,
)

class NewSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication
    private val discovery = CatalogDiscoveryService(
        app.profileRepository,
        FlareSolverrLifecycle(app, app.appPreferences),
        app,
    )

    val profiles: StateFlow<List<SiteProfileEntity>> = app.profileRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(SessionUiState())
    val ui: StateFlow<SessionUiState> = _ui.asStateFlow()

    fun selectProfile(profileId: String) {
        _ui.update {
            it.copy(
                selectedProfileId = profileId,
                makers = emptyList(),
                models = emptyList(),
                chassis = emptyList(),
                selectedMaker = null,
                selectedModel = null,
                selectedChassis = emptySet(),
                error = null,
            )
        }
        refreshMakers()
    }

    fun refreshMakers() {
        val profileId = _ui.value.selectedProfileId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loadingMakers = true, error = null) }
            val profile = app.profileRepository.getProfile(profileId)
            if (profile == null) {
                _ui.update { it.copy(loadingMakers = false, error = "Profile missing") }
                return@launch
            }
            discovery.discoverMakers(profile)
                .onSuccess { makers ->
                    _ui.update { it.copy(loadingMakers = false, makers = makers, error = null) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loadingMakers = false, error = e.message, makers = emptyList()) }
                }
        }
    }

    fun selectMaker(maker: DiscoveredMaker) {
        _ui.update {
            it.copy(
                selectedMaker = maker,
                models = emptyList(),
                chassis = emptyList(),
                selectedModel = null,
                selectedChassis = emptySet(),
                error = null,
            )
        }
        val profileId = _ui.value.selectedProfileId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loadingModels = true) }
            val profile = app.profileRepository.getProfile(profileId) ?: return@launch
            discovery.discoverModels(profile, maker)
                .onSuccess { models ->
                    _ui.update { it.copy(loadingModels = false, models = models) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loadingModels = false, error = e.message, models = emptyList()) }
                }
        }
    }

    fun selectModel(model: DiscoveredModel) {
        val maker = _ui.value.selectedMaker ?: return
        _ui.update {
            it.copy(
                selectedModel = model,
                chassis = emptyList(),
                selectedChassis = emptySet(),
                error = null,
            )
        }
        val profileId = _ui.value.selectedProfileId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loadingChassis = true) }
            val profile = app.profileRepository.getProfile(profileId) ?: return@launch
            discovery.discoverChassis(profile, maker, model)
                .onSuccess { chassis ->
                    _ui.update { it.copy(loadingChassis = false, chassis = chassis) }
                }
                .onFailure { e ->
                    _ui.update { it.copy(loadingChassis = false, error = e.message, chassis = emptyList()) }
                }
        }
    }

    fun toggleChassis(code: String) {
        _ui.update { state ->
            val next = if (code in state.selectedChassis) state.selectedChassis - code else state.selectedChassis + code
            state.copy(selectedChassis = next)
        }
    }

    fun selectAllChassis() {
        _ui.update { it.copy(selectedChassis = it.chassis.map { c -> c.code }.toSet()) }
    }

    fun startJobs(onStarted: (List<String>) -> Unit) {
        val state = _ui.value
        val profileId = state.selectedProfileId ?: return
        val maker = state.selectedMaker ?: return
        val model = state.selectedModel ?: return
        if (state.selectedChassis.isEmpty()) return
        viewModelScope.launch {
            val gate = co.zw.nissangtr.catalogapk.worker.CrawlGates.evaluate(
                getApplication(),
                app.appPreferences,
            )
            if (!gate.allowed) {
                _ui.update {
                    it.copy(error = "Crawl gated: ${gate.reasons.joinToString("; ")}")
                }
                return@launch
            }
            val jobs = app.jobRepository.createJobs(
                profileId = profileId,
                maker = maker.name,
                modelSlug = model.slug,
                modelDisplayName = model.displayName,
                chassisCodes = state.selectedChassis.toList().sorted(),
            )
            // Slot-aware: enqueue up to free slots; supervisor reclaim picks the rest.
            val active = app.database.jobDao().findByDesiredStateAndStatuses(
                co.zw.nissangtr.catalogapk.data.model.JobDesiredState.RUN.name,
                listOf(co.zw.nissangtr.catalogapk.data.model.JobStatus.PROCESSING.name),
            )
            var free = (gate.effectiveMaxSlots - active.size).coerceAtLeast(0)
            jobs.forEach { job ->
                if (free > 0) {
                    SupervisorScheduler.enqueue(getApplication(), job.id, replace = true)
                    free--
                }
            }
            SupervisorScheduler.kickSupervisor(getApplication())
            onStarted(jobs.map { it.id })
        }
    }
}
