package co.zw.nissangtr.catalogapk.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class QualitySnapshot(
    val sections: Int = 0,
    val diagrams: Int = 0,
    val publishableVariants: Int = 0,
    val uncategorized: Int = 0,
    val engineFillPct: Int = 0,
    val enginePresent: Int = 0,
    val engineMissing: Int = 0,
    val isStub: Boolean = false,
)

object QualityReportReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun readFromOutRoot(outRoot: String, maker: String): QualitySnapshot {
        val bundleDir = File(outRoot, "${maker.lowercase()}/bundle")
        val qualityFile = File(bundleDir, "quality_report.json")
        val attrsFile = File(bundleDir, "attrs_audit.json")
        if (!qualityFile.exists()) return QualitySnapshot()

        val quality = runCatching {
            json.decodeFromString<JsonObject>(qualityFile.readText())
        }.getOrNull() ?: return QualitySnapshot()

        val attrs = if (attrsFile.exists()) {
            runCatching { json.decodeFromString<JsonObject>(attrsFile.readText()) }.getOrNull()
        } else {
            null
        }

        return QualitySnapshot(
            sections = quality.int("sections"),
            diagrams = quality.int("diagrams"),
            publishableVariants = quality.int("publishable_variants"),
            uncategorized = quality.int("uncategorized"),
            engineFillPct = quality.int("engine_fill_pct"),
            enginePresent = attrs?.int("engine_present") ?: 0,
            engineMissing = attrs?.int("engine_missing") ?: 0,
            isStub = quality["stub"]?.jsonPrimitive?.content == "true",
        )
    }

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0
}
