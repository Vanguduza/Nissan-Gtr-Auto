package co.zw.nissangtr.catalogapk.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class BundleVariantRow(
    val modelSlug: String,
    val variantSlug: String,
    val chassisCode: String,
    val publishable: Boolean,
    val explodedPassing: Int,
)

data class BundleSnapshot(
    val outRoot: String,
    val maker: String,
    val variants: List<BundleVariantRow>,
    val bundleJson: JsonObject?,
    val qualityPublishable: Boolean,
)

object BundlePickerReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(outRoot: String, maker: String): BundleSnapshot {
        val slug = maker.lowercase()
        val bundleDir = File(outRoot, "$slug/bundle")
        val variantFile = File(bundleDir, "variant_quality.json")
        val qualityFile = File(bundleDir, "quality_report.json")
        val hierarchyCandidates = listOf(
            File(bundleDir, "hierarchy_bundle.json"),
            File(bundleDir, "bundle.json"),
            File(bundleDir, "catalog_hierarchy.json"),
        )

        val variants = if (variantFile.exists()) {
            val root = runCatching { json.parseToJsonElement(variantFile.readText()) }.getOrNull()
            val arr: JsonArray? = when {
                root is JsonObject -> root["variants"]?.jsonArray
                root is JsonArray -> root
                else -> null
            }
            arr?.mapNotNull { el ->
                val o = el.jsonObject
                BundleVariantRow(
                    modelSlug = o.str("model_slug"),
                    variantSlug = o.str("variant_slug"),
                    chassisCode = o.str("chassis_code"),
                    publishable = o["publishable"]?.jsonPrimitive?.booleanOrNull == true,
                    explodedPassing = o["exploded_passing_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                )
            }.orEmpty()
        } else {
            emptyList()
        }

        val qualityPublishable = if (qualityFile.exists()) {
            runCatching {
                json.parseToJsonElement(qualityFile.readText()).jsonObject["publishable"]
                    ?.jsonPrimitive?.booleanOrNull == true
            }.getOrDefault(false)
        } else {
            false
        }

        val bundleJson = hierarchyCandidates.firstOrNull { it.exists() }?.let { file ->
            runCatching { json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
        } ?: synthesizeBundleStub(bundleDir, maker, variants)

        return BundleSnapshot(
            outRoot = outRoot,
            maker = maker,
            variants = variants,
            bundleJson = bundleJson,
            qualityPublishable = qualityPublishable,
        )
    }

    private fun synthesizeBundleStub(
        bundleDir: File,
        maker: String,
        variants: List<BundleVariantRow>,
    ): JsonObject {
        // Prefer real transform outputs when present as split files.
        val parts = listOf(
            "catalog_makers",
            "catalog_models",
            "catalog_variants",
            "catalog_sections",
            "catalog_diagrams",
            "catalog_diagram_parts",
            "vehicle_master",
            "pnc_categories",
            "part_fitment",
            "diagram_assets",
        )
        val obj = buildMap {
            put("maker", kotlinx.serialization.json.JsonPrimitive(maker))
            put("source_dir", kotlinx.serialization.json.JsonPrimitive(bundleDir.absolutePath))
            for (name in parts) {
                val f = File(bundleDir, "$name.json")
                if (f.exists()) {
                    runCatching {
                        put(name, json.parseToJsonElement(f.readText()))
                    }
                }
            }
            put(
                "selected_variant_hints",
                JsonArray(
                    variants.filter { it.publishable }.map {
                        JsonObject(
                            mapOf(
                                "model_slug" to kotlinx.serialization.json.JsonPrimitive(it.modelSlug),
                                "variant_slug" to kotlinx.serialization.json.JsonPrimitive(it.variantSlug),
                                "chassis_code" to kotlinx.serialization.json.JsonPrimitive(it.chassisCode),
                            ),
                        )
                    },
                ),
            )
        }
        return JsonObject(obj)
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}
