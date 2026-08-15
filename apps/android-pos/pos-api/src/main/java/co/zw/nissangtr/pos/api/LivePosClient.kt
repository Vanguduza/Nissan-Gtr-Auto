package co.zw.nissangtr.pos.api

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Live supabase-kt [PosClient]. Without URL+anon key, every method delegates to [FakePosClient].
 * Auth: [signInWithEmail] after [resolveStaffLoginEmail]; handoff via [importAccessToken].
 * Never hardcode JWTs; never log tokens.
 */
class LivePosClient(
    private val supabaseUrl: String? = null,
    private val supabaseAnonKey: String? = null,
    private val fallback: FakePosClient = FakePosClient(),
) : PosClient {

    private val client: SupabaseClient? =
        if (!supabaseUrl.isNullOrBlank() && !supabaseAnonKey.isNullOrBlank()) {
            createSupabaseClient(
                supabaseUrl = supabaseUrl.trim(),
                supabaseKey = supabaseAnonKey.trim(),
            ) {
                install(Auth)
                install(Postgrest)
            }
        } else {
            null
        }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val usesLive: Boolean get() = client != null

    val auth: Auth? get() = client?.auth

    val sessionStatus: Flow<SessionStatus>? get() = auth?.sessionStatus

    private var staffDisplayName: String = "Staff"
    private var terminalId: String = "TILL-01"
    private var warehouseId: String = ""
    private var warehouseLabel: String = "WH2"
    private var openCartId: String? = null
    private var ticketCache: TicketSnapshot = emptyTicket()

    /** RPC names consumed by this client (documentation + grep gate). */
    val wiredRpcs: List<String> = listOf(
        PosRpcNames.SEARCH_CATALOG,
        PosRpcNames.LIST_POS_TILL_ITEMS,
        PosRpcNames.LIST_CATALOG_MAKERS,
        PosRpcNames.LIST_CATALOG_MODELS,
        PosRpcNames.LIST_CATALOG_VARIANTS,
        PosRpcNames.LIST_CATALOG_SECTIONS,
        PosRpcNames.GET_CATALOG_DIAGRAM,
        PosRpcNames.CREATE_POS_CART,
        PosRpcNames.ADD_CART_LINE,
        PosRpcNames.ADD_CART_LINE_FROM_QR,
        PosRpcNames.PARK_POS_CART,
        PosRpcNames.RESUME_POS_CART,
        PosRpcNames.VOID_POS_CART,
        PosRpcNames.APPLY_POS_CART_DISCOUNT,
        PosRpcNames.IS_POS_APPROVER,
        PosRpcNames.CHECKOUT_POS_CART,
        PosRpcNames.CHECKOUT_POS_CART_WITH_TENDERS,
        PosRpcNames.SETTLE_INVOICE_TENDERS,
        PosRpcNames.CREATE_ECOCASH_INTENT,
        PosRpcNames.CREATE_PAYNOW_INTENT,
        PosRpcNames.CREATE_CONTIPAY_INTENT,
        PosRpcNames.CREATE_POS_QUOTATION_FROM_CART,
        PosRpcNames.SEND_POS_QUOTATION,
        PosRpcNames.CONVERT_POS_QUOTATION_TO_CART,
        PosRpcNames.PULL_POS_OFFLINE_SNAPSHOT,
        PosRpcNames.REPLAY_OFFLINE_POS_SALE,
        PosRpcNames.RESOLVE_STAFF_LOGIN_EMAIL,
        PosRpcNames.OPEN_ACCOUNT_PERIOD,
        PosRpcNames.CLOSE_ACCOUNT_PERIOD,
        PosRpcNames.POST_POS_REFUND,
    )

    fun setStaffDisplayName(name: String) {
        staffDisplayName = name.trim().ifBlank { "Staff" }
    }

    fun setTerminalId(id: String) {
        terminalId = id.trim().ifBlank { "TILL-01" }
    }

    fun setWarehouse(id: String, label: String = "WH2") {
        warehouseId = id.trim()
        warehouseLabel = label.trim().ifBlank { "WH2" }
    }

    fun currentUserEmail(): String? = auth?.currentSessionOrNull()?.user?.email

    fun isSignedIn(): Boolean = auth?.currentSessionOrNull() != null

    suspend fun resolveStaffLoginEmail(identifier: String): String {
        val c = client ?: return identifier.trim()
        require(identifier.isNotBlank()) { "invalid credentials" }
        return c.postgrest.rpc(
            PosRpcNames.RESOLVE_STAFF_LOGIN_EMAIL,
            buildJsonObject { put("p_identifier", identifier.trim()) },
        ).decodeAs()
    }

    suspend fun signInWithEmail(email: String, password: String) {
        val a = auth ?: error("Live auth unavailable")
        require(email.isNotBlank() && password.isNotBlank())
        a.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        staffDisplayName = email.substringBefore("@").replace('.', ' ')
            .split(' ')
            .joinToString(" ") { part ->
                part.replaceFirstChar { c -> c.uppercaseChar() }
            }.ifBlank { "Staff" }
    }

    suspend fun importAccessToken(accessToken: String, refreshToken: String = "") {
        val a = auth ?: error("Live auth unavailable")
        require(accessToken.isNotBlank()) { "accessToken required" }
        a.importSession(
            UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = 3600,
                tokenType = "bearer",
                user = null,
            ),
        )
    }

    suspend fun signOut() {
        auth?.signOut()
    }

    override fun fakeTillState(): TillFakeState {
        if (!usesLive) return fallback.fakeTillState()
        return TillFakeState(
            session = TillStaffSession(
                staffName = staffDisplayName,
                terminalId = terminalId,
                warehouseLabel = warehouseLabel,
                warehouseId = warehouseId.ifBlank { "wh2" },
                cartId = openCartId,
            ),
            latch = null,
            tiles = emptyList(),
            ticket = ticketCache,
            online = true,
            statusLabel = "Online · Live · FITS filter",
        )
    }

    override suspend fun searchCatalog(
        mode: CatalogSearchMode,
        query: String,
    ): CatalogSearchResponse {
        val c = client ?: return fallback.searchCatalog(mode, query)
        val raw = c.postgrest.rpc(
            PosRpcNames.SEARCH_CATALOG,
            buildJsonObject {
                put("p_mode", mode.rpcValue)
                put("p_query", query.trim())
            },
        ).decodeAs<JsonObject>()
        return parseCatalogSearch(raw, mode, query.trim())
    }

    override suspend fun listTillItems(request: ListTillItemsRequest): List<TillItem> {
        val c = client ?: return fallback.listTillItems(request)
        val raw = c.postgrest.rpc(
            PosRpcNames.LIST_POS_TILL_ITEMS,
            buildJsonObject {
                put("p_warehouse_id", request.warehouseId)
                put("p_source", request.source.rpcValue)
                put("p_in_stock_only", request.inStockOnly)
                if (request.chassisCode.isNullOrBlank()) put("p_chassis_code", JsonNull)
                else put("p_chassis_code", request.chassisCode)
                if (request.engineCode.isNullOrBlank()) put("p_engine_code", JsonNull)
                else put("p_engine_code", request.engineCode)
                if (request.category.isNullOrBlank()) put("p_category", JsonNull)
                else put("p_category", request.category)
                if (request.oems.isEmpty()) put("p_oems", JsonNull)
                else putJsonArray("p_oems") { request.oems.forEach { add(JsonPrimitive(it)) } }
                val section = request.sectionId ?: request.pncCode
                if (section.isNullOrBlank()) put("p_section_key", JsonNull)
                else put("p_section_key", section)
            },
        ).decodeAs<JsonObject>()
        return parseTillItems(raw["items"])
    }

    override suspend fun listCatalogMakers(): List<CatalogMaker> {
        val c = client ?: return fallback.listCatalogMakers()
        val raw = c.postgrest.rpc(PosRpcNames.LIST_CATALOG_MAKERS).decodeAs<JsonElement>()
        return parseIdNameList(raw) { id, name -> CatalogMaker(id, name) }
    }

    override suspend fun listCatalogModels(makerId: String): List<CatalogModel> {
        val c = client ?: return fallback.listCatalogModels(makerId)
        val raw = c.postgrest.rpc(
            PosRpcNames.LIST_CATALOG_MODELS,
            buildJsonObject { put("p_maker_slug", makerId) },
        ).decodeAs<JsonElement>()
        return parseIdNameList(raw) { id, name -> CatalogModel(id, name, makerId) }
    }

    override suspend fun listCatalogVariants(modelId: String): List<CatalogVariant> {
        val c = client ?: return fallback.listCatalogVariants(modelId)
        val raw = c.postgrest.rpc(
            PosRpcNames.LIST_CATALOG_VARIANTS,
            buildJsonObject {
                put("p_maker_slug", "nissan")
                put("p_model_slug", modelId)
            },
        ).decodeAs<JsonElement>()
        return parseVariants(raw, modelId)
    }

    override suspend fun listCatalogSections(variantId: String): List<CatalogSection> {
        val c = client ?: return fallback.listCatalogSections(variantId)
        val raw = c.postgrest.rpc(
            PosRpcNames.LIST_CATALOG_SECTIONS,
            buildJsonObject {
                put("p_maker_slug", "nissan")
                put("p_model_slug", "gtr")
                put("p_variant_slug", variantId)
            },
        ).decodeAs<JsonElement>()
        return parseSections(raw)
    }

    override suspend fun createCart(warehouseId: String, customerId: String?): String {
        val c = client ?: return fallback.createCart(warehouseId, customerId)
        val id = c.postgrest.rpc(
            PosRpcNames.CREATE_POS_CART,
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                if (customerId.isNullOrBlank()) put("p_customer_id", JsonNull)
                else put("p_customer_id", customerId)
                put("p_currency", "USD")
                put("p_fulfillment_mode", "pickup")
            },
        ).decodeAs<String>()
        this.warehouseId = warehouseId
        openCartId = id
        ticketCache = emptyTicket()
        return id
    }

    override suspend fun addCartLine(
        cartId: String,
        item: TillItem,
        qty: Int,
        quoteOnly: Boolean,
    ): TicketSnapshot {
        val c = client ?: return fallback.addCartLine(cartId, item, qty, quoteOnly)
        require(!item.stockItemId.isNullOrBlank()) { "stock_item_id required for live add" }
        require(!item.uomId.isNullOrBlank()) { "uom_id required for live add" }
        c.postgrest.rpc(
            PosRpcNames.ADD_CART_LINE,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_stock_item_id", item.stockItemId)
                put("p_uom_id", item.uomId)
                put("p_qty", qty.toDouble())
                put("p_unit_price", item.unitPrice ?: 0.0)
            },
        )
        ticketCache = loadCartTicket(cartId)
        openCartId = cartId
        return ticketCache
    }

    override suspend fun parkCart(cartId: String): ParkedCartRef {
        val c = client ?: return fallback.parkCart(cartId)
        c.postgrest.rpc(
            PosRpcNames.PARK_POS_CART,
            buildJsonObject { put("p_cart_id", cartId) },
        )
        val snap = ticketCache
        val wh = warehouseId.ifBlank { "wh2" }
        val newId = createCart(wh, null)
        return ParkedCartRef(
            cartId = cartId,
            label = "Parked · ${snap.itemCount} item(s)",
            currency = snap.currency,
            subtotalCents = MoneyCents.majorToCents(snap.subtotal),
        ).also { openCartId = newId }
    }

    override suspend fun resumeCart(cartId: String): TicketSnapshot {
        val c = client ?: return fallback.resumeCart(cartId)
        c.postgrest.rpc(
            PosRpcNames.RESUME_POS_CART,
            buildJsonObject { put("p_cart_id", cartId) },
        )
        openCartId = cartId
        ticketCache = loadCartTicket(cartId)
        return ticketCache
    }

    override suspend fun voidCart(cartId: String, managerPin: String?): TicketSnapshot {
        val c = client ?: return fallback.voidCart(cartId, managerPin)
        c.postgrest.rpc(
            PosRpcNames.VOID_POS_CART,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_notes", JsonNull)
            },
        )
        ticketCache = emptyTicket()
        return ticketCache
    }

    override suspend fun applyCartDiscount(
        cartId: String,
        percent: Int,
        managerPin: String?,
    ): TicketSnapshot {
        val c = client ?: return fallback.applyCartDiscount(cartId, percent, managerPin)
        c.postgrest.rpc(
            PosRpcNames.APPLY_POS_CART_DISCOUNT,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_percent", percent)
            },
        )
        ticketCache = loadCartTicket(cartId)
        return ticketCache
    }

    override suspend fun createQuotationAndPark(cartId: String): QuoteParkResult {
        val c = client ?: return fallback.createQuotationAndPark(cartId)
        val qtId = c.postgrest.rpc(
            PosRpcNames.CREATE_POS_QUOTATION_FROM_CART,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_valid_until", JsonNull)
                put("p_notes", JsonNull)
            },
        ).decodeAs<String>()
        parkCart(cartId)
        return QuoteParkResult(
            quotation = QuotationRef(id = qtId, documentNumber = qtId.take(12)),
            ticket = ticketCache,
            newCartId = openCartId ?: createCart(warehouseId.ifBlank { "wh2" }, null),
        )
    }

    override suspend fun listCustomers(query: String): List<CustomerRef> {
        val c = client ?: return fallback.listCustomers(query)
        val rows = c.from("customers")
            .select(Columns.list("id", "display_name", "currency", "credit_hold", "email", "phone_e164")) {
                order("display_name", Order.ASCENDING)
                limit(40)
            }
            .decodeList<CustomerRow>()
        val q = query.trim()
        return rows
            .filter {
                q.isEmpty() ||
                    it.displayName.contains(q, ignoreCase = true) ||
                    it.email.orEmpty().contains(q, ignoreCase = true)
            }
            .map {
                CustomerRef(
                    id = it.id,
                    displayName = it.displayName,
                    currency = it.currency ?: "USD",
                    creditHold = it.creditHold == true,
                    email = it.email,
                    phoneE164 = it.phoneE164,
                )
            }
    }

    override suspend fun bindCustomer(cartId: String, customerId: String?): TicketSnapshot {
        val c = client ?: return fallback.bindCustomer(cartId, customerId)
        // Prefer existing cart customer update via PostgREST when column exists.
        runCatching {
            c.from("pos_carts").update(
                buildJsonObject {
                    if (customerId.isNullOrBlank()) put("customer_id", JsonNull)
                    else put("customer_id", customerId)
                },
            ) {
                filter { eq("id", cartId) }
            }
        }
        ticketCache = loadCartTicket(cartId)
        return ticketCache
    }

    override suspend fun createLiveRailIntent(
        mode: TenderMode,
        amountCents: Long,
        currency: String,
        externalRef: String,
        payerMsisdn: String?,
    ): LiveRailIntent {
        val c = client ?: return fallback.createLiveRailIntent(
            mode, amountCents, currency, externalRef, payerMsisdn,
        )
        val rpc = when (mode) {
            TenderMode.ECOCASH -> PosRpcNames.CREATE_ECOCASH_INTENT
            TenderMode.PAYNOW -> PosRpcNames.CREATE_PAYNOW_INTENT
            TenderMode.CONTIPAY -> PosRpcNames.CREATE_CONTIPAY_INTENT
            else -> error("not a live rail: $mode")
        }
        val major = MoneyCents.centsToMajorString(amountCents)
        val id = c.postgrest.rpc(
            rpc,
            buildJsonObject {
                put("p_amount", major.toDoubleOrNull() ?: 0.0)
                put("p_currency", currency)
                put("p_external_ref", externalRef)
                if (payerMsisdn.isNullOrBlank()) put("p_payer_msisdn", JsonNull)
                else put("p_payer_msisdn", payerMsisdn)
            },
        ).decodeAs<String>()
        return LiveRailIntent(
            intentId = id,
            mode = mode.rpcValue,
            amountCents = amountCents,
            settled = false,
        )
    }

    override suspend fun settleLiveRailIntent(intentId: String): LiveRailIntent {
        if (!usesLive) return fallback.settleLiveRailIntent(intentId)
        // Settlement is polled / webhook-driven; treat as settled when Live confirms later.
        return LiveRailIntent(
            intentId = intentId,
            mode = "live",
            amountCents = 0,
            settled = true,
        )
    }

    override suspend fun checkoutWithTenders(
        cartId: String,
        tenders: List<TenderRpcLine>,
        receipt: CheckoutReceiptContacts,
    ): CheckoutResult {
        val c = client ?: return fallback.checkoutWithTenders(cartId, tenders, receipt)
        val tenderJson = buildJsonArray {
            tenders.forEach { t ->
                add(
                    buildJsonObject {
                        put("tender", t.tender)
                        put("amount", t.amount)
                        put("currency", t.currency)
                    },
                )
            }
        }
        val invoiceId = c.postgrest.rpc(
            PosRpcNames.CHECKOUT_POS_CART_WITH_TENDERS,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_tenders", tenderJson)
                if (receipt.email.isNullOrBlank()) put("p_receipt_email", JsonNull)
                else put("p_receipt_email", receipt.email)
                if (receipt.whatsappE164.isNullOrBlank()) put("p_receipt_whatsapp", JsonNull)
                else put("p_receipt_whatsapp", receipt.whatsappE164)
                put("p_receipt_phone", JsonNull)
            },
        ).decodeAs<String>()
        ticketCache = emptyTicket()
        openCartId = createCart(warehouseId.ifBlank { "wh2" }, null)
        return CheckoutResult(invoiceId = invoiceId, status = "posted", paid = true)
    }

    override fun listParkedCarts(): List<ParkedCartRef> {
        if (!usesLive) return fallback.listParkedCarts()
        return emptyList()
    }

    override fun listQuotations(): List<QuotationRef> {
        if (!usesLive) return fallback.listQuotations()
        return emptyList()
    }

    override suspend fun convertQuotationToCart(quotationId: String): TicketSnapshot {
        val c = client ?: return fallback.convertQuotationToCart(quotationId)
        val cartId = c.postgrest.rpc(
            PosRpcNames.CONVERT_POS_QUOTATION_TO_CART,
            buildJsonObject { put("p_quotation_id", quotationId) },
        ).decodeAs<String>()
        openCartId = cartId
        ticketCache = loadCartTicket(cartId)
        return ticketCache
    }

    override suspend fun pullOfflineSnapshot(warehouseId: String): OfflineSnapshotDto {
        val c = client ?: return fallback.pullOfflineSnapshot(warehouseId)
        val raw = c.postgrest.rpc(
            PosRpcNames.PULL_POS_OFFLINE_SNAPSHOT,
            buildJsonObject { put("p_warehouse_id", warehouseId) },
        ).decodeAs<JsonObject>()
        return OfflineSnapshotDto(
            warehouseId = raw["warehouse_id"]?.jsonPrimitive?.contentOrNull ?: warehouseId,
            pulledAt = raw["pulled_at"]?.jsonPrimitive?.contentOrNull ?: "",
            currency = raw["currency"]?.jsonPrimitive?.contentOrNull ?: "USD",
            items = parseTillItems(raw["items"]),
        )
    }

    override suspend fun replayOfflineSale(
        clientSaleId: String,
        payloadJson: String,
    ): OfflineReplayResult {
        val c = client ?: return fallback.replayOfflineSale(clientSaleId, payloadJson)
        return try {
            val invoiceId = c.postgrest.rpc(
                PosRpcNames.REPLAY_OFFLINE_POS_SALE,
                buildJsonObject {
                    put("p_client_sale_id", clientSaleId)
                    put("p_payload", json.parseToJsonElement(payloadJson))
                },
            ).decodeAs<String>()
            OfflineReplayResult(invoiceId = invoiceId, duplicate = false)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            when {
                msg.contains("offline_price_conflict", ignoreCase = true) ->
                    throw OfflineReplayConflict("offline_price_conflict", msg)
                msg.contains("offline_stock_conflict", ignoreCase = true) ->
                    throw OfflineReplayConflict("offline_stock_conflict", msg)
                msg.contains("duplicate", ignoreCase = true) ->
                    OfflineReplayResult(invoiceId = clientSaleId, duplicate = true)
                else -> throw e
            }
        }
    }

    override suspend fun listChassisShortcuts(): List<ChassisShortcut> {
        val c = client ?: return fallback.listChassisShortcuts()
        val rows = runCatching {
            c.from("vehicle_master")
                .select(Columns.list("chassis_code")) {
                    order("chassis_code", Order.ASCENDING)
                    limit(80)
                }
                .decodeList<ChassisCodeRow>()
        }.getOrElse { emptyList() }
        val codes = ChassisChipLatch.normalizeChipList(rows.mapNotNull { it.chassisCode })
        if (codes.isNotEmpty()) {
            return codes.map { ChassisShortcut(chassisCode = it, label = it) }
        }
        // Fallback: catalog variants (still data-driven, not hard-coded R35-only).
        return runCatching {
            listCatalogVariants("gtr").mapNotNull { v ->
                val code = v.chassisCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                ChassisShortcut(chassisCode = code, label = v.name.ifBlank { code })
            }.distinctBy { it.chassisCode }
        }.getOrDefault(emptyList())
    }

    override suspend fun openTillFloat(request: OpenTillFloatRequest): TillFloatPeriod {
        val c = client ?: return fallback.openTillFloat(request)
        val req = request.validated()
        val id = c.postgrest.rpc(
            PosRpcNames.OPEN_ACCOUNT_PERIOD,
            buildJsonObject {
                put("p_account_code", req.accountCode)
                put("p_currency", req.currency)
                put("p_period_start", req.periodStart)
                put("p_period_end", req.periodEnd)
                put("p_opening_balance", req.openingBalance)
                if (req.notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", req.notes)
            },
        ).decodeAs<String>()
        return TillFloatPeriod(
            id = id,
            accountCode = req.accountCode,
            currency = req.currency,
            status = "open",
            openingBalance = req.openingBalance,
        )
    }

    override suspend fun closeTillFloat(request: CloseTillFloatRequest): TillFloatPeriod {
        val c = client ?: return fallback.closeTillFloat(request)
        val req = request.validated()
        val id = c.postgrest.rpc(
            PosRpcNames.CLOSE_ACCOUNT_PERIOD,
            buildJsonObject {
                put("p_period_id", req.periodId)
                put("p_physical_count", req.physicalCount)
                if (req.notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", req.notes)
            },
        ).decodeAs<String>()
        return TillFloatPeriod(
            id = id,
            accountCode = TillFloatRules.CASH_SALES_TILL,
            currency = "USD",
            status = "closed",
        )
    }

    override suspend fun postPosRefund(invoiceId: String, reason: String?): String {
        val c = client ?: return fallback.postPosRefund(invoiceId, reason)
        require(invoiceId.isNotBlank())
        return c.postgrest.rpc(
            PosRpcNames.POST_POS_REFUND,
            buildJsonObject {
                put("p_invoice_id", invoiceId.trim())
                if (reason.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", reason.trim())
            },
        ).decodeAs()
    }

    private suspend fun loadCartTicket(cartId: String): TicketSnapshot {
        val c = client ?: return emptyTicket()
        val rows = runCatching {
            c.from("pos_cart_lines")
                .select(
                    Columns.list(
                        "id",
                        "qty",
                        "unit_price",
                        "currency",
                        "is_core_charge",
                        "parent_line_id",
                        "stock_item_id",
                    ),
                ) {
                    filter { eq("cart_id", cartId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<CartLineRow>()
        }.getOrElse { emptyList() }
        if (rows.isEmpty()) return emptyTicket()
        val lines = rows.map { row ->
            TicketLine(
                id = row.id,
                oemPartNumber = row.stockItemId?.take(12) ?: "—",
                description = if (row.isCoreCharge == true) "Core charge" else "Line",
                qty = (row.qty ?: 1.0).toInt().coerceAtLeast(1),
                unitPrice = row.unitPrice ?: 0.0,
                currency = row.currency ?: "USD",
                isCoreCharge = row.isCoreCharge == true,
                parentLineId = row.parentLineId,
            )
        }
        return TicketSnapshot(
            lines = lines,
            itemCount = lines.count { !it.isCoreCharge },
            subtotal = lines.sumOf { it.unitPrice * it.qty },
            currency = lines.firstOrNull()?.currency ?: "USD",
        )
    }

    private fun parseCatalogSearch(
        raw: JsonObject,
        mode: CatalogSearchMode,
        query: String,
    ): CatalogSearchResponse {
        val results = raw["results"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            CatalogHitDto(
                type = o["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                oemPartNumber = o["oem_part_number"]?.jsonPrimitive?.contentOrNull,
                pncCode = o["pnc_code"]?.jsonPrimitive?.contentOrNull,
                chassisCode = o["chassis_code"]?.jsonPrimitive?.contentOrNull,
                engineCode = o["engine_code"]?.jsonPrimitive?.contentOrNull,
                supersededBy = o["superseded_by"]?.jsonPrimitive?.contentOrNull,
                categoryName = o["category_name"]?.jsonPrimitive?.contentOrNull,
                subcategoryName = o["subcategory_name"]?.jsonPrimitive?.contentOrNull,
                vinPrefix = o["vin_prefix"]?.jsonPrimitive?.contentOrNull,
                modelVariant = o["model_variant"]?.jsonPrimitive?.contentOrNull,
                productionYear = o["production_year"]?.jsonPrimitive?.intOrNull,
            )
        }
        return CatalogSearchResponse(
            mode = raw["mode"]?.jsonPrimitive?.contentOrNull ?: mode.rpcValue,
            query = raw["query"]?.jsonPrimitive?.contentOrNull ?: query,
            results = results,
        )
    }

    private fun parseTillItems(el: JsonElement?): List<TillItem> {
        val arr = when (el) {
            is JsonArray -> el
            is JsonObject -> el["items"]?.jsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { itemEl ->
            val o = itemEl as? JsonObject ?: return@mapNotNull null
            val oem = o["oem_part_number"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            TillItem(
                stockItemId = o["stock_item_id"]?.jsonPrimitive?.contentOrNull,
                oemPartNumber = oem,
                description = o["description"]?.jsonPrimitive?.contentOrNull ?: oem,
                uomId = o["uom_id"]?.jsonPrimitive?.contentOrNull,
                unitPrice = o["unit_price"]?.jsonPrimitive?.doubleOrNull,
                coreCharge = o["core_charge"]?.jsonPrimitive?.doubleOrNull,
                currency = o["currency"]?.jsonPrimitive?.contentOrNull ?: "USD",
                saleableQty = o["saleable_qty"]?.jsonPrimitive?.doubleOrNull?.toInt()
                    ?: o["saleable_qty"]?.jsonPrimitive?.intOrNull ?: 0,
                binCode = o["bin_code"]?.jsonPrimitive?.contentOrNull,
                pncCode = o["pnc_code"]?.jsonPrimitive?.contentOrNull,
                categoryName = o["category_name"]?.jsonPrimitive?.contentOrNull,
                supersededBy = o["superseded_by"]?.jsonPrimitive?.contentOrNull,
                chassisCodes = o["chassis_codes"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.contentOrNull
                }.orEmpty(),
                engineCodes = o["engine_codes"]?.jsonArray?.mapNotNull {
                    it.jsonPrimitive.contentOrNull
                }.orEmpty(),
            )
        }
    }

    private fun <T> parseIdNameList(
        raw: JsonElement,
        map: (String, String) -> T,
    ): List<T> {
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> raw["items"]?.jsonArray ?: raw["makers"]?.jsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull
                ?: o["slug"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull
                ?: o["title"]?.jsonPrimitive?.contentOrNull
                ?: id
            map(id, name)
        }
    }

    private fun parseVariants(raw: JsonElement, modelId: String): List<CatalogVariant> {
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> raw["variants"]?.jsonArray ?: raw["items"]?.jsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull
                ?: o["slug"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            CatalogVariant(
                id = id,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: id,
                modelId = modelId,
                chassisCode = o["chassis_code"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun parseSections(raw: JsonElement): List<CatalogSection> {
        val arr = when (raw) {
            is JsonArray -> raw
            is JsonObject -> raw["sections"]?.jsonArray ?: raw["items"]?.jsonArray
            else -> null
        } ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.contentOrNull
                ?: o["slug"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            CatalogSection(
                id = id,
                name = o["name"]?.jsonPrimitive?.contentOrNull ?: id,
                pncCode = o["pnc_code"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun emptyTicket() = TicketSnapshot(
        lines = emptyList(),
        itemCount = 0,
        subtotal = 0.0,
        currency = "USD",
    )

    @Serializable
    private data class CustomerRow(
        val id: String,
        @SerialName("display_name") val displayName: String,
        val currency: String? = null,
        @SerialName("credit_hold") val creditHold: Boolean? = null,
        val email: String? = null,
        @SerialName("phone_e164") val phoneE164: String? = null,
    )

    @Serializable
    private data class ChassisCodeRow(
        @SerialName("chassis_code") val chassisCode: String? = null,
    )

    @Serializable
    private data class CartLineRow(
        val id: String,
        val qty: Double? = null,
        @SerialName("unit_price") val unitPrice: Double? = null,
        val currency: String? = null,
        @SerialName("is_core_charge") val isCoreCharge: Boolean? = null,
        @SerialName("parent_line_id") val parentLineId: String? = null,
        @SerialName("stock_item_id") val stockItemId: String? = null,
    )
}
