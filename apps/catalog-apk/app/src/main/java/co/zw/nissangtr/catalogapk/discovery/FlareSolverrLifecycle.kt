package co.zw.nissangtr.catalogapk.discovery

import android.content.Context
import android.util.Log
import co.zw.nissangtr.catalogapk.data.prefs.AppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Zero-config FlareSolverr lifecycle. Tries loopback / emulator agent + API
 * candidates automatically; optional prefs only override defaults (no UI required).
 */
class FlareSolverrLifecycle(
    private val context: Context,
    private val preferences: AppPreferences? = null,
) {
    data class EnsureResult(
        val ok: Boolean,
        val resolvedUrl: String,
        val message: String,
        val startedCompanion: Boolean = false,
        val agentUsed: Boolean = false,
    )

    suspend fun ensure(
        preferredUrl: String = DEFAULT_FLARE_API,
        attempts: Int = 8,
    ): EnsureResult {
        val agentToken = preferences?.sidecarAgentToken?.first()?.trim()
            ?.ifBlank { null }
            ?: DEFAULT_AGENT_TOKEN
        val prefAgent = preferences?.sidecarAgentUrl?.first()?.trim().orEmpty()
        val extraHosts = preferences?.sidecarExtraHosts?.first().orEmpty()
        val agentCandidates = agentCandidateUrls(prefAgent)

        var startedViaAgent = false
        var agentMessage: String? = null
        var resolvedFromAgent: String? = null

        for (agentUrl in agentCandidates) {
            val agent = FlareSolverrSidecarAgent(agentUrl, agentToken)
            val ensure = agent.ensure()
            agentMessage = ensure.message
            if (ensure.ok) {
                startedViaAgent = startedViaAgent || ensure.started
                val fromAgent = ensure.flaresolverrUrl?.trim().orEmpty()
                if (fromAgent.isNotBlank()) {
                    val api = normalizeApi(fromAgent)
                    if (FlareSolverrClient(api).healthOk()) {
                        return EnsureResult(
                            ok = true,
                            resolvedUrl = api,
                            message = ensure.message ?: "FlareSolverr healthy via sidecar agent",
                            startedCompanion = startedViaAgent,
                            agentUsed = true,
                        )
                    }
                    resolvedFromAgent = api
                }
            } else {
                Log.i(TAG, "Agent $agentUrl ensure: ${ensure.message}")
            }
        }

        val candidates = candidateUrls(preferredUrl, extraHosts, resolvedFromAgent)
        repeat(attempts) { attempt ->
            for (url in candidates) {
                if (FlareSolverrClient(url).healthOk()) {
                    return EnsureResult(
                        ok = true,
                        resolvedUrl = url,
                        message = buildString {
                            append("FlareSolverr healthy at $url")
                            if (agentMessage != null) append(" (agent: $agentMessage)")
                        },
                        startedCompanion = startedViaAgent,
                        agentUsed = startedViaAgent || agentMessage != null,
                    )
                }
            }
            if (attempt == 2) {
                for (agentUrl in agentCandidates) {
                    val retry = FlareSolverrSidecarAgent(agentUrl, agentToken).ensure()
                    agentMessage = retry.message
                    startedViaAgent = startedViaAgent || retry.started
                    retry.flaresolverrUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
                        resolvedFromAgent = normalizeApi(it)
                    }
                }
            }
            delay(1_500)
        }

        return EnsureResult(
            ok = false,
            resolvedUrl = normalizeApi(preferredUrl),
            message = buildString {
                append("FlareSolverr unreachable (auto). ")
                if (agentMessage != null) append("Last agent: $agentMessage. ")
                append("On the host leave sidecar running: apps/catalog-apk/sidecar/start.ps1 -Agent ")
                append("(+ adb-reverse.ps1 for USB). Tried: ")
                append(candidates.joinToString())
            },
            startedCompanion = startedViaAgent,
            agentUsed = agentMessage != null,
        )
    }

    companion object {
        private const val TAG = "FlareSolverrLifecycle"
        const val DEFAULT_FLARE_API = "http://127.0.0.1:8191/v1"
        const val DEFAULT_AGENT_TOKEN = "catalog-apk-dev"

        fun normalizeApi(url: String): String {
            val trimmed = url.trim().ifBlank { DEFAULT_FLARE_API }
            val base = trimmed.trimEnd('/')
            return if (base.endsWith("/v1")) base else "$base/v1"
        }

        fun agentCandidateUrls(preferred: String = ""): List<String> {
            val pref = preferred.trim().trimEnd('/')
            return listOfNotNull(
                pref.takeIf { it.isNotBlank() },
                "http://127.0.0.1:8192",
                "http://10.0.2.2:8192",
                "http://localhost:8192",
            ).distinct()
        }

        fun candidateUrls(
            preferred: String,
            extraHostsCsv: String = "",
            resolvedFromAgent: String? = null,
        ): List<String> {
            val base = normalizeApi(preferred)
            val extras = extraHostsCsv.split(',', ' ', '\n', '\t')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { normalizeApi(it) }
            return (
                listOfNotNull(resolvedFromAgent?.let { normalizeApi(it) }) +
                    listOf(
                        base,
                        DEFAULT_FLARE_API,
                        "http://10.0.2.2:8191/v1",
                        "http://localhost:8191/v1",
                    ) + extras
                ).distinct()
        }
    }
}
