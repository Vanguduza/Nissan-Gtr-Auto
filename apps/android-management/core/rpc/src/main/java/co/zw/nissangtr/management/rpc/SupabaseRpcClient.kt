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
import kotlinx.serialization.json.JsonElement
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
private const val CATALOG_LIVE_FN = "catalog-live-r2"

class SupabaseRpcClient(
    val client: SupabaseClient,
    private val projectUrl: String? = null,
) : RpcClient, ManagerApproval {

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

        // Operator POS promises part-name and partial-OEM discovery. The canonical catalog RPC is
        // fitment-centric, so merge the shop's stock (`search_pos_stock_items`: part number and
        // every description word, same rule as the web POS) without replacing its authority.
        val stock = client.postgrest.rpc(
            RpcNames.SEARCH_POS_STOCK_ITEMS,
            buildJsonObject {
                put("p_query", q)
                put("p_limit", 50)
            },
        ).decodeAs<kotlinx.serialization.json.JsonObject>()
        val stockHits = parseCatalogSearchResult(stock, CatalogSearchMode.PART, q).parts
        val seen = stockHits.map { it.oemPartNumber.trim().uppercase() }.toSet()
        return canonical.copy(
            parts = (stockHits + canonical.parts.filterNot { it.oemPartNumber.trim().uppercase() in seen }).take(50),
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
        return withDiagramUrl(parseEpcDiagram(raw))
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
        return withDiagramUrl(parseEpcDiagram(raw))
    }

    /**
     * Seeded and pipeline diagrams carry only `storage_path`; resolve it to the public
     * `catalog-diagrams` object so the tablet (and the offline catalogue sync) can fetch the image.
     */
    private fun withDiagramUrl(d: EpcDiagramResponse): EpcDiagramResponse {
        if (!d.imageUrl.isNullOrBlank()) return d
        val path = d.storagePath?.trim()?.takeIf { it.isNotEmpty() } ?: return d
        val url = if (path.startsWith("http://", true) || path.startsWith("https://", true)) path
        else projectUrl?.let { base -> "$base/storage/v1/object/public/catalog-diagrams/${path.trimStart('/')}" }
        return d.copy(imageUrl = url)
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

        // Merchandising labels/images are optional — soft-fail so the authoritative cart remains usable.
        val itemMeta = mutableMapOf<String, StockItemOemRow>()
        val imageByItem = mutableMapOf<String, String>()
        rows.map { it.stockItemId }.distinct().take(40).forEach { itemId ->
            runCatching {
                client.from("stock_items")
                    .select(Columns.list("id", "oem_part_number", "description")) {
                        filter { eq("id", itemId) }
                        limit(1)
                    }
                    .decodeList<StockItemOemRow>()
                    .firstOrNull()
                    ?.let { itemMeta[it.id] = it }
            }
            runCatching {
                client.from("stock_item_images")
                    .select(Columns.list("storage_path", "is_primary", "sort_order")) {
                        filter { eq("stock_item_id", itemId) }
                        order("is_primary", Order.DESCENDING)
                        order("sort_order", Order.ASCENDING)
                        limit(1)
                    }
                    .decodeList<StockItemImagePathRow>()
                    .firstOrNull()?.storagePath?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                        imageByItem[itemId] = if (path.startsWith("http://", true) || path.startsWith("https://", true)) {
                            path
                        } else {
                            projectUrl?.let { base -> "$base/storage/v1/object/public/product-images/${path.trimStart('/')}" } ?: path
                        }
                    }
            }
        }

        return rows.map { row ->
            PosCartLineSummary(
                id = row.id,
                stockItemId = row.stockItemId,
                oemPartNumber = itemMeta[row.stockItemId]?.oemPartNumber,
                qty = row.qty,
                unitPrice = row.unitPrice,
                lineTotal = row.lineTotal,
                isCoreCharge = row.isCoreCharge,
                description = itemMeta[row.stockItemId]?.description,
                imageUrl = imageByItem[row.stockItemId],
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

    @Volatile private var vehicleMasterCache: List<VehicleMasterEntry>? = null

    // The API returns at most 1000 rows per call; the published master is larger, so page.
    override suspend fun listVehicleMaster(): List<VehicleMasterEntry> =
        vehicleMasterCache ?: buildList {
            var offset = 0
            while (true) {
                val page = client.postgrest.rpc(
                    "list_customer_vehicle_master",
                    buildJsonObject {
                        put("p_maker", "nissan")
                        put("p_limit", VEHICLE_MASTER_PAGE)
                        put("p_offset", offset)
                    },
                ).decodeList<VehicleMasterRpcRow>()
                addAll(page)
                if (page.size < VEHICLE_MASTER_PAGE) break
                offset += page.size
            }
        }.map {
            VehicleMasterEntry(
                id = it.id,
                modelFamily = it.modelFamily,
                chassisCode = it.chassisCode,
                engineCode = it.engineCode,
                yearStart = it.yearStart,
                yearEnd = it.yearEnd,
                salesRegion = it.salesRegion,
            )
        }.also { vehicleMasterCache = it }

    override suspend fun catalogLive(action: String, params: Map<String, String>): JsonObject {
        val response = try {
            client.functions.invoke(CATALOG_LIVE_FN) {
                setBody(
                    buildJsonObject {
                        put("action", action)
                        put("maker", "nissan")
                        params.forEach { (k, v) -> put(k, v) }
                    },
                )
            }
        } catch (e: io.github.jan.supabase.exceptions.RestException) {
            // Non-2xx: the gateway's JSON body ({error, status}) is carried in the exception text.
            val text = listOfNotNull(e.error, e.description, e.message).joinToString(" ")
            throw CatalogLiveException(
                httpStatus = e.statusCode,
                catalogStatus = if ("CATALOG_REPUBLISH_REQUIRED" in text) "CATALOG_REPUBLISH_REQUIRED" else null,
                message = e.error,
            )
        }
        val root = Json.parseToJsonElement(response.bodyAsText()) as? JsonObject
            ?: throw CatalogLiveException(502, null, "invalid live catalogue response")
        root.stringOrNull("error")?.takeIf { it.isNotBlank() }?.let {
            throw CatalogLiveException(response.status.value, root.stringOrNull("status"), it)
        }
        return root
    }

    override suspend fun getPosScanSessionStatus(sessionId: String): String? {
        require(sessionId.isNotBlank())
        return client.from("pos_scan_sessions")
            .select(Columns.list("status")) {
                filter { eq("id", sessionId) }
                limit(1)
            }
            .decodeList<PosScanSessionStatusRow>()
            .firstOrNull()
            ?.status
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
                imageUrl = row.imageStoragePath?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                    if (path.startsWith("http://", true) || path.startsWith("https://", true)) path
                    else projectUrl?.let { base -> "$base/storage/v1/object/public/product-images/${path.trimStart('/')}" }
                },
            )
        }
    }

    override suspend fun listPosPopularPins(): List<PosPopularPin> {
        val rows = client.postgrest.rpc(RpcNames.LIST_POS_POPULAR_PINS).decodeList<PosPopularPinRow>()
        return rows.map { row ->
            PosPopularPin(
                kind = PosPopularItemKind.fromRpc(row.itemType),
                itemKey = row.itemKey,
                label = row.label,
                subtitle = row.subtitle,
                searchQuery = row.searchQuery,
                makerSlug = row.makerSlug,
                modelSlug = row.modelSlug,
                categoryName = row.categoryName,
                subcategoryName = row.subcategoryName,
                oemPartNumber = row.oemPartNumber,
                imageUrl = row.imageUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                    if (path.startsWith("http://", true) || path.startsWith("https://", true)) path
                    else if (PosPopularItemKind.fromRpc(row.itemType) == PosPopularItemKind.PART) {
                        projectUrl?.let { base -> "$base/storage/v1/object/public/product-images/${path.trimStart('/')}" } ?: path
                    } else path
                },
                updatedAt = row.updatedAt,
            )
        }
    }

    override suspend fun upsertPosPopularPin(pin: PosPopularPin): String =
        client.postgrest.rpc(
            RpcNames.UPSERT_POS_POPULAR_PIN,
            buildJsonObject {
                put("p_item_type", pin.kind.rpcValue)
                put("p_item_key", pin.itemKey)
                put("p_label", pin.label)
                if (pin.subtitle.isNullOrBlank()) put("p_subtitle", JsonNull) else put("p_subtitle", pin.subtitle)
                put("p_search_query", pin.searchQuery)
                if (pin.makerSlug.isNullOrBlank()) put("p_maker_slug", JsonNull) else put("p_maker_slug", pin.makerSlug)
                if (pin.modelSlug.isNullOrBlank()) put("p_model_slug", JsonNull) else put("p_model_slug", pin.modelSlug)
                if (pin.categoryName.isNullOrBlank()) put("p_category_name", JsonNull) else put("p_category_name", pin.categoryName)
                if (pin.subcategoryName.isNullOrBlank()) put("p_subcategory_name", JsonNull) else put("p_subcategory_name", pin.subcategoryName)
                if (pin.oemPartNumber.isNullOrBlank()) put("p_oem_part_number", JsonNull) else put("p_oem_part_number", pin.oemPartNumber)
                if (pin.imageUrl.isNullOrBlank()) put("p_image_url", JsonNull) else put("p_image_url", pin.imageUrl)
            },
        ).decodeAs<String>()

    override suspend fun deletePosPopularPin(kind: PosPopularItemKind, itemKey: String): Boolean =
        client.postgrest.rpc(
            RpcNames.DELETE_POS_POPULAR_PIN,
            buildJsonObject {
                put("p_item_type", kind.rpcValue)
                put("p_item_key", itemKey)
            },
        ).decodeAs<Boolean>()

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
                        putJsonArray("vehicle_contexts") {
                            payload.vehicleContexts.distinctBy { v ->
                                "${v.modelSlug}|${v.chassisCode}|${v.engineCode}"
                            }.forEach { v ->
                                add(
                                    buildJsonObject {
                                        put("model_slug", v.modelSlug)
                                        put("model_name", v.modelName)
                                        put("generation", v.generation)
                                        put("chassis_code", v.chassisCode)
                                        put("engine_code", v.engineCode)
                                    },
                                )
                            }
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
            signInWithEmail(email, password)
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

    override suspend fun hydratePosParts(oemPartNumbers: List<String>): List<PosPartMeta> {
        val oems = oemPartNumbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(60)
        if (oems.isEmpty()) return emptyList()
        val items = client.from("stock_items")
            .select(Columns.list("id", "base_uom_id", "oem_part_number", "description")) {
                filter { isIn("oem_part_number", oems) }
            }
            .decodeList<StockItemRow>()
        if (items.isEmpty()) return emptyList()
        val ids = items.map { it.id }
        val prices = client.from("price_list_items")
            .select(Columns.raw("stock_item_id, unit_price, price_lists!inner(currency, is_default, is_active)")) {
                filter {
                    isIn("stock_item_id", ids)
                    eq("price_lists.is_default", true)
                    eq("price_lists.is_active", true)
                }
            }
            .decodeList<PosPriceRow>()
            .groupBy { it.stockItemId }
            .mapValues { it.value.first() }
        val levels = client.from("stock_levels")
            .select(Columns.raw("stock_item_id, quantity, warehouses!inner(is_active, is_quarantine)")) {
                filter {
                    isIn("stock_item_id", ids)
                    eq("warehouses.is_active", true)
                    eq("warehouses.is_quarantine", false)
                }
            }
            .decodeList<PosLevelRow>()
            .groupBy { it.stockItemId }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
        val images = client.from("stock_item_images")
            .select(Columns.list("stock_item_id", "storage_path", "is_primary", "sort_order")) {
                filter { isIn("stock_item_id", ids) }
                order("is_primary", Order.DESCENDING)
                order("sort_order", Order.ASCENDING)
            }
            .decodeList<PosImageRow>()
            .groupBy { it.stockItemId }
            .mapValues { it.value.first().storagePath }
        return items.map { row ->
            val price = prices[row.id]
            PosPartMeta(
                stockItemId = row.id,
                uomId = row.baseUomId,
                oemPartNumber = row.oemPartNumber,
                description = row.description,
                unitPrice = price?.unitPrice,
                currency = price?.priceList?.currency?.let { c -> CurrencyCode.entries.find { it.rpcValue == c } },
                saleableQty = levels[row.id] ?: 0.0,
                imageUrl = images[row.id]?.let(::productImageUrl),
            )
        }
    }

    private fun productImageUrl(path: String): String? {
        val trimmed = path.trim().takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) return trimmed
        return projectUrl?.let { base -> "$base/storage/v1/object/public/product-images/${trimmed.trimStart('/')}" }
    }

    override suspend fun listPosHiddenBestsellers(): List<String> =
        client.postgrest.rpc(RpcNames.LIST_POS_HIDDEN_BESTSELLERS)
            .decodeList<PosHiddenRow>()
            .map { it.stockItemId }

    override suspend fun hidePosBestseller(stockItemId: String): Boolean =
        client.postgrest.rpc(
            RpcNames.HIDE_POS_BESTSELLER,
            buildJsonObject { put("p_stock_item_id", stockItemId) },
        ).decodeAs<Boolean>()

    override suspend fun unhidePosBestseller(stockItemId: String): Boolean =
        client.postgrest.rpc(
            RpcNames.UNHIDE_POS_BESTSELLER,
            buildJsonObject { put("p_stock_item_id", stockItemId) },
        ).decodeAs<Boolean>()

    override suspend fun currentStaffDisplayName(): String? {
        val uid = currentUserId() ?: return null
        val name = client.from("profiles")
            .select(Columns.list("full_name")) { filter { eq("id", uid) } }
            .decodeList<PosProfileNameRow>()
            .firstOrNull()
            ?.fullName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return name ?: currentUserEmail()?.substringBefore('@')
    }

    override suspend fun listPosParkedCarts(limit: Int): List<PosParkedCart> =
        client.from("pos_carts")
            .select(Columns.raw("id, document_number, updated_at, currency, pos_cart_lines ( line_total )")) {
                filter {
                    eq("status", "parked")
                    eq("channel", "pos")
                }
                order("updated_at", Order.DESCENDING)
                limit(limit.toLong().coerceIn(1, 100))
            }
            .decodeList<PosParkedCartRow>()
            .map { row ->
                PosParkedCart(
                    id = row.id,
                    documentNumber = row.documentNumber,
                    updatedAt = row.updatedAt,
                    currency = CurrencyCode.entries.find { it.rpcValue == row.currency } ?: CurrencyCode.USD,
                    total = row.lines.sumOf { it.lineTotal },
                    lineCount = row.lines.size,
                )
            }

    override suspend fun posCartCurrency(cartId: String): CurrencyCode? =
        client.from("pos_carts")
            .select(Columns.list("currency")) { filter { eq("id", cartId) } }
            .decodeList<PosCartCurrencyRow>()
            .firstOrNull()
            ?.currency
            ?.let { c -> CurrencyCode.entries.find { it.rpcValue == c } }

    override suspend fun salesInvoiceDocumentNumber(invoiceId: String): String? =
        client.from("sales_invoices")
            .select(Columns.list("document_number")) { filter { eq("id", invoiceId) } }
            .decodeList<PosInvoiceDocRow>()
            .firstOrNull()
            ?.documentNumber

    suspend fun endStaffPortalSession() {
        val attendant = staffPortalAttendantSession ?: return
        try {
            auth.importSession(attendant)
        } finally {
            staffPortalAttendantSession = null
        }
    }

    fun isStaffPortalSessionActive(): Boolean = staffPortalAttendantSession != null

    override suspend fun <T> withManagerApproval(
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
        return client.postgrest.rpc(
            RpcNames.LIST_POS_CUSTOMERS,
            buildJsonObject {
                put("p_query", q)
                put("p_limit", 30)
            },
        ).decodeList<PosCustomerRow>().map { it.toModel() }
    }

    override suspend fun createPosCustomer(
        kind: PosCustomerKind,
        displayName: String,
        businessName: String?,
        email: String?,
        phoneE164: String?,
        whatsappE164: String?,
    ): String = client.postgrest.rpc(
        RpcNames.CREATE_POS_CUSTOMER,
        buildJsonObject {
            put("p_customer_kind", kind.rpcValue)
            put("p_display_name", displayName.trim())
            if (businessName.isNullOrBlank()) put("p_business_name", JsonNull) else put("p_business_name", businessName.trim())
            if (email.isNullOrBlank()) put("p_email", JsonNull) else put("p_email", email.trim())
            if (phoneE164.isNullOrBlank()) put("p_phone_e164", JsonNull) else put("p_phone_e164", phoneE164.trim())
            if (whatsappE164.isNullOrBlank()) put("p_whatsapp_e164", JsonNull) else put("p_whatsapp_e164", whatsappE164.trim())
        },
    ).decodeAs<String>()

    override suspend fun updatePosCustomer(
        customerId: String,
        kind: PosCustomerKind,
        displayName: String,
        businessName: String?,
        email: String?,
        phoneE164: String?,
        whatsappE164: String?,
    ) {
        client.postgrest.rpc(
            RpcNames.UPDATE_POS_CUSTOMER,
            buildJsonObject {
                put("p_customer_id", customerId)
                put("p_customer_kind", kind.rpcValue)
                put("p_display_name", displayName.trim())
                if (businessName.isNullOrBlank()) put("p_business_name", JsonNull) else put("p_business_name", businessName.trim())
                if (email.isNullOrBlank()) put("p_email", JsonNull) else put("p_email", email.trim())
                if (phoneE164.isNullOrBlank()) put("p_phone_e164", JsonNull) else put("p_phone_e164", phoneE164.trim())
                if (whatsappE164.isNullOrBlank()) put("p_whatsapp_e164", JsonNull) else put("p_whatsapp_e164", whatsappE164.trim())
            },
        ).decodeAs<String>()
    }

    override suspend fun listPosCustomerGarage(customerId: String): List<CustomerGarageVehicle> =
        client.postgrest.rpc(
            RpcNames.LIST_POS_CUSTOMER_GARAGE,
            buildJsonObject { put("p_customer_id", customerId) },
        ).decodeList<PosCustomerGarageRow>().map { it.toModel() }

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
    ): String = client.postgrest.rpc(
        RpcNames.UPSERT_POS_CUSTOMER_GARAGE_VEHICLE,
        buildJsonObject {
            put("p_customer_id", customerId)
            if (vehicleId.isNullOrBlank()) put("p_vehicle_id", JsonNull) else put("p_vehicle_id", vehicleId)
            put("p_model_slug", modelSlug.trim())
            put("p_make", make.trim())
            put("p_model", model.trim())
            put("p_generation", generation.trim())
            put("p_chassis_code", chassisCode.trim())
            put("p_engine", engine.trim())
            if (vin.isNullOrBlank()) put("p_vin", JsonNull) else put("p_vin", vin.trim())
            put("p_is_primary", isPrimary)
        },
    ).decodeAs<String>()

    override suspend fun setPosCartCustomer(cartId: String, customerId: String?) {
        client.postgrest.rpc(
            RpcNames.SET_POS_CART_CUSTOMER,
            buildJsonObject {
                put("p_cart_id", cartId)
                if (customerId.isNullOrBlank()) put("p_customer_id", JsonNull) else put("p_customer_id", customerId)
            },
        ).decodeAs<String>()
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
            return SupabaseRpcClient(client, supabaseUrl.trimEnd('/'))
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

    // --- POS reserve-first checkout

    override suspend fun preparePosCommerceCheckout(cartId: String, checkoutRequestId: String, receiptEmail: String?, receiptWhatsappE164: String?): String =
        client.postgrest.rpc(
            "prepare_pos_commerce_checkout_v2",
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_checkout_request_id", checkoutRequestId)
                put("p_reservation_ttl", "20 minutes")
                if (receiptEmail.isNullOrBlank()) put("p_receipt_email", JsonNull) else put("p_receipt_email", receiptEmail)
                if (receiptWhatsappE164.isNullOrBlank()) put("p_receipt_whatsapp_e164", JsonNull) else put("p_receipt_whatsapp_e164", receiptWhatsappE164)
                put("p_receipt_phone_e164", JsonNull)
            },
        ).decodeAs<String>()

    override suspend fun posPaymentStatus(orderId: String): PosPaymentStatus {
        val o = client.postgrest.rpc("get_pos_payment_status", buildJsonObject { put("p_order_id", orderId) }).decodeAs<JsonObject>()
        return PosPaymentStatus(
            orderId = o.stringOrNull("order_id") ?: orderId,
            cartId = o.stringOrNull("cart_id"),
            state = o.stringOrNull("state") ?: "",
            total = o.number("total") ?: 0.0,
            currency = CurrencyCode.entries.find { it.rpcValue == o.stringOrNull("currency") } ?: CurrencyCode.USD,
            reservationExpiresAt = o.stringOrNull("reservation_expires_at"),
            activeProvider = o.stringOrNull("active_provider"),
            activeIntentId = o.stringOrNull("active_intent_id"),
            providerStatus = o.stringOrNull("provider_status"),
            providerFailure = o.stringOrNull("provider_failure"),
            settledProvider = o.stringOrNull("settled_provider"),
            settledProviderRef = o.stringOrNull("settled_provider_ref"),
            salesInvoiceId = o.stringOrNull("sales_invoice_id"),
            paymentException = o.stringOrNull("payment_exception"),
            exceptions = (o["exceptions"] as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                PosPaymentExceptionRow(x.stringOrNull("code") ?: "", x.stringOrNull("detail"), x.stringOrNull("resolved_at"), x.stringOrNull("resolution"), x.stringOrNull("created_at") ?: "")
            },
        )
    }

    override suspend fun settlePosCommerceTenders(orderId: String, paymentRequestId: String, tenders: List<PosTenderLine>): String {
        val o = client.postgrest.rpc(
            "settle_pos_commerce_tenders",
            buildJsonObject {
                put("p_order_id", orderId)
                put("p_payment_request_id", paymentRequestId)
                putJsonArray("p_tenders") {
                    tenders.forEach { t -> add(buildJsonObject { put("tender", t.tender); put("amount", t.amount) }) }
                }
            },
        ).decodeAs<JsonObject>()
        return o.stringOrNull("invoice_id") ?: error("payment was not recorded")
    }

    override suspend fun posProviderAvailability(provider: String): String? = try {
        // A provider without keys answers 503 before it reads the body, so an empty probe is harmless.
        client.functions.invoke("$provider-initiate") { setBody(buildJsonObject { }) }
        null
    } catch (e: io.github.jan.supabase.exceptions.RestException) {
        when (e.statusCode) {
            503 -> "Not set up for this shop yet."
            401 -> "Sign in again to use this provider."
            else -> null // 400: configured, it just wants a real request
        }
    } catch (e: Exception) {
        "Provider unreachable."
    }

    override suspend fun startPosProviderPayment(orderId: String, provider: String, msisdn: String?, method: String?, returnUrl: String): PosProviderStart {
        val body = buildJsonObject {
            put("pos_commerce_order_id", orderId)
            put("channel", "pos")
            put("metadata", buildJsonObject { put("source", "tablet_pos") })
            if (provider == "ecocash") {
                put("payer_msisdn", msisdn ?: "")
            } else {
                put("method", method ?: "ecocash")
                put("return_url", returnUrl)
                put("cancel_url", returnUrl)
                if (!msisdn.isNullOrBlank()) {
                    put("phone", msisdn)
                    put("authphone", msisdn)
                }
            }
        }
        val text = try {
            client.functions.invoke("$provider-initiate") { setBody(body) }.bodyAsText()
        } catch (e: io.github.jan.supabase.exceptions.RestException) {
            // The intent may exist even when the provider call failed; the order tracks it for recovery.
            val o = runCatching { Json.parseToJsonElement(e.error) as? JsonObject }.getOrNull()
            o?.stringOrNull("intent_id")?.let { return PosProviderStart(it, null, o.stringOrNull("error")) }
            throw IllegalStateException(o?.stringOrNull("error") ?: e.error)
        }
        val o = Json.parseToJsonElement(text) as? JsonObject ?: error("The payment request was not sent.")
        val intent = o.stringOrNull("intent_id") ?: error(o.stringOrNull("error") ?: "The payment request was not sent.")
        return PosProviderStart(intent, o.stringOrNull("checkout_url"), o.stringOrNull("message"))
    }

    override suspend fun cancelPosCommerceCheckout(orderId: String, reason: String) {
        client.postgrest.rpc("cancel_pos_commerce_checkout", buildJsonObject { put("p_order_id", orderId); put("p_reason", reason) })
    }

    override suspend fun checkoutPosCartOnAccount(cartId: String, receiptEmail: String?, receiptWhatsappE164: String?): String =
        client.postgrest.rpc(
            "checkout_pos_cart_on_account",
            buildJsonObject {
                put("p_cart_id", cartId)
                if (receiptEmail.isNullOrBlank()) put("p_receipt_email", JsonNull) else put("p_receipt_email", receiptEmail)
                if (receiptWhatsappE164.isNullOrBlank()) put("p_receipt_whatsapp_e164", JsonNull) else put("p_receipt_whatsapp_e164", receiptWhatsappE164)
                put("p_receipt_phone_e164", JsonNull)
            },
        ).decodeAs<String>()

    override suspend fun listPosPaymentRecovery(): List<PosRecoveryRow> =
        client.postgrest.rpc("list_pos_payment_recovery", buildJsonObject { put("p_limit", 100) }).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosRecoveryRow(
                orderId = o.stringOrNull("order_id") ?: return@mapNotNull null,
                state = o.stringOrNull("state") ?: "",
                total = o.number("total") ?: 0.0,
                currency = CurrencyCode.entries.find { it.rpcValue == o.stringOrNull("currency") } ?: CurrencyCode.USD,
                activeProvider = o.stringOrNull("active_provider"),
                settledProvider = o.stringOrNull("settled_provider"),
                salesInvoiceId = o.stringOrNull("sales_invoice_id"),
                paymentException = o.stringOrNull("payment_exception"),
                updatedAt = o.stringOrNull("updated_at") ?: "",
                openExceptions = (o.number("open_exception_count") ?: 0.0).toInt(),
            )
        }

    // --- POS card terminals (ECR)

    private fun stringMap(e: JsonElement?): Map<String, String?> =
        (e as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.takeIf { it.isString || it !is JsonNull }?.contentOrNull } ?: emptyMap()

    private fun attemptFrom(o: JsonObject): PosTerminalAttempt {
        val t = o["terminal"] as? JsonObject
        return PosTerminalAttempt(
            attemptId = o.stringOrNull("attempt_id") ?: error("card terminal attempt missing"),
            operation = o.stringOrNull("operation") ?: "purchase",
            status = o.stringOrNull("status") ?: "initiated",
            terminalId = o.stringOrNull("terminal_id"),
            terminalLabel = t?.stringOrNull("label"),
            adapterKey = t?.stringOrNull("adapter_key"),
            adapterConfig = stringMap(t?.get("adapter_config")),
            amount = o.number("amount") ?: 0.0,
            currency = CurrencyCode.entries.find { it.rpcValue == o.stringOrNull("currency") } ?: CurrencyCode.USD,
            externalRef = o.stringOrNull("external_ref"),
            transactionId = o.stringOrNull("terminal_transaction_id"),
            rrn = o.stringOrNull("rrn"),
            authorizationCode = o.stringOrNull("authorization_code"),
            cardLast4 = o.stringOrNull("card_last4"),
            cardScheme = o.stringOrNull("card_scheme"),
            responseMessage = o.stringOrNull("response_message"),
            orderId = o.stringOrNull("commerce_order_id") ?: o.stringOrNull("order_id"),
            splitLegId = o.stringOrNull("split_leg_id"),
            invoiceId = o.stringOrNull("invoice_id"),
            finalizationError = o.stringOrNull("finalization_error") ?: o.stringOrNull("error"),
        )
    }

    private suspend fun attemptRpc(fn: String, args: JsonObject): PosTerminalAttempt =
        attemptFrom(client.postgrest.rpc(fn, args).decodeAs<JsonObject>())

    override suspend fun listPosCardTerminals(warehouseId: String?, deviceId: String?): List<PosCardTerminalRow> =
        client.postgrest.rpc(
            "list_pos_card_terminals",
            buildJsonObject {
                if (warehouseId == null) put("p_warehouse_id", JsonNull) else put("p_warehouse_id", warehouseId)
                if (deviceId == null) put("p_device_id", JsonNull) else put("p_device_id", deviceId)
            },
        ).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosCardTerminalRow(
                id = o.stringOrNull("id") ?: return@mapNotNull null,
                code = o.stringOrNull("code") ?: "",
                label = o.stringOrNull("label") ?: "",
                acquirerName = o.stringOrNull("acquirer_name"),
                adapterKey = o.stringOrNull("adapter_key") ?: "",
                adapterConfig = stringMap(o["adapter_config"]),
                warehouseId = o.stringOrNull("warehouse_id"),
                deviceId = o.stringOrNull("device_id"),
            )
        }

    override suspend fun beginPosCardTerminalPurchase(orderId: String, terminalId: String, requestId: String) = attemptRpc(
        "begin_pos_card_terminal_purchase",
        buildJsonObject { put("p_order_id", orderId); put("p_terminal_id", terminalId); put("p_request_id", requestId) },
    )

    override suspend fun beginPosSplitCardTerminalLeg(legId: String, terminalId: String, requestId: String) = attemptRpc(
        "begin_pos_split_card_terminal_leg",
        buildJsonObject { put("p_leg_id", legId); put("p_terminal_id", terminalId); put("p_request_id", requestId) },
    )

    override suspend fun getPosCardTerminalAttempt(attemptId: String) =
        attemptRpc("get_pos_card_terminal_attempt", buildJsonObject { put("p_attempt_id", attemptId) })

    override suspend fun submitCardTerminalEvidence(payloadJson: String, signatureBase64: String): PosTerminalAttempt {
        val body = buildJsonObject {
            put("payload", Json.parseToJsonElement(payloadJson))
            put("signature_base64", signatureBase64)
        }
        val text = try {
            client.functions.invoke("card-terminal-result") { setBody(body) }.bodyAsText()
        } catch (e: io.github.jan.supabase.exceptions.RestException) {
            val o = runCatching { Json.parseToJsonElement(e.error) as? JsonObject }.getOrNull()
            throw IllegalStateException(o?.stringOrNull("error") ?: e.error)
        }
        val o = Json.parseToJsonElement(text) as? JsonObject ?: error("The card machine answer was not recorded.")
        return attemptFrom(o["attempt"] as? JsonObject ?: error(o.stringOrNull("error") ?: "The card machine answer was not recorded."))
    }

    override suspend fun finalizePosCardTerminalPurchase(attemptId: String) =
        attemptRpc("finalize_pos_card_terminal_purchase", buildJsonObject { put("p_attempt_id", attemptId) })

    override suspend fun beginPosCardTerminalReversal(purchaseAttemptId: String, requestId: String) = attemptRpc(
        "begin_pos_card_terminal_reversal",
        buildJsonObject { put("p_purchase_attempt_id", purchaseAttemptId); put("p_request_id", requestId) },
    )

    override suspend fun listPosCardTerminalRecovery(): List<PosTerminalRecoveryRow> =
        client.postgrest.rpc("list_pos_card_terminal_recovery", buildJsonObject { put("p_limit", 100) }).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosTerminalRecoveryRow(
                attemptId = o.stringOrNull("id") ?: return@mapNotNull null,
                operation = o.stringOrNull("operation") ?: "",
                status = o.stringOrNull("status") ?: "",
                terminalLabel = o.stringOrNull("terminal_label"),
                orderId = o.stringOrNull("commerce_order_id"),
                amount = o.number("amount") ?: 0.0,
                currency = CurrencyCode.entries.find { it.rpcValue == o.stringOrNull("currency") } ?: CurrencyCode.USD,
                externalRef = o.stringOrNull("external_ref"),
                transactionId = o.stringOrNull("terminal_transaction_id"),
                cardLast4 = o.stringOrNull("card_last4"),
                responseMessage = o.stringOrNull("response_message"),
                finalizationError = o.stringOrNull("finalization_error"),
                updatedAt = o.stringOrNull("updated_at") ?: "",
            )
        }

    override suspend fun registerPosCardTerminalDeviceKey(terminalId: String, deviceId: String, publicKeySpkiBase64: String, keySha256: String): String =
        client.postgrest.rpc(
            "register_pos_card_terminal_device_key",
            buildJsonObject {
                put("p_terminal_id", terminalId)
                put("p_device_id", deviceId)
                put("p_public_key_spki_base64", publicKeySpkiBase64)
                put("p_key_sha256", keySha256)
            },
        ).decodeAs<String>()

    override suspend fun beginPosCardTerminalRefund(invoiceId: String, terminalId: String, requestId: String) = attemptRpc(
        "begin_pos_card_terminal_refund",
        buildJsonObject { put("p_invoice_id", invoiceId); put("p_terminal_id", terminalId); put("p_request_id", requestId) },
    )

    override suspend fun finalizePosCardTerminalRefund(attemptId: String, notes: String?) = attemptRpc(
        "finalize_pos_card_terminal_refund",
        buildJsonObject { put("p_attempt_id", attemptId); if (notes == null) put("p_notes", JsonNull) else put("p_notes", notes) },
    )

    // --- POS returns, cores, warranty and stock by branch

    override suspend fun getPosInvoiceDetail(invoiceId: String): PosInvoiceDetail {
        val o = client.postgrest.rpc("get_pos_invoice_detail", buildJsonObject { put("p_invoice_id", invoiceId) }).decodeAs<JsonObject>()
        return PosInvoiceDetail(
            id = o.stringOrNull("id") ?: invoiceId,
            documentNumber = o.stringOrNull("document_number"),
            customerId = o.stringOrNull("customer_id"),
            currency = CurrencyCode.entries.find { it.rpcValue == o.stringOrNull("currency") } ?: CurrencyCode.USD,
            total = o.number("total") ?: 0.0,
            amountPaid = o.number("amount_paid") ?: 0.0,
            postedAt = o.stringOrNull("posted_at"),
            tillSessionId = o.stringOrNull("till_session_id"),
            lines = (o["lines"] as? JsonArray).orEmpty().mapNotNull { e ->
                val l = e as? JsonObject ?: return@mapNotNull null
                PosInvoiceDetailLine(
                    id = l.stringOrNull("id") ?: return@mapNotNull null,
                    stockItemId = l.stringOrNull("stock_item_id") ?: return@mapNotNull null,
                    oemPartNumber = l.stringOrNull("oem_part_number").orEmpty(),
                    description = l.stringOrNull("description"),
                    uomId = l.stringOrNull("uom_id").orEmpty(),
                    qty = l.number("qty") ?: 0.0,
                    unitPrice = l.number("unit_price") ?: 0.0,
                    lineTotal = l.number("line_total") ?: 0.0,
                    isCoreCharge = l["is_core_charge"]?.jsonPrimitive?.booleanOrNull == true,
                    returnableQty = l.number("returnable_qty") ?: 0.0,
                )
            },
        )
    }

    private fun List<PosReplacementLineInput>.toReplacementJson(): JsonArray = buildJsonArray {
        forEach { r ->
            add(
                buildJsonObject {
                    put("stock_item_id", r.stockItemId)
                    put("uom_id", r.uomId)
                    put("qty", r.qty)
                    // A missing serial is left out, never sent as null.
                    r.serialId?.let { put("replacement_serial_id", it) }
                },
            )
        }
    }

    override suspend fun createPosReturnCase(
        invoiceId: String,
        resolution: String,
        reasonCode: String,
        lines: List<PosReturnLineInput>,
        notes: String?,
        replacementLines: List<PosReplacementLineInput>?,
        tillSessionId: String?,
    ): String = client.postgrest.rpc(
        "create_pos_return_case",
        buildJsonObject {
            put("p_invoice_id", invoiceId)
            put("p_resolution", resolution)
            put("p_reason_code", reasonCode)
            put("p_lines", buildJsonArray { lines.forEach { l -> add(buildJsonObject { put("invoice_line_id", l.invoiceLineId); put("qty", l.qty); put("condition", l.condition) }) } })
            if (notes == null) put("p_notes", JsonNull) else put("p_notes", notes)
            if (replacementLines == null) put("p_replacement_lines", JsonNull) else put("p_replacement_lines", replacementLines.toReplacementJson())
            if (tillSessionId == null) put("p_till_session_id", JsonNull) else put("p_till_session_id", tillSessionId)
        },
    ).decodeAs<String>()

    override suspend fun postPosReturnCase(returnCaseId: String) {
        client.postgrest.rpc("post_pos_return_case", buildJsonObject { put("p_return_case_id", returnCaseId) })
    }

    override suspend fun postPosCoreReturn(invoiceId: String, coreLineId: String, qty: Double, resolution: String, reasonCode: String, tillSessionId: String?, notes: String?) {
        client.postgrest.rpc(
            "post_pos_core_return",
            buildJsonObject {
                put("p_invoice_id", invoiceId)
                put("p_source_core_line_id", coreLineId)
                put("p_qty", qty)
                put("p_resolution", resolution)
                put("p_reason_code", reasonCode)
                if (tillSessionId == null) put("p_till_session_id", JsonNull) else put("p_till_session_id", tillSessionId)
                if (notes == null) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        )
    }

    override suspend fun openPosWarrantyClaim(invoiceId: String, invoiceLineId: String, serialId: String?, notes: String?): String =
        client.postgrest.rpc(
            "open_pos_warranty_claim",
            buildJsonObject {
                put("p_sales_invoice_id", invoiceId)
                put("p_invoice_line_id", invoiceLineId)
                if (serialId == null) put("p_stock_serial_id", JsonNull) else put("p_stock_serial_id", serialId)
                if (notes == null) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        ).decodeAs<String>()

    override suspend fun findPosWarrantySerial(serialNumber: String): List<PosWarrantySerialRow> =
        client.postgrest.rpc("find_pos_warranty_serial", buildJsonObject { put("p_serial_number", serialNumber.trim()) }).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosWarrantySerialRow(
                id = o.stringOrNull("id") ?: return@mapNotNull null,
                serialNumber = o.stringOrNull("serial_number").orEmpty(),
                stockItemId = o.stringOrNull("stock_item_id").orEmpty(),
                oemPartNumber = o.stringOrNull("oem_part_number"),
                status = o.stringOrNull("status").orEmpty(),
            )
        }

    override suspend fun listPosWarrantyClaims(query: String?, status: String?): List<PosWarrantyClaimRow> =
        client.postgrest.rpc(
            "list_pos_warranty_claims",
            buildJsonObject {
                if (query.isNullOrBlank()) put("p_query", JsonNull) else put("p_query", query.trim())
                if (status == null) put("p_status", JsonNull) else put("p_status", status)
                put("p_limit", 100)
            },
        ).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosWarrantyClaimRow(
                id = o.stringOrNull("id") ?: return@mapNotNull null,
                documentNumber = o.stringOrNull("document_number"),
                status = o.stringOrNull("status").orEmpty(),
                resolution = o.stringOrNull("resolution"),
                salesInvoiceId = o.stringOrNull("sales_invoice_id"),
                invoiceNumber = o.stringOrNull("invoice_number"),
                stockItemId = o.stringOrNull("stock_item_id"),
                oemPartNumber = o.stringOrNull("oem_part_number"),
                serialNumber = o.stringOrNull("serial_number"),
                notes = o.stringOrNull("notes"),
                rejectReason = o.stringOrNull("reject_reason"),
                creditNoteId = o.stringOrNull("credit_note_id"),
                createdAt = o.stringOrNull("created_at").orEmpty(),
                decidedAt = o.stringOrNull("decided_at"),
                closedAt = o.stringOrNull("closed_at"),
            )
        }

    override suspend fun approvePosWarrantyClaim(claimId: String, resolution: String, creditLines: List<Pair<String, Double>>?, replacementLines: List<PosReplacementLineInput>?) {
        client.postgrest.rpc(
            "approve_pos_warranty_claim",
            buildJsonObject {
                put("p_claim_id", claimId)
                put("p_resolution", resolution)
                if (creditLines == null) put("p_lines", JsonNull)
                else put("p_lines", buildJsonArray { creditLines.forEach { (item, qty) -> add(buildJsonObject { put("stock_item_id", item); put("qty", qty) }) } })
                if (replacementLines == null) put("p_replacement_lines", JsonNull) else put("p_replacement_lines", replacementLines.toReplacementJson())
            },
        )
    }

    override suspend fun rejectPosWarrantyClaim(claimId: String, reason: String) {
        client.postgrest.rpc("reject_pos_warranty_claim", buildJsonObject { put("p_claim_id", claimId); put("p_reason", reason) })
    }

    override suspend fun closeWarrantyClaim(claimId: String) {
        client.postgrest.rpc("close_warranty_claim", buildJsonObject { put("p_claim_id", claimId) })
    }

    override suspend fun listPosStockAvailability(stockItemId: String): List<PosStockAvailabilityRow> =
        client.postgrest.rpc("list_pos_stock_availability", buildJsonObject { put("p_stock_item_id", stockItemId) }).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosStockAvailabilityRow(
                warehouseId = o.stringOrNull("warehouse_id") ?: return@mapNotNull null,
                warehouseCode = o.stringOrNull("warehouse_code").orEmpty(),
                warehouseName = o.stringOrNull("warehouse_name").orEmpty(),
                onHand = o.number("on_hand") ?: 0.0,
                reserved = o.number("reserved") ?: 0.0,
                available = o.number("available") ?: 0.0,
                transferIncoming = o.number("transfer_incoming") ?: 0.0,
            )
        }

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
        // Requests are in the part's stock unit.
        val item = client.postgrest.from("stock_items").select(Columns.list("base_uom_id")) { filter { eq("id", stockItemId) } }.decodeList<JsonObject>().firstOrNull()
        val uom = item?.stringOrNull("base_uom_id") ?: error("This part has no stock unit set up.")
        return client.postgrest.rpc(
            "create_pos_fulfillment_request",
            buildJsonObject {
                put("p_kind", kind)
                put("p_stock_item_id", stockItemId)
                put("p_uom_id", uom)
                put("p_qty", qty)
                if (sourceWarehouseId == null) put("p_source_warehouse_id", JsonNull) else put("p_source_warehouse_id", sourceWarehouseId)
                if (destinationWarehouseId == null) put("p_destination_warehouse_id", JsonNull) else put("p_destination_warehouse_id", destinationWarehouseId)
                if (customerId == null) put("p_customer_id", JsonNull) else put("p_customer_id", customerId)
                if (cartId == null) put("p_cart_id", JsonNull) else put("p_cart_id", cartId)
                put("p_invoice_id", JsonNull)
                if (notes == null) put("p_notes", JsonNull) else put("p_notes", notes)
                put("p_hold_minutes", holdMinutes)
            },
        ).decodeAs<String>()
    }

    override suspend fun listPosFulfillmentRequests(query: String?, status: String?): List<PosFulfillmentRow> =
        client.postgrest.rpc(
            "list_pos_fulfillment_requests",
            buildJsonObject {
                if (query.isNullOrBlank()) put("p_query", JsonNull) else put("p_query", query.trim())
                if (status == null) put("p_status", JsonNull) else put("p_status", status)
                put("p_limit", 100)
            },
        ).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosFulfillmentRow(
                id = o.stringOrNull("id") ?: return@mapNotNull null,
                documentNumber = o.stringOrNull("document_number"),
                kind = o.stringOrNull("kind").orEmpty(),
                status = o.stringOrNull("status").orEmpty(),
                stockItemId = o.stringOrNull("stock_item_id").orEmpty(),
                oemPartNumber = o.stringOrNull("oem_part_number").orEmpty(),
                description = o.stringOrNull("description"),
                qty = o.number("qty") ?: 0.0,
                sourceWarehouseId = o.stringOrNull("source_warehouse_id"),
                sourceWarehouseName = o.stringOrNull("source_warehouse_name"),
                destinationWarehouseId = o.stringOrNull("destination_warehouse_id"),
                destinationWarehouseName = o.stringOrNull("destination_warehouse_name"),
                customerId = o.stringOrNull("customer_id"),
                cartId = o.stringOrNull("cart_id"),
                invoiceId = o.stringOrNull("invoice_id"),
                expiresAt = o.stringOrNull("expires_at"),
                readyAt = o.stringOrNull("ready_at"),
                collectedAt = o.stringOrNull("collected_at"),
                createdAt = o.stringOrNull("created_at").orEmpty(),
            )
        }

    override suspend fun posFulfillmentStep(requestId: String, step: String, notes: String?) {
        val fn = when (step) {
            "approve" -> "approve_pos_fulfillment_request"
            "ready" -> "mark_pos_fulfillment_ready"
            "collect" -> "collect_pos_fulfillment_request"
            "cancel" -> "cancel_pos_fulfillment_request"
            else -> error("unknown fulfilment step $step")
        }
        client.postgrest.rpc(fn, buildJsonObject { put("p_request_id", requestId); if (notes == null) put("p_notes", JsonNull) else put("p_notes", notes) })
    }

    // --- Payment resolution letters, manager signature, business document profile

    private fun letterRow(o: JsonObject) = PaymentLetterRow(
        id = o.stringOrNull("id") ?: error("letter id missing"),
        documentNumber = o.stringOrNull("document_number"),
        sourceKind = o.stringOrNull("source_kind").orEmpty(),
        provider = o.stringOrNull("provider"),
        observedStatus = o.stringOrNull("observed_status"),
        amount = o.number("amount") ?: 0.0,
        currency = CurrencyCode.entries.find { it.rpcValue == o.stringOrNull("currency") } ?: CurrencyCode.USD,
        customerName = o.stringOrNull("customer_name"),
        invoiceNumber = o.stringOrNull("invoice_document_number"),
        managerName = o.stringOrNull("manager_name"),
        managerTitle = o.stringOrNull("manager_title"),
        issuedAt = o.stringOrNull("issued_at").orEmpty(),
    )

    private fun profileRow(o: JsonObject) = BusinessProfileRow(
        legalName = o.stringOrNull("legal_name").orEmpty(),
        tradingName = o.stringOrNull("trading_name").orEmpty(),
        domain = o.stringOrNull("domain").orEmpty(),
        city = o.stringOrNull("city"),
        country = o.stringOrNull("country"),
        addressLine1 = o.stringOrNull("address_line1"),
        addressLine2 = o.stringOrNull("address_line2"),
        phoneE164 = o.stringOrNull("phone_e164"),
        email = o.stringOrNull("email"),
        registrationNumber = o.stringOrNull("registration_number"),
    )

    /** Storage REST with the signed-in user's own token (storage policies decide; no service key here). */
    private suspend fun storageRequest(method: String, path: String, body: ByteArray? = null, contentType: String? = null): ByteArray =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val base = projectUrl ?: error("Supabase URL is not configured.")
            val token = auth.currentAccessTokenOrNull() ?: error("Sign in again.")
            val conn = (java.net.URL("$base/storage/v1/$path").openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("apikey", client.supabaseKey)
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                if (body != null) {
                    doOutput = true
                    setRequestProperty("x-upsert", "true")
                    outputStream.use { it.write(body) }
                }
            }
            try {
                val code = conn.responseCode
                val bytes = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
                if (code !in 200..299) error("Storage refused ($code): ${bytes.decodeToString().take(200)}")
                bytes
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun downloadPrivate(bucket: String, path: String): ByteArray? =
        runCatching { storageRequest("GET", "object/authenticated/$bucket/$path") }.getOrNull()

    override suspend fun listPaymentLetters(sourceKind: String?, sourceId: String?): List<PaymentLetterRow> =
        client.postgrest.rpc(
            "list_payment_resolution_letters",
            buildJsonObject {
                if (sourceKind == null) put("p_source_kind", JsonNull) else put("p_source_kind", sourceKind)
                if (sourceId == null) put("p_source_id", JsonNull) else put("p_source_id", sourceId)
                put("p_query", JsonNull)
                put("p_limit", 50)
            },
        ).decodeAs<JsonArray>().mapNotNull { (it as? JsonObject)?.let(::letterRow) }

    override suspend fun createPaymentLetter(sourceKind: String, sourceId: String, notes: String?): String =
        client.postgrest.rpc(
            "create_payment_resolution_letter",
            buildJsonObject {
                put("p_source_kind", sourceKind)
                put("p_source_id", sourceId)
                if (notes == null) put("p_issue_notes", JsonNull) else put("p_issue_notes", notes)
            },
        ).decodeAs<String>()

    override suspend fun getPaymentLetter(letterId: String): PaymentLetterDocument {
        val o = client.postgrest.rpc("get_payment_resolution_letter_render_data", buildJsonObject { put("p_letter_id", letterId) }).decodeAs<JsonObject>()
        val l = o["letter"] as? JsonObject ?: error("letter missing")
        val fields = listOf(
            "external_reference", "provider_reference", "terminal_transaction_id", "rrn", "authorization_code",
            "card_last4", "card_scheme", "failure_detail", "manager_employee_code", "issue_notes",
        ).associateWith { l.stringOrNull(it) }
        val path = l.stringOrNull("signature_storage_path")
        return PaymentLetterDocument(
            row = letterRow(l),
            fields = fields,
            business = (o["business"] as? JsonObject)?.let(::profileRow),
            signature = path?.let { downloadPrivate(l.stringOrNull("signature_storage_bucket") ?: "staff-signatures", it) },
            signatureSha256 = l.stringOrNull("signature_sha256"),
        )
    }

    override suspend fun getMyManagerSignature(): ManagerSignatureRow {
        val o = client.postgrest.rpc("get_my_manager_signature").decodeAs<JsonObject>()
        val path = o.stringOrNull("signature_path")
        return ManagerSignatureRow(
            fullName = o.stringOrNull("full_name").orEmpty(),
            employeeCode = o.stringOrNull("employee_code"),
            hasSignature = o["has_signature"]?.jsonPrimitive?.booleanOrNull == true,
            capturedAt = o.stringOrNull("signature_captured_at"),
            image = path?.let { downloadPrivate(o.stringOrNull("signature_bucket") ?: "staff-signatures", it) },
        )
    }

    override suspend fun saveMyManagerSignature(png: ByteArray): ManagerSignatureRow {
        require(png.size in 1..(2 * 1024 * 1024)) { "The signature image must be under 2 MB." }
        val uid = auth.currentSessionOrNull()?.user?.id ?: error("Sign in again.")
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(png).joinToString("") { "%02x".format(it) }
        // Own folder only (storage policy); a new file per signature keeps old letters' signatures intact.
        val path = "$uid/signature-${sha.take(16)}.png"
        storageRequest("POST", "object/staff-signatures/$path", png, "image/png")
        client.postgrest.rpc(
            "register_my_manager_signature",
            buildJsonObject { put("p_storage_path", path); put("p_mime_type", "image/png"); put("p_sha256", sha) },
        )
        return getMyManagerSignature()
    }

    override suspend fun getBusinessDocumentProfile(): BusinessProfileRow =
        profileRow(client.postgrest.rpc("get_business_document_profile").decodeAs<JsonObject>())

    override suspend fun setBusinessDocumentProfile(profile: BusinessProfileRow): BusinessProfileRow =
        profileRow(
            client.postgrest.rpc(
                "set_business_document_profile",
                buildJsonObject {
                    put("p_legal_name", profile.legalName)
                    put("p_trading_name", profile.tradingName)
                    put("p_domain", profile.domain)
                    fun opt(k: String, v: String?) = if (v.isNullOrBlank()) put(k, JsonNull) else put(k, v.trim())
                    opt("p_city", profile.city)
                    opt("p_country", profile.country)
                    opt("p_address_line1", profile.addressLine1)
                    opt("p_address_line2", profile.addressLine2)
                    opt("p_phone_e164", profile.phoneE164)
                    opt("p_email", profile.email)
                    opt("p_registration_number", profile.registrationNumber)
                },
            ).decodeAs<JsonObject>(),
        )

    // --- POS part payments (staged split)

    private fun splitFrom(o: JsonObject): PosSplitSession = PosSplitSession(
        sessionId = o.stringOrNull("session_id") ?: error("split session missing"),
        orderId = o.stringOrNull("order_id") ?: "",
        status = o.stringOrNull("status") ?: "open",
        total = o.number("total") ?: 0.0,
        currency = CurrencyCode.entries.find { it.rpcValue == o.stringOrNull("currency") } ?: CurrencyCode.USD,
        captured = o.number("captured_amount") ?: 0.0,
        held = o.number("held_amount") ?: 0.0,
        pending = o.number("pending_amount") ?: 0.0,
        locked = o.number("locked_amount") ?: 0.0,
        balanceDue = o.number("balance_due") ?: 0.0,
        availableToAllocate = o.number("available_to_allocate") ?: 0.0,
        finalInvoiceId = o.stringOrNull("final_invoice_id"),
        finalizationError = o.stringOrNull("finalization_error"),
        legs = (o["legs"] as? JsonArray).orEmpty().mapNotNull { e ->
            val l = e as? JsonObject ?: return@mapNotNull null
            PosSplitLegRow(
                id = l.stringOrNull("id") ?: return@mapNotNull null,
                sequenceNo = (l.number("sequence_no") ?: 0.0).toInt(),
                tender = l.stringOrNull("tender") ?: "",
                amount = l.number("amount") ?: 0.0,
                status = l.stringOrNull("status") ?: "planned",
                externalReference = l.stringOrNull("external_reference"),
                providerRef = l.stringOrNull("provider_ref"),
                statusDetail = l.stringOrNull("status_detail"),
                appliedAmount = l.number("applied_target_amount"),
                refundRequired = l.number("refund_required_amount"),
            )
        },
        refunds = (o["refunds"] as? JsonArray).orEmpty().mapNotNull { e ->
            val r = e as? JsonObject ?: return@mapNotNull null
            PosSplitRefundRow(
                id = r.stringOrNull("id") ?: return@mapNotNull null,
                legId = r.stringOrNull("leg_id") ?: "",
                status = r.stringOrNull("status") ?: "review",
                grossAmount = r.number("gross_amount") ?: 0.0,
                feePolicy = r.stringOrNull("fee_policy") ?: "manual_review",
                netCustomerRefund = r.number("net_customer_refund"),
                providerRef = r.stringOrNull("provider_ref"),
                failureReason = r.stringOrNull("failure_reason"),
                notes = r.stringOrNull("notes"),
            )
        },
    )

    private suspend fun splitRpc(fn: String, args: JsonObject): PosSplitSession =
        splitFrom(client.postgrest.rpc(fn, args).decodeAs<JsonObject>())

    override suspend fun findPosSplitPayment(orderId: String): PosSplitSession? {
        val e = client.postgrest.rpc("find_pos_split_payment", buildJsonObject { put("p_order_id", orderId) }).decodeAs<JsonElement>()
        return (e as? JsonObject)?.let(::splitFrom)
    }

    override suspend fun startPosSplitPayment(orderId: String) = splitRpc("start_pos_split_payment", buildJsonObject { put("p_order_id", orderId) })

    override suspend fun getPosSplitPayment(sessionId: String) = splitRpc("get_pos_split_payment", buildJsonObject { put("p_session_id", sessionId) })

    override suspend fun addPosSplitPaymentLeg(sessionId: String, tender: String, amount: Double, requestId: String, externalReference: String?): PosSplitSession {
        val o = client.postgrest.rpc(
            "add_pos_split_payment_leg",
            buildJsonObject {
                put("p_session_id", sessionId)
                put("p_tender", tender)
                put("p_amount", amount)
                put("p_request_id", requestId)
                if (externalReference.isNullOrBlank()) put("p_external_reference", JsonNull) else put("p_external_reference", externalReference)
            },
        ).decodeAs<JsonObject>()
        return splitFrom(o["session"] as? JsonObject ?: error("part payment was not recorded"))
    }

    override suspend fun acceptPosSplitAffordableItems(sessionId: String, items: List<Pair<String, Double>>, notes: String?) = splitRpc(
        "accept_pos_split_affordable_items",
        buildJsonObject {
            put("p_session_id", sessionId)
            putJsonArray("p_items") { items.forEach { (line, qty) -> add(buildJsonObject { put("cart_line_id", line); put("qty", qty) }) } }
            put("p_customer_confirmed", true)
            if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
        },
    )

    override suspend fun requestPosSplitCancellation(sessionId: String, reason: String, feePolicy: String) = splitRpc(
        "request_pos_split_cancellation",
        buildJsonObject { put("p_session_id", sessionId); put("p_reason", reason); put("p_fee_policy", feePolicy) },
    )

    override suspend fun retryPosSplitFinalization(sessionId: String) =
        splitRpc("retry_pos_split_finalization", buildJsonObject { put("p_session_id", sessionId) })

    override suspend fun listPosSplitPaymentRecovery(): List<PosSplitRecoveryRow> =
        client.postgrest.rpc("list_pos_split_payment_recovery", buildJsonObject { put("p_limit", 100) }).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosSplitRecoveryRow(
                documentNumber = o.stringOrNull("document_number"),
                customerName = o.stringOrNull("customer_name"),
                updatedAt = o.stringOrNull("updated_at") ?: "",
                session = splitFrom(o["payload"] as? JsonObject ?: return@mapNotNull null),
            )
        }

    override suspend fun approvePosSplitRefund(refundId: String, feePolicy: String, customerFee: Double, notes: String?) = splitRpc(
        "approve_pos_split_refund",
        buildJsonObject {
            put("p_refund_id", refundId)
            put("p_fee_policy", feePolicy)
            put("p_estimated_provider_fee", 0)
            put("p_estimated_transfer_fee", 0)
            put("p_customer_fee", customerFee)
            put("p_expected_days", 3)
            if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
        },
    )

    override suspend fun completePosSplitRefund(refundId: String, providerRef: String, notes: String?) = splitRpc(
        "complete_pos_split_refund",
        buildJsonObject {
            put("p_refund_id", refundId)
            put("p_provider_ref", providerRef)
            put("p_actual_provider_fee", 0)
            put("p_actual_transfer_fee", 0)
            put("p_actual_customer_fee", JsonNull)
            if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
        },
    )

    override suspend fun failPosSplitRefund(refundId: String, reason: String) =
        splitRpc("fail_pos_split_refund", buildJsonObject { put("p_refund_id", refundId); put("p_reason", reason) })

    override suspend fun repairPosPaidOrder(orderId: String, notes: String?): String =
        client.postgrest.rpc(
            "repair_pos_paid_order",
            buildJsonObject {
                put("p_order_id", orderId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        ).decodeAs<String>()

    override suspend fun listPosPickupOrders(query: String?): List<PosPickupRow> =
        client.postgrest.rpc(
            "list_pos_pickup_orders",
            buildJsonObject {
                if (query.isNullOrBlank()) put("p_query", JsonNull) else put("p_query", query)
                put("p_limit", 100)
            },
        ).decodeAs<JsonArray>().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PosPickupRow(
                orderId = o.stringOrNull("order_id") ?: return@mapNotNull null,
                documentNumber = o.stringOrNull("document_number"),
                customerName = o.stringOrNull("customer_name"),
                state = o.stringOrNull("state") ?: "",
                total = o.number("total") ?: 0.0,
                currency = CurrencyCode.entries.find { it.rpcValue == o.stringOrNull("currency") } ?: CurrencyCode.USD,
                salesInvoiceId = o.stringOrNull("sales_invoice_id"),
                settledProvider = o.stringOrNull("settled_provider"),
                updatedAt = o.stringOrNull("updated_at") ?: "",
            )
        }

    override suspend fun collectPosCommerceOrder(orderId: String, notes: String?) {
        client.postgrest.rpc(
            "collect_pos_commerce_order",
            buildJsonObject {
                put("p_order_id", orderId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        )
    }

    // --- POS manager badges

    override suspend fun myPosApproverStatus(): Boolean =
        runCatching {
            (client.postgrest.rpc("get_my_pos_approver_status").decodeAs<kotlinx.serialization.json.JsonElement>() as? JsonObject)
                ?.get("is_approver")?.jsonPrimitive?.booleanOrNull == true
        }.getOrDefault(false)

    override suspend fun posBadgeApprove(badge: String, action: String, args: Map<String, Any?>, deviceId: String?): PosBadgeApproval {
        val o = client.postgrest.rpc(
            "pos_badge_approve",
            buildJsonObject {
                put("p_badge", badge.trim())
                put("p_action", action)
                put(
                    "p_args",
                    buildJsonObject {
                        args.forEach { (k, v) -> put(k, badgeArg(v)) }
                    },
                )
                if (deviceId.isNullOrBlank()) put("p_device_id", JsonNull) else put("p_device_id", deviceId)
            },
        ).decodeAs<JsonObject>()
        return PosBadgeApproval(
            ok = o["ok"]?.jsonPrimitive?.booleanOrNull == true,
            managerName = o.stringOrNull("manager_name"),
            error = o.stringOrNull("error"),
            attemptId = (o["result"] as? JsonObject)?.stringOrNull("attempt_id"),
        )
    }

    // --- POS governance

    private suspend fun governed(fn: String, build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String =
        client.postgrest.rpc(fn, buildJsonObject(build)).decodeAs<String>()

    override suspend fun applyPosCartDiscountGoverned(cartId: String, discountPercent: Double, reasonCode: String, notes: String?): String =
        governed("apply_pos_cart_discount_governed") {
            put("p_cart_id", cartId)
            put("p_discount_percent", discountPercent)
            put("p_reason_code", reasonCode)
            if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
        }

    override suspend fun applyPosLinePriceOverrideGoverned(lineId: String, unitPrice: Double, reasonCode: String, notes: String?): String =
        governed("apply_pos_line_price_override_governed") {
            put("p_line_id", lineId)
            put("p_unit_price", unitPrice)
            put("p_reason_code", reasonCode)
            if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
        }

    override suspend fun voidPosCartGoverned(cartId: String, reasonCode: String, notes: String?): String =
        governed("void_pos_cart_governed") {
            put("p_cart_id", cartId)
            put("p_reason_code", reasonCode)
            if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
        }

    override suspend fun postPosRefundGoverned(invoiceId: String, reasonCode: String, notes: String?): String =
        governed("post_pos_refund_governed") {
            put("p_invoice_id", invoiceId)
            put("p_reason_code", reasonCode)
            if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
        }

    override suspend fun posActionRequiresManager(action: String, value: Double): Boolean =
        client.postgrest.rpc(
            "pos_action_requires_manager",
            buildJsonObject {
                put("p_action", action)
                put("p_value", value)
            },
        ).decodeAs<kotlinx.serialization.json.JsonElement>().let { (it as? JsonPrimitive)?.booleanOrNull != false }

    override suspend fun listPosApprovalPolicies(): List<PosApprovalPolicy> =
        client.postgrest.rpc("list_pos_approval_policies")
            .decodeAs<JsonArray>()
            .mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                PosApprovalPolicy(
                    action = o.stringOrNull("action") ?: return@mapNotNull null,
                    thresholdValue = o.number("threshold_value") ?: 0.0,
                    alwaysRequireManager = o["always_require_manager"]?.jsonPrimitive?.booleanOrNull == true,
                    reasonRequired = o["reason_required"]?.jsonPrimitive?.booleanOrNull != false,
                    updatedAt = o.stringOrNull("updated_at"),
                )
            }

    override suspend fun setPosApprovalPolicy(action: String, thresholdValue: Double, alwaysRequireManager: Boolean, reasonRequired: Boolean) {
        client.postgrest.rpc(
            "set_pos_approval_policy",
            buildJsonObject {
                put("p_action", action)
                put("p_threshold_value", thresholdValue)
                put("p_always_require_manager", alwaysRequireManager)
                put("p_reason_required", reasonRequired)
            },
        )
    }

    // --- POS till sessions

    override suspend fun getMyOpenPosTillSession(deviceId: String?): PosTillSessionRow? {
        val raw = client.postgrest.rpc(
            "get_my_open_pos_till_session",
            buildJsonObject { if (deviceId.isNullOrBlank()) put("p_device_id", JsonNull) else put("p_device_id", deviceId) },
        ).decodeAs<kotlinx.serialization.json.JsonElement>()
        return (raw as? JsonObject)?.toTillRow()
    }

    override suspend fun openPosTillSession(warehouseId: String, deviceId: String, openingFloat: Double, currency: CurrencyCode): String =
        client.postgrest.rpc(
            "open_pos_till_session",
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                put("p_device_id", deviceId)
                put("p_opening_float", openingFloat)
                put("p_currency", currency.rpcValue)
            },
        ).decodeAs<String>()

    override suspend fun attachPosCartTillSession(cartId: String, sessionId: String) {
        client.postgrest.rpc(
            "attach_pos_cart_till_session",
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_session_id", sessionId)
            },
        )
    }

    override suspend fun listPosApprovalReasons(action: String): List<PosApprovalReason> =
        client.postgrest.rpc("list_pos_approval_reasons", buildJsonObject { put("p_action", action) })
            .decodeAs<JsonArray>()
            .mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val code = o.stringOrNull("code") ?: return@mapNotNull null
                PosApprovalReason(code, o.stringOrNull("label") ?: code, o["requires_notes"]?.jsonPrimitive?.booleanOrNull == true)
            }

    override suspend fun recordPosTillCashMovement(sessionId: String, kind: String, amount: Double, reasonCode: String, notes: String?): String =
        client.postgrest.rpc(
            "record_pos_till_cash_movement",
            buildJsonObject {
                put("p_session_id", sessionId)
                put("p_kind", kind)
                put("p_amount", amount)
                put("p_reason_code", reasonCode)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        ).decodeAs<String>()

    override suspend fun submitPosTillDenominatedClose(
        sessionId: String,
        lines: List<PosDenominationLine>,
        varianceReasonCode: String?,
        notes: String?,
    ): PosTillCloseResult {
        val o = client.postgrest.rpc(
            "submit_pos_till_denominated_close",
            buildJsonObject {
                put("p_session_id", sessionId)
                putJsonArray("p_denominations") {
                    lines.forEach { l ->
                        add(
                            buildJsonObject {
                                put("denomination", l.denomination)
                                put("quantity", l.quantity)
                            },
                        )
                    }
                }
                if (varianceReasonCode.isNullOrBlank()) put("p_variance_reason_code", JsonNull) else put("p_variance_reason_code", varianceReasonCode)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        ).decodeAs<JsonObject>()
        return PosTillCloseResult(
            sessionId = o.stringOrNull("session_id") ?: sessionId,
            expectedCash = o.number("expected_cash") ?: 0.0,
            countedCash = o.number("counted_cash") ?: 0.0,
            variance = o.number("variance") ?: 0.0,
            status = o.stringOrNull("status") ?: "closed",
        )
    }

    override suspend fun approvePosTillVariance(sessionId: String, reasonCode: String, notes: String?) {
        client.postgrest.rpc(
            "approve_pos_till_variance",
            buildJsonObject {
                put("p_session_id", sessionId)
                put("p_reason_code", reasonCode)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        )
    }

    override suspend fun listPosHandoverOperators(): List<PosHandoverOperatorRow> =
        client.postgrest.rpc("list_pos_handover_operators")
            .decodeAs<JsonArray>()
            .mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = o.stringOrNull("user_id") ?: return@mapNotNull null
                PosHandoverOperatorRow(id, o.stringOrNull("employee_code") ?: "", o.stringOrNull("full_name") ?: "")
            }

    override suspend fun handoverPosTillSession(sessionId: String, newOperatorUserId: String, notes: String?) {
        client.postgrest.rpc(
            "handover_pos_till_session",
            buildJsonObject {
                put("p_session_id", sessionId)
                put("p_new_operator_user_id", newOperatorUserId)
                if (notes.isNullOrBlank()) put("p_notes", JsonNull) else put("p_notes", notes)
            },
        )
    }

    override suspend fun listPosTillSessions(limit: Int): List<PosTillSessionRow> =
        client.postgrest.rpc(
            "list_pos_till_sessions",
            buildJsonObject {
                put("p_status", JsonNull)
                put("p_limit", limit.coerceIn(1, 200))
            },
        ).decodeAs<JsonArray>().mapNotNull { (it as? JsonObject)?.toTillRow() }
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

private const val VEHICLE_MASTER_PAGE = 1000

@Serializable
private data class VehicleMasterRpcRow(
    val id: String,
    @SerialName("model_family") val modelFamily: String,
    @SerialName("chassis_code") val chassisCode: String,
    @SerialName("engine_code") val engineCode: String? = null,
    @SerialName("year_start") val yearStart: Int? = null,
    @SerialName("year_end") val yearEnd: Int? = null,
    @SerialName("sales_region") val salesRegion: String? = null,
)

@Serializable
private data class PosScanSessionStatusRow(
    @SerialName("status") val status: String,
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
    @SerialName("image_storage_path") val imageStoragePath: String? = null,
)

@Serializable
private data class PosPopularPinRow(
    @SerialName("item_type") val itemType: String,
    @SerialName("item_key") val itemKey: String,
    val label: String,
    val subtitle: String? = null,
    @SerialName("search_query") val searchQuery: String,
    @SerialName("maker_slug") val makerSlug: String? = null,
    @SerialName("model_slug") val modelSlug: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("subcategory_name") val subcategoryName: String? = null,
    @SerialName("oem_part_number") val oemPartNumber: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
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
    val description: String? = null,
)

@Serializable
private data class StockItemImagePathRow(
    @SerialName("storage_path") val storagePath: String,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
private data class PosCustomerRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("customer_kind") val customerKind: String = "individual",
    @SerialName("business_name") val businessName: String? = null,
    val email: String? = null,
    @SerialName("phone_e164") val phoneE164: String? = null,
    @SerialName("whatsapp_e164") val whatsappE164: String? = null,
) {
    fun toModel() = CustomerOption(
        id = id,
        displayName = displayName,
        kind = PosCustomerKind.fromRpc(customerKind),
        businessName = businessName,
        email = email,
        phoneE164 = phoneE164,
        whatsappE164 = whatsappE164,
    )
}

@Serializable
private data class PosCustomerGarageRow(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    val make: String? = null,
    @SerialName("model_slug") val modelSlug: String? = null,
    val model: String? = null,
    val generation: String? = null,
    @SerialName("chassis_code") val chassisCode: String? = null,
    val engine: String? = null,
    val vin: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
) {
    fun toModel() = CustomerGarageVehicle(
        id = id, customerId = customerId, make = make, modelSlug = modelSlug, model = model,
        generation = generation, chassisCode = chassisCode, engine = engine, vin = vin, isPrimary = isPrimary,
    )
}

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

/** Badge arguments may nest (warranty lines, replacement lines): lists and maps become JSON arrays and objects. */
private fun badgeArg(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is List<*> -> JsonArray(v.map(::badgeArg))
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to badgeArg(x) })
    else -> JsonPrimitive(v.toString())
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

@Serializable
private data class PosPriceListRef(val currency: String? = null)

@Serializable
private data class PosPriceRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("price_lists") val priceList: PosPriceListRef? = null,
)

@Serializable
private data class PosLevelRow(
    @SerialName("stock_item_id") val stockItemId: String,
    val quantity: Double,
)

@Serializable
private data class PosImageRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("storage_path") val storagePath: String,
)

@Serializable
private data class PosHiddenRow(@SerialName("stock_item_id") val stockItemId: String)

@Serializable
private data class PosProfileNameRow(@SerialName("full_name") val fullName: String? = null)

@Serializable
private data class PosLineTotalRow(@SerialName("line_total") val lineTotal: Double)

@Serializable
private data class PosParkedCartRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val currency: String,
    @SerialName("pos_cart_lines") val lines: List<PosLineTotalRow> = emptyList(),
)

@Serializable
private data class PosInvoiceDocRow(@SerialName("document_number") val documentNumber: String? = null)

@Serializable
private data class PosCartCurrencyRow(val currency: String)

private fun JsonObject.number(key: String): Double? =
    this[key]?.jsonPrimitive?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

private fun JsonObject.toTillRow(): PosTillSessionRow? {
    val id = stringOrNull("id") ?: return null
    return PosTillSessionRow(
        id = id,
        warehouseId = stringOrNull("warehouse_id") ?: "",
        deviceId = stringOrNull("device_id") ?: "",
        currency = CurrencyCode.entries.find { it.rpcValue == stringOrNull("currency") } ?: CurrencyCode.USD,
        operatorUserId = stringOrNull("operator_user_id") ?: "",
        openingFloat = number("opening_float") ?: 0.0,
        status = stringOrNull("status") ?: "open",
        expectedCash = number("expected_cash"),
        countedCash = number("counted_cash"),
        variance = number("variance"),
        varianceReasonCode = stringOrNull("variance_reason_code"),
        openedAt = stringOrNull("opened_at") ?: "",
        closedAt = stringOrNull("closed_at"),
    )
}
