package co.zw.nissangtr.catalogapk.ui.projects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.SupabaseProjectEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CrawlSettingsUi(
    val maxSlots: Int = 1,
    val requireCharging: Boolean = true,
    val requireWifi: Boolean = true,
    val debugBypass: Boolean = false,
    val maxPagesDebug: Int = 0,
)

class ProjectsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication

    val projects: StateFlow<List<SupabaseProjectEntity>> = app.projectRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val signedInProjectId = app.supabaseSession.signedInProjectId

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val settings: StateFlow<CrawlSettingsUi> = kotlinx.coroutines.flow.combine(
        app.appPreferences.maxConcurrentJobs,
        app.appPreferences.requireCharging,
        app.appPreferences.requireUnmeteredWifi,
        app.appPreferences.debugBypassGates,
    ) { slots, charge, wifi, bypass ->
        CrawlSettingsUi(slots, charge, wifi, bypass, 0)
    }.let { base ->
        kotlinx.coroutines.flow.combine(base, app.appPreferences.maxPagesDebug) { s, pages ->
            s.copy(maxPagesDebug = pages)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CrawlSettingsUi())

    fun addProject(name: String, url: String, anonKey: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            app.projectRepository.upsert(
                SupabaseProjectEntity(
                    id = id,
                    name = name.trim(),
                    url = url.trim(),
                    anonKeyEncrypted = anonKey.trim(),
                ),
            )
            app.securePrefs.putAnonKey(id, anonKey.trim())
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch { app.projectRepository.delete(id) }
    }

    fun signIn(projectId: String, email: String, password: String) {
        viewModelScope.launch {
            val project = projects.value.firstOrNull { it.id == projectId } ?: return@launch
            val anon = app.securePrefs.getAnonKey(projectId) ?: project.anonKeyEncrypted
            runCatching {
                app.supabaseSession.signInEmail(
                    projectId,
                    project.url,
                    anon,
                    email.trim(),
                    password,
                )
            }.onSuccess {
                _message.value = "Signed in to ${project.name}"
            }.onFailure {
                _message.value = "Sign-in failed: ${it.message}"
            }
        }
    }

    fun signOut(projectId: String) {
        viewModelScope.launch {
            app.supabaseSession.signOut(projectId)
            _message.value = "Signed out"
        }
    }

    fun setMaxSlots(value: Int) {
        viewModelScope.launch { app.appPreferences.setMaxConcurrentJobs(value) }
    }

    fun setRequireCharging(value: Boolean) {
        viewModelScope.launch { app.appPreferences.setRequireCharging(value) }
    }

    fun setRequireWifi(value: Boolean) {
        viewModelScope.launch { app.appPreferences.setRequireUnmeteredWifi(value) }
    }

    fun setDebugBypass(value: Boolean) {
        viewModelScope.launch { app.appPreferences.setDebugBypassGates(value) }
    }

    fun setMaxPagesDebug(value: Int) {
        viewModelScope.launch { app.appPreferences.setMaxPagesDebug(value) }
    }
}
