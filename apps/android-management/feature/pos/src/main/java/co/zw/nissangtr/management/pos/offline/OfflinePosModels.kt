package co.zw.nissangtr.management.pos.offline

import co.zw.nissangtr.management.rpc.CatalogPartHit
import co.zw.nissangtr.management.rpc.EpcDiagramResponse
import co.zw.nissangtr.management.rpc.EpcDiagramSummary
import co.zw.nissangtr.management.rpc.EpcMaker
import co.zw.nissangtr.management.rpc.EpcModel
import co.zw.nissangtr.management.rpc.EpcSection
import co.zw.nissangtr.management.rpc.EpcVariant
import co.zw.nissangtr.management.rpc.PosSaleVehicleSelection
import co.zw.nissangtr.management.rpc.PosPopularPin
import co.zw.nissangtr.management.rpc.PosPopularItemKind

/**
 * Local encrypted outbox / catalog cache for tablet offline POS.
 * Privilege: never stores manager approval tokens (see ADR).
 */
enum class PendingSaleStatus {
    Pending,
    Syncing,
    Synced,
    Conflict,
    Failed,
}

data class LocalCatalogItem(
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String?,
    val uomId: String,
    val unitPrice: Double,
    val coreCharge: Double,
    val saleableQty: Double,
    val currency: String,
)

data class LocalCartLine(
    val id: String,
    val stockItemId: String,
    val oemPartNumber: String,
    val uomId: String,
    val qty: Double,
    val unitPrice: Double,
    val lineTotal: Double,
    val isCoreCharge: Boolean = false,
)

enum class PopularPinDirtyAction { UPSERT, DELETE }

data class LocalPopularPinRecord(
    val userId: String,
    val pin: PosPopularPin,
    val dirtyAction: PopularPinDirtyAction? = null,
    val deleted: Boolean = false,
)

data class PendingOfflineSale(
    val clientSaleId: String,
    val warehouseId: String,
    val currency: String,
    val exchangeRate: Double,
    val deviceId: String?,
    val linesJson: String,
    val tendersJson: String,
    val receiptEmail: String?,
    val receiptWhatsapp: String?,
    val receiptPhone: String?,
    val vehicleJson: String? = null,
    val vehicleContextsJson: String? = null,
    val soldAtEpochMs: Long,
    val status: PendingSaleStatus,
    val lastError: String? = null,
    val serverInvoiceId: String? = null,
)


/** Full Nissan EPC snapshot stored in the same encrypted tablet SQLite file as POS offline data. */
data class LocalEpcCatalogBundle(
    val makers: List<EpcMaker>,
    val models: List<LocalEpcModelRow>,
    val variants: List<LocalEpcVariantRow>,
    val sections: List<LocalEpcSectionRow>,
    val diagrams: List<LocalEpcDiagramRow>,
    val syncedAtEpochMs: Long,
)

data class LocalEpcModelRow(val makerSlug: String, val model: EpcModel)
data class LocalEpcVariantRow(val makerSlug: String, val modelSlug: String, val variant: EpcVariant)
data class LocalEpcSectionRow(
    val makerSlug: String,
    val modelSlug: String,
    val variantSlug: String,
    val section: EpcSection,
)
data class LocalEpcDiagramRow(
    val makerSlug: String,
    val modelSlug: String,
    val variantSlug: String,
    val sectionSlug: String,
    val diagram: EpcDiagramResponse,
    val imageBytes: ByteArray? = null,
)

interface OfflinePosStore {
    fun replaceCatalog(warehouseId: String, pulledAtEpochMs: Long, items: List<LocalCatalogItem>)
    fun listPopularPins(userId: String, includeDeleted: Boolean = false): List<LocalPopularPinRecord> = emptyList()
    fun upsertPopularPin(userId: String, pin: PosPopularPin, dirtyAction: PopularPinDirtyAction? = null) = Unit
    fun markPopularPinDeleted(userId: String, kind: PosPopularItemKind, itemKey: String, dirtyAction: PopularPinDirtyAction? = PopularPinDirtyAction.DELETE) = Unit
    fun deletePopularPinRecord(userId: String, kind: PosPopularItemKind, itemKey: String) = Unit
    fun replacePopularPins(userId: String, pins: List<PosPopularPin>) = Unit
    fun replaceEpcCatalog(bundle: LocalEpcCatalogBundle) = Unit
    fun hasEpcCatalog(): Boolean = false
    fun epcCatalogSyncedAtEpochMs(): Long? = null
    fun listEpcMakers(): List<EpcMaker> = emptyList()
    fun listEpcModels(makerSlug: String): List<EpcModel> = emptyList()
    fun listEpcVariants(makerSlug: String, modelSlug: String): List<EpcVariant> = emptyList()
    fun listEpcSections(makerSlug: String, modelSlug: String, variantSlug: String): List<EpcSection> = emptyList()
    fun getEpcDiagram(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String): EpcDiagramResponse = EpcDiagramResponse()
    fun listEpcDiagrams(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String): List<EpcDiagramSummary> = emptyList()
    fun getEpcDiagramBySlug(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String, diagramSlug: String): EpcDiagramResponse =
        getEpcDiagram(makerSlug, modelSlug, variantSlug, sectionSlug)
    fun searchEpcParts(vehicle: PosSaleVehicleSelection, query: String, limit: Int = 80): List<CatalogPartHit> = emptyList()
    fun catalogFor(warehouseId: String): List<LocalCatalogItem>
    fun searchCatalog(warehouseId: String, query: String): List<LocalCatalogItem>
    fun adjustSaleableQty(warehouseId: String, stockItemId: String, delta: Double): Boolean
    fun enqueueSale(sale: PendingOfflineSale)
    fun pendingSales(includeTerminal: Boolean = false): List<PendingOfflineSale>
    fun markSaleStatus(
        clientSaleId: String,
        status: PendingSaleStatus,
        error: String? = null,
        serverInvoiceId: String? = null,
    )
    fun lastPulledAtEpochMs(warehouseId: String): Long?
    fun close()
}
