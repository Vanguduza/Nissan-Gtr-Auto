package co.zw.nissangtr.catalogapk.ui.targets

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.zw.nissangtr.catalogapk.CatalogApkApplication
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import co.zw.nissangtr.catalogapk.data.profile.ProfilePathsJson
import co.zw.nissangtr.catalogapk.data.profile.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TargetEditState(
    val profile: SiteProfileEntity? = null,
    val baseUrl: String = "",
    val makerHub: String = "",
    val partsHub: String = "",
    val previewUrl: String = "",
    val cloudflareMode: String = "auto",
    val flaresolverrUrl: String = "http://127.0.0.1:8191/v1",
)

class TargetsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CatalogApkApplication
    private val repo: ProfileRepository = app.profileRepository

    val profiles: StateFlow<List<SiteProfileEntity>> = repo.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadEdit(profileId: String, maker: String = "Nissan"): TargetEditState {
        val profile = profiles.value.find { it.id == profileId }
            ?: return TargetEditState()
        val paths = repo.decodePaths(profile)
        val preview = repo.resolveMakerHub(profile, maker)
        return TargetEditState(
            profile = profile,
            baseUrl = profile.baseUrl,
            makerHub = paths.makerHub,
            partsHub = paths.partsHub,
            previewUrl = preview,
            cloudflareMode = profile.cloudflareMode,
            flaresolverrUrl = profile.flaresolverrUrl,
        )
    }

    fun saveEdit(
        profileId: String,
        baseUrl: String,
        makerHub: String,
        partsHub: String,
        cloudflareMode: String,
        flaresolverrUrl: String,
    ) {
        viewModelScope.launch {
            val existing = repo.getProfile(profileId) ?: return@launch
            val paths = repo.decodePaths(existing).copy(
                makerHub = makerHub,
                partsHub = partsHub,
            )
            repo.saveProfile(
                existing.copy(
                    baseUrl = baseUrl.trim().trimEnd('/'),
                    pathsJson = repo.encodePaths(paths),
                    cloudflareMode = cloudflareMode.trim().ifBlank { "auto" },
                    flaresolverrUrl = flaresolverrUrl.trim().ifBlank { existing.flaresolverrUrl },
                ),
            )
        }
    }

    fun addTarget(
        displayName: String,
        engine: String,
        baseUrl: String,
        makerHub: String,
        partsHub: String,
        cloudflareMode: String,
        flaresolverrUrl: String,
        onDone: (SiteProfileEntity) -> Unit,
    ) {
        viewModelScope.launch {
            val created = repo.createCustomProfile(
                displayName = displayName,
                engine = engine,
                baseUrl = baseUrl,
                makerHub = makerHub,
                partsHub = partsHub,
                cloudflareMode = cloudflareMode,
                flaresolverrUrl = flaresolverrUrl,
            )
            onDone(created)
        }
    }

    fun deleteCustom(profileId: String) {
        viewModelScope.launch { repo.deleteCustomProfile(profileId) }
    }

    fun previewMakerHub(baseUrl: String, makerHub: String, maker: String = "Nissan"): String {
        val paths = ProfilePathsJson(makerHub = makerHub)
        val slug = paths.makerSlugMap[maker] ?: maker.lowercase()
        return baseUrl.trimEnd('/') + makerHub.replace("{maker_slug}", slug)
    }
}
