package co.zw.nissangtr.pos.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `search_catalog` hit shapes — identity only.
 * Never carry Meili/FTS inventory fields (qty / saleable_qty).
 */
enum class CatalogSearchMode(val rpcValue: String) {
    PART("part"),
    VIN("vin"),
    MODEL("model"),
    PNC("pnc"),
}

sealed class CatalogHit {
    abstract val type: String
}

data class PartHit(
    override val type: String = "part",
    val oemPartNumber: String,
    val pncCode: String? = null,
    val chassisCode: String? = null,
    val engineCode: String? = null,
    val supersededBy: String? = null,
    val categoryName: String? = null,
    val subcategoryName: String? = null,
) : CatalogHit()

data class VehicleHit(
    override val type: String = "vehicle",
    val vinPrefix: String? = null,
    val modelVariant: String? = null,
    val chassisCode: String? = null,
    val engineCode: String? = null,
    val productionYear: Int? = null,
) : CatalogHit()

data class PncHit(
    override val type: String = "pnc",
    val pncCode: String,
    val categoryName: String? = null,
    val subcategoryName: String? = null,
) : CatalogHit()

@Serializable
data class CatalogSearchResponse(
    val mode: String,
    val query: String,
    val results: List<CatalogHitDto> = emptyList(),
)

/**
 * Flat DTO for Fake JSON / Live parse before typed routing.
 * Inventory keys are intentionally absent — qty lives on [TillItem] only.
 */
@Serializable
data class CatalogHitDto(
    val type: String,
    @SerialName("oem_part_number") val oemPartNumber: String? = null,
    @SerialName("pnc_code") val pncCode: String? = null,
    @SerialName("chassis_code") val chassisCode: String? = null,
    @SerialName("engine_code") val engineCode: String? = null,
    @SerialName("superseded_by") val supersededBy: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("subcategory_name") val subcategoryName: String? = null,
    @SerialName("vin_prefix") val vinPrefix: String? = null,
    @SerialName("model_variant") val modelVariant: String? = null,
    @SerialName("production_year") val productionYear: Int? = null,
)

fun CatalogHitDto.toTyped(): CatalogHit? = when (type.lowercase()) {
    "part" -> {
        val oem = oemPartNumber?.takeIf { it.isNotBlank() } ?: return null
        PartHit(
            oemPartNumber = oem,
            pncCode = pncCode,
            chassisCode = chassisCode,
            engineCode = engineCode,
            supersededBy = supersededBy,
            categoryName = categoryName,
            subcategoryName = subcategoryName,
        )
    }
    "vehicle" -> VehicleHit(
        vinPrefix = vinPrefix,
        modelVariant = modelVariant,
        chassisCode = chassisCode,
        engineCode = engineCode,
        productionYear = productionYear,
    )
    "pnc" -> {
        val pnc = pncCode?.takeIf { it.isNotBlank() } ?: return null
        PncHit(
            pncCode = pnc,
            categoryName = categoryName,
            subcategoryName = subcategoryName,
        )
    }
    else -> null
}
