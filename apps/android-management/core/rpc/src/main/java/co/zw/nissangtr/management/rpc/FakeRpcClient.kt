package co.zw.nissangtr.management.rpc

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stub so HR / POS / warehouse / dispatch / chat screens compile and exercise
 * flows without a configured Supabase project. Live: [SupabaseRpcClient] via [RpcClientFactory].
 *
 * Documented live RPC → param map:
 * - [RpcNames.CLOCK_ATTENDANCE]: p_employee_id, p_event_type, p_occurred_at?, p_notes?
 * - [RpcNames.SAVE_HR_ONBOARDING_STAGE] / [RpcNames.COMPLETE_HR_ONBOARDING]
 * - createHrOnboardingAuthUser → Edge [RpcNames.HR_ONBOARDING_CREATE_AUTH_FN]
 * - listHrOnboardingDrafts / listHrGrades / listHrRoles: PostgREST
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
    /** cartId → immutable sale vehicle context. */
    private val cartVehicles = mutableMapOf<String, PosSaleVehicleSelection>()
    /** pairing_code → (sessionId, cartId) */
    private val openScanSessions = mutableMapOf<String, Pair<String, String>>()
    private val claimedSessions = mutableSetOf<String>()
    /** sessionId → cartId (after claim) */
    private val claimedSessionCarts = mutableMapOf<String, String>()
    /** client_sale_id → invoice_id (offline replay idempotency). */
    private val offlineSaleReceipts = mutableMapOf<String, String>()
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
    private val fleetVehicles = mutableListOf(
        FleetVehicleSummary(
            id = FAKE_FLEET_VEHICLE_ID,
            plate = "AB-1234",
            label = "Van Alpha",
            status = FleetVehicleStatus.ACTIVE,
            assignedDriverUserId = FAKE_DRIVER_USER_ID,
            notes = "Fake seed vehicle",
        ),
    )
    private val knownDriverIds = setOf(FAKE_DRIVER_USER_ID, FAKE_DRIVER_USER_ID_2)
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

    private val popularPins = mutableListOf<PosPopularPin>()

    private val customers = mutableListOf(
        CustomerOption(
            id = FAKE_CUSTOMER_ID,
            displayName = "Tendai Moyo",
            kind = PosCustomerKind.BUSINESS,
            businessName = "Acme Motors",
            email = "accounts@acme.test",
            phoneE164 = "+263771000001",
            whatsappE164 = "+263771000001",
        ),
        CustomerOption(
            id = FAKE_CUSTOMER_USER_ID,
            displayName = "Walk-in Sample",
            email = "sample@example.test",
        ),
    )
    private val customerGarage = mutableListOf(
        CustomerGarageVehicle(
            id = "00000000-0000-0000-0000-000000000191",
            customerId = FAKE_CUSTOMER_ID,
            make = "Nissan", modelSlug = "gt-r", model = "GT-R", generation = "R35",
            chassisCode = "R35", engine = "VR38DETT", isPrimary = true,
        ),
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
        val invoiceId = UUID.randomUUID().toString()
        // A posted sale makes its holds ready with the invoice (server trigger `sync_pos_fulfillment_invoice`).
        fakeFulfillment.replaceAll {
            if (it.cartId == cartId && it.status == "reserved" && it.kind in setOf("customer_collection", "alternate_pickup")) {
                it.copy(status = "ready", invoiceId = invoiceId, readyAt = java.time.Instant.now().toString(), expiresAt = null)
            } else it
        }
        return CheckoutPosResult(
            invoiceId = invoiceId,
            customerId = customerId,
            receiptEmail = email,
            receiptWhatsappE164 = wa,
            hadCustomerBeforeCheckout = hadCustomer,
        )
    }

    override suspend fun checkoutPosCartWithTenders(
        cartId: String,
        tenders: List<PosTenderLine>,
        receiptEmail: String?,
        receiptWhatsappE164: String?,
        receiptPhoneE164: String?,
    ): CheckoutPosResult {
        require(cartId.isNotBlank()) {
            "cartId required for ${RpcNames.CHECKOUT_POS_CART_WITH_TENDERS}"
        }
        require(tenders.isNotEmpty()) { "tenders required" }
        require(tenders.all { it.amount > 0 }) { "each tender amount must be > 0" }
        return checkoutPosCart(cartId, receiptEmail, receiptWhatsappE164, receiptPhoneE164)
    }

    override suspend fun createEcocashIntent(
        externalRef: String,
        payerMsisdn: String,
        amount: Double,
        currency: CurrencyCode,
        payerMode: String,
        customerId: String?,
        salesInvoiceId: String?,
    ): String {
        require(externalRef.isNotBlank())
        require(payerMsisdn.isNotBlank())
        require(amount > 0)
        return UUID.randomUUID().toString()
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
                saleableQty = 4.0,
            ),
            CatalogPartHit(
                oemPartNumber = "P2-POS-SMOKE-001",
                pncCode = "PNC-002",
                categoryName = "Brakes",
                subcategoryName = "Pads",
                saleableQty = 12.0,
            ),
        )
        return CatalogSearchResult(mode = mode, query = q, parts = parts)
    }

    override suspend fun searchCatalogForVehicle(
        vehicle: PosSaleVehicleSelection,
        query: String,
        limit: Int,
    ): CatalogSearchResult {
        val base = searchCatalog(CatalogSearchMode.PART, query)
        return base.copy(
            parts = base.parts.take(limit.coerceIn(1, 100)).map {
                it.copy(chassisCode = vehicle.chassisCode, engineCode = vehicle.engineCode)
            },
        )
    }

    override suspend fun setPosCartVehicle(
        cartId: String,
        vehicle: PosSaleVehicleSelection?,
    ): String {
        require(cartId in openCarts) { "open cart required" }
        if (vehicle == null) cartVehicles.remove(cartId) else cartVehicles[cartId] = vehicle
        return cartId
    }

    override suspend fun getPosCartVehicle(cartId: String): PosSaleVehicleSelection? =
        cartVehicles[cartId]

    override suspend fun setPosCartLineQty(lineId: String, qty: Double, unitPrice: Double) {
        require(lineId.isNotBlank()) { "lineId required" }
        require(qty > 0) { "qty must be > 0" }
        cartLines.values.forEach { lines ->
            val idx = lines.indexOfFirst { it.id == lineId }
            if (idx >= 0) {
                val prev = lines[idx]
                lines[idx] = prev.copy(
                    qty = qty,
                    unitPrice = unitPrice,
                    lineTotal = kotlin.math.round(unitPrice * qty * 100.0) / 100.0,
                )
                return
            }
        }
        error("cart line not found: $lineId")
    }

    override suspend fun deletePosCartLine(lineId: String) {
        require(lineId.isNotBlank()) { "lineId required" }
        cartLines.values.forEach { lines ->
            if (lines.removeAll { it.id == lineId }) return
        }
        error("cart line not found: $lineId")
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

    override suspend fun getPosScanSessionStatus(sessionId: String): String? = when {
        openScanSessions.values.any { it.first == sessionId } -> "open"
        sessionId in claimedSessions -> "claimed"
        else -> "revoked"
    }

    private val parkedCarts = mutableSetOf<String>()
    private val quotations = mutableListOf<PosQuotationSummary>()
    private var fakeIsPosApprover = true

    fun setFakePosApprover(value: Boolean) {
        fakeIsPosApprover = value
    }

    override suspend fun parkPosCart(cartId: String): String {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.PARK_POS_CART}" }
        require(openCarts.contains(cartId)) { "open cart required" }
        parkedCarts.add(cartId)
        return cartId
    }

    override suspend fun resumePosCart(cartId: String): String {
        require(cartId.isNotBlank()) { "cartId required for ${RpcNames.RESUME_POS_CART}" }
        require(parkedCarts.remove(cartId)) { "parked cart required" }
        openCarts.add(cartId)
        return cartId
    }

    override suspend fun isPosApprover(): Boolean = fakeIsPosApprover

    override suspend fun applyPosCartDiscount(
        cartId: String,
        discountPercent: Double,
        notes: String?,
    ): String {
        require(fakeIsPosApprover) { "admin or shop manager approval required" }
        require(cartId.isNotBlank())
        require(discountPercent in 0.0..100.0)
        val lines = cartLines[cartId] ?: error("cart not found")
        for (i in lines.indices) {
            val line = lines[i]
            if (line.isCoreCharge) continue
            val unit = kotlin.math.round(line.unitPrice * (1 - discountPercent / 100.0) * 10000.0) / 10000.0
            lines[i] = line.copy(
                unitPrice = unit,
                lineTotal = kotlin.math.round(unit * line.qty * 100.0) / 100.0,
            )
        }
        return cartId
    }

    override suspend fun voidPosCart(cartId: String, notes: String?): String {
        require(fakeIsPosApprover) { "admin or shop manager approval required" }
        require(cartId.isNotBlank())
        openCarts.remove(cartId)
        parkedCarts.remove(cartId)
        cartLines.remove(cartId)
        return cartId
    }

    override suspend fun postPosRefund(invoiceId: String, notes: String?): String {
        require(fakeIsPosApprover) { "admin or shop manager approval required" }
        require(invoiceId.isNotBlank()) { "invoiceId required for ${RpcNames.POST_POS_REFUND}" }
        // Fake: finance pipeline id — same SoR name as Live post_finance_refund.
        return UUID.randomUUID().toString()
    }

    override suspend fun createPosQuotationFromCart(
        cartId: String,
        validUntil: String?,
        notes: String?,
    ): String {
        require(cartId.isNotBlank())
        val lines = cartLines[cartId].orEmpty()
        require(lines.any { !it.isCoreCharge }) { "quotation requires at least one non-core line" }
        val id = UUID.randomUUID().toString()
        quotations.add(
            0,
            PosQuotationSummary(
                id = id,
                documentNumber = "QT-FAKE-${quotations.size + 1}",
                customerId = cartCustomers[cartId],
                warehouseId = FAKE_WAREHOUSE_ID,
                currency = CurrencyCode.USD,
                status = "issued",
                validUntil = validUntil,
                sentChannel = null,
                createdAt = "2026-08-03T00:00:00Z",
                lineCount = lines.size.toLong(),
                total = lines.filter { !it.isCoreCharge }.sumOf { it.lineTotal },
            ),
        )
        return id
    }

    override suspend fun sendPosQuotation(
        quotationId: String,
        channel: String,
        contact: String?,
    ): String {
        require(quotationId.isNotBlank())
        val ch = channel.trim().lowercase()
        require(ch in setOf("print", "email", "sms", "whatsapp")) { "invalid channel" }
        if (ch != "print") require(!contact.isNullOrBlank()) { "contact required" }
        val idx = quotations.indexOfFirst { it.id == quotationId }
        require(idx >= 0) { "quotation not found" }
        quotations[idx] = quotations[idx].copy(status = "sent", sentChannel = ch)
        return quotationId
    }

    override suspend fun convertPosQuotationToCart(quotationId: String): String {
        require(quotationId.isNotBlank())
        val idx = quotations.indexOfFirst { it.id == quotationId }
        require(idx >= 0) { "quotation not found" }
        val q = quotations[idx]
        require(q.status in setOf("issued", "sent")) { "cannot convert status=${q.status}" }
        quotations[idx] = q.copy(status = "converted")
        val cartId = UUID.randomUUID().toString()
        openCarts.add(cartId)
        cartLines[cartId] = mutableListOf(
            PosCartLineSummary(
                id = UUID.randomUUID().toString(),
                stockItemId = FAKE_STOCK_ITEM_ID,
                oemPartNumber = "OEM-QUOTE",
                qty = 1.0,
                unitPrice = q.total.coerceAtLeast(1.0),
                lineTotal = q.total.coerceAtLeast(1.0),
            ),
        )
        return cartId
    }

    override suspend fun listPosQuotations(
        status: String?,
        limit: Int,
    ): List<PosQuotationSummary> =
        quotations
            .filter { status.isNullOrBlank() || it.status.equals(status, ignoreCase = true) }
            .take(limit.coerceIn(1, 200))

    override suspend fun listPosPopularSpares(
        days: Int,
        limit: Int,
    ): List<PopularPosSpare> = listOf(
        PopularPosSpare(
            stockItemId = FAKE_STOCK_ITEM_ID,
            oemPartNumber = "P2-POS-SMOKE-001",
            description = "Oil filter",
            unitsSold = 42.0,
            saleableQty = 18.0,
            unitPrice = 25.0,
            currency = CurrencyCode.USD,
        ),
        PopularPosSpare(
            stockItemId = "00000000-0000-4000-8000-0000000000b2",
            oemPartNumber = "FAKE-PAD-001",
            description = "Front brake pad set",
            unitsSold = 31.0,
            saleableQty = 7.0,
            unitPrice = 85.0,
            currency = CurrencyCode.USD,
        ),
    ).take(limit.coerceIn(1, 24))

    private val hiddenBestsellers = linkedSetOf<String>()

    override suspend fun hydratePosParts(oemPartNumbers: List<String>): List<PosPartMeta> =
        oemPartNumbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { oem ->
            val ref = lookupStockItemByOem(oem)
            PosPartMeta(
                stockItemId = ref.stockItemId,
                uomId = ref.uomId,
                oemPartNumber = oem,
                description = null,
                unitPrice = 10.0 + (oem.hashCode().toLong().and(0xFFFF) % 9000) / 100.0,
                currency = CurrencyCode.USD,
                saleableQty = (oem.hashCode().toLong().and(0xFF) % 40).toDouble(),
                imageUrl = null,
            )
        }

    override suspend fun listPosHiddenBestsellers(): List<String> = hiddenBestsellers.toList()

    override suspend fun hidePosBestseller(stockItemId: String): Boolean = hiddenBestsellers.add(stockItemId) || true

    override suspend fun unhidePosBestseller(stockItemId: String): Boolean = hiddenBestsellers.remove(stockItemId)

    override suspend fun currentStaffDisplayName(): String = "Fake Operator"

    override suspend fun listPosParkedCarts(limit: Int): List<PosParkedCart> =
        parkedCarts.take(limit).map { id ->
            val lines = cartLines[id].orEmpty()
            PosParkedCart(
                id = id,
                documentNumber = "PARK-${id.take(6)}",
                updatedAt = null,
                currency = CurrencyCode.USD,
                total = lines.sumOf { it.lineTotal },
                lineCount = lines.size,
            )
        }

    override suspend fun salesInvoiceDocumentNumber(invoiceId: String): String = "INV-FAKE-${invoiceId.take(6)}"

    override suspend fun listPosPopularPins(): List<PosPopularPin> = popularPins.toList()

    override suspend fun upsertPosPopularPin(pin: PosPopularPin): String {
        popularPins.removeAll { it.kind == pin.kind && it.itemKey == pin.itemKey }
        popularPins.add(0, pin)
        while (popularPins.size > 24) popularPins.removeLast()
        return pin.stableKey
    }

    override suspend fun deletePosPopularPin(kind: PosPopularItemKind, itemKey: String): Boolean =
        popularPins.removeAll { it.kind == kind && it.itemKey == itemKey }

    override suspend fun listPosRecentInvoices(
        query: String?,
        limit: Int,
    ): List<PosInvoiceSummary> {
        val rows = listOf(
            PosInvoiceSummary(
                id = "00000000-0000-4000-8000-0000000000i1",
                documentNumber = "SINV-00001",
                customerId = null,
                customerName = "Walk-in",
                total = 110.0,
                currency = CurrencyCode.USD,
                postedAt = "2026-09-07T06:00:00Z",
            ),
        )
        val q = query?.trim()?.lowercase().orEmpty()
        return rows.filter { row ->
            q.isBlank() || row.id.lowercase() == q ||
                row.documentNumber.orEmpty().lowercase().contains(q) ||
                row.customerName.orEmpty().lowercase().contains(q)
        }.take(limit.coerceIn(1, 200))
    }

    override suspend fun pullPosOfflineSnapshot(warehouseId: String): OfflinePosSnapshot {
        require(warehouseId.isNotBlank()) { "warehouseId required for ${RpcNames.PULL_POS_OFFLINE_SNAPSHOT}" }
        val oemA = "P2-POS-SMOKE-001"
        val oemB = "FAKE-PAD-001"
        val refA = lookupStockItemByOem(oemA)
        val refB = lookupStockItemByOem(oemB)
        return OfflinePosSnapshot(
            warehouseId = warehouseId,
            pulledAt = "2026-08-03T00:00:00Z",
            priceListId = "00000000-0000-4000-8000-0000000000pl",
            currency = CurrencyCode.USD,
            items = listOf(
                OfflineCatalogItem(
                    stockItemId = refA.stockItemId,
                    oemPartNumber = oemA,
                    description = "Smoke pad",
                    uomId = refA.uomId,
                    unitPrice = 12.5,
                    coreCharge = 0.0,
                    saleableQty = 20.0,
                    currency = CurrencyCode.USD,
                ),
                OfflineCatalogItem(
                    stockItemId = refB.stockItemId,
                    oemPartNumber = oemB,
                    description = "Demo pad",
                    uomId = refB.uomId,
                    unitPrice = 8.0,
                    coreCharge = 2.0,
                    saleableQty = 5.0,
                    currency = CurrencyCode.USD,
                ),
            ),
        )
    }

    override suspend fun replayOfflinePosSale(
        clientSaleId: String,
        payload: OfflineSaleReplayPayload,
    ): String {
        require(clientSaleId.isNotBlank()) { "clientSaleId required for ${RpcNames.REPLAY_OFFLINE_POS_SALE}" }
        offlineSaleReceipts[clientSaleId]?.let { return it }
        require(payload.warehouseId.isNotBlank()) { "warehouseId required" }
        require(payload.lines.isNotEmpty()) { "lines required" }
        require(payload.tenders.isNotEmpty()) { "tenders required" }
        require(payload.tenders.all { it.tender.equals("cash", ignoreCase = true) }) {
            "offline_tender_not_allowed: cash only"
        }
        val snap = pullPosOfflineSnapshot(payload.warehouseId)
        for (line in payload.lines) {
            require(line.qty > 0) { "qty must be > 0" }
            val item = snap.items.find { it.stockItemId == line.stockItemId }
                ?: throw IllegalStateException("offline_price_conflict: unknown item ${line.stockItemId}")
            if (kotlin.math.abs(item.unitPrice - line.expectedUnitPrice) > 0.05) {
                throw IllegalStateException(
                    "offline_price_conflict: item ${line.stockItemId} expected ${line.expectedUnitPrice} got ${item.unitPrice}",
                )
            }
        }
        val invoiceId = UUID.randomUUID().toString()
        offlineSaleReceipts[clientSaleId] = invoiceId
        return invoiceId
    }

    override suspend fun resolveStaffLoginEmail(identifier: String): String {
        val raw = identifier.trim()
        require(raw.isNotBlank()) { "invalid credentials" }
        return when {
            raw.contains("@") -> raw.lowercase()
            raw.equals("GTRB1001", ignoreCase = true) -> "manager@gtr.local"
            raw.startsWith("+") || raw.all { it.isDigit() } -> "phone.staff@gtr.local"
            else -> "$raw@staff.gtr.local"
        }
    }

    private val loginFailCounts = mutableMapOf<String, Int>()
    private var fakeDefaultLanding: String? = null

    /** Test hook: set organogram landing override (`pos`|`hub`|null). */
    fun setFakeDefaultLanding(value: String?) {
        fakeDefaultLanding = value?.trim()?.lowercase()
    }

    override suspend fun staffLoginIsLocked(identifier: String): Boolean {
        val key = identifier.trim().lowercase()
        if (key.isEmpty()) return false
        return (loginFailCounts[key] ?: 0) >= 5
    }

    override suspend fun recordStaffLoginAttempt(identifier: String, success: Boolean) {
        val key = identifier.trim().lowercase()
        if (key.isEmpty()) return
        if (success) {
            loginFailCounts.remove(key)
        } else {
            loginFailCounts[key] = (loginFailCounts[key] ?: 0) + 1
        }
    }

    override suspend fun myDefaultLanding(): String? = fakeDefaultLanding

    override suspend fun applyPosLinePriceOverride(
        lineId: String,
        unitPrice: Double,
        notes: String?,
    ): String {
        require(fakeIsPosApprover) { "admin or shop manager approval required" }
        require(lineId.isNotBlank())
        require(unitPrice >= 0.0) { "unit price must be >= 0" }
        cartLines.values.forEach { lines ->
            val idx = lines.indexOfFirst { it.id == lineId }
            if (idx >= 0) {
                val prev = lines[idx]
                require(!prev.isCoreCharge) { "cannot override core-charge lines" }
                val unit = kotlin.math.round(unitPrice * 10000.0) / 10000.0
                lines[idx] = prev.copy(
                    unitPrice = unit,
                    lineTotal = kotlin.math.round(unit * prev.qty * 100.0) / 100.0,
                )
                return lineId
            }
        }
        error("line not found: $lineId")
    }

    override suspend fun lookupSaleableQtyByOem(oemPartNumber: String): Double? {
        val oem = oemPartNumber.trim()
        if (oem.isEmpty()) return null
        // Deterministic demo qty for Fake catalog tiles.
        return if (oem.contains("SMOKE", ignoreCase = true)) 12.0 else 4.0
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
        listOf("admin", "hr", "sales", "warehouse", "finance")

    override suspend fun listMyModuleAccess(): List<String> = emptyList()

    override suspend fun searchCustomers(query: String): List<CustomerOption> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val uuidLike = UUID_REGEX.matches(q)
        return if (uuidLike) {
            customers.filter { it.id.equals(q, ignoreCase = true) }
        } else {
            customers.filter { c ->
                listOfNotNull(c.displayName, c.businessName, c.email, c.phoneE164, c.whatsappE164)
                    .any { it.contains(q, ignoreCase = true) }
            }
        }
    }

    override suspend fun createPosCustomer(
        kind: PosCustomerKind,
        displayName: String,
        businessName: String?,
        email: String?,
        phoneE164: String?,
        whatsappE164: String?,
    ): String {
        require(displayName.isNotBlank())
        val id = UUID.randomUUID().toString()
        customers += CustomerOption(id, displayName.trim(), kind, businessName, email, phoneE164, whatsappE164)
        return id
    }

    override suspend fun updatePosCustomer(
        customerId: String,
        kind: PosCustomerKind,
        displayName: String,
        businessName: String?,
        email: String?,
        phoneE164: String?,
        whatsappE164: String?,
    ) {
        val i = customers.indexOfFirst { it.id == customerId }
        require(i >= 0) { "customer not found" }
        customers[i] = CustomerOption(customerId, displayName.trim(), kind, businessName, email, phoneE164, whatsappE164)
    }

    override suspend fun listPosCustomerGarage(customerId: String): List<CustomerGarageVehicle> =
        customerGarage.filter { it.customerId == customerId }.sortedByDescending { it.isPrimary }

    override suspend fun upsertPosCustomerGarageVehicle(
        customerId: String,
        vehicleId: String?,
        modelSlug: String,
        make: String,
        model: String,
        generation: String,
        chassisCode: String,
        engine: String,
        vin: String?,
        isPrimary: Boolean,
    ): String {
        require(customers.any { it.id == customerId }) { "customer not found" }
        val id = vehicleId ?: UUID.randomUUID().toString()
        if (isPrimary) {
            for (i in customerGarage.indices) {
                if (customerGarage[i].customerId == customerId) customerGarage[i] = customerGarage[i].copy(isPrimary = false)
            }
        }
        val row = CustomerGarageVehicle(
            id, customerId, make, modelSlug, model, generation, chassisCode, engine, vin, isPrimary,
        )
        val i = customerGarage.indexOfFirst { it.id == id && it.customerId == customerId }
        if (i >= 0) customerGarage[i] = row else customerGarage += row
        return id
    }

    override suspend fun setPosCartCustomer(cartId: String, customerId: String?) {
        require(cartId in openCarts) { "open cart required" }
        if (customerId.isNullOrBlank()) cartCustomers.remove(cartId) else cartCustomers[cartId] = customerId
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

    override suspend fun listFleetVehicles(status: FleetVehicleStatus?): List<FleetVehicleSummary> {
        return fleetVehicles
            .filter { status == null || it.status == status }
            .sortedBy { it.plate }
    }

    override suspend fun upsertFleetVehicle(
        plate: String,
        label: String?,
        status: FleetVehicleStatus,
        assignedDriverUserId: String?,
        notes: String?,
        id: String?,
    ): String {
        val normalized = plate.trim().uppercase()
        require(normalized.isNotEmpty()) { "plate is required" }
        val assignee = assignedDriverUserId?.trim()?.takeIf { it.isNotEmpty() }
        if (assignee != null) {
            require(assignee in knownDriverIds) {
                "assigned_driver_user_id must have driver staff role (or be null)"
            }
        }
        val cleanLabel = label?.trim()?.takeIf { it.isNotEmpty() }
        val cleanNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
        if (id.isNullOrBlank()) {
            require(fleetVehicles.none { it.plate == normalized }) {
                "fleet plate already exists: $normalized"
            }
            val newId = UUID.randomUUID().toString()
            fleetVehicles.add(
                FleetVehicleSummary(
                    id = newId,
                    plate = normalized,
                    label = cleanLabel,
                    status = status,
                    assignedDriverUserId = assignee,
                    notes = cleanNotes,
                ),
            )
            return newId
        }
        val idx = fleetVehicles.indexOfFirst { it.id == id }
        require(idx >= 0) { "fleet vehicle not found" }
        require(fleetVehicles.none { it.plate == normalized && it.id != id }) {
            "fleet plate already exists: $normalized"
        }
        fleetVehicles[idx] = FleetVehicleSummary(
            id = id,
            plate = normalized,
            label = cleanLabel,
            status = status,
            assignedDriverUserId = assignee,
            notes = cleanNotes,
        )
        return id
    }

    override suspend fun setFleetVehicleStatus(id: String, status: FleetVehicleStatus): String {
        require(id.isNotBlank())
        val idx = fleetVehicles.indexOfFirst { it.id == id }
        require(idx >= 0) { "fleet vehicle not found" }
        fleetVehicles[idx] = fleetVehicles[idx].copy(status = status)
        return id
    }

    // --- HR onboarding ---

    private val hrOnboardingDrafts = mutableListOf(
        HrOnboardingDraft(
            id = FAKE_HR_DRAFT_ID,
            employeeId = null,
            stage = HrOnboardingStage.PERSONAL,
            payload = mapOf(
                "full_name" to "Fake New Hire",
                "email" to "newhire@example.com",
                "phone_e164" to "+263771000999",
                "grade_id" to FAKE_HR_GRADE_ID,
            ),
            bankingJson = null,
            healthJson = null,
            completedAt = null,
            updatedAt = "2026-08-03T12:00:00Z",
        ),
    )
    private val hrGrades = listOf(
        HrGradeOption(FAKE_HR_GRADE_ID, "A1", "Grade A1", 10),
        HrGradeOption(FAKE_HR_GRADE_ID_2, "B2", "Grade B2", 20),
    )
    private val hrRoles = listOf(
        HrRoleOption(FAKE_HR_ROLE_ID, "Parts Counter", "Sales", FAKE_HR_GRADE_ID),
        HrRoleOption(FAKE_HR_ROLE_ID_2, "Warehouse Clerk", "Warehouse", FAKE_HR_GRADE_ID_2),
    )
    private val hrDraftSeq = AtomicInteger(2)

    override suspend fun listHrOnboardingDrafts(): List<HrOnboardingDraft> =
        hrOnboardingDrafts.filter { it.completedAt == null }
            .sortedByDescending { it.updatedAt }

    override suspend fun listHrGrades(): List<HrGradeOption> =
        hrGrades.sortedBy { it.sortOrder }

    override suspend fun listHrRoles(): List<HrRoleOption> =
        hrRoles.sortedBy { it.title }

    override suspend fun saveHrOnboardingStage(
        draftId: String?,
        stage: HrOnboardingStage,
        payload: Map<String, String?>,
        bankingJson: Map<String, String?>?,
        healthJson: Map<String, String?>?,
        employeeId: String?,
    ): String {
        val now = java.time.Instant.now().toString()
        if (draftId.isNullOrBlank()) {
            val id = "00000000-0000-4000-8000-0000000000h${hrDraftSeq.getAndIncrement()}"
            hrOnboardingDrafts.add(
                0,
                HrOnboardingDraft(
                    id = id,
                    employeeId = employeeId,
                    stage = stage,
                    payload = payload,
                    bankingJson = bankingJson,
                    healthJson = healthJson,
                    completedAt = null,
                    updatedAt = now,
                ),
            )
            return id
        }
        val idx = hrOnboardingDrafts.indexOfFirst { it.id == draftId }
        require(idx >= 0) { "onboarding draft not found" }
        val prev = hrOnboardingDrafts[idx]
        require(prev.completedAt == null) { "onboarding already completed" }
        hrOnboardingDrafts[idx] = prev.copy(
            stage = stage,
            payload = payload,
            bankingJson = bankingJson ?: prev.bankingJson,
            healthJson = healthJson ?: prev.healthJson,
            employeeId = employeeId ?: prev.employeeId,
            updatedAt = now,
        )
        return draftId
    }

    override suspend fun completeHrOnboarding(draftId: String): HrOnboardingCompleteResult {
        require(draftId.isNotBlank())
        val idx = hrOnboardingDrafts.indexOfFirst { it.id == draftId }
        require(idx >= 0) { "onboarding draft not found" }
        val draft = hrOnboardingDrafts[idx]
        require(draft.completedAt == null) { "onboarding already completed" }
        require(!draft.payload["full_name"].isNullOrBlank()) { "payload.full_name required" }
        require(!draft.payload["grade_id"].isNullOrBlank()) { "payload.grade_id required" }
        require(draft.bankingJson != null) { "banking_json required before completion" }
        val gradeCode = hrGrades.firstOrNull { it.id == draft.payload["grade_id"] }?.code ?: "A1"
        val empId = draft.employeeId ?: UUID.randomUUID().toString()
        val empCode = "GTR${gradeCode}001"
        val userId = draft.payload["user_id"]
        val now = java.time.Instant.now().toString()
        hrOnboardingDrafts[idx] = draft.copy(
            employeeId = empId,
            stage = HrOnboardingStage.CREDENTIALS,
            completedAt = now,
            updatedAt = now,
            payload = draft.payload + mapOf(
                "employee_code" to empCode,
                "completed" to "true",
            ),
        )
        return HrOnboardingCompleteResult(
            draftId = draftId,
            employeeId = empId,
            employeeCode = empCode,
            email = draft.payload["email"],
            phoneE164 = draft.payload["phone_e164"],
            userId = userId,
            mustChangePassword = !userId.isNullOrBlank(),
            message = "Fake complete — create auth via Edge when user_id absent",
        )
    }

    override suspend fun createHrOnboardingAuthUser(employeeId: String): HrOnboardingAuthResult {
        require(employeeId.isNotBlank())
        val userId = UUID.randomUUID().toString()
        return HrOnboardingAuthResult(
            employeeId = employeeId,
            userId = userId,
            created = true,
            mustChangePassword = true,
            channels = listOf(
                HrOnboardingAuthChannel("email", "stub"),
                HrOnboardingAuthChannel("sms", "stub"),
                HrOnboardingAuthChannel("whatsapp", "skipped"),
            ),
        )
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
        /** What the demo manager's printed badge QR carries (fake backend only). */
        const val FAKE_MANAGER_BADGE: String = "GTRMGR1:fake-manager:demo-badge"

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
        const val FAKE_FLEET_VEHICLE_ID = "00000000-0000-4000-8000-0000000000fv"
        const val FAKE_HR_DRAFT_ID = "00000000-0000-4000-8000-0000000000hd"
        const val FAKE_HR_GRADE_ID = "00000000-0000-4000-8000-0000000000hg"
        const val FAKE_HR_GRADE_ID_2 = "00000000-0000-4000-8000-0000000000hh"
        const val FAKE_HR_ROLE_ID = "00000000-0000-4000-8000-0000000000hr"
        const val FAKE_HR_ROLE_ID_2 = "00000000-0000-4000-8000-0000000000hs"
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

    // --- POS till sessions (in memory; one operator)

    private val tills = mutableListOf<PosTillSessionRow>()
    private val tillCash = mutableMapOf<String, Double>()

    private fun replaceTill(row: PosTillSessionRow) {
        val i = tills.indexOfFirst { it.id == row.id }
        if (i >= 0) tills[i] = row else tills.add(0, row)
    }

    override suspend fun getMyOpenPosTillSession(deviceId: String?): PosTillSessionRow? =
        tills.firstOrNull { it.status == "open" || it.status == "variance_pending" }

    override suspend fun openPosTillSession(warehouseId: String, deviceId: String, openingFloat: Double, currency: CurrencyCode): String {
        check(getMyOpenPosTillSession(deviceId) == null) { "operator already has an open till" }
        val id = "till-${tills.size + 1}"
        replaceTill(
            PosTillSessionRow(id, warehouseId, deviceId, currency, "fake-operator", openingFloat, "open",
                null, null, null, null, java.time.OffsetDateTime.now().withNano(0).toString(), null),
        )
        tillCash[id] = openingFloat
        return id
    }

    override suspend fun recordPosTillCashMovement(sessionId: String, kind: String, amount: Double, reasonCode: String, notes: String?): String {
        require(amount > 0) { "amount must be > 0" }
        tillCash[sessionId] = (tillCash[sessionId] ?: 0.0) + if (kind == "cash_in") amount else -amount
        return "move-${System.nanoTime()}"
    }

    override suspend fun submitPosTillDenominatedClose(
        sessionId: String,
        lines: List<PosDenominationLine>,
        varianceReasonCode: String?,
        notes: String?,
    ): PosTillCloseResult {
        val row = tills.first { it.id == sessionId && it.status == "open" }
        val counted = Math.round(lines.sumOf { it.denomination * it.quantity } * 100) / 100.0
        val expected = Math.round((tillCash[sessionId] ?: 0.0) * 100) / 100.0
        val variance = Math.round((counted - expected) * 100) / 100.0
        if (kotlin.math.abs(variance) > 0.009 && varianceReasonCode.isNullOrBlank()) error("variance reason required")
        val status = if (kotlin.math.abs(variance) > 0.009) "variance_pending" else "closed"
        replaceTill(row.copy(status = status, expectedCash = expected, countedCash = counted, variance = variance, varianceReasonCode = varianceReasonCode))
        return PosTillCloseResult(sessionId, expected, counted, variance, status)
    }

    override suspend fun approvePosTillVariance(sessionId: String, reasonCode: String, notes: String?) {
        val row = tills.first { it.id == sessionId && it.status == "variance_pending" }
        replaceTill(row.copy(status = "closed", closedAt = java.time.OffsetDateTime.now().withNano(0).toString()))
    }

    override suspend fun listPosApprovalReasons(action: String): List<PosApprovalReason> = when (action) {
        "cash_out" -> listOf(
            PosApprovalReason("petty_cash", "Petty cash", false),
            PosApprovalReason("bank_drop", "Bank drop", false),
            PosApprovalReason("other_cash_out", "Other", true),
        )
        "discount_percent" -> listOf(
            PosApprovalReason("customer_retention", "Customer retention", false),
            PosApprovalReason("price_match", "Price match", false),
            PosApprovalReason("damaged_packaging", "Damaged packaging", false),
        )
        "price_override_delta_percent" -> listOf(
            PosApprovalReason("supplier_price", "Supplier price change", false),
            PosApprovalReason("advertised_price", "Advertised price", false),
            PosApprovalReason("data_correction", "Price data correction", false),
        )
        "void_cart" -> listOf(
            PosApprovalReason("customer_cancelled", "Customer cancelled", false),
            PosApprovalReason("duplicate_cart", "Duplicate sale", false),
            PosApprovalReason("pricing_error", "Pricing error", true),
            PosApprovalReason("operator_error", "Operator error", true),
        )
        "refund_full_invoice" -> listOf(
            PosApprovalReason("wrong_part", "Wrong part", false),
            PosApprovalReason("customer_changed_mind", "Customer changed mind", false),
            PosApprovalReason("defective", "Defective", false),
            PosApprovalReason("manager_exception", "Manager exception", true),
        )
        "return_post" -> listOf(
            PosApprovalReason("wrong_part", "Wrong part", false),
            PosApprovalReason("customer_changed_mind", "Customer changed mind", false),
            PosApprovalReason("defective", "Defective", false),
            PosApprovalReason("fitment_issue", "Does not fit", false),
        )
        "core_return" -> listOf(
            PosApprovalReason("eligible_core", "Eligible core", false),
            PosApprovalReason("manager_exception", "Manager exception", true),
        )
        "till_variance" -> listOf(
            PosApprovalReason("count_error", "Count error", false),
            PosApprovalReason("short_change", "Short change given", false),
            PosApprovalReason("other_variance", "Other", true),
        )
        else -> emptyList()
    }

    override suspend fun listPosHandoverOperators(): List<PosHandoverOperatorRow> = listOf(
        PosHandoverOperatorRow("fake-operator-2", "E002", "Rudo Moyo"),
        PosHandoverOperatorRow("fake-operator-3", "E003", "Tendai Ncube"),
    )

    override suspend fun handoverPosTillSession(sessionId: String, newOperatorUserId: String, notes: String?) {
        val row = tills.first { it.id == sessionId && it.status == "open" }
        replaceTill(row.copy(operatorUserId = newOperatorUserId))
    }

    override suspend fun listPosTillSessions(limit: Int): List<PosTillSessionRow> = tills.take(limit)

    // --- POS approval policies (in memory; same actions as the server)

    private val approvalPolicies = mutableListOf(
        "cash_out", "core_return", "discount_percent", "price_override_delta_percent", "refund_full_invoice",
        "return_post", "till_variance", "void_cart", "warranty_decision",
    ).map { PosApprovalPolicy(it, 0.0, alwaysRequireManager = true, reasonRequired = true, updatedAt = null) }.toMutableList()

    override suspend fun posActionRequiresManager(action: String, value: Double): Boolean =
        approvalPolicies.firstOrNull { it.action == action }?.let { it.alwaysRequireManager || value > it.thresholdValue } ?: true

    override suspend fun listPosApprovalPolicies(): List<PosApprovalPolicy> = approvalPolicies.toList()

    override suspend fun setPosApprovalPolicy(action: String, thresholdValue: Double, alwaysRequireManager: Boolean, reasonRequired: Boolean) {
        require(thresholdValue >= 0) { "threshold must be >= 0" }
        val row = PosApprovalPolicy(action, thresholdValue, alwaysRequireManager, reasonRequired, java.time.OffsetDateTime.now().toString())
        val i = approvalPolicies.indexOfFirst { it.action == action }
        if (i >= 0) approvalPolicies[i] = row else approvalPolicies += row
    }

    // --- POS manager badges (fake: the demo manager's card)

    override suspend fun posBadgeApprove(badge: String, action: String, args: Map<String, Any?>, deviceId: String?): PosBadgeApproval {
        if (badge.trim() != FAKE_MANAGER_BADGE) return PosBadgeApproval(false, null, "badge not recognised")
        return runCatching {
            val notes = args["notes"]?.toString()
            when (action) {
                "discount" -> applyPosCartDiscount(args["cart_id"].toString(), args["percent"].toString().toDouble(), notes)
                "price_override" -> applyPosLinePriceOverride(args["line_id"].toString(), args["unit_price"].toString().toDouble(), notes)
                "void_sale" -> voidPosCart(args["cart_id"].toString(), notes)
                "refund" -> postPosRefund(args["invoice_id"].toString(), notes)
                "cash_out" -> recordPosTillCashMovement(args["session_id"].toString(), args["kind"].toString(), args["amount"].toString().toDouble(), args["reason_code"].toString(), notes)
                "till_variance" -> approvePosTillVariance(args["session_id"].toString(), args["reason_code"].toString(), notes)
                "till_handover" -> handoverPosTillSession(args["session_id"].toString(), args["new_operator_user_id"].toString(), notes)
                "return_post" -> postPosReturnCase(args["return_case_id"].toString())
                "core_return" -> postPosCoreReturn(
                    args["invoice_id"].toString(), args["core_line_id"].toString(), args["qty"].toString().toDouble(), args["resolution"].toString(),
                    args["reason_code"].toString(), args["till_session_id"]?.toString(), notes,
                )
                "warranty_approve" -> approvePosWarrantyClaim(
                    args["claim_id"].toString(),
                    args["resolution"].toString(),
                    (args["lines"] as? List<*>)?.map { l -> (l as Map<*, *>).let { it["stock_item_id"].toString() to it["qty"].toString().toDouble() } },
                    (args["replacement_lines"] as? List<*>)?.map { l ->
                        (l as Map<*, *>).let { PosReplacementLineInput(it["stock_item_id"].toString(), it["uom_id"].toString(), it["qty"].toString().toDouble()) }
                    },
                )
                "warranty_reject" -> rejectPosWarrantyClaim(args["claim_id"].toString(), args["reason"].toString())
                "card_refund_begin" -> return PosBadgeApproval(
                    true, "Demo manager", null,
                    beginPosCardTerminalRefund(args["invoice_id"].toString(), args["terminal_id"].toString(), args["request_id"].toString()).attemptId,
                )
                "card_refund_finish" -> finalizePosCardTerminalRefund(args["attempt_id"].toString(), notes)
                else -> error("unknown action $action")
            }
            PosBadgeApproval(true, "Demo manager", null)
        }.getOrElse { PosBadgeApproval(false, "Demo manager", it.message) }
    }


    // --- POS reserve-first checkout (in memory). EcoCash approves after a few seconds; a number
    // ending 0 is declined and one ending 9 never answers (recovery); ContiPay is not set up.

    private data class FakeOrder(
        val orderId: String,
        val cartId: String,
        var state: String,
        var total: Double,
        val currency: CurrencyCode,
        var expiresAt: Long,
        var provider: String? = null,
        var intentId: String? = null,
        var providerStatus: String? = null,
        var providerFailure: String? = null,
        var resolveAt: Long = 0,
        var declines: Boolean = false,
        var invoiceId: String? = null,
    )
    private val fakeOrders = mutableMapOf<String, FakeOrder>()
    private val fakeSettlements = mutableMapOf<String, String>()

    private fun FakeOrder.tick() {
        if (state != "payment_processing" || System.currentTimeMillis() < resolveAt) return
        if (declines) {
            state = "payment_failed"; providerStatus = "failed"; providerFailure = "Insufficient funds in the wallet."
        } else {
            state = "paid"; providerStatus = "settled"
            invoiceId = "inv-${orderId.takeLast(6)}"
        }
    }

    override suspend fun preparePosCommerceCheckout(cartId: String, checkoutRequestId: String, receiptEmail: String?, receiptWhatsappE164: String?): String {
        fakeOrders.values.firstOrNull { it.cartId == cartId && it.state in setOf("awaiting_payment", "payment_processing", "payment_failed") && it.expiresAt > System.currentTimeMillis() }
            ?.let { return it.orderId }
        val lines = cartLines[cartId].orEmpty()
        require(lines.isNotEmpty()) { "cart has no lines" }
        val id = "order-${UUID.randomUUID().toString().take(8)}"
        fakeOrders[id] = FakeOrder(id, cartId, "awaiting_payment", lines.sumOf { it.lineTotal }, CurrencyCode.USD, System.currentTimeMillis() + 20 * 60_000)
        return id
    }

    override suspend fun posPaymentStatus(orderId: String): PosPaymentStatus {
        val o = fakeOrders[orderId] ?: error("commerce order not found")
        o.tick()
        return PosPaymentStatus(
            o.orderId, o.cartId, o.state, o.total, o.currency, java.time.Instant.ofEpochMilli(o.expiresAt).toString(),
            o.provider, o.intentId, o.providerStatus, o.providerFailure, o.provider.takeIf { o.invoiceId != null }, null, o.invoiceId, null, emptyList(),
        )
    }

    override suspend fun settlePosCommerceTenders(orderId: String, paymentRequestId: String, tenders: List<PosTenderLine>): String {
        fakeSettlements[paymentRequestId]?.let { return it }
        val o = fakeOrders[orderId] ?: error("commerce order not found")
        check(o.state == "awaiting_payment" || o.state == "payment_failed") { "manual tenders cannot settle order in state ${o.state}" }
        check(kotlin.math.abs(tenders.sumOf { it.amount } - o.total) < 0.01) { "manual tenders must equal order total" }
        val invoice = checkoutPosCartWithTenders(o.cartId, tenders).invoiceId
        o.state = "paid"; o.invoiceId = invoice
        fakeSettlements[paymentRequestId] = invoice
        return invoice
    }

    override suspend fun posProviderAvailability(provider: String): String? =
        if (provider == "contipay") "Not set up for this shop yet." else null

    override suspend fun startPosProviderPayment(orderId: String, provider: String, msisdn: String?, method: String?, returnUrl: String): PosProviderStart {
        val o = fakeOrders[orderId] ?: error("commerce order not found")
        check(provider != "contipay") { "CONTIPAY_API_KEY / CONTIPAY_MERCHANT_ID required" }
        val digits = msisdn.orEmpty().filter { it.isDigit() }
        if (provider == "ecocash") require(Regex("^(263|0)7\\d{8}$").matches(digits)) { "payer_msisdn must normalize to 263XXXXXXXXX" }
        o.state = "payment_processing"; o.provider = provider; o.intentId = "intent-${UUID.randomUUID().toString().take(6)}"
        o.providerStatus = "pending"; o.providerFailure = null; o.declines = digits.endsWith("0")
        o.resolveAt = if (digits.endsWith("9")) Long.MAX_VALUE else System.currentTimeMillis() + 5_000
        return PosProviderStart(o.intentId!!, if (provider == "paynow") "https://www.paynow.co.zw/payment/demo/${o.intentId}" else null,
            if (provider == "ecocash") "PIN request sent to $msisdn." else null)
    }

    override suspend fun cancelPosCommerceCheckout(orderId: String, reason: String) {
        val o = fakeOrders[orderId] ?: error("commerce order not found")
        check(o.state != "payment_processing") { "payment is in flight; resolve it from recovery" }
        check(o.invoiceId == null) { "order already settled" }
        o.state = "cancelled"
    }

    override suspend fun checkoutPosCartOnAccount(cartId: String, receiptEmail: String?, receiptWhatsappE164: String?): String {
        checkNotNull(cartCustomers[cartId]) { "registered customer required for on-account checkout" }
        val lines = cartLines[cartId].orEmpty()
        return checkoutPosCartWithTenders(cartId, listOf(PosTenderLine("bank", lines.sumOf { it.lineTotal }))).invoiceId
    }

    override suspend fun listPosPaymentRecovery(): List<PosRecoveryRow> =
        fakeOrders.values.onEach { it.tick() }.filter { it.state in setOf("payment_processing", "payment_failed", "allocation_pending") }.map {
            PosRecoveryRow(it.orderId, it.state, it.total, it.currency, it.provider, null, it.invoiceId, null, java.time.Instant.now().toString(), 0)
        }

    override suspend fun repairPosPaidOrder(orderId: String, notes: String?): String = error("paid-but-unfinalized commerce order required")

    override suspend fun listPosPickupOrders(query: String?): List<PosPickupRow> =
        fakeOrders.values.onEach { it.tick() }.filter { it.state == "paid" && it.invoiceId != null }.map {
            PosPickupRow(it.orderId, it.invoiceId, null, it.state, it.total, it.currency, it.invoiceId, it.provider, java.time.Instant.now().toString())
        }

    override suspend fun collectPosCommerceOrder(orderId: String, notes: String?) {
        val o = fakeOrders[orderId] ?: error("eligible pickup order required")
        check(o.state == "paid") { "eligible pickup order required" }
        o.state = "delivered"
    }

    // Part payments: one session per order; cash/bank captured at once (bank needs a reference),
    // store credit held for the registered customer; the sale posts when the parts cover it.
    private class FakeSplit(val id: String, val orderId: String) {
        var status = "open"
        val legs = mutableListOf<PosSplitLegRow>()
        val refunds = mutableListOf<PosSplitRefundRow>()
        var invoiceId: String? = null
        val keys = mutableSetOf<String>()
    }
    private val fakeSplits = mutableMapOf<String, FakeSplit>()

    private fun FakeSplit.view(): PosSplitSession {
        val o = fakeOrders.getValue(orderId)
        fun sum(vararg st: String) = legs.filter { it.status in st }.sumOf { it.amount }
        val captured = sum("captured", "allocated", "refund_review", "refund_pending")
        val held = sum("held")
        val locked = captured + held
        if (status !in setOf("settled", "cancelled", "refunded", "refund_review", "refund_pending")) {
            status = if (locked + 0.01 >= o.total) "fully_committed" else if (locked > 0) "partially_captured" else "open"
        }
        val due = (o.total - locked).coerceAtLeast(0.0)
        return PosSplitSession(id, orderId, status, o.total, o.currency, captured, held, 0.0, locked, due, due, invoiceId, null, legs.toList(), refunds.toList())
    }

    private suspend fun FakeSplit.finalize(): PosSplitSession {
        val v = view()
        if (v.status != "fully_committed") return v
        val o = fakeOrders.getValue(orderId)
        var left = o.total
        val tenders = mutableListOf<PosTenderLine>()
        legs.replaceAll { l ->
            if (l.status != "captured" && l.status != "held") return@replaceAll l
            val apply = minOf(l.amount, left.coerceAtLeast(0.0)); left -= apply
            if (apply > 0) tenders += PosTenderLine(l.tender, apply)
            l.copy(status = if (l.status == "held") "allocated" else l.status, appliedAmount = apply)
        }
        invoiceId = checkoutPosCartWithTenders(o.cartId, tenders).invoiceId
        o.state = "paid"; o.invoiceId = invoiceId
        status = if (refunds.any { it.status != "settled" && it.status != "cancelled" }) "refund_review" else "settled"
        return view()
    }

    override suspend fun findPosSplitPayment(orderId: String): PosSplitSession? = fakeSplits.values.firstOrNull { it.orderId == orderId }?.view()

    override suspend fun startPosSplitPayment(orderId: String): PosSplitSession {
        val o = fakeOrders[orderId] ?: error("unsettled reserve-first commerce order required")
        check(o.invoiceId == null && o.state in setOf("awaiting_payment", "payment_processing", "payment_failed")) { "unsettled reserve-first commerce order required" }
        val sp = fakeSplits.values.firstOrNull { it.orderId == orderId } ?: FakeSplit("split-${UUID.randomUUID().toString().take(8)}", orderId).also { fakeSplits[it.id] = it }
        o.state = "payment_processing"; o.expiresAt = maxOf(o.expiresAt, System.currentTimeMillis() + 60 * 60_000)
        return sp.view()
    }

    override suspend fun getPosSplitPayment(sessionId: String): PosSplitSession = (fakeSplits[sessionId] ?: error("split payment session not found")).view()

    override suspend fun addPosSplitPaymentLeg(sessionId: String, tender: String, amount: Double, requestId: String, externalReference: String?): PosSplitSession {
        val sp = fakeSplits[sessionId] ?: error("split payment session not found")
        if (requestId in sp.keys) return sp.view()
        val v = sp.view()
        check(v.status !in setOf("settled", "refund_review", "refund_pending", "refunded", "cancelled")) { "split session does not accept new payments in status ${v.status}" }
        require(amount > 0 && amount <= v.availableToAllocate + 0.01) { "split leg amount must be > 0 and <= available balance ${"%.2f".format(v.availableToAllocate)}" }
        require(tender != "bank" || !externalReference.isNullOrBlank()) { "bank split payment requires a transfer/reference number" }
        if (tender == "store_credit") checkNotNull(cartCustomers[fakeOrders.getValue(sp.orderId).cartId]) { "insufficient available store credit after active POS holds" }
        sp.keys += requestId
        sp.legs += PosSplitLegRow("leg-${UUID.randomUUID().toString().take(6)}", sp.legs.size + 1, tender, amount,
            if (tender == "store_credit") "held" else "captured", externalReference, externalReference, null, null, null)
        return sp.finalize()
    }

    override suspend fun acceptPosSplitAffordableItems(sessionId: String, items: List<Pair<String, Double>>, notes: String?): PosSplitSession {
        val sp = fakeSplits[sessionId] ?: error("split payment session not found")
        val v = sp.view()
        check(v.locked > 0) { "no locked payment is available for reduced-basket settlement" }
        val o = fakeOrders.getValue(sp.orderId)
        val lines = cartLines[o.cartId] ?: error("current operator cart required")
        val kept = items.map { (id, qty) ->
            val l = lines.firstOrNull { it.id == id } ?: error("invalid accepted quantity for cart line $id")
            require(qty > 0 && qty <= l.qty) { "invalid accepted quantity for cart line $id" }
            l.copy(qty = qty, lineTotal = l.lineTotal / l.qty * qty)
        }
        val total = kept.sumOf { it.lineTotal }
        check(total <= v.locked + 0.01) { "accepted basket total ${"%.2f".format(total)} must be > 0 and <= locked payment ${"%.2f".format(v.locked)}" }
        lines.clear(); lines.addAll(kept)
        o.total = total
        var left = total
        sp.legs.replaceAll { l ->
            if (l.status != "captured") return@replaceAll l
            val apply = minOf(l.amount, left.coerceAtLeast(0.0)); left -= apply
            val refund = l.amount - apply
            if (refund > 0.009) sp.refunds += PosSplitRefundRow("refund-${UUID.randomUUID().toString().take(6)}", l.id, "review", refund, "manual_review", null, null, null,
                notes ?: "Captured surplus after customer accepted reduced basket")
            l.copy(appliedAmount = apply, refundRequired = refund)
        }
        return sp.finalize()
    }

    override suspend fun requestPosSplitCancellation(sessionId: String, reason: String, feePolicy: String): PosSplitSession {
        val sp = fakeSplits[sessionId] ?: error("split payment session not found")
        check(sp.status != "settled") { "settled sale must use the posted invoice return/refund workflow" }
        sp.legs.replaceAll { l ->
            when (l.status) {
                "planned", "failed", "held" -> l.copy(status = "cancelled")
                "captured" -> {
                    sp.refunds += PosSplitRefundRow("refund-${UUID.randomUUID().toString().take(6)}", l.id, "review", l.amount, feePolicy, null, null, null, reason)
                    l.copy(status = "refund_review")
                }
                else -> l
            }
        }
        fakeOrders.getValue(sp.orderId).state = "cancelled"
        sp.status = if (sp.legs.any { it.status == "refund_review" }) "refund_review" else "cancelled"
        return sp.view()
    }

    override suspend fun retryPosSplitFinalization(sessionId: String): PosSplitSession = (fakeSplits[sessionId] ?: error("split payment session not found")).finalize()

    override suspend fun listPosSplitPaymentRecovery(): List<PosSplitRecoveryRow> = fakeSplits.values.map { it.view() }
        .filter { it.status in setOf("partially_captured", "fully_committed", "finalization_failed", "refund_review", "refund_pending") }
        .map { PosSplitRecoveryRow(null, null, java.time.Instant.now().toString(), it) }

    private fun refundStep(refundId: String, change: (FakeSplit, PosSplitRefundRow) -> PosSplitRefundRow): PosSplitSession {
        val sp = fakeSplits.values.firstOrNull { s -> s.refunds.any { it.id == refundId } } ?: error("split refund request not found")
        val i = sp.refunds.indexOfFirst { it.id == refundId }
        sp.refunds[i] = change(sp, sp.refunds[i])
        val open = sp.refunds.any { it.status != "settled" && it.status != "cancelled" }
        sp.status = when {
            sp.refunds[i].status == "failed" -> "refund_review"
            sp.refunds[i].status == "pending" -> "refund_pending"
            open -> "refund_pending"
            sp.invoiceId != null -> "settled"
            else -> "refunded"
        }
        return sp.view()
    }

    override suspend fun approvePosSplitRefund(refundId: String, feePolicy: String, customerFee: Double, notes: String?) = refundStep(refundId) { _, r ->
        check(r.status == "review" || r.status == "failed") { "review/failed refund request required" }
        require(customerFee == 0.0 || feePolicy == "customer_bears") { "customer fee deduction requires customer_bears policy" }
        r.copy(status = "pending", feePolicy = feePolicy, netCustomerRefund = r.grossAmount - customerFee)
    }

    override suspend fun completePosSplitRefund(refundId: String, providerRef: String, notes: String?) = refundStep(refundId) { _, r ->
        check(r.status in setOf("pending", "review", "failed")) { "refund cannot settle in status ${r.status}" }
        r.copy(status = "settled", providerRef = providerRef)
    }

    override suspend fun failPosSplitRefund(refundId: String, reason: String) = refundStep(refundId) { _, r -> r.copy(status = "failed", failureReason = reason) }

    // Card terminal (demo): one simulated card machine; results are trusted as sent (no device signature here).
    private val fakeTerminal = PosCardTerminalRow(
        "term-demo", "DEMO-1", "Demo card machine", "Demo bank", "android_intent_v1",
        mapOf("package_name" to "co.zw.nissangtr.demo.terminal", "purchase_action" to "co.zw.nissangtr.demo.PURCHASE",
            "reversal_action" to "co.zw.nissangtr.demo.REVERSAL", "status_action" to "co.zw.nissangtr.demo.STATUS"),
        null, null,
    )
    private val fakeAttempts = mutableMapOf<String, PosTerminalAttempt>()
    private val fakeAttemptKeys = mutableMapOf<String, String>()

    override suspend fun listPosCardTerminals(warehouseId: String?, deviceId: String?) = listOf(fakeTerminal)

    private fun newAttempt(requestId: String, operation: String, orderId: String?, legId: String?, amount: Double): PosTerminalAttempt {
        fakeAttemptKeys[requestId]?.let { return fakeAttempts.getValue(it) }
        val id = "att-${UUID.randomUUID().toString().take(8)}"
        val a = PosTerminalAttempt(
            id, operation, "initiated", fakeTerminal.id, fakeTerminal.label, fakeTerminal.adapterKey, fakeTerminal.adapterConfig,
            amount, CurrencyCode.USD, "GTR-CT-${requestId.replace("-", "").take(12)}", null, null, null, null, null, null, orderId, legId, null, null,
        )
        fakeAttempts[id] = a; fakeAttemptKeys[requestId] = id
        return a
    }

    override suspend fun beginPosCardTerminalPurchase(orderId: String, terminalId: String, requestId: String): PosTerminalAttempt {
        val o = fakeOrders[orderId] ?: error("unsettled commerce order required")
        check(o.invoiceId == null) { "unsettled commerce order required" }
        o.state = "payment_processing"
        return newAttempt(requestId, "purchase", orderId, null, o.total)
    }

    override suspend fun beginPosSplitCardTerminalLeg(legId: String, terminalId: String, requestId: String): PosTerminalAttempt {
        val sp = fakeSplits.values.firstOrNull { s -> s.legs.any { it.id == legId } } ?: error("planned card-terminal split leg required")
        val leg = sp.legs.first { it.id == legId }
        check(leg.status == "planned") { "planned card-terminal split leg required" }
        sp.legs.replaceAll { if (it.id == legId) it.copy(status = "pending") else it }
        return newAttempt(requestId, "purchase", sp.orderId, legId, leg.amount)
    }

    override suspend fun getPosCardTerminalAttempt(attemptId: String) = fakeAttempts[attemptId] ?: error("card terminal attempt not found")

    override suspend fun submitCardTerminalEvidence(payloadJson: String, signatureBase64: String): PosTerminalAttempt {
        val p = kotlinx.serialization.json.Json.parseToJsonElement(payloadJson) as kotlinx.serialization.json.JsonObject
        fun f(k: String) = (p[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        val id = f("attempt_id") ?: error("attempt_id required")
        val a = fakeAttempts[id] ?: error("card terminal attempt not found")
        if (a.status == "settled" || a.status == "reversed") return a
        val outcome = f("outcome") ?: error("invalid terminal outcome")
        check(!(a.status == "approved" && outcome != "approved")) { "approved terminal result cannot be downgraded; reverse or finalize it" }
        var next = a.copy(status = outcome, transactionId = f("terminal_transaction_id"), rrn = f("rrn"), authorizationCode = f("authorization_code"),
            cardLast4 = f("card_last4"), cardScheme = f("card_scheme"), responseMessage = f("response_message"))
        if (a.operation == "reversal" && outcome == "approved") {
            a.orderId?.let { oid -> fakeAttempts.values.firstOrNull { it.orderId == oid && it.operation == "purchase" && it.status == "approved" } }
                ?.let { fakeAttempts[it.attemptId] = it.copy(status = "reversed") }
            next = next.copy(status = "settled")
        }
        if (a.operation == "purchase" && a.splitLegId == null && outcome in setOf("declined", "cancelled", "failed")) {
            a.orderId?.let { fakeOrders[it]?.state = "payment_failed" }
        }
        if (a.splitLegId != null && outcome in setOf("declined", "cancelled", "failed")) {
            fakeSplits.values.forEach { s -> s.legs.replaceAll { if (it.id == a.splitLegId) it.copy(status = "failed") else it } }
        }
        fakeAttempts[id] = next
        return next
    }

    override suspend fun finalizePosCardTerminalPurchase(attemptId: String): PosTerminalAttempt {
        val a = fakeAttempts[attemptId] ?: error("card terminal attempt not found")
        if (a.status == "settled") return a
        check(a.status == "approved") { "terminal approval required before finalization" }
        val settled = if (a.splitLegId != null) {
            val sp = fakeSplits.values.first { s -> s.legs.any { it.id == a.splitLegId } }
            sp.legs.replaceAll { if (it.id == a.splitLegId) it.copy(status = "captured", externalReference = a.transactionId) else it }
            val v = sp.finalize()
            a.copy(status = "settled", invoiceId = v.finalInvoiceId)
        } else {
            val o = fakeOrders[a.orderId] ?: error("order not found")
            val invoice = checkoutPosCartWithTenders(o.cartId, listOf(PosTenderLine("card_terminal", o.total))).invoiceId
            o.state = "paid"; o.invoiceId = invoice
            a.copy(status = "settled", invoiceId = invoice)
        }
        fakeAttempts[attemptId] = settled
        return settled
    }

    override suspend fun beginPosCardTerminalReversal(purchaseAttemptId: String, requestId: String): PosTerminalAttempt {
        val p = fakeAttempts[purchaseAttemptId] ?: error("approved, unfinalized purchase attempt required for reversal")
        check(p.operation == "purchase" && p.status == "approved") { "approved, unfinalized purchase attempt required for reversal" }
        return newAttempt(requestId, "reversal", p.orderId, null, p.amount)
    }

    override suspend fun listPosCardTerminalRecovery(): List<PosTerminalRecoveryRow> =
        fakeAttempts.values.filter { it.status in setOf("initiated", "approved", "unknown") || it.finalizationError != null }.map {
            PosTerminalRecoveryRow(it.attemptId, it.operation, it.status, it.terminalLabel, it.orderId, it.amount, it.currency, it.externalRef,
                it.transactionId, it.cardLast4, it.responseMessage, it.finalizationError, java.time.Instant.now().toString())
        }

    // --- POS returns, cores, warranty and stock (in memory, same rules as the server)

    private val fakeInvoiceId = "00000000-0000-4000-8000-0000000000i1"
    private val fakeInvoiceLines = mutableListOf(
        PosInvoiceDetailLine("line-fake-pad", "item-fake-pad", "FAKE-PAD-001", "Front brake pad set", "uom-ea", 2.0, 45.0, 90.0, false, 2.0),
        PosInvoiceDetailLine("line-fake-core", "item-fake-pad", "FAKE-PAD-001", "Brake caliper core charge", "uom-ea", 1.0, 20.0, 20.0, true, 1.0),
    )
    private data class FakeReturnCase(val invoiceId: String, val resolution: String, val lines: List<PosReturnLineInput>, var posted: Boolean = false)
    private val fakeReturnCases = linkedMapOf<String, FakeReturnCase>()
    private val fakeCoreBack = mutableMapOf<String, Double>()
    private val fakeClaims = mutableListOf(
        PosWarrantyClaimRow(
            "wc-fake-1", "WAR-00001", "open", null, fakeInvoiceId, "SINV-00001", "item-fake-pad", "FAKE-PAD-001", "PAD-SN-0042",
            "Squeal after one week", null, null, "2026-09-08T09:00:00Z", null, null,
        ),
    )
    private val fakeRefundInvoice = mutableMapOf<String, String>()

    private fun fakeReturnable(line: PosInvoiceDetailLine): Double =
        if (line.isCoreCharge) (line.qty - (fakeCoreBack[line.id] ?: 0.0)).coerceAtLeast(0.0)
        else (line.qty - fakeReturnCases.values.filter { it.posted }.flatMap { it.lines }.filter { it.invoiceLineId == line.id }.sumOf { it.qty }).coerceAtLeast(0.0)

    override suspend fun getPosInvoiceDetail(invoiceId: String): PosInvoiceDetail {
        check(invoiceId == fakeInvoiceId) { "posted invoice not found" }
        return PosInvoiceDetail(
            fakeInvoiceId, "SINV-00001", "cust-fake-1", CurrencyCode.USD, 110.0, 110.0, "2026-09-07T06:00:00Z", null,
            fakeInvoiceLines.sortedBy { it.isCoreCharge }.map { it.copy(returnableQty = fakeReturnable(it)) },
        )
    }

    override suspend fun createPosReturnCase(
        invoiceId: String,
        resolution: String,
        reasonCode: String,
        lines: List<PosReturnLineInput>,
        notes: String?,
        replacementLines: List<PosReplacementLineInput>?,
        tillSessionId: String?,
    ): String {
        check(invoiceId == fakeInvoiceId) { "posted source invoice required" }
        check(reasonCode.isNotBlank()) { "return reason required" }
        check(lines.isNotEmpty()) { "at least one return line required" }
        check(resolution != "replacement" || !replacementLines.isNullOrEmpty()) { "replacement lines required" }
        lines.forEach { l ->
            val line = fakeInvoiceLines.firstOrNull { it.id == l.invoiceLineId && !it.isCoreCharge } ?: error("return line must be a non-core line from source invoice")
            check(l.qty > 0) { "return qty must be > 0" }
            check(l.qty <= fakeReturnable(line)) { "return qty ${l.qty} exceeds remaining returnable qty ${fakeReturnable(line)}" }
        }
        val id = "rc-${UUID.randomUUID().toString().take(8)}"
        fakeReturnCases[id] = FakeReturnCase(invoiceId, resolution, lines)
        return id
    }

    override suspend fun postPosReturnCase(returnCaseId: String) {
        val c = fakeReturnCases[returnCaseId] ?: error("draft return case required")
        check(!c.posted) { "draft return case required" }
        if (c.resolution == "warranty") {
            check(c.lines.size == 1) { "warranty submission supports one claimed item per case" }
            fakeClaims.add(0, PosWarrantyClaimRow("wc-${UUID.randomUUID().toString().take(8)}", "WAR-${fakeClaims.size + 1}", "open", null, c.invoiceId, "SINV-00001",
                "item-fake-pad", "FAKE-PAD-001", null, null, null, null, java.time.Instant.now().toString(), null, null))
        }
        c.posted = true
    }

    override suspend fun postPosCoreReturn(invoiceId: String, coreLineId: String, qty: Double, resolution: String, reasonCode: String, tillSessionId: String?, notes: String?) {
        val line = fakeInvoiceLines.firstOrNull { it.id == coreLineId && it.isCoreCharge } ?: error("core-charge invoice line required")
        check(qty > 0) { "core return qty must be > 0" }
        check(qty <= fakeReturnable(line)) { "core return qty exceeds remaining eligible core qty" }
        check(reasonCode.isNotBlank()) { "core return reason required" }
        fakeCoreBack[line.id] = (fakeCoreBack[line.id] ?: 0.0) + qty
    }

    override suspend fun openPosWarrantyClaim(invoiceId: String, invoiceLineId: String, serialId: String?, notes: String?): String {
        val line = fakeInvoiceLines.firstOrNull { it.id == invoiceLineId && !it.isCoreCharge } ?: error("non-core source invoice line required")
        val id = "wc-${UUID.randomUUID().toString().take(8)}"
        fakeClaims.add(0, PosWarrantyClaimRow(id, "WAR-${fakeClaims.size + 1}", "open", null, invoiceId, "SINV-00001", line.stockItemId, line.oemPartNumber,
            if (serialId != null) "PAD-SN-0042" else null, notes, null, null, java.time.Instant.now().toString(), null, null))
        return id
    }

    override suspend fun findPosWarrantySerial(serialNumber: String): List<PosWarrantySerialRow> =
        if (serialNumber.trim().equals("PAD-SN-0042", ignoreCase = true)) listOf(PosWarrantySerialRow("ser-fake-1", "PAD-SN-0042", "item-fake-pad", "FAKE-PAD-001", "sold")) else emptyList()

    override suspend fun listPosWarrantyClaims(query: String?, status: String?): List<PosWarrantyClaimRow> {
        val q = query?.trim()?.lowercase().orEmpty()
        return fakeClaims.filter { c ->
            (status == null || c.status == status) &&
                (q.isBlank() || listOf(c.documentNumber, c.invoiceNumber, c.oemPartNumber, c.serialNumber).any { it.orEmpty().lowercase().contains(q) })
        }
    }

    private fun updateClaim(claimId: String, f: (PosWarrantyClaimRow) -> PosWarrantyClaimRow) {
        val i = fakeClaims.indexOfFirst { it.id == claimId }
        check(i >= 0) { "warranty claim not found" }
        fakeClaims[i] = f(fakeClaims[i])
    }

    override suspend fun approvePosWarrantyClaim(claimId: String, resolution: String, creditLines: List<Pair<String, Double>>?, replacementLines: List<PosReplacementLineInput>?) {
        check(fakeClaims.firstOrNull { it.id == claimId }?.status == "open") { "open warranty claim required" }
        check(resolution in setOf("replacement", "credit_note", "return_only")) { "invalid warranty approval resolution" }
        check(resolution != "replacement" || !replacementLines.isNullOrEmpty()) { "replacement lines required" }
        updateClaim(claimId) {
            it.copy(status = "approved", resolution = resolution, creditNoteId = if (resolution == "credit_note") "cn-fake" else null, decidedAt = java.time.Instant.now().toString())
        }
    }

    override suspend fun rejectPosWarrantyClaim(claimId: String, reason: String) {
        check(fakeClaims.firstOrNull { it.id == claimId }?.status == "open") { "open warranty claim required" }
        check(reason.isNotBlank()) { "reject reason required" }
        updateClaim(claimId) { it.copy(status = "rejected", resolution = "reject_only", rejectReason = reason.trim(), decidedAt = java.time.Instant.now().toString()) }
    }

    override suspend fun closeWarrantyClaim(claimId: String) {
        check(fakeClaims.firstOrNull { it.id == claimId }?.status in setOf("approved", "rejected")) { "decided warranty claim required" }
        updateClaim(claimId) { it.copy(status = "closed", closedAt = java.time.Instant.now().toString()) }
    }

    override suspend fun listPosStockAvailability(stockItemId: String): List<PosStockAvailabilityRow> {
        val seed = stockItemId.sumOf { it.code }
        return listOf(
            PosStockAvailabilityRow("wh-main", "MAIN", "Harare main", (seed % 9 + 2).toDouble(), (seed % 2).toDouble(), 0.0, 0.0),
            PosStockAvailabilityRow("wh-byo", "BYO", "Bulawayo branch", (seed % 4).toDouble(), 0.0, 0.0, if (seed % 3 == 0) 2.0 else 0.0),
            PosStockAvailabilityRow("wh-mut", "MUT", "Mutare branch", 0.0, 0.0, 0.0, 0.0),
        ).map { it.copy(available = (it.onHand - it.reserved).coerceAtLeast(0.0)) }
    }

    override suspend fun beginPosCardTerminalRefund(invoiceId: String, terminalId: String, requestId: String): PosTerminalAttempt {
        fakeAttemptKeys[requestId]?.let { return fakeAttempts.getValue(it) }
        val purchase = fakeAttempts.values.firstOrNull { it.operation == "purchase" && it.status == "settled" && it.invoiceId == invoiceId }
            ?: error("invoice was not fully settled through a card terminal")
        check(fakeAttempts.values.none { it.operation == "refund" && fakeRefundInvoice[it.attemptId] == invoiceId && it.status in setOf("initiated", "approved", "unknown", "settled") }) {
            "an existing card terminal refund must be reconciled before another refund"
        }
        val a = newAttempt(requestId, "refund", purchase.orderId, null, purchase.amount)
        fakeRefundInvoice[a.attemptId] = invoiceId
        return a
    }

    override suspend fun finalizePosCardTerminalRefund(attemptId: String, notes: String?): PosTerminalAttempt {
        val a = fakeAttempts[attemptId] ?: error("card terminal attempt not found")
        if (a.status == "settled") return a
        check(a.operation == "refund" && a.status == "approved") { "approved card terminal refund required" }
        val settled = a.copy(status = "settled", invoiceId = fakeRefundInvoice[attemptId])
        fakeAttempts[attemptId] = settled
        return settled
    }

    // --- Payment letters (in memory; the demo manager signs)

    private var fakeProfile = BusinessProfileRow("Nissan GTR Auto", "Nissan GTR Auto", "nissangtrauto.co.zw", "Harare", "Zimbabwe", null, null, null, null, null)
    private var fakeSignature: ByteArray? = null
    private val fakeLetters = mutableListOf<PaymentLetterDocument>()
    private val fakeLetterSource = mutableMapOf<String, Pair<String, String>>()

    override suspend fun listPaymentLetters(sourceKind: String?, sourceId: String?): List<PaymentLetterRow> =
        fakeLetters.map { it.row }.filter { r -> fakeLetterSource[r.id]?.let { (k, id) -> (sourceKind == null || k == sourceKind) && (sourceId == null || id == sourceId) } ?: false }

    override suspend fun createPaymentLetter(sourceKind: String, sourceId: String, notes: String?): String {
        checkNotNull(fakeSignature) { "manager profile signature required before issuing a payment-resolution letter" }
        val attempt = fakeAttempts[sourceId]
        val id = "letter-${UUID.randomUUID().toString().take(8)}"
        val row = PaymentLetterRow(
            id, "PDL-${fakeLetters.size + 1}", sourceKind, if (attempt != null) "card_terminal" else sourceKind, attempt?.status ?: "unknown",
            attempt?.amount ?: 0.0, CurrencyCode.USD, null, null, "Demo manager", "Shop manager", java.time.Instant.now().toString(),
        )
        fakeLetters.add(
            0,
            PaymentLetterDocument(
                row,
                mapOf(
                    "external_reference" to attempt?.externalRef, "terminal_transaction_id" to attempt?.transactionId, "card_last4" to attempt?.cardLast4,
                    "card_scheme" to attempt?.cardScheme, "failure_detail" to (attempt?.finalizationError ?: attempt?.responseMessage),
                    "manager_employee_code" to "E001", "issue_notes" to notes,
                ),
                fakeProfile, fakeSignature, "demo",
            ),
        )
        fakeLetterSource[id] = sourceKind to sourceId
        return id
    }

    override suspend fun getPaymentLetter(letterId: String): PaymentLetterDocument =
        fakeLetters.firstOrNull { it.row.id == letterId }?.copy(business = fakeProfile) ?: error("payment-resolution letter not found")

    override suspend fun getMyManagerSignature() = ManagerSignatureRow("Demo manager", "E001", fakeSignature != null, null, fakeSignature)

    override suspend fun saveMyManagerSignature(png: ByteArray): ManagerSignatureRow {
        require(png.isNotEmpty()) { "valid PNG/JPEG signature and sha256 required" }
        fakeSignature = png
        return getMyManagerSignature()
    }

    override suspend fun getBusinessDocumentProfile() = fakeProfile

    override suspend fun setBusinessDocumentProfile(profile: BusinessProfileRow): BusinessProfileRow {
        check(profile.legalName.isNotBlank() && profile.tradingName.isNotBlank() && profile.domain.isNotBlank()) { "legal name, trading name and domain required" }
        fakeProfile = profile
        return fakeProfile
    }

    // --- POS fulfilment (in memory, same rules as the server)

    private val fakeFulfillment = mutableListOf<PosFulfillmentRow>()
    private val fakeBranches = mapOf("wh-main" to "Harare main", "wh-byo" to "Bulawayo branch", "wh-mut" to "Mutare branch")

    override suspend fun createPosFulfillmentRequest(
        kind: String,
        stockItemId: String,
        qty: Double,
        sourceWarehouseId: String?,
        destinationWarehouseId: String?,
        customerId: String?,
        cartId: String?,
        notes: String?,
        holdMinutes: Int,
    ): String {
        check(qty > 0) { "qty must be > 0" }
        check(kind != "branch_transfer" || (sourceWarehouseId != null && destinationWarehouseId != null && sourceWarehouseId != destinationWarehouseId)) {
            "branch transfer requires distinct source and destination warehouses"
        }
        check(kind !in setOf("alternate_pickup", "customer_collection") || sourceWarehouseId != null) { "source warehouse required for stock hold" }
        val id = "pfr-${UUID.randomUUID().toString().take(8)}"
        fakeFulfillment.add(
            0,
            PosFulfillmentRow(
                id, "PFR-${fakeFulfillment.size + 1}", kind, if (kind == "backorder") "requested" else "reserved", stockItemId, "FAKE-PAD-001", "Front brake pad set", qty,
                sourceWarehouseId, sourceWarehouseId?.let { fakeBranches[it] ?: it }, destinationWarehouseId, destinationWarehouseId?.let { fakeBranches[it] ?: it },
                customerId, cartId, null, if (kind == "backorder") null else java.time.Instant.now().plusSeconds(holdMinutes * 60L).toString(), null, null,
                java.time.Instant.now().toString(),
            ),
        )
        return id
    }

    override suspend fun listPosFulfillmentRequests(query: String?, status: String?): List<PosFulfillmentRow> {
        val q = query?.trim()?.lowercase().orEmpty()
        return fakeFulfillment.filter { (status == null || it.status == status) && (q.isBlank() || "${it.documentNumber} ${it.oemPartNumber}".lowercase().contains(q)) }
    }

    override suspend fun posFulfillmentStep(requestId: String, step: String, notes: String?) {
        val i = fakeFulfillment.indexOfFirst { it.id == requestId }
        check(i >= 0) { "fulfillment request not found" }
        val f = fakeFulfillment[i]
        val now = java.time.Instant.now().toString()
        fakeFulfillment[i] = when (step) {
            // The fake warehouse posts the transfer at once, which makes it ready.
            "approve" -> { check(f.kind == "branch_transfer" && f.status == "reserved") { "reserved branch-transfer request required" }; f.copy(status = "ready", readyAt = now) }
            "ready" -> { check(f.status in setOf("requested", "reserved") && f.kind != "branch_transfer") { "request cannot be marked ready" }; f.copy(status = "ready", readyAt = now) }
            "collect" -> {
                check(f.status == "ready") { "ready fulfillment request required" }
                check(f.invoiceId != null || f.kind == "branch_transfer") { "sale/invoice must be linked before customer collection" }
                f.copy(status = "collected", collectedAt = now)
            }
            "cancel" -> {
                check(f.status !in setOf("collected", "cancelled", "rejected")) { "active fulfillment request required" }
                f.copy(status = "cancelled")
            }
            else -> error("unknown fulfilment step $step")
        }
    }

    override suspend fun registerPosCardTerminalDeviceKey(terminalId: String, deviceId: String, publicKeySpkiBase64: String, keySha256: String) =
        "key-${keySha256.take(8)}"
}
