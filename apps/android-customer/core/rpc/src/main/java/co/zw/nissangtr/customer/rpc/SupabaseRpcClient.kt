package co.zw.nissangtr.customer.rpc

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
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/**
 * Live supabase-kt [RpcClient] for cart / orders / garage / pay / chat / wishlist /
 * compare / reviews RPCs.
 *
 * Uses anon key + Auth session (never hardcode JWTs). Payment: RPC create only — no PSP crypto.
 * Review photos: Storage [RpcNames.REVIEW_PHOTOS_BUCKET] then attach RPC.
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
     * Email/password sign-in via GoTrue. Session is stored by the SDK session manager —
     * never put passwords or JWTs in BuildConfig.
     */
    suspend fun signInWithEmail(email: String, password: String) {
        require(email.isNotBlank()) { "email required" }
        require(password.isNotBlank()) { "password required" }
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /** Clears the persisted GoTrue session. */
    suspend fun signOut() {
        auth.signOut()
    }

    fun currentUserEmail(): String? =
        auth.currentSessionOrNull()?.user?.email

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

    override suspend fun createCustomerCart(
        warehouseId: String,
        currency: CurrencyCode,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Double,
    ): String {
        require(warehouseId.isNotBlank())
        require(exchangeRate > 0)
        return client.postgrest.rpc(
            RpcNames.CREATE_CUSTOMER_CART,
            buildJsonObject {
                put("p_warehouse_id", warehouseId)
                put("p_currency", currency.rpcValue)
                put("p_fulfillment_mode", fulfillmentMode.rpcValue)
                put("p_exchange_rate", exchangeRate)
            },
        ).decodeAs<String>()
    }

    override suspend fun addCustomerCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String {
        require(qty > 0)
        return client.postgrest.rpc(
            RpcNames.ADD_CUSTOMER_CART_LINE,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_stock_item_id", stockItemId)
                put("p_uom_id", uomId)
                put("p_qty", qty)
            },
        ).decodeAs<String>()
    }

    override suspend fun checkoutCustomerCart(cartId: String): String =
        client.postgrest.rpc(
            RpcNames.CHECKOUT_CUSTOMER_CART,
            buildJsonObject { put("p_cart_id", cartId) },
        ).decodeAs<String>()

    override suspend fun getOpenCart(): CartSummary? {
        val cart = client.from("pos_carts")
            .select(Columns.list("id", "currency", "fulfillment_mode", "status")) {
                filter {
                    eq("status", "open")
                    eq("channel", "storefront")
                }
                order("created_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<PosCartRow>()
            .firstOrNull()
            ?: return null

        val lines = client.from("pos_cart_lines")
            .select(Columns.list("id", "stock_item_id", "uom_id", "qty")) {
                filter { eq("cart_id", cart.id) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<PosCartLineRow>()

        return CartSummary(
            id = cart.id,
            currency = CurrencyCode.entries.find { it.rpcValue == cart.currency } ?: CurrencyCode.USD,
            fulfillmentMode = FulfillmentMode.entries.find { it.rpcValue == cart.fulfillmentMode }
                ?: FulfillmentMode.IMMEDIATE,
            status = cart.status,
            lines = lines.map {
                CartLineSummary(
                    id = it.id,
                    stockItemId = it.stockItemId,
                    uomId = it.uomId,
                    qty = it.qty,
                )
            },
        )
    }

    override suspend fun getCustomerOrder(invoiceId: String): CustomerOrder {
        require(invoiceId.isNotBlank())
        val dto = client.postgrest.rpc(
            RpcNames.GET_CUSTOMER_ORDER,
            buildJsonObject { put("p_invoice_id", invoiceId) },
        ).decodeAs<CustomerOrderDto>()
        return dto.toModel()
    }

    override suspend fun listOwnInvoices(): List<InvoiceSummary> =
        client.from("sales_invoices")
            .select(
                Columns.list(
                    "id",
                    "document_number",
                    "status",
                    "currency",
                    "total",
                    "amount_paid",
                ),
            ) {
                filter { eq("doc_type", "invoice") }
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<InvoiceRow>()
            .map {
                InvoiceSummary(
                    id = it.id,
                    documentNumber = it.documentNumber,
                    status = it.status,
                    currency = CurrencyCode.entries.find { c -> c.rpcValue == it.currency }
                        ?: CurrencyCode.USD,
                    total = it.total,
                    amountPaid = it.amountPaid,
                )
            }

    override suspend fun createCustomerContipayIntent(
        salesInvoiceId: String,
        method: ContipayMethod,
        metadataJson: String,
    ): PaymentIntentResult {
        val intentId = client.postgrest.rpc(
            RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT,
            buildJsonObject {
                put("p_sales_invoice_id", salesInvoiceId)
                put("p_method", method.rpcValue)
                put("p_metadata", parseMetadata(metadataJson))
            },
        ).decodeAs<String>()
        // Intent create only — no ContiPay HMAC / secrets in the app.
        return PaymentIntentResult(intentId = intentId, provider = "contipay")
    }

    override suspend fun createCustomerPaynowIntent(
        salesInvoiceId: String,
        method: PaynowMethod,
        metadataJson: String,
    ): PaymentIntentResult {
        val intentId = client.postgrest.rpc(
            RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT,
            buildJsonObject {
                put("p_sales_invoice_id", salesInvoiceId)
                put("p_method", method.rpcValue)
                put("p_metadata", parseMetadata(metadataJson))
            },
        ).decodeAs<String>()
        // Intent create only — no Paynow hash / secrets in the app.
        return PaymentIntentResult(intentId = intentId, provider = "paynow")
    }

    override suspend fun listGarageVehicles(): List<GarageVehicle> =
        client.from("customer_garage_vehicles")
            .select(
                Columns.list(
                    "id",
                    "make",
                    "model",
                    "generation",
                    "engine",
                    "vin",
                    "is_primary",
                ),
            ) {
                order("is_primary", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
            }
            .decodeList<GarageRow>()
            .map {
                GarageVehicle(
                    id = it.id,
                    make = it.make,
                    model = it.model,
                    generation = it.generation,
                    engine = it.engine,
                    vin = it.vin,
                    isPrimary = it.isPrimary,
                )
            }

    override suspend fun upsertCustomerGarageVehicle(input: GarageVehicleInput): String =
        client.postgrest.rpc(
            RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE,
            buildJsonObject {
                putNullable("p_id", input.id)
                putNullable("p_make", input.make)
                putNullable("p_model", input.model)
                putNullable("p_generation", input.generation)
                putNullable("p_engine", input.engine)
                putNullable("p_vin", input.vin)
                put("p_is_primary", input.isPrimary)
            },
        ).decodeAs<String>()

    override suspend fun deleteCustomerGarageVehicle(id: String) {
        client.postgrest.rpc(
            RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE,
            buildJsonObject { put("p_id", id) },
        )
    }

    override suspend fun listChatThreads(): List<ChatThread> =
        client.from("chat_threads")
            .select(
                Columns.list(
                    "id",
                    "customer_user_id",
                    "customer_id",
                    "kind",
                    "status",
                    "subject",
                    "assigned_to",
                    "last_message_at",
                    "created_at",
                ),
            ) {
                order("last_message_at", Order.DESCENDING)
            }
            .decodeList<ChatThreadRow>()
            .map { it.toModel() }

    override suspend fun listChatMessages(threadId: String): List<ChatMessage> {
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
            }
            .decodeList<ChatMessageRow>()
            .map { it.toModel() }
    }

    override suspend fun startChatThread(input: StartChatThreadInput): String =
        client.postgrest.rpc(
            RpcNames.START_CHAT_THREAD,
            buildJsonObject {
                put("p_kind", input.kind.rpcValue)
                putNullable("p_subject", input.subject)
                putNullable("p_body", input.body)
            },
        ).decodeAs<String>()

    override suspend fun postChatMessage(threadId: String, body: String): String {
        require(threadId.isNotBlank())
        require(body.trim().isNotEmpty()) { "body required" }
        return client.postgrest.rpc(
            RpcNames.POST_CHAT_MESSAGE,
            buildJsonObject {
                put("p_thread_id", threadId)
                put("p_body", body.trim())
            },
        ).decodeAs<String>()
    }

    override suspend fun markChatThreadRead(threadId: String) {
        require(threadId.isNotBlank())
        client.postgrest.rpc(
            RpcNames.MARK_CHAT_THREAD_READ,
            buildJsonObject { put("p_thread_id", threadId) },
        )
    }

    override suspend fun chatUnreadCount(threadId: String?): Int =
        client.postgrest.rpc(
            RpcNames.CHAT_UNREAD_COUNT,
            buildJsonObject {
                if (threadId.isNullOrBlank()) put("p_thread_id", JsonNull)
                else put("p_thread_id", threadId)
            },
        ).decodeAs<Int>()

    override suspend fun getDeliveryTrackPoint(
        deliveryJobId: String?,
        token: String?,
    ): DeliveryTrackPoint? {
        val jobId = deliveryJobId?.trim()?.takeIf { it.isNotEmpty() }
        val tok = token?.trim()?.takeIf { it.isNotEmpty() }
        require(jobId != null || tok != null) {
            "delivery_job_id or token required for ${RpcNames.GET_DELIVERY_TRACK_POINT}"
        }
        return client.postgrest.rpc(
            RpcNames.GET_DELIVERY_TRACK_POINT,
            buildJsonObject {
                if (jobId == null) put("p_delivery_job_id", JsonNull) else put("p_delivery_job_id", jobId)
                if (tok == null) put("p_token", JsonNull) else put("p_token", tok)
            },
        ).decodeList<TrackPointRow>().firstOrNull()?.toModel()
    }

    override suspend fun listWishlist(): List<WishlistItem> =
        client.from("customer_wishlist_items")
            .select(
                Columns.raw(
                    "id,stock_item_id,notify_when_in_stock,created_at," +
                        "stock_items(id,oem_part_number,description)",
                ),
            ) {
                order("created_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList<WishlistRow>()
            .map { it.toModel() }

    override suspend fun addCustomerWishlistItem(stockItemId: String?, oem: String?): String =
        client.postgrest.rpc(
            RpcNames.ADD_CUSTOMER_WISHLIST_ITEM,
            stockItemOemBody(stockItemId, oem),
        ).decodeAs<String>()

    override suspend fun removeCustomerWishlistItem(
        wishlistId: String?,
        stockItemId: String?,
        oem: String?,
    ) {
        client.postgrest.rpc(
            RpcNames.REMOVE_CUSTOMER_WISHLIST_ITEM,
            buildJsonObject {
                putNullable("p_wishlist_id", wishlistId)
                putNullable("p_stock_item_id", stockItemId)
                putNullable("p_oem_part_number", oem)
            },
        )
    }

    override suspend fun setWishlistNotifyWhenInStock(
        notify: Boolean,
        wishlistId: String?,
        stockItemId: String?,
        oem: String?,
    ): String =
        client.postgrest.rpc(
            RpcNames.SET_WISHLIST_NOTIFY_WHEN_IN_STOCK,
            buildJsonObject {
                put("p_notify", notify)
                putNullable("p_wishlist_id", wishlistId)
                putNullable("p_stock_item_id", stockItemId)
                putNullable("p_oem_part_number", oem)
            },
        ).decodeAs<String>()

    override suspend fun wishlistMoveToCart(
        wishlistId: String?,
        stockItemId: String?,
        oem: String?,
        qty: Double,
        removeFromWishlist: Boolean,
    ): String {
        require(qty > 0)
        val cartId = ensureOpenCartId()
        return client.postgrest.rpc(
            RpcNames.WISHLIST_MOVE_TO_CART,
            buildJsonObject {
                put("p_cart_id", cartId)
                put("p_qty", qty)
                put("p_remove_from_wishlist", removeFromWishlist)
                putNullable("p_wishlist_id", wishlistId)
                putNullable("p_stock_item_id", stockItemId)
                putNullable("p_oem_part_number", oem)
            },
        ).decodeAs<String>()
    }

    override suspend fun listCompareItems(): List<CompareItem> =
        client.postgrest.rpc(RpcNames.LIST_CUSTOMER_COMPARE_ITEMS, buildJsonObject { })
            .decodeList<CompareItemRow>()
            .map { it.toModel() }

    override suspend fun addCustomerCompareItem(stockItemId: String?, oem: String?): String =
        client.postgrest.rpc(
            RpcNames.ADD_CUSTOMER_COMPARE_ITEM,
            stockItemOemBody(stockItemId, oem),
        ).decodeAs<String>()

    override suspend fun removeCustomerCompareItem(
        compareId: String?,
        stockItemId: String?,
        oem: String?,
    ) {
        client.postgrest.rpc(
            RpcNames.REMOVE_CUSTOMER_COMPARE_ITEM,
            buildJsonObject {
                putNullable("p_compare_id", compareId)
                putNullable("p_stock_item_id", stockItemId)
                putNullable("p_oem_part_number", oem)
            },
        )
    }

    override suspend fun listOwnReviews(): List<ProductReview> {
        val customerId = currentCustomerId() ?: return emptyList()
        return client.from("customer_product_reviews")
            .select(
                Columns.raw(
                    "id,stock_item_id,rating,body,status,created_at," +
                        "stock_items(id,oem_part_number,description)",
                ),
            ) {
                filter { eq("customer_id", customerId) }
                order("created_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList<ProductReviewRow>()
            .map { it.toModel() }
    }

    override suspend fun listApprovedReviews(oem: String): List<ProductReview> {
        val needle = oem.trim()
        if (needle.isEmpty()) return emptyList()
        // Resolve OEM via DEFINER stats RPC (same as PDP), then SELECT approved rows.
        val stats = getProductReviewStats(oem = needle) ?: return emptyList()
        return client.from("customer_product_reviews")
            .select(
                Columns.raw(
                    "id,stock_item_id,rating,body,status,created_at," +
                        "stock_items(id,oem_part_number,description)",
                ),
            ) {
                filter {
                    eq("stock_item_id", stats.stockItemId)
                    eq("status", "approved")
                }
                order("created_at", Order.DESCENDING)
                limit(40)
            }
            .decodeList<ProductReviewRow>()
            .map { it.toModel() }
    }

    override suspend fun getProductReviewStats(
        stockItemId: String?,
        oem: String?,
    ): ProductReviewStats? =
        client.postgrest.rpc(
            RpcNames.GET_PRODUCT_REVIEW_STATS,
            stockItemOemBody(stockItemId, oem),
        ).decodeList<ProductReviewStatsRow>().firstOrNull()?.toModel()

    override suspend fun submitCustomerProductReview(
        rating: Int,
        body: String,
        stockItemId: String?,
        oem: String?,
    ): String {
        require(rating in 1..5) { "rating must be 1..5" }
        return client.postgrest.rpc(
            RpcNames.SUBMIT_CUSTOMER_PRODUCT_REVIEW,
            buildJsonObject {
                put("p_rating", rating)
                put("p_body", body)
                putNullable("p_stock_item_id", stockItemId)
                putNullable("p_oem_part_number", oem)
            },
        ).decodeAs<String>()
    }

    override suspend fun uploadReviewPhoto(
        reviewId: String,
        localFilePath: String,
        mimeType: String,
        sortOrder: Int,
    ): String {
        require(reviewId.isNotBlank())
        val file = File(localFilePath)
        require(file.exists()) { "review photo file missing: $localFilePath" }
        val ext = when {
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
        val objectPath = "${reviewId.lowercase()}/${UUID.randomUUID().toString().lowercase()}.$ext"
        val bytes = file.readBytes()
        client.storage.from(RpcNames.REVIEW_PHOTOS_BUCKET).upload(
            path = objectPath,
            data = bytes,
        ) {
            upsert = true
        }
        return client.postgrest.rpc(
            RpcNames.ADD_CUSTOMER_PRODUCT_REVIEW_PHOTO,
            buildJsonObject {
                put("p_review_id", reviewId)
                put("p_storage_path", objectPath)
                put("p_sort_order", sortOrder)
            },
        ).decodeAs<String>()
    }

    private suspend fun ensureOpenCartId(): String {
        getOpenCart()?.id?.let { return it }
        val warehouseId = resolveMainWarehouseId()
        return createCustomerCart(
            warehouseId = warehouseId,
            currency = CurrencyCode.USD,
            fulfillmentMode = FulfillmentMode.IMMEDIATE,
            exchangeRate = 1.0,
        )
    }

    private suspend fun resolveMainWarehouseId(): String {
        val main = client.from("warehouses")
            .select(Columns.list("id")) {
                filter {
                    eq("is_active", true)
                    eq("is_quarantine", false)
                    eq("code", "MAIN")
                }
                limit(1)
            }
            .decodeList<WarehouseIdRow>()
            .firstOrNull()
        if (main != null) return main.id
        return client.from("warehouses")
            .select(Columns.list("id")) {
                filter {
                    eq("is_active", true)
                    eq("is_quarantine", false)
                }
                order("code", Order.ASCENDING)
                limit(1)
            }
            .decodeList<WarehouseIdRow>()
            .firstOrNull()
            ?.id
            ?: error("no active warehouse for wishlist_move_to_cart")
    }

    private suspend fun currentCustomerId(): String? =
        client.from("customers")
            .select(Columns.list("id")) {
                limit(1)
            }
            .decodeList<CustomerIdRow>()
            .firstOrNull()
            ?.id

    private fun stockItemOemBody(stockItemId: String?, oem: String?) =
        buildJsonObject {
            putNullable("p_stock_item_id", stockItemId)
            putNullable("p_oem_part_number", oem)
        }

    companion object {
        private val metadataJson = Json { ignoreUnknownKeys = true }

        fun create(supabaseUrl: String, supabaseAnonKey: String): SupabaseRpcClient {
            val client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseAnonKey,
            ) {
                install(Auth)
                install(Postgrest)
                install(Storage)
            }
            return SupabaseRpcClient(client)
        }

        private fun parseMetadata(raw: String): JsonElement {
            if (raw.isBlank()) return buildJsonObject { }
            return runCatching { metadataJson.parseToJsonElement(raw) }
                .getOrElse { buildJsonObject { } }
        }
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
    key: String,
    value: String?,
) {
    if (value.isNullOrBlank()) put(key, JsonNull)
    else put(key, value)
}

@Serializable
private data class PosCartRow(
    val id: String,
    val currency: String = "USD",
    @SerialName("fulfillment_mode") val fulfillmentMode: String = "immediate",
    val status: String = "open",
)

@Serializable
private data class PosCartLineRow(
    val id: String,
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("uom_id") val uomId: String,
    val qty: Double,
)

@Serializable
private data class InvoiceRow(
    val id: String,
    @SerialName("document_number") val documentNumber: String? = null,
    val status: String,
    val currency: String = "USD",
    val total: Double = 0.0,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
)

@Serializable
private data class GarageRow(
    val id: String,
    val make: String? = null,
    val model: String? = null,
    val generation: String? = null,
    val engine: String? = null,
    val vin: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
)

@Serializable
private data class ChatThreadRow(
    val id: String,
    @SerialName("customer_user_id") val customerUserId: String = "",
    @SerialName("customer_id") val customerId: String? = null,
    val kind: String = "support",
    val status: String = "open",
    val subject: String? = null,
    @SerialName("assigned_to") val assignedTo: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
) {
    fun toModel() = ChatThread(
        id = id,
        customerUserId = customerUserId,
        customerId = customerId,
        kind = ChatThreadKind.entries.find { it.rpcValue == kind } ?: ChatThreadKind.SUPPORT,
        status = ChatThreadStatus.entries.find { it.rpcValue == status } ?: ChatThreadStatus.OPEN,
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
    @SerialName("sender_user_id") val senderUserId: String = "",
    @SerialName("sender_kind") val senderKind: String = "customer",
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
) {
    fun toModel() = ChatMessage(
        id = id,
        threadId = threadId,
        senderUserId = senderUserId,
        senderKind = ChatSenderKind.entries.find { it.rpcValue == senderKind }
            ?: ChatSenderKind.CUSTOMER,
        body = body,
        createdAt = createdAt,
    )
}

@Serializable
private data class CustomerOrderDto(
    @SerialName("invoice_id") val invoiceId: String,
    @SerialName("document_number") val documentNumber: String? = null,
    @SerialName("doc_type") val docType: String = "invoice",
    val status: String = "",
    @SerialName("fulfillment_mode") val fulfillmentMode: String = "immediate",
    val currency: String = "USD",
    @SerialName("exchange_rate_applied") val exchangeRateApplied: Double = 1.0,
    val subtotal: Double = 0.0,
    val total: Double = 0.0,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
    @SerialName("amount_open") val amountOpen: Double = 0.0,
    @SerialName("cart_id") val cartId: String? = null,
    @SerialName("posted_at") val postedAt: String? = null,
    @SerialName("pick_list_status") val pickListStatus: String? = null,
    @SerialName("delivery_note_status") val deliveryNoteStatus: String? = null,
    @SerialName("active_delivery_job_id") val activeDeliveryJobId: String? = null,
) {
    fun toModel() = CustomerOrder(
        invoiceId = invoiceId,
        documentNumber = documentNumber,
        docType = docType,
        status = status,
        fulfillmentMode = FulfillmentMode.entries.find { it.rpcValue == fulfillmentMode }
            ?: FulfillmentMode.IMMEDIATE,
        currency = CurrencyCode.entries.find { it.rpcValue == currency } ?: CurrencyCode.USD,
        exchangeRateApplied = exchangeRateApplied,
        subtotal = subtotal,
        total = total,
        amountPaid = amountPaid,
        amountOpen = amountOpen,
        cartId = cartId,
        postedAt = postedAt,
        pickListStatus = pickListStatus,
        deliveryNoteStatus = deliveryNoteStatus,
        activeDeliveryJobId = activeDeliveryJobId,
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
    fun toModel() = DeliveryTrackPoint(
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
private data class StockItemEmbed(
    val id: String? = null,
    @SerialName("oem_part_number") val oemPartNumber: String? = null,
    val description: String? = null,
)

@Serializable
private data class WishlistRow(
    val id: String,
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("notify_when_in_stock") val notifyWhenInStock: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("stock_items") val stockItems: StockItemEmbed? = null,
) {
    fun toModel() = WishlistItem(
        id = id,
        stockItemId = stockItemId,
        oemPartNumber = stockItems?.oemPartNumber ?: "—",
        description = stockItems?.description,
        notifyWhenInStock = notifyWhenInStock,
        createdAt = createdAt,
    )
}

@Serializable
private data class CompareItemRow(
    val id: String,
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("oem_part_number") val oemPartNumber: String = "",
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toModel() = CompareItem(
        id = id,
        stockItemId = stockItemId,
        oemPartNumber = oemPartNumber,
        description = description,
        createdAt = createdAt,
    )
}

@Serializable
private data class ProductReviewRow(
    val id: String,
    @SerialName("stock_item_id") val stockItemId: String,
    val rating: Int = 0,
    val body: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("stock_items") val stockItems: StockItemEmbed? = null,
) {
    fun toModel() = ProductReview(
        id = id,
        stockItemId = stockItemId,
        oemPartNumber = stockItems?.oemPartNumber,
        description = stockItems?.description,
        rating = rating,
        body = body.orEmpty(),
        status = ProductReviewStatus.entries.find { it.rpcValue == status }
            ?: ProductReviewStatus.PENDING,
        createdAt = createdAt,
    )
}

@Serializable
private data class ProductReviewStatsRow(
    @SerialName("stock_item_id") val stockItemId: String,
    @SerialName("avg_rating") val avgRating: Double = 0.0,
    @SerialName("review_count") val reviewCount: Long = 0,
) {
    fun toModel() = ProductReviewStats(
        stockItemId = stockItemId,
        avgRating = avgRating,
        reviewCount = reviewCount.toInt(),
    )
}

@Serializable
private data class WarehouseIdRow(val id: String)

@Serializable
private data class CustomerIdRow(val id: String)
