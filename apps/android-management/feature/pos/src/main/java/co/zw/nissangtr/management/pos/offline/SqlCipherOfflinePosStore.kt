package co.zw.nissangtr.management.pos.offline

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper

/**
 * SQLCipher-backed offline POS catalog + pending sales queue.
 * Schema is intentionally small; privilege tokens are never persisted.
 */
class SqlCipherOfflinePosStore private constructor(
    private val helper: Helper,
    private val passphrase: CharArray,
) : OfflinePosStore {

    private fun db(): SQLiteDatabase = helper.getWritableDatabase(passphrase)

    override fun replaceCatalog(
        warehouseId: String,
        pulledAtEpochMs: Long,
        items: List<LocalCatalogItem>,
    ) {
        val database = db()
        database.beginTransaction()
        try {
            database.execSQL("DELETE FROM catalog_items WHERE warehouse_id = ?", arrayOf(warehouseId))
            val stmt = database.compileStatement(
                """
                INSERT INTO catalog_items (
                  warehouse_id, stock_item_id, oem_part_number, description, uom_id,
                  unit_price, core_charge, saleable_qty, currency
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """.trimIndent(),
            )
            for (item in items) {
                stmt.clearBindings()
                stmt.bindString(1, warehouseId)
                stmt.bindString(2, item.stockItemId)
                stmt.bindString(3, item.oemPartNumber)
                if (item.description == null) stmt.bindNull(4) else stmt.bindString(4, item.description)
                stmt.bindString(5, item.uomId)
                stmt.bindDouble(6, item.unitPrice)
                stmt.bindDouble(7, item.coreCharge)
                stmt.bindDouble(8, item.saleableQty)
                stmt.bindString(9, item.currency)
                stmt.executeInsert()
            }
            database.execSQL(
                """
                INSERT OR REPLACE INTO snapshot_meta (warehouse_id, pulled_at_ms)
                VALUES (?, ?)
                """.trimIndent(),
                arrayOf(warehouseId, pulledAtEpochMs),
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    override fun catalogFor(warehouseId: String): List<LocalCatalogItem> {
        val out = mutableListOf<LocalCatalogItem>()
        db().rawQuery(
            """
            SELECT stock_item_id, oem_part_number, description, uom_id,
                   unit_price, core_charge, saleable_qty, currency
            FROM catalog_items WHERE warehouse_id = ?
            ORDER BY oem_part_number
            """.trimIndent(),
            arrayOf(warehouseId),
        ).use { c ->
            while (c.moveToNext()) {
                out += LocalCatalogItem(
                    stockItemId = c.getString(0),
                    oemPartNumber = c.getString(1),
                    description = c.getString(2),
                    uomId = c.getString(3),
                    unitPrice = c.getDouble(4),
                    coreCharge = c.getDouble(5),
                    saleableQty = c.getDouble(6),
                    currency = c.getString(7),
                )
            }
        }
        return out
    }

    override fun searchCatalog(warehouseId: String, query: String): List<LocalCatalogItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val like = "%${q.lowercase()}%"
        val out = mutableListOf<LocalCatalogItem>()
        db().rawQuery(
            """
            SELECT stock_item_id, oem_part_number, description, uom_id,
                   unit_price, core_charge, saleable_qty, currency
            FROM catalog_items
            WHERE warehouse_id = ?
              AND (
                lower(oem_part_number) LIKE ?
                OR lower(COALESCE(description, '')) LIKE ?
              )
            ORDER BY oem_part_number
            LIMIT 80
            """.trimIndent(),
            arrayOf(warehouseId, like, like),
        ).use { c ->
            while (c.moveToNext()) {
                out += LocalCatalogItem(
                    stockItemId = c.getString(0),
                    oemPartNumber = c.getString(1),
                    description = c.getString(2),
                    uomId = c.getString(3),
                    unitPrice = c.getDouble(4),
                    coreCharge = c.getDouble(5),
                    saleableQty = c.getDouble(6),
                    currency = c.getString(7),
                )
            }
        }
        return out
    }

    override fun adjustSaleableQty(
        warehouseId: String,
        stockItemId: String,
        delta: Double,
    ): Boolean {
        val database = db()
        database.beginTransaction()
        try {
            var qty = 0.0
            database.rawQuery(
                """
                SELECT saleable_qty FROM catalog_items
                WHERE warehouse_id = ? AND stock_item_id = ?
                """.trimIndent(),
                arrayOf(warehouseId, stockItemId),
            ).use { c ->
                if (!c.moveToFirst()) return false
                qty = c.getDouble(0)
            }
            val next = qty + delta
            if (next < -0.0001) return false
            database.execSQL(
                """
                UPDATE catalog_items SET saleable_qty = ?
                WHERE warehouse_id = ? AND stock_item_id = ?
                """.trimIndent(),
                arrayOf(next.coerceAtLeast(0.0), warehouseId, stockItemId),
            )
            database.setTransactionSuccessful()
            return true
        } finally {
            database.endTransaction()
        }
    }

    override fun enqueueSale(sale: PendingOfflineSale) {
        db().execSQL(
            """
            INSERT OR REPLACE INTO pending_sales (
              client_sale_id, warehouse_id, currency, exchange_rate, device_id,
              lines_json, tenders_json, receipt_email, receipt_whatsapp, receipt_phone,
              sold_at_ms, status, last_error, server_invoice_id
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(
                sale.clientSaleId,
                sale.warehouseId,
                sale.currency,
                sale.exchangeRate,
                sale.deviceId,
                sale.linesJson,
                sale.tendersJson,
                sale.receiptEmail,
                sale.receiptWhatsapp,
                sale.receiptPhone,
                sale.soldAtEpochMs,
                sale.status.name,
                sale.lastError,
                sale.serverInvoiceId,
            ),
        )
    }

    override fun pendingSales(includeTerminal: Boolean): List<PendingOfflineSale> {
        val sql = if (includeTerminal) {
            "SELECT * FROM pending_sales ORDER BY sold_at_ms"
        } else {
            """
            SELECT * FROM pending_sales
            WHERE status IN ('Pending','Syncing','Conflict','Failed')
            ORDER BY sold_at_ms
            """.trimIndent()
        }
        val out = mutableListOf<PendingOfflineSale>()
        db().rawQuery(sql, emptyArray()).use { c ->
            val idx = (0 until c.columnCount).associateBy { c.getColumnName(it) }
            while (c.moveToNext()) {
                out += PendingOfflineSale(
                    clientSaleId = c.getString(idx.getValue("client_sale_id")),
                    warehouseId = c.getString(idx.getValue("warehouse_id")),
                    currency = c.getString(idx.getValue("currency")),
                    exchangeRate = c.getDouble(idx.getValue("exchange_rate")),
                    deviceId = c.nullString(idx.getValue("device_id")),
                    linesJson = c.getString(idx.getValue("lines_json")),
                    tendersJson = c.getString(idx.getValue("tenders_json")),
                    receiptEmail = c.nullString(idx.getValue("receipt_email")),
                    receiptWhatsapp = c.nullString(idx.getValue("receipt_whatsapp")),
                    receiptPhone = c.nullString(idx.getValue("receipt_phone")),
                    soldAtEpochMs = c.getLong(idx.getValue("sold_at_ms")),
                    status = PendingSaleStatus.valueOf(c.getString(idx.getValue("status"))),
                    lastError = c.nullString(idx.getValue("last_error")),
                    serverInvoiceId = c.nullString(idx.getValue("server_invoice_id")),
                )
            }
        }
        return out
    }

    override fun markSaleStatus(
        clientSaleId: String,
        status: PendingSaleStatus,
        error: String?,
        serverInvoiceId: String?,
    ) {
        db().execSQL(
            """
            UPDATE pending_sales
            SET status = ?, last_error = ?,
                server_invoice_id = COALESCE(?, server_invoice_id)
            WHERE client_sale_id = ?
            """.trimIndent(),
            arrayOf(status.name, error, serverInvoiceId, clientSaleId),
        )
    }

    override fun lastPulledAtEpochMs(warehouseId: String): Long? {
        db().rawQuery(
            "SELECT pulled_at_ms FROM snapshot_meta WHERE warehouse_id = ?",
            arrayOf(warehouseId),
        ).use { c ->
            if (!c.moveToFirst()) return null
            return c.getLong(0)
        }
    }

    override fun close() {
        helper.close()
        passphrase.fill('\u0000')
    }

    private fun net.sqlcipher.Cursor.nullString(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private class Helper(context: Context) : SQLiteOpenHelper(
        context.applicationContext,
        DB_NAME,
        null,
        DB_VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE catalog_items (
                  warehouse_id TEXT NOT NULL,
                  stock_item_id TEXT NOT NULL,
                  oem_part_number TEXT NOT NULL,
                  description TEXT,
                  uom_id TEXT NOT NULL,
                  unit_price REAL NOT NULL,
                  core_charge REAL NOT NULL DEFAULT 0,
                  saleable_qty REAL NOT NULL DEFAULT 0,
                  currency TEXT NOT NULL,
                  PRIMARY KEY (warehouse_id, stock_item_id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE snapshot_meta (
                  warehouse_id TEXT PRIMARY KEY,
                  pulled_at_ms INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE pending_sales (
                  client_sale_id TEXT PRIMARY KEY,
                  warehouse_id TEXT NOT NULL,
                  currency TEXT NOT NULL,
                  exchange_rate REAL NOT NULL DEFAULT 1,
                  device_id TEXT,
                  lines_json TEXT NOT NULL,
                  tenders_json TEXT NOT NULL,
                  receipt_email TEXT,
                  receipt_whatsapp TEXT,
                  receipt_phone TEXT,
                  sold_at_ms INTEGER NOT NULL,
                  status TEXT NOT NULL,
                  last_error TEXT,
                  server_invoice_id TEXT
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val DB_NAME = "gtr_pos_offline.db"
        private const val DB_VERSION = 1

        fun open(context: Context): SqlCipherOfflinePosStore {
            SQLiteDatabase.loadLibs(context.applicationContext)
            val bytes = OfflinePosPassphrase.getOrCreate(context)
            val chars = CharArray(bytes.size) { i ->
                (bytes[i].toInt() and 0xFF).toChar()
            }
            return SqlCipherOfflinePosStore(Helper(context), chars)
        }
    }
}
