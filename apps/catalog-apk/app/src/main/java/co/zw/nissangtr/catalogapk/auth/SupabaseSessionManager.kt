package co.zw.nissangtr.catalogapk.auth

import android.content.Context
import android.content.Intent
import co.zw.nissangtr.catalogapk.data.prefs.SecurePrefs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Multi-project Supabase auth — Custom Tabs / deep link + JWT Edge import.
 * Never stores or uses a service-role key.
 */
class SupabaseSessionManager(
    @Suppress("unused") private val context: Context,
    private val securePrefs: SecurePrefs,
) {
    private val clients = mutableMapOf<String, SupabaseClient>()
    private val _signedInProjectId = MutableStateFlow<String?>(securePrefs.getSessionProject())
    val signedInProjectId: StateFlow<String?> = _signedInProjectId.asStateFlow()

    fun clientFor(projectId: String, url: String, anonKey: String): SupabaseClient {
        securePrefs.putAnonKey(projectId, anonKey)
        return clients.getOrPut(projectId) {
            createSupabaseClient(
                supabaseUrl = url.trimEnd('/'),
                supabaseKey = anonKey,
            ) {
                install(Auth) {
                    scheme = "catalogapk"
                    host = "auth-callback"
                    defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
                }
                install(Postgrest)
                install(Storage)
                install(Functions)
            }
        }
    }

    suspend fun signInEmail(
        projectId: String,
        url: String,
        anonKey: String,
        email: String,
        password: String,
    ) {
        val client = clientFor(projectId, url, anonKey)
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        _signedInProjectId.value = projectId
        securePrefs.putSessionProject(projectId)
    }

    suspend fun signOut(projectId: String) {
        clients[projectId]?.auth?.signOut()
        if (_signedInProjectId.value == projectId) {
            _signedInProjectId.value = null
        }
    }

    suspend fun handleAuthIntent(intent: Intent?, projectId: String, url: String, anonKey: String) {
        if (intent?.data == null) return
        val client = clientFor(projectId, url, anonKey)
        client.handleDeeplinks(intent)
        if (client.auth.currentSessionOrNull() != null) {
            _signedInProjectId.value = projectId
            securePrefs.putSessionProject(projectId)
        }
    }

    fun currentAccessToken(projectId: String): String? =
        clients[projectId]?.auth?.currentAccessTokenOrNull()

    fun isSignedIn(projectId: String): Boolean =
        clients[projectId]?.auth?.currentSessionOrNull() != null

    suspend fun invokeHierarchyImport(
        projectId: String,
        url: String,
        anonKey: String,
        payload: JsonObject,
    ): Result<String> {
        val client = clientFor(projectId, url, anonKey)
        if (client.auth.currentAccessTokenOrNull() == null) {
            return Result.failure(IllegalStateException("Sign in required before import"))
        }
        return runCatching {
            client.functions.invoke(
                function = "catalog-hierarchy-import",
                body = payload,
            )
            "imported"
        }
    }

    companion object {
        fun buildImportPayload(
            jobId: String,
            maker: String,
            selectedVariantKeys: List<Pair<String, String>>,
            bundleJson: JsonObject,
        ): JsonObject = buildJsonObject {
            put("job_id", jobId)
            put("maker", maker)
            put("complete_only", true)
            put("strict_gate", true)
            putJsonArray("selected_variants") {
                selectedVariantKeys.forEach { (model, variant) ->
                    add(
                        buildJsonObject {
                            put("model_slug", model)
                            put("variant_slug", variant)
                        },
                    )
                }
            }
            put("bundle", bundleJson)
        }
    }
}
