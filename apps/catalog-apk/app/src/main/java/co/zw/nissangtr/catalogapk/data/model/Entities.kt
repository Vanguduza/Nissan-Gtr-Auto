package co.zw.nissangtr.catalogapk.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_profiles")
data class SiteProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val engine: String,
    val baseUrl: String,
    val pathsJson: String,
    val cloudflareMode: String,
    val flaresolverrUrl: String,
    val isPreset: Boolean,
)

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val maker: String,
    val modelSlug: String = "",
    val modelDisplayName: String = "",
    val chassisCodesCsv: String,
    val desiredState: String,
    val status: String,
    val outRoot: String,
    val htmlDropped: Long,
    val htmlRetained: Long,
    val lastHeartbeat: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?,
)

@Entity(tableName = "supabase_projects")
data class SupabaseProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    /** Placeholder scaffold — use SecurePrefs wrapper in Phase 3. */
    val anonKeyEncrypted: String,
)
