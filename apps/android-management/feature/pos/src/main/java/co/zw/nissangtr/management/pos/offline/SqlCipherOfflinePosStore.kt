package co.zw.nissangtr.management.pos.offline

import android.content.Context
import co.zw.nissangtr.management.rpc.CatalogPartHit
import co.zw.nissangtr.management.rpc.EpcDiagramPart
import co.zw.nissangtr.management.rpc.EpcDiagramResponse
import co.zw.nissangtr.management.rpc.EpcDiagramSummary
import co.zw.nissangtr.management.rpc.EpcHotspot
import co.zw.nissangtr.management.rpc.EpcMaker
import co.zw.nissangtr.management.rpc.EpcModel
import co.zw.nissangtr.management.rpc.EpcSection
import co.zw.nissangtr.management.rpc.EpcVariant
import co.zw.nissangtr.management.rpc.PosSaleVehicleSelection
import co.zw.nissangtr.management.rpc.PosPopularItemKind
import co.zw.nissangtr.management.rpc.PosPopularPin
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteOpenHelper


internal fun epcSearchTerms(vararg values: String?): List<String> =
    values.asSequence()
        .filterNotNull()
        .flatMap { value -> value.lowercase().split(Regex("[^a-z0-9]+")) .asSequence() }
        .map(String::trim)
        .filter { it.length >= 2 }
        .distinct()
        .take(64)
        .toList()

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

    override fun listPopularPins(userId: String, includeDeleted: Boolean): List<LocalPopularPinRecord> {
        val out = mutableListOf<LocalPopularPinRecord>()
        val sql = if (includeDeleted) {
            "SELECT item_type,item_key,label,subtitle,search_query,maker_slug,model_slug,category_name,subcategory_name,oem_part_number,image_url,updated_at,dirty_action,deleted FROM popular_pins WHERE user_id=? ORDER BY updated_at DESC"
        } else {
            "SELECT item_type,item_key,label,subtitle,search_query,maker_slug,model_slug,category_name,subcategory_name,oem_part_number,image_url,updated_at,dirty_action,deleted FROM popular_pins WHERE user_id=? AND deleted=0 ORDER BY updated_at DESC"
        }
        db().rawQuery(sql, arrayOf(userId)).use { c ->
            while (c.moveToNext()) {
                val pin = PosPopularPin(
                    kind = PosPopularItemKind.fromRpc(c.getString(0)),
                    itemKey = c.getString(1),
                    label = c.getString(2),
                    subtitle = c.nullString(3),
                    searchQuery = c.getString(4),
                    makerSlug = c.nullString(5),
                    modelSlug = c.nullString(6),
                    categoryName = c.nullString(7),
                    subcategoryName = c.nullString(8),
                    oemPartNumber = c.nullString(9),
                    imageUrl = c.nullString(10),
                    updatedAt = c.nullString(11),
                )
                out += LocalPopularPinRecord(
                    userId = userId,
                    pin = pin,
                    dirtyAction = c.nullString(12)?.let { PopularPinDirtyAction.valueOf(it) },
                    deleted = c.getInt(13) != 0,
                )
            }
        }
        return out
    }

    override fun upsertPopularPin(userId: String, pin: PosPopularPin, dirtyAction: PopularPinDirtyAction?) {
        db().execSQL(
            """INSERT OR REPLACE INTO popular_pins(
               user_id,item_type,item_key,label,subtitle,search_query,maker_slug,model_slug,category_name,subcategory_name,oem_part_number,image_url,updated_at,dirty_action,deleted
               ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)""".trimIndent(),
            arrayOf(
                userId, pin.kind.rpcValue, pin.itemKey, pin.label, pin.subtitle, pin.searchQuery,
                pin.makerSlug, pin.modelSlug, pin.categoryName, pin.subcategoryName, pin.oemPartNumber, pin.imageUrl,
                pin.updatedAt ?: java.time.Instant.now().toString(), dirtyAction?.name,
            ),
        )
    }

    override fun markPopularPinDeleted(
        userId: String, kind: PosPopularItemKind, itemKey: String, dirtyAction: PopularPinDirtyAction?,
    ) {
        val existing = listPopularPins(userId, includeDeleted = true)
            .firstOrNull { it.pin.kind == kind && it.pin.itemKey == itemKey }
        val pin = existing?.pin ?: PosPopularPin(kind, itemKey, itemKey, searchQuery = itemKey)
        upsertPopularPin(userId, pin, dirtyAction)
        db().execSQL(
            "UPDATE popular_pins SET deleted=1, dirty_action=?, updated_at=? WHERE user_id=? AND item_type=? AND item_key=?",
            arrayOf(dirtyAction?.name, java.time.Instant.now().toString(), userId, kind.rpcValue, itemKey),
        )
    }

    override fun deletePopularPinRecord(userId: String, kind: PosPopularItemKind, itemKey: String) {
        db().execSQL(
            "DELETE FROM popular_pins WHERE user_id=? AND item_type=? AND item_key=?",
            arrayOf(userId, kind.rpcValue, itemKey),
        )
    }

    override fun replacePopularPins(userId: String, pins: List<PosPopularPin>) {
        val database = db()
        database.beginTransaction()
        try {
            database.execSQL("DELETE FROM popular_pins WHERE user_id=?", arrayOf(userId))
            pins.forEach { pin ->
                database.execSQL(
                    """INSERT INTO popular_pins(user_id,item_type,item_key,label,subtitle,search_query,maker_slug,model_slug,category_name,subcategory_name,oem_part_number,image_url,updated_at,dirty_action,deleted)
                       VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,NULL,0)""".trimIndent(),
                    arrayOf(userId,pin.kind.rpcValue,pin.itemKey,pin.label,pin.subtitle,pin.searchQuery,pin.makerSlug,pin.modelSlug,pin.categoryName,pin.subcategoryName,pin.oemPartNumber,pin.imageUrl,pin.updatedAt ?: java.time.Instant.now().toString()),
                )
            }
            database.setTransactionSuccessful()
        } finally { database.endTransaction() }
    }

    override fun replaceEpcCatalog(bundle: LocalEpcCatalogBundle) {
        val database = db()
        database.beginTransaction()
        try {
            listOf("epc_part_terms", "epc_hotspots", "epc_parts", "epc_diagrams", "epc_sections", "epc_variants", "epc_models", "epc_makers").forEach {
                database.execSQL("DELETE FROM $it")
            }
            bundle.makers.forEach { m ->
                database.execSQL(
                    "INSERT INTO epc_makers(slug,name,sort_order,model_count) VALUES(?,?,?,?)",
                    arrayOf(m.slug, m.name, m.sortOrder, m.modelCount),
                )
            }
            bundle.models.forEach { row ->
                val m = row.model
                database.execSQL(
                    "INSERT INTO epc_models(maker_slug,slug,display_name,body_type,sort_key,year_start,year_end) VALUES(?,?,?,?,?,?,?)",
                    arrayOf(row.makerSlug, m.slug, m.displayName, m.bodyType, m.sortKey, m.yearStart, m.yearEnd),
                )
            }
            bundle.variants.forEach { row ->
                val v = row.variant
                database.execSQL(
                    "INSERT INTO epc_variants(maker_slug,model_slug,slug,chassis_code,frame,grade,sales_region,year_label,engine_code) VALUES(?,?,?,?,?,?,?,?,?)",
                    arrayOf(row.makerSlug, row.modelSlug, v.slug, v.chassisCode, v.frame, v.grade, v.salesRegion, v.yearLabel, v.engineCode),
                )
            }
            bundle.sections.forEach { row ->
                val sec = row.section
                database.execSQL(
                    "INSERT INTO epc_sections(maker_slug,model_slug,variant_slug,slug,name,thumbnail_url,sort_order) VALUES(?,?,?,?,?,?,?)",
                    arrayOf(row.makerSlug, row.modelSlug, row.variantSlug, sec.slug, sec.name, sec.thumbnailUrl, sec.sortOrder),
                )
            }
            bundle.diagrams.forEach { row ->
                val d = row.diagram
                database.execSQL(
                    "INSERT INTO epc_diagrams(maker_slug,model_slug,variant_slug,section_slug,diagram_slug,title,storage_path,image_url,image_blob) VALUES(?,?,?,?,?,?,?,?,?)",
                    arrayOf(row.makerSlug, row.modelSlug, row.variantSlug, row.sectionSlug, d.diagramSlug ?: row.sectionSlug, d.diagramTitle, d.storagePath, d.imageUrl, row.imageBytes),
                )
                d.parts.forEach { part ->
                    database.execSQL(
                        "INSERT INTO epc_parts(maker_slug,model_slug,variant_slug,section_slug,diagram_slug,oem_part_number,pnc_code,category_name,subcategory_name,stock_item_id,stock_description,chassis_code,engine_code) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf(row.makerSlug, row.modelSlug, row.variantSlug, row.sectionSlug, d.diagramSlug ?: row.sectionSlug, part.oemPartNumber, part.pncCode, part.categoryName, part.subcategoryName, part.stockItemId, part.stockDescription, part.chassisCode, part.engineCode),
                    )
                    epcSearchTerms(
                        part.oemPartNumber, part.pncCode, part.stockDescription, part.categoryName, part.subcategoryName,
                    ).forEach { term ->
                        database.execSQL(
                            "INSERT OR IGNORE INTO epc_part_terms(model_slug,oem_part_number,term) VALUES(?,?,?)",
                            arrayOf(row.modelSlug, part.oemPartNumber, term),
                        )
                    }
                }
                d.hotspots.forEach { h ->
                    database.execSQL(
                        "INSERT INTO epc_hotspots(maker_slug,model_slug,variant_slug,section_slug,diagram_slug,oem_part_number,pnc_code,bbox_x,bbox_y,bbox_width,bbox_height) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        arrayOf(row.makerSlug, row.modelSlug, row.variantSlug, row.sectionSlug, d.diagramSlug ?: row.sectionSlug, h.oem, h.pncCode, h.bboxX, h.bboxY, h.bboxWidth, h.bboxHeight),
                    )
                }
            }
            database.execSQL("INSERT OR REPLACE INTO epc_meta(id,synced_at_ms) VALUES(1,?)", arrayOf(bundle.syncedAtEpochMs))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    override fun hasEpcCatalog(): Boolean = db().rawQuery("SELECT 1 FROM epc_models LIMIT 1", emptyArray()).use { it.moveToFirst() }

    override fun epcCatalogSyncedAtEpochMs(): Long? = db().rawQuery("SELECT synced_at_ms FROM epc_meta WHERE id=1", emptyArray()).use {
        if (it.moveToFirst()) it.getLong(0) else null
    }

    override fun listEpcMakers(): List<EpcMaker> {
        val out = mutableListOf<EpcMaker>()
        db().rawQuery("SELECT slug,name,sort_order,model_count FROM epc_makers ORDER BY sort_order,name", emptyArray()).use { c ->
            while (c.moveToNext()) out += EpcMaker(c.getString(0), c.getString(1), c.getInt(2), if (c.isNull(3)) null else c.getInt(3))
        }
        return out
    }

    override fun listEpcModels(makerSlug: String): List<EpcModel> {
        val out = mutableListOf<EpcModel>()
        db().rawQuery("SELECT slug,display_name,body_type,sort_key,year_start,year_end FROM epc_models WHERE maker_slug=? ORDER BY sort_key", arrayOf(makerSlug)).use { c ->
            while (c.moveToNext()) out += EpcModel(c.getString(0), c.getString(1), c.nullString(2), c.getString(3), if(c.isNull(4)) null else c.getInt(4), if(c.isNull(5)) null else c.getInt(5))
        }
        return out
    }

    override fun listEpcVariants(makerSlug: String, modelSlug: String): List<EpcVariant> {
        val out = mutableListOf<EpcVariant>()
        db().rawQuery("SELECT slug,chassis_code,frame,grade,sales_region,year_label,engine_code FROM epc_variants WHERE maker_slug=? AND model_slug=? ORDER BY chassis_code,slug", arrayOf(makerSlug, modelSlug)).use { c ->
            while (c.moveToNext()) out += EpcVariant(c.getString(0), c.getString(1), c.nullString(2), c.nullString(3), c.nullString(4), c.nullString(5), c.nullString(6))
        }
        return out
    }

    override fun listEpcSections(makerSlug: String, modelSlug: String, variantSlug: String): List<EpcSection> {
        val out = mutableListOf<EpcSection>()
        db().rawQuery("SELECT slug,name,thumbnail_url,sort_order FROM epc_sections WHERE maker_slug=? AND model_slug=? AND variant_slug=? ORDER BY sort_order,name", arrayOf(makerSlug, modelSlug, variantSlug)).use { c ->
            while (c.moveToNext()) out += EpcSection(c.getString(0), c.getString(1), c.nullString(2), c.getInt(3))
        }
        return out
    }

    override fun listEpcDiagrams(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String): List<EpcDiagramSummary> {
        val out = mutableListOf<EpcDiagramSummary>()
        db().rawQuery(
            "SELECT diagram_slug,title,storage_path,image_url FROM epc_diagrams WHERE maker_slug=? AND model_slug=? AND variant_slug=? AND section_slug=? ORDER BY diagram_slug",
            arrayOf(makerSlug, modelSlug, variantSlug, sectionSlug),
        ).use { c ->
            while (c.moveToNext()) out += EpcDiagramSummary(c.getString(0), c.nullString(1) ?: c.getString(0), c.nullString(2), c.nullString(3))
        }
        return out
    }

    override fun getEpcDiagram(makerSlug: String, modelSlug: String, variantSlug: String, sectionSlug: String): EpcDiagramResponse {
        val slug = listEpcDiagrams(makerSlug, modelSlug, variantSlug, sectionSlug).firstOrNull()?.slug
            ?: return EpcDiagramResponse()
        return getEpcDiagramBySlug(makerSlug, modelSlug, variantSlug, sectionSlug, slug)
    }

    override fun getEpcDiagramBySlug(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
        diagramSlug: String,
    ): EpcDiagramResponse {
        var response = EpcDiagramResponse()
        db().rawQuery(
            "SELECT diagram_slug,title,storage_path,image_url,image_blob FROM epc_diagrams WHERE maker_slug=? AND model_slug=? AND variant_slug=? AND section_slug=? AND diagram_slug=? LIMIT 1",
            arrayOf(makerSlug, modelSlug, variantSlug, sectionSlug, diagramSlug),
        ).use { c ->
            if (c.moveToFirst()) response = EpcDiagramResponse(c.nullString(0), c.nullString(1), c.nullString(2), c.nullString(3), if(c.isNull(4)) null else c.getBlob(4))
        }
        val parts = mutableListOf<EpcDiagramPart>()
        db().rawQuery(
            "SELECT oem_part_number,pnc_code,category_name,subcategory_name,stock_item_id,stock_description,chassis_code,engine_code FROM epc_parts WHERE maker_slug=? AND model_slug=? AND variant_slug=? AND section_slug=? AND diagram_slug=? ORDER BY oem_part_number",
            arrayOf(makerSlug, modelSlug, variantSlug, sectionSlug, diagramSlug),
        ).use { c ->
            while (c.moveToNext()) parts += EpcDiagramPart(c.getString(0), c.nullString(1), c.nullString(2), c.nullString(3), c.nullString(4), c.nullString(5), c.nullString(6), c.nullString(7))
        }
        val hotspots = mutableListOf<EpcHotspot>()
        db().rawQuery(
            "SELECT oem_part_number,pnc_code,bbox_x,bbox_y,bbox_width,bbox_height FROM epc_hotspots WHERE maker_slug=? AND model_slug=? AND variant_slug=? AND section_slug=? AND diagram_slug=?",
            arrayOf(makerSlug, modelSlug, variantSlug, sectionSlug, diagramSlug),
        ).use { c ->
            while (c.moveToNext()) hotspots += EpcHotspot(c.getString(0), c.nullString(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getDouble(5))
        }
        return response.copy(parts = parts, hotspots = hotspots)
    }

    override fun searchEpcParts(vehicle: PosSaleVehicleSelection, query: String, limit: Int): List<CatalogPartHit> {
        val terms = epcSearchTerms(query).take(4)
        if (terms.isEmpty()) return emptyList()
        val existsClauses = terms.indices.joinToString("\n") { index ->
            "AND EXISTS (SELECT 1 FROM epc_part_terms t$index WHERE t$index.model_slug=p.model_slug AND t$index.oem_part_number=p.oem_part_number AND t$index.term>=? AND t$index.term<?)"
        }
        val args = mutableListOf<String>()
        args += vehicle.modelSlug
        args += vehicle.chassisCode
        args += vehicle.engineCode
        terms.forEach { term ->
            args += term
            args += term + '\uFFFF'
        }
        args += limit.coerceIn(1, 200).toString()
        val out = mutableListOf<CatalogPartHit>()
        db().rawQuery(
            """SELECT DISTINCT p.oem_part_number,p.pnc_code,p.category_name,p.subcategory_name,p.chassis_code,p.engine_code
               FROM epc_parts p
               WHERE p.model_slug=? AND (p.chassis_code=? OR p.chassis_code IS NULL OR p.chassis_code='')
                 AND (p.engine_code=? OR p.engine_code IS NULL OR p.engine_code='')
                 $existsClauses
               ORDER BY p.oem_part_number LIMIT ?""".trimIndent(),
            args.toTypedArray(),
        ).use { c ->
            while (c.moveToNext()) out += CatalogPartHit(
                oemPartNumber = c.getString(0),
                pncCode = c.nullString(1),
                categoryName = c.nullString(2),
                subcategoryName = c.nullString(3),
                chassisCode = c.nullString(4),
                engineCode = c.nullString(5),
            )
        }
        return out
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
              lines_json, tenders_json, receipt_email, receipt_whatsapp, receipt_phone, vehicle_json, vehicle_contexts_json,
              sold_at_ms, status, last_error, server_invoice_id
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                sale.vehicleJson,
                sale.vehicleContextsJson,
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
                    vehicleJson = idx["vehicle_json"]?.let { c.nullString(it) },
                    vehicleContextsJson = idx["vehicle_contexts_json"]?.let { c.nullString(it) },
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
                  vehicle_json TEXT,
                  vehicle_contexts_json TEXT,
                  sold_at_ms INTEGER NOT NULL,
                  status TEXT NOT NULL,
                  last_error TEXT,
                  server_invoice_id TEXT
                )
                """.trimIndent(),
            )
            createEpcTables(db)
            createPopularPinsTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE pending_sales ADD COLUMN vehicle_json TEXT")
            }
            if (oldVersion < 3) createEpcTables(db)
            if (oldVersion == 3 && newVersion >= 4) {
                listOf("epc_hotspots", "epc_parts", "epc_diagrams", "epc_sections", "epc_variants", "epc_models", "epc_makers", "epc_meta").forEach {
                    db.execSQL("DROP TABLE IF EXISTS $it")
                }
                createEpcTables(db)
            }
            if (oldVersion < 5 && newVersion >= 5) {
                createEpcTables(db)
                rebuildEpcSearchTerms(db)
            }
            if (oldVersion < 6 && newVersion >= 6) {
                db.execSQL("ALTER TABLE pending_sales ADD COLUMN vehicle_contexts_json TEXT")
            }
            if (oldVersion < 7 && newVersion >= 7) {
                createPopularPinsTable(db)
            }
        }

        private fun createPopularPinsTable(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS popular_pins(
                user_id TEXT NOT NULL,item_type TEXT NOT NULL,item_key TEXT NOT NULL,label TEXT NOT NULL,subtitle TEXT,search_query TEXT NOT NULL,
                maker_slug TEXT,model_slug TEXT,category_name TEXT,subcategory_name TEXT,oem_part_number TEXT,image_url TEXT,updated_at TEXT NOT NULL,
                dirty_action TEXT,deleted INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(user_id,item_type,item_key))""".trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS popular_pins_user_idx ON popular_pins(user_id,deleted,updated_at)")
        }

        private fun createEpcTables(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_meta(id INTEGER PRIMARY KEY CHECK(id=1), synced_at_ms INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_makers(slug TEXT PRIMARY KEY,name TEXT NOT NULL,sort_order INTEGER NOT NULL,model_count INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_models(maker_slug TEXT NOT NULL,slug TEXT NOT NULL,display_name TEXT NOT NULL,body_type TEXT,sort_key TEXT NOT NULL,year_start INTEGER,year_end INTEGER,PRIMARY KEY(maker_slug,slug))")
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_variants(maker_slug TEXT NOT NULL,model_slug TEXT NOT NULL,slug TEXT NOT NULL,chassis_code TEXT NOT NULL,frame TEXT,grade TEXT,sales_region TEXT,year_label TEXT,engine_code TEXT,PRIMARY KEY(maker_slug,model_slug,slug))")
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_sections(maker_slug TEXT NOT NULL,model_slug TEXT NOT NULL,variant_slug TEXT NOT NULL,slug TEXT NOT NULL,name TEXT NOT NULL,thumbnail_url TEXT,sort_order INTEGER NOT NULL,PRIMARY KEY(maker_slug,model_slug,variant_slug,slug))")
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_diagrams(maker_slug TEXT NOT NULL,model_slug TEXT NOT NULL,variant_slug TEXT NOT NULL,section_slug TEXT NOT NULL,diagram_slug TEXT NOT NULL,title TEXT,storage_path TEXT,image_url TEXT,image_blob BLOB,PRIMARY KEY(maker_slug,model_slug,variant_slug,section_slug,diagram_slug))")
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_parts(id INTEGER PRIMARY KEY AUTOINCREMENT,maker_slug TEXT NOT NULL,model_slug TEXT NOT NULL,variant_slug TEXT NOT NULL,section_slug TEXT NOT NULL,diagram_slug TEXT NOT NULL,oem_part_number TEXT NOT NULL,pnc_code TEXT,category_name TEXT,subcategory_name TEXT,stock_item_id TEXT,stock_description TEXT,chassis_code TEXT,engine_code TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_hotspots(id INTEGER PRIMARY KEY AUTOINCREMENT,maker_slug TEXT NOT NULL,model_slug TEXT NOT NULL,variant_slug TEXT NOT NULL,section_slug TEXT NOT NULL,diagram_slug TEXT NOT NULL,oem_part_number TEXT NOT NULL,pnc_code TEXT,bbox_x REAL NOT NULL,bbox_y REAL NOT NULL,bbox_width REAL NOT NULL,bbox_height REAL NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS epc_part_terms(model_slug TEXT NOT NULL,oem_part_number TEXT NOT NULL,term TEXT NOT NULL,PRIMARY KEY(model_slug,oem_part_number,term))")
            db.execSQL("CREATE INDEX IF NOT EXISTS epc_models_maker_idx ON epc_models(maker_slug,sort_key)")
            db.execSQL("CREATE INDEX IF NOT EXISTS epc_variants_model_idx ON epc_variants(maker_slug,model_slug,chassis_code,engine_code)")
            db.execSQL("CREATE INDEX IF NOT EXISTS epc_sections_variant_idx ON epc_sections(maker_slug,model_slug,variant_slug,sort_order)")
            db.execSQL("CREATE INDEX IF NOT EXISTS epc_parts_fitment_idx ON epc_parts(model_slug,chassis_code,engine_code)")
            db.execSQL("CREATE INDEX IF NOT EXISTS epc_parts_oem_idx ON epc_parts(oem_part_number)")
            db.execSQL("CREATE INDEX IF NOT EXISTS epc_parts_pnc_idx ON epc_parts(pnc_code)")
            db.execSQL("CREATE INDEX IF NOT EXISTS epc_part_terms_search_idx ON epc_part_terms(model_slug,term,oem_part_number)")
        }

        private fun rebuildEpcSearchTerms(db: SQLiteDatabase) {
            db.execSQL("DELETE FROM epc_part_terms")
            db.rawQuery(
                "SELECT DISTINCT model_slug,oem_part_number,pnc_code,stock_description,category_name,subcategory_name FROM epc_parts",
                emptyArray(),
            ).use { c ->
                while (c.moveToNext()) {
                    val modelSlug = c.getString(0)
                    val oem = c.getString(1)
                    epcSearchTerms(
                        oem,
                        if (c.isNull(2)) null else c.getString(2),
                        if (c.isNull(3)) null else c.getString(3),
                        if (c.isNull(4)) null else c.getString(4),
                        if (c.isNull(5)) null else c.getString(5),
                    ).forEach { term ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO epc_part_terms(model_slug,oem_part_number,term) VALUES(?,?,?)",
                            arrayOf(modelSlug, oem, term),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val DB_NAME = "gtr_pos_offline.db"
        private const val DB_VERSION = 7

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
