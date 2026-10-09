package co.zw.nissangtr.catalogapk.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Client for the host FlareSolverr control agent (`apps/catalog-apk/sidecar/agent`).
 * POST /v1/ensure starts Docker Compose when the Chromium solver is down.
 */
class FlareSolverrSidecarAgent(
    private val agentBaseUrl: String,
    private val token: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class EnsureResponse(
        val ok: Boolean = false,
        val started: Boolean = false,
        @SerialName("flaresolverr_url") val flaresolverrUrl: String? = null,
        val message: String? = null,
    )

    @Serializable
    data class HealthResponse(
        val ok: Boolean = false,
        @SerialName("flaresolverr_healthy") val flaresolverrHealthy: Boolean = false,
        @SerialName("flaresolverr_url") val flaresolverrUrl: String? = null,
    )

    suspend fun health(): HealthResponse? = withContext(Dispatchers.IO) {
        val base = agentBaseUrl.trimEnd('/')
        val req = Request.Builder().url("$base/health").get().build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use null
                json.decodeFromString(HealthResponse.serializer(), body)
            }
        }.getOrNull()
    }

    suspend fun ensure(): EnsureResponse = withContext(Dispatchers.IO) {
        val base = agentBaseUrl.trimEnd('/')
        val req = Request.Builder()
            .url("$base/v1/ensure")
            .addHeader("X-Sidecar-Token", token)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@use EnsureResponse(ok = false, message = "Agent HTTP ${resp.code}")
                }
                runCatching {
                    json.decodeFromString(EnsureResponse.serializer(), body)
                }.getOrElse {
                    EnsureResponse(ok = false, message = "Agent HTTP ${resp.code}: ${body.take(160)}")
                }
            }
        }.getOrElse {
            EnsureResponse(ok = false, message = it.message ?: "agent ensure failed")
        }
    }
}