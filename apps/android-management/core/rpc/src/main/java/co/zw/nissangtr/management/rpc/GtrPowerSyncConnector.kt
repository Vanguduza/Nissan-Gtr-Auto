package co.zw.nissangtr.management.rpc

import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials

/**
 * PowerSync backend connector for GTR management (H7).
 *
 * - Credentials from [POWERSYNC_URL] + caller-supplied JWT (Supabase session).
 * - [uploadData] never pushes table CRUD (esp. journal) — mutations stay RPC intents
 *   ([OfflinePosSyncEngine] / checkout / DN / recon). Pending CRUD is discarded after
 *   fail-closed checks so the queue does not stall.
 */
class GtrPowerSyncConnector(
    private val endpointUrl: String,
    private val tokenProvider: suspend () -> String?,
) : PowerSyncBackendConnector() {
    init {
        require(endpointUrl.isNotBlank()) { "POWERSYNC_URL required for live connector" }
    }

    override suspend fun fetchCredentials(): PowerSyncCredentials? {
        val token = tokenProvider()?.trim().orEmpty()
        if (token.isBlank()) return null
        return PowerSyncCredentials(
            endpoint = endpointUrl.trim().trimEnd('/'),
            token = token,
        )
    }

    override suspend fun uploadData(database: PowerSyncDatabase) {
        val transaction = database.getNextCrudTransaction() ?: return
        // Fail closed on ledger/payment tables; never upload JE mutations.
        for (entry in transaction.crud) {
            PowerSyncOfflineContract.assertNotForbiddenUpload(entry.table)
        }
        // Read-sync only: discard local CRUD. Offline writes use RPC intents.
        transaction.complete(null)
    }

    companion object {
        /**
         * Pure guard used by unit tests (no open database).
         * Returns false if any table is forbidden for upload.
         */
        fun wouldAllowUpload(tables: Iterable<String>): Boolean =
            tables.none { it in PowerSyncOfflineContract.FORBIDDEN_UPLOAD_TABLES }
    }
}
