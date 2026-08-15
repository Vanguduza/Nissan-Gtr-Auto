package co.zw.nissangtr.catalogapk.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Direct HTTP by default. In `auto` mode, FlareSolverr is used only when a
 * Cloudflare challenge is detected — then [lifecycle] auto-ensures the host sidecar.
 */
class SiteHttpClient(
    private val cloudflareMode: String,
    private var flaresolverrUrl: String,
    private val lifecycle: FlareSolverrLifecycle? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        val mode = cloudflareMode.lowercase()
        if (mode == "always") {
            return@withContext fetchViaFlare(url, ensureFirst = true)
        }
        val direct = directFetch(url)
        if (mode == "off") return@withContext direct
        // auto
        if (direct.ok && !CloudflareDetector.looksLikeChallenge(direct.statusCode, direct.html, direct.headers)) {
            return@withContext direct
        }
        val viaFlare = fetchViaFlare(url, ensureFirst = true)
        if (viaFlare.ok) return@withContext viaFlare
        // Sidecar missing: keep the direct result so callers can proceed or show HTTP error.
        if (!direct.ok && viaFlare.error?.contains("unreachable", ignoreCase = true) == true) {
            return@withContext viaFlare.copy(
                error = viaFlare.error,
                html = direct.html,
                statusCode = direct.statusCode,
            )
        }
        if (direct.html.isNotBlank()) return@withContext direct
        return@withContext viaFlare
    }

    private suspend fun fetchViaFlare(url: String, ensureFirst: Boolean): FetchResult {
        var api = FlareSolverrLifecycle.normalizeApi(flaresolverrUrl)
        if (ensureFirst && lifecycle != null) {
            val ensure = lifecycle.ensure(api)
            if (ensure.ok) {
                api = ensure.resolvedUrl
                flaresolverrUrl = api
            } else if (!FlareSolverrClient(api).healthOk()) {
                return FetchResult(
                    ok = false,
                    statusCode = 0,
                    html = "",
                    viaFlareSolverr = true,
                    error = ensure.message,
                    flaresolverrUrlUsed = api,
                )
            }
        } else if (!FlareSolverrClient(api).healthOk() && lifecycle != null) {
            val ensure = lifecycle.ensure(api)
            if (ensure.ok) {
                api = ensure.resolvedUrl
                flaresolverrUrl = api
            }
        }
        val result = FlareSolverrClient(api).fetchHtml(url)
        return result.copy(flaresolverrUrlUsed = api)
    }

    private fun directFetch(url: String): FetchResult {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "GTR-CatalogApk/0.3 (+local; discovery)")
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
