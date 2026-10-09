package co.zw.nissangtr.catalogapk.domain

import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import co.zw.nissangtr.catalogapk.data.profile.ProfileJsonCodec
import co.zw.nissangtr.catalogapk.data.profile.SiteProfileJson
import co.zw.nissangtr.catalogapk.data.profile.toJson

/**
 * Maps a crawl job to argv for the embedded Python orchestrator (Chaquopy).
 */
object OrchestratorArgBuilder {
    // Include upload so diagram PNGs are fetched into out/*/diagrams when present.
    private const val DEFAULT_PHASES = "crawl,parse,transform,pcdb,filter,upload"

    fun buildArgv(
        job: JobEntity,
        profile: SiteProfileEntity,
        flaresolverrUrlOverride: String? = null,
        forceFlareSolverr: Boolean = false,
        maxPages: Int? = null,
    ): List<String> {
        val chassis = job.chassisCodesCsv.trim()
        val engine = profile.engine.lowercase()
        return when (engine) {
            "partsouq" -> buildPartsouqArgv(job, profile, flaresolverrUrlOverride, forceFlareSolverr, maxPages)
            "megazip" -> buildMegazipArgv(job, profile, flaresolverrUrlOverride, forceFlareSolverr, maxPages, chassis)
            // 7zap / catcar / japancats / japan_parts / custom → path-template adapter
            else -> buildCustomArgv(job, profile, flaresolverrUrlOverride, forceFlareSolverr, maxPages)
        }
    }

    private fun buildMegazipArgv(
        job: JobEntity,
        profile: SiteProfileEntity,
        flaresolverrUrlOverride: String?,
        forceFlareSolverr: Boolean,
        maxPages: Int?,
        chassis: String,
    ): List<String> {
        val argv = mutableListOf(
            "-m",
            "data_pipeline.megazip_catalog_orchestrator",
            "--out-root",
            job.outRoot,
            "--makers",
            job.maker,
            "--phase",
            DEFAULT_PHASES,
            "--drop-html-after-parse",
            "--prune-html-cache",
        )
        if (chassis.isNotEmpty()) {
            argv += listOf("--single-chassis", chassis, "--no-nissan-two-phase")
        }
        // Fail job when filter yields hierarchy-only / 0 publishable variants.
        argv += "--strict-gate"
        if (maxPages != null && maxPages > 0) {
            argv += listOf("--max-pages", maxPages.toString())
        }
        appendFlare(argv, profile, flaresolverrUrlOverride, forceFlareSolverr)
        argv += listOf("--profile-snapshot", "${job.outRoot}/profile_snapshot.json")
        return argv
    }

    private fun buildPartsouqArgv(
        job: JobEntity,
        profile: SiteProfileEntity,
        flaresolverrUrlOverride: String?,
        forceFlareSolverr: Boolean,
        maxPages: Int?,
    ): List<String> {
        val flare = flaresolverrUrlOverride?.ifBlank { null } ?: profile.flaresolverrUrl
        val argv = mutableListOf(
            "-m",
            "data_pipeline.partsouq_catalog_orchestrator",
            "--out-root",
            job.outRoot,
            "--makers",
            job.maker,
            "--download-diagrams",
        )
        // Cap => smoke; omit max-pages => until-complete unlimited depth
        if (maxPages != null && maxPages > 0) {
            argv += listOf("--no-until-complete", "--max-pages", maxPages.toString())
        }
        if (job.chassisCodesCsv.isNotBlank()) {
            // Chassis-scoped priority only (not the full priority_chassis.json roster)
            argv += listOf("--single-chassis", job.chassisCodesCsv.trim())
        }
        if (flare.isNotBlank()) {
            argv += listOf("--flaresolverr-url", flare)
        }
        if (profile.cloudflareMode.equals("off", ignoreCase = true) && !forceFlareSolverr) {
            argv += "--skip-flaresolverr-check"
        }
        argv += listOf("--profile-snapshot", "${job.outRoot}/profile_snapshot.json")
        return argv
    }

    private fun buildCustomArgv(
        job: JobEntity,
        profile: SiteProfileEntity,
        flaresolverrUrlOverride: String?,
        forceFlareSolverr: Boolean,
        maxPages: Int?,
    ): List<String> {
        val argv = mutableListOf(
            "-m",
            "data_pipeline.custom_catalog_orchestrator",
            "--out-root",
            job.outRoot,
            "--makers",
            job.maker,
            "--model-slug",
            job.modelSlug,
            "--phase",
            "crawl,transform,filter",
        )
        if (job.chassisCodesCsv.isNotBlank()) {
            argv += listOf("--single-chassis", job.chassisCodesCsv.trim())
        }
        if (maxPages != null && maxPages > 0) {
            argv += listOf("--max-pages", maxPages.toString())
        }
        appendFlare(argv, profile, flaresolverrUrlOverride, forceFlareSolverr)
        argv += listOf("--profile-snapshot", "${job.outRoot}/profile_snapshot.json")
        return argv
    }

    private fun appendFlare(
        argv: MutableList<String>,
        profile: SiteProfileEntity,
        flaresolverrUrlOverride: String?,
        forceFlareSolverr: Boolean,
    ) {
        val flareUrl = flaresolverrUrlOverride?.ifBlank { null } ?: profile.flaresolverrUrl
        val mode = profile.cloudflareMode.lowercase()
        val always = mode == "always" || forceFlareSolverr
        if ((always || mode == "auto") && flareUrl.isNotBlank()) {
            argv += listOf("--flaresolverr-url", flareUrl)
        }
        if (always && flareUrl.isNotBlank()) {
            argv += "--force-flaresolverr"
        }
    }

    fun buildPythonCommand(job: JobEntity, profile: SiteProfileEntity): String {
        return "python ${buildArgv(job, profile).joinToString(" ")}"
    }

    fun profileSnapshotJson(profile: SiteProfileEntity): String {
        val json = profile.toJson()
        return ProfileJsonCodec.json.encodeToString(SiteProfileJson.serializer(), json)
    }
}
