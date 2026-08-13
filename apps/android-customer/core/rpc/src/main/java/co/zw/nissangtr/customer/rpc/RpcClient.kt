package co.zw.nissangtr.customer.rpc

/**
 * Thin customer RPC boundary for Compose screens.
 *
 * **Live:** [SupabaseRpcClient] via [RpcClientFactory] when `SUPABASE_URL` +
 * `SUPABASE_ANON_KEY` are set (override with `rpc.forceFake=true`).
 * **Fallback:** [FakeRpcClient].
 *
 * List reads (open cart lines, own invoices, garage, chat) use PostgREST / RLS —
 * not mutation RPCs.
 *
 * Payment intents: create only (no real PSP crypto). Settle stays webhook.
 * Chat mutations: start / post / mark_read / unread_count RPCs.
 * Delivery track: last point + ETA via getDeliveryTrackPoint (no trail).
 * Wishlist / compare / reviews: AuthZ RPCs + PostgREST reads (RLS).
 * Review photos: Storage `review-photos` then add_customer_product_review_photo.
 * QR / camera: Bridge-First only (`bridges/android/`) — never HTML5 / WebView.
 */
interface RpcClient {
    /**
     * Mint retail `customers` row for the signed-in auth user if missing
     * (`ensure_own_customer`). No-op when already linked or unsigned.
     */
    suspend fun ensureOwnCustomerIfNeeded(): String? = null

    /** Mirrors web `searchCatalog` → `search_catalog` (Postgres FTS). */
    suspend fun searchCatalog(mode: SearchMode, query: String): SearchCatalogResponse

    /**
     * Meili Edge proxy ([RpcNames.CATALOG_SEARCH_MEILI_FN]) with user JWT;
     * falls back to [searchCatalog] on failure or missing Meili config.
     */
    suspend fun searchCatalogMeili(
        mode: SearchMode,
        query: String,
        limit: Int = 20,
        facets: List<String>? = null,
    ): SearchCatalogResponse

    /** Browse PLP — PostgREST stock_items + default price list (web `listCatalogProducts` subset). */
    suspend fun listCatalogBrowse(category: String? = null, limit: Int = 50): CatalogBrowseResult

    /** Megazip hierarchy — maker hub. */
    suspend fun listCatalogMakers(): List<EpcMaker>

    suspend fun listCatalogModels(makerSlug: String): List<EpcModel>

    suspend fun listCatalogVariants(makerSlug: String, modelSlug: String): List<EpcVariant>

    suspend fun listCatalogSections(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
    ): List<EpcSection>

    suspend fun getCatalogDiagram(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String,
    ): EpcDiagramResponse

    /**
     * Live `vehicle_master` rows for cascading maker → model → generation → engine.
     * PostgREST SELECT (RLS); never a fabricated maker/model list.
     */
    suspend fun listVehicleMaster(): List<VehicleMasterRow>

    /**
     * Parts that fit a chassis (+ optional engine) via `part_fitment` → `stock_items`.
     * Used after Select vehicle confirm to scope browsing to the live catalog.
     */
    suspend fun listCatalogForVehicle(
        chassisCode: String,
        engineCode: String? = null,
        limit: Int = 50,
    ): CatalogBrowseResult

    /** PDP load — stock_items + price + saleable qty (web `loadCatalogProduct` subset). */
    suspend fun loadCatalogProduct(oem: String): CatalogProduct

    /**
     * Ensure open cart then add line by OEM — mirrors web `addCartLineByOem`.
     * Returns `(cartId, lineId)`.
     */
    suspend fun addCustomerCartLineByOem(oem: String, qty: Double = 1.0): Pair<String, String>

    /**
     * Official daily ZiG rate (ZiG per 1 USD) via [RpcNames.GET_ZIG_EXCHANGE_RATE].
     * Falls back to 1.0 when unset (matches web env default).
     */
    suspend fun fetchZigExchangeRate(asOf: String? = null): Double

    /**
     * D-57: `daily_exchange_rates.id` for the row [fetchZigExchangeRate] would use
     * (latest ZIG `rate_date` ≤ as-of). Null when no ops row (fallback rate only).
     */
    suspend fun fetchZigExchangeRateId(asOf: String? = null): String?

    /** Resolve active MAIN (or first non-quarantine) warehouse — mirrors web `resolveMainWarehouseId`. */
    suspend fun resolveMainWarehouseId(): String

    /**
     * Open cart or create one — mirrors web `ensureOpenCart`.
     * Returns the open [CartSummary].
     */
    suspend fun ensureOpenCart(
        currency: CurrencyCode = CurrencyCode.USD,
        fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
        exchangeRate: Double = 1.0,
    ): CartSummary

    suspend fun createCustomerCart(
        warehouseId: String,
        currency: CurrencyCode = CurrencyCode.USD,
        fulfillmentMode: FulfillmentMode = FulfillmentMode.IMMEDIATE,
        exchangeRate: Double = 1.0,
    ): String

    suspend fun addCustomerCartLine(
        cartId: String,
        stockItemId: String,
        uomId: String,
        qty: Double,
    ): String

    suspend fun checkoutCustomerCart(cartId: String): String

    /** Live: SELECT open storefront cart + lines via PostgREST + RLS. */
    suspend fun getOpenCart(): CartSummary?

    suspend fun getCustomerOrder(invoiceId: String): CustomerOrder

    /** Live: SELECT sales_invoices own rows via RLS. */
    suspend fun listOwnInvoices(): List<InvoiceSummary>

    suspend fun createCustomerContipayIntent(
        salesInvoiceId: String,
        method: ContipayMethod = ContipayMethod.ECOCASH,
        metadataJson: String = "{}",
    ): PaymentIntentResult

    suspend fun createCustomerPaynowIntent(
        salesInvoiceId: String,
        method: PaynowMethod = PaynowMethod.ECOCASH,
        metadataJson: String = "{}",
    ): PaymentIntentResult

    /** EcoCash direct C2B — not ContiPay/Paynow. */
    suspend fun createCustomerEcocashIntent(
        salesInvoiceId: String,
        payerMsisdn: String,
        payerMode: String = "other",
        metadataJson: String = "{}",
    ): PaymentIntentResult

    /** Live: SELECT customer_garage_vehicles own rows. */
    suspend fun listGarageVehicles(): List<GarageVehicle>

    suspend fun upsertCustomerGarageVehicle(input: GarageVehicleInput): String

    suspend fun deleteCustomerGarageVehicle(id: String)

    /** Live: SELECT chat_threads own rows via RLS (ordered by last_message_at). */
    suspend fun listChatThreads(): List<ChatThread>

    /** Live: SELECT chat_messages for thread via RLS. */
    suspend fun listChatMessages(threadId: String): List<ChatMessage>

    suspend fun startChatThread(input: StartChatThreadInput = StartChatThreadInput()): String

    suspend fun postChatMessage(threadId: String, body: String): String

    suspend fun markChatThreadRead(threadId: String)

    /** Unread across all threads when [threadId] is null. */
    suspend fun chatUnreadCount(threadId: String? = null): Int

    /**
     * Last point + ETA for an active delivery only.
     * Pass [deliveryJobId] (owner JWT) and/or share [token]. Returns null when inactive/expired.
     * Never mint — customers do not call `mint_delivery_track_token` (staff/dispatch only).
     */
    suspend fun getDeliveryTrackPoint(
        deliveryJobId: String? = null,
        token: String? = null,
    ): DeliveryTrackPoint?

    /** Live: SELECT customer_wishlist_items (+ stock_items embed) via RLS. */
    suspend fun listWishlist(): List<WishlistItem>

    suspend fun addCustomerWishlistItem(
        stockItemId: String? = null,
        oem: String? = null,
    ): String

    suspend fun removeCustomerWishlistItem(
        wishlistId: String? = null,
        stockItemId: String? = null,
        oem: String? = null,
    )

    suspend fun setWishlistNotifyWhenInStock(
        notify: Boolean,
        wishlistId: String? = null,
        stockItemId: String? = null,
        oem: String? = null,
    ): String

    /**
     * Ensures an open cart then [RpcNames.WISHLIST_MOVE_TO_CART].
     * Returns cart line id.
     */
    suspend fun wishlistMoveToCart(
        wishlistId: String? = null,
        stockItemId: String? = null,
        oem: String? = null,
        qty: Double = 1.0,
        removeFromWishlist: Boolean = true,
    ): String

    /** Live: [RpcNames.LIST_CUSTOMER_COMPARE_ITEMS]. Guests use on-device OEM store in UI. */
    suspend fun listCompareItems(): List<CompareItem>

    suspend fun addCustomerCompareItem(
        stockItemId: String? = null,
        oem: String? = null,
    ): String

    suspend fun removeCustomerCompareItem(
        compareId: String? = null,
        stockItemId: String? = null,
        oem: String? = null,
    )

    /** Live: SELECT own rows on customer_product_reviews via RLS. */
    suspend fun listOwnReviews(): List<ProductReview>

    /** Approved reviews for an OEM (PDP subset). */
    suspend fun listApprovedReviews(oem: String): List<ProductReview>

    suspend fun getProductReviewStats(
        stockItemId: String? = null,
        oem: String? = null,
    ): ProductReviewStats?

    suspend fun submitCustomerProductReview(
        rating: Int,
        body: String = "",
        stockItemId: String? = null,
        oem: String? = null,
    ): String

    /**
     * Upload local image to Storage [RpcNames.REVIEW_PHOTOS_BUCKET], then
     * [RpcNames.ADD_CUSTOMER_PRODUCT_REVIEW_PHOTO]. Returns photo row id.
     */
    suspend fun uploadReviewPhoto(
        reviewId: String,
        localFilePath: String,
        mimeType: String = "image/jpeg",
        sortOrder: Int = 0,
    ): String

    /** Live: SELECT own `customer_addresses` via RLS (default first). */
    suspend fun listOwnAddresses(): List<CustomerAddress>

    /** [RpcNames.UPSERT_CUSTOMER_ADDRESS] — returns address id. Map lat/lng → line2 geo tag. */
    suspend fun upsertCustomerAddress(input: CustomerAddressInput): String

    /** [RpcNames.DELETE_CUSTOMER_ADDRESS]. */
    suspend fun deleteCustomerAddress(id: String)

    /** PostgREST `profiles` own row (RLS). */
    suspend fun loadOwnProfile(): UserProfile?

    /** PostgREST `customers` own row (RLS). */
    suspend fun loadOwnCustomer(): CustomerProfile?

    suspend fun updateOwnFullName(fullName: String)

    /** [RpcNames.UPDATE_OWN_CUSTOMER_PROFILE]. */
    suspend fun updateOwnCustomerContact(patch: CustomerContactPatch)

    /** [RpcNames.SET_OWN_MARKETING_OPT_IN]. */
    suspend fun setOwnMarketingOptIn(optIn: Boolean)

    /** [RpcNames.GET_LOYALTY_BALANCE] — requires own customer id. */
    suspend fun getLoyaltyBalance(customerId: String): LoyaltyBalance

    /**
     * Quarantine CN path — [RpcNames.POST_CUSTOMER_RETURN_CREDIT_NOTE].
     * Unit prices forced server-side from source invoice.
     */
    suspend fun postCustomerReturnCreditNote(
        invoiceId: String,
        lines: List<ReturnCreditNoteLine>,
    ): String

    /** Active kits — PostgREST `item_kits` + components (web `listActiveKits`). */
    suspend fun listActiveKits(limit: Int = 50): List<KitListItem>

    /** Invoice lines for returns — PostgREST `sales_invoice_lines` (web `listInvoiceLines`). */
    suspend fun listInvoiceLines(invoiceId: String): List<InvoiceLineSummary>
}
