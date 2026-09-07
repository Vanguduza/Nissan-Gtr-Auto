package co.zw.nissangtr.management.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Live supabase-kt [RpcClient] for HR, POS, warehouse, and pick/DN logistics RPCs.
 *
 * Uses anon key + Auth session (never hardcode JWTs). List reads via PostgREST + RLS.
 * Session persistence: auth-kt default Android session manager (Settings / SharedPreferences).
 */
class SupabaseRpcClient(
    val client: SupabaseClient,
) : RpcClient {

    /** GoTrue Auth plugin — [signInWithEmail] (preferred) or [importAccessToken] fallback. */
    val auth: Auth get() = client.auth

    /** Hot flow of GoTrue session state (persisted across process restarts). */
    val sessionStatus: Flow<SessionStatus> get() = auth.sessionStatus

    /**
     * Staff email/password sign-in through the hardened Auth Edge. Supabase Auth
     * still validates credentials and owns the resulting session.
     */
    suspend fun signInWithEmail(email: String, password: String) {
        require(email.isNotBlank()) { "email required" }
        require(password.isNotBlank()) { "password required" }
        val session = AuthEdgeClient.login(client, email.trim(), password)
        importAccessToken(session.accessToken, session.refreshToken, session.expiresIn)
    }

    suspend fun requestPasswordResetForEmail(email: String) {
        require(email.isNotBlank()) { "email required" }
        AuthEdgeClient.requestPasswordReset(client, email.trim())
    }

    suspend fun completePasswordResetForEmail(email: String, code: String, newPassword: String) {
        require(code.length >= 6) { "recovery code required" }
        require(newPassword.length >= 8) { "new password must be at least 8 characters" }
        val session = AuthEdgeClient.verifyPasswordReset(client, email.trim(), code, newPassword)
        importAccessToken(session.accessToken, session.refreshToken, session.expiresIn)
    }

    /** Clears the persisted GoTrue session. */
    suspend fun signOut() {
        auth.signOut()
    }

    fun currentUserEmail(): String? =
        auth.currentSessionOrNull()?.user?.email

    override fun currentUserId(): String? =
        auth.currentSessionOrNull()?.user?.id

    fun isSignedIn(): Boolean =
        auth.currentSessionOrNull() != null

    /**
     * Fallback: import an existing access token when a custom auth path cannot use
     * [signInWithEmail]. Prefer real sign-in — do not embed JWTs in source.
     */
    suspend fun importAccessToken(
        accessToken: String,
        refreshToken: String = "",
        expiresIn: Long = 3600,
    ) {
        require(accessToken.isNotBlank()) { "accessToken required — do not hardcode JWTs in BuildConfig" }
        auth.importSession(
            UserSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = expiresIn,
                tokenType = "bearer",
                user = null,
            ),
        )
    }

    override suspend fun clockAttendance(
        employeeId: String,
        eventType: AttendanceEventType,
        notes: String?,
    ): String {
        require(employeeId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CLOCK_ATTENDANCE,
            buildJsonObject {
                put("p_employee_id", employeeId)
                put("p_event_type", eventType.rpcValue)
                put("p_occurred_at", JsonNull)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun createPosCart(
        warehouseId: String,
        currency: CurrencyCode,
        fulfillmentMode: FulfillmentMode,
        customerId: String?,
    ): String {
        require(warehouseId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CREATE_POS_CART,
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                if (customerId.isNullOrBlank()) put("p_customer_id", JsonNull)
                else put("p_customer_id", customerId)
                put("p_currency", currency.rpcValue)
                put("p_fulfillment_mode", fulfillmentMode.rpcValue)
            },
        ).decodeAs<String>()
    }

    override suspend fun addCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String {
        require(cartId.isNotBlank() && stockItemId.isNotBlank() && uomId.isNotBlank())
        require(qty > 0)
        return client.postgrest.rpc(
            RpcNames.ADD_CART_LINE,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_stock_item_id", stockItemId)
                put("p_uom_id", uomId)
                put("p_qty", qty)
            },
        ).decodeAs<String>()
    }

    override suspend fun addCartLineFromQr(
        cartId: String,
        qrPayload: String,
        qty: Double,
    ): String {
        require(cartId.isNotBlank() && qrPayload.isNotBlank())
        require(qty > 0)
        return client.postgrest.rpc(
            RpcNames.ADD_CART_LINE_FROM_QR,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_qr_payload", qrPayload.trim())
                put("p_qty", qty)
            },
        ).decodeAs<String>()
    }

    override suspend fun checkoutPosCart(
        cartId: String,
        receiptEmail: String?,
        receiptWhatsappE164: String?,
        receiptPhoneE164: String?,
    ): CheckoutPosResult {
        require(cartId.isNotBlank())
        val hadCustomer = getPosCartCustomerId(cartId) != null
        val invoiceId = client.postgrest.rpc(
            RpcNames.CHECKOUT_POS_CART,
            buildJsonObject {
                put("p_cart_id", cartId)
                if (receiptEmail.isNullOrBlank()) put("p_receipt_email", JsonNull)
                else put("p_receipt_email", receiptEmail.trim())
                if (receiptWhatsappE164.isNullOrBlank()) put("p_receipt_whatsapp_e164", JsonNull)
                else put("p_receipt_whatsapp_e164", receiptWhatsappE164.trim())
                if (receiptPhoneE164.isNullOrBlank()) put("p_receipt_phone_e164", JsonNull)
                else put("p_receipt_phone_e164", receiptPhoneE164.trim())
            },
        ).decodeAs<String>()

        val inv = client.from("sales_invoices")
            .select(
                Columns.list(
                    "id",
                    "customer_id",
                    "customer_email",
                    "customer_whatsapp_e164",
                ),
            ) {
                filter { eq("id", invoiceId) }
                limit(1)
            }
            .decodeList<SalesInvoiceContactRow>()
            .firstOrNull()

        return CheckoutPosResult(
            invoiceId = invoiceId,
            customerId = inv?.customerId,
            receiptEmail = inv?.customerEmail ?: receiptEmail?.trim()?.ifBlank { null },
            receiptWhatsappE164 = inv?.customerWhatsappE164
                ?: receiptWhatsappE164?.trim()?.ifBlank { null },
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
        require(cartId.isNotBlank())
        require(tenders.isNotEmpty()) { "tenders required for split-bill" }
        val hadCustomer = getPosCartCustomerId(cartId) != null
        val tendersJson = kotlinx.serialization.json.buildJsonArray {
            tenders.forEach { t ->
                add(
                    buildJsonObject {
                        put("tender", t.tender)
                        put("amount", t.amount)
                        if (t.currency != null) put("currency", t.currency)
                        if (t.exchangeRate != null) put("exchange_rate", t.exchangeRate)
                    },
                )
            }
        }
        val invoiceId = client.postgrest.rpc(
            RpcNames.CHECKOUT_POS_CART_WITH_TENDERS,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_tenders", tendersJson)
                if (receiptEmail.isNullOrBlank()) put("p_receipt_email", JsonNull)
                else put("p_receipt_email", receiptEmail.trim())
                if (receiptWhatsappE164.isNullOrBlank()) put("p_receipt_whatsapp_e164", JsonNull)
                else put("p_receipt_whatsapp_e164", receiptWhatsappE164.trim())
                if (receiptPhoneE164.isNullOrBlank()) put("p_receipt_phone_e164", JsonNull)
                else put("p_receipt_phone_e164", receiptPhoneE164.trim())
            },
        ).decodeAs<String>()

        val inv = client.from("sales_invoices")
            .select(
                Columns.list(
                    "id",
                    "customer_id",
                    "customer_email",
                    "customer_whatsapp_e164",
                ),
            ) {
                filter { eq("id", invoiceId) }
                limit(1)
            }
            .decodeList<SalesInvoiceContactRow>()
            .firstOrNull()

        return CheckoutPosResult(
            invoiceId = invoiceId,
            customerId = inv?.customerId,
            receiptEmail = inv?.customerEmail ?: receiptEmail?.trim()?.ifBlank { null },
            receiptWhatsappE164 = inv?.customerWhatsappE164
                ?: receiptWhatsappE164?.trim()?.ifBlank { null },
            hadCustomerBeforeCheckout = hadCustomer,
        )
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
        return client.postgrest.rpc(
            RpcNames.CREATE_ECOCASH_INTENT,
            buildJsonObject {
                put("p_external_ref", externalRef)
                put("p_payer_msisdn", payerMsisdn)
                put("p_amount", amount)
                put("p_currency", currency.rpcValue)
                put("p_payer_mode", payerMode)
                put("p_channel", "pos")
                if (customerId.isNullOrBlank()) put("p_customer_id", JsonNull)
                else put("p_customer_id", customerId)
                if (salesInvoiceId.isNullOrBlank()) put("p_sales_invoice_id", JsonNull)
                else put("p_sales_invoice_id", salesInvoiceId)
            },
        ).decodeAs<String>()
    }

    override suspend fun lookupStockItemByOem(oemPartNumber: String): StockItemRef {
        val oem = oemPartNumber.trim()
        require(oem.isNotBlank())
        val row = client.from("stock_items")
            .select(Columns.list("id", "base_uom_id", "oem_part_number")) {
                filter {
                    eq("oem_part_number", oem)
                }
                limit(1)
            }
            .decodeList<StockItemRow>()
            .firstOrNull()
            ?: throw IllegalStateException("unknown part $oem")
        return StockItemRef(
            stockItemId = row.id,
            uomId = row.baseUomId,
            oemPartNumber = row.oemPartNumber,
        )
    }

    override suspend fun searchCatalog(
        mode: CatalogSearchMode,
        query: String,
    ): CatalogSearchResult {
        val q = query.trim()
        val raw = client.postgrest.rpc(
            RpcNames.SEARCH_CATALOG,
            buildJsonObject {
                put("p_mode", mode.rpcValue)
                put("p_query", q)
            },
        ).decodeAs<kotlinx.serialization.json.JsonObject>()
        val canonical = parseCatalogSearchResult(raw, mode, q)
        if (mode != CatalogSearchMode.PART || q.length < 2) return canonical

        // Operator POS promises part-name and partial-OEM discovery. The canonical catalog RPC
        // is fitment-centric, so merge stock-master matches without replacing its authority.
        val descriptionMatches = client.from("stock_items")
            .select(Columns.list("id", "base_uom_id", "oem_part_number", "description")) {
                filter { ilike("description", "%$q%") }
                order("oem_part_number", Order.ASCENDING)
                limit(30)
            }
            .decodeList<StockItemRow>()
        val oemMatches = client.from("stock_items")
            .select(Columns.list("id", "base_uom_id", "oem_part_number", "description")) {
                filter { ilike("oem_part_number", "%$q%") }
                order("oem_part_number", Order.ASCENDING)
                limit(30)
            }
            .decodeList<StockItemRow>()
        val stockHits = (descriptionMatches + oemMatches)
            .distinctBy { it.oemPartNumber }
            .map { row ->
                CatalogPartHit(
                    oemPartNumber = row.oemPartNumber,
                    description = row.description,
                )
            }
        return canonical.copy(
            parts = (canonical.parts + stockHits).distinctBy { it.oemPartNumber }.take(50),
        )
    }

    override suspend fun searchCatalogForVehicle(
        vehicle: PosSaleVehicleSelection,
        query: String,
        limit: Int,
    ): CatalogSearchResult {
        val q = query.trim()
        val raw = client.postgrest.rpc(
            RpcNames.SEARCH_POS_VEHICLE_SPARES,
            buildJsonObject {
                put("p_model_slug", vehicle.modelSlug)
                put("p_chassis_code", vehicle.chassisCode)
                put("p_engine_code", vehicle.engineCode)
                put("p_query", q)
                put("p_limit", limit.coerceIn(1, 100))
            },
        ).decodeAs<kotlinx.serialization.json.JsonObject>()
        return parseCatalogSearchResult(raw, CatalogSearchMode.PART, q)
    }

    override suspend fun setPosCartVehicle(
        cartId: String,
        vehicle: PosSaleVehicleSelection?,
    ): String = client.postgrest.rpc(
        RpcNames.SET_POS_CART_VEHICLE,
        buildJsonObject {
            put("p_cart_id", cartId)
            if (vehicle == null) {
                put("p_model_slug", JsonNull)
                put("p_model_name", JsonNull)
                put("p_generation", JsonNull)
                put("p_chassis_code", JsonNull)
                put("p_engine_code", JsonNull)
            } else {
                put("p_model_slug", vehicle.modelSlug)
                put("p_model_name", vehicle.modelName)
                put("p_generation", vehicle.generation)
                put("p_chassis_code", vehicle.chassisCode)
                put("p_engine_code", vehicle.engineCode)
            }
        },
    ).decodeAs<String>()

    override suspend fun getPosCartVehicle(cartId: String): PosSaleVehicleSelection? {
        val row = client.from("pos_carts")
            .select(Columns.list(
                "vehicle_model_slug", "vehicle_model_name", "vehicle_generation",
                "vehicle_chassis_code", "vehicle_engine_code",
            )) {
                filter { eq("id", cartId) }
                limit(1)
            }
            .decodeList<PosCartVehicleRow>()
            .firstOrNull() ?: return null
        val slug = row.modelSlug?.takeIf { it.isNotBlank() } ?: return null
        val name = row.modelName?.takeIf { it.isNotBlank() } ?: return null
        val generation = row.generation?.takeIf { it.isNotBlank() } ?: row.chassisCode.orEmpty()
        val chassis = row.chassisCode?.takeIf { it.isNotBlank() } ?: return null
        val engine = row.engineCode?.takeIf { it.isNotBlank() } ?: return null
        return PosSaleVehicleSelection(slug, name, generation, chassis, engine)
    }

    override suspend fun listCatalogMakers(): List<EpcMaker> {
        val raw = client.postgrest.rpc(RpcNames.LIST_CATALOG_MAKERS)
            .decodeAs<kotlinx.serialization.json.JsonElement>()
        return parseEpcMakerList(raw)
    }

    override suspend fun listCatalogModels(makerSlug: String): List<EpcModel> {
        val raw = client.postgrest.rpc(
            RpcNames.LIST_CATALOG_MODELS,
            buildJsonObject { put("p_maker_slug", makerSlug) },
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        return parseEpcModelList(raw)
    }

    override suspend fun listCatalogVariants(makerSlug: String, modelSlug: String): List<EpcVariant> {
        val raw = client.postgrest.rpc(
            RpcNames.LIST_CATALOG_VARIANTS,
            buildJsonObject {
                put("p_maker_slug", makerSlug)
                put("p_model_slug", modelSlug)
            },
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        return parseEpcVariantList(raw)
    }

    override suspend fun listCatalogSections(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
    ): List<EpcSection> {
        val raw = client.postgrest.rpc(
            RpcNames.LIST_CATALOG_SECTIONS,
            buildJsonObject {
                put("p_maker_slug", makerSlug)
                put("p_model_slug", modelSlug)
                put("p_variant_slug", variantSlug)
            },
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        return parseEpcSectionList(raw)
    }

    override suspend fun getCatalogDiagram(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
    ): EpcDiagramResponse {
        val raw = client.postgrest.rpc(
            RpcNames.GET_CATALOG_DIAGRAM,
            buildJsonObject {
                put("p_maker_slug", makerSlug)
                put("p_model_slug", modelSlug)
                put("p_variant_slug", variantSlug)
                put("p_section_slug", sectionSlug)
            },
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        val parsed = parseEpcDiagram(raw)
        // image_url / storage_path come from RPC; UI resolves Storage when needed.
        return parsed
    }

    override suspend fun listCatalogDiagrams(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
    ): List<EpcDiagramSummary> {
        val raw = client.postgrest.rpc(
            RpcNames.LIST_CATALOG_DIAGRAMS,
            buildJsonObject {
                put("p_maker_slug", makerSlug)
                put("p_model_slug", modelSlug)
                put("p_variant_slug", variantSlug)
                put("p_section_slug", sectionSlug)
            },
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        return parseEpcDiagramSummaryList(raw)
    }

    override suspend fun getCatalogDiagramBySlug(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
        diagramSlug: String,
    ): EpcDiagramResponse {
        val raw = client.postgrest.rpc(
            RpcNames.GET_CATALOG_DIAGRAM_BY_SLUG,
            buildJsonObject {
                put("p_maker_slug", makerSlug)
                put("p_model_slug", modelSlug)
                put("p_variant_slug", variantSlug)
                put("p_section_slug", sectionSlug)
                put("p_diagram_slug", diagramSlug)
            },
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        return parseEpcDiagram(raw)
    }

    override suspend fun setPosCartLineQty(lineId: String, qty: Double, unitPrice: Double) {
        require(lineId.isNotBlank())
        require(qty > 0) { "qty must be > 0" }
        val total = kotlin.math.round(unitPrice * qty * 100.0) / 100.0
        client.from("pos_cart_lines").update(
            PosCartLineQtyUpdate(qty = qty, lineTotal = total),
        ) {
            filter { eq("id", lineId) }
        }
    }

    override suspend fun deletePosCartLine(lineId: String) {
        require(lineId.isNotBlank())
        client.from("pos_cart_lines").delete {
            filter { eq("id", lineId) }
        }
    }

    override suspend fun listPosCartLines(cartId: String): List<PosCartLineSummary> {
        require(cartId.isNotBlank())
        val rows = client.from("pos_cart_lines")
            .select(
                Columns.list(
                    "id",
                    "stock_item_id",
                    "qty",
                    "unit_price",
                    "line_total",
                    "is_core_charge",
                ),
            ) {
                filter { eq("cart_id", cartId) }
                order("created_at", Order.ASCENDING)
                limit(200)
            }
            .decodeList<PosCartLineRow>()

        // OEM labels optional — soft-fail so cart still shows without stock_items join.
        val oemByItem = mutableMapOf<String, String>()
        rows.map { it.stockItemId }.distinct().take(20).forEach { itemId ->
            runCatching {
                client.from("stock_items")
                    .select(Columns.list("id", "oem_part_number")) {
                        filter { eq("id", itemId) }
                        limit(1)
                    }
                    .decodeList<StockItemOemRow>()
                    .firstOrNull()
                    ?.let { oemByItem[it.id] = it.oemPartNumber }
            }
        }

        return rows.map { row ->
            PosCartLineSummary(
                id = row.id,
                stockItemId = row.stockItemId,
                oemPartNumber = oemByItem[row.stockItemId],
                qty = row.qty,
                unitPrice = row.unitPrice,
                lineTotal = row.lineTotal,
                isCoreCharge = row.isCoreCharge,
            )
        }
    }

    override suspend fun getPosCartCustomerId(cartId: String): String? {
        require(cartId.isNotBlank())
        return client.from("pos_carts")
            .select(Columns.list("customer_id")) {
                filter { eq("id", cartId) }
                limit(1)
            }
            .decodeList<PosCartCustomerRow>()
            .firstOrNull()
            ?.customerId
    }

    override suspend fun listWarehouses(): List<WarehouseRef> =
        client.from("warehouses")
            .select(Columns.list("id", "code", "name")) {
                order("code", Order.ASCENDING)
                limit(50)
            }
            .decodeList<WarehouseRow>()
            .map { WarehouseRef(id = it.id, code = it.code, name = it.name) }

    override suspend fun createPosScanSession(cartId: String): PosScanSessionCreated {
        require(cartId.isNotBlank())
        val row = client.postgrest.rpc(
            RpcNames.CREATE_POS_SCAN_SESSION,
            buildJsonObject { put("p_cart_id", cartId) },
        ).decodeList<PosScanSessionRow>().firstOrNull()
            ?: error("create_pos_scan_session returned empty")
        return PosScanSessionCreated(
            sessionId = row.sessionId,
            pairingCode = row.pairingCode,
            expiresAt = row.expiresAt,
        )
    }

    override suspend fun claimPosScanSession(pairingCode: String): String {
        require(pairingCode.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CLAIM_POS_SCAN_SESSION,
            buildJsonObject { put("p_pairing_code", pairingCode.trim()) },
        ).decodeAs<String>()
    }

    override suspend fun revokePosScanSession(sessionId: String): String {
        require(sessionId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.REVOKE_POS_SCAN_SESSION,
            buildJsonObject { put("p_session_id", sessionId) },
        ).decodeAs<String>()
    }

    override suspend fun getPosScanSessionCartId(sessionId: String): String? {
        require(sessionId.isNotBlank())
        return client.from("pos_scan_sessions")
            .select(Columns.list("cart_id")) {
                filter { eq("id", sessionId) }
                limit(1)
            }
            .decodeList<PosScanSessionCartRow>()
            .firstOrNull()
            ?.cartId
    }

    override suspend fun parkPosCart(cartId: String): String {
        require(cartId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.PARK_POS_CART,
            buildJsonObject { put("p_cart_id", cartId) },
        ).decodeAs<String>()
    }

    override suspend fun resumePosCart(cartId: String): String {
        require(cartId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.RESUME_POS_CART,
            buildJsonObject { put("p_cart_id", cartId) },
        ).decodeAs<String>()
    }

    override suspend fun isPosApprover(): Boolean =
        client.postgrest.rpc(RpcNames.IS_POS_APPROVER).decodeAs<Boolean>()

    override suspend fun applyPosCartDiscount(
        cartId: String,
        discountPercent: Double,
        notes: String?,
    ): String {
        require(cartId.isNotBlank())
        require(discountPercent in 0.0..100.0)
        return client.postgrest.rpc(
            RpcNames.APPLY_POS_CART_DISCOUNT,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_discount_percent", discountPercent)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun applyPosLinePriceOverride(
        lineId: String,
        unitPrice: Double,
        notes: String?,
    ): String {
        require(lineId.isNotBlank())
        require(unitPrice >= 0.0) { "unit price must be >= 0" }
        return client.postgrest.rpc(
            RpcNames.APPLY_POS_LINE_PRICE_OVERRIDE,
            buildJsonObject {
                put("p_line_id", lineId)
                put("p_unit_price", unitPrice)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun voidPosCart(cartId: String, notes: String?): String {
        require(cartId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.VOID_POS_CART,
            buildJsonObject {
                put("p_cart_id", cartId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun postPosRefund(invoiceId: String, notes: String?): String {
        require(invoiceId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.POST_POS_REFUND,
            buildJsonObject {
                put("p_invoice_id", invoiceId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun createPosQuotationFromCart(
        cartId: String,
        validUntil: String?,
        notes: String?,
    ): String {
        require(cartId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CREATE_POS_QUOTATION_FROM_CART,
            buildJsonObject {
                put("p_cart_id", cartId)
                if (validUntil.isNullOrBlank()) put("p_valid_until", JsonNull)
                else put("p_valid_until", validUntil)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun sendPosQuotation(
        quotationId: String,
        channel: String,
        contact: String?,
    ): String {
        require(quotationId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SEND_POS_QUOTATION,
            buildJsonObject {
                put("p_quotation_id", quotationId)
                put("p_channel", channel.trim().lowercase())
                if (contact.isNullOrBlank()) put("p_contact", JsonNull)
                else put("p_contact", contact.trim())
            },
        ).decodeAs<String>()
    }

    override suspend fun convertPosQuotationToCart(quotationId: String): String {
        require(quotationId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CONVERT_POS_QUOTATION_TO_CART,
            buildJsonObject { put("p_quotation_id", quotationId) },
        ).decodeAs<String>()
    }

    override suspend fun listPosQuotations(
        status: String?,
        limit: Int,
    ): List<PosQuotationSummary> {
        val rows = client.postgrest.rpc(
            RpcNames.LIST_POS_QUOTATIONS,
            buildJsonObject {
                if (status.isNullOrBlank()) put("p_status", JsonNull)
                else put("p_status", status.trim().lowercase())
                put("p_limit", limit.coerceIn(1, 200))
            },
        ).decodeList<PosQuotationRow>()
        return rows.map { row ->
            PosQuotationSummary(
                id = row.id,
                documentNumber = row.documentNumber,
                customerId = row.customerId,
                warehouseId = row.warehouseId,
                currency = CurrencyCode.entries.find { it.rpcValue == row.currency }
                    ?: CurrencyCode.USD,
                status = row.status,
                validUntil = row.validUntil,
                sentChannel = row.sentChannel,
                createdAt = row.createdAt,
                lineCount = row.lineCount,
                total = row.total,
            )
        }
    }

    override suspend fun listPosPopularSpares(
        days: Int,
        limit: Int,
    ): List<PopularPosSpare> {
        val rows = client.postgrest.rpc(
            RpcNames.LIST_POS_POPULAR_SPARES,
            buildJsonObject {
                put("p_days", days.coerceIn(1, 365))
                put("p_limit", limit.coerceIn(1, 24))
            },
        ).decodeList<PopularPosSpareRow>()
        return rows.map { row ->
            PopularPosSpare(
                stockItemId = row.stockItemId,
                oemPartNumber = row.oemPartNumber,
                description = row.description,
                unitsSold = row.unitsSold,
                saleableQty = row.saleableQty,
                unitPrice = row.unitPrice,
                currency = row.currency?.let { value ->
                    CurrencyCode.entries.find { it.rpcValue == value }
                },
            )
        }
    }

    override suspend fun listPosRecentInvoices(
        query: String?,
        limit: Int,
    ): List<PosInvoiceSummary> {
        val rows = client.postgrest.rpc(
            RpcNames.LIST_POS_RECENT_INVOICES,
            buildJsonObject {
                if (query.isNullOrBlank()) put("p_query", JsonNull) else put("p_query", query.trim())
                put("p_limit", limit.coerceIn(1, 200))
            },
        ).decodeList<PosInvoiceRow>()
        return rows.map { row ->
            PosInvoiceSummary(
                id = row.id,
                documentNumber = row.documentNumber,
                customerId = row.customerId,
                customerName = row.customerName,
                total = row.total,
                currency = CurrencyCode.entries.find { it.rpcValue == row.currency } ?: CurrencyCode.USD,
                postedAt = row.postedAt,
                vehicleModelName = row.vehicleModelName,
                vehicleGeneration = row.vehicleGeneration,
                vehicleChassisCode = row.vehicleChassisCode,
                vehicleEngineCode = row.vehicleEngineCode,
            )
        }
    }

    override suspend fun pullPosOfflineSnapshot(warehouseId: String): OfflinePosSnapshot {
        require(warehouseId.isNotBlank())
        val raw = client.postgrest.rpc(
            RpcNames.PULL_POS_OFFLINE_SNAPSHOT,
            buildJsonObject { put("p_warehouse_id", warehouseId) },
        ).decodeAs<JsonObject>()
        val currency = CurrencyCode.entries.find {
            it.rpcValue == raw["currency"]?.jsonPrimitive?.contentOrNull
        } ?: CurrencyCode.USD
        val items = raw["items"]?.jsonArray?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val stockId = o["stock_item_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val oem = o["oem_part_number"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val uom = o["uom_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            OfflineCatalogItem(
                stockItemId = stockId,
                oemPartNumber = oem,
                description = o["description"]?.jsonPrimitive?.contentOrNull,
                uomId = uom,
                unitPrice = o["unit_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                coreCharge = o["core_charge"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                saleableQty = o["saleable_qty"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                currency = CurrencyCode.entries.find {
                    it.rpcValue == o["currency"]?.jsonPrimitive?.contentOrNull
                } ?: currency,
            )
        }.orEmpty()
        return OfflinePosSnapshot(
            warehouseId = raw["warehouse_id"]?.jsonPrimitive?.contentOrNull ?: warehouseId,
            pulledAt = raw["pulled_at"]?.jsonPrimitive?.contentOrNull ?: "",
            priceListId = raw["price_list_id"]?.jsonPrimitive?.contentOrNull,
            currency = currency,
            items = items,
        )
    }

    override suspend fun replayOfflinePosSale(
        clientSaleId: String,
        payload: OfflineSaleReplayPayload,
    ): String {
        require(clientSaleId.isNotBlank())
        require(payload.lines.isNotEmpty())
        require(payload.tenders.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.REPLAY_OFFLINE_POS_SALE,
            buildJsonObject {
                put("p_client_sale_id", clientSaleId)
                put(
                    "p_payload",
                    buildJsonObject {
                        put("warehouse_id", payload.warehouseId)
                        put("currency", payload.currency.rpcValue)
                        put("exchange_rate", payload.exchangeRate)
                        if (payload.deviceId.isNullOrBlank()) put("device_id", JsonNull)
                        else put("device_id", payload.deviceId)
                        putJsonArray("lines") {
                            payload.lines.forEach { line ->
                                add(
                                    buildJsonObject {
                                        put("stock_item_id", line.stockItemId)
                                        put("uom_id", line.uomId)
                                        put("qty", line.qty)
                                        put("expected_unit_price", line.expectedUnitPrice)
                                    },
                                )
                            }
                        }
                        putJsonArray("tenders") {
                            payload.tenders.forEach { t ->
                                add(
                                    buildJsonObject {
                                        put("tender", t.tender)
                                        put("amount", t.amount)
                                        put("currency", t.currency ?: payload.currency.rpcValue)
                                        if (t.exchangeRate != null) put("exchange_rate", t.exchangeRate)
                                    },
                                )
                            }
                        }
                        if (payload.receiptEmail.isNullOrBlank()) put("receipt_email", JsonNull)
                        else put("receipt_email", payload.receiptEmail)
                        if (payload.receiptWhatsappE164.isNullOrBlank()) {
                            put("receipt_whatsapp_e164", JsonNull)
                        } else {
                            put("receipt_whatsapp_e164", payload.receiptWhatsappE164)
                        }
                        if (payload.receiptPhoneE164.isNullOrBlank()) {
                            put("receipt_phone_e164", JsonNull)
                        } else {
                            put("receipt_phone_e164", payload.receiptPhoneE164)
                        }
                        if (payload.soldAt.isNullOrBlank()) put("sold_at", JsonNull)
                        else put("sold_at", payload.soldAt)
                        if (payload.vehicle == null) {
                            put("vehicle", JsonNull)
                        } else {
                            put(
                                "vehicle",
                                buildJsonObject {
                                    put("model_slug", payload.vehicle.modelSlug)
                                    put("model_name", payload.vehicle.modelName)
                                    put("generation", payload.vehicle.generation)
                                    put("chassis_code", payload.vehicle.chassisCode)
                                    put("engine_code", payload.vehicle.engineCode)
                                },
                            )
                        }
                    },
                )
            },
        ).decodeAs<String>()
    }

    override suspend fun resolveStaffLoginEmail(identifier: String): String {
        require(identifier.isNotBlank()) { "invalid credentials" }
        return client.postgrest.rpc(
            RpcNames.RESOLVE_STAFF_LOGIN_EMAIL,
            buildJsonObject { put("p_identifier", identifier.trim()) },
        ).decodeAs<String>()
    }

    override suspend fun staffLoginIsLocked(identifier: String): Boolean {
        val raw = identifier.trim()
        if (raw.isEmpty()) return false
        return client.postgrest.rpc(
            RpcNames.STAFF_LOGIN_IS_LOCKED,
            buildJsonObject { put("p_identifier", raw) },
        ).decodeAs<Boolean>()
    }

    override suspend fun recordStaffLoginAttempt(identifier: String, success: Boolean) {
        val raw = identifier.trim()
        if (raw.isEmpty()) return
        client.postgrest.rpc(
            RpcNames.RECORD_STAFF_LOGIN_ATTEMPT,
            buildJsonObject {
                put("p_identifier", raw)
                put("p_success", success)
            },
        )
    }

    override suspend fun myDefaultLanding(): String? {
        val raw = runCatching {
            client.postgrest.rpc(RpcNames.MY_DEFAULT_LANDING).decodeAs<String>()
        }.getOrNull()
        return raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun lookupSaleableQtyByOem(oemPartNumber: String): Double? {
        val oem = oemPartNumber.trim()
        if (oem.isEmpty()) return null
        val item = client.from("stock_items")
            .select(Columns.list("id")) {
                filter { eq("oem_part_number", oem) }
                limit(1)
            }
            .decodeList<StockItemIdRow>()
            .firstOrNull()
            ?: return null
        val levels = client.from("stock_levels")
            .select(Columns.list("quantity")) {
                filter { eq("stock_item_id", item.id) }
                limit(200)
            }
            .decodeList<StockLevelQtyRow>()
        return levels.sumOf { it.quantity }
    }

    /**
     * Second-user manager reauth for POS approval RPCs.
     * Saves attendant session → signs in manager → runs [block] → restores attendant.
     */
    private var staffPortalAttendantSession: UserSession? = null

    /**
     * Starts an elevated staff-portal session without losing the POS attendant session.
     * The supplied staff identity is authenticated normally, then all hub/module access is
     * resolved from that identity. The attendant session is retained in memory only and
     * restored by [endStaffPortalSession].
     */
    suspend fun beginStaffPortalSession(identifier: String, password: String) {
        require(identifier.isNotBlank() && password.isNotBlank()) { "staff credentials required" }
        if (staffPortalAttendantSession != null) error("staff portal session already active")
        val attendant = auth.currentSessionOrNull()
            ?: error("attendant session required before staff portal access")
        if (staffLoginIsLocked(identifier)) error("staff sign-in temporarily locked")
        val email = resolveStaffLoginEmail(identifier)
        try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val roles = listMyStaffRoles()
            if (roles.isEmpty()) error("no active staff role")
            recordStaffLoginAttempt(identifier, true)
            staffPortalAttendantSession = attendant
        } catch (t: Throwable) {
            runCatching { recordStaffLoginAttempt(identifier, false) }
            auth.importSession(attendant)
            throw t
        }
    }

    suspend fun endStaffPortalSession() {
        val attendant = staffPortalAttendantSession ?: return
        try {
            auth.importSession(attendant)
        } finally {
            staffPortalAttendantSession = null
        }
    }

    fun isStaffPortalSessionActive(): Boolean = staffPortalAttendantSession != null

    suspend fun <T> withManagerApproval(
        managerIdentifier: String,
        managerPassword: String,
        block: suspend () -> T,
    ): T {
        val attendant = auth.currentSessionOrNull()
            ?: error("attendant session required before manager approval")
        val email = resolveStaffLoginEmail(managerIdentifier)
        try {
            val manager = AuthEdgeClient.login(client, email, managerPassword)
            importAccessToken(manager.accessToken, manager.refreshToken, manager.expiresIn)
            return block()
        } finally {
            auth.importSession(
                UserSession(
                    accessToken = attendant.accessToken,
                    refreshToken = attendant.refreshToken,
                    expiresIn = attendant.expiresIn,
                    tokenType = attendant.tokenType,
                    user = attendant.user,
                ),
            )
        }
    }

    override suspend fun postStockReceipt(
        toWarehouseId: String,
        notes: String?,
        lines: List<ReceiptLineInput>,
    ): String {
        require(toWarehouseId.isNotBlank())
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.POST_STOCK_RECEIPT,
            buildJsonObject {
                put("p_to_warehouse_id", toWarehouseId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
                put("p_lines", lines.toReceiptJsonArray())
            },
        ).decodeAs<String>()
    }

    override suspend fun createStockTransfer(
        fromWarehouseId: String,
        toWarehouseId: String,
        notes: String?,
        lines: List<TransferLineInput>,
    ): String {
        require(fromWarehouseId.isNotBlank() && toWarehouseId.isNotBlank())
        require(fromWarehouseId != toWarehouseId)
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.CREATE_STOCK_TRANSFER,
            buildJsonObject {
                put("p_from_warehouse_id", fromWarehouseId)
                put("p_to_warehouse_id", toWarehouseId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
                put("p_lines", lines.toTransferJsonArray())
            },
        ).decodeAs<String>()
    }

    override suspend fun approveStockTransfer(entryId: String): String {
        require(entryId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.APPROVE_STOCK_TRANSFER,
            buildJsonObject { put("p_entry_id", entryId) },
        ).decodeAs<String>()
    }

    override suspend fun rejectStockTransfer(entryId: String): String {
        require(entryId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.REJECT_STOCK_TRANSFER,
            buildJsonObject { put("p_entry_id", entryId) },
        ).decodeAs<String>()
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
            require(!itemIds.isNullOrEmpty())
        }
        val rate = when {
            exchangeRate != null -> exchangeRate
            currency == CurrencyCode.USD -> 1.0
            else -> null
        }
        return client.postgrest.rpc(
            RpcNames.CREATE_STOCK_RECONCILIATION_DRAFT,
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                put("p_scope", scope.rpcValue)
                if (itemIds.isNullOrEmpty()) put("p_item_ids", JsonNull)
                else put("p_item_ids", buildJsonArray { itemIds.forEach { add(JsonPrimitive(it)) } })
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
                put("p_currency", currency.rpcValue)
                if (rate == null) put("p_exchange_rate", JsonNull)
                else put("p_exchange_rate", rate)
            },
        ).decodeAs<String>()
    }

    override suspend fun upsertStockReconciliationLines(
        reconciliationId: String,
        lines: List<ReconciliationLineInput>,
    ): Int {
        require(reconciliationId.isNotBlank())
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.UPSERT_STOCK_RECONCILIATION_LINES,
            buildJsonObject {
                put("p_reconciliation_id", reconciliationId)
                put(
                    "p_lines",
                    buildJsonArray {
                        lines.forEach { line ->
                            add(
                                buildJsonObject {
                                    put("stock_item_id", line.stockItemId)
                                    put("counted_qty", line.countedQty)
                                },
                            )
                        }
                    },
                )
            },
        ).decodeAs<Int>()
    }

    override suspend fun submitStockReconciliation(reconciliationId: String): String {
        require(reconciliationId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SUBMIT_STOCK_RECONCILIATION,
            buildJsonObject { put("p_reconciliation_id", reconciliationId) },
        ).decodeAs<String>()
    }

    override suspend fun approveStockReconciliation(reconciliationId: String): String {
        require(reconciliationId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.APPROVE_STOCK_RECONCILIATION,
            buildJsonObject { put("p_reconciliation_id", reconciliationId) },
        ).decodeAs<String>()
    }

    override suspend fun cancelStockReconciliation(
        reconciliationId: String,
        notes: String?,
    ): String {
        require(reconciliationId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CANCEL_STOCK_RECONCILIATION,
            buildJsonObject {
                put("p_reconciliation_id", reconciliationId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun listDeliveryNotes(): List<DeliveryNoteSummary> =
        client.from("delivery_notes")
            .select(
                Columns.list(
                    "id",
                    "document_number",
                    "sales_invoice_id",
                    "status",
                ),
            ) {
                order("created_at", Order.DESCENDING)
                limit(40)
            }
            .decodeList<DeliveryNoteRow>()
            .map {
                DeliveryNoteSummary(
                    id = it.id,
                    documentNumber = it.documentNumber,
                    salesInvoiceId = it.salesInvoiceId,
                    status = it.status,
                )
            }

    override suspend fun listPickLists(): List<PickListSummary> =
        client.from("pick_lists")
            .select(
                Columns.list(
                    "id",
                    "document_number",
                    "sales_invoice_id",
                    "status",
                ),
            ) {
                order("created_at", Order.DESCENDING)
                limit(40)
            }
            .decodeList<PickListRow>()
            .map {
                PickListSummary(
                    id = it.id,
                    documentNumber = it.documentNumber,
                    salesInvoiceId = it.salesInvoiceId,
                    status = it.status,
                )
            }

    override suspend fun createPickList(salesInvoiceId: String, linesJson: String?): String {
        require(salesInvoiceId.isNotBlank())
        val linesElement = when {
            linesJson.isNullOrBlank() -> JsonNull
            else -> runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(linesJson)
            }.getOrElse { JsonNull }
        }
        return client.postgrest.rpc(
            RpcNames.CREATE_PICK_LIST,
            buildJsonObject {
                put("p_sales_invoice_id", salesInvoiceId)
                put("p_lines", linesElement)
            },
        ).decodeAs<String>()
    }

    override suspend fun confirmPickLines(
        pickListId: String,
        lines: List<ConfirmPickLineInput>,
    ): String {
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.CONFIRM_PICK_LINES,
            buildJsonObject {
                put("p_pick_list_id", pickListId)
                put("p_lines", lines.toJsonArray())
            },
        ).decodeAs<String>()
    }

    override suspend fun createDeliveryNote(
        salesInvoiceId: String,
        lines: List<DnLineInput>,
        pickListId: String?,
    ): String {
        require(salesInvoiceId.isNotBlank())
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.CREATE_DELIVERY_NOTE,
            buildJsonObject {
                put("p_sales_invoice_id", salesInvoiceId)
                put(
                    "p_lines",
                    buildJsonArray {
                        lines.forEach { line ->
                            add(
                                buildJsonObject {
                                    put("sales_invoice_line_id", line.salesInvoiceLineId)
                                    put("qty", line.qty)
                                },
                            )
                        }
                    },
                )
                if (pickListId.isNullOrBlank()) put("p_pick_list_id", JsonNull)
                else put("p_pick_list_id", pickListId)
            },
        ).decodeAs<String>()
    }

    override suspend fun submitDeliveryNote(deliveryNoteId: String): String =
        client.postgrest.rpc(
            RpcNames.SUBMIT_DELIVERY_NOTE,
            buildJsonObject { put("p_delivery_note_id", deliveryNoteId) },
        ).decodeAs<String>()

    override suspend fun cancelDeliveryNote(deliveryNoteId: String): String =
        client.postgrest.rpc(
            RpcNames.CANCEL_DELIVERY_NOTE,
            buildJsonObject { put("p_delivery_note_id", deliveryNoteId) },
        ).decodeAs<String>()

    override suspend fun createDeliveryJob(
        deliveryNoteId: String,
        assigneeUserId: String?,
        etaAt: String?,
        notes: String?,
    ): String {
        require(deliveryNoteId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CREATE_DELIVERY_JOB,
            buildJsonObject {
                put("p_delivery_note_id", deliveryNoteId)
                if (assigneeUserId.isNullOrBlank()) put("p_assignee_user_id", JsonNull)
                else put("p_assignee_user_id", assigneeUserId)
                if (etaAt.isNullOrBlank()) put("p_eta_at", JsonNull)
                else put("p_eta_at", etaAt)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull)
                else put("p_notes", notes)
            },
        ).decodeAs<String>()
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
        return client.postgrest.rpc(
            RpcNames.SET_DELIVERY_JOB_GEO,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                if (pickupLat == null) put("p_pickup_lat", JsonNull) else put("p_pickup_lat", pickupLat)
                if (pickupLng == null) put("p_pickup_lng", JsonNull) else put("p_pickup_lng", pickupLng)
                if (dropoffLat == null) put("p_dropoff_lat", JsonNull) else put("p_dropoff_lat", dropoffLat)
                if (dropoffLng == null) put("p_dropoff_lng", JsonNull) else put("p_dropoff_lng", dropoffLng)
            },
        ).decodeAs<String>()
    }

    override suspend fun updateDeliveryJobStatus(
        deliveryJobId: String,
        status: DeliveryJobStatus,
    ): UpdateDeliveryJobStatusResult {
        require(deliveryJobId.isNotBlank())
        val row = client.postgrest.rpc(
            RpcNames.UPDATE_DELIVERY_JOB_STATUS,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_status", status.rpcValue)
            },
        ).decodeAs<UpdateDeliveryJobStatusRow>()
        return UpdateDeliveryJobStatusResult(
            deliveryJobId = row.deliveryJobId,
            trackToken = row.trackToken,
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
        return client.postgrest.rpc(
            RpcNames.INGEST_DELIVERY_LOCATION,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_lat", lat)
                put("p_lng", lng)
                if (recordedAt.isNullOrBlank()) put("p_recorded_at", JsonNull)
                else put("p_recorded_at", recordedAt)
                if (accuracyM == null) put("p_accuracy_m", JsonNull)
                else put("p_accuracy_m", accuracyM)
            },
        ).decodeAs<String>()
    }

    override suspend fun suggestDeliveryAssignees(
        deliveryJobId: String,
        limit: Int,
    ): List<DeliveryAssigneeSuggestion> {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SUGGEST_DELIVERY_ASSIGNEES,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_limit", limit.coerceIn(1, 50))
            },
        ).decodeList<AssigneeSuggestionRow>().map { it.toSummary() }
    }

    override suspend fun assignDeliveryJob(
        deliveryJobId: String,
        assigneeUserId: String,
        override: Boolean,
    ): String {
        require(deliveryJobId.isNotBlank())
        require(assigneeUserId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.ASSIGN_DELIVERY_JOB,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_assignee_user_id", assigneeUserId)
                put("p_override", override)
            },
        ).decodeAs<String>()
    }

    override suspend fun optimizeDriverStops(driverUserId: String): List<OptimizedDriverStop> {
        require(driverUserId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.OPTIMIZE_DRIVER_STOPS,
            buildJsonObject { put("p_driver_user_id", driverUserId) },
        ).decodeList<OptimizedStopRow>().map { it.toSummary() }
    }

    override suspend fun getDeliveryTrackPoint(deliveryJobId: String): DeliveryTrackPoint? {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.GET_DELIVERY_TRACK_POINT,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                put("p_token", JsonNull)
            },
        ).decodeList<TrackPointRow>().firstOrNull()?.toSummary()
    }

    override suspend fun mintDeliveryTrackToken(
        deliveryJobId: String,
        ttl: String?,
    ): String {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.MINT_DELIVERY_TRACK_TOKEN,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                if (ttl.isNullOrBlank()) put("p_ttl", JsonNull) else put("p_ttl", ttl)
            },
        ).decodeAs<String>()
    }

    override suspend fun generateDeliveryPodOtp(
        deliveryJobId: String,
        ttl: String?,
    ): String {
        require(deliveryJobId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.GENERATE_DELIVERY_POD_OTP,
            buildJsonObject {
                put("p_delivery_job_id", deliveryJobId)
                if (ttl.isNullOrBlank()) put("p_ttl", JsonNull) else put("p_ttl", ttl)
            },
        ).decodeAs<String>()
    }

    override suspend fun listOpenPanicEvents(): List<PanicEventSummary> {
        return client.from("panic_events")
            .select(
                Columns.list(
                    "id",
                    "driver_user_id",
                    "delivery_job_id",
                    "lat",
                    "lng",
                    "created_at",
                    "acknowledged_at",
                    "acknowledged_by",
                ),
            ) {
                filter { exact("acknowledged_at", null) }
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<PanicEventRow>()
            .map { it.toSummary() }
    }

    override suspend fun acknowledgePanicEvent(panicEventId: String): String {
        require(panicEventId.isNotBlank())
        val uid = currentUserId() ?: error("signed-in user required to acknowledge panic")
        val now = java.time.Instant.now().toString()
        client.from("panic_events").update(
            PanicAckUpdate(
                acknowledgedAt = now,
                acknowledgedBy = uid,
            ),
        ) {
            filter { eq("id", panicEventId) }
        }
        return panicEventId
    }

    override suspend fun listMyStaffRoles(): List<String> {
        val uid = currentUserId() ?: return emptyList()
        return client.from("staff_roles")
            .select(Columns.list("role")) {
                filter { eq("user_id", uid) }
            }
            .decodeList<StaffRoleRow>()
            .map { it.role }
    }

    override suspend fun listMyModuleAccess(): List<String> {
        val arr = runCatching {
            client.postgrest.rpc(RpcNames.MY_MODULE_ACCESS).decodeAs<JsonArray>()
        }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            (el as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun listStaffChatThreads(filter: StaffChatFilter): List<ChatThreadSummary> {
        val uid = currentUserId()
        return client.from("chat_threads")
            .select(
                Columns.list(
                    "id",
                    "kind",
                    "status",
                    "subject",
                    "assigned_to",
                    "last_message_at",
                    "created_at",
                ),
            ) {
                filter {
                    when (filter) {
                        StaffChatFilter.OPEN -> eq("status", "open")
                        StaffChatFilter.MINE -> {
                            require(!uid.isNullOrBlank()) { "signed-in user required for mine filter" }
                            eq("assigned_to", uid)
                            neq("status", "closed")
                        }
                        StaffChatFilter.CLOSED -> eq("status", "closed")
                    }
                }
                order("last_message_at", Order.DESCENDING)
                limit(80)
            }
            .decodeList<ChatThreadRow>()
            .map { it.toSummary() }
    }

    override suspend fun listChatMessages(threadId: String): List<ChatMessageSummary> {
        require(threadId.isNotBlank())
        return client.from("chat_messages")
            .select(
                Columns.list(
                    "id",
                    "thread_id",
                    "sender_user_id",
                    "sender_kind",
                    "body",
                    "created_at",
                ),
            ) {
                filter { eq("thread_id", threadId) }
                order("created_at", Order.ASCENDING)
                limit(200)
            }
            .decodeList<ChatMessageRow>()
            .map { it.toSummary() }
    }

    override suspend fun claimChatThread(threadId: String) {
        require(threadId.isNotBlank())
        client.postgrest.rpc(
            RpcNames.CLAIM_CHAT_THREAD,
            buildJsonObject { put("p_thread_id", threadId) },
        )
    }

    override suspend fun closeChatThread(threadId: String) {
        require(threadId.isNotBlank())
        client.postgrest.rpc(
            RpcNames.CLOSE_CHAT_THREAD,
            buildJsonObject { put("p_thread_id", threadId) },
        )
    }

    override suspend fun markChatThreadRead(threadId: String) {
        require(threadId.isNotBlank())
        client.postgrest.rpc(
            RpcNames.MARK_CHAT_THREAD_READ,
            buildJsonObject { put("p_thread_id", threadId) },
        )
    }

    override suspend fun postChatMessage(threadId: String, body: String): String {
        require(threadId.isNotBlank())
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "message body required" }
        return client.postgrest.rpc(
            RpcNames.POST_CHAT_MESSAGE,
            buildJsonObject {
                put("p_thread_id", threadId)
                put("p_body", trimmed)
            },
        ).decodeAs<String>()
    }

    override suspend fun chatUnreadCount(threadId: String?): Int {
        return client.postgrest.rpc(
            RpcNames.CHAT_UNREAD_COUNT,
            buildJsonObject {
                if (threadId.isNullOrBlank()) put("p_thread_id", JsonNull)
                else put("p_thread_id", threadId)
            },
        ).decodeAs<Int>()
    }

    override suspend fun searchCustomers(query: String): List<CustomerOption> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val uuidLike = UUID_REGEX.matches(q)
        return client.from("customers")
            .select(Columns.list("id", "display_name")) {
                filter {
                    if (uuidLike) eq("id", q)
                    else ilike("display_name", "%$q%")
                }
                order("display_name", Order.ASCENDING)
                limit(20)
            }
            .decodeList<CustomerOptionRow>()
            .map { CustomerOption(id = it.id, displayName = it.displayName) }
    }

    override suspend fun listSuppliers(): List<SupplierRef> =
        client.from("suppliers")
            .select(Columns.list("id", "code", "name")) {
                filter { eq("is_active", true) }
                order("code", Order.ASCENDING)
                limit(100)
            }
            .decodeList<SupplierRow>()
            .map { SupplierRef(id = it.id, code = it.code, name = it.name) }

    override suspend fun listBlanketPurchaseOrders(): List<BlanketSummary> {
        val pos = client.from("purchase_orders")
            .select(
                Columns.list(
                    "id",
                    "document_number",
                    "status",
                    "supplier_id",
                    "warehouse_id",
                    "currency",
                    "blanket_max_value",
                    "blanket_value_released",
                    "expected_date",
                ),
            ) {
                filter { eq("is_blanket", true) }
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<BlanketPoRow>()

        if (pos.isEmpty()) return emptyList()

        val supplierIds = pos.map { it.supplierId }.distinct()
        val warehouseIds = pos.map { it.warehouseId }.distinct()
        val suppliers = client.from("suppliers")
            .select(Columns.list("id", "code", "name")) {
                filter { isIn("id", supplierIds) }
            }
            .decodeList<SupplierRow>()
            .associateBy { it.id }
        val warehouses = client.from("warehouses")
            .select(Columns.list("id", "code", "name")) {
                filter { isIn("id", warehouseIds) }
            }
            .decodeList<WarehouseRow>()
            .associateBy { it.id }

        val poIds = pos.map { it.id }
        val lines = client.from("purchase_order_lines")
            .select(
                Columns.list(
                    "id",
                    "purchase_order_id",
                    "line_no",
                    "stock_item_id",
                    "qty_ordered",
                    "qty_released",
                    "unit_price",
                    "currency",
                ),
            ) {
                filter { isIn("purchase_order_id", poIds) }
                order("line_no", Order.ASCENDING)
            }
            .decodeList<BlanketLineRow>()

        val itemIds = lines.map { it.stockItemId }.distinct()
        val oems = if (itemIds.isEmpty()) {
            emptyMap()
        } else {
            client.from("stock_items")
                .select(Columns.list("id", "oem_part_number")) {
                    filter { isIn("id", itemIds) }
                }
                .decodeList<StockItemOemRow>()
                .associate { it.id to it.oemPartNumber }
        }

        val linesByPo = lines.groupBy { it.purchaseOrderId }
        return pos.map { po ->
            val currency = CurrencyCode.entries.find { it.rpcValue == po.currency }
                ?: CurrencyCode.USD
            BlanketSummary(
                id = po.id,
                documentNumber = po.documentNumber,
                status = po.status,
                supplierId = po.supplierId,
                supplierName = suppliers[po.supplierId]?.name,
                warehouseId = po.warehouseId,
                warehouseCode = warehouses[po.warehouseId]?.code,
                currency = currency,
                blanketMaxValue = po.blanketMaxValue ?: 0.0,
                blanketValueReleased = po.blanketValueReleased ?: 0.0,
                expectedDate = po.expectedDate,
                lines = (linesByPo[po.id] ?: emptyList()).map { line ->
                    BlanketLineSummary(
                        id = line.id,
                        lineNo = line.lineNo,
                        stockItemId = line.stockItemId,
                        oemPartNumber = oems[line.stockItemId],
                        qtyOrdered = line.qtyOrdered,
                        qtyReleased = line.qtyReleased,
                        unitPrice = line.unitPrice,
                        currency = CurrencyCode.entries.find { it.rpcValue == line.currency }
                            ?: currency,
                    )
                },
            )
        }
    }

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
        require(supplierId.isNotBlank() && warehouseId.isNotBlank())
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.CREATE_BLANKET_PURCHASE_ORDER,
            buildJsonObject {
                put("p_supplier_id", supplierId)
                put("p_warehouse_id", warehouseId)
                put("p_currency", currency.rpcValue)
                put("p_exchange_rate", exchangeRate)
                put("p_blanket_max_value", blanketMaxValue)
                put(
                    "p_lines",
                    buildJsonArray {
                        lines.forEach { line ->
                            add(
                                buildJsonObject {
                                    put("stock_item_id", line.stockItemId)
                                    put("uom_id", line.uomId)
                                    put("qty", line.qty)
                                    put("unit_price", line.unitPrice)
                                    put(
                                        "currency",
                                        (line.currency ?: currency).rpcValue,
                                    )
                                },
                            )
                        }
                    },
                )
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
                if (expectedDate.isNullOrBlank()) put("p_expected_date", JsonNull)
                else put("p_expected_date", expectedDate)
            },
        ).decodeAs<String>()
    }

    override suspend fun submitPurchaseOrder(purchaseOrderId: String): String {
        require(purchaseOrderId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SUBMIT_PURCHASE_ORDER,
            buildJsonObject { put("p_purchase_order_id", purchaseOrderId) },
        ).decodeAs<String>()
    }

    override suspend fun createBlanketRelease(
        blanketPurchaseOrderId: String,
        lines: List<BlanketReleaseLineInput>,
        notes: String?,
    ): String {
        require(blanketPurchaseOrderId.isNotBlank())
        require(lines.isNotEmpty())
        return client.postgrest.rpc(
            RpcNames.CREATE_BLANKET_RELEASE,
            buildJsonObject {
                put("p_blanket_purchase_order_id", blanketPurchaseOrderId)
                put(
                    "p_lines",
                    buildJsonArray {
                        lines.forEach { line ->
                            add(
                                buildJsonObject {
                                    put("blanket_line_id", line.blanketLineId)
                                    put("qty", line.qty)
                                },
                            )
                        }
                    },
                )
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        ).decodeAs<String>()
    }

    override suspend fun listWarehouseBins(warehouseId: String): List<WarehouseBinSummary> {
        require(warehouseId.isNotBlank())
        return client.from("warehouse_bins")
            .select(
                Columns.list(
                    "id",
                    "warehouse_id",
                    "code",
                    "name",
                    "pick_path_seq",
                    "aisle",
                    "rack",
                    "shelf",
                    "is_active",
                ),
            ) {
                filter { eq("warehouse_id", warehouseId) }
                order("pick_path_seq", Order.ASCENDING)
                order("code", Order.ASCENDING)
                limit(200)
            }
            .decodeList<WarehouseBinRow>()
            .map { it.toSummary() }
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
        require(warehouseId.isNotBlank() && code.isNotBlank() && name.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CREATE_WAREHOUSE_BIN,
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                put("p_code", code.trim())
                put("p_name", name.trim())
                put("p_pick_path_seq", pickPathSeq)
                if (aisle.isNullOrBlank()) put("p_aisle", JsonNull) else put("p_aisle", aisle)
                if (rack.isNullOrBlank()) put("p_rack", JsonNull) else put("p_rack", rack)
                if (shelf.isNullOrBlank()) put("p_shelf", JsonNull) else put("p_shelf", shelf)
            },
        ).decodeAs<String>()
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
        require(binId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.UPDATE_WAREHOUSE_BIN,
            buildJsonObject {
                put("p_bin_id", binId)
                if (name == null) put("p_name", JsonNull) else put("p_name", name)
                if (pickPathSeq == null) put("p_pick_path_seq", JsonNull)
                else put("p_pick_path_seq", pickPathSeq)
                if (aisle == null) put("p_aisle", JsonNull) else put("p_aisle", aisle)
                if (rack == null) put("p_rack", JsonNull) else put("p_rack", rack)
                if (shelf == null) put("p_shelf", JsonNull) else put("p_shelf", shelf)
                if (isActive == null) put("p_is_active", JsonNull) else put("p_is_active", isActive)
            },
        ).decodeAs<String>()
    }

    override suspend fun deactivateWarehouseBin(binId: String): String {
        require(binId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.DEACTIVATE_WAREHOUSE_BIN,
            buildJsonObject { put("p_bin_id", binId) },
        ).decodeAs<String>()
    }

    override suspend fun setStockLevelBin(
        stockItemId: String,
        warehouseId: String,
        binId: String?,
    ): String {
        require(stockItemId.isNotBlank() && warehouseId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SET_STOCK_LEVEL_BIN,
            buildJsonObject {
                put("p_stock_item_id", stockItemId)
                put("p_warehouse_id", warehouseId)
                if (binId.isNullOrBlank()) put("p_bin_id", JsonNull) else put("p_bin_id", binId)
            },
        ).decodeAs<String>()
    }

    override suspend fun getPickPathHints(
        warehouseId: String,
        stockItemIds: List<String>?,
    ): List<PickPathHint> {
        require(warehouseId.isNotBlank())
        val ids = stockItemIds?.filter { it.isNotBlank() }.orEmpty()
        return client.postgrest.rpc(
            RpcNames.GET_PICK_PATH_HINTS,
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                if (ids.isEmpty()) {
                    put("p_stock_item_ids", JsonNull)
                } else {
                    put(
                        "p_stock_item_ids",
                        buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } },
                    )
                }
            },
        ).decodeList<PickPathHintRow>().map { it.toSummary() }
    }

    override suspend fun listConsignmentEntries(): List<ConsignmentEntrySummary> =
        client.from("consignment_entries")
            .select(
                Columns.list(
                    "id",
                    "document_number",
                    "status",
                    "kind",
                    "purpose",
                    "warehouse_id",
                    "supplier_id",
                    "customer_id",
                    "currency",
                ),
            ) {
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<ConsignmentEntryRow>()
            .map { it.toSummary() }

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
        return client.postgrest.rpc(
            RpcNames.CREATE_CONSIGNMENT_ENTRY_DRAFT,
            buildJsonObject {
                put("p_kind", kind.rpcValue)
                put("p_purpose", purpose.rpcValue)
                put("p_warehouse_id", warehouseId)
                if (supplierId.isNullOrBlank()) put("p_supplier_id", JsonNull)
                else put("p_supplier_id", supplierId)
                if (customerId.isNullOrBlank()) put("p_customer_id", JsonNull)
                else put("p_customer_id", customerId)
                put("p_currency", currency.rpcValue)
                put("p_exchange_rate", exchangeRate)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        ).decodeAs<String>()
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
        require(entryId.isNotBlank() && stockItemId.isNotBlank() && uomId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.ADD_CONSIGNMENT_ENTRY_LINE,
            buildJsonObject {
                put("p_entry_id", entryId)
                put("p_stock_item_id", stockItemId)
                put("p_uom_id", uomId)
                put("p_qty", qty)
                put("p_unit_cost", unitCost)
                put("p_unit_price", unitPrice)
                if (currency == null) put("p_currency", JsonNull)
                else put("p_currency", currency.rpcValue)
            },
        ).decodeAs<String>()
    }

    override suspend fun submitConsignmentEntry(entryId: String): String {
        require(entryId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SUBMIT_CONSIGNMENT_ENTRY,
            buildJsonObject { put("p_entry_id", entryId) },
        ).decodeAs<String>()
    }

    override suspend fun cancelConsignmentEntry(entryId: String): String {
        require(entryId.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.CANCEL_CONSIGNMENT_ENTRY,
            buildJsonObject { put("p_entry_id", entryId) },
        ).decodeAs<String>()
    }

    override suspend fun loadCustomerCredit(customerId: String): CustomerCreditSnapshot? {
        require(customerId.isNotBlank())
        return client.from("customers")
            .select(
                Columns.list(
                    "id",
                    "credit_limit",
                    "credit_hold",
                    "open_balance",
                    "currency",
                ),
            ) {
                filter { eq("id", customerId) }
                limit(1)
            }
            .decodeList<CustomerCreditRow>()
            .firstOrNull()
            ?.toSnapshot()
    }

    override suspend fun setCustomerCredit(
        customerId: String,
        creditLimit: Double?,
        creditHold: Boolean?,
    ): CustomerCreditSnapshot {
        require(customerId.isNotBlank())
        require(creditLimit != null || creditHold != null)
        val rows = client.postgrest.rpc(
            RpcNames.SET_CUSTOMER_CREDIT,
            buildJsonObject {
                put("p_customer_id", customerId)
                if (creditLimit == null) put("p_credit_limit", JsonNull)
                else put("p_credit_limit", creditLimit)
                if (creditHold == null) put("p_credit_hold", JsonNull)
                else put("p_credit_hold", creditHold)
            },
        ).decodeList<CustomerCreditRpcRow>()
        val row = rows.firstOrNull()
            ?: error("${RpcNames.SET_CUSTOMER_CREDIT} returned no row")
        return row.toSnapshot()
    }

    override suspend fun listFleetVehicles(status: FleetVehicleStatus?): List<FleetVehicleSummary> {
        return client.postgrest.rpc(
            RpcNames.LIST_FLEET_VEHICLES,
            buildJsonObject {
                if (status == null) put("p_status", JsonNull)
                else put("p_status", status.rpcValue)
            },
        ).decodeList<FleetVehicleRow>().map { it.toSummary() }
    }

    override suspend fun upsertFleetVehicle(
        plate: String,
        label: String?,
        status: FleetVehicleStatus,
        assignedDriverUserId: String?,
        notes: String?,
        id: String?,
    ): String {
        require(plate.isNotBlank()) { "plate is required" }
        return client.postgrest.rpc(
            RpcNames.UPSERT_FLEET_VEHICLE,
            buildJsonObject {
                put("p_plate", plate)
                if (label.isNullOrBlank()) put("p_label", JsonNull) else put("p_label", label)
                put("p_status", status.rpcValue)
                if (assignedDriverUserId.isNullOrBlank()) {
                    put("p_assigned_driver_user_id", JsonNull)
                } else {
                    put("p_assigned_driver_user_id", assignedDriverUserId)
                }
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
                if (id.isNullOrBlank()) put("p_id", JsonNull) else put("p_id", id)
            },
        ).decodeAs<String>()
    }

    override suspend fun setFleetVehicleStatus(id: String, status: FleetVehicleStatus): String {
        require(id.isNotBlank())
        return client.postgrest.rpc(
            RpcNames.SET_FLEET_VEHICLE_STATUS,
            buildJsonObject {
                put("p_id", id)
                put("p_status", status.rpcValue)
            },
        ).decodeAs<String>()
    }

    // --- HR onboarding ---

    override suspend fun listHrOnboardingDrafts(): List<HrOnboardingDraft> {
        return client.from("hr_onboarding_drafts")
            .select(
                Columns.list(
                    "id",
                    "employee_id",
                    "stage",
                    "payload",
                    "banking_json",
                    "health_json",
                    "completed_at",
                    "updated_at",
                ),
            ) {
                filter { exact("completed_at", null) }
                order("updated_at", Order.DESCENDING)
                limit(40)
            }
            .decodeList<HrOnboardingDraftRow>()
            .map { it.toSummary() }
    }

    override suspend fun listHrGrades(): List<HrGradeOption> {
        return client.from("hr_grades")
            .select(Columns.list("id", "code", "title", "sort_order")) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<HrGradeRow>()
            .map { it.toOption() }
    }

    override suspend fun listHrRoles(): List<HrRoleOption> {
        return client.from("hr_roles")
            .select(Columns.list("id", "title", "department", "grade_id")) {
                filter { eq("is_active", true) }
                order("title", Order.ASCENDING)
            }
            .decodeList<HrRoleRow>()
            .map { it.toOption() }
    }

    override suspend fun saveHrOnboardingStage(
        draftId: String?,
        stage: HrOnboardingStage,
        payload: Map<String, String?>,
        bankingJson: Map<String, String?>?,
        healthJson: Map<String, String?>?,
        employeeId: String?,
    ): String {
        return client.postgrest.rpc(
            RpcNames.SAVE_HR_ONBOARDING_STAGE,
            buildJsonObject {
                if (draftId.isNullOrBlank()) put("p_draft_id", JsonNull)
                else put("p_draft_id", draftId)
                put("p_stage", stage.rpcValue)
                put("p_payload", payload.toJsonObject())
                if (bankingJson == null) put("p_banking_json", JsonNull)
                else put("p_banking_json", bankingJson.toJsonObject())
                if (healthJson == null) put("p_health_json", JsonNull)
                else put("p_health_json", healthJson.toJsonObject())
                if (employeeId.isNullOrBlank()) put("p_employee_id", JsonNull)
                else put("p_employee_id", employeeId)
            },
        ).decodeAs<String>()
    }

    override suspend fun completeHrOnboarding(draftId: String): HrOnboardingCompleteResult {
        require(draftId.isNotBlank())
        val raw = client.postgrest.rpc(
            RpcNames.COMPLETE_HR_ONBOARDING,
            buildJsonObject { put("p_draft_id", draftId) },
        ).decodeAs<JsonObject>()
        // Never surface temp_password_hint in UI models.
        return HrOnboardingCompleteResult(
            draftId = raw.stringOrNull("draft_id"),
            employeeId = raw.stringOrNull("employee_id"),
            employeeCode = raw.stringOrNull("employee_code"),
            email = raw.stringOrNull("email"),
            phoneE164 = raw.stringOrNull("phone_e164"),
            userId = raw.stringOrNull("user_id"),
            mustChangePassword = raw["must_change_password"]?.jsonPrimitive?.booleanOrNull == true,
            message = raw.stringOrNull("message"),
        )
    }

    override suspend fun createHrOnboardingAuthUser(employeeId: String): HrOnboardingAuthResult {
        require(employeeId.isNotBlank())
        val response = client.functions.invoke(RpcNames.HR_ONBOARDING_CREATE_AUTH_FN) {
            setBody(
                buildJsonObject {
                    put("employee_id", employeeId)
                },
            )
        }
        val text = response.bodyAsText()
        val root = Json.parseToJsonElement(text).jsonObject
        val err = root.stringOrNull("error")
        if (!err.isNullOrBlank()) error(err)
        require(root["ok"]?.jsonPrimitive?.booleanOrNull == true) {
            "hr-onboarding-create-auth failed"
        }
        val userId = root.stringOrNull("user_id")
            ?: error("hr-onboarding-create-auth missing user_id")
        val channels = root["channels"]?.jsonArray?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            HrOnboardingAuthChannel(
                channel = o.stringOrNull("channel") ?: return@mapNotNull null,
                status = o.stringOrNull("status") ?: "unknown",
                error = o.stringOrNull("error"),
            )
        }.orEmpty()
        return HrOnboardingAuthResult(
            employeeId = root.stringOrNull("employee_id") ?: employeeId,
            userId = userId,
            created = root["created"]?.jsonPrimitive?.booleanOrNull == true,
            mustChangePassword = root["must_change_password"]?.jsonPrimitive?.booleanOrNull != false,
            channels = channels,
        )
    }

    companion object {
        private val UUID_REGEX =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

        fun create(supabaseUrl: String, supabaseAnonKey: String): SupabaseRpcClient {
            val client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseAnonKey,
            ) {
                install(Auth)
                install(Postgrest)
                install(Functions)
            }
            return SupabaseRpcClient(client)
        }

        private fun List<ConfirmPickLineInput>.toJsonArray(): JsonArray = buildJsonArray {
            forEach { line ->
                add(
                    buildJsonObject {
                        if (!line.pickListLineId.isNullOrBlank()) {
                            put("pick_list_line_id", line.pickListLineId)
                        }
                        if (!line.salesInvoiceLineId.isNullOrBlank()) {
                            put("sales_invoice_line_id", line.salesInvoiceLineId)
                        }
                        put("qty_picked", line.qtyPicked)
                    },
                )
            }
        }

        private fun List<ReceiptLineInput>.toReceiptJsonArray(): JsonArray = buildJsonArray {
            forEach { line ->
                add(
                    buildJsonObject {
                        put("stock_item_id", line.stockItemId)
                        put("uom_id", line.uomId)
                        put("qty", line.qty)
                        put("unit_cost", line.unitCost)
                        put("currency", line.currency.rpcValue)
                        put("valuation_method", line.valuationMethod.rpcValue)
                        val serials = line.serials
                        if (!serials.isNullOrEmpty()) {
                            put("serials", buildJsonArray { serials.forEach { add(JsonPrimitive(it)) } })
                        }
                    },
                )
            }
        }

        private fun List<TransferLineInput>.toTransferJsonArray(): JsonArray = buildJsonArray {
            forEach { line ->
                add(
                    buildJsonObject {
                        put("stock_item_id", line.stockItemId)
                        put("uom_id", line.uomId)
                        put("qty", line.qty)
                        put("valuation_method", line.valuationMethod.rpcValue)
                    },
                )
            }
        }
    }
}

@Serializable
private data class DeliveryNoteRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String,
    @SerialName("sales_invoice_id") val salesInvoiceId: String,
    val status: String,
)

@Serializable
private data class PickListRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String,
    @SerialName("sales_invoice_id") val salesInvoiceId: String,
    val status: String,
)

@Serializable
private data class StockItemRow(
    val id: String,
    @SerialName("base_uom_id") val baseUomId: String,
    @SerialName("oem_part_number") val oemPartNumber: String,
    val description: String? = null,
)

@Serializable
private data class StockItemIdRow(
    val id: String,
)

@Serializable
private data class StockLevelQtyRow(
    val quantity: Double = 0.0,
)

@Serializable
private data class StaffRoleRow(
    val role: String,
)

@Serializable
private data class ChatThreadRow(
    val id: String,
    val kind: String,
    val status: String,
    val subject: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    fun toSummary() = ChatThreadSummary(
        id = id,
        kind = kind,
        status = status,
        subject = subject,
        assignedTo = assignedTo,
        lastMessageAt = lastMessageAt,
        createdAt = createdAt,
    )
}

@Serializable
private data class ChatMessageRow(
    val id: String,
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_user_id") val senderUserId: String,
    @SerialName("sender_kind") val senderKind: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
) {
    fun toSummary() = ChatMessageSummary(
        id = id,
        threadId = threadId,
        senderUserId = senderUserId,
        senderKind = senderKind,
        body = body,
        createdAt = createdAt,
    )
}

@Serializable
private data class AssigneeSuggestionRow(
    @SerialName("user_id") val userId: String,
    val status: String,
    @SerialName("distance_m") val distanceM: Double? = null,
    val capacity: Int,
    @SerialName("open_jobs") val openJobs: Int,
    @SerialName("last_lat") val lastLat: Double? = null,
    @SerialName("last_lng") val lastLng: Double? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
) {
    fun toSummary() = DeliveryAssigneeSuggestion(
        userId = userId,
        status = status,
        distanceM = distanceM,
        capacity = capacity,
        openJobs = openJobs,
        lastLat = lastLat,
        lastLng = lastLng,
        lastSeenAt = lastSeenAt,
    )
}

@Serializable
private data class OptimizedStopRow(
    @SerialName("delivery_job_id") val deliveryJobId: String,
    @SerialName("route_sequence") val routeSequence: Int,
    @SerialName("distance_m") val distanceM: Double? = null,
) {
    fun toSummary() = OptimizedDriverStop(
        deliveryJobId = deliveryJobId,
        routeSequence = routeSequence,
        distanceM = distanceM,
    )
}

@Serializable
private data class TrackPointRow(
    @SerialName("delivery_job_id") val deliveryJobId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("eta_at") val etaAt: String? = null,
    @SerialName("eta_seconds") val etaSeconds: Int? = null,
    val status: String,
) {
    fun toSummary() = DeliveryTrackPoint(
        deliveryJobId = deliveryJobId,
        lat = lat,
        lng = lng,
        recordedAt = recordedAt,
        etaAt = etaAt,
        etaSeconds = etaSeconds,
        status = status,
    )
}

@Serializable
private data class UpdateDeliveryJobStatusRow(
    @SerialName("delivery_job_id") val deliveryJobId: String,
    @SerialName("track_token") val trackToken: String? = null,
)

@Serializable
private data class PanicEventRow(
    val id: String,
    @SerialName("driver_user_id") val driverUserId: String,
    @SerialName("delivery_job_id") val deliveryJobId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
    @SerialName("acknowledged_by") val acknowledgedBy: String? = null,
) {
    fun toSummary() = PanicEventSummary(
        id = id,
        driverUserId = driverUserId,
        deliveryJobId = deliveryJobId,
        lat = lat,
        lng = lng,
        createdAt = createdAt,
        acknowledgedAt = acknowledgedAt,
        acknowledgedBy = acknowledgedBy,
    )
}

@Serializable
private data class PanicAckUpdate(
    @SerialName("acknowledged_at") val acknowledgedAt: String,
    @SerialName("acknowledged_by") val acknowledgedBy: String,
)

@Serializable
private data class SalesInvoiceContactRow(
    val id: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("customer_email") val customerEmail: String? = null,
    @SerialName("customer_whatsapp_e164") val customerWhatsappE164: String? = null,
)

@Serializable
private data class PosCartCustomerRow(
    @SerialName("customer_id") val customerId: String? = null,
)

@Serializable
private data class WarehouseRow(
    val id: String,
    val code: String,
    val name: String,
)

@Serializable
private data class PosScanSessionRow(
    @SerialName("session_id") val sessionId: String,
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
private data class PosScanSessionCartRow(
    @SerialName("cart_id") val cartId: String,
)

@Serializable
private data class PosCartVehicleRow(
    @SerialName("vehicle_model_slug") val modelSlug: String? = null,
    @SerialName("vehicle_model_name") val modelName: String? = null,
    @SerialName("vehicle_generation") val generation: String? = null,
    @SerialName("vehicle_chassis_code") val chassisCode: String? = null,
    @SerialName("vehicle_engine_code") val engineCode: String? = null,
)

@Serializable
private data class PosQuotationRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("warehouse_id") val warehouseId: String,
    val currency: String,
    val status: String,
    @SerialName("valid_until") val validUntil: String? = null,
    @SerialName("sent_channel") val sentChannel: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("line_count") val lineCount: Long = 0,
    val total: Double = 0.0,
)

@Serializable
private data class PopularPosSpareRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("oem_part_number") val oemPartNumber: String,
    val description: String? = null,
    @SerialName("units_sold") val unitsSold: Double = 0.0,
    @SerialName("saleable_qty") val saleableQty: Double = 0.0,
    @SerialName("unit_price") val unitPrice: Double? = null,
    val currency: String? = null,
)

@Serializable
private data class PosInvoiceRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("customer_name") val customerName: String? = null,
    val total: Double = 0.0,
    val currency: String,
    @SerialName("posted_at") val postedAt: String? = null,
    @SerialName("vehicle_model_name") val vehicleModelName: String? = null,
    @SerialName("vehicle_generation") val vehicleGeneration: String? = null,
    @SerialName("vehicle_chassis_code") val vehicleChassisCode: String? = null,
    @SerialName("vehicle_engine_code") val vehicleEngineCode: String? = null,
)

@Serializable
private data class PosCartLineRow(
    val id: String,
    @SerialName("stock_item_id") val stockItemId: String,
    val qty: Double,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("line_total") val lineTotal: Double,
    @SerialName("is_core_charge") val isCoreCharge: Boolean = false,
)

@Serializable
private data class PosCartLineQtyUpdate(
    val qty: Double,
    @SerialName("line_total") val lineTotal: Double,
)

@Serializable
private data class StockItemOemRow(
    val id: String,
    @SerialName("oem_part_number") val oemPartNumber: String,
)

@Serializable
private data class CustomerOptionRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
private data class SupplierRow(
    val id: String,
    val code: String,
    val name: String,
)

@Serializable
private data class BlanketPoRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String,
    val status: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("warehouse_id") val warehouseId: String,
    val currency: String,
    @SerialName("blanket_max_value") val blanketMaxValue: Double? = null,
    @SerialName("blanket_value_released") val blanketValueReleased: Double? = null,
    @SerialName("expected_date") val expectedDate: String? = null,
)

@Serializable
private data class BlanketLineRow(
    val id: String,
    @SerialName("purchase_order_id") val purchaseOrderId: String,
    @SerialName("line_no") val lineNo: Int,
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("qty_ordered") val qtyOrdered: Double,
    @SerialName("qty_released") val qtyReleased: Double = 0.0,
    @SerialName("unit_price") val unitPrice: Double,
    val currency: String,
)

@Serializable
private data class WarehouseBinRow(
    val id: String,
    @SerialName("warehouse_id") val warehouseId: String,
    val code: String,
    val name: String,
    @SerialName("pick_path_seq") val pickPathSeq: Int = 100,
    val aisle: String? = null,
    val rack: String? = null,
    val shelf: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
) {
    fun toSummary() = WarehouseBinSummary(
        id = id,
        warehouseId = warehouseId,
        code = code,
        name = name,
        pickPathSeq = pickPathSeq,
        aisle = aisle,
        rack = rack,
        shelf = shelf,
        isActive = isActive,
    )
}

@Serializable
private data class PickPathHintRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("oem_part_number") val oemPartNumber: String? = null,
    val quantity: Double = 0.0,
    @SerialName("bin_id") val binId: String? = null,
    @SerialName("bin_code") val binCode: String? = null,
    @SerialName("bin_name") val binName: String? = null,
    @SerialName("pick_path_seq") val pickPathSeq: Int? = null,
    val aisle: String? = null,
    val rack: String? = null,
    val shelf: String? = null,
) {
    fun toSummary() = PickPathHint(
        stockItemId = stockItemId,
        oemPartNumber = oemPartNumber,
        quantity = quantity,
        binId = binId,
        binCode = binCode,
        binName = binName,
        pickPathSeq = pickPathSeq,
        aisle = aisle,
        rack = rack,
        shelf = shelf,
    )
}

@Serializable
private data class ConsignmentEntryRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String,
    val status: String,
    val kind: String,
    val purpose: String,
    @SerialName("warehouse_id") val warehouseId: String,
    @SerialName("supplier_id") val supplierId: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    val currency: String = "USD",
) {
    fun toSummary() = ConsignmentEntrySummary(
        id = id,
        documentNumber = documentNumber,
        status = status,
        kind = kind,
        purpose = purpose,
        warehouseId = warehouseId,
        supplierId = supplierId,
        customerId = customerId,
        currency = CurrencyCode.entries.find { it.rpcValue == currency } ?: CurrencyCode.USD,
    )
}

@Serializable
private data class CustomerCreditRow(
    val id: String,
    @SerialName("credit_limit") val creditLimit: Double = 0.0,
    @SerialName("credit_hold") val creditHold: Boolean = false,
    @SerialName("open_balance") val openBalance: Double = 0.0,
    val currency: String? = null,
) {
    fun toSnapshot() = CustomerCreditSnapshot(
        customerId = id,
        creditLimit = creditLimit,
        creditHold = creditHold,
        openBalance = openBalance,
        currency = CurrencyCode.entries.find { it.rpcValue == currency } ?: CurrencyCode.USD,
    )
}

@Serializable
private data class CustomerCreditRpcRow(
    @SerialName("customer_id") val customerId: String,
    @SerialName("credit_limit") val creditLimit: Double = 0.0,
    @SerialName("credit_hold") val creditHold: Boolean = false,
    @SerialName("open_balance") val openBalance: Double = 0.0,
    val currency: String = "USD",
) {
    fun toSnapshot() = CustomerCreditSnapshot(
        customerId = customerId,
        creditLimit = creditLimit,
        creditHold = creditHold,
        openBalance = openBalance,
        currency = CurrencyCode.entries.find { it.rpcValue == currency } ?: CurrencyCode.USD,
    )
}

@Serializable
private data class FleetVehicleRow(
    val id: String,
    val plate: String,
    val label: String? = null,
    val status: String,
    @SerialName("assigned_driver_user_id") val assignedDriverUserId: String? = null,
    val notes: String? = null,
) {
    fun toSummary() = FleetVehicleSummary(
        id = id,
        plate = plate,
        label = label,
        status = FleetVehicleStatus.fromRpc(status),
        assignedDriverUserId = assignedDriverUserId,
        notes = notes,
    )
}

@Serializable
private data class HrOnboardingDraftRow(
    val id: String,
    @SerialName("employee_id") val employeeId: String? = null,
    val stage: String,
    val payload: JsonObject? = null,
    @SerialName("banking_json") val bankingJson: JsonObject? = null,
    @SerialName("health_json") val healthJson: JsonObject? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
) {
    fun toSummary() = HrOnboardingDraft(
        id = id,
        employeeId = employeeId,
        stage = HrOnboardingStage.fromRpc(stage),
        payload = payload?.toStringMap().orEmpty(),
        bankingJson = bankingJson?.toStringMap(),
        healthJson = healthJson?.toStringMap(),
        completedAt = completedAt,
        updatedAt = updatedAt,
    )
}

@Serializable
private data class HrGradeRow(
    val id: String,
    val code: String,
    val title: String,
    @SerialName("sort_order") val sortOrder: Int = 100,
) {
    fun toOption() = HrGradeOption(id = id, code = code, title = title, sortOrder = sortOrder)
}

@Serializable
private data class HrRoleRow(
    val id: String,
    val title: String,
    val department: String? = null,
    @SerialName("grade_id") val gradeId: String? = null,
) {
    fun toOption() = HrRoleOption(id = id, title = title, department = department, gradeId = gradeId)
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.toStringMap(): Map<String, String?> =
    entries.associate { (k, v) ->
        k to when (v) {
            is JsonPrimitive -> v.contentOrNull
            JsonNull -> null
            else -> v.toString()
        }
    }

private fun Map<String, String?>.toJsonObject(): JsonObject = buildJsonObject {
    forEach { (k, v) ->
        if (v == null) put(k, JsonNull) else put(k, v)
    }
}

private fun parseCatalogSearchResult(
    raw: kotlinx.serialization.json.JsonObject,
    fallbackMode: CatalogSearchMode,
    fallbackQuery: String,
): CatalogSearchResult {
    val modeStr = raw["mode"]?.let {
        (it as? JsonPrimitive)?.content
    }
    val mode = CatalogSearchMode.entries.find { it.rpcValue == modeStr } ?: fallbackMode
    val query = raw["query"]?.let { (it as? JsonPrimitive)?.content } ?: fallbackQuery
    val results = raw["results"] as? JsonArray ?: JsonArray(emptyList())
    val parts = mutableListOf<CatalogPartHit>()
    for (el in results) {
        val obj = el as? kotlinx.serialization.json.JsonObject ?: continue
        collectPartHits(obj, parts)
    }
    return CatalogSearchResult(mode = mode, query = query, parts = parts.distinctBy { it.oemPartNumber })
}

private fun collectPartHits(
    obj: kotlinx.serialization.json.JsonObject,
    out: MutableList<CatalogPartHit>,
) {
    val type = (obj["type"] as? JsonPrimitive)?.content
    when (type) {
        "part" -> {
            val oem = (obj["oem_part_number"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (oem.isNotEmpty()) {
                out.add(
                    CatalogPartHit(
                        oemPartNumber = oem,
                        description = (obj["description"] as? JsonPrimitive)?.contentOrNull,
                        pncCode = (obj["pnc_code"] as? JsonPrimitive)?.content,
                        categoryName = (obj["category_name"] as? JsonPrimitive)?.content,
                        subcategoryName = (obj["subcategory_name"] as? JsonPrimitive)?.content,
                        chassisCode = (obj["chassis_code"] as? JsonPrimitive)?.content,
                        engineCode = (obj["engine_code"] as? JsonPrimitive)?.content,
                    ),
                )
            }
        }
        "vehicle", "pnc" -> {
            val fitments = obj["fitments"] as? JsonArray ?: return
            for (f in fitments) {
                val fo = f as? kotlinx.serialization.json.JsonObject ?: continue
                collectPartHits(fo, out)
            }
        }
    }
}
