package co.zw.nissangtr.management.rpc

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stub so HR / POS / warehouse / dispatch / chat screens compile and exercise
 * flows without a configured Supabase project. Live: [SupabaseRpcClient] via [RpcClientFactory].
 *
 * Documented live RPC → param map:
 * - [RpcNames.CLOCK_ATTENDANCE]: p_employee_id, p_event_type, p_occurred_at?, p_notes?
 * - [RpcNames.CREATE_POS_CART]: p_warehouse_id, p_customer_id?, p_currency, p_fulfillment_mode?
 * - [RpcNames.ADD_CART_LINE]: p_cart_id, p_stock_item_id, p_uom_id, p_qty
 * - [RpcNames.ADD_CART_LINE_FROM_QR]: p_cart_id, p_qr_payload, p_qty?
 * - [RpcNames.CHECKOUT_POS_CART]: p_cart_id, p_receipt_email?, p_receipt_whatsapp_e164?, p_receipt_phone_e164?
 * - [RpcNames.SEARCH_CATALOG]: p_mode, p_query
 * - [RpcNames.CREATE_POS_SCAN_SESSION]: p_cart_id → session_id, pairing_code, expires_at
 * - [RpcNames.CLAIM_POS_SCAN_SESSION]: p_pairing_code → session_id
 * - [RpcNames.REVOKE_POS_SCAN_SESSION]: p_session_id
 * - listPosCartLines / getPosCartCustomerId / listWarehouses: PostgREST
 * - lookupStockItemByOem: PostgREST stock_items by oem_part_number (not an RPC)
 * - [RpcNames.POST_STOCK_RECEIPT]: p_to_warehouse_id, p_notes?, p_lines
 * - [RpcNames.CREATE_STOCK_TRANSFER]: p_from_warehouse_id, p_to_warehouse_id, p_notes?, p_lines
 * - [RpcNames.APPROVE_STOCK_TRANSFER] / [RpcNames.REJECT_STOCK_TRANSFER]: p_entry_id
 * - [RpcNames.CREATE_STOCK_RECONCILIATION_DRAFT]: p_warehouse_id, p_scope, p_item_ids?, p_notes?, p_currency, p_exchange_rate?
 * - [RpcNames.UPSERT_STOCK_RECONCILIATION_LINES]: p_reconciliation_id, p_lines
 * - [RpcNames.SUBMIT_STOCK_RECONCILIATION] / [RpcNames.APPROVE_STOCK_RECONCILIATION]: p_reconciliation_id
 * - [RpcNames.CANCEL_STOCK_RECONCILIATION]: p_reconciliation_id, p_notes?
 * - [RpcNames.CREATE_PICK_LIST]: p_sales_invoice_id, p_lines?
 * - [RpcNames.CONFIRM_PICK_LINES]: p_pick_list_id, p_lines
 * - [RpcNames.CREATE_DELIVERY_NOTE]: p_sales_invoice_id, p_lines, p_pick_list_id?
 * - [RpcNames.SUBMIT_DELIVERY_NOTE]: p_delivery_note_id
 * - [RpcNames.CANCEL_DELIVERY_NOTE]: p_delivery_note_id
 * - [RpcNames.CREATE_DELIVERY_JOB]: p_delivery_note_id, p_assignee_user_id?, p_eta_at?, p_notes?
 * - [RpcNames.SET_DELIVERY_JOB_GEO]: p_delivery_job_id, p_pickup_lat?, p_pickup_lng?, p_dropoff_lat?, p_dropoff_lng?
 * - [RpcNames.UPDATE_DELIVERY_JOB_STATUS]: p_delivery_job_id, p_status → jsonb {delivery_job_id, track_token?}
 * - [RpcNames.INGEST_DELIVERY_LOCATION]: p_delivery_job_id, p_lat, p_lng, p_recorded_at?, p_accuracy_m?
 *   (management must not call from UI — delivery app sole producer)
 * - [RpcNames.SUGGEST_DELIVERY_ASSIGNEES]: p_delivery_job_id, p_limit?
 * - [RpcNames.ASSIGN_DELIVERY_JOB]: p_delivery_job_id, p_assignee_user_id, p_override?
 * - [RpcNames.OPTIMIZE_DRIVER_STOPS]: p_driver_user_id
 * - [RpcNames.GET_DELIVERY_TRACK_POINT]: p_delivery_job_id (staff view)
 * - [RpcNames.MINT_DELIVERY_TRACK_TOKEN]: p_delivery_job_id, p_ttl? → remint/rotate only
 * - [RpcNames.GENERATE_DELIVERY_POD_OTP]: p_delivery_job_id, p_ttl? → 6-digit once
 * - listOpenPanicEvents / acknowledgePanicEvent: PostgREST panic_events
 * - [RpcNames.CLAIM_CHAT_THREAD] / [RpcNames.CLOSE_CHAT_THREAD] / [RpcNames.MARK_CHAT_THREAD_READ]: p_thread_id
 * - [RpcNames.POST_CHAT_MESSAGE]: p_thread_id, p_body
 * - [RpcNames.CHAT_UNREAD_COUNT]: p_thread_id?
 * - listStaffChatThreads / listChatMessages: PostgREST (not RPCs)
 * - listMyStaffRoles: PostgREST staff_roles
 * - searchCustomers / listSuppliers / listBlanketPurchaseOrders / listWarehouseBins /
 *   listConsignmentEntries / loadCustomerCredit: PostgREST
 * - [RpcNames.CREATE_BLANKET_PURCHASE_ORDER]: p_supplier_id, p_warehouse_id, p_currency,
 *   p_exchange_rate, p_blanket_max_value, p_lines, p_notes?, p_expected_date?
 * - [RpcNames.SUBMIT_PURCHASE_ORDER]: p_purchase_order_id
 * - [RpcNames.CREATE_BLANKET_RELEASE]: p_blanket_purchase_order_id, p_lines, p_notes?
 * - [RpcNames.CREATE_WAREHOUSE_BIN] / [RpcNames.UPDATE_WAREHOUSE_BIN] /
 *   [RpcNames.DEACTIVATE_WAREHOUSE_BIN] / [RpcNames.SET_STOCK_LEVEL_BIN]
 * - [RpcNames.GET_PICK_PATH_HINTS]: p_warehouse_id, p_stock_item_ids?
 * - [RpcNames.CREATE_CONSIGNMENT_ENTRY_DRAFT] / [RpcNames.ADD_CONSIGNMENT_ENTRY_LINE] /
 *   [RpcNames.SUBMIT_CONSIGNMENT_ENTRY] / [RpcNames.CANCEL_CONSIGNMENT_ENTRY]
 * - [RpcNames.SET_CUSTOMER_CREDIT]: p_customer_id, p_credit_limit?, p_credit_hold?
 * - [RpcNames.LIST_FLEET_VEHICLES]: p_status?
 * - [RpcNames.UPSERT_FLEET_VEHICLE]: p_plate, p_label?, p_status?, p_assigned_driver_user_id?,
 *   p_notes?, p_id?
 * - [RpcNames.SET_FLEET_VEHICLE_STATUS]: p_id, p_status
 */
class FakeRpcClient : RpcClient {
    private val dnSeq = AtomicInteger(1)
    private val plSeq = AtomicInteger(1)
    private val jobSeq = AtomicInteger(1)
    private val openCarts = mutableSetOf<String>()
    /** cartId → lines */
    private val cartLines = mutableMapOf<String, MutableList<PosCartLineSummary>>()
    /** cartId → customer_id */
    private val cartCustomers = mutableMapOf<String, String>()
    /** pairing_code → (sessionId, cartId) */
    private val openScanSessions = mutableMapOf<String, Pair<String, String>>()
    private val claimedSessions = mutableSetOf<String>()
    /** sessionId → cartId (after claim) */
    private val claimedSessionCarts = mutableMapOf<String, String>()
    private val pendingTransfers = mutableSetOf<String>()
    private val reconDrafts = mutableSetOf<String>()
    private val deliveryJobs = mutableMapOf<String, Pair<String, String>>() // id → (dnId, status)
    private val jobAssignees = mutableMapOf<String, String>() // jobId → driverUserId
    /** jobId → (pickupLat, pickupLng, dropoffLat, dropoffLng) */
    private val jobCoords = mutableMapOf<String, JobCoords>()
    private val panicEvents = mutableListOf(
        PanicEventSummary(
            id = OPEN_PANIC_ID,
            driverUserId = FAKE_DRIVER_USER_ID,
            deliveryJobId = null,
            lat = -17.8292,
            lng = 31.0522,
            createdAt = "2026-07-25T09:00:00Z",
            acknowledgedAt = null,
            acknowledgedBy = null,
        ),
    )
    /** Exposed for unit/demo checks — count of successful GPS ingests. */
    val ingestedLocationCount: AtomicInteger = AtomicInteger(0)
    private val deliveryNotes = mutableListOf(
        DeliveryNoteSummary(
            id = "00000000-0000-4000-8000-0000000000d1",
            documentNumber = "DN-SEED-001",
            salesInvoiceId = "00000000-0000-4000-8000-0000000000i1",
            status = "draft",
        ),
    )
    private val pickLists = mutableListOf(
        PickListSummary(
            id = "00000000-0000-4000-8000-0000000000p1",
            documentNumber = "PL-SEED-001",
            salesInvoiceId = "00000000-0000-4000-8000-0000000000i1",
            status = "draft",
        ),
    )

    private val fakeStaffUserId = FAKE_STAFF_USER_ID
    private val chatThreads = mutableListOf(
        ChatThreadSummary(
            id = OPEN_THREAD_ID,
            kind = "support",
            status = "open",
            subject = "Brake pads fitment",
            assignedTo = null,
            lastMessageAt = "2026-07-25T08:00:00Z",
            createdAt = "2026-07-25T07:55:00Z",
        ),
        ChatThreadSummary(
            id = MINE_THREAD_ID,
            kind = "parts",
            status = "assigned",
            subject = "OEM 12345 stock?",
            assignedTo = FAKE_STAFF_USER_ID,
            lastMessageAt = "2026-07-25T08:10:00Z",
            createdAt = "2026-07-25T08:05:00Z",
        ),
        ChatThreadSummary(
            id = CLOSED_THREAD_ID,
            kind = "support",
            status = "closed",
            subject = "Closed sample",
            assignedTo = FAKE_STAFF_USER_ID,
            lastMessageAt = "2026-07-24T12:00:00Z",
            createdAt = "2026-07-24T11:00:00Z",
        ),
    )
    private val chatMessages = mutableMapOf(
        OPEN_THREAD_ID to mutableListOf(
            ChatMessageSummary(
                id = "00000000-0000-4000-8000-0000000000m1",
                threadId = OPEN_THREAD_ID,
                senderUserId = FAKE_CUSTOMER_USER_ID,
                senderKind = "customer",
                body = "Do you have front pads for GTR R35?",
                createdAt = "2026-07-25T08:00:00Z",
            ),
        ),
        MINE_THREAD_ID to mutableListOf(
            ChatMessageSummary(
                id = "00000000-0000-4000-8000-0000000000m2",
                threadId = MINE_THREAD_ID,
                senderUserId = FAKE_CUSTOMER_USER_ID,
                senderKind = "customer",
                body = "Is OEM 12345 in stock?",
                createdAt = "2026-07-25T08:05:00Z",
            ),
            ChatMessageSummary(
                id = "00000000-0000-4000-8000-0000000000m3",
                threadId = MINE_THREAD_ID,
                senderUserId = FAKE_STAFF_USER_ID,
                senderKind = "staff",
                body = "Checking warehouse now.",
                createdAt = "2026-07-25T08:10:00Z",
            ),
        ),
        CLOSED_THREAD_ID to mutableListOf(
            ChatMessageSummary(
                id = "00000000-0000-4000-8000-0000000000m4",
                threadId = CLOSED_THREAD_ID,
                senderUserId = FAKE_CUSTOMER_USER_ID,
                senderKind = "customer",
                body = "Thanks, resolved.",
                createdAt = "2026-07-24T12:00:00Z",
            ),
        ),
    )
    private val chatUnread = mutableMapOf(
        OPEN_THREAD_ID to 1,
        MINE_THREAD_ID to 0,
        CLOSED_THREAD_ID to 0,
    )

    private val suppliers = mutableListOf(
        SupplierRef(
            id = FAKE_SUPPLIER_ID,
            code = "SUP-1",
            name = "Fake OEM Supplier",
        ),
    )

    private val customers = mutableListOf(
        CustomerOption(id = FAKE_CUSTOMER_ID, displayName = "Acme Motors (B2B)"),
        CustomerOption(id = FAKE_CUSTOMER_USER_ID, displayName = "Walk-in Sample"),
    )

    private val customerCredit = mutableMapOf(
        FAKE_CUSTOMER_ID to CustomerCreditSnapshot(
            customerId = FAKE_CUSTOMER_ID,
            creditLimit = 5_000.0,
            creditHold = false,
            openBalance = 1_250.0,
            currency = CurrencyCode.USD,
        ),
    )

    private val blankets = mutableListOf(
        BlanketSummary(
            id = FAKE_BLANKET_ID,
            documentNumber = "BPO-SEED-001",
            status = "submitted",
            supplierId = FAKE_SUPPLIER_ID,
            supplierName = "Fake OEM Supplier",
            warehouseId = FAKE_WAREHOUSE_ID,
            warehouseCode = "MAIN",
            currency = CurrencyCode.USD,
            blanketMaxValue = 10_000.0,
            blanketValueReleased = 9_200.0,
            expectedDate = java.time.LocalDate.now().plusDays(7).toString(),
            lines = listOf(
                BlanketLineSummary(
                    id = FAKE_BLANKET_LINE_ID,
                    lineNo = 1,
                    stockItemId = FAKE_STOCK_ITEM_ID,
                    oemPartNumber = "21410-JF00A",
                    qtyOrdered = 100.0,
                    qtyReleased = 96.0,
                    unitPrice = 25.0,
                    currency = CurrencyCode.USD,
                ),
            ),
        ),
    )

    private val warehouseBins = mutableListOf(
        WarehouseBinSummary(
            id = FAKE_BIN_A_ID,
            warehouseId = FAKE_WAREHOUSE_ID,
            code = "A-01-01",
            name = "Aisle A rack 1",
            pickPathSeq = 10,
            aisle = "A",
            rack = "01",
            shelf = "01",
            isActive = true,
        ),
        WarehouseBinSummary(
            id = FAKE_BIN_B_ID,
            warehouseId = FAKE_WAREHOUSE_ID,
            code = "B-02-03",
            name = "Aisle B rack 2",
            pickPathSeq = 20,
            aisle = "B",
            rack = "02",
            shelf = "03",
            isActive = true,
        ),
    )

    private val stockLevelBins = mutableMapOf(
        FAKE_STOCK_ITEM_ID to FAKE_BIN_A_ID,
    )

    private val consignmentEntries = mutableListOf(
        ConsignmentEntrySummary(
            id = FAKE_CONSIGNMENT_ID,
            documentNumber = "CNS-SEED-001",
            status = "draft",
            kind = ConsignmentKind.SUPPLIER_OWNED.rpcValue,
            purpose = ConsignmentPurpose.RECEIVE.rpcValue,
            warehouseId = FAKE_WAREHOUSE_ID,
            supplierId = FAKE_SUPPLIER_ID,
            currency = CurrencyCode.USD,
        ),
    )
    private val consignmentLineCounts = mutableMapOf(FAKE_CONSIGNMENT_ID to 0)

    override suspend fun clockAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        notes: String?,
    ): String {
        require(employeeId.isNotBlank()) { "employeeId required for ${RpcNames.CLOCK_ATTENDANCE}" }
        return UUID.randomUUID().toString()
    }

    override suspend fun createPosCart(
        warehouseId: String,
        currency: CurrencyCode,
        fulfillmentMode: FulfillmentMode,
        customerId: String?,
    ): String {
        require(warehouseId.isNotBlank()) { "warehouseId required for ${RpcNames.CREATE_POS_CART}" }
        val id = UUID.randomUUID().toString()
        openCarts.add(id)
        cartLines[id] = mutableListOf()
        if (!customerId.isNullOrBlank()) cartCustomers[id] = customerId
        return id
    }

    override suspend fun addCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.ADD_CART_LINE}" }
        require(stockItemId.isNotBlank()) { "stockItemId required" }
        require(uomId.isNotBlank()) { "uomId required" }
        require(qty > 0) { "qty must be > 0" }
        openCarts.add(cartId)
        val lineId = UUID.randomUUID().toString()
        val unit = 10.0
        val lines = cartLines.getOrPut(cartId) { mutableListOf() }
        lines.add(
            PosCartLineSummary(
                id = lineId,
                stockItemId = stockItemId,
                oemPartNumber = "OEM-$stockItemId".take(24),
                qty = qty,
                unitPrice = unit,
                lineTotal = unit * qty,
            ),
        )
        return lineId
    }

    override suspend fun addCartLineFromQr(
        cartId: String,
        qrPayload: String,
        qty: Double,
    ): String {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.ADD_CART_LINE_FROM_QR}" }
        require(qty > 0) { "qty must be > 0" }
        val m = INVENTORY_QR_REGEX.matchEntire(qrPayload.trim())
            ?: throw IllegalArgumentException("invalid inventory QR payload")
        val oem = m.groupValues[1]
        require(oem.isNotBlank()) { "OEM required in QR" }
        val ref = lookupStockItemByOem(oem)
        return addCartLine(cartId, ref.stockItemId, ref.uomId, qty)
    }

    override suspend fun checkoutPosCart(
        cartId: String,
        receiptEmail: String?,
        receiptWhatsappE164: String?,
        receiptPhoneE164: String?,
    ): CheckoutPosResult {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.CHECKOUT_POS_CART}" }
        val hadCustomer = cartCustomers.containsKey(cartId)
        var customerId = cartCustomers[cartId]
        val email = receiptEmail?.trim()?.lowercase()?.ifBlank { null }
        val wa = receiptWhatsappE164?.trim()?.ifBlank { null }
        // Fake bind: unique email/wa matching demo pattern binds a fake customer
        if (customerId == null && (email != null || wa != null)) {
            val bindable = email?.endsWith("@example.com") == true ||
                wa?.startsWith("+263") == true
            if (bindable) {
                customerId = UUID.nameUUIDFromBytes("cust:${email ?: wa}".toByteArray()).toString()
            }
        }
        openCarts.remove(cartId)
        cartLines.remove(cartId)
        cartCustomers.remove(cartId)
        return CheckoutPosResult(
            invoiceId = UUID.randomUUID().toString(),
            customerId = customerId,
            receiptEmail = email,
            receiptWhatsappE164 = wa,
            hadCustomerBeforeCheckout = hadCustomer,
        )
    }

    override suspend fun lookupStockItemByOem(oemPartNumber: String): StockItemRef {
        val oem = oemPartNumber.trim()
        require(oem.isNotBlank()) { "oemPartNumber required" }
        // Deterministic fake UUIDs so scaffold demos stay stable per OEM.
        val item = UUID.nameUUIDFromBytes("item:$oem".toByteArray()).toString()
        val uom = UUID.nameUUIDFromBytes("uom:$oem".toByteArray()).toString()
        return StockItemRef(stockItemId = item, uomId = uom, oemPartNumber = oem)
    }

    override suspend fun searchCatalog(
        mode: CatalogSearchMode,
        query: String,
    ): CatalogSearchResult {
        val q = query.trim()
        if (q.isEmpty()) {
            return CatalogSearchResult(mode = mode, query = q, parts = emptyList())
        }
        // Deterministic demo hits for Fake mode (part / browse).
        val parts = listOf(
            CatalogPartHit(
                oemPartNumber = "FAKE-$q".uppercase().take(32),
                pncCode = "PNC-001",
                categoryName = "Demo",
                subcategoryName = mode.rpcValue,
            ),
            CatalogPartHit(
                oemPartNumber = "P2-POS-SMOKE-001",
                pncCode = "PNC-002",
                categoryName = "Brakes",
                subcategoryName = "Pads",
            ),
        )
        return CatalogSearchResult(mode = mode, query = q, parts = parts)
    }

    override suspend fun listPosCartLines(cartId: String): List<PosCartLineSummary> {
        require(cartId.isNotBlank())
        return cartLines[cartId]?.toList().orEmpty()
    }

    override suspend fun getPosCartCustomerId(cartId: String): String? {
        require(cartId.isNotBlank())
        return cartCustomers[cartId]
    }

    override suspend fun listWarehouses(): List<WarehouseRef> = listOf(
        WarehouseRef(
            id = FAKE_WAREHOUSE_ID,
            code = "MAIN",
            name = "Main warehouse (Fake)",
        ),
    )

    override suspend fun createPosScanSession(cartId: String): PosScanSessionCreated {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.CREATE_POS_SCAN_SESSION}" }
        openCarts.add(cartId)
        val sessionId = UUID.randomUUID().toString()
        val code = "%06d".format((0..999_999).random())
        openScanSessions[code] = sessionId to cartId
        return PosScanSessionCreated(
            sessionId = sessionId,
            pairingCode = code,
            expiresAt = "2099-01-01T00:00:00Z",
        )
    }

    override suspend fun claimPosScanSession(pairingCode: String): String {
        val code = pairingCode.trim()
        require(code.matches(Regex("^\\d{6}$"))) { "invalid pairing code" }
        val pair = openScanSessions.remove(code)
            ?: throw IllegalStateException("pairing code not found or not open")
        claimedSessions.add(pair.first)
        claimedSessionCarts[pair.first] = pair.second
        return pair.first
    }

    override suspend fun revokePosScanSession(sessionId: String): String {
        require(sessionId.isNotBlank())
        claimedSessions.remove(sessionId)
        claimedSessionCarts.remove(sessionId)
        openScanSessions.entries.removeAll { it.value.first == sessionId }
        return sessionId
    }

    override suspend fun getPosScanSessionCartId(sessionId: String): String? {
        require(sessionId.isNotBlank())
        openScanSessions.values.firstOrNull { it.first == sessionId }?.let { return it.second }
        // After claim the open map entry is gone — track claimed cart via reverse lookup from create
        return claimedSessionCarts[sessionId]
    }

    override suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String?,
        lines: List<ReceiptLineInput>,
    ): String {
        require(toWarehouseId.isNotBlank())
        require(lines.isNotEmpty()) { "${RpcNames.POST_STOCK_RECEIPT} requires lines" }
        lines.forEach { line ->
            require(line.stockItemId.isNotBlank() && line.uomId.isNotBlank())
            require(line.qty > 0)
        }
        return UUID.randomUUID().toString()
    }

    override suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String?,
        lines: List<TransferLineInput>,
    ): String {
        require(fromWarehouseId.isNotBlank() && toWarehouseId.isNotBlank())
        require(fromWarehouseId != toWarehouseId) { "from and to warehouses must differ" }
        require(lines.isNotEmpty()) { "${RpcNames.CREATE_STOCK_TRANSFER} requires lines" }
        val id = UUID.randomUUID().toString()
        pendingTransfers.add(id)
        return id
    }

    override suspend fun approveStockTransfer(entryId: String): String {
        require(entryId.isNotBlank())
        pendingTransfers.remove(entryId)
        return entryId
    }

    override suspend fun rejectStockTransfer(entryId: String): String {
        require(entryId.isNotBlank())
        pendingTransfers.remove(entryId)
        return entryId
    }

    override suspend fun createStockReconciliationDraft(
        warehouseId: String,
        scope: ReconciliationScope,
        currency: CurrencyCode,
        itemIds: List<String>?,
        notes: String?,
        exchangeRate: Double?,
    ): String {
        require(warehouseId.isNotBlank())
        if (scope == ReconciliationScope.PARTIAL) {
            require(!itemIds.isNullOrEmpty()) { "partial reconciliation requires item_ids" }
        }
        if (currency == CurrencyCode.ZIG) {
            require(exchangeRate != null && exchangeRate > 0) {
                "ZIG requires positive exchange_rate"
            }
        }
        val id = UUID.randomUUID().toString()
        reconDrafts.add(id)
        return id
    }

    override suspend fun upsertStockReconciliationLines(
        reconciliationId: String,
        lines: List<ReconciliationLineInput>,
    ): Int {
        require(reconciliationId.isNotBlank())
        require(lines.isNotEmpty()) { "${RpcNames.UPSERT_STOCK_RECONCILIATION_LINES} requires lines" }
        return lines.size
    }

    override suspend fun submitStockReconciliation(reconciliationId: String): String {
        require(reconciliationId.isNotBlank())
        reconDrafts.remove(reconciliationId)
        return reconciliationId
    }

    override suspend fun approveStockReconciliation(reconciliationId: String): String {
        require(reconciliationId.isNotBlank())
        return reconciliationId
    }

    override suspend fun cancelStockReconciliation(
        reconciliationId: String,
        notes: String?,
    ): String {
        require(reconciliationId.isNotBlank())
        reconDrafts.remove(reconciliationId)
        return reconciliationId
    }

    override suspend fun listDeliveryNotes(): List<DeliveryNoteSummary> =
        deliveryNotes.toList()

    override suspend fun listPickLists(): List<PickListSummary> =
        pickLists.toList()

    override suspend fun createPickList(salesInvoiceId: String, linesJson: String?): String {
        require(salesInvoiceId.isNotBlank())
        val id = UUID.randomUUID().toString()
        val n = plSeq.getAndIncrement()
        pickLists.add(
            0,
            PickListSummary(
                id = id,
                documentNumber = "PL-FAKE-%03d".format(n),
                salesInvoiceId = salesInvoiceId,
                status = "draft",
            ),
        )
        return id
    }

    override suspend fun confirmPickLines(
        pickListId: String,
        lines: List<ConfirmPickLineInput>,
    ): String {
        require(lines.isNotEmpty())
        val idx = pickLists.indexOfFirst { it.id == pickListId }
        if (idx >= 0) {
            val pl = pickLists[idx]
            pickLists[idx] = pl.copy(status = "done")
        }
        return pickListId
    }

    override suspend fun createDeliveryNote(
        salesInvoiceId: String,
        lines: List<DnLineInput>,
        pickListId: String?,
    ): String {
        require(salesInvoiceId.isNotBlank())
        require(lines.isNotEmpty()) { "${RpcNames.CREATE_DELIVERY_NOTE} requires lines" }
        val id = UUID.randomUUID().toString()
        val n = dnSeq.getAndIncrement()
        deliveryNotes.add(
            0,
            DeliveryNoteSummary(
                id = id,
                documentNumber = "DN-FAKE-%03d".format(n),
                salesInvoiceId = salesInvoiceId,
                status = "draft",
            ),
        )
        return id
    }

    override suspend fun submitDeliveryNote(deliveryNoteId: String): String {
        val idx = deliveryNotes.indexOfFirst { it.id == deliveryNoteId }
        require(idx >= 0) { "delivery note not found" }
        deliveryNotes[idx] = deliveryNotes[idx].copy(status = "submitted")
        return deliveryNoteId
    }

    override suspend fun cancelDeliveryNote(deliveryNoteId: String): String {
        val idx = deliveryNotes.indexOfFirst { it.id == deliveryNoteId }
        require(idx >= 0) { "delivery note not found" }
        deliveryNotes[idx] = deliveryNotes[idx].copy(status = "cancelled")
        return deliveryNoteId
    }

    override suspend fun createDeliveryJob(
        deliveryNoteId: String,
        assigneeUserId: String?,
        etaAt: String?,
        notes: String?,
    ): String {
        require(deliveryNoteId.isNotBlank())
        val dn = deliveryNotes.find { it.id == deliveryNoteId }
            ?: DeliveryNoteSummary(
                id = deliveryNoteId,
                documentNumber = "DN-EXT",
                salesInvoiceId = "",
                status = "submitted",
            )
        require(dn.status == "submitted" || dn.status == "draft") {
            // Fake allows draft for scaffold demos; live requires submitted.
            "delivery job requires DN"
        }
        val id = UUID.randomUUID().toString()
        jobSeq.getAndIncrement()
        deliveryJobs[id] = deliveryNoteId to "pending"
        // Demo default coords (Harare) so suggest + ETA demos work without extra steps
        jobCoords[id] = JobCoords(
            pickupLat = -17.8250,
            pickupLng = 31.0330,
            dropoffLat = -17.8400,
            dropoffLng = 31.0500,
        )
        return id
    }

    override suspend fun setDeliveryJobCoords(
        deliveryJobId: String,
        pickupLat: Double?,
        pickupLng: Double?,
        dropoffLat: Double?,
        dropoffLng: Double?,
    ): String {
        require(deliveryJobId.isNotBlank())
        require((pickupLat == null) == (pickupLng == null)) {
            "pickup_lat and pickup_lng must both be set or both null"
        }
        require((dropoffLat == null) == (dropoffLng == null)) {
            "dropoff_lat and dropoff_lng must both be set or both null"
        }
        fun checkLat(v: Double?) {
            if (v != null) require(v in -90.0..90.0) { "lat out of range" }
        }
        fun checkLng(v: Double?) {
            if (v != null) require(v in -180.0..180.0) { "lng out of range" }
        }
        checkLat(pickupLat)
        checkLng(pickupLng)
        checkLat(dropoffLat)
        checkLng(dropoffLng)
        val job = deliveryJobs[deliveryJobId]
        require(job == null || job.second !in listOf("completed", "failed")) {
            "cannot set geo on terminal delivery job"
        }
        if (job == null) {
            deliveryJobs[deliveryJobId] = "unknown" to "pending"
        }
        // Match Live: null pair clears that endpoint (no merge).
        jobCoords[deliveryJobId] = JobCoords(
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            dropoffLat = dropoffLat,
            dropoffLng = dropoffLng,
        )
        return deliveryJobId
    }

    override suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): UpdateDeliveryJobStatusResult {
        val current = deliveryJobs[deliveryJobId]
            ?: ("unknown" to "pending").also { deliveryJobs[deliveryJobId] = it }
        require(current.second !in listOf("completed", "failed")) {
            "terminal delivery job cannot change status"
        }
        deliveryJobs[deliveryJobId] = current.first to status.rpcValue
        val token = if (status == DeliveryJobStatus.DISPATCHED) {
            "fake_track_" + deliveryJobId.replace("-", "").take(32).padEnd(32, '0')
        } else {
            null
        }
        return UpdateDeliveryJobStatusResult(
            deliveryJobId = deliveryJobId,
            trackToken = token,
        )
    }

    override suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String?,
        accuracyM: Double?,
    ): String {
        require(deliveryJobId.isNotBlank())
        require(lat in -90.0..90.0) { "lat out of range" }
        require(lng in -180.0..180.0) { "lng out of range" }
        val job = deliveryJobs[deliveryJobId]
        if (job != null) {
            require(job.second !in listOf("completed", "failed")) {
                "cannot ingest locations for terminal job"
            }
        }
        ingestedLocationCount.incrementAndGet()
        return UUID.randomUUID().toString()
    }

    override suspend fun suggestDeliveryAssignees(
        deliveryJobId: String,
        limit: Int,
    ): List<DeliveryAssigneeSuggestion> {
        require(deliveryJobId.isNotBlank())
        val lim = limit.coerceIn(1, 50)
        val coords = jobCoords[deliveryJobId]
        val originLat = coords?.pickupLat ?: coords?.dropoffLat
        val originLng = coords?.pickupLng ?: coords?.dropoffLng
        val seeded = listOf(
            Triple(FAKE_DRIVER_USER_ID, "available", Pair(-17.83, 31.05)),
            Triple(FAKE_DRIVER_USER_ID_2, "on_duty", Pair(-17.84, 31.06)),
        )
        return seeded.mapIndexed { idx, (uid, status, last) ->
            val dist = if (originLat != null && originLng != null) {
                haversineM(originLat, originLng, last.first, last.second)
            } else {
                if (idx == 0) 420.0 else 1_200.0
            }
            DeliveryAssigneeSuggestion(
                userId = uid,
                status = status,
                distanceM = dist,
                capacity = if (idx == 0) 5 else 3,
                openJobs = if (idx == 0) 1 else 2,
                lastLat = last.first,
                lastLng = last.second,
                lastSeenAt = "2026-07-25T09:05:00Z",
            )
        }.sortedBy { it.distanceM ?: Double.MAX_VALUE }.take(lim)
    }

    override suspend fun assignDeliveryJob(
        deliveryJobId: String,
        assigneeUserId: String,
        override: Boolean,
    ): String {
        require(deliveryJobId.isNotBlank())
        require(assigneeUserId.isNotBlank())
        val job = deliveryJobs[deliveryJobId]
        if (job != null) {
            require(job.second !in listOf("completed", "failed")) {
                "cannot assign terminal delivery job"
            }
        } else {
            deliveryJobs[deliveryJobId] = "unknown" to "pending"
        }
        jobAssignees[deliveryJobId] = assigneeUserId
        return deliveryJobId
    }

    override suspend fun optimizeDriverStops(driverUserId: String): List<OptimizedDriverStop> {
        require(driverUserId.isNotBlank())
        val open = jobAssignees.filter { it.value == driverUserId }
            .keys
            .filter { jobId ->
                val status = deliveryJobs[jobId]?.second
                status == null || status in listOf("pending", "dispatched")
            }
            .sorted()
        if (open.isEmpty()) {
            // Seed demo stops when no assigned jobs yet
            return listOf(
                OptimizedDriverStop(
                    deliveryJobId = "00000000-0000-4000-8000-0000000000j1",
                    routeSequence = 1,
                    distanceM = 800.0,
                ),
                OptimizedDriverStop(
                    deliveryJobId = "00000000-0000-4000-8000-0000000000j2",
                    routeSequence = 2,
                    distanceM = 1_500.0,
                ),
            )
        }
        return open.mapIndexed { idx, id ->
            OptimizedDriverStop(
                deliveryJobId = id,
                routeSequence = idx + 1,
                distanceM = (idx + 1) * 500.0,
            )
        }
    }

    override suspend fun getDeliveryTrackPoint(deliveryJobId: String): DeliveryTrackPoint? {
        require(deliveryJobId.isNotBlank())
        val status = deliveryJobs[deliveryJobId]?.second ?: "dispatched"
        if (status != "dispatched") return null
        val coords = jobCoords[deliveryJobId]
        return DeliveryTrackPoint(
            deliveryJobId = deliveryJobId,
            lat = coords?.dropoffLat?.let { it + 0.01 } ?: -17.8292,
            lng = coords?.dropoffLng?.let { it - 0.01 } ?: 31.0522,
            recordedAt = "2026-07-25T09:10:00Z",
            etaAt = "2026-07-25T09:45:00Z",
            etaSeconds = 2_100,
            status = status,
        )
    }

    override suspend fun mintDeliveryTrackToken(
        deliveryJobId: String,
        ttl: String?,
    ): String {
        require(deliveryJobId.isNotBlank())
        val status = deliveryJobs[deliveryJobId]?.second
        require(status == null || status !in listOf("completed", "failed")) {
            "cannot mint track token for terminal job"
        }
        // Remint / rotate — different suffix so tests can distinguish from dispatch token.
        return "fake_remint_" + deliveryJobId.replace("-", "").take(32).padEnd(32, '0')
    }

    override suspend fun generateDeliveryPodOtp(
        deliveryJobId: String,
        ttl: String?,
    ): String {
        require(deliveryJobId.isNotBlank())
        val status = deliveryJobs[deliveryJobId]?.second
        require(status == "dispatched") {
            "POD OTP requires dispatched job (status=${status ?: "missing"})"
        }
        return "042891"
    }

    override suspend fun listOpenPanicEvents(): List<PanicEventSummary> =
        panicEvents.filter { it.acknowledgedAt == null }
            .sortedByDescending { it.createdAt }

    override suspend fun acknowledgePanicEvent(panicEventId: String): String {
        require(panicEventId.isNotBlank())
        val idx = panicEvents.indexOfFirst { it.id == panicEventId }
        require(idx >= 0) { "panic event not found" }
        val now = java.time.Instant.now().toString()
        panicEvents[idx] = panicEvents[idx].copy(
            acknowledgedAt = now,
            acknowledgedBy = fakeStaffUserId,
        )
        return panicEventId
    }

    override fun currentUserId(): String? = fakeStaffUserId

    override suspend fun listMyStaffRoles(): List<String> =
        listOf("admin", "sales", "warehouse", "finance")

    override suspend fun searchCustomers(query: String): List<CustomerOption> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val uuidLike = UUID_REGEX.matches(q)
        return if (uuidLike) {
            customers.filter { it.id.equals(q, ignoreCase = true) }
        } else {
            customers.filter { it.displayName.contains(q, ignoreCase = true) }
        }
    }

    override suspend fun listSuppliers(): List<SupplierRef> = suppliers.toList()

    override suspend fun listBlanketPurchaseOrders(): List<BlanketSummary> =
        blankets.sortedByDescending { it.documentNumber }

    override suspend fun createBlanketPurchaseOrder(
        supplierId: String,
        warehouseId: String,
        currency: CurrencyCode,
        exchangeRate: Double,
        blanketMaxValue: Double,
        lines: List<BlanketLineInput>,
        notes: String?,
        expectedDate: String?,
    ): String {
        require(supplierId.isNotBlank())
        require(warehouseId.isNotBlank())
        require(blanketMaxValue >= 0)
        require(lines.isNotEmpty()) { "blanket lines required" }
        val id = UUID.randomUUID().toString()
        val supplierName = suppliers.find { it.id == supplierId }?.name
        blankets.add(
            0,
            BlanketSummary(
                id = id,
                documentNumber = "BPO-FAKE-${blankets.size + 1}",
                status = "draft",
                supplierId = supplierId,
                supplierName = supplierName,
                warehouseId = warehouseId,
                warehouseCode = "MAIN",
                currency = currency,
                blanketMaxValue = blanketMaxValue,
                blanketValueReleased = 0.0,
                expectedDate = expectedDate,
                lines = lines.mapIndexed { idx, line ->
                    BlanketLineSummary(
                        id = UUID.randomUUID().toString(),
                        lineNo = idx + 1,
                        stockItemId = line.stockItemId,
                        oemPartNumber = null,
                        qtyOrdered = line.qty,
                        qtyReleased = 0.0,
                        unitPrice = line.unitPrice,
                        currency = line.currency ?: currency,
                    )
                },
            ),
        )
        return id
    }

    override suspend fun submitPurchaseOrder(purchaseOrderId: String): String {
        require(purchaseOrderId.isNotBlank())
        val idx = blankets.indexOfFirst { it.id == purchaseOrderId }
        require(idx >= 0) { "blanket PO not found" }
        blankets[idx] = blankets[idx].copy(status = "submitted")
        return purchaseOrderId
    }

    override suspend fun createBlanketRelease(
        blanketPurchaseOrderId: String,
        lines: List<BlanketReleaseLineInput>,
        notes: String?,
    ): String {
        require(blanketPurchaseOrderId.isNotBlank())
        require(lines.isNotEmpty())
        val idx = blankets.indexOfFirst { it.id == blanketPurchaseOrderId }
        require(idx >= 0) { "blanket PO not found" }
        val blanket = blankets[idx]
        require(blanket.status == "submitted") { "blanket PO must be submitted before release" }
        var releaseValue = 0.0
        val updatedLines = blanket.lines.map { line ->
            val release = lines.find { it.blanketLineId == line.id } ?: return@map line
            require(release.qty > 0)
            require(line.qtyReleased + release.qty <= line.qtyOrdered) {
                "release exceeds remaining qty"
            }
            releaseValue += release.qty * line.unitPrice
            line.copy(qtyReleased = line.qtyReleased + release.qty)
        }
        require(blanket.blanketValueReleased + releaseValue <= blanket.blanketMaxValue) {
            "release exceeds remaining blanket value"
        }
        blankets[idx] = blanket.copy(
            lines = updatedLines,
            blanketValueReleased = blanket.blanketValueReleased + releaseValue,
        )
        return UUID.randomUUID().toString()
    }

    override suspend fun listWarehouseBins(warehouseId: String): List<WarehouseBinSummary> {
        require(warehouseId.isNotBlank())
        return warehouseBins
            .filter { it.warehouseId == warehouseId }
            .sortedWith(compareBy({ it.pickPathSeq }, { it.code }))
    }

    override suspend fun createWarehouseBin(
        warehouseId: String,
        code: String,
        name: String,
        pickPathSeq: Int,
        aisle: String?,
        rack: String?,
        shelf: String?,
    ): String {
        require(warehouseId.isNotBlank())
        require(code.isNotBlank())
        require(name.isNotBlank())
        val id = UUID.randomUUID().toString()
        warehouseBins.add(
            WarehouseBinSummary(
                id = id,
                warehouseId = warehouseId,
                code = code.trim().uppercase(),
                name = name.trim(),
                pickPathSeq = pickPathSeq,
                aisle = aisle?.trim()?.ifBlank { null },
                rack = rack?.trim()?.ifBlank { null },
                shelf = shelf?.trim()?.ifBlank { null },
                isActive = true,
            ),
        )
        return id
    }

    override suspend fun updateWarehouseBin(
        binId: String,
        name: String?,
        pickPathSeq: Int?,
        aisle: String?,
        rack: String?,
        shelf: String?,
        isActive: Boolean?,
    ): String {
        val idx = warehouseBins.indexOfFirst { it.id == binId }
        require(idx >= 0) { "bin not found" }
        val cur = warehouseBins[idx]
        warehouseBins[idx] = cur.copy(
            name = name?.trim()?.ifBlank { null } ?: cur.name,
            pickPathSeq = pickPathSeq ?: cur.pickPathSeq,
            aisle = if (aisle == null) cur.aisle else aisle.trim().ifBlank { null },
            rack = if (rack == null) cur.rack else rack.trim().ifBlank { null },
            shelf = if (shelf == null) cur.shelf else shelf.trim().ifBlank { null },
            isActive = isActive ?: cur.isActive,
        )
        return binId
    }

    override suspend fun deactivateWarehouseBin(binId: String): String =
        updateWarehouseBin(binId, isActive = false)

    override suspend fun setStockLevelBin(
        stockItemId: String,
        warehouseId: String,
        binId: String?,
    ): String {
        require(stockItemId.isNotBlank())
        require(warehouseId.isNotBlank())
        if (binId != null) {
            require(warehouseBins.any { it.id == binId && it.warehouseId == warehouseId }) {
                "bin not in warehouse"
            }
            stockLevelBins[stockItemId] = binId
        } else {
            stockLevelBins.remove(stockItemId)
        }
        return UUID.randomUUID().toString()
    }

    override suspend fun getPickPathHints(
        warehouseId: String,
        stockItemIds: List<String>?,
    ): List<PickPathHint> {
        require(warehouseId.isNotBlank())
        val filter = stockItemIds?.filter { it.isNotBlank() }.orEmpty()
        val items = if (filter.isEmpty()) {
            listOf(FAKE_STOCK_ITEM_ID to "21410-JF00A")
        } else {
            filter.map { it to if (it == FAKE_STOCK_ITEM_ID) "21410-JF00A" else null }
        }
        return items.mapNotNull { (itemId, oem) ->
            val binId = stockLevelBins[itemId]
            val bin = warehouseBins.find { it.id == binId && it.isActive }
            PickPathHint(
                stockItemId = itemId,
                oemPartNumber = oem,
                quantity = 12.0,
                binId = bin?.id,
                binCode = bin?.code,
                binName = bin?.name,
                pickPathSeq = bin?.pickPathSeq,
                aisle = bin?.aisle,
                rack = bin?.rack,
                shelf = bin?.shelf,
            )
        }.sortedWith(
            compareBy(nullsLast()) { it.pickPathSeq },
        )
    }

    override suspend fun listConsignmentEntries(): List<ConsignmentEntrySummary> =
        consignmentEntries.sortedByDescending { it.documentNumber }

    override suspend fun createConsignmentEntryDraft(
        kind: ConsignmentKind,
        purpose: ConsignmentPurpose,
        warehouseId: String,
        supplierId: String?,
        customerId: String?,
        currency: CurrencyCode,
        exchangeRate: Double,
        notes: String?,
    ): String {
        require(warehouseId.isNotBlank())
        when (kind) {
            ConsignmentKind.SUPPLIER_OWNED -> {
                require(!supplierId.isNullOrBlank()) { "supplier_owned requires supplier_id" }
                require(customerId.isNullOrBlank())
            }
            ConsignmentKind.CUSTOMER_HELD -> {
                require(!customerId.isNullOrBlank()) { "customer_held requires customer_id" }
                require(supplierId.isNullOrBlank())
            }
        }
        val id = UUID.randomUUID().toString()
        consignmentEntries.add(
            0,
            ConsignmentEntrySummary(
                id = id,
                documentNumber = "CNS-FAKE-${consignmentEntries.size + 1}",
                status = "draft",
                kind = kind.rpcValue,
                purpose = purpose.rpcValue,
                warehouseId = warehouseId,
                supplierId = supplierId,
                customerId = customerId,
                currency = currency,
            ),
        )
        consignmentLineCounts[id] = 0
        return id
    }

    override suspend fun addConsignmentEntryLine(
        entryId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
        unitCost: Double,
        unitPrice: Double,
        currency: CurrencyCode?,
    ): String {
        require(entryId.isNotBlank())
        require(stockItemId.isNotBlank() && uomId.isNotBlank())
        require(qty > 0)
        val entry = consignmentEntries.find { it.id == entryId }
            ?: error("consignment entry not found")
        require(entry.status == "draft") { "lines editable only while draft" }
        consignmentLineCounts[entryId] = (consignmentLineCounts[entryId] ?: 0) + 1
        return UUID.randomUUID().toString()
    }

    override suspend fun submitConsignmentEntry(entryId: String): String {
        val idx = consignmentEntries.indexOfFirst { it.id == entryId }
        require(idx >= 0) { "consignment entry not found" }
        require(consignmentEntries[idx].status == "draft")
        require((consignmentLineCounts[entryId] ?: 0) > 0) { "consignment entry has no lines" }
        consignmentEntries[idx] = consignmentEntries[idx].copy(status = "submitted")
        return entryId
    }

    override suspend fun cancelConsignmentEntry(entryId: String): String {
        val idx = consignmentEntries.indexOfFirst { it.id == entryId }
        require(idx >= 0) { "consignment entry not found" }
        require(consignmentEntries[idx].status == "draft")
        consignmentEntries[idx] = consignmentEntries[idx].copy(status = "cancelled")
        return entryId
    }

    override suspend fun loadCustomerCredit(customerId: String): CustomerCreditSnapshot? {
        require(customerId.isNotBlank())
        return customerCredit[customerId]
            ?: customers.find { it.id == customerId }?.let {
                CustomerCreditSnapshot(
                    customerId = it.id,
                    creditLimit = 0.0,
                    creditHold = false,
                    openBalance = 0.0,
                    currency = CurrencyCode.USD,
                )
            }
    }

    override suspend fun setCustomerCredit(
        customerId: String,
        creditLimit: Double?,
        creditHold: Boolean?,
    ): CustomerCreditSnapshot {
        require(customerId.isNotBlank())
        require(creditLimit != null || creditHold != null) {
            "provide credit_limit and/or credit_hold"
        }
        if (creditLimit != null) require(creditLimit >= 0)
        val cur = loadCustomerCredit(customerId)
            ?: error("customer not found")
        val updated = cur.copy(
            creditLimit = creditLimit ?: cur.creditLimit,
            creditHold = creditHold ?: cur.creditHold,
        )
        customerCredit[customerId] = updated
        return updated
    }

    override suspend fun listStaffChatThreads(filter: StaffChatFilter): List<ChatThreadSummary> {
        val uid = fakeStaffUserId
        return chatThreads.filter { t ->
            when (filter) {
                StaffChatFilter.OPEN -> t.status == "open"
                StaffChatFilter.MINE -> t.assignedTo == uid && t.status != "closed"
                StaffChatFilter.CLOSED -> t.status == "closed"
            }
        }.sortedByDescending { it.lastMessageAt ?: it.createdAt }
    }

    override suspend fun listChatMessages(threadId: String): List<ChatMessageSummary> {
        require(threadId.isNotBlank())
        return chatMessages[threadId]?.toList().orEmpty()
    }

    override suspend fun claimChatThread(threadId: String) {
        val idx = chatThreads.indexOfFirst { it.id == threadId }
        require(idx >= 0) { "thread not found" }
        val t = chatThreads[idx]
        require(t.status != "closed") { "cannot claim closed thread" }
        chatThreads[idx] = t.copy(
            status = "assigned",
            assignedTo = fakeStaffUserId,
        )
    }

    override suspend fun closeChatThread(threadId: String) {
        val idx = chatThreads.indexOfFirst { it.id == threadId }
        require(idx >= 0) { "thread not found" }
        chatThreads[idx] = chatThreads[idx].copy(status = "closed")
    }

    override suspend fun markChatThreadRead(threadId: String) {
        require(threadId.isNotBlank())
        chatUnread[threadId] = 0
    }

    override suspend fun postChatMessage(threadId: String, body: String): String {
        require(threadId.isNotBlank())
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "message body required" }
        val idx = chatThreads.indexOfFirst { it.id == threadId }
        require(idx >= 0) { "thread not found" }
        require(chatThreads[idx].status != "closed") { "thread closed" }
        val id = UUID.randomUUID().toString()
        val now = java.time.Instant.now().toString()
        val list = chatMessages.getOrPut(threadId) { mutableListOf() }
        list.add(
            ChatMessageSummary(
                id = id,
                threadId = threadId,
                senderUserId = fakeStaffUserId,
                senderKind = "staff",
                body = trimmed,
                createdAt = now,
            ),
        )
        chatThreads[idx] = chatThreads[idx].copy(lastMessageAt = now)
        return id
    }

    override suspend fun chatUnreadCount(threadId: String?): Int {
        return if (threadId.isNullOrBlank()) {
            chatUnread.values.sum()
        } else {
            chatUnread[threadId] ?: 0
        }
    }

    companion object {
        const val FAKE_STAFF_USER_ID = "00000000-0000-4000-8000-0000000000a1"
        const val FAKE_CUSTOMER_USER_ID = "00000000-0000-4000-8000-0000000000c1"
        const val FAKE_CUSTOMER_ID = "00000000-0000-4000-8000-0000000000c2"
        const val FAKE_DRIVER_USER_ID = "00000000-0000-4000-8000-0000000000d0"
        const val FAKE_DRIVER_USER_ID_2 = "00000000-0000-4000-8000-0000000000d2"
        const val FAKE_WAREHOUSE_ID = "00000000-0000-4000-8000-0000000000w1"
        const val FAKE_SUPPLIER_ID = "00000000-0000-4000-8000-0000000000s1"
        const val FAKE_STOCK_ITEM_ID = "00000000-0000-4000-8000-0000000000i1"
        const val FAKE_BLANKET_ID = "00000000-0000-4000-8000-0000000000b1"
        const val FAKE_BLANKET_LINE_ID = "00000000-0000-4000-8000-0000000000bl"
        const val FAKE_BIN_A_ID = "00000000-0000-4000-8000-0000000000ba"
        const val FAKE_BIN_B_ID = "00000000-0000-4000-8000-0000000000bb"
        const val FAKE_CONSIGNMENT_ID = "00000000-0000-4000-8000-0000000000n1"
        const val OPEN_PANIC_ID = "00000000-0000-4000-8000-0000000000p0"
        private const val OPEN_THREAD_ID = "00000000-0000-4000-8000-0000000000t1"
        private const val MINE_THREAD_ID = "00000000-0000-4000-8000-0000000000t2"
        private const val CLOSED_THREAD_ID = "00000000-0000-4000-8000-0000000000t3"

        private val UUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

        private val INVENTORY_QR_REGEX =
            Regex("""^gtr://part/([^?]+)\?batch=([^&]+)&valuation=(FIFO|AVG)$""")

        private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_000.0
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(p1) * kotlin.math.cos(p2) *
                kotlin.math.sin(dLng / 2).let { it * it }
            return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
        }
    }

    private data class JobCoords(
        val pickupLat: Double?,
        val pickupLng: Double?,
        val dropoffLat: Double?,
        val dropoffLng: Double?,
    )
}
