package co.zw.nissangtr.management.pos.offline

import co.zw.nissangtr.management.rpc.CurrencyCode
import co.zw.nissangtr.management.rpc.OfflineCatalogItem
import co.zw.nissangtr.management.rpc.OfflinePosSnapshot
import co.zw.nissangtr.management.rpc.OfflineSaleLine
import co.zw.nissangtr.management.rpc.OfflineSaleReplayPayload
import co.zw.nissangtr.management.rpc.PosTenderLine
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
        )
    }
}
