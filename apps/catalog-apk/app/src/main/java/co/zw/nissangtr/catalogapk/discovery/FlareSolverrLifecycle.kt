package co.zw.nissangtr.catalogapk.discovery

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay

/**
 * Live FlareSolverr lifecycle: health probe, alternate localhost/emulator hosts,
 * optional companion Intent, and clear failure when CF needs a sidecar.
 */
class FlareSolverrLifecycle(
    private val context: Context,
) {
    data class EnsureResult(
        val ok: Boolean,
        val resolvedUrl: String,
        val message: String,
        val startedCompanion: Boolean = false,
    )

    suspend fun ensure(preferredUrl: String, attempts: Int = 8): EnsureResult {
        val candidates = candidateUrls(preferredUrl)
        repeat(attempts) { attempt ->
            for (url in candidates) {
                val client = FlareSolverrClient(url)
                if (client.healthOk()) {
                    return EnsureResult(
                        ok = true,
                        resolvedUrl = url,
                        message = "FlareSolverr healthy at $url",
                    )
                }
            }
            if (attempt == 0) {
                tryStartCompanion()
            }
            delay(1_500)
        }
        return EnsureResult(
            ok = false,
            resolvedUrl = preferredUrl,
            message = "FlareSolverr unreachable. Start LAN sidecar " +
                "(docker compose --profile scrape) or Termux/companion on " +
                "${candidateUrls(preferredUrl).joinToString()}",
        )
    }

    fun tryStartCompanion(): Boolean {
        // Prefer an explicit companion package if installed; otherwise open docs URI.
        val companion = Intent().apply {
            setClassName(
                "co.zw.nissangtr.flaresolverr",
                "co.zw.nissangtr.flaresolverr.StartService",
            )
            action = Intent.ACTION_VIEW
        }
        return try {
            context.startService(companion)
            true
        } catch (e: Exception) {
            Log.i(TAG, "No FlareSolverr companion service: ${e.message}")
            runCatching {
                val view = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/FlareSolverr/FlareSolverr"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(view)
            }
            false
        }
    }

    companion object {
        private const val TAG = "FlareSolverrLifecycle"

        fun candidateUrls(preferred: String): List<String> {
            val normalized = preferred.trim().ifBlank { "http://127.0.0.1:8191/v1" }
            val base = normalized.trimEnd('/').let {
                if (it.endsWith("/v1")) it else "$it/v1"
            }
            val alts = listOf(
                base,
                "http://127.0.0.1:8191/v1",
                "http://10.0.2.2:8191/v1", // emulator → host
                "http://localhost:8191/v1",
            )
            return alts.distinct()
        }
    }
}
