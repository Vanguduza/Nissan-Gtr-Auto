package co.zw.nissangtr.pos.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Till tile DTO — matches §16.1 `list_pos_till_items` / offline snapshot item shape.
 * Qty is WH2/`stock_levels` only; never from Meili.
 */
@Serializable
data class TillItem(
    @SerialName("stock_item_id") val stockItemId: String? = null,
    @SerialName("oem_part_number") val oemPartNumber: String,
    val description: String,
    @SerialName("uom_id") val uomId: String? = null,
    @SerialName("unit_price") val unitPrice: Double? = null,
    @SerialName("core_charge") val coreCharge: Double? = null,
    val currency: String,
    @SerialName("saleable_qty") val saleableQty: Int = 0,
    @SerialName("bin_code") val binCode: String? = null,
    @SerialName("pnc_code") val pncCode: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("superseded_by") val supersededBy: String? = null,
    @SerialName("chassis_codes") val chassisCodes: List<String> = emptyList(),
    @SerialName("engine_codes") val engineCodes: List<String> = emptyList(),
)

@Serializable
data class VehicleLatch(
    @SerialName("chassis_code") val chassisCode: String,
    @SerialName("engine_code") val engineCode: String? = null,
    @SerialName("model_variant") val modelVariant: String? = null,
    @SerialName("vin_prefix") val vinPrefix: String? = null,
    @SerialName("production_year") val productionYear: Int? = null,
)

enum class FitmentBadge {
    FITS,
    VERIFY,
    NO_FIT,
}

@Serializable
data class TicketLine(
    val id: String,
    val oemPartNumber: String,
    val description: String,
    val qty: Int,
    val unitPrice: Double,
    val currency: String,
    val isCoreCharge: Boolean = false,
    val parentLineId: String? = null,
    /** Priced OOS / force-quote — blocks PAY, drives QUOTE CTA. */
    val isQuoteOnly: Boolean = false,
)

@Serializable
data class TicketSnapshot(
    val lines: List<TicketLine>,
    val itemCount: Int,
    val subtotal: Double,
    val currency: String,
) {
    val cta: TicketCta get() = TicketCtaResolver.resolve(lines)
}

@Serializable
data class TillStaffSession(
    val staffName: String,
    val terminalId: String,
    val warehouseLabel: String = "WH2",
    val warehouseId: String = "wh2-fake",
    val cartId: String? = "cart-fake-1",
)

@Serializable
data class TillFakeState(
    val session: TillStaffSession,
    val latch: VehicleLatch?,
    val tiles: List<TillItem>,
    val ticket: TicketSnapshot,
    val online: Boolean,
    val statusLabel: String,
)

@Serializable
data class QuotationRef(
    val id: String,
    @SerialName("document_number") val documentNumber: String,
)

@Serializable
data class CatalogMaker(
    val id: String,
    val name: String,
)

@Serializable
data class CatalogModel(
    val id: String,
    val name: String,
    val makerId: String,
)

@Serializable
data class CatalogVariant(
    val id: String,
    val name: String,
    val modelId: String,
    val chassisCode: String? = null,
)

@Serializable
data class CatalogSection(
    val id: String,
    val name: String,
    val pncCode: String? = null,
)

enum class TillItemsSource(val rpcValue: String) {
    SHOP_STOCK("shop_stock"),
    OEMS("oems"),
    SECTION("section"),
}

data class ListTillItemsRequest(
    val warehouseId: String,
    val source: TillItemsSource,
    val inStockOnly: Boolean = true,
    val oems: List<String> = emptyList(),
    val sectionId: String? = null,
    val pncCode: String? = null,
    val category: String? = null,
    val chassisCode: String? = null,
    val engineCode: String? = null,
)
