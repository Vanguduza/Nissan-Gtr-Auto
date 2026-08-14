package co.zw.nissangtr.catalogapk.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SiteHttpClient(
    private val cloudflareMode: String,
    private val flaresolverrUrl: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val flare by lazy { FlareSolverrClient(flaresolverrUrl) }

    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        val mode = cloudflareMode.lowercase()
        if (mode == "always") {
            return@withContext flare.fetchHtml(url)
        }
        val direct = directFetch(url)
        if (mode == "off") return@withContext direct
        // auto
        if (direct.ok && !CloudflareDetector.looksLikeChallenge(direct.statusCode, direct.html, direct.headers)) {
            return@withContext direct
        }
        if (!flare.healthOk()) {
            return@withContext direct.copy(
                ok = false,
                error = direct.error
                    ?: "Cloudflare detected but FlareSolverr unreachable at $flaresolverrUrl",
            )
        }
        flare.fetchHtml(url)
    }

    private fun directFetch(url: String): FetchResult {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "GTR-CatalogApk/0.2 (+local; discovery)")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                val headers = resp.headers.toMultimap().mapValues { it.value.joinToString(",") }
                FetchResult(
                    ok = resp.isSuccessful && html.isNotBlank(),
                    statusCode = resp.code,
                    html = html,
                    viaFlareSolverr = false,
                    headers = headers,
                    error = if (!resp.isSuccessful) "HTTP ${resp.code}" else null,
                )
            }
        }.getOrElse {
            FetchResult(ok = false, statusCode = 0, html = "", viaFlareSolverr = false, error = it.message)
        }
    }
}
