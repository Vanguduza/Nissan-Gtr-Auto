package co.zw.nissangtr.management.rpc

/**
 * Thin staff RPC boundary for management Compose screens.
 *
 * **Live:** [SupabaseRpcClient] via [RpcClientFactory] when `SUPABASE_URL` +
 * `SUPABASE_ANON_KEY` are set (override with `rpc.forceFake=true`).
 * **Fallback:** [FakeRpcClient].
 *
 * Covers HR clock, POS cart, warehouse receive/transfer/recon, logistics pick/DN,
 * staff live chat, Phase 8b blankets, Phase 16 bins/consignment/pick-path, and B2B credit.
 * Reads (DN/pick/chat/blankets/bins lists) use PostgREST + RLS — not mutation RPCs.
 *
 * GPS / QR: Bridge-First only (`bridges/android/`) — never HTML5 or WebView APIs.
 */
interface RpcClient {
    suspend fun clockAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        notes: String? = null,
    ): String

    // --- POS (typed stock_item / UOM + Bridge-First QR — no HTML5 QR) ---

    suspend fun createPosCart(
        warehouseId: String,
        currency: CurrencyCode = CurrencyCode.USD,
        fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
        customerId: String? = null,
    ): String

    suspend fun addCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String

    /**
     * Bridge-decoded inventory QR payload → cart line.
     * Payload must match `gtr://part/{OEM}?batch=…&valuation=FIFO|AVG`.
     * Standalone staff OR claimed companion — session never required for staff.
     */
    suspend fun addCartLineFromQr(
        cartId: String,
        qrPayload: String,
        qty: Double = 1.0,
    ): String

    /**
     * Checkout with optional receipt contacts (email / WhatsApp / phone).
     * Same RPC for standalone and paired flows. Returns invoice + bind hint.
     */
    suspend fun checkoutPosCart(
        cartId: String,
        receiptEmail: String? = null,
        receiptWhatsappE164: String? = null,
        receiptPhoneE164: String? = null,
    ): CheckoutPosResult

    /**
     * Split-bill checkout — [RpcNames.CHECKOUT_POS_CART_WITH_TENDERS].
     * Tenders must sum to open invoice balance in cart currency.
     */
    suspend fun checkoutPosCartWithTenders(
        cartId: String,
        tenders: List<PosTenderLine>,
        receiptEmail: String? = null,
        receiptWhatsappE164: String? = null,
        receiptPhoneE164: String? = null,
    ): CheckoutPosResult

    /**
     * EcoCash direct C2B intent (staff). Not ContiPay/Paynow.
     * Returns intent UUID; Edge ecocash-initiate pushes PIN when keys are set.
     */
    suspend fun createEcocashIntent(
        externalRef: String,
        payerMsisdn: String,
        amount: Double,
        currency: CurrencyCode = CurrencyCode.USD,
        payerMode: String = "pos_entered",
        customerId: String? = null,
        salesInvoiceId: String? = null,
    ): String

    /**
     * Resolve OEM (from parsed inventory QR) to stock_item + base UOM.
     * Used by warehouse receive / cycle-count after bridge scan — not inside the bridge.
     */
    suspend fun lookupStockItemByOem(oemPartNumber: String): StockItemRef

    /** 4-way catalog search for standalone POS add-to-cart. */
    suspend fun searchCatalog(
        mode: CatalogSearchMode,
        query: String,
    ): CatalogSearchResult

    /** Fitment-scoped spare search when the attendant selected a specific Nissan. */
    suspend fun searchCatalogForVehicle(
        vehicle: PosSaleVehicleSelection,
        query: String,
        limit: Int = 50,
    ): CatalogSearchResult = searchCatalog(CatalogSearchMode.PART, query)

    /** Persist or clear sale vehicle context on an open cart. */
    suspend fun setPosCartVehicle(
        cartId: String,
        vehicle: PosSaleVehicleSelection?,
    ): String = cartId

    /** Read vehicle snapshot from a live/parked cart (resume / quote conversion). */
    suspend fun getPosCartVehicle(cartId: String): PosSaleVehicleSelection? = null

    /**
     * Megazip hierarchy browse (online-only). Offline POS cache remains flat catalog_items.
     */
    suspend fun listCatalogMakers(): List<EpcMaker> = emptyList()
    suspend fun listCatalogModels(makerSlug: String): List<EpcModel> = emptyList()
    suspend fun listCatalogVariants(makerSlug: String, modelSlug: String): List<EpcVariant> =
        emptyList()
    suspend fun listCatalogSections(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
    ): List<EpcSection> = emptyList()
    suspend fun getCatalogDiagram(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
    ): EpcDiagramResponse = EpcDiagramResponse()
    suspend fun listCatalogDiagrams(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
    ): List<EpcDiagramSummary> = emptyList()
    suspend fun getCatalogDiagramBySlug(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
        diagramSlug: String,
    ): EpcDiagramResponse = getCatalogDiagram(makerSlug, modelSlug, variantSlug, sectionSlug)

    /** Open cart lines (poll refresh for companion scans). */
    suspend fun listPosCartLines(cartId: String): List<PosCartLineSummary>

    /**
     * Staff till qty stepper — updates [pos_cart_lines.qty] + [line_total]
     * (RLS [cart_lines_staff]). Prefer over inventing a parallel cart SoR.
     */
    suspend fun setPosCartLineQty(lineId: String, qty: Double, unitPrice: Double)

    /** Remove a cart line (core-charge children CASCADE on parent). */
    suspend fun deletePosCartLine(lineId: String)

    /** Whether cart already has a customer_id (for bind messaging). */
    suspend fun getPosCartCustomerId(cartId: String): String?

    /** Warehouses for POS till picker (PostgREST + RLS). */
    suspend fun listWarehouses(): List<WarehouseRef>

    /** Optional companion: owner creates pairing code for phone scanner. */
    suspend fun createPosScanSession(cartId: String): PosScanSessionCreated

    /** Optional companion: phone claims 6-digit code → session id. */
    suspend fun claimPosScanSession(pairingCode: String): String

    /** Close companion scanner rights. */
    suspend fun revokePosScanSession(sessionId: String): String

    /** Load cart_id for a claimed/open session (companion after claim). */
    suspend fun getPosScanSessionCartId(sessionId: String): String?

    /** Hold open cart ([RpcNames.PARK_POS_CART]). */
    suspend fun parkPosCart(cartId: String): String

    /** Resume parked cart ([RpcNames.RESUME_POS_CART]). */
    suspend fun resumePosCart(cartId: String): String

    /** True when signed-in user is Admin or shop manager (POS approval gate). */
    suspend fun isPosApprover(): Boolean

    /**
     * Apply percent discount to non-core open cart lines.
     * Caller must be Admin|shop-manager (second-user reauth on device).
     */
    suspend fun applyPosCartDiscount(
        cartId: String,
        discountPercent: Double,
        notes: String? = null,
    ): String

    /**
     * Override unit price on a single open non-core line.
     * Same Admin|shop-manager gate as discount.
     */
    suspend fun applyPosLinePriceOverride(
        lineId: String,
        unitPrice: Double,
        notes: String? = null,
    ): String

    /** Void (abandon) open/parked cart — Admin|shop-manager. */
    suspend fun voidPosCart(cartId: String, notes: String? = null): String

    /**
     * POS counter refund — posts through [RpcNames.POST_POS_REFUND] → finance
     * reversing JE. No parallel POS-only ledger path.
     */
    suspend fun postPosRefund(invoiceId: String, notes: String? = null): String

    /** Save open cart as issued quotation (no tender / no ledger). */
    suspend fun createPosQuotationFromCart(
        cartId: String,
        validUntil: String? = null,
        notes: String? = null,
    ): String

    /** Record send audit (print|email|sms|whatsapp). */
    suspend fun sendPosQuotation(
        quotationId: String,
        channel: String,
        contact: String? = null,
    ): String

    /** Convert issued/sent quote → new open cart; cannot double-convert. */
    suspend fun convertPosQuotationToCart(quotationId: String): String

    /** List quotations for till quote browser. */
    suspend fun listPosQuotations(
        status: String? = null,
        limit: Int = 50,
    ): List<PosQuotationSummary>

    /** Best-selling spares from posted invoices for the operator Home screen. */
    suspend fun listPosPopularSpares(
        days: Int = 90,
        limit: Int = 8,
    ): List<PopularPosSpare> = emptyList()

    /** Posted sales history search for Orders / Returns. */
    suspend fun listPosRecentInvoices(
        query: String? = null,
        limit: Int = 50,
    ): List<PosInvoiceSummary> = emptyList()

    /**
     * Pull retail catalog + warehouse qty for encrypted offline POS cache.
     * [RpcNames.PULL_POS_OFFLINE_SNAPSHOT].
     */
    suspend fun pullPosOfflineSnapshot(warehouseId: String): OfflinePosSnapshot

    /**
     * Idempotent offline cash-sale replay.
     * Duplicate [clientSaleId] returns the original invoice id.
     */
    suspend fun replayOfflinePosSale(
        clientSaleId: String,
        payload: OfflineSaleReplayPayload,
    ): String

    /**
     * Pre-auth: emp#|email|phone → GoTrue email for staff password sign-in.
     * Non-enumerating failures. Not for customer storefront.
     */
    suspend fun resolveStaffLoginEmail(identifier: String): String

    /** True when identifier is temporarily locked out (≥5 fails / 15 min). */
    suspend fun staffLoginIsLocked(identifier: String): Boolean

    /** Record password attempt outcome (clears failures on success). */
    suspend fun recordStaffLoginAttempt(identifier: String, success: Boolean)

    /** Optional DB landing override: "pos" | "hub" | null. */
    suspend fun myDefaultLanding(): String?

    /**
     * Saleable qty summed across warehouses for OEM (PostgREST stock_levels).
     * Null when OEM not in stock_items.
     */
    suspend fun lookupSaleableQtyByOem(oemPartNumber: String): Double?

    // --- Warehouse ---

    suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String?,
        lines: List<ReceiptLineInput>,
    ): String

    suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String?,
        lines: List<TransferLineInput>,
    ): String

    suspend fun approveStockTransfer(entryId: String): String

    suspend fun rejectStockTransfer(entryId: String): String

    suspend fun createStockReconciliationDraft(
        warehouseId: String,
        scope: ReconciliationScope,
        currency: CurrencyCode = CurrencyCode.USD,
        itemIds: List<String>? = null,
        notes: String? = null,
        exchangeRate: Double? = null,
    ): String

    /** Returns count of upserted lines. */
    suspend fun upsertStockReconciliationLines(
        reconciliationId: String,
        lines: List<ReconciliationLineInput>,
    ): Int

    suspend fun submitStockReconciliation(reconciliationId: String): String

    suspend fun approveStockReconciliation(reconciliationId: String): String

    suspend fun cancelStockReconciliation(
        reconciliationId: String,
        notes: String? = null,
    ): String

    // --- Logistics ---

    /** Live: SELECT delivery_notes via PostgREST + RLS. */
    suspend fun listDeliveryNotes(): List<DeliveryNoteSummary>

    /** Live: SELECT pick_lists via PostgREST + RLS. */
    suspend fun listPickLists(): List<PickListSummary>

    suspend fun createPickList(salesInvoiceId: String, linesJson: String? = null): String

    suspend fun confirmPickLines(pickListId: String, lines: List<ConfirmPickLineInput>): String

    suspend fun createDeliveryNote(
        salesInvoiceId: String,
        lines: List<DnLineInput>,
        pickListId: String? = null,
    ): String

    suspend fun submitDeliveryNote(deliveryNoteId: String): String

    suspend fun cancelDeliveryNote(deliveryNoteId: String): String

    /** Staff/dispatcher: create a delivery job from a submitted DN. */
    suspend fun createDeliveryJob(
        deliveryNoteId: String,
        assigneeUserId: String? = null,
        etaAt: String? = null,
        notes: String? = null,
    ): String

    /**
     * Set pickup/dropoff coords so suggest ranking + ETA work
     * ([RpcNames.SET_DELIVERY_JOB_GEO]). Lat/lng pairs must both be set or both null
     * (null pair clears that endpoint). Returns job id.
     */
    suspend fun setDeliveryJobCoords(
        deliveryJobId: String,
        pickupLat: Double?,
        pickupLng: Double?,
        dropoffLat: Double?,
        dropoffLng: Double?,
    ): String

    /**
     * Staff/dispatcher: pending → dispatched | completed | failed.
     * On dispatched, [UpdateDeliveryJobStatusResult.trackToken] holds the share
     * plaintext (single mint). Remint only via [mintDeliveryTrackToken] to rotate.
     */
    suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): UpdateDeliveryJobStatusResult

    /**
     * GPS trail ingest — **apps/android-delivery is the sole producer**.
     * Management dispatch must not call this from UI (view-only via
     * [getDeliveryTrackPoint]). Kept for Fake/Live parity only.
     * Never from browser / WebView geolocation.
     */
    suspend fun ingestDeliveryLocation(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String? = null,
        accuracyM: Double? = null,
    ): String

    /** Nearest / capacity / shift suggestions for a pending job. */
    suspend fun suggestDeliveryAssignees(
        deliveryJobId: String,
        limit: Int = 5,
    ): List<DeliveryAssigneeSuggestion>

    /**
     * Assign driver to job. [override] = true bypasses eligibility
     * (manual override when suggest rules fail).
     */
    suspend fun assignDeliveryJob(
        deliveryJobId: String,
        assigneeUserId: String,
        override: Boolean = false,
    ): String

    /** Nearest-neighbor stop order for driver’s open jobs (persists route_sequence). */
    suspend fun optimizeDriverStops(driverUserId: String): List<OptimizedDriverStop>

    /** Staff live last-point + ETA for an active dispatched job (not a GPS producer). */
    suspend fun getDeliveryTrackPoint(deliveryJobId: String): DeliveryTrackPoint?

    /**
     * Intentional remint / rotate only. Revokes prior active tokens (including
     * the SMS/share token from dispatch). Prefer [updateDeliveryJobStatus]
     * `trackToken` after Mark dispatched.
     */
    suspend fun mintDeliveryTrackToken(
        deliveryJobId: String,
        ttl: String? = null,
    ): String

    /**
     * POD OTP plaintext (6 digits). Dispatcher may read to customer;
     * hash-only in DB. Job must be dispatched.
     */
    suspend fun generateDeliveryPodOtp(
        deliveryJobId: String,
        ttl: String? = null,
    ): String

    /** Open panic rows (`acknowledged_at` IS NULL). Poll — Realtime not wired yet. */
    suspend fun listOpenPanicEvents(): List<PanicEventSummary>

    /** Mark panic handled (sets acknowledged_at / acknowledged_by). */
    suspend fun acknowledgePanicEvent(panicEventId: String): String

    // --- Live chat (staff inbox) ---

    /** GoTrue user id, or null when Fake / signed out. */
    fun currentUserId(): String?

    /** Own rows from `staff_roles` (RLS). Used for chat nav gate. */
    suspend fun listMyStaffRoles(): List<String>

    /**
     * Organogram `hr_roles.module_access` module ids for the signed-in employee.
     * Empty → hub falls back to staff_roles-only gating (admins bypass in UI).
     */
    suspend fun listMyModuleAccess(): List<String>

    /** Live: SELECT chat_threads via PostgREST + RLS (open / mine / closed). */
    suspend fun listStaffChatThreads(filter: StaffChatFilter): List<ChatThreadSummary>

    /** Live: SELECT chat_messages for thread, oldest first. */
    suspend fun listChatMessages(threadId: String): List<ChatMessageSummary>

    suspend fun claimChatThread(threadId: String)

    suspend fun closeChatThread(threadId: String)

    suspend fun markChatThreadRead(threadId: String)

    /** Returns new message id. */
    suspend fun postChatMessage(threadId: String, body: String): String

    /** Unread across inbox, or for one thread when [threadId] set. */
    suspend fun chatUnreadCount(threadId: String? = null): Int

    // --- Named customers (PostgREST + RLS — finance/POS/credit pattern) ---

    /** Search by display_name ilike or exact UUID (≥2 chars). */
    suspend fun searchCustomers(query: String): List<CustomerOption>

    // --- Phase 8b blankets (procurement) ---

    suspend fun listSuppliers(): List<SupplierRef>

    /** Live: SELECT purchase_orders WHERE is_blanket + lines. */
    suspend fun listBlanketPurchaseOrders(): List<BlanketSummary>

    suspend fun createBlanketPurchaseOrder(
        supplierId: String,
        warehouseId: String,
        currency: CurrencyCode,
        exchangeRate: Double,
        blanketMaxValue: Double,
        lines: List<BlanketLineInput>,
        notes: String? = null,
        expectedDate: String? = null,
    ): String

    /** Submit draft blanket (or any draft PO) via [RpcNames.SUBMIT_PURCHASE_ORDER]. */
    suspend fun submitPurchaseOrder(purchaseOrderId: String): String

    suspend fun createBlanketRelease(
        blanketPurchaseOrderId: String,
        lines: List<BlanketReleaseLineInput>,
        notes: String? = null,
    ): String

    // --- Phase 16 bins / pick-path ---

    suspend fun listWarehouseBins(warehouseId: String): List<WarehouseBinSummary>

    suspend fun createWarehouseBin(
        warehouseId: String,
        code: String,
        name: String,
        pickPathSeq: Int = 100,
        aisle: String? = null,
        rack: String? = null,
        shelf: String? = null,
    ): String

    suspend fun updateWarehouseBin(
        binId: String,
        name: String? = null,
        pickPathSeq: Int? = null,
        aisle: String? = null,
        rack: String? = null,
        shelf: String? = null,
        isActive: Boolean? = null,
    ): String

    suspend fun deactivateWarehouseBin(binId: String): String

    suspend fun setStockLevelBin(
        stockItemId: String,
        warehouseId: String,
        binId: String?,
    ): String

    suspend fun getPickPathHints(
        warehouseId: String,
        stockItemIds: List<String>? = null,
    ): List<PickPathHint>

    // --- Phase 16 consignment ---

    suspend fun listConsignmentEntries(): List<ConsignmentEntrySummary>

    suspend fun createConsignmentEntryDraft(
        kind: ConsignmentKind,
        purpose: ConsignmentPurpose,
        warehouseId: String,
        supplierId: String? = null,
        customerId: String? = null,
        currency: CurrencyCode = CurrencyCode.USD,
        exchangeRate: Double = 1.0,
        notes: String? = null,
    ): String

    suspend fun addConsignmentEntryLine(
        entryId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
        unitCost: Double = 0.0,
        unitPrice: Double = 0.0,
        currency: CurrencyCode? = null,
    ): String

    suspend fun submitConsignmentEntry(entryId: String): String

    suspend fun cancelConsignmentEntry(entryId: String): String

    // --- B2B credit ---

    /** Load credit fields for a customer (PostgREST). */
    suspend fun loadCustomerCredit(customerId: String): CustomerCreditSnapshot?

    /**
     * Staff DEFINER mutator — admin|sales|finance.
     * Pass null to leave limit or hold unchanged; at least one must be set.
     */
    suspend fun setCustomerCredit(
        customerId: String,
        creditLimit: Double? = null,
        creditHold: Boolean? = null,
    ): CustomerCreditSnapshot

    // --- Company fleet (admin|warehouse|dispatcher) ---

    suspend fun listFleetVehicles(status: FleetVehicleStatus? = null): List<FleetVehicleSummary>

    suspend fun upsertFleetVehicle(
        plate: String,
        label: String? = null,
        status: FleetVehicleStatus = FleetVehicleStatus.ACTIVE,
        assignedDriverUserId: String? = null,
        notes: String? = null,
        id: String? = null,
    ): String

    suspend fun setFleetVehicleStatus(id: String, status: FleetVehicleStatus): String

    // --- HR onboarding (admin|hr — banking/health via RLS) ---

    suspend fun listHrOnboardingDrafts(): List<HrOnboardingDraft>

    suspend fun listHrGrades(): List<HrGradeOption>

    suspend fun listHrRoles(): List<HrRoleOption>

    suspend fun saveHrOnboardingStage(
        draftId: String? = null,
        stage: HrOnboardingStage,
        payload: Map<String, String?>,
        bankingJson: Map<String, String?>? = null,
        healthJson: Map<String, String?>? = null,
        employeeId: String? = null,
    ): String

    /** Returns employee_id, employee_code, user_id (nullable), never temp password for UI. */
    suspend fun completeHrOnboarding(draftId: String): HrOnboardingCompleteResult

    /**
     * Edge [RpcNames.HR_ONBOARDING_CREATE_AUTH_FN] when complete left user_id null.
     * Never returns the temp password.
     */
    suspend fun createHrOnboardingAuthUser(employeeId: String): HrOnboardingAuthResult
}
