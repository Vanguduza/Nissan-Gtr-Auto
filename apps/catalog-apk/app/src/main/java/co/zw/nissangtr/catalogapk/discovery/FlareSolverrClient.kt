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

@Serializable
private data class FsRequest(
    val cmd: String = "request.get",
    val url: String,
    @SerialName("maxTimeout") val maxTimeout: Int = 60_000,
)

@Serializable
private data class FsResponse(
    val status: String? = null,
    val message: String? = null,
    val solution: FsSolution? = null,
)

@Serializable
private data class FsSolution(
    val url: String? = null,
    val status: Int? = null,
    val response: String? = null,
)

class FlareSolverrClient(
    private val apiUrl: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun healthOk(): Boolean = withContext(Dispatchers.IO) {
        val base = apiUrl.trimEnd('/').removeSuffix("/v1")
        val req = Request.Builder().url("$base/").get().build()
        runCatching { http.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    suspend fun fetchHtml(url: String): FetchResult = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(FsRequest.serializer(), FsRequest(url = url))
        val req = Request.Builder()
            .url(apiUrl.trimEnd('/').let { if (it.endsWith("/v1")) it else "$it/v1" })
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext FetchResult(
                    ok = false,
                    statusCode = resp.code,
                    html = body,
                    viaFlareSolverr = true,
                    error = "FlareSolverr HTTP ${resp.code}",
                )
            }
            val parsed = runCatching { json.decodeFromString(FsResponse.serializer(), body) }.getOrNull()
            val html = parsed?.solution?.response.orEmpty()
            val status = parsed?.solution?.status ?: 0
            FetchResult(
                ok = parsed?.status.equals("ok", ignoreCase = true) && html.isNotBlank(),
                statusCode = status,
                html = html,
                viaFlareSolverr = true,
                error = if (parsed?.status != "ok") parsed?.message else null,
            )
        }
    }
}

data class FetchResult(
    val ok: Boolean,
    val statusCode: Int,
    val html: String,
    val viaFlareSolverr: Boolean,
    val error: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
