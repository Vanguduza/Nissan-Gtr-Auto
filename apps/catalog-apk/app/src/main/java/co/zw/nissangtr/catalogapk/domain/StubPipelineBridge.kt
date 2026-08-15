package co.zw.nissangtr.catalogapk.domain

import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Phase 0 stub — simulates crawl progress and writes sample quality artifacts
 * so the UI quality strip works before Chaquopy embed.
 */
class StubPipelineBridge : PipelineBridge {
    override suspend fun run(
        job: JobEntity,
        profile: SiteProfileEntity,
        argv: List<String>,
        shouldPause: suspend () -> Boolean,
        onHeartbeat: suspend () -> Unit,
    ): PipelineResult {
        val makerSlug = job.maker.lowercase()
        File(job.outRoot).mkdirs()
        File(job.outRoot).parentFile?.mkdirs()
        val bundleDir = File(job.outRoot, "$makerSlug/bundle")
        bundleDir.mkdirs()

        File(job.outRoot, "profile_snapshot.json").writeText(
            OrchestratorArgBuilder.profileSnapshotJson(profile),
        )
        File(job.outRoot, "argv.txt").writeText(argv.joinToString("\n"))

        val steps = listOf("probe", "crawl", "parse", "transform", "filter")
        for (step in steps) {
            if (shouldPause()) return PipelineResult.PAUSED
            delay(600)
            onHeartbeat()
            File(bundleDir, "progress.txt").appendText("$step\n")
        }

        if (shouldPause()) return PipelineResult.PAUSED

        val chassis = job.chassisCodesCsv
        val qualityReport = buildJsonObject {
            put("job_id", job.id)
            put("maker", job.maker)
            put("chassis", chassis)
            put("sections", 42)
            put("diagrams", 18)
            put("publishable_variants", 12)
            put("uncategorized", 3)
            put("engine_fill_pct", 87)
            put("stub", true)
        }
        File(bundleDir, "quality_report.json").writeText(
            json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), qualityReport),
        )

        val attrsAudit = buildJsonObject {
            put("chassis", chassis)
            put("variants_checked", 12)
            put("engine_present", 10)
            put("engine_missing", 2)
            put("sample_missing_engine", "T31-X-TRAIL-2010")
            put("stub", true)
        }
        File(bundleDir, "attrs_audit.json").writeText(
            json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), attrsAudit),
        )

        return PipelineResult.COMPLETE
    }

    companion object {
        private val json = Json { prettyPrint = true }
    }
}
