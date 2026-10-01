package co.zw.nissangtr.management.pos

import android.content.Context
import android.provider.Settings
import co.zw.nissangtr.management.pos.offline.InMemoryOfflinePosStore
import co.zw.nissangtr.management.pos.offline.LocalCartLine
import co.zw.nissangtr.management.pos.offline.OfflinePosRpcHolder
import co.zw.nissangtr.management.pos.offline.OfflinePosStore
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncEngine
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncWorker
import co.zw.nissangtr.management.pos.offline.PendingSaleStatus
import co.zw.nissangtr.management.pos.offline.SqlCipherOfflinePosStore
import co.zw.nissangtr.management.rpc.CurrencyCode as RpcCurrency
import co.zw.nissangtr.management.rpc.PosSaleVehicleSelection
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.gateway.OfflineSaleGateway
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.OfflineQueued
import co.zw.nissangtr.pos.domain.model.OfflineSyncStatus
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import co.zw.nissangtr.pos.domain.result.PosResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * The tablet's offline outbox for the benchmark shell (Blueprint §10.12), over the existing
 * SQLCipher store and [OfflinePosSyncEngine]: local snapshot search, cash walk-in queueing with a
 * client sale id, idempotent replay, and a WorkManager drain if the app closes before reconnecting.
 */
class PosOfflineOutbox private constructor(
    private val context: Context,
    private val rpc: RpcClient,
    private val store: OfflinePosStore,
    private val engine: OfflinePosSyncEngine,
) : OfflineSaleGateway {

    private val prefs = context.getSharedPreferences("pos_offline_outbox", Context.MODE_PRIVATE)

    /** The selling warehouse, remembered so offline search and queueing work without the server. */
    private suspend fun warehouseId(): String {
        prefs.getString(KEY_WAREHOUSE, null)?.let { return it }
        val all = rpc.listWarehouses()
        val chosen = all.firstOrNull { it.code.equals("MAIN", ignoreCase = true) } ?: all.firstOrNull()
            ?: throw OutboxRefusal(PosError.BusinessRule("no_warehouse", "No active warehouse is set up for this shop."))
        prefs.edit().putString(KEY_WAREHOUSE, chosen.id).apply()
        return chosen.id
    }

    override suspend fun searchLocal(query: String) = io {
        val warehouse = prefs.getString(KEY_WAREHOUSE, null)
            ?: throw OutboxRefusal(PosError.OfflineRestricted(setOf("no_snapshot")))
        engine.searchLocal(warehouse, query.trim()).map { item ->
            val currency = CurrencyCode(item.currency.uppercase())
            CatalogPart(
                stockItemId = item.stockItemId,
                oemPartNumber = item.oemPartNumber,
                name = item.description ?: item.oemPartNumber,
                price = Money.ofMajor(item.unitPrice, currency),
                saleableQty = item.saleableQty,
                imageUrl = null,
            )
        }
    }

    override suspend fun queueCashSale(cart: CartProjection, vehicle: VehicleSelection?, contacts: ReceiptContacts) = io {
        val warehouse = prefs.getString(KEY_WAREHOUSE, null)
            ?: throw OutboxRefusal(PosError.OfflineRestricted(setOf("no_snapshot")))
        val uoms = store.catalogFor(warehouse).associate { it.stockItemId to it.uomId }
        val lines = cart.lines.map { line ->
            LocalCartLine(
                id = line.lineId,
                stockItemId = line.stockItemId,
                oemPartNumber = line.oemPartNumber,
                uomId = uoms[line.stockItemId]
                    ?: throw OutboxRefusal(PosError.BusinessRule("offline_stock", line.oemPartNumber)),
                qty = line.qty,
                unitPrice = line.unitPrice.minor / 100.0,
                lineTotal = line.lineTotal.minor / 100.0,
            )
        }
        val clientSaleId = try {
            engine.queueCashSale(
                warehouseId = warehouse,
                currency = RpcCurrency.entries.first { it.rpcValue.equals(cart.currency.code, ignoreCase = true) },
                // Priced in the snapshot's own currency: no conversion happens on the till.
                exchangeRate = 1.0,
                lines = lines,
                receiptEmail = contacts.email,
                receiptWhatsapp = contacts.whatsappE164,
                vehicle = vehicle?.let { PosSaleVehicleSelection(it.modelSlug, it.modelName, it.generation, it.chassisCode, it.engineCode) },
            )
        } catch (e: IllegalArgumentException) {
            throw OutboxRefusal(PosError.BusinessRule("offline_queue", e.message.orEmpty()))
        }
        // Drains on its own once a network is back, even if the till app is closed first.
        OfflinePosRpcHolder.set(rpc)
        OfflinePosSyncWorker.enqueue(context)
        val soldAt = Instant.now().atZone(ZoneId.systemDefault()).withNano(0).toOffsetDateTime().toString()
        OfflineQueued(clientSaleId, soldAt, counts())
    }

    override suspend fun sync() = io {
        val drained = engine.drainQueue()
        // Refresh prices and stock for the next offline spell.
        engine.pullSnapshot(warehouseId())
        counts().copy(synced = drained.synced)
    }

    override suspend fun status() = io { counts() }

    private fun counts(): OfflineSyncStatus {
        val open = store.pendingSales(includeTerminal = false)
        return OfflineSyncStatus(
            pending = open.count { it.status == PendingSaleStatus.Pending || it.status == PendingSaleStatus.Failed || it.status == PendingSaleStatus.Syncing },
            conflicts = open.count { it.status == PendingSaleStatus.Conflict },
        )
    }

    fun close() = store.close()

    private suspend fun <T> io(block: suspend () -> T): PosResult<T> = withContext(Dispatchers.IO) {
        try {
            PosResult.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutboxRefusal) {
            PosResult.Err(e.error)
        } catch (e: IOException) {
            PosResult.Err(PosError.Transient(retryable = true, message = e.message.orEmpty()))
        } catch (e: Exception) {
            PosResult.Err(PosError.Transient(retryable = true, message = e.message.orEmpty()))
        }
    }

    private class OutboxRefusal(val error: PosError) : Exception()

    companion object {
        private const val KEY_WAREHOUSE = "warehouse_id"

        fun open(context: Context, rpc: RpcClient): PosOfflineOutbox {
            val app = context.applicationContext
            val store: OfflinePosStore = runCatching { SqlCipherOfflinePosStore.open(app) }.getOrElse { InMemoryOfflinePosStore() }
            val engine = OfflinePosSyncEngine(
                rpc = rpc,
                store = store,
                deviceId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "tablet",
            )
            return PosOfflineOutbox(app, rpc, store, engine)
        }
    }
}
