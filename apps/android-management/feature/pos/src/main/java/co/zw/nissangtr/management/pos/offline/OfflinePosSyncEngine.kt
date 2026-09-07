package co.zw.nissangtr.management.pos.offline

import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.CatalogPartHit
import co.zw.nissangtr.management.rpc.EpcDiagramResponse
import co.zw.nissangtr.management.rpc.EpcDiagramSummary
import co.zw.nissangtr.management.rpc.EpcMaker
import co.zw.nissangtr.management.rpc.EpcModel
import co.zw.nissangtr.management.rpc.EpcSection
import co.zw.nissangtr.management.rpc.EpcVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL
import co.zw.nissangtr.management.rpc.OfflineCatalogItem
import co.zw.nissangtr.management.rpc.OfflinePosSnapshot
import co.zw.nissangtr.management.rpc.OfflineSaleLine
import co.zw.nissangtr.management.rpc.OfflineSaleReplayPayload
import co.zw.nissangtr.management.rpc.PosTenderLine
import co.zw.nissangtr.management.rpc.PosSaleVehicleSelection
import co.zw.nissangtr.management.rpc.RpcClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Pull / queue / replay engine for offline counter sales.
 * Pure Kotlin — unit-testable without Android or SQLCipher.
 */
class OfflinePosSyncEngine(
    private val rpc: RpcClient,
    private val store: OfflinePosStore,
    private val deviceId: String = "unknown-device",
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Refreshes the complete Nissan EPC into the tablet's single encrypted catalog database.
     * Network calls are intentionally hierarchical to avoid one unbounded JSON response.
     */
    suspend fun refreshEpcCatalog(cacheDiagramImages: Boolean = true): LocalEpcCatalogBundle {
        val makers = rpc.listCatalogMakers().filter { it.slug.equals("nissan", ignoreCase = true) }
        require(makers.isNotEmpty()) { "Nissan EPC maker not available" }
        val modelRows = mutableListOf<LocalEpcModelRow>()
        val variantRows = mutableListOf<LocalEpcVariantRow>()
        val sectionRows = mutableListOf<LocalEpcSectionRow>()
        val diagramRows = mutableListOf<LocalEpcDiagramRow>()
        for (maker in makers) {
            val models = rpc.listCatalogModels(maker.slug)
            for (model in models) {
                modelRows += LocalEpcModelRow(maker.slug, model)
                val variants = rpc.listCatalogVariants(maker.slug, model.slug)
                for (variant in variants) {
                    variantRows += LocalEpcVariantRow(maker.slug, model.slug, variant)
                    val sections = rpc.listCatalogSections(maker.slug, model.slug, variant.slug)
                    for (section in sections) {
                        sectionRows += LocalEpcSectionRow(maker.slug, model.slug, variant.slug, section)
                        val summaries = rpc.listCatalogDiagrams(maker.slug, model.slug, variant.slug, section.slug)
                        if (summaries.isEmpty()) {
                            val fallback = rpc.getCatalogDiagram(maker.slug, model.slug, variant.slug, section.slug)
                            if (fallback.diagramSlug != null || fallback.parts.isNotEmpty()) {
                                val bytes = if (cacheDiagramImages && !fallback.imageUrl.isNullOrBlank()) {
                                    runCatching { downloadDiagramBytes(fallback.imageUrl) }.getOrNull()
                                } else null
                                diagramRows += LocalEpcDiagramRow(maker.slug, model.slug, variant.slug, section.slug, fallback, bytes)
                            }
                        } else {
                            for (summary in summaries) {
                                val diagram = rpc.getCatalogDiagramBySlug(
                                    maker.slug, model.slug, variant.slug, section.slug, summary.slug,
                                )
                                val bytes = if (cacheDiagramImages && !diagram.imageUrl.isNullOrBlank()) {
                                    runCatching { downloadDiagramBytes(diagram.imageUrl) }.getOrNull()
                                } else null
                                diagramRows += LocalEpcDiagramRow(
                                    makerSlug = maker.slug,
                                    modelSlug = model.slug,
                                    variantSlug = variant.slug,
                                    sectionSlug = section.slug,
                                    diagram = diagram,
                                    imageBytes = bytes,
                                )
                            }
                        }
                    }
                }
            }
        }
        val bundle = LocalEpcCatalogBundle(
            makers = makers,
            models = modelRows,
            variants = variantRows,
            sections = sectionRows,
            diagrams = diagramRows,
            syncedAtEpochMs = clockMs(),
        )
        store.replaceEpcCatalog(bundle)
        return bundle
    }

    fun hasLocalEpcCatalog(): Boolean = store.hasEpcCatalog()
    fun localEpcSyncedAtEpochMs(): Long? = store.epcCatalogSyncedAtEpochMs()
    fun listLocalEpcMakers(): List<EpcMaker> = store.listEpcMakers()
    fun listLocalEpcModels(makerSlug: String): List<EpcModel> = store.listEpcModels(makerSlug)
    fun listLocalEpcVariants(makerSlug: String, modelSlug: String): List<EpcVariant> = store.listEpcVariants(makerSlug, modelSlug)
    fun listLocalEpcSections(makerSlug: String, modelSlug: String, variantSlug: String): List<EpcSection> =
        store.listEpcSections(makerSlug, modelSlug, variantSlug)
    fun getLocalEpcDiagram(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String): EpcDiagramResponse =
        store.getEpcDiagram(makerSlug, modelSlug, variantSlug, sectionSlug)
    fun listLocalEpcDiagrams(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String): List<EpcDiagramSummary> =
        store.listEpcDiagrams(makerSlug, modelSlug, variantSlug, sectionSlug)
    fun getLocalEpcDiagramBySlug(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String, diagramSlug: String): EpcDiagramResponse =
        store.getEpcDiagramBySlug(makerSlug, modelSlug, variantSlug, sectionSlug, diagramSlug)
    fun searchLocalEpcParts(vehicle: PosSaleVehicleSelection, query: String, limit: Int = 80): List<CatalogPartHit> =
        store.searchEpcParts(vehicle, query, limit)

    private suspend fun downloadDiagramBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection().apply {
            connectTimeout = 10_000
            readTimeout = 20_000
        }
        connection.getInputStream().use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= MAX_EPC_IMAGE_BYTES) { "EPC diagram image exceeds local cache limit" }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }

    suspend fun pullSnapshot(warehouseId: String): OfflinePosSnapshot {
        require(warehouseId.isNotBlank())
        val snap = rpc.pullPosOfflineSnapshot(warehouseId)
        store.replaceCatalog(
            warehouseId = snap.warehouseId,
            pulledAtEpochMs = clockMs(),
            items = snap.items.map { it.toLocal() },
        )
        return snap
    }

    fun searchLocal(warehouseId: String, query: String): List<LocalCatalogItem> =
        store.searchCatalog(warehouseId, query)

    /**
     * Queue a cash walk-in sale from local cart lines. Decrements cached qty.
     * @return client_sale_id
     */
    fun queueCashSale(
        warehouseId: String,
        currency: CurrencyCode,
        exchangeRate: Double,
        lines: List<LocalCartLine>,
        receiptEmail: String? = null,
        receiptWhatsapp: String? = null,
        receiptPhone: String? = null,
        vehicle: PosSaleVehicleSelection? = null,
    ): String {
        require(warehouseId.isNotBlank())
        require(lines.isNotEmpty()) { "Add at least one line before offline checkout" }
        val saleLines = lines.filter { !it.isCoreCharge }
        require(saleLines.isNotEmpty()) { "Offline sale requires non-core lines" }

        for (line in saleLines) {
            val ok = store.adjustSaleableQty(warehouseId, line.stockItemId, -line.qty)
            require(ok) {
                "Insufficient local stock for ${line.oemPartNumber} (refresh snapshot when online)"
            }
        }

        val total = lines.sumOf { it.lineTotal }
        val clientSaleId = UUID.randomUUID().toString()
        val linesJson = JSONArray().apply {
            saleLines.forEach { line ->
                put(
                    JSONObject()
                        .put("stock_item_id", line.stockItemId)
                        .put("uom_id", line.uomId)
                        .put("qty", line.qty)
                        .put("expected_unit_price", line.unitPrice),
                )
            }
        }.toString()
        val tendersJson = JSONArray().apply {
            put(
                JSONObject()
                    .put("tender", "cash")
                    .put("amount", total)
                    .put("currency", currency.rpcValue),
            )
        }.toString()

        val vehicleJson = vehicle?.let { v ->
            JSONObject()
                .put("model_slug", v.modelSlug)
                .put("model_name", v.modelName)
                .put("generation", v.generation)
                .put("chassis_code", v.chassisCode)
                .put("engine_code", v.engineCode)
                .toString()
        }

        store.enqueueSale(
            PendingOfflineSale(
                clientSaleId = clientSaleId,
                warehouseId = warehouseId,
                currency = currency.rpcValue,
                exchangeRate = exchangeRate,
                deviceId = deviceId,
                linesJson = linesJson,
                tendersJson = tendersJson,
                receiptEmail = receiptEmail,
                receiptWhatsapp = receiptWhatsapp,
                receiptPhone = receiptPhone,
                vehicleJson = vehicleJson,
                soldAtEpochMs = clockMs(),
                status = PendingSaleStatus.Pending,
            ),
        )
        return clientSaleId
    }

    data class DrainResult(
        val synced: Int,
        val conflicts: Int,
        val failed: Int,
        val remaining: Int,
    )

    /** Replay pending/failed/conflict sales. Idempotent per client_sale_id. */
    suspend fun drainQueue(): DrainResult {
        var synced = 0
        var conflicts = 0
        var failed = 0
        val queue = store.pendingSales(includeTerminal = false)
            .filter {
                it.status == PendingSaleStatus.Pending ||
                    it.status == PendingSaleStatus.Failed ||
                    it.status == PendingSaleStatus.Conflict
            }
        for (sale in queue) {
            store.markSaleStatus(sale.clientSaleId, PendingSaleStatus.Syncing)
            try {
                val payload = sale.toReplayPayload()
                val invoiceId = rpc.replayOfflinePosSale(sale.clientSaleId, payload)
                store.markSaleStatus(
                    clientSaleId = sale.clientSaleId,
                    status = PendingSaleStatus.Synced,
                    serverInvoiceId = invoiceId,
                )
                synced++
            } catch (e: Exception) {
                val msg = e.message.orEmpty()
                val conflict = msg.contains("offline_price_conflict", ignoreCase = true) ||
                    msg.contains("insufficient", ignoreCase = true) ||
                    msg.contains("stock", ignoreCase = true)
                if (conflict) {
                    store.markSaleStatus(
                        sale.clientSaleId,
                        PendingSaleStatus.Conflict,
                        error = msg.ifBlank { "conflict" },
                    )
                    conflicts++
                } else {
                    store.markSaleStatus(
                        sale.clientSaleId,
                        PendingSaleStatus.Failed,
                        error = msg.ifBlank { "sync failed" },
                    )
                    failed++
                }
            }
        }
        val remaining = store.pendingSales(includeTerminal = false).count {
            it.status != PendingSaleStatus.Synced
        }
        return DrainResult(synced = synced, conflicts = conflicts, failed = failed, remaining = remaining)
    }

    fun pendingCount(): Int =
        store.pendingSales(includeTerminal = false).count {
            it.status == PendingSaleStatus.Pending ||
                it.status == PendingSaleStatus.Failed ||
                it.status == PendingSaleStatus.Conflict
        }

    private fun OfflineCatalogItem.toLocal() = LocalCatalogItem(
        stockItemId = stockItemId,
        oemPartNumber = oemPartNumber,
        description = description,
        uomId = uomId,
        unitPrice = unitPrice,
        coreCharge = coreCharge,
        saleableQty = saleableQty,
        currency = currency.rpcValue,
    )

    private fun PendingOfflineSale.toReplayPayload(): OfflineSaleReplayPayload {
        val linesArr = JSONArray(linesJson)
        val lines = buildList {
            for (i in 0 until linesArr.length()) {
                val o = linesArr.getJSONObject(i)
                add(
                    OfflineSaleLine(
                        stockItemId = o.getString("stock_item_id"),
                        uomId = o.getString("uom_id"),
                        qty = o.getDouble("qty"),
                        expectedUnitPrice = o.getDouble("expected_unit_price"),
                    ),
                )
            }
        }
        val tendersArr = JSONArray(tendersJson)
        val tenders = buildList {
            for (i in 0 until tendersArr.length()) {
                val o = tendersArr.getJSONObject(i)
                add(
                    PosTenderLine(
                        tender = o.getString("tender"),
                        amount = o.getDouble("amount"),
                        currency = o.optString("currency").ifBlank { null },
                    ),
                )
            }
        }
        val vehicle = vehicleJson?.takeIf { it.isNotBlank() }?.let { raw ->
            val o = JSONObject(raw)
            PosSaleVehicleSelection(
                modelSlug = o.getString("model_slug"),
                modelName = o.getString("model_name"),
                generation = o.getString("generation"),
                chassisCode = o.getString("chassis_code"),
                engineCode = o.getString("engine_code"),
            )
        }
        return OfflineSaleReplayPayload(
            warehouseId = warehouseId,
            currency = CurrencyCode.entries.find { it.rpcValue == currency } ?: CurrencyCode.USD,
            exchangeRate = exchangeRate,
            deviceId = deviceId,
            lines = lines,
            tenders = tenders,
            receiptEmail = receiptEmail,
            receiptWhatsappE164 = receiptWhatsapp,
            receiptPhoneE164 = receiptPhone,
            soldAt = null,
            vehicle = vehicle,
        )
    }
    private companion object {
        const val MAX_EPC_IMAGE_BYTES = 12 * 1024 * 1024
    }

}
