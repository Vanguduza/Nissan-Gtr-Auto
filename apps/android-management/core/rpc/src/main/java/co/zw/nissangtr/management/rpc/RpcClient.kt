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
    /**
     * Full-catalogue gateway (`catalog-live-r2`): the Supabase hierarchy plus R2 part shards and
     * signed diagram images. Fails closed with [CatalogLiveException] (never fixture data).
     */
    suspend fun catalogLive(action: String, params: Map<String, String> = emptyMap()): kotlinx.serialization.json.JsonObject =
        throw CatalogLiveException(503, null, "live catalogue not available on this client")

    /**
     * Published vehicle-master id (full catalogue) for an exact chassis + engine; null when the
     * vehicle is absent. The catalogue lists several build variants per chassis + engine with
     * near-identical fitment, so the first (by id, for a stable choice) keys the R2 fitment shard.
     */
    suspend fun resolveVehicleMasterId(chassisCode: String, engineCode: String): String? =
        listVehicleMaster()
            .filter {
                it.chassisCode.equals(chassisCode.trim(), ignoreCase = true) &&
                    it.engineCode.orEmpty().equals(engineCode.trim(), ignoreCase = true)
            }
            .map { it.id }
            .minOrNull()

    /** Every published vehicle of the full catalogue (vehicle selector + EPC variants). */
    suspend fun listVehicleMaster(): List<VehicleMasterEntry> = emptyList()

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

    /** Session status (`open` / `claimed` / `revoked` / `expired`) for the till showing the pairing. */
    suspend fun getPosScanSessionStatus(sessionId: String): String? = null

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

    /** Per-operator explicit shortcuts merged with algorithmic popular spares in the Home row. */
    suspend fun listPosPopularPins(): List<PosPopularPin> = emptyList()

    suspend fun upsertPosPopularPin(pin: PosPopularPin): String = pin.stableKey

    suspend fun deletePosPopularPin(kind: PosPopularItemKind, itemKey: String): Boolean = false

    /** Posted sales history search for Orders / Returns. */
    /** Stock id, base UOM, default price, saleable qty and image for OEM numbers (benchmark POS search). */
    suspend fun hydratePosParts(oemPartNumbers: List<String>): List<PosPartMeta> = emptyList()

    /** Owner decision D1: best sellers this operator removed from Popular Items. */
    suspend fun listPosHiddenBestsellers(): List<String> = emptyList()

    suspend fun hidePosBestseller(stockItemId: String): Boolean = false

    suspend fun unhidePosBestseller(stockItemId: String): Boolean = false

    /** Signed-in staff member's display name for the POS header. */
    suspend fun currentStaffDisplayName(): String? = null

    /** Parked POS sales, newest first. */
    suspend fun listPosParkedCarts(limit: Int = 50): List<PosParkedCart> = emptyList()

    /** Currency of a POS cart. */
    suspend fun posCartCurrency(cartId: String): CurrencyCode? = null

    /** Invoice number for a posted sale (receipt header). */
    suspend fun salesInvoiceDocumentNumber(invoiceId: String): String? = null

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

    /** Search POS-visible customers by name/business/contact or exact UUID (≥2 chars). */
    suspend fun searchCustomers(query: String): List<CustomerOption>

    /** Staff-safe customer create/update surface; finance/credit fields are deliberately excluded. */
    suspend fun createPosCustomer(
        kind: PosCustomerKind,
        displayName: String,
        businessName: String? = null,
        email: String? = null,
        phoneE164: String? = null,
        whatsappE164: String? = null,
    ): String

    suspend fun updatePosCustomer(
        customerId: String,
        kind: PosCustomerKind,
        displayName: String,
        businessName: String? = null,
        email: String? = null,
        phoneE164: String? = null,
        whatsappE164: String? = null,
    )

    suspend fun listPosCustomerGarage(customerId: String): List<CustomerGarageVehicle>

    suspend fun upsertPosCustomerGarageVehicle(
        customerId: String,
        vehicleId: String? = null,
        modelSlug: String,
        make: String,
        model: String,
        generation: String,
        chassisCode: String,
        engine: String,
        vin: String? = null,
        isPrimary: Boolean = false,
    ): String

    /** Changes the customer on an already-open POS cart without recreating the sale. */
    suspend fun setPosCartCustomer(cartId: String, customerId: String?)

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

    // --- POS till sessions (live `pos_operations_p0_p1`; see docs/plans/2026-10-03-pos-operations-backend-integration.md)

    /** Open or variance-pending till of this operator or [deviceId]; null when none. */
    suspend fun getMyOpenPosTillSession(deviceId: String?): PosTillSessionRow? = null

    suspend fun openPosTillSession(warehouseId: String, deviceId: String, openingFloat: Double, currency: CurrencyCode): String =
        throw UnsupportedOperationException("till sessions are not available")

    /** Cart → till, so the invoice's cash counts towards the drawer. */
    suspend fun attachPosCartTillSession(cartId: String, sessionId: String) {}

    suspend fun listPosApprovalReasons(action: String): List<PosApprovalReason> = emptyList()

    /** `kind` is `pos_till_cash_movement_kind`; anything but `cash_in` needs a manager session. */
    suspend fun recordPosTillCashMovement(sessionId: String, kind: String, amount: Double, reasonCode: String, notes: String?): String =
        throw UnsupportedOperationException("till sessions are not available")

    suspend fun submitPosTillDenominatedClose(
        sessionId: String,
        lines: List<PosDenominationLine>,
        varianceReasonCode: String?,
        notes: String?,
    ): PosTillCloseResult = throw UnsupportedOperationException("till sessions are not available")

    /** Manager session only. */
    suspend fun approvePosTillVariance(sessionId: String, reasonCode: String, notes: String?) {
        throw UnsupportedOperationException("till sessions are not available")
    }

    suspend fun listPosHandoverOperators(): List<PosHandoverOperatorRow> = emptyList()

    /** Manager session only. */
    suspend fun handoverPosTillSession(sessionId: String, newOperatorUserId: String, notes: String?) {
        throw UnsupportedOperationException("till sessions are not available")
    }

    suspend fun listPosTillSessions(limit: Int = 20): List<PosTillSessionRow> = emptyList()

    // --- POS governance (`*_governed`: configured reason always; manager when the policy says so)

    suspend fun applyPosCartDiscountGoverned(cartId: String, discountPercent: Double, reasonCode: String, notes: String?): String =
        applyPosCartDiscount(cartId, discountPercent, listOfNotNull(reasonCode, notes).joinToString(" · "))

    suspend fun applyPosLinePriceOverrideGoverned(lineId: String, unitPrice: Double, reasonCode: String, notes: String?): String =
        applyPosLinePriceOverride(lineId, unitPrice, listOfNotNull(reasonCode, notes).joinToString(" · "))

    suspend fun voidPosCartGoverned(cartId: String, reasonCode: String, notes: String?): String =
        voidPosCart(cartId, listOfNotNull(reasonCode, notes).joinToString(" · "))

    suspend fun postPosRefundGoverned(invoiceId: String, reasonCode: String, notes: String?): String =
        postPosRefund(invoiceId, listOfNotNull(reasonCode, notes).joinToString(" · "))

    /** `pos_action_requires_manager`; true (fail closed) when there is no policy backend. */
    suspend fun posActionRequiresManager(action: String, value: Double): Boolean = true

    suspend fun listPosApprovalPolicies(): List<PosApprovalPolicy> = emptyList()

    /** Admin only (server-enforced). */
    suspend fun setPosApprovalPolicy(action: String, thresholdValue: Double, alwaysRequireManager: Boolean, reasonRequired: Boolean) {
        throw UnsupportedOperationException("approval policies are not available")
    }

    // --- POS manager badges (`pos_badge_approve`, `get_my_pos_approver_status`)

    /** The signed-in user is a POS manager: approvals need no badge or password. */
    suspend fun myPosApproverStatus(): Boolean = false

    /**
     * Run one governed action approved by a scanned manager badge. Returns the outcome object
     * `{ok, manager_name, result | error}`; a refused badge is `ok=false`, not an exception.
     */
    suspend fun posBadgeApprove(badge: String, action: String, args: Map<String, Any?>, deviceId: String?): PosBadgeApproval =
        PosBadgeApproval(false, null, "manager badges need the live backend")

    // --- POS reserve-first checkout (Blueprint §10.6): reserve, then take money against the order

    suspend fun preparePosCommerceCheckout(
        cartId: String,
        checkoutRequestId: String,
        receiptEmail: String?,
        receiptWhatsappE164: String?,
    ): String = throw UnsupportedOperationException("reserve-first checkout needs the live backend")

    suspend fun posPaymentStatus(orderId: String): PosPaymentStatus = throw UnsupportedOperationException("reserve-first checkout needs the live backend")

    /** Cash, bank, store credit; returns the invoice id. Idempotent on [paymentRequestId]. */
    suspend fun settlePosCommerceTenders(orderId: String, paymentRequestId: String, tenders: List<PosTenderLine>): String =
        throw UnsupportedOperationException("reserve-first checkout needs the live backend")

    /** Null when the provider can be offered, else why not (a provider without keys answers 503). */
    suspend fun posProviderAvailability(provider: String): String? = "Not available"

    suspend fun startPosProviderPayment(orderId: String, provider: String, msisdn: String?, method: String?, returnUrl: String): PosProviderStart =
        throw UnsupportedOperationException("provider payments need the live backend")

    suspend fun cancelPosCommerceCheckout(orderId: String, reason: String) {
        throw UnsupportedOperationException("reserve-first checkout needs the live backend")
    }

    /** On account (server checks credit limit, hold and currency); returns the invoice id. */
    suspend fun checkoutPosCartOnAccount(cartId: String, receiptEmail: String?, receiptWhatsappE164: String?): String =
        throw UnsupportedOperationException("on-account checkout needs the live backend")

    suspend fun listPosPaymentRecovery(): List<PosRecoveryRow> = emptyList()

    /** Approver session only (manager, finance, or badge). */
    suspend fun repairPosPaidOrder(orderId: String, notes: String?): String =
        throw UnsupportedOperationException("payment recovery needs the live backend")

    suspend fun listPosPickupOrders(query: String?): List<PosPickupRow> = emptyList()

    suspend fun collectPosCommerceOrder(orderId: String, notes: String?) {
        throw UnsupportedOperationException("pickup needs the live backend")
    }

    // --- POS part payments (staged split, Blueprint §10.5 / §10.8): money taken part by part on the reserved order

    suspend fun findPosSplitPayment(orderId: String): PosSplitSession? = null

    suspend fun startPosSplitPayment(orderId: String): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    suspend fun getPosSplitPayment(sessionId: String): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    /** Cash and bank are received at once (bank needs a reference); store credit is held. Idempotent on [requestId]. */
    suspend fun addPosSplitPaymentLeg(sessionId: String, tender: String, amount: Double, requestId: String, externalReference: String?): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    /** The customer keeps only [items] (cart line id → qty); the server computes the total and posts. */
    suspend fun acceptPosSplitAffordableItems(sessionId: String, items: List<Pair<String, Double>>, notes: String?): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    suspend fun requestPosSplitCancellation(sessionId: String, reason: String, feePolicy: String): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    suspend fun retryPosSplitFinalization(sessionId: String): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    suspend fun listPosSplitPaymentRecovery(): List<PosSplitRecoveryRow> = emptyList()

    /** Manager or finance (or a badge grant). */
    suspend fun approvePosSplitRefund(refundId: String, feePolicy: String, customerFee: Double, notes: String?): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    suspend fun completePosSplitRefund(refundId: String, providerRef: String, notes: String?): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    suspend fun failPosSplitRefund(refundId: String, reason: String): PosSplitSession =
        throw UnsupportedOperationException("part payments need the live backend")

    // --- POS card terminals (ECR, adapter android_intent_v1): money is taken on the acquirer's terminal app,
    // its answer is signed by this paired device and recorded by the `card-terminal-result` function.

    suspend fun listPosCardTerminals(warehouseId: String?, deviceId: String?): List<PosCardTerminalRow> = emptyList()

    suspend fun beginPosCardTerminalPurchase(orderId: String, terminalId: String, requestId: String): PosTerminalAttempt =
        throw UnsupportedOperationException("card terminals need the live backend")

    /** A planned `card_terminal` part of a split payment. */
    suspend fun beginPosSplitCardTerminalLeg(legId: String, terminalId: String, requestId: String): PosTerminalAttempt =
        throw UnsupportedOperationException("card terminals need the live backend")

    suspend fun getPosCardTerminalAttempt(attemptId: String): PosTerminalAttempt =
        throw UnsupportedOperationException("card terminals need the live backend")

    /** Posts signed evidence (`gtr-card-terminal-evidence-v1`); [payloadJson] is the canonical JSON that was signed. */
    suspend fun submitCardTerminalEvidence(payloadJson: String, signatureBase64: String): PosTerminalAttempt =
        throw UnsupportedOperationException("card terminals need the live backend")

    /** Approved → posts the sale (or the split part). May answer status `recovery_required` with an error. */
    suspend fun finalizePosCardTerminalPurchase(attemptId: String): PosTerminalAttempt =
        throw UnsupportedOperationException("card terminals need the live backend")

    /** Undo an approved purchase that could not be posted (operator who took it, or a manager). */
    suspend fun beginPosCardTerminalReversal(purchaseAttemptId: String, requestId: String): PosTerminalAttempt =
        throw UnsupportedOperationException("card terminals need the live backend")

    suspend fun listPosCardTerminalRecovery(): List<PosTerminalRecoveryRow> = emptyList()

    /** Admin: pair this device with a terminal by registering its evidence public key. */
    suspend fun registerPosCardTerminalDeviceKey(terminalId: String, deviceId: String, publicKeySpkiBase64: String, keySha256: String): String =
        throw UnsupportedOperationException("card terminals need the live backend")

    /** Card refund of a whole sale paid in full on a card machine (manager or finance; badge `card_refund_begin`). */
    suspend fun beginPosCardTerminalRefund(invoiceId: String, terminalId: String, requestId: String): PosTerminalAttempt =
        throw UnsupportedOperationException("card terminals need the live backend")

    /** Approved refund on the machine → posts the finance refund (manager or finance; badge `card_refund_finish`). */
    suspend fun finalizePosCardTerminalRefund(attemptId: String, notes: String?): PosTerminalAttempt =
        throw UnsupportedOperationException("card terminals need the live backend")

    // --- POS returns, cores, warranty and stock by branch (Blueprint §10, phase 6). Sales staff prepare;
    // posting and warranty decisions need an approver (own sign-in, password for one call, or badge).

    suspend fun getPosInvoiceDetail(invoiceId: String): PosInvoiceDetail =
        throw UnsupportedOperationException("returns need the live backend")

    /** Draft return case; [resolution] credit_note | cash_refund | store_credit | replacement | warranty. */
    suspend fun createPosReturnCase(
        invoiceId: String,
        resolution: String,
        reasonCode: String,
        lines: List<PosReturnLineInput>,
        notes: String?,
        replacementLines: List<PosReplacementLineInput>?,
        tillSessionId: String?,
    ): String = throw UnsupportedOperationException("returns need the live backend")

    suspend fun postPosReturnCase(returnCaseId: String) {
        throw UnsupportedOperationException("returns need the live backend")
    }

    /** [resolution] cash_refund | account_credit | store_credit. */
    suspend fun postPosCoreReturn(invoiceId: String, coreLineId: String, qty: Double, resolution: String, reasonCode: String, tillSessionId: String?, notes: String?) {
        throw UnsupportedOperationException("returns need the live backend")
    }

    suspend fun openPosWarrantyClaim(invoiceId: String, invoiceLineId: String, serialId: String?, notes: String?): String =
        throw UnsupportedOperationException("warranty needs the live backend")

    suspend fun findPosWarrantySerial(serialNumber: String): List<PosWarrantySerialRow> = emptyList()

    suspend fun listPosWarrantyClaims(query: String?, status: String?): List<PosWarrantyClaimRow> = emptyList()

    /** [resolution] replacement | credit_note | return_only; credit lines `{stock_item_id, qty}` are valued at the sold price. */
    suspend fun approvePosWarrantyClaim(claimId: String, resolution: String, creditLines: List<Pair<String, Double>>?, replacementLines: List<PosReplacementLineInput>?) {
        throw UnsupportedOperationException("warranty needs the live backend")
    }

    suspend fun rejectPosWarrantyClaim(claimId: String, reason: String) {
        throw UnsupportedOperationException("warranty needs the live backend")
    }

    suspend fun closeWarrantyClaim(claimId: String) {
        throw UnsupportedOperationException("warranty needs the live backend")
    }

    suspend fun listPosStockAvailability(stockItemId: String): List<PosStockAvailabilityRow> = emptyList()

    // --- POS fulfilment (phase 7). Requests are in the part's stock unit; a hold with a cart becomes
    // ready with its invoice when that sale posts, a transfer when the warehouse posts it.

    suspend fun createPosFulfillmentRequest(
        kind: String,
        stockItemId: String,
        qty: Double,
        sourceWarehouseId: String?,
        destinationWarehouseId: String?,
        customerId: String?,
        cartId: String?,
        notes: String?,
        holdMinutes: Int,
    ): String = throw UnsupportedOperationException("fulfilment needs the live backend")

    suspend fun listPosFulfillmentRequests(query: String?, status: String?): List<PosFulfillmentRow> = emptyList()

    /** [step] approve (warehouse staff) | ready | collect | cancel. */
    suspend fun posFulfillmentStep(requestId: String, step: String, notes: String?) {
        throw UnsupportedOperationException("fulfilment needs the live backend")
    }
}

/**
 * Outcome of [RpcClient.posBadgeApprove]. [attemptId] is the card-machine attempt a badge started
 * (`card_refund_begin`), when the action returns one.
 */
data class PosBadgeApproval(val ok: Boolean, val managerName: String?, val error: String?, val attemptId: String? = null)
