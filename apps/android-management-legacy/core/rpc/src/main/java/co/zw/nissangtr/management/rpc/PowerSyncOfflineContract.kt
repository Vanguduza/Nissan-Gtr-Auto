package co.zw.nissangtr.management.rpc

import android.content.Context
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.db.schema.Schema

/**
 * H7 / B-PS-1 — PowerSync offline contract.
 *
 * Mirrors `powersync/sync-rules.yaml` + `schema.json`. Mutations stay RPC intents
 * ([OfflinePosSyncEngine] / checkout / DN / recon RPCs) — never journal upload.
 */
object PowerSyncOfflineContract {
    /** Tables allowed in PowerSync read buckets (Phase 14 stubs). */
    val SYNC_ELIGIBLE_TABLES: Set<String> =
        setOf(
            // by_user_pos
            "pos_carts",
            "pos_cart_lines",
            "customers",
            "price_lists",
            "price_list_items",
            "customer_price_overrides",
            "stock_items",
            "uoms",
            "warehouses",
            "stock_levels",
            // by_staff_dispatch
            "sales_invoices",
            "sales_invoice_lines",
            "pick_lists",
            "pick_list_lines",
            "delivery_notes",
            "delivery_note_lines",
            "delivery_jobs",
            "delivery_locations",
            // by_staff_cycle_count
            "stock_reconciliations",
            "stock_reconciliation_lines",
        )

    /** Never sync-upload or treat as PowerSync write SoR (ledger immutability). */
    val FORBIDDEN_UPLOAD_TABLES: Set<String> =
        setOf(
            "journal_entries",
            "journal_entry_lines",
            "payment_entries",
            "payment_allocations",
        )

    /** Offline mutation path = RPC intents only (not PowerSync CRUD upload). */
    val RPC_INTENT_NAMES: Set<String> =
        setOf(
            "checkout_pos_cart",
            "checkout_pos_cart_with_tenders",
            "replay_offline_pos_sale",
            "create_delivery_note",
            "submit_delivery_note",
            "create_stock_reconciliation_draft",
            "upsert_stock_reconciliation_lines",
            "submit_stock_reconciliation",
            "approve_stock_reconciliation",
        )

    fun assertNotForbiddenUpload(table: String) {
        require(table !in FORBIDDEN_UPLOAD_TABLES) {
            "PowerSync must not upload ledger/payment table: $table (use RPC intents)"
        }
    }

    fun isSyncEligible(table: String): Boolean = table in SYNC_ELIGIBLE_TABLES
}

/**
 * Thin client facade for H7. Live [openDatabase] when [PowerSyncEndpointConfig.isLiveReady];
 * Fake when URL unset (matches FakeRpc habit). Secrets via local.properties / BuildConfig only.
 */
interface PowerSyncClient {
    val mode: PowerSyncMode

    /** True when a live PowerSync endpoint is configured (not Fake). */
    fun isLiveConfigured(): Boolean

    /** True after a successful [LivePowerSyncClient.openDatabase] (live path only). */
    fun isDatabaseOpen(): Boolean = false
}

enum class PowerSyncMode {
    FAKE,
    LIVE,
}

data class PowerSyncEndpointConfig(
    val url: String?,
    val publicKey: String? = null,
    val projectId: String? = null,
) {
    fun isLiveReady(): Boolean = !url.isNullOrBlank()

    companion object {
        /** Build from BuildConfig / local.properties names (never commit real values). */
        fun fromProperties(
            url: String?,
            publicKey: String? = null,
            projectId: String? = null,
        ): PowerSyncEndpointConfig =
            PowerSyncEndpointConfig(
                url = url?.trim()?.takeIf { it.isNotEmpty() },
                publicKey = publicKey?.trim()?.takeIf { it.isNotEmpty() },
                projectId = projectId?.trim()?.takeIf { it.isNotEmpty() },
            )
    }
}

/** Fake until POWERSYNC_URL / cloud project is configured. */
class FakePowerSyncClient : PowerSyncClient {
    override val mode: PowerSyncMode = PowerSyncMode.FAKE

    override fun isLiveConfigured(): Boolean = false
}

fun powerSyncClientFor(config: PowerSyncEndpointConfig): PowerSyncClient =
    if (config.isLiveReady()) {
        LivePowerSyncClient(config)
    } else {
        FakePowerSyncClient()
    }

/**
 * Live path: [com.powersync:core] openDatabase + connect when URL set.
 * Call [openDatabase] with Android [Context]; [connect] needs a JWT provider.
 * Unit tests must not open without URL / Context — use Fake or assert [isDatabaseOpen] false.
 */
class LivePowerSyncClient(
    val config: PowerSyncEndpointConfig,
) : PowerSyncClient {
    override val mode: PowerSyncMode = PowerSyncMode.LIVE

    @Volatile
    private var database: PowerSyncDatabase? = null

    override fun isLiveConfigured(): Boolean = config.isLiveReady()

    override fun isDatabaseOpen(): Boolean = database != null

    fun schema(): Schema = GtrPowerSyncSchema.schema

    /**
     * Opens local PowerSync SQLite with [GtrPowerSyncSchema]. Idempotent.
     * Does not connect to the cloud until [connect].
     */
    fun openDatabase(
        context: Context,
        dbFilename: String = "gtr-powersync.db",
    ): PowerSyncDatabase {
        database?.let { return it }
        val url = config.url?.trim().orEmpty()
        require(url.isNotBlank()) { "POWERSYNC_URL required to open live PowerSync database" }
        val factory = DatabaseDriverFactory(context.applicationContext)
        val opened =
            PowerSyncDatabase(
                factory = factory,
                schema = GtrPowerSyncSchema.schema,
                dbFilename = dbFilename,
            )
        database = opened
        return opened
    }

    /**
     * Connects sync using [GtrPowerSyncConnector] (RPC-intent upload policy).
     * [tokenProvider] should return the current Supabase access token, or null to skip.
     */
    suspend fun connect(tokenProvider: suspend () -> String?) {
        val db = database ?: error("openDatabase(context) before connect()")
        val endpoint = config.url?.trim().orEmpty()
        require(endpoint.isNotBlank()) { "POWERSYNC_URL required to connect" }
        db.connect(GtrPowerSyncConnector(endpoint, tokenProvider))
    }

    fun requireDatabase(): PowerSyncDatabase =
        database ?: error("PowerSync database not open — call openDatabase when POWERSYNC_URL is set")
}

/** @deprecated Slice-1 name; prefer [LivePowerSyncClient]. */
@Deprecated("Use LivePowerSyncClient", ReplaceWith("LivePowerSyncClient"))
typealias LivePendingPowerSyncClient = LivePowerSyncClient
