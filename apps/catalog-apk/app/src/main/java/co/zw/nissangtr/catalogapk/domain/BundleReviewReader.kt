package co.zw.nissangtr.catalogapk.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class BundleSectionRow(
    val key: String,
    val slug: String,
    val name: String,
    val modelSlug: String,
    val variantSlug: String,
    val diagramCount: Int,
)

data class BundleDiagramRow(
    val key: String,
    val slug: String,
    val title: String,
    val sectionSlug: String,
    val modelSlug: String,
    val variantSlug: String,
    val storagePath: String,
    val localPngPath: String?,
    val hotspotCount: Int,
    val diagramKind: String,
)

data class BundleFitmentRow(
    val oem: String,
    val pnc: String,
    val callout: String,
    val description: String,
    val quantity: String,
    val bboxLabel: String,
)

data class BundleReviewModel(
    val outRoot: String,
    val maker: String,
    val makerSlug: String,
    val sections: List<BundleSectionRow>,
    val diagrams: List<BundleDiagramRow>,
    val gateOk: Boolean,
    val qualityPublishable: Boolean,
    val qualityReportPresent: Boolean,
    val diagramPngCount: Int,
)

object BundleReviewReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun isReviewable(outRoot: String, maker: String): Boolean {
        val makerSlug = maker.lowercase()
        val bundleDir = File(outRoot, "$makerSlug/bundle")
        if (File(outRoot, "bundle_quality.txt").isFile) return true
        if (File(bundleDir, "quality_report.json").isFile) return true
        val markers = listOf(
            "catalog_diagrams.json",
            "catalog_sections.json",
            "part_fitment.json",
            "hierarchy_bundle.json",
            "bundle.json",
            "catalog_hierarchy.json",
        )
        if (markers.any { File(bundleDir, it).isFile }) return true
        val diagramsDir = File(outRoot, "$makerSlug/diagrams")
        return diagramsDir.isDirectory &&
            diagramsDir.listFiles()?.any { it.isFile && it.extension.equals("png", ignoreCase = true) } == true
    }

    fun read(outRoot: String, maker: String): BundleReviewModel {
        val makerSlug = maker.lowercase()
        val bundleDir = File(outRoot, "$makerSlug/bundle")
        val diagramsDir = File(outRoot, "$makerSlug/diagrams")
        val gateOk = File(outRoot, "bundle_quality.txt").takeIf { it.isFile }
            ?.readText()
            ?.lineSequence()
            ?.any { it.trim() == "ok=true" } == true
        val qualityFile = File(bundleDir, "quality_report.json")
        val snap = BundlePickerReader.read(outRoot, maker)
        val qualityPublishable = if (qualityFile.isFile) {
            runCatching {
                json.parseToJsonElement(qualityFile.readText()).jsonObject["publishable"]
                    ?.jsonPrimitive?.booleanOrNull == true
            }.getOrDefault(false) || snap.qualityPublishable
        } else {
            snap.qualityPublishable
        }

        val root = snap.bundleJson ?: JsonObject(emptyMap())
        val sectionsJson = root.arrayOrEmpty("catalog_sections")
        val diagramsJson = root.arrayOrEmpty("catalog_diagrams")

        val diagrams = diagramsJson.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val storagePath = o.str("storage_path")
            val sectionSlug = o.str("section_slug")
            val modelSlug = o.str("model_slug")
            val variantSlug = o.str("variant_slug")
            val slug = o.str("slug").ifBlank {
                storagePath.substringAfterLast('/').substringBeforeLast('.')
            }
            val key = storagePath.ifBlank { "$modelSlug::$variantSlug::$sectionSlug::$slug" }
            BundleDiagramRow(
                key = key,
                slug = slug,
                title = o.str("title").ifBlank { sectionSlug.ifBlank { slug } },
                sectionSlug = sectionSlug,
                modelSlug = modelSlug,
                variantSlug = variantSlug,
                storagePath = storagePath,
                localPngPath = resolveDiagramPng(outRoot, makerSlug, storagePath)?.absolutePath,
                hotspotCount = o.int("hotspot_count"),
                diagramKind = o.str("diagram_kind").ifBlank { "—" },
            )
        }

        val diagramCounts = diagrams.groupingBy {
            sectionKey(it.modelSlug, it.variantSlug, it.sectionSlug)
        }.eachCount()

        val sections = sectionsJson.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val slug = o.str("slug")
            val modelSlug = o.str("model_slug")
            val variantSlug = o.str("variant_slug")
            val key = sectionKey(modelSlug, variantSlug, slug)
            BundleSectionRow(
                key = key,
                slug = slug,
                name = o.str("name").ifBlank { slug },
                modelSlug = modelSlug,
                variantSlug = variantSlug,
                diagramCount = diagramCounts[key] ?: 0,
            )
        }.sortedWith(compareBy({ it.modelSlug }, { it.variantSlug }, { it.name }))

        val effectiveSections = sections.ifEmpty {
            diagrams
                .groupBy { sectionKey(it.modelSlug, it.variantSlug, it.sectionSlug) }
                .map { (key, rows) ->
                    val first = rows.first()
                    BundleSectionRow(
                        key = key,
                        slug = first.sectionSlug,
                        name = first.sectionSlug.ifBlank { "Diagrams" },
                        modelSlug = first.modelSlug,
                        variantSlug = first.variantSlug,
                        diagramCount = rows.size,
                    )
                }
                .sortedWith(compareBy({ it.modelSlug }, { it.variantSlug }, { it.name }))
        }

        val pngCount = diagramsDir.takeIf { it.isDirectory }
            ?.listFiles()
            ?.count { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?: 0

        return BundleReviewModel(
            outRoot = outRoot,
            maker = maker,
            makerSlug = makerSlug,
            sections = effectiveSections,
            diagrams = diagrams,
            gateOk = gateOk,
            qualityPublishable = qualityPublishable,
            qualityReportPresent = qualityFile.isFile,
            diagramPngCount = pngCount,
        )
    }

    fun fitmentsForDiagram(outRoot: String, maker: String, diagram: BundleDiagramRow): List<BundleFitmentRow> {
        val snap = BundlePickerReader.read(outRoot, maker)
        val root = snap.bundleJson ?: return emptyList()
        val parts = root.arrayOrEmpty("catalog_diagram_parts")
        val fitments = root.arrayOrEmpty("part_fitment")
        val fromParts = parts.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val path = o.str("diagram_path")
            val section = o.str("section_slug")
            val match = when {
                diagram.storagePath.isNotBlank() && path == diagram.storagePath -> true
                diagram.slug.isNotBlank() && o.str("diagram_slug") == diagram.slug &&
                    section == diagram.sectionSlug -> true
                else -> false
            }
            if (!match) return@mapNotNull null
            BundleFitmentRow(
                oem = o.str("oem_part_number"),
                pnc = "",
                callout = o.str("callout_ref"),
                description = o.str("description"),
                quantity = o.str("quantity"),
                bboxLabel = bboxLabel(o),
            )
        }
        if (fromParts.isNotEmpty()) return fromParts

        return fitments.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val path = o.str("diagram_path")
            if (diagram.storagePath.isBlank() || path != diagram.storagePath) return@mapNotNull null
            BundleFitmentRow(
                oem = o.str("oem_part_number"),
                pnc = o.str("pnc_code"),
                callout = "",
                description = "",
                quantity = "",
                bboxLabel = bboxLabel(o),
            )
        }
    }

    fun findDiagram(model: BundleReviewModel, key: String): BundleDiagramRow? =
        model.diagrams.firstOrNull { it.key == key }

    fun diagramsForSection(model: BundleReviewModel, sectionKey: String): List<BundleDiagramRow> =
        model.diagrams.filter {
            sectionKey(it.modelSlug, it.variantSlug, it.sectionSlug) == sectionKey
        }

    fun sectionKey(modelSlug: String, variantSlug: String, sectionSlug: String): String =
        "$modelSlug::$variantSlug::$sectionSlug"

    fun resolveDiagramPng(outRoot: String, makerSlug: String, storagePath: String): File? {
        if (storagePath.isBlank()) return null
        val name = File(storagePath).name
        val candidates = listOf(
            File(outRoot, "$makerSlug/diagrams/$name"),
            File(outRoot, "diagrams/$name"),
            File(outRoot, storagePath),
            File(outRoot, "$makerSlug/$storagePath"),
            File(storagePath),
        )
        return candidates.firstOrNull { it.isFile }
    }

    private fun bboxLabel(o: JsonObject): String {
        val x = o.str("bbox_x")
        val y = o.str("bbox_y")
        val w = o.str("bbox_width")
        val h = o.str("bbox_height")
        if (x.isBlank() && y.isBlank()) return ""
        return "bbox $x,$y ${w}×$h"
    }

    private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
        this[key]?.jsonArray ?: JsonArray(emptyList())

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: 0
}
