package co.zw.nissangtr.management.pos

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModelProvider
import co.zw.nissangtr.bridges.escpos.DocumentPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.pos.offline.InMemoryOfflinePosStore
import co.zw.nissangtr.management.pos.offline.OfflinePosConnectivity
import co.zw.nissangtr.management.pos.offline.OfflinePosRpcHolder
import co.zw.nissangtr.management.pos.offline.OfflinePosStore
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncEngine
import co.zw.nissangtr.management.pos.offline.OfflinePosSyncWorker
import co.zw.nissangtr.management.pos.offline.SqlCipherOfflinePosStore
import co.zw.nissangtr.management.rpc.RpcClient

/**
 * Non-visual POS runtime: encrypted offline store, sync engine, connectivity and the
 * [PosViewModel] factory. Extracted from the retired pre-benchmark `PosScreen` so the
 * benchmark POS UI (`:feature:pos-ui`) can bind to the same behaviour without inheriting
 * any of the old composables.
 *
 * Owner must call [close] when the hosting screen leaves composition.
 */
class PosRuntime private constructor(
    private val context: Context,
    val store: OfflinePosStore,
    val offlineEngine: OfflinePosSyncEngine,
    val viewModelFactory: ViewModelProvider.Factory,
) {
    /** Schedules the background drain when the till is online with queued offline sales. */
    fun onSyncState(isOffline: Boolean, pendingOfflineSales: Int) {
        if (!isOffline && pendingOfflineSales > 0) {
            OfflinePosSyncWorker.enqueue(context)
        }
    }

    fun close() {
        OfflinePosRpcHolder.set(null)
        store.close()
    }

    companion object {
        fun create(
            context: Context,
            rpc: RpcClient,
            qr: QrScannerBridge,
            printer: EscPosPrinterBridge,
            documentPrinter: DocumentPrinterBridge? = null,
        ): PosRuntime {
            val appContext = context.applicationContext
            val store: OfflinePosStore = runCatching { SqlCipherOfflinePosStore.open(appContext) }
                .getOrElse { InMemoryOfflinePosStore() }
            OfflinePosRpcHolder.set(rpc)
            val engine = OfflinePosSyncEngine(
                rpc = rpc,
                store = store,
                deviceId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "tablet",
            )
            val factory = PosViewModel.factory(
                rpc = rpc,
                qr = qr,
                printer = printer,
                documentPrinter = documentPrinter,
                offlineEngine = engine,
                onlineFlow = OfflinePosConnectivity.onlineFlow(appContext),
            )
            return PosRuntime(appContext, store, engine, factory)
        }
    }
}
