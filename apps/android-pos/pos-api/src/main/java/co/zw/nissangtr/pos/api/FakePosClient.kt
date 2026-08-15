package co.zw.nissangtr.pos.api

/**
 * Stateful Fake — search / till-items / EPC / quote / pay / park for P1–P2 tests
 * without Supabase credentials.
 */
class FakePosClient : PosClient {

    private val catalog: List<TillItem> = listOf(
        TillItem(
            stockItemId = "stock-pad-r35",
            oemPartNumber = "40206-JF00A",
            description = "Pad kit, disc brake, front",
            uomId = "EA",
            unitPrice = 190.00,
            coreCharge = 22.00,
            currency = "USD",
            saleableQty = 4,
            binCode = "A-12",
            pncCode = "40206",
            categoryName = "Brakes",
            chassisCodes = listOf("R35"),
            engineCodes = listOf("VR38DETT"),
        ),
        TillItem(
            stockItemId = "stock-rotor-r35",
            oemPartNumber = "40206-JF01B",
            description = "Disc, front brake",
            uomId = "EA",
            unitPrice = 145.00,
            coreCharge = null,
            currency = "USD",
            saleableQty = 2,
            binCode = "A-14",
            pncCode = "40206",
            categoryName = "Brakes",
            chassisCodes = listOf("R35"),
            engineCodes = listOf("VR38DETT"),
        ),
        TillItem(
            stockItemId = "stock-pad-y62",
            oemPartNumber = "41060-1LA0A",
            description = "Pad kit, disc brake (Y62)",
            uomId = "EA",
            unitPrice = 98.00,
            coreCharge = null,
            currency = "USD",
            saleableQty = 6,
            binCode = "B-03",
            pncCode = "41060",
            categoryName = "Brakes",
            chassisCodes = listOf("Y62"),
            engineCodes = emptyList(),
        ),
        TillItem(
            stockItemId = "stock-filter",
            oemPartNumber = "16546-JF00A",
            description = "Air cleaner element",
            uomId = "EA",
            unitPrice = 32.00,
            coreCharge = null,
            currency = "USD",
            saleableQty = 12,
            binCode = "C-01",
            pncCode = "16546",
            categoryName = "Engine",
            chassisCodes = emptyList(),
            engineCodes = emptyList(),
        ),
        TillItem(
            stockItemId = "stock-oos",
            oemPartNumber = "99999-OOS0A",
            description = "OOS priced gasket",
            uomId = "EA",
            unitPrice = 15.00,
            coreCharge = null,
            currency = "USD",
            saleableQty = 0,
            binCode = null,
            pncCode = "99999",
            categoryName = "Engine",
            chassisCodes = listOf("R35"),
            engineCodes = listOf("VR38DETT"),
        ),
        TillItem(
            stockItemId = "stock-noprice",
            oemPartNumber = "11111-NOP0A",
            description = "Unpriced special",
            uomId = "EA",
            unitPrice = null,
            coreCharge = null,
            currency = "USD",
            saleableQty = 3,
            binCode = "D-01",
            pncCode = "11111",
            categoryName = "Body",
            chassisCodes = listOf("R35"),
            engineCodes = emptyList(),
        ),
        TillItem(
            stockItemId = "stock-super-old",
            oemPartNumber = "40000-OLD0A",
            description = "Superseded pad (old)",
            uomId = "EA",
            unitPrice = 80.00,
            coreCharge = null,
            currency = "USD",
            saleableQty = 1,
            binCode = "A-01",
            pncCode = "40000",
            categoryName = "Brakes",
            supersededBy = "40206-JF00A",
            chassisCodes = listOf("R35"),
            engineCodes = listOf("VR38DETT"),
        ),
    )

    private val customers: List<CustomerRef> = listOf(
        CustomerRef(
            id = "cust-walkin",
            displayName = "POS Walk-in",
            currency = "USD",
            creditHold = false,
        ),
        CustomerRef(
            id = "cust-trade",
            displayName = "Harare Fleet Co",
            currency = "USD",
            creditHold = false,
            email = "fleet@example.co.zw",
            phoneE164 = "+263771000001",
        ),
        CustomerRef(
            id = "cust-hold",
            displayName = "Credit Hold Customer",
            currency = "USD",
            creditHold = true,
            email = "hold@example.co.zw",
        ),
    )

    private var latch: VehicleLatch? = VehicleLatch(
        chassisCode = "R35",
        engineCode = "VR38DETT",
        modelVariant = "GT-R",
        vinPrefix = "JN1AR5EF",
        productionYear = 2012,
    )

    private var cartId: String = "cart-fake-1"
    private var customerId: String? = null
    private var lines: MutableList<TicketLine> = defaultTicketLines()
    private var quoteSeq = 1
    private var invoiceSeq = 1
    private val parked = mutableListOf<ParkedCartSnapshot>()
    private val quotations = mutableListOf<QuotationRef>()
    private val railIntents = mutableMapOf<String, LiveRailIntent>()
    private val replayedSales = mutableMapOf<String, String>()
    private var conflictNextReplay: String? = null

    /** When true, next live-rail settle fails (integration tests). */
    var failNextRailSettle: Boolean = false

    /** Next offline replay throws this conflict code (e.g. offline_price_conflict). */
    fun failNextReplayWith(code: String) {
        conflictNextReplay = code
    }

    /** Last checkout tender payload (applied only) — for assertions. */
    var lastCheckoutTenders: List<TenderRpcLine>? = null
        private set

    var lastReceiptContacts: CheckoutReceiptContacts? = null
        private set

    var lastManagerPin: String? = null
        private set

    override fun fakeTillState(): TillFakeState {
        val ticket = buildTicket()
        return TillFakeState(
            session = TillStaffSession(
                staffName = "T. Moyo",
                terminalId = "TILL-01",
                warehouseLabel = "WH2",
                warehouseId = "wh2-fake",
                cartId = cartId,
            ),
            latch = latch,
            tiles = catalog.filter {
                it.oemPartNumber in setOf(
                    "40206-JF00A",
                    "40206-JF01B",
                    "41060-1LA0A",
                    "16546-JF00A",
                )
            },
            ticket = ticket,
            online = onlineOverride,
            statusLabel = if (onlineOverride) {
                "Online · Fake · FITS filter"
            } else {
                "Offline · cash only"
            },
        )
    }

    fun setLatch(next: VehicleLatch?) {
        latch = next
    }

    fun currentLatch(): VehicleLatch? = latch

    fun currentCustomerId(): String? = customerId

    fun setOnline(online: Boolean) {
        // Exposed via mutating fakeTillState callers through TillSession.
        onlineOverride = online
    }

    private var onlineOverride: Boolean = true

    fun isOnline(): Boolean = onlineOverride

    override suspend fun searchCatalog(
        mode: CatalogSearchMode,
        query: String,
    ): CatalogSearchResponse {
        val q = OemNormalize.normalize(query)
        if (q.isEmpty()) {
            return CatalogSearchResponse(mode = mode.rpcValue, query = query, results = emptyList())
        }
        val results = when (mode) {
            CatalogSearchMode.PART -> catalog
                .filter {
                    OemNormalize.normalize(it.oemPartNumber).contains(q) ||
                        (it.pncCode?.let { p -> OemNormalize.normalize(p).contains(q) } == true)
                }
                .map {
                    CatalogHitDto(
                        type = "part",
                        oemPartNumber = it.oemPartNumber,
                        pncCode = it.pncCode,
                        categoryName = it.categoryName,
                        supersededBy = it.supersededBy,
                    )
                }
            CatalogSearchMode.VIN, CatalogSearchMode.MODEL -> listOf(
                CatalogHitDto(
                    type = "vehicle",
                    chassisCode = "R35",
                    engineCode = "VR38DETT",
                    modelVariant = "GT-R",
                    vinPrefix = "JN1AR5EF",
                    productionYear = 2012,
                ),
            )
            CatalogSearchMode.PNC -> {
                val pnc = q.takeWhile { it.isDigit() }.ifEmpty { q }
                listOf(
                    CatalogHitDto(
                        type = "pnc",
                        pncCode = pnc,
                        categoryName = "Brakes",
                    ),
                )
            }
        }
        return CatalogSearchResponse(mode = mode.rpcValue, query = query, results = results)
    }

    override suspend fun listTillItems(request: ListTillItemsRequest): List<TillItem> {
        var list = when (request.source) {
            TillItemsSource.OEMS -> {
                val order = request.oems.map { OemNormalize.normalize(it) }
                order.mapNotNull { oem ->
                    catalog.find { OemNormalize.normalize(it.oemPartNumber) == oem }
                }
            }
            TillItemsSource.SECTION -> {
                val pnc = request.pncCode?.let { OemNormalize.normalize(it) }
                catalog.filter {
                    pnc == null || OemNormalize.normalize(it.pncCode.orEmpty()) == pnc
                }
            }
            TillItemsSource.SHOP_STOCK -> catalog.filter {
                it.unitPrice != null && it.unitPrice > 0.0
            }
        }
        if (request.inStockOnly) {
            list = list.filter { it.saleableQty > 0 }
        }
        request.category?.let { cat ->
            list = list.filter { it.categoryName.equals(cat, ignoreCase = true) }
        }
        request.chassisCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { chassis ->
            list = list.filter { item ->
                item.chassisCodes.isEmpty() ||
                    item.chassisCodes.any { it.equals(chassis, ignoreCase = true) }
            }
        }
        return list
    }

    override suspend fun listCatalogMakers(): List<CatalogMaker> =
        listOf(CatalogMaker(id = "nissan", name = "Nissan"))

    override suspend fun listCatalogModels(makerId: String): List<CatalogModel> =
        listOf(CatalogModel(id = "gtr", name = "GT-R", makerId = makerId))

    override suspend fun listCatalogVariants(modelId: String): List<CatalogVariant> =
        listOf(
            CatalogVariant(
                id = "r35",
                name = "R35",
                modelId = modelId,
                chassisCode = "R35",
            ),
        )

    override suspend fun listCatalogSections(variantId: String): List<CatalogSection> =
        listOf(
            CatalogSection(id = "sec-40206", name = "Front brake", pncCode = "40206"),
        )

    override suspend fun createCart(warehouseId: String, customerId: String?): String {
        cartId = "cart-fake-${System.currentTimeMillis() % 100000}"
        this.customerId = customerId
        lines = mutableListOf()
        return cartId
    }

    override suspend fun addCartLine(
        cartId: String,
        item: TillItem,
        qty: Int,
        quoteOnly: Boolean,
    ): TicketSnapshot {
        val parentId = "line-${lines.size + 1}"
        lines.add(
            TicketLine(
                id = parentId,
                oemPartNumber = item.oemPartNumber,
                description = item.description,
                qty = qty,
                unitPrice = item.unitPrice ?: 0.0,
                currency = item.currency,
                isCoreCharge = false,
                isQuoteOnly = quoteOnly,
            ),
        )
        val core = item.coreCharge
        if (core != null && core > 0.0) {
            lines.add(
                TicketLine(
                    id = "$parentId-core",
                    oemPartNumber = item.oemPartNumber,
                    description = "Core charge",
                    qty = qty,
                    unitPrice = core,
                    currency = item.currency,
                    isCoreCharge = true,
                    parentLineId = parentId,
                    isQuoteOnly = quoteOnly,
                ),
            )
        }
        return buildTicket()
    }

    override suspend fun parkCart(cartId: String): ParkedCartRef {
        val ticket = buildTicket()
        val ref = ParkedCartRef(
            cartId = cartId,
            label = "Parked · ${ticket.itemCount} item(s)",
            currency = ticket.currency,
            subtotalCents = MoneyCents.majorToCents(ticket.subtotal),
        )
        parked.add(
            ParkedCartSnapshot(
                ref = ref,
                lines = lines.toList(),
                customerId = customerId,
            ),
        )
        lines = mutableListOf()
        this.cartId = createCart("wh2-fake", null)
        return ref
    }

    override suspend fun resumeCart(cartId: String): TicketSnapshot {
        val idx = parked.indexOfFirst { it.ref.cartId == cartId }
        require(idx >= 0) { "parked cart not found: $cartId" }
        val snap = parked.removeAt(idx)
        this.cartId = snap.ref.cartId
        this.customerId = snap.customerId
        this.lines = snap.lines.toMutableList()
        return buildTicket()
    }

    override suspend fun voidCart(cartId: String, managerPin: String?): TicketSnapshot {
        lastManagerPin = managerPin
        // Manager reauth stub — pin presence is enough for Fake.
        require(!managerPin.isNullOrBlank()) { "manager reauth required (${PosRpcNames.VOID_POS_CART})" }
        lines = mutableListOf()
        return buildTicket()
    }

    override suspend fun applyCartDiscount(
        cartId: String,
        percent: Int,
        managerPin: String?,
    ): TicketSnapshot {
        lastManagerPin = managerPin
        require(!managerPin.isNullOrBlank()) {
            "manager reauth required (${PosRpcNames.APPLY_POS_CART_DISCOUNT})"
        }
        require(percent in 0..100) { "discount percent 0..100" }
        lines = lines.map { line ->
            if (line.isCoreCharge) {
                line
            } else {
                val factor = (100 - percent) / 100.0
                line.copy(unitPrice = line.unitPrice * factor)
            }
        }.toMutableList()
        return buildTicket()
    }

    override suspend fun createQuotationAndPark(cartId: String): QuoteParkResult {
        val ref = QuotationRef(
            id = "qt-$quoteSeq",
            documentNumber = "QT-${1000 + quoteSeq}",
        )
        quoteSeq++
        quotations.add(ref)
        parkCart(cartId)
        val newId = this.cartId
        return QuoteParkResult(
            quotation = ref,
            ticket = buildTicket(),
            newCartId = newId,
        )
    }

    override suspend fun listCustomers(query: String): List<CustomerRef> {
        val q = query.trim()
        if (q.isEmpty()) return customers
        return customers.filter {
            it.displayName.contains(q, ignoreCase = true) ||
                it.email.orEmpty().contains(q, ignoreCase = true)
        }
    }

    override suspend fun bindCustomer(cartId: String, customerId: String?): TicketSnapshot {
        this.customerId = customerId
        return buildTicket()
    }

    override suspend fun createLiveRailIntent(
        mode: TenderMode,
        amountCents: Long,
        currency: String,
        externalRef: String,
        payerMsisdn: String?,
    ): LiveRailIntent {
        require(mode.isLiveRail) { "not a live rail: $mode" }
        require(onlineOverride) { "needs connection" }
        val id = "intent-${railIntents.size + 1}"
        val intent = LiveRailIntent(
            intentId = id,
            mode = mode.rpcValue,
            amountCents = amountCents,
            settled = false,
        )
        railIntents[id] = intent
        return intent
    }

    override suspend fun settleLiveRailIntent(intentId: String): LiveRailIntent {
        if (failNextRailSettle) {
            failNextRailSettle = false
            throw LiveRailException("rail settle failed: $intentId")
        }
        val cur = railIntents[intentId] ?: throw LiveRailException("unknown intent")
        val settled = cur.copy(settled = true)
        railIntents[intentId] = settled
        return settled
    }

    override suspend fun checkoutWithTenders(
        cartId: String,
        tenders: List<TenderRpcLine>,
        receipt: CheckoutReceiptContacts,
    ): CheckoutResult {
        require(tenders.isNotEmpty()) { "p_tenders required" }
        val ticket = buildTicket()
        val dueCents = MoneyCents.majorToCents(ticket.subtotal)
        val sumCents = tenders.sumOf { MoneyCents.majorStringToCents(it.amount) }
        require(sumCents == dueCents) {
            "tenders sum $sumCents must equal due $dueCents (applied only)"
        }
        lastCheckoutTenders = tenders
        lastReceiptContacts = receipt

        val cust = customers.find { it.id == customerId }
        val invoiceId = "inv-${invoiceSeq++}"
        if (cust?.creditHold == true) {
            // Credit-hold → on_hold; not paid; do not clear cart as settled sale.
            return CheckoutResult(
                invoiceId = invoiceId,
                status = "on_hold",
                paid = false,
            )
        }

        lines = mutableListOf()
        this.customerId = null
        this.cartId = createCart("wh2-fake", null)
        return CheckoutResult(
            invoiceId = invoiceId,
            status = "posted",
            paid = true,
        )
    }

    override fun listParkedCarts(): List<ParkedCartRef> = parked.map { it.ref }

    override fun listQuotations(): List<QuotationRef> = quotations.toList()

    override suspend fun convertQuotationToCart(quotationId: String): TicketSnapshot {
        val idx = quotations.indexOfFirst { it.id == quotationId }
        require(idx >= 0) { "quotation not found: $quotationId" }
        quotations.removeAt(idx)
        // Fresh cart with empty ticket — convert path opens QT- for resume.
        this.cartId = createCart("wh2-fake", null)
        return buildTicket()
    }

    override suspend fun pullOfflineSnapshot(warehouseId: String): OfflineSnapshotDto {
        return OfflineSnapshotDto(
            warehouseId = warehouseId,
            pulledAt = "2026-08-15T12:00:00Z",
            currency = "USD",
            items = catalog,
        )
    }

    override suspend fun replayOfflineSale(
        clientSaleId: String,
        payloadJson: String,
    ): OfflineReplayResult {
        conflictNextReplay?.let { code ->
            conflictNextReplay = null
            throw OfflineReplayConflict(code, "replay conflict")
        }
        val existing = replayedSales[clientSaleId]
        if (existing != null) {
            return OfflineReplayResult(invoiceId = existing, duplicate = true)
        }
        val invoiceId = "inv-off-${invoiceSeq++}"
        replayedSales[clientSaleId] = invoiceId
        return OfflineReplayResult(invoiceId = invoiceId, duplicate = false)
    }

    var lastRefundInvoiceId: String? = null
        private set
    var lastRefundReason: String? = null
        private set
    var openFloatPeriod: TillFloatPeriod? = null
        private set

    override suspend fun listChassisShortcuts(): List<ChassisShortcut> =
        listOf(
            ChassisShortcut("R35", label = "R35", engineCode = "VR38DETT", modelVariant = "GT-R"),
            ChassisShortcut("Y62", label = "Y62", modelVariant = "Patrol"),
            ChassisShortcut("D22", label = "D22", modelVariant = "Navara"),
            ChassisShortcut("T30", label = "T30", modelVariant = "X-Trail"),
        )

    override suspend fun openTillFloat(request: OpenTillFloatRequest): TillFloatPeriod {
        val req = request.validated()
        require(openFloatPeriod == null) { "open period already exists for ${req.accountCode}" }
        val period = TillFloatPeriod(
            id = "period-fake-${System.currentTimeMillis() % 100000}",
            accountCode = req.accountCode,
            currency = req.currency,
            status = "open",
            openingBalance = req.openingBalance,
        )
        openFloatPeriod = period
        return period
    }

    override suspend fun closeTillFloat(request: CloseTillFloatRequest): TillFloatPeriod {
        val req = request.validated()
        val open = openFloatPeriod
        require(open != null && open.id == req.periodId) { "open period not found: ${req.periodId}" }
        val closed = open.copy(status = "closed")
        openFloatPeriod = null
        return closed
    }

    override suspend fun postPosRefund(invoiceId: String, reason: String?): String {
        require(invoiceId.isNotBlank()) { "invoice id required" }
        lastRefundInvoiceId = invoiceId.trim()
        lastRefundReason = reason?.trim()
        // Server routes to quarantine — Fake acknowledges only.
        return "refund-fake-${invoiceSeq++}"
    }

    private fun buildTicket(): TicketSnapshot {
        val productCount = lines.count { !it.isCoreCharge }
        val subtotal = lines.sumOf { it.unitPrice * it.qty }
        return TicketSnapshot(
            lines = lines.toList(),
            itemCount = productCount,
            subtotal = subtotal,
            currency = lines.firstOrNull()?.currency ?: "USD",
        )
    }

    private fun defaultTicketLines(): MutableList<TicketLine> {
        val parentId = "line-pad-1"
        return mutableListOf(
            TicketLine(
                id = parentId,
                oemPartNumber = "40206-JF00A",
                description = "Pad kit, disc brake, front",
                qty = 1,
                unitPrice = 190.00,
                currency = "USD",
                isCoreCharge = false,
                isQuoteOnly = false,
            ),
            TicketLine(
                id = "line-pad-1-core",
                oemPartNumber = "40206-JF00A",
                description = "Core charge",
                qty = 1,
                unitPrice = 22.00,
                currency = "USD",
                isCoreCharge = true,
                parentLineId = parentId,
                isQuoteOnly = false,
            ),
        )
    }

    private data class ParkedCartSnapshot(
        val ref: ParkedCartRef,
        val lines: List<TicketLine>,
        val customerId: String?,
    )
}
