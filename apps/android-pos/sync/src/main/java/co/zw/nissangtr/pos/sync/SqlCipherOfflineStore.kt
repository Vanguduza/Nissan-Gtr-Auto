package co.zw.nissangtr.pos.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.zw.nissangtr.pos.api.OemNormalize
import co.zw.nissangtr.pos.api.TillItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.sqlcipher.database.SQLiteDatabase
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * SQLCipher-backed offline catalog + outbox.
 * Passphrase is Keystore-wrapped via [EncryptedSharedPreferences] — never manager tokens.
 */
class SqlCipherOfflineStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : OfflineStore {

    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        "pos_offline_keystore",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val db: SQLiteDatabase

    init {
        SQLiteDatabase.loadLibs(appContext)
        val passphrase = loadOrCreatePassphrase()
        val path = File(appContext.getDatabasePath(DB_NAME).path)
        path.parentFile?.mkdirs()
        db = SQLiteDatabase.openOrCreateDatabase(path, passphrase, null)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS catalog_items (
              oem TEXT PRIMARY KEY NOT NULL,
              payload TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meta (
              key TEXT PRIMARY KEY NOT NULL,
              value TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS outbox (
              client_sale_id TEXT PRIMARY KEY NOT NULL,
              payload TEXT NOT NULL,
              status TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun loadOrCreatePassphrase(): String {
        val existing = prefs.getString(KEY_PASSPHRASE, null)
        if (existing != null) return existing
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val created = Base64.getEncoder().encodeToString(bytes)
        prefs.edit().putString(KEY_PASSPHRASE, created).apply()
        return created
    }

    override fun replaceCatalog(snapshot: OfflineSnapshot) {
        db.beginTransaction()
        try {
            db.delete("catalog_items", null, null)
            snapshot.items.forEach { item ->
                db.execSQL(
                    "INSERT INTO catalog_items(oem, payload) VALUES(?,?)",
                    arrayOf(
                        OemNormalize.normalize(item.oemPartNumber),
                        json.encodeToString(item),
                    ),
                )
            }
            upsertMeta("warehouse_id", snapshot.warehouseId)
            upsertMeta("pulled_at", snapshot.pulledAt)
            upsertMeta("currency", snapshot.currency)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun getSnapshotMeta(): OfflineSnapshot? {
        val warehouseId = getMeta("warehouse_id") ?: return null
        val pulledAt = getMeta("pulled_at") ?: return null
        val currency = getMeta("currency") ?: "USD"
        return OfflineSnapshot(
            warehouseId = warehouseId,
            pulledAt = pulledAt,
            currency = currency,
            items = emptyList(),
        )
    }

    override fun listCatalog(): List<TillItem> {
        val out = mutableListOf<TillItem>()
        db.rawQuery("SELECT payload FROM catalog_items", null).use { c ->
            while (c.moveToNext()) {
                out += json.decodeFromString<TillItem>(c.getString(0))
            }
        }
        return out
    }

    override fun findByOem(oemNormalized: String): TillItem? {
        val key = OemNormalize.normalize(oemNormalized)
        db.rawQuery("SELECT payload FROM catalog_items WHERE oem=?", arrayOf(key)).use { c ->
            if (!c.moveToFirst()) return null
            return json.decodeFromString(c.getString(0))
        }
    }

    override fun decrementQty(stockItemId: String?, oem: String, qty: Int): Boolean {
        val cur = findByOem(oem) ?: return false
        if (cur.saleableQty < qty) return false
        upsertItem(cur.copy(saleableQty = cur.saleableQty - qty))
        return true
    }

    override fun restoreQty(stockItemId: String?, oem: String, qty: Int) {
        val cur = findByOem(oem) ?: return
        upsertItem(cur.copy(saleableQty = cur.saleableQty + qty))
    }

    override fun enqueueSale(row: OfflineSaleOutboxRow) {
        db.execSQL(
            "INSERT OR REPLACE INTO outbox(client_sale_id, payload, status) VALUES(?,?,?)",
            arrayOf(row.clientSaleId, json.encodeToString(row), row.status.name),
        )
    }

    override fun listOutbox(statuses: Set<OutboxStatus>): List<OfflineSaleOutboxRow> {
        val out = mutableListOf<OfflineSaleOutboxRow>()
        db.rawQuery("SELECT payload FROM outbox", null).use { c ->
            while (c.moveToNext()) {
                val row = json.decodeFromString<OfflineSaleOutboxRow>(c.getString(0))
                if (row.status in statuses) out += row
            }
        }
        return out
    }

    override fun updateOutbox(row: OfflineSaleOutboxRow) {
        enqueueSale(row)
    }

    private fun upsertItem(item: TillItem) {
        db.execSQL(
            "INSERT OR REPLACE INTO catalog_items(oem, payload) VALUES(?,?)",
            arrayOf(OemNormalize.normalize(item.oemPartNumber), json.encodeToString(item)),
        )
    }

    private fun upsertMeta(key: String, value: String) {
        db.execSQL(
            "INSERT OR REPLACE INTO meta(key, value) VALUES(?,?)",
            arrayOf(key, value),
        )
    }

    private fun getMeta(key: String): String? {
        db.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use { c ->
            if (!c.moveToFirst()) return null
            return c.getString(0)
        }
    }

    companion object {
        private const val DB_NAME = "pos_offline.db"
        private const val KEY_PASSPHRASE = "sqlcipher_passphrase"
    }
}
