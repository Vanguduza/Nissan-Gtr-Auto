package co.zw.nissangtr.customer.rpc

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory stub so cart / orders / garage / pay screens compile and exercise
 * flows without a configured Supabase project. Live: [SupabaseRpcClient] via [RpcClientFactory].
 *
 * Documented live RPC → param map (mirrors web + migration):
 * - [RpcNames.CREATE_CUSTOMER_CART]: p_warehouse_id, p_currency?, p_fulfillment_mode?, p_exchange_rate?
 * - [RpcNames.ADD_CUSTOMER_CART_LINE]: p_cart_id, p_stock_item_id, p_uom_id, p_qty
 * - [RpcNames.CHECKOUT_CUSTOMER_CART]: p_cart_id
 * - [RpcNames.GET_CUSTOMER_ORDER]: p_invoice_id
 * - [RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT]: p_sales_invoice_id, p_method, p_metadata?
 * - [RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT]: p_sales_invoice_id, p_method, p_metadata?
 * - [RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE]: p_id?, p_make?, p_model?, p_generation?, p_engine?, p_vin?, p_is_primary?
 * - [RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE]: p_id
 *
 * No PSP secrets or crypto here — intent UUID only.
 */
class FakeRpcClient : RpcClient {
    private val invSeq = AtomicInteger(1)
    private var openCart: CartSummary? = null
    private val invoices = mutableListOf(
        InvoiceSummary(
            id = "00000000-0000-4000-8000-0000000000i1",
            documentNumber = "INV-SEED-001",
            status = "posted",
            currency = CurrencyCode.USD,
            total = 42.0,
            amountPaid = 0.0,
        ),
    )
    private val orders = mutableMapOf<String, CustomerOrder>()
    private val garage = mutableListOf(
        GarageVehicle(
            id = "00000000-0000-4000-8000-0000000000g1",
            make = "Nissan",
            model = "GT-R",
            generation = "R35",
            engine = "VR38DETT",
            vin = null,
            isPrimary = true,
        ),
    )

    init {
        val seed = invoices.first()
        orders[seed.id] = CustomerOrder(
            invoiceId = seed.id,
            documentNumber = seed.documentNumber,
            docType = "invoice",
            status = seed.status,
            fulfillmentMode = FulfillmentMode.IMMEDIATE,
            currency = seed.currency,
            exchangeRateApplied = 1.0,
            subtotal = 40.0,
            total = seed.total,
            amountPaid = seed.amountPaid,
            amountOpen = seed.total - seed.amountPaid,
            cartId = null,
            postedAt = "2026-07-24T00:00:00Z",
            pickListStatus = null,
            deliveryNoteStatus = null,
        )
    }

    override suspend fun createCustomerCart(
        warehouseId: String,
        currency: CurrencyCode,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Double,
    ): String {
        require(warehouseId.isNotBlank()) { "warehouseId required for ${RpcNames.CREATE_CUSTOMER_CART}" }
        require(exchangeRate > 0) { "exchangeRate must be > 0" }
        val id = UUID.randomUUID().toString()
        openCart = CartSummary(
            id = id,
            currency = currency,
            fulfillmentMode = fulfillmentMode,
            status = "open",
        )
        // TODO(live): supabase.rpc(RpcNames.CREATE_CUSTOMER_CART, …)
        return id
    }

    override suspend fun addCustomerCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String {
        require(qty > 0) { "qty must be > 0" }
        val cart = openCart
        require(cart != null && cart.id == cartId && cart.status == "open") {
            "open cart $cartId required for ${RpcNames.ADD_CUSTOMER_CART_LINE}"
        }
        val lineId = UUID.randomUUID().toString()
        openCart = cart.copy(
            lines = cart.lines + CartLineSummary(
                id = lineId,
                stockItemId = stockItemId,
                uomId = uomId,
                qty = qty,
                oemPartNumber = "FAKE-OEM",
            ),
        )
        // TODO(live): supabase.rpc(RpcNames.ADD_CUSTOMER_CART_LINE, …)
        return lineId
    }

    override suspend fun checkoutCustomerCart(cartId: String): String {
        val cart = openCart
        require(cart != null && cart.id == cartId && cart.status == "open") {
            "open cart $cartId required for ${RpcNames.CHECKOUT_CUSTOMER_CART}"
        }
        require(cart.lines.isNotEmpty()) { "cart has no lines" }
        val invId = UUID.randomUUID().toString()
        val n = invSeq.getAndIncrement()
        val total = cart.lines.sumOf { it.qty } * 10.0
        val summary = InvoiceSummary(
            id = invId,
            documentNumber = "INV-FAKE-%03d".format(n),
            status = "posted",
            currency = cart.currency,
            total = total,
            amountPaid = 0.0,
        )
        invoices.add(0, summary)
        orders[invId] = CustomerOrder(
            invoiceId = invId,
            documentNumber = summary.documentNumber,
            docType = "invoice",
            status = "posted",
            fulfillmentMode = cart.fulfillmentMode,
            currency = cart.currency,
            exchangeRateApplied = if (cart.currency == CurrencyCode.USD) 1.0 else 1.0,
            subtotal = total,
            total = total,
            amountPaid = 0.0,
            amountOpen = total,
            cartId = cartId,
            postedAt = "2026-07-24T12:00:00Z",
            pickListStatus = null,
            deliveryNoteStatus = null,
        )
        openCart = cart.copy(status = "checked_out")
        // TODO(live): supabase.rpc(RpcNames.CHECKOUT_CUSTOMER_CART, …)
        return invId
    }

    override suspend fun getOpenCart(): CartSummary? =
        openCart?.takeIf { it.status == "open" }

    override suspend fun getCustomerOrder(invoiceId: String): CustomerOrder {
        require(invoiceId.isNotBlank())
        // TODO(live): supabase.rpc(RpcNames.GET_CUSTOMER_ORDER, …)
        return orders[invoiceId]
            ?: error("order not found for ${RpcNames.GET_CUSTOMER_ORDER}")
    }

    override suspend fun listOwnInvoices(): List<InvoiceSummary> =
        invoices.toList()

    override suspend fun createCustomerContipayIntent(
        salesInvoiceId: String,
        method: ContipayMethod,
        metadataJson: String,
    ): PaymentIntentResult {
        val inv = invoices.find { it.id == salesInvoiceId }
            ?: error("invoice required for ${RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT}")
        require(inv.status == "posted" && inv.total > inv.amountPaid) {
            "only unpaid posted invoices"
        }
        val id = UUID.randomUUID().toString()
        // TODO(live): supabase.rpc(RpcNames.CREATE_CUSTOMER_CONTIPAY_INTENT, …) — no PSP crypto
        return PaymentIntentResult(intentId = id, provider = "contipay")
    }

    override suspend fun createCustomerPaynowIntent(
        salesInvoiceId: String,
        method: PaynowMethod,
        metadataJson: String,
    ): PaymentIntentResult {
        val inv = invoices.find { it.id == salesInvoiceId }
            ?: error("invoice required for ${RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT}")
        require(inv.status == "posted" && inv.total > inv.amountPaid) {
            "only unpaid posted invoices"
        }
        val id = UUID.randomUUID().toString()
        // TODO(live): supabase.rpc(RpcNames.CREATE_CUSTOMER_PAYNOW_INTENT, …) — no PSP crypto
        return PaymentIntentResult(intentId = id, provider = "paynow")
    }

    override suspend fun listGarageVehicles(): List<GarageVehicle> =
        garage.sortedWith(
            compareByDescending<GarageVehicle> { it.isPrimary }
                .thenByDescending { it.id },
        )

    override suspend fun upsertCustomerGarageVehicle(input: GarageVehicleInput): String {
        val hasFitment = listOf(input.make, input.model, input.generation, input.engine, input.vin)
            .any { !it.isNullOrBlank() }
        require(hasFitment) { "vin or fitment required for ${RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE}" }
        val id = input.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        if (input.isPrimary) {
            for (i in garage.indices) {
                garage[i] = garage[i].copy(isPrimary = false)
            }
        }
        val idx = garage.indexOfFirst { it.id == id }
        val row = GarageVehicle(
            id = id,
            make = input.make,
            model = input.model,
            generation = input.generation,
            engine = input.engine,
            vin = input.vin,
            isPrimary = input.isPrimary,
        )
        if (idx >= 0) garage[idx] = row else garage.add(0, row)
        // TODO(live): supabase.rpc(RpcNames.UPSERT_CUSTOMER_GARAGE_VEHICLE, …)
        return id
    }

    override suspend fun deleteCustomerGarageVehicle(id: String) {
        val removed = garage.removeAll { it.id == id }
        require(removed) { "garage vehicle not found for ${RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE}" }
        // TODO(live): supabase.rpc(RpcNames.DELETE_CUSTOMER_GARAGE_VEHICLE, …)
    }
}
