package co.zw.nissangtr.management.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Mirrors packages/shared CatalogMaker — Megazip hierarchy. */
data class EpcMaker(
    val slug: String,
    val name: String,
    val sortOrder: Int = 0,
    val modelCount: Int? = null,
)

data class EpcModel(
    val slug: String,
    val displayName: String,
    val bodyType: String? = null,
    val sortKey: String,
    val yearStart: Int? = null,
    val yearEnd: Int? = null,
)

data class EpcVariant(
    val slug: String,
    val chassisCode: String,
    val frame: String? = null,
    val grade: String? = null,
    val salesRegion: String? = null,
    val yearLabel: String? = null,
    val engineCode: String? = null,
)

data class EpcSection(
    val slug: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val sortOrder: Int = 0,
)

data class EpcHotspot(
    val oem: String,
    val pncCode: String? = null,
    val bboxX: Double,
    val bboxY: Double,
    val bboxWidth: Double,
    val bboxHeight: Double,
)

data class EpcDiagramPart(
    val oemPartNumber: String,
    val pncCode: String? = null,
    val categoryName: String? = null,
    val subcategoryName: String? = null,
    val stockItemId: String? = null,
    val stockDescription: String? = null,
)

data class EpcDiagramResponse(
    val diagramSlug: String? = null,
    val diagramTitle: String? = null,
    val storagePath: String? = null,
    val imageUrl: String? = null,
    val hotspots: List<EpcHotspot> = emptyList(),
    val parts: List<EpcDiagramPart> = emptyList(),
)

internal fun parseEpcMakerList(raw: JsonElement): List<EpcMaker> =
    raw.asObjectList().mapNotNull { o ->
        val slug = o.str("slug") ?: return@mapNotNull null
        EpcMaker(
            slug = slug,
            name = o.str("name") ?: slug,
            sortOrder = o.int("sort_order") ?: 0,
            modelCount = o.int("model_count"),
        )
    }

internal fun parseEpcModelList(raw: JsonElement): List<EpcModel> =
    raw.asObjectList().mapNotNull { o ->
        val slug = o.str("slug") ?: return@mapNotNull null
        EpcModel(
            slug = slug,
            displayName = o.str("display_name") ?: slug,
            bodyType = o.str("body_type"),
            sortKey = o.str("sort_key") ?: slug,
            yearStart = o.int("year_start"),
            yearEnd = o.int("year_end"),
        )
    }

internal fun parseEpcVariantList(raw: JsonElement): List<EpcVariant> =
    raw.asObjectList().mapNotNull { o ->
        val slug = o.str("slug") ?: return@mapNotNull null
        EpcVariant(
            slug = slug,
            chassisCode = o.str("chassis_code") ?: slug,
            frame = o.str("frame"),
            grade = o.str("grade"),
            salesRegion = o.str("sales_region"),
            yearLabel = o.str("year_label"),
            engineCode = o.str("engine_code"),
        )
    }

internal fun parseEpcSectionList(raw: JsonElement): List<EpcSection> =
    raw.asObjectList().mapNotNull { o ->
        val slug = o.str("slug") ?: return@mapNotNull null
        EpcSection(
            slug = slug,
            name = o.str("name") ?: slug,
            thumbnailUrl = o.str("thumbnail_url"),
            sortOrder = o.int("sort_order") ?: 0,
        )
    }

internal fun parseEpcDiagram(raw: JsonElement): EpcDiagramResponse {
    val root = raw.jsonObjectOrNull() ?: return EpcDiagramResponse()
    val diagram = root["diagram"]?.jsonObjectOrNull()
    val hotspots = root["hotspots"]?.asObjectList().orEmpty().mapNotNull { o ->
        val oem = o.str("oem") ?: return@mapNotNull null
        EpcHotspot(
            oem = oem,
            pncCode = o.str("pnc_code"),
            bboxX = o.double("bbox_x") ?: return@mapNotNull null,
            bboxY = o.double("bbox_y") ?: return@mapNotNull null,
            bboxWidth = o.double("bbox_width") ?: return@mapNotNull null,
            bboxHeight = o.double("bbox_height") ?: return@mapNotNull null,
        )
    }
    val parts = root["parts"]?.asObjectList().orEmpty().mapNotNull { o ->
        val oem = o.str("oem_part_number") ?: return@mapNotNull null
        EpcDiagramPart(
            oemPartNumber = oem,
            pncCode = o.str("pnc_code"),
            categoryName = o.str("category_name"),
            subcategoryName = o.str("subcategory_name"),
            stockItemId = o.str("stock_item_id"),
            stockDescription = o.str("stock_description"),
        )
    }
    return EpcDiagramResponse(
        diagramSlug = diagram?.str("slug"),
        diagramTitle = diagram?.str("title"),
        storagePath = diagram?.str("storage_path"),
        imageUrl = diagram?.str("image_url"),
        hotspots = hotspots,
        parts = parts,
    )
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    this as? JsonObject ?: runCatching { jsonObject }.getOrNull()

private fun JsonElement.asObjectList(): List<JsonObject> {
    val arr = this as? JsonArray ?: runCatching { jsonArray }.getOrNull() ?: return emptyList()
    return arr.mapNotNull { it as? JsonObject ?: runCatching { it.jsonObject }.getOrNull() }
}

private fun JsonObject.str(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.int(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull
        ?: this[key]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
