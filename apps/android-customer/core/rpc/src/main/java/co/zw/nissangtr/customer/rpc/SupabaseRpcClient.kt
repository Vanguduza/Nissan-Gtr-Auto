package co.zw.nissangtr.customer.rpc

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Live supabase-kt [RpcClient] for cart / orders / garage / pay-initiate RPCs.
 *
 * Uses anon key + Auth session (never hardcode JWTs). Payment: RPC create only — no PSP crypto.
 */
class SupabaseRpcClient(
    val client: SupabaseClient,
) : RpcClient {

    /** GoTrue Auth plugin — sign-in or [importAccessToken] before authenticated RPCs. */
    val auth: Auth get() = client.auth

    /**
     * Import an existing access token (e.g. from a future login screen).
     * Prefer real sign-in flows when Auth UI exists — do not embed JWTs in source.
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

    companion object {
        private val metadataJson = Json { ignoreUnknownKeys = true }

        fun create(supabaseUrl: String, supabaseAnonKey: String): SupabaseRpcClient {
            val client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseAnonKey,
            ) {
                install(Auth)
                install(Postgrest)
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
    )
}
