import Foundation

/// Live storefront client — PostgREST RPC / select + edge pay-initiate + live chat
/// + privacy-safe delivery last-point track + wishlist / compare / reviews.
///
/// RPC names match web `apps/web/lib/customer-storefront.ts`, `apps/web/lib/chat.ts`,
/// `apps/web/lib/customer-delivery-track.ts`, `customer-wishlist.ts`, `customer-compare.ts`,
/// `customer-reviews.ts`, and AuthZ / delivery migrations.
/// Session: uses anon key as Bearer by default; AuthZ customer RPCs require a
/// **customer user JWT** (`customers.profile_id = auth.uid()`). Pass via
/// GoTrue sign-in → `setAccessToken(_:)`, restored `AuthTokenStore`, or
/// scheme env `SUPABASE_ACCESS_TOKEN`.
/// No ContiPay/Paynow secrets or HMAC in the app binary.
/// Chat updates: hardened PostgREST poll from UI (backoff / foreground resume;
/// no supabase-swift Realtime — Phoenix WS would be a heavy custom client).
@MainActor
public final class LiveStorefrontApi: StorefrontApi {
    public enum RpcName {
        public static let createCustomerCart = "create_customer_cart"
        public static let addCustomerCartLine = "add_customer_cart_line"
        public static let checkoutCustomerCart = "checkout_customer_cart"
        public static let getCustomerOrder = "get_customer_order"
        public static let upsertGarage = "upsert_customer_garage_vehicle"
        public static let deleteGarage = "delete_customer_garage_vehicle"
        public static let createContipayIntent = "create_customer_contipay_intent"
        public static let createPaynowIntent = "create_customer_paynow_intent"
        public static let createEcocashIntent = "create_customer_ecocash_intent"
        public static let startChatThread = "start_chat_thread"
        public static let postChatMessage = "post_chat_message"
        public static let markChatThreadRead = "mark_chat_thread_read"
        public static let chatUnreadCount = "chat_unread_count"
        public static let getDeliveryTrackPoint = "get_delivery_track_point"
        public static let addCustomerWishlistItem = "add_customer_wishlist_item"
        public static let removeCustomerWishlistItem = "remove_customer_wishlist_item"
        public static let setWishlistNotifyWhenInStock = "set_wishlist_notify_when_in_stock"
        public static let wishlistMoveToCart = "wishlist_move_to_cart"
        public static let listCustomerCompareItems = "list_customer_compare_items"
        public static let addCustomerCompareItem = "add_customer_compare_item"
        public static let removeCustomerCompareItem = "remove_customer_compare_item"
        public static let submitCustomerProductReview = "submit_customer_product_review"
        public static let getProductReviewStats = "get_product_review_stats"
        public static let addCustomerProductReviewPhoto = "add_customer_product_review_photo"
        public static let searchCatalog = "search_catalog"
        public static let listCatalogMakers = "list_catalog_makers"
        public static let listCatalogModels = "list_catalog_models"
        public static let listCatalogVariants = "list_catalog_variants"
        public static let listCatalogSections = "list_catalog_sections"
        public static let getCatalogDiagram = "get_catalog_diagram"
        public static let getLoyaltyBalance = "get_loyalty_balance"
        public static let postCustomerReturnCreditNote = "post_customer_return_credit_note"
        public static let getZigExchangeRate = "get_zig_exchange_rate"
        public static let upsertCustomerAddress = "upsert_customer_address"
        public static let deleteCustomerAddress = "delete_customer_address"
        public static let updateOwnCustomerProfile = "update_own_customer_profile"
        public static let setOwnMarketingOptIn = "set_own_marketing_opt_in"
        /// Defense-in-depth after OAuth / session — mint retail `customers` if AuthZ context null.
        public static let ensureOwnCustomer = "ensure_own_customer"
    }

    public enum EdgeName {
        public static let contipayInitiate = "contipay-initiate"
        public static let paynowInitiate = "paynow-initiate"
        public static let ecocashInitiate = "ecocash-initiate"
        /// Meili catalog search proxy — prefer over direct Meili; FTS fallback server-side.
        public static let catalogSearchMeili = "catalog-search-meili"
    }

    /// PostgREST tables for kits browse (no RPC) — mirrors web `listActiveKits`.
    public enum StorefrontTable {
        public static let itemKits = "item_kits"
        public static let itemKitComponents = "item_kit_components"
    }

    public static let reviewPhotosBucket = "review-photos"
    public static let catalogDiagramsBucket = "catalog-diagrams"

    private let client: PostgrestClient
    private let returnURLScheme: String

    public init(
        client: PostgrestClient,
        returnURLScheme: String = "gtr-customer"
    ) {
        self.client = client
        self.returnURLScheme = returnURLScheme
    }

    public convenience init() throws {
        try self.init(
            client: PostgrestClient(
                supabaseURL: AppEnv.supabaseURL,
                anonKey: AppEnv.supabaseAnonKey,
                accessToken: AppEnv.accessToken
            )
        )
    }

    /// Import a customer JWT after GoTrue sign-in (do not hardcode tokens in source).
    /// Empty string clears to the anon key (signed-out Bearer).
    public func setAccessToken(_ token: String) {
        client.accessToken = token.isEmpty ? AppEnv.supabaseAnonKey : token
    }

    /// Own `customers.id` via RLS — nil when AuthZ customer context is missing.
    public func currentCustomerId() async throws -> UUID? {
        let customers: [CustomerIdRow] = try await client.selectDecode(
            table: "customers",
            query: ["select=id", "limit=1"].joined(separator: "&")
        )
        return customers.first?.id
    }

    /// Call after OAuth / password session when `_current_customer_id()` may still be null
    /// (trigger race / legacy users). Idempotent; staff accounts are denied server-side.
    @discardableResult
    public func ensureOwnCustomerIfNeeded() async throws -> UUID? {
        if let existing = try await currentCustomerId() {
            return existing
        }
        return try await client.rpcUUID(RpcName.ensureOwnCustomer, body: [:])
    }

    // MARK: - Cart

    public func createCart(
        warehouseId: UUID,
        currency: StorefrontCurrency,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Decimal
    ) async throws -> UUID {
        try await client.rpcUUID(
            RpcName.createCustomerCart,
            body: [
                "p_warehouse_id": JSONValue.uuid(warehouseId),
                "p_currency": currency.rawValue,
                "p_fulfillment_mode": fulfillmentMode.rawValue,
                "p_exchange_rate": JSONValue.number(exchangeRate),
            ]
        )
    }

    public func fetchZigExchangeRate(asOf: String?) async throws -> Decimal {
        do {
            let data = try await client.rpc(
                RpcName.getZigExchangeRate,
                body: ["p_as_of": asOf as Any? ?? NSNull()]
            )
            if let n = try? JSONDecoder().decode(Decimal.self, from: data), n > 0 {
                return n
            }
            if let d = try? JSONDecoder().decode(Double.self, from: data), d > 0 {
                return Decimal(d)
            }
            if let s = String(data: data, encoding: .utf8),
               let d = Double(s.trimmingCharacters(in: CharacterSet(charactersIn: "\""))),
               d > 0 {
                return Decimal(d)
            }
        } catch {
            // fall through to default
        }
        return 1
    }

    public func resolveMainWarehouseId() async throws -> UUID {
        let main: [WarehouseIdRow] = try await client.selectDecode(
            table: "warehouses",
            query: [
                "select=id",
                "is_active=eq.true",
                "is_quarantine=eq.false",
                "code=eq.MAIN",
                "limit=1",
            ].joined(separator: "&")
        )
        if let id = main.first?.id { return id }
        let any: [WarehouseIdRow] = try await client.selectDecode(
            table: "warehouses",
            query: [
                "select=id",
                "is_active=eq.true",
                "is_quarantine=eq.false",
                "order=code.asc",
                "limit=1",
            ].joined(separator: "&")
        )
        guard let id = any.first?.id else {
            throw StorefrontError.message("No saleable warehouse found.")
        }
        return id
    }

    public func ensureOpenCart(
        currency: StorefrontCurrency,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Decimal
    ) async throws -> CartSummary {
        if let open = try await loadOpenCart() { return open }
        _ = try await createCart(
            warehouseId: try await resolveMainWarehouseId(),
            currency: currency,
            fulfillmentMode: fulfillmentMode,
            exchangeRate: exchangeRate
        )
        guard let open = try await loadOpenCart() else {
            throw StorefrontError.message("ensureOpenCart failed")
        }
        return open
    }

    public func addCartLine(
        cartId: UUID,
        stockItemId: UUID,
        uomId: UUID,
        qty: Decimal
    ) async throws -> UUID {
        try await client.rpcUUID(
            RpcName.addCustomerCartLine,
            body: [
                "p_cart_id": JSONValue.uuid(cartId),
                "p_stock_item_id": JSONValue.uuid(stockItemId),
                "p_uom_id": JSONValue.uuid(uomId),
                "p_qty": JSONValue.number(qty),
            ]
        )
    }

    public func checkoutCart(cartId: UUID) async throws -> UUID {
        try await client.rpcUUID(
            RpcName.checkoutCustomerCart,
            body: ["p_cart_id": JSONValue.uuid(cartId)]
        )
    }

    public func loadOpenCart() async throws -> CartSummary? {
        let carts: [PosCartRow] = try await client.selectDecode(
            table: "pos_carts",
            query: [
                "select=id,currency,fulfillment_mode,exchange_rate_applied,status",
                "status=eq.open",
                "channel=eq.storefront",
                "order=created_at.desc",
                "limit=1",
            ].joined(separator: "&")
        )
        guard let cart = carts.first else { return nil }

        let lines: [PosCartLineRow] = try await client.selectDecode(
            table: "pos_cart_lines",
            query: [
                "select=id,stock_item_id,qty,unit_price,stock_items(oem_part_number,description)",
                "cart_id=eq.\(cart.id.uuidString.lowercased())",
                "order=created_at.asc",
            ].joined(separator: "&")
        )

        return CartSummary(
            id: cart.id,
            currency: cart.currency,
            fulfillmentMode: cart.fulfillmentMode,
            exchangeRateApplied: cart.exchangeRateApplied.value,
            lines: lines.map { line in
                CartLineSummary(
                    id: line.id,
                    stockItemId: line.stockItemId,
                    oemPartNumber: line.stockItems?.oemPartNumber ?? "—",
                    description: line.stockItems?.description,
                    qty: line.qty.value,
                    unitPrice: line.unitPrice.value,
                    currency: cart.currency
                )
            }
        )
    }

    // MARK: - Orders

    public func listOrders() async throws -> [CustomerOrder] {
        let rows: [InvoiceRow] = try await client.selectDecode(
            table: "sales_invoices",
            query: [
                "select=id,document_number,doc_type,status,fulfillment_mode,currency,exchange_rate_applied,subtotal,total,amount_paid,cart_id,posted_at",
                "doc_type=eq.invoice",
                "order=created_at.desc",
                "limit=50",
            ].joined(separator: "&")
        )
        return rows.map { $0.toCustomerOrder() }
    }

    public func getOrder(invoiceId: UUID) async throws -> CustomerOrder {
        let dto: CustomerOrderDTO = try await client.rpcDecode(
            RpcName.getCustomerOrder,
            body: ["p_invoice_id": JSONValue.uuid(invoiceId)]
        )
        return dto.toModel()
    }

    // MARK: - Garage

    public func listGarage() async throws -> [GarageVehicle] {
        let rows: [GarageRow] = try await client.selectDecode(
            table: "customer_garage_vehicles",
            query: [
                "select=id,make,model,generation,engine,vin,is_primary",
                "order=is_primary.desc,created_at.desc",
            ].joined(separator: "&")
        )
        return rows.map {
            GarageVehicle(
                id: $0.id,
                make: $0.make,
                model: $0.model,
                generation: $0.generation,
                engine: $0.engine,
                vin: $0.vin,
                isPrimary: $0.isPrimary
            )
        }
    }

    public func upsertGarage(_ input: GarageVehicleInput) async throws -> UUID {
        var body: [String: Any] = [
            "p_is_primary": input.isPrimary,
        ]
        body["p_id"] = input.id.map { JSONValue.uuid($0) } ?? NSNull()
        body["p_make"] = input.make ?? NSNull()
        body["p_model"] = input.model ?? NSNull()
        body["p_generation"] = input.generation ?? NSNull()
        body["p_engine"] = input.engine ?? NSNull()
        body["p_vin"] = input.vin ?? NSNull()
        return try await client.rpcUUID(RpcName.upsertGarage, body: body)
    }

    public func deleteGarage(id: UUID) async throws {
        _ = try await client.rpc(
            RpcName.deleteGarage,
            body: ["p_id": JSONValue.uuid(id)]
        )
    }

    // MARK: - Pay (edge preferred → RPC fallback; no PSP crypto)

    public func createContipayIntent(
        invoiceId: UUID,
        method: ContipayMethod
    ) async throws -> PaymentIntentResult {
        let returnURL = checkoutReturnURL(invoiceId: invoiceId)
        let cancelURL = checkoutCancelURL(invoiceId: invoiceId)
        let metadata: [String: Any] = [
            "sales_invoice_id": JSONValue.uuid(invoiceId),
            "channel": "storefront",
            "return_url": returnURL.absoluteString,
            "cancel_url": cancelURL.absoluteString,
        ]
        let edgeBody: [String: Any] = [
            "sales_invoice_id": JSONValue.uuid(invoiceId),
            "method": method.rawValue,
            "return_url": returnURL.absoluteString,
            "cancel_url": cancelURL.absoluteString,
            "metadata": metadata,
        ]

        if let fromEdge = try await tryEdgeIntent(
            EdgeName.contipayInitiate,
            body: edgeBody,
            rail: .contipay
        ) {
            return fromEdge
        }

        let intentId = try await client.rpcUUID(
            RpcName.createContipayIntent,
            body: [
                "p_sales_invoice_id": JSONValue.uuid(invoiceId),
                "p_method": method.rawValue,
                "p_metadata": metadata,
            ]
        )
        return PaymentIntentResult(
            intentId: intentId,
            rail: .contipay,
            checkoutURL: nil,
            stubMessage: "ContiPay intent created via RPC (no checkout_url). Settlement is webhook-only — no client PSP crypto."
        )
    }

    public func createPaynowIntent(
        invoiceId: UUID,
        method: PaynowMethod
    ) async throws -> PaymentIntentResult {
        let returnURL = checkoutReturnURL(invoiceId: invoiceId)
        let cancelURL = checkoutCancelURL(invoiceId: invoiceId)
        let metadata: [String: Any] = [
            "sales_invoice_id": JSONValue.uuid(invoiceId),
            "channel": "storefront",
            "return_url": returnURL.absoluteString,
            "cancel_url": cancelURL.absoluteString,
            "result_url": returnURL.absoluteString,
        ]
        let edgeBody: [String: Any] = [
            "sales_invoice_id": JSONValue.uuid(invoiceId),
            "method": method.rawValue,
            "return_url": returnURL.absoluteString,
            "cancel_url": cancelURL.absoluteString,
            "result_url": returnURL.absoluteString,
            "metadata": metadata,
        ]

        if let fromEdge = try await tryEdgeIntent(
            EdgeName.paynowInitiate,
            body: edgeBody,
            rail: .paynow
        ) {
            return fromEdge
        }

        let intentId = try await client.rpcUUID(
            RpcName.createPaynowIntent,
            body: [
                "p_sales_invoice_id": JSONValue.uuid(invoiceId),
                "p_method": method.rawValue,
                "p_metadata": metadata,
            ]
        )
        return PaymentIntentResult(
            intentId: intentId,
            rail: .paynow,
            checkoutURL: nil,
            stubMessage: "Paynow intent created via RPC (no checkout_url). Settlement is webhook-only — no client PSP crypto."
        )
    }

    public func createEcocashIntent(
        invoiceId: UUID,
        payerMsisdn: String,
        payerMode: String
    ) async throws -> PaymentIntentResult {
        let metadata: [String: Any] = [
            "sales_invoice_id": JSONValue.uuid(invoiceId),
            "channel": "ios",
        ]
        let edgeBody: [String: Any] = [
            "sales_invoice_id": JSONValue.uuid(invoiceId),
            "payer_msisdn": payerMsisdn,
            "payer_mode": payerMode,
            "channel": "ios",
            "metadata": metadata,
        ]

        if let fromEdge = try await tryEdgeIntent(
            EdgeName.ecocashInitiate,
            body: edgeBody,
            rail: .ecocash
        ) {
            return fromEdge
        }

        let intentId = try await client.rpcUUID(
            RpcName.createEcocashIntent,
            body: [
                "p_sales_invoice_id": JSONValue.uuid(invoiceId),
                "p_payer_msisdn": payerMsisdn,
                "p_payer_mode": payerMode,
                "p_channel": "ios",
                "p_metadata": metadata,
            ]
        )
        return PaymentIntentResult(
            intentId: intentId,
            rail: .ecocash,
            checkoutURL: nil,
            stubMessage: "EcoCash direct intent created. Approve PIN on the EcoCash handset; settlement is webhook-only."
        )
    }

    // MARK: - Chat

    public func listChatThreads() async throws -> [ChatThread] {
        let rows: [ChatThreadRow] = try await client.selectDecode(
            table: "chat_threads",
            query: [
                "select=id,customer_user_id,kind,status,subject,last_message_at,created_at",
                "order=last_message_at.desc.nullslast",
            ].joined(separator: "&")
        )
        return rows.map { $0.toModel() }
    }

    public func listChatMessages(threadId: UUID) async throws -> [ChatMessage] {
        let rows: [ChatMessageRow] = try await client.selectDecode(
            table: "chat_messages",
            query: [
                "select=id,thread_id,sender_user_id,sender_kind,body,created_at",
                "thread_id=eq.\(threadId.uuidString.lowercased())",
                "order=created_at.asc",
            ].joined(separator: "&")
        )
        return rows.map { $0.toModel() }
    }

    public func startChatThread(_ input: StartChatThreadInput) async throws -> UUID {
        let subject = input.subject?.trimmingCharacters(in: .whitespacesAndNewlines)
        let body = input.body?.trimmingCharacters(in: .whitespacesAndNewlines)
        return try await client.rpcUUID(
            RpcName.startChatThread,
            body: [
                "p_kind": input.kind.rawValue,
                "p_subject": (subject?.isEmpty == false) ? subject! : NSNull(),
                "p_body": (body?.isEmpty == false) ? body! : NSNull(),
            ]
        )
    }

    public func postChatMessage(threadId: UUID, body: String) async throws -> UUID {
        try await client.rpcUUID(
            RpcName.postChatMessage,
            body: [
                "p_thread_id": JSONValue.uuid(threadId),
                "p_body": body,
            ]
        )
    }

    public func markChatThreadRead(threadId: UUID) async throws {
        _ = try await client.rpc(
            RpcName.markChatThreadRead,
            body: ["p_thread_id": JSONValue.uuid(threadId)]
        )
    }

    public func chatUnreadCount(threadId: UUID?) async throws -> Int {
        let body: [String: Any] =
            threadId.map { ["p_thread_id": JSONValue.uuid($0)] }
            ?? ["p_thread_id": NSNull()]
        // PostgREST may return a bare JSON number.
        let data = try await client.rpc(RpcName.chatUnreadCount, body: body)
        if let n = try? JSONDecoder().decode(Int.self, from: data) {
            return n
        }
        if let d = try? JSONDecoder().decode(Double.self, from: data) {
            return Int(d)
        }
        if let s = try? JSONDecoder().decode(String.self, from: data), let n = Int(s) {
            return n
        }
        return 0
    }

    // MARK: - Delivery track (last point + ETA only)

    public func getDeliveryTrackPoint(_ ref: DeliveryTrackRef) async throws -> DeliveryTrackPoint? {
        var body: [String: Any] = [:]
        switch ref {
        case .job(let id):
            body["p_delivery_job_id"] = JSONValue.uuid(id)
            body["p_token"] = NSNull()
        case .token(let raw):
            let token = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard token.count >= 8 else {
                throw StorefrontError.message("Invalid track token.")
            }
            body["p_delivery_job_id"] = NSNull()
            body["p_token"] = token
        }

        // Empty / null / [] = inactive, expired, or non-dispatched — not an error.
        // SECURITY: take at most one row — never accumulate a trail client-side.
        let rows: [DeliveryTrackPointDTO] = try await client.rpcDecodeArrayAllowEmpty(
            RpcName.getDeliveryTrackPoint,
            body: body
        )
        guard let first = rows.first else { return nil }
        return first.toModel()
    }

    // MARK: - Wishlist

    public func listWishlist() async throws -> [WishlistItem] {
        let rows: [WishlistRow] = try await client.selectDecode(
            table: "customer_wishlist_items",
            query: [
                "select=id,stock_item_id,notify_when_in_stock,created_at,stock_items(id,oem_part_number,description)",
                "order=created_at.desc",
                "limit=100",
            ].joined(separator: "&")
        )
        return rows.map { $0.toModel() }
    }

    public func addWishlistItem(stockItemId: UUID?, oem: String?) async throws -> UUID {
        try await client.rpcUUID(
            RpcName.addCustomerWishlistItem,
            body: stockItemOemBody(stockItemId: stockItemId, oem: oem)
        )
    }

    public func removeWishlistItem(wishlistId: UUID?, stockItemId: UUID?, oem: String?) async throws {
        var body = stockItemOemBody(stockItemId: stockItemId, oem: oem)
        body["p_wishlist_id"] = wishlistId.map { JSONValue.uuid($0) } ?? NSNull()
        _ = try await client.rpc(RpcName.removeCustomerWishlistItem, body: body)
    }

    public func setWishlistNotifyWhenInStock(
        notify: Bool,
        wishlistId: UUID?,
        stockItemId: UUID?,
        oem: String?
    ) async throws -> UUID {
        var body = stockItemOemBody(stockItemId: stockItemId, oem: oem)
        body["p_notify"] = notify
        body["p_wishlist_id"] = wishlistId.map { JSONValue.uuid($0) } ?? NSNull()
        return try await client.rpcUUID(RpcName.setWishlistNotifyWhenInStock, body: body)
    }

    public func wishlistMoveToCart(
        wishlistId: UUID?,
        stockItemId: UUID?,
        oem: String?,
        qty: Decimal,
        removeFromWishlist: Bool
    ) async throws -> UUID {
        let cartId = try await ensureOpenCartId()
        var body = stockItemOemBody(stockItemId: stockItemId, oem: oem)
        body["p_cart_id"] = JSONValue.uuid(cartId)
        body["p_qty"] = JSONValue.number(qty)
        body["p_remove_from_wishlist"] = removeFromWishlist
        body["p_wishlist_id"] = wishlistId.map { JSONValue.uuid($0) } ?? NSNull()
        return try await client.rpcUUID(RpcName.wishlistMoveToCart, body: body)
    }

    // MARK: - Compare

    public func listCompareItems() async throws -> [CompareItem] {
        let rows: [CompareItemDTO] = try await client.rpcDecodeArrayAllowEmpty(
            RpcName.listCustomerCompareItems,
            body: [:]
        )
        return rows.map { $0.toModel() }
    }

    public func addCompareItem(stockItemId: UUID?, oem: String?) async throws -> UUID {
        try await client.rpcUUID(
            RpcName.addCustomerCompareItem,
            body: stockItemOemBody(stockItemId: stockItemId, oem: oem)
        )
    }

    public func removeCompareItem(compareId: UUID?, stockItemId: UUID?, oem: String?) async throws {
        var body = stockItemOemBody(stockItemId: stockItemId, oem: oem)
        body["p_compare_id"] = compareId.map { JSONValue.uuid($0) } ?? NSNull()
        _ = try await client.rpc(RpcName.removeCustomerCompareItem, body: body)
    }

    // MARK: - Reviews

    public func listOwnReviews() async throws -> [ProductReview] {
        let customers: [CustomerIdRow] = try await client.selectDecode(
            table: "customers",
            query: ["select=id", "limit=1"].joined(separator: "&")
        )
        guard let customerId = customers.first?.id else { return [] }
        let rows: [ProductReviewRow] = try await client.selectDecode(
            table: "customer_product_reviews",
            query: [
                "select=id,stock_item_id,rating,body,status,created_at,stock_items(id,oem_part_number,description)",
                "customer_id=eq.\(customerId.uuidString.lowercased())",
                "order=created_at.desc",
                "limit=100",
            ].joined(separator: "&")
        )
        return rows.map { $0.toModel() }
    }

    public func listApprovedReviews(oem: String) async throws -> [ProductReview] {
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else { return [] }
        let items: [StockItemIdRow] = try await client.selectDecode(
            table: "stock_items",
            query: [
                "select=id",
                "oem_part_number=ilike.\(Self.percentEncodeQueryValue(needle))",
                "limit=1",
            ].joined(separator: "&")
        )
        guard let item = items.first else { return [] }
        let rows: [ProductReviewRow] = try await client.selectDecode(
            table: "customer_product_reviews",
            query: [
                "select=id,stock_item_id,rating,body,status,created_at,stock_items(id,oem_part_number,description)",
                "stock_item_id=eq.\(item.id.uuidString.lowercased())",
                "status=eq.approved",
                "order=created_at.desc",
                "limit=40",
            ].joined(separator: "&")
        )
        return rows.map { $0.toModel() }
    }

    public func getProductReviewStats(stockItemId: UUID?, oem: String?) async throws -> ProductReviewStats? {
        let rows: [ProductReviewStatsDTO] = try await client.rpcDecodeArrayAllowEmpty(
            RpcName.getProductReviewStats,
            body: stockItemOemBody(stockItemId: stockItemId, oem: oem)
        )
        return rows.first?.toModel()
    }

    public func submitProductReview(
        rating: Int,
        body: String,
        stockItemId: UUID?,
        oem: String?
    ) async throws -> UUID {
        var rpcBody = stockItemOemBody(stockItemId: stockItemId, oem: oem)
        rpcBody["p_rating"] = rating
        rpcBody["p_body"] = body
        return try await client.rpcUUID(RpcName.submitCustomerProductReview, body: rpcBody)
    }

    public func uploadReviewPhoto(
        reviewId: UUID,
        photo: ReviewPhotoUpload,
        sortOrder: Int
    ) async throws -> UUID {
        let ext = Self.safeImageExtension(photo.fileExtension)
        let objectPath = "\(reviewId.uuidString.lowercased())/\(UUID().uuidString.lowercased()).\(ext)"
        try await client.uploadStorageObject(
            bucket: Self.reviewPhotosBucket,
            path: objectPath,
            data: photo.data,
            contentType: photo.contentType.isEmpty ? "image/\(ext == "jpg" ? "jpeg" : ext)" : photo.contentType
        )
        return try await client.rpcUUID(
            RpcName.addCustomerProductReviewPhoto,
            body: [
                "p_review_id": JSONValue.uuid(reviewId),
                "p_storage_path": objectPath,
                "p_sort_order": sortOrder,
            ]
        )
    }

    // MARK: - Catalog

    public func searchCatalog(mode: CatalogSearchMode, query: String) async throws -> SearchCatalogResponse {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw StorefrontError.message("search query required")
        }
        let data = try await client.rpc(
            RpcName.searchCatalog,
            body: [
                "p_mode": mode.rawValue,
                "p_query": trimmed,
            ]
        )
        return try CatalogSearchParser.parse(
            data: data,
            fallbackMode: mode,
            fallbackQuery: trimmed,
            backend: "fts"
        )
    }

    public func searchCatalogMeili(
        mode: CatalogSearchMode,
        query: String,
        limit: Int,
        facets: [String]?
    ) async throws -> SearchCatalogResponse {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw StorefrontError.message("search query required")
        }
        var edgeBody: [String: Any] = [
            "mode": mode.rawValue,
            "query": trimmed,
            "limit": min(max(limit, 1), 50),
        ]
        if let facets, !facets.isEmpty {
            edgeBody["facets"] = facets
        }
        do {
            let data = try await client.invokeFunction(EdgeName.catalogSearchMeili, body: edgeBody)
            if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               obj["error"] != nil {
                return try await searchCatalog(mode: mode, query: trimmed)
            }
            let parsed = try CatalogSearchParser.parse(
                data: data,
                fallbackMode: mode,
                fallbackQuery: trimmed,
                backend: "meili"
            )
            if parsed.parts.isEmpty {
                return try await searchCatalog(mode: mode, query: trimmed)
            }
            return parsed
        } catch {
            return try await searchCatalog(mode: mode, query: trimmed)
        }
    }

    public func listCatalogBrowse(category: String?, limit: Int) async throws -> CatalogBrowseResult {
        let cap = min(max(limit, 1), 100)
        let cat = category?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        let oemFilter = cat.map { try await resolveOemFilterForCategory($0) }
        if let oemFilter, oemFilter.isEmpty {
            return CatalogBrowseResult(items: [], categories: [])
        }

        var queryParts = [
            "select=id,oem_part_number,description,reorder_point",
            "order=oem_part_number.asc",
            "limit=\(cap)",
        ]
        if let oemFilter, !oemFilter.isEmpty {
            let encoded = oemFilter.map { Self.percentEncodeQueryValue($0) }.joined(separator: ",")
            queryParts.insert("oem_part_number=in.(\(encoded))", at: 1)
        }
        let items: [StockItemBrowseRow] = try await client.selectDecode(
            table: "stock_items",
            query: queryParts.joined(separator: "&")
        )
        if items.isEmpty {
            return CatalogBrowseResult(items: [], categories: [])
        }
        let ids = items.map(\.id)
        let oems = items.map(\.oemPartNumber)
        let priceByItem = try await loadDefaultPrices(ids)
        let qtyByItem = try await loadSaleableQty(ids)
        let catByOem = try await loadCategoryByOem(oems)
        let list = items.map { row in
            let price = priceByItem[row.id]
            let usd = price.map { Decimal($0.unitPrice) }
            return CatalogListItem(
                stockItemId: row.id,
                oem: row.oemPartNumber,
                name: row.description?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                    ?? row.oemPartNumber,
                stock: catalogStockState(qty: qtyByItem[row.id] ?? 0, reorderPoint: row.reorderPoint),
                usd: usd,
                category: catByOem[row.oemPartNumber] ?? cat
            )
        }
        return CatalogBrowseResult(items: list, categories: [])
    }

    public func listVehicleMaster() async throws -> [VehicleMasterRow] {
        let rows: [VehicleMasterDbRow] = try await client.selectDecode(
            table: "vehicle_master",
            query: [
                "select=id,vin_prefix,chassis_code,engine_code,production_year,model_variant",
                "order=model_variant.asc",
                "limit=500",
            ].joined(separator: "&")
        )
        return rows.map {
            VehicleMasterRow(
                id: $0.id,
                vinPrefix: $0.vinPrefix,
                chassisCode: $0.chassisCode,
                engineCode: $0.engineCode,
                productionYear: $0.productionYear,
                modelVariant: $0.modelVariant
            )
        }
    }

    public func listCatalogMakers() async throws -> [EpcMaker] {
        let data = try await client.rpc(RpcName.listCatalogMakers, body: [:])
        return try EpcCatalogParser.parseMakers(data)
    }

    public func listCatalogModels(makerSlug: String) async throws -> [EpcModel] {
        let data = try await client.rpc(
            RpcName.listCatalogModels,
            body: ["p_maker_slug": makerSlug]
        )
        return try EpcCatalogParser.parseModels(data)
    }

    public func listCatalogVariants(makerSlug: String, modelSlug: String) async throws -> [EpcVariant] {
        let data = try await client.rpc(
            RpcName.listCatalogVariants,
            body: [
                "p_maker_slug": makerSlug,
                "p_model_slug": modelSlug,
            ]
        )
        return try EpcCatalogParser.parseVariants(data)
    }

    public func listCatalogSections(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String
    ) async throws -> [EpcSection] {
        let data = try await client.rpc(
            RpcName.listCatalogSections,
            body: [
                "p_maker_slug": makerSlug,
                "p_model_slug": modelSlug,
                "p_variant_slug": variantSlug,
            ]
        )
        return try EpcCatalogParser.parseSections(data)
    }

    public func getCatalogDiagram(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String
    ) async throws -> EpcDiagramResponse {
        let data = try await client.rpc(
            RpcName.getCatalogDiagram,
            body: [
                "p_maker_slug": makerSlug,
                "p_model_slug": modelSlug,
                "p_variant_slug": variantSlug,
                "p_section_slug": sectionSlug,
            ]
        )
        var parsed = try EpcCatalogParser.parseDiagram(data)
        if (parsed.imageUrl == nil || parsed.imageUrl?.isEmpty == true),
           let path = parsed.storagePath?.trimmingCharacters(in: .whitespacesAndNewlines),
           !path.isEmpty
        {
            if path.hasPrefix("http") {
                parsed.imageUrl = path
            } else {
                let base = client.baseURL.absoluteString
                    .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                let clean = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                parsed.imageUrl =
                    "\(base)/storage/v1/object/public/\(Self.catalogDiagramsBucket)/\(clean)"
            }
        }
        return parsed
    }

    public func listCatalogForVehicle(
        chassisCode: String,
        engineCode: String?,
        limit: Int
    ) async throws -> CatalogBrowseResult {
        let chassis = chassisCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !chassis.isEmpty else { throw StorefrontError.message("chassis required") }
        let cap = min(max(limit, 1), 100)
        let engine = engineCode?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        var fitQuery = [
            "select=oem_part_number",
            "chassis_code=eq.\(Self.percentEncodeQueryValue(chassis))",
            "limit=200",
        ]
        if let engine {
            fitQuery.insert("engine_code=eq.\(Self.percentEncodeQueryValue(engine))", at: 2)
        }
        let oemRows: [OemOnlyRow] = try await client.selectDecode(
            table: "part_fitment",
            query: fitQuery.joined(separator: "&")
        )
        let oemFilter = Array(Set(oemRows.map(\.oemPartNumber)))
        if oemFilter.isEmpty {
            return CatalogBrowseResult(items: [], categories: [])
        }
        let encoded = oemFilter.map { Self.percentEncodeQueryValue($0) }.joined(separator: ",")
        let items: [StockItemBrowseRow] = try await client.selectDecode(
            table: "stock_items",
            query: [
                "select=id,oem_part_number,description,reorder_point",
                "oem_part_number=in.(\(encoded))",
                "order=oem_part_number.asc",
                "limit=\(cap)",
            ].joined(separator: "&")
        )
        if items.isEmpty {
            return CatalogBrowseResult(items: [], categories: [])
        }
        let ids = items.map(\.id)
        let oems = items.map(\.oemPartNumber)
        let priceByItem = try await loadDefaultPrices(ids)
        let qtyByItem = try await loadSaleableQty(ids)
        let catByOem = try await loadCategoryByOem(oems)
        let list = items.map { row in
            let price = priceByItem[row.id]
            return CatalogListItem(
                stockItemId: row.id,
                oem: row.oemPartNumber,
                name: row.description?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                    ?? row.oemPartNumber,
                stock: catalogStockState(qty: qtyByItem[row.id] ?? 0, reorderPoint: row.reorderPoint),
                usd: price.map { Decimal($0.unitPrice) },
                category: catByOem[row.oemPartNumber]
            )
        }
        return CatalogBrowseResult(items: list, categories: [])
    }

    public func loadCatalogProduct(oem: String) async throws -> CatalogProduct {
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else { throw StorefrontError.message("OEM required") }
        let items: [StockItemPdpRow] = try await client.selectDecode(
            table: "stock_items",
            query: [
                "select=id,oem_part_number,description,reorder_point,base_uom_id",
                "oem_part_number=eq.\(Self.percentEncodeQueryValue(needle))",
                "limit=1",
            ].joined(separator: "&")
        )
        guard let item = items.first else {
            throw StorefrontError.message("Part not found: \(needle)")
        }
        guard let uomId = item.baseUomId else {
            throw StorefrontError.message("Part \(needle) has no base UOM")
        }
        let price = try await loadDefaultPrices([item.id])[item.id]
        let qty = try await loadSaleableQty([item.id])[item.id] ?? 0
        let fitmentMeta = try await loadFitmentMeta(oem: item.oemPartNumber)
        let diagramUrl = try await loadDiagramPublicUrl(oem: item.oemPartNumber)
        let replaces = try await loadReplaces(oem: item.oemPartNumber)
        var imageUrls: [String] = []
        if let diagramUrl { imageUrls.append(diagramUrl) }

        return CatalogProduct(
            stockItemId: item.id,
            baseUomId: uomId,
            oem: item.oemPartNumber,
            name: item.description?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                ?? item.oemPartNumber,
            brand: "Nissan OE",
            category: fitmentMeta.category,
            usd: price.map { Decimal($0.unitPrice) },
            stock: catalogStockState(qty: qty, reorderPoint: item.reorderPoint),
            coreCharge: Decimal(price?.coreCharge ?? 0),
            fitmentLines: fitmentMeta.lines,
            imageUrls: imageUrls,
            diagramUrl: diagramUrl,
            specs: fitmentMeta.specs,
            replaces: replaces
        )
    }

    public func addCartLineByOem(oem: String, qty: Decimal) async throws -> (cartId: UUID, lineId: UUID) {
        guard qty > 0 else { throw StorefrontError.message("qty must be > 0") }
        let product = try await loadCatalogProduct(oem: oem)
        let cartId = try await ensureOpenCartId()
        let lineId = try await addCartLine(
            cartId: cartId,
            stockItemId: product.stockItemId,
            uomId: product.baseUomId,
            qty: qty
        )
        return (cartId, lineId)
    }

    // MARK: - Addresses

    public func listOwnAddresses() async throws -> [CustomerAddress] {
        let rows: [AddressRow] = try await client.selectDecode(
            table: "customer_addresses",
            query: [
                "select=id,label,line1,line2,city,province,postal_code,country,is_default,created_at,updated_at",
                "order=is_default.desc,created_at.desc",
            ].joined(separator: "&")
        )
        return rows.map { $0.toModel() }
    }

    public func upsertCustomerAddress(_ input: CustomerAddressInput) async throws -> UUID {
        let line1 = input.line1.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !line1.isEmpty else {
            throw StorefrontError.message("line1 required for \(RpcName.upsertCustomerAddress)")
        }
        let line2 = AddressGeo.embed(
            line2: input.line2,
            latitude: input.latitude,
            longitude: input.longitude
        )
        let country = input.country.trimmingCharacters(in: .whitespacesAndNewlines)
        return try await client.rpcUUID(
            RpcName.upsertCustomerAddress,
            body: [
                "p_id": input.id.map { JSONValue.uuid($0) } ?? NSNull(),
                "p_label": input.label,
                "p_line1": line1,
                "p_line2": line2 as Any? ?? NSNull(),
                "p_city": input.city as Any? ?? NSNull(),
                "p_province": input.province as Any? ?? NSNull(),
                "p_postal_code": input.postalCode as Any? ?? NSNull(),
                "p_country": country.isEmpty ? "Zimbabwe" : country,
                "p_is_default": input.isDefault,
            ]
        )
    }

    public func deleteCustomerAddress(id: UUID) async throws {
        _ = try await client.rpc(
            RpcName.deleteCustomerAddress,
            body: ["p_id": JSONValue.uuid(id)]
        )
    }

    // MARK: Profile

    public func loadOwnProfile() async throws -> UserProfile? {
        guard let userId = currentUserId() else { return nil }
        let rows: [ProfileRow] = try await client.selectDecode(
            table: "profiles",
            query: [
                "select=id,full_name",
                "id=eq.\(userId.uuidString.lowercased())",
                "limit=1",
            ].joined(separator: "&")
        )
        return rows.first?.toModel()
    }

    public func loadOwnCustomer() async throws -> CustomerProfile? {
        let rows: [CustomerProfileRow] = try await client.selectDecode(
            table: "customers",
            query: [
                "select=id,display_name,email,phone_e164,whatsapp_e164,sms_receipts,email_receipts,whatsapp_receipts,marketing_opt_in,last_promotional_message_at",
                "limit=1",
            ].joined(separator: "&")
        )
        return rows.first?.toModel()
    }

    public func updateOwnFullName(_ fullName: String) async throws {
        guard let userId = currentUserId() else {
            throw StorefrontError.notAuthenticated
        }
        let trimmed = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        try await client.patch(
            table: "profiles",
            query: "id=eq.\(userId.uuidString.lowercased())",
            body: ["full_name": trimmed.isEmpty ? NSNull() : trimmed]
        )
    }

    public func updateOwnCustomerContact(_ patch: CustomerContactPatch) async throws {
        var body: [String: Any] = [:]
        body["p_display_name"] = patch.displayName as Any? ?? NSNull()
        body["p_email"] = patch.email as Any? ?? NSNull()
        body["p_phone_e164"] = patch.phoneE164 as Any? ?? NSNull()
        body["p_whatsapp_e164"] = patch.whatsappE164 as Any? ?? NSNull()
        body["p_sms_receipts"] = patch.smsReceipts as Any? ?? NSNull()
        body["p_email_receipts"] = patch.emailReceipts as Any? ?? NSNull()
        body["p_whatsapp_receipts"] = patch.whatsappReceipts as Any? ?? NSNull()
        _ = try await client.rpc(RpcName.updateOwnCustomerProfile, body: body)
    }

    public func setOwnMarketingOptIn(_ optIn: Bool) async throws {
        _ = try await client.rpc(
            RpcName.setOwnMarketingOptIn,
            body: ["p_opt_in": optIn]
        )
    }

    // MARK: Loyalty / returns / kits

    public func getLoyaltyBalance(customerId: UUID) async throws -> LoyaltyBalance {
        let rows: [LoyaltyBalanceRow] = try await client.rpcDecodeArrayAllowEmpty(
            RpcName.getLoyaltyBalance,
            body: ["p_customer_id": JSONValue.uuid(customerId)]
        )
        guard let row = rows.first else {
            throw StorefrontError.message("get_loyalty_balance returned no row")
        }
        return row.toModel(fallbackCustomerId: customerId)
    }

    public func postCustomerReturnCreditNote(
        invoiceId: UUID,
        lines: [ReturnCreditNoteLine]
    ) async throws -> UUID {
        guard !lines.isEmpty else { throw StorefrontError.message("return lines required") }
        let payload: [[String: Any]] = lines.map { line in
            [
                "stock_item_id": JSONValue.uuid(line.stockItemId),
                "uom_id": JSONValue.uuid(line.uomId),
                "qty": JSONValue.number(line.qty),
            ]
        }
        let data = try await client.rpc(
            RpcName.postCustomerReturnCreditNote,
            body: [
                "p_invoice_id": JSONValue.uuid(invoiceId),
                "p_lines": payload,
            ]
        )
        if let s = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: CharacterSet(charactersIn: "\" \n\r\t")),
           let id = UUID(uuidString: s) {
            return id
        }
        let raw: String = try JSONDecoder().decode(String.self, from: data)
        guard let id = UUID(uuidString: raw) else {
            throw StorefrontError.message("post_customer_return_credit_note returned invalid UUID")
        }
        return id
    }

    public func listActiveKits(limit: Int) async throws -> [KitListItem] {
        let cap = min(max(limit, 1), 50)
        let kits: [ItemKitRow] = try await client.selectDecode(
            table: StorefrontTable.itemKits,
            query: [
                "select=id,sell_mode,stock_item_id,stock_items(oem_part_number,description)",
                "is_active=eq.true",
                "order=created_at.desc",
                "limit=\(cap)",
            ].joined(separator: "&")
        )
        if kits.isEmpty { return [] }
        let kitIds = kits.map(\.id)
        let idList = kitIds.map { $0.uuidString.lowercased() }.joined(separator: ",")
        let comps: [KitComponentRow] = try await client.selectDecode(
            table: StorefrontTable.itemKitComponents,
            query: [
                "select=kit_id,qty,stock_items:component_item_id(oem_part_number,description)",
                "kit_id=in.(\(idList))",
            ].joined(separator: "&")
        )
        var byKit: [UUID: [KitComponent]] = [:]
        for c in comps {
            let item = c.stockItems
            let comp = KitComponent(
                oem: item?.oemPartNumber ?? "—",
                name: item?.description?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                    ?? item?.oemPartNumber ?? "Component",
                qty: c.qty
            )
            byKit[c.kitId, default: []].append(comp)
        }
        return kits.map { k in
            let item = k.stockItems
            return KitListItem(
                kitId: k.id,
                stockItemId: k.stockItemId,
                oem: item?.oemPartNumber ?? k.stockItemId.uuidString,
                name: item?.description?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                    ?? item?.oemPartNumber ?? "Kit",
                sellMode: k.sellMode,
                components: byKit[k.id] ?? []
            )
        }
    }

    public func listInvoiceLines(invoiceId: UUID) async throws -> [InvoiceLineSummary] {
        let rows: [InvoiceLineRow] = try await client.selectDecode(
            table: "sales_invoice_lines",
            query: [
                "select=id,stock_item_id,uom_id,qty,stock_items(oem_part_number,description)",
                "invoice_id=eq.\(invoiceId.uuidString.lowercased())",
                "order=created_at.asc",
            ].joined(separator: "&")
        )
        return rows.map { $0.toModel() }
    }

    // MARK: - Private

    private func loadFitmentMeta(oem: String) async throws -> FitmentMeta {
        let rows: [FitmentMetaRow] = try await client.selectDecode(
            table: "part_fitment",
            query: [
                "select=chassis_code,engine_code,pnc_code,pnc_categories(category_name,subcategory_name)",
                "oem_part_number=eq.\(Self.percentEncodeQueryValue(oem))",
                "limit=12",
            ].joined(separator: "&")
        )
        let lines = rows.compactMap { row -> String? in
            let bits = [row.chassisCode, row.engineCode, row.pncCode.map { "PNC \($0)" }]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            return bits.isEmpty ? nil : bits.joined(separator: " · ")
        }
        let primary = rows.first
        var specs: [String] = []
        if let pnc = primary?.pncCode?.trimmingCharacters(in: .whitespacesAndNewlines), !pnc.isEmpty {
            specs.append("PNC \(pnc)")
        }
        if let chassis = primary?.chassisCode?.trimmingCharacters(in: .whitespacesAndNewlines), !chassis.isEmpty {
            specs.append("Chassis \(chassis)")
        }
        if let engine = primary?.engineCode?.trimmingCharacters(in: .whitespacesAndNewlines), !engine.isEmpty {
            specs.append("Engine \(engine)")
        }
        let category = primary?.pncCategories?.categoryName?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            ?? primary?.pncCategories?.subcategoryName?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        return FitmentMeta(lines: lines, specs: specs, category: category)
    }

    private func loadReplaces(oem: String) async throws -> [String] {
        let xrefs: [OeNumberRow] = try await client.selectDecode(
            table: "oe_cross_refs",
            query: [
                "select=oe_number",
                "oem_part_number=eq.\(Self.percentEncodeQueryValue(oem))",
                "limit=20",
            ].joined(separator: "&")
        )
        let superseded: [SupersededRow] = try await client.selectDecode(
            table: "part_fitment",
            query: [
                "select=superseded_by",
                "oem_part_number=eq.\(Self.percentEncodeQueryValue(oem))",
                "limit=20",
            ].joined(separator: "&")
        )
        return (xrefs.compactMap(\.oeNumber) + superseded.compactMap(\.supersededBy))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.caseInsensitiveCompare(oem).equals(.orderedSame) }
            .uniqued()
    }

    private func loadDiagramPublicUrl(oem: String) async throws -> String? {
        let rows: [DiagramPathRow] = try await client.selectDecode(
            table: "part_fitment",
            query: [
                "select=diagram_path",
                "oem_part_number=eq.\(Self.percentEncodeQueryValue(oem))",
                "limit=20",
            ].joined(separator: "&")
        )
        guard let path = rows.first(where: { !($0.diagramPath?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true) })?
            .diagramPath?
            .trimmingCharacters(in: .whitespacesAndNewlines),
            !path.isEmpty else { return nil }
        if path.lowercased().hasPrefix("http://") || path.lowercased().hasPrefix("https://") {
            return path
        }
        let base = client.baseURL.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let clean = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return "\(base)/storage/v1/object/public/\(Self.catalogDiagramsBucket)/\(clean)"
    }

    private func resolveOemFilterForCategory(_ categoryLabel: String) async throws -> [String] {
        let cat = categoryLabel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cat.isEmpty else { return [] }
        // Fetch + match client-side — exact ILIKE misses EPC groups like
        // "BRAKE PIPING & CONTROL" for shop slug "brakes".
        let pncRows: [PncCategoryMatchRow] = try await client.selectDecode(
            table: "pnc_categories",
            query: [
                "select=pnc_code,category_name,subcategory_name",
                "limit=2000",
            ].joined(separator: "&")
        )
        let codes = pncRows
            .filter {
                CatalogCategoryFilter.matches(
                    filter: cat,
                    fields: $0.categoryName,
                    $0.subcategoryName
                )
            }
            .map(\.pncCode)
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        if codes.isEmpty { return [] }
        let codeList = codes.map { Self.percentEncodeQueryValue($0) }.joined(separator: ",")
        let fits: [OemOnlyRow] = try await client.selectDecode(
            table: "part_fitment",
            query: [
                "select=oem_part_number",
                "pnc_code=in.(\(codeList))",
                "limit=200",
            ].joined(separator: "&")
        )
        return fits.map(\.oemPartNumber).uniqued()
    }

    private func loadCategoryByOem(_ oems: [String]) async throws -> [String: String] {
        guard !oems.isEmpty else { return [:] }
        let encoded = oems.map { Self.percentEncodeQueryValue($0) }.joined(separator: ",")
        let rows: [FitmentCategoryRow] = try await client.selectDecode(
            table: "part_fitment",
            query: [
                "select=oem_part_number,pnc_categories(category_name)",
                "oem_part_number=in.(\(encoded))",
                "limit=200",
            ].joined(separator: "&")
        )
        var out: [String: String] = [:]
        for row in rows {
            if out[row.oemPartNumber] != nil { continue }
            if let name = row.pncCategories?.categoryName?.trimmingCharacters(in: .whitespacesAndNewlines),
               !name.isEmpty {
                out[row.oemPartNumber] = name
            }
        }
        return out
    }

    private func currentUserId() -> UUID? {
        JWTSubjectParser.userId(from: client.accessToken)
    }

    private struct FitmentMeta {
        let lines: [String]
        let specs: [String]
        let category: String?
    }

    private func loadSaleableQty(_ stockItemIds: [UUID]) async throws -> [UUID: Double] {
        guard !stockItemIds.isEmpty else { return [:] }
        let idList = stockItemIds.map { $0.uuidString.lowercased() }.joined(separator: ",")
        let rows: [StockLevelQtyRow] = try await client.selectDecode(
            table: "stock_levels",
            query: [
                "select=stock_item_id,quantity,warehouses!inner(is_quarantine,is_active)",
                "stock_item_id=in.(\(idList))",
            ].joined(separator: "&")
        )
        var out: [UUID: Double] = [:]
        for row in rows {
            guard !row.warehouses.isQuarantine, row.warehouses.isActive else { continue }
            out[row.stockItemId, default: 0] += row.quantity
        }
        return out
    }

    private func loadDefaultPrices(_ stockItemIds: [UUID]) async throws -> [UUID: CatalogPriceRow] {
        guard !stockItemIds.isEmpty else { return [:] }
        let lists: [PriceListHeadRow] = try await client.selectDecode(
            table: "price_lists",
            query: [
                "select=id,currency",
                "is_default=eq.true",
                "is_active=eq.true",
                "limit=1",
            ].joined(separator: "&")
        )
        guard let list = lists.first else { return [:] }
        let idList = stockItemIds.map { $0.uuidString.lowercased() }.joined(separator: ",")
        let rows: [PriceListItemPriceRow] = try await client.selectDecode(
            table: "price_list_items",
            query: [
                "select=stock_item_id,unit_price,core_charge",
                "price_list_id=eq.\(list.id.uuidString.lowercased())",
                "stock_item_id=in.(\(idList))",
            ].joined(separator: "&")
        )
        return Dictionary(uniqueKeysWithValues: rows.map {
            ($0.stockItemId, CatalogPriceRow(unitPrice: $0.unitPrice, coreCharge: $0.coreCharge))
        })
    }

    private func ensureOpenCartId() async throws -> UUID {
        if let open = try await loadOpenCart() {
            return open.id
        }
        let warehouseId = try await resolveMainWarehouseId()
        return try await createCart(
            warehouseId: warehouseId,
            currency: .USD,
            fulfillmentMode: .immediate,
            exchangeRate: 1
        )
    }

    private func stockItemOemBody(stockItemId: UUID?, oem: String?) -> [String: Any] {
        let trimmed = oem?.trimmingCharacters(in: .whitespacesAndNewlines)
        return [
            "p_stock_item_id": stockItemId.map { JSONValue.uuid($0) } ?? NSNull(),
            "p_oem_part_number": (trimmed?.isEmpty == false) ? trimmed! : NSNull(),
        ]
    }

    private static func safeImageExtension(_ raw: String) -> String {
        let ext = raw.lowercased().replacingOccurrences(of: "[^a-z0-9]", with: "", options: .regularExpression)
        if ["jpg", "jpeg", "png", "webp"].contains(ext) {
            return ext == "jpeg" ? "jpg" : ext
        }
        return "jpg"
    }

    private static func percentEncodeQueryValue(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&=")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private func tryEdgeIntent(
        _ name: String,
        body: [String: Any],
        rail: PaymentRail
    ) async throws -> PaymentIntentResult? {
        let data = try await client.invokeFunction(name, body: body)
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        if obj["error"] != nil { return nil }

        let intentRaw =
            (obj["intent_id"] as? String)
            ?? (obj["intentId"] as? String)
        guard let intentRaw, let intentId = UUID(uuidString: intentRaw) else {
            return nil
        }

        let checkout =
            (obj["checkout_url"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            ?? (obj["poll_url"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let url = checkout.flatMap { URL(string: $0) }

        return PaymentIntentResult(
            intentId: intentId,
            rail: rail,
            checkoutURL: url,
            stubMessage: url == nil
                ? "Edge \(name) returned intent without checkout URL. Open hosted checkout when available; settlement stays webhook-only."
                : "Open checkout URL in Safari / ASWebAuthenticationSession. Settlement is webhook / service_role only."
        )
    }

    private func checkoutReturnURL(invoiceId: UUID) -> URL {
        URL(string: "\(returnURLScheme)://checkout/return?invoice=\(invoiceId.uuidString)")!
    }

    private func checkoutCancelURL(invoiceId: UUID) -> URL {
        URL(string: "\(returnURLScheme)://checkout/cancel?invoice=\(invoiceId.uuidString)")!
    }
}

// MARK: - DTOs (snake_case PostgREST)

private struct PosCartRow: Decodable {
    let id: UUID
    let currency: StorefrontCurrency
    let fulfillmentMode: FulfillmentMode
    let exchangeRateApplied: FlexibleDecimal
    let status: String

    enum CodingKeys: String, CodingKey {
        case id, currency, status
        case fulfillmentMode = "fulfillment_mode"
        case exchangeRateApplied = "exchange_rate_applied"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        currency = try c.decodeIfPresent(StorefrontCurrency.self, forKey: .currency) ?? .USD
        fulfillmentMode = try c.decodeIfPresent(FulfillmentMode.self, forKey: .fulfillmentMode) ?? .immediate
        exchangeRateApplied = try c.decodeIfPresent(FlexibleDecimal.self, forKey: .exchangeRateApplied)
            ?? FlexibleDecimal(1)
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? "open"
    }
}

private struct StockItemEmbed: Decodable {
    let oemPartNumber: String?
    let description: String?

    enum CodingKeys: String, CodingKey {
        case oemPartNumber = "oem_part_number"
        case description
    }
}

private struct PosCartLineRow: Decodable {
    let id: UUID
    let stockItemId: UUID
    let qty: FlexibleDecimal
    let unitPrice: FlexibleDecimal
    let stockItems: StockItemEmbed?

    enum CodingKeys: String, CodingKey {
        case id, qty
        case stockItemId = "stock_item_id"
        case unitPrice = "unit_price"
        case stockItems = "stock_items"
    }
}

private struct InvoiceRow: Decodable {
    let id: UUID
    let documentNumber: String?
    let docType: String?
    let status: String
    let fulfillmentMode: FulfillmentMode?
    let currency: StorefrontCurrency?
    let exchangeRateApplied: FlexibleDecimal?
    let subtotal: FlexibleDecimal?
    let total: FlexibleDecimal?
    let amountPaid: FlexibleDecimal?
    let cartId: UUID?
    let postedAt: Date?

    enum CodingKeys: String, CodingKey {
        case id, status, currency, subtotal, total
        case documentNumber = "document_number"
        case docType = "doc_type"
        case fulfillmentMode = "fulfillment_mode"
        case exchangeRateApplied = "exchange_rate_applied"
        case amountPaid = "amount_paid"
        case cartId = "cart_id"
        case postedAt = "posted_at"
    }

    func toCustomerOrder() -> CustomerOrder {
        let totalVal = total?.value ?? 0
        let paid = amountPaid?.value ?? 0
        return CustomerOrder(
            invoiceId: id,
            documentNumber: documentNumber,
            docType: docType ?? "invoice",
            status: status,
            fulfillmentMode: fulfillmentMode ?? .immediate,
            currency: currency ?? .USD,
            exchangeRateApplied: exchangeRateApplied?.value ?? 1,
            subtotal: subtotal?.value ?? 0,
            total: totalVal,
            amountPaid: paid,
            amountOpen: totalVal - paid,
            cartId: cartId,
            postedAt: postedAt
        )
    }
}

private struct GarageRow: Decodable {
    let id: UUID
    let make: String?
    let model: String?
    let generation: String?
    let engine: String?
    let vin: String?
    let isPrimary: Bool

    enum CodingKeys: String, CodingKey {
        case id, make, model, generation, engine, vin
        case isPrimary = "is_primary"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        make = try c.decodeIfPresent(String.self, forKey: .make)
        model = try c.decodeIfPresent(String.self, forKey: .model)
        generation = try c.decodeIfPresent(String.self, forKey: .generation)
        engine = try c.decodeIfPresent(String.self, forKey: .engine)
        vin = try c.decodeIfPresent(String.self, forKey: .vin)
        isPrimary = try c.decodeIfPresent(Bool.self, forKey: .isPrimary) ?? false
    }
}

private struct CustomerOrderDTO: Decodable {
    let invoiceId: UUID
    let documentNumber: String?
    let docType: String?
    let status: String
    let fulfillmentMode: FulfillmentMode?
    let currency: StorefrontCurrency?
    let exchangeRateApplied: FlexibleDecimal?
    let subtotal: FlexibleDecimal?
    let total: FlexibleDecimal?
    let amountPaid: FlexibleDecimal?
    let amountOpen: FlexibleDecimal?
    let cartId: UUID?
    let postedAt: Date?
    let pickListStatus: String?
    let deliveryNoteStatus: String?
    let activeDeliveryJobId: UUID?

    enum CodingKeys: String, CodingKey {
        case status, currency, subtotal, total
        case invoiceId = "invoice_id"
        case documentNumber = "document_number"
        case docType = "doc_type"
        case fulfillmentMode = "fulfillment_mode"
        case exchangeRateApplied = "exchange_rate_applied"
        case amountPaid = "amount_paid"
        case amountOpen = "amount_open"
        case cartId = "cart_id"
        case postedAt = "posted_at"
        case pickListStatus = "pick_list_status"
        case deliveryNoteStatus = "delivery_note_status"
        case activeDeliveryJobId = "active_delivery_job_id"
        case deliveryJobId = "delivery_job_id"
    }

    /// `get_customer_order` returns JSONB; invoice_id may arrive as string.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .invoiceId) {
            invoiceId = id
        } else if let s = try c.decode(String.self, forKey: .invoiceId), let id = UUID(uuidString: s) {
            invoiceId = id
        } else {
            throw DecodingError.dataCorruptedError(
                forKey: .invoiceId,
                in: c,
                debugDescription: "invoice_id required"
            )
        }
        documentNumber = try c.decodeIfPresent(String.self, forKey: .documentNumber)
        docType = try c.decodeIfPresent(String.self, forKey: .docType)
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? ""
        fulfillmentMode = try c.decodeIfPresent(FulfillmentMode.self, forKey: .fulfillmentMode)
        currency = try c.decodeIfPresent(StorefrontCurrency.self, forKey: .currency)
        exchangeRateApplied = try c.decodeIfPresent(FlexibleDecimal.self, forKey: .exchangeRateApplied)
        subtotal = try c.decodeIfPresent(FlexibleDecimal.self, forKey: .subtotal)
        total = try c.decodeIfPresent(FlexibleDecimal.self, forKey: .total)
        amountPaid = try c.decodeIfPresent(FlexibleDecimal.self, forKey: .amountPaid)
        amountOpen = try c.decodeIfPresent(FlexibleDecimal.self, forKey: .amountOpen)
        if let id = try? c.decode(UUID.self, forKey: .cartId) {
            cartId = id
        } else if let s = try c.decodeIfPresent(String.self, forKey: .cartId) {
            cartId = UUID(uuidString: s)
        } else {
            cartId = nil
        }
        postedAt = try c.decodeIfPresent(Date.self, forKey: .postedAt)
        pickListStatus = try c.decodeIfPresent(String.self, forKey: .pickListStatus)
        deliveryNoteStatus = try c.decodeIfPresent(String.self, forKey: .deliveryNoteStatus)
        // Forward-compat: backend may add active_delivery_job_id or delivery_job_id later.
        if let id = try? c.decodeIfPresent(UUID.self, forKey: .activeDeliveryJobId) {
            activeDeliveryJobId = id
        } else if let s = try c.decodeIfPresent(String.self, forKey: .activeDeliveryJobId) {
            activeDeliveryJobId = UUID(uuidString: s)
        } else if let id = try? c.decodeIfPresent(UUID.self, forKey: .deliveryJobId) {
            activeDeliveryJobId = id
        } else if let s = try c.decodeIfPresent(String.self, forKey: .deliveryJobId) {
            activeDeliveryJobId = UUID(uuidString: s)
        } else {
            activeDeliveryJobId = nil
        }
    }

    func toModel() -> CustomerOrder {
        let totalVal = total?.value ?? 0
        let paid = amountPaid?.value ?? 0
        return CustomerOrder(
            invoiceId: invoiceId,
            documentNumber: documentNumber,
            docType: docType ?? "invoice",
            status: status,
            fulfillmentMode: fulfillmentMode ?? .immediate,
            currency: currency ?? .USD,
            exchangeRateApplied: exchangeRateApplied?.value ?? 1,
            subtotal: subtotal?.value ?? 0,
            total: totalVal,
            amountPaid: paid,
            amountOpen: amountOpen?.value ?? (totalVal - paid),
            cartId: cartId,
            postedAt: postedAt,
            pickListStatus: pickListStatus,
            deliveryNoteStatus: deliveryNoteStatus,
            activeDeliveryJobId: activeDeliveryJobId
        )
    }
}

/// Single-row DTO from `get_delivery_track_point` — never decode a trail.
private struct DeliveryTrackPointDTO: Decodable {
    let deliveryJobId: UUID
    let lat: Double
    let lng: Double
    let recordedAt: Date
    let etaAt: Date?
    let etaSeconds: Int?
    let status: String

    enum CodingKeys: String, CodingKey {
        case lat, lng, status
        case deliveryJobId = "delivery_job_id"
        case recordedAt = "recorded_at"
        case etaAt = "eta_at"
        case etaSeconds = "eta_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .deliveryJobId) {
            deliveryJobId = id
        } else if let s = try c.decode(String.self, forKey: .deliveryJobId),
                  let id = UUID(uuidString: s) {
            deliveryJobId = id
        } else {
            throw DecodingError.dataCorruptedError(
                forKey: .deliveryJobId,
                in: c,
                debugDescription: "delivery_job_id required"
            )
        }
        lat = try c.decode(Double.self, forKey: .lat)
        lng = try c.decode(Double.self, forKey: .lng)
        recordedAt = try c.decodeIfPresent(Date.self, forKey: .recordedAt) ?? Date()
        etaAt = try c.decodeIfPresent(Date.self, forKey: .etaAt)
        if let n = try c.decodeIfPresent(Int.self, forKey: .etaSeconds) {
            etaSeconds = n
        } else if let d = try c.decodeIfPresent(Double.self, forKey: .etaSeconds) {
            etaSeconds = Int(d)
        } else {
            etaSeconds = nil
        }
        status = try c.decodeIfPresent(String.self, forKey: .status) ?? "dispatched"
    }

    func toModel() -> DeliveryTrackPoint {
        DeliveryTrackPoint(
            deliveryJobId: deliveryJobId,
            lat: lat,
            lng: lng,
            recordedAt: recordedAt,
            etaAt: etaAt,
            etaSeconds: etaSeconds,
            status: status
        )
    }
}

private struct ChatThreadRow: Decodable {
    let id: UUID
    let customerUserId: UUID?
    let kind: ChatThreadKind
    let status: ChatThreadStatus
    let subject: String?
    let lastMessageAt: Date?
    let createdAt: Date?

    enum CodingKeys: String, CodingKey {
        case id, kind, status, subject
        case customerUserId = "customer_user_id"
        case lastMessageAt = "last_message_at"
        case createdAt = "created_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try Self.decodeUUID(c, key: .id)
        customerUserId = try Self.decodeOptionalUUID(c, key: .customerUserId)
        kind = try c.decodeIfPresent(ChatThreadKind.self, forKey: .kind) ?? .support
        status = try c.decodeIfPresent(ChatThreadStatus.self, forKey: .status) ?? .open
        subject = try c.decodeIfPresent(String.self, forKey: .subject)
        lastMessageAt = try c.decodeIfPresent(Date.self, forKey: .lastMessageAt)
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt)
    }

    func toModel() -> ChatThread {
        ChatThread(
            id: id,
            customerUserId: customerUserId,
            kind: kind,
            status: status,
            subject: subject,
            lastMessageAt: lastMessageAt,
            createdAt: createdAt
        )
    }

    private static func decodeUUID(_ c: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) throws -> UUID {
        if let id = try? c.decode(UUID.self, forKey: key) { return id }
        if let s = try c.decode(String.self, forKey: key), let id = UUID(uuidString: s) { return id }
        throw DecodingError.dataCorruptedError(forKey: key, in: c, debugDescription: "UUID required")
    }

    private static func decodeOptionalUUID(_ c: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) throws -> UUID? {
        if let id = try? c.decodeIfPresent(UUID.self, forKey: key) { return id }
        if let s = try c.decodeIfPresent(String.self, forKey: key) { return UUID(uuidString: s) }
        return nil
    }
}

private struct ChatMessageRow: Decodable {
    let id: UUID
    let threadId: UUID
    let senderUserId: UUID?
    let senderKind: ChatSenderKind
    let body: String
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id, body
        case threadId = "thread_id"
        case senderUserId = "sender_user_id"
        case senderKind = "sender_kind"
        case createdAt = "created_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let u = try? c.decode(UUID.self, forKey: .id) {
            id = u
        } else if let s = try c.decode(String.self, forKey: .id), let u = UUID(uuidString: s) {
            id = u
        } else {
            throw DecodingError.dataCorruptedError(forKey: .id, in: c, debugDescription: "id required")
        }
        if let u = try? c.decode(UUID.self, forKey: .threadId) {
            threadId = u
        } else if let s = try c.decode(String.self, forKey: .threadId), let u = UUID(uuidString: s) {
            threadId = u
        } else {
            throw DecodingError.dataCorruptedError(forKey: .threadId, in: c, debugDescription: "thread_id required")
        }
        if let u = try? c.decodeIfPresent(UUID.self, forKey: .senderUserId) {
            senderUserId = u
        } else if let s = try c.decodeIfPresent(String.self, forKey: .senderUserId) {
            senderUserId = UUID(uuidString: s)
        } else {
            senderUserId = nil
        }
        senderKind = try c.decodeIfPresent(ChatSenderKind.self, forKey: .senderKind) ?? .customer
        body = try c.decode(String.self, forKey: .body)
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
    }

    func toModel() -> ChatMessage {
        ChatMessage(
            id: id,
            threadId: threadId,
            senderUserId: senderUserId,
            senderKind: senderKind,
            body: body,
            createdAt: createdAt
        )
    }
}

private struct AddressRow: Decodable {
    let id: UUID
    let label: String
    let line1: String
    let line2: String?
    let city: String?
    let province: String?
    let postalCode: String?
    let country: String
    let isDefault: Bool
    let createdAt: Date?
    let updatedAt: Date?

    enum CodingKeys: String, CodingKey {
        case id, label, line1, line2, city, province, country
        case postalCode = "postal_code"
        case isDefault = "is_default"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .id) {
            self.id = id
        } else if let s = try c.decode(String.self, forKey: .id), let id = UUID(uuidString: s) {
            self.id = id
        } else {
            throw DecodingError.dataCorruptedError(forKey: .id, in: c, debugDescription: "id required")
        }
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? ""
        line1 = try c.decode(String.self, forKey: .line1)
        line2 = try c.decodeIfPresent(String.self, forKey: .line2)
        city = try c.decodeIfPresent(String.self, forKey: .city)
        province = try c.decodeIfPresent(String.self, forKey: .province)
        postalCode = try c.decodeIfPresent(String.self, forKey: .postalCode)
        country = try c.decodeIfPresent(String.self, forKey: .country) ?? "Zimbabwe"
        isDefault = try c.decodeIfPresent(Bool.self, forKey: .isDefault) ?? false
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt)
        updatedAt = try c.decodeIfPresent(Date.self, forKey: .updatedAt)
    }

    func toModel() -> CustomerAddress {
        CustomerAddress(
            id: id,
            label: label,
            line1: line1,
            line2: line2,
            city: city,
            province: province,
            postalCode: postalCode,
            country: country,
            isDefault: isDefault,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}

private struct WarehouseIdRow: Decodable {
    let id: UUID
}

private struct CustomerIdRow: Decodable {
    let id: UUID
}

private struct StockItemIdRow: Decodable {
    let id: UUID
}

private struct WishlistStockEmbed: Decodable {
    let id: UUID?
    let oemPartNumber: String?
    let description: String?

    enum CodingKeys: String, CodingKey {
        case id, description
        case oemPartNumber = "oem_part_number"
    }
}

private struct WishlistRow: Decodable {
    let id: UUID
    let stockItemId: UUID
    let notifyWhenInStock: Bool
    let createdAt: Date?
    let stockItems: WishlistStockEmbed?

    enum CodingKeys: String, CodingKey {
        case id
        case stockItemId = "stock_item_id"
        case notifyWhenInStock = "notify_when_in_stock"
        case createdAt = "created_at"
        case stockItems = "stock_items"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try Self.decodeUUID(c, key: .id)
        stockItemId = try Self.decodeUUID(c, key: .stockItemId)
        notifyWhenInStock = try c.decodeIfPresent(Bool.self, forKey: .notifyWhenInStock) ?? false
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt)
        if let embed = try? c.decodeIfPresent(WishlistStockEmbed.self, forKey: .stockItems) {
            stockItems = embed
        } else if let arr = try? c.decodeIfPresent([WishlistStockEmbed].self, forKey: .stockItems) {
            stockItems = arr.first
        } else {
            stockItems = nil
        }
    }

    func toModel() -> WishlistItem {
        WishlistItem(
            id: id,
            stockItemId: stockItemId,
            oemPartNumber: stockItems?.oemPartNumber ?? "—",
            description: stockItems?.description,
            notifyWhenInStock: notifyWhenInStock,
            createdAt: createdAt
        )
    }

    private static func decodeUUID(_ c: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) throws -> UUID {
        if let id = try? c.decode(UUID.self, forKey: key) { return id }
        if let s = try c.decode(String.self, forKey: key), let id = UUID(uuidString: s) { return id }
        throw DecodingError.dataCorruptedError(forKey: key, in: c, debugDescription: "UUID required")
    }
}

private struct CompareItemDTO: Decodable {
    let id: UUID
    let stockItemId: UUID
    let oemPartNumber: String
    let description: String?
    let createdAt: Date?

    enum CodingKeys: String, CodingKey {
        case id, description
        case stockItemId = "stock_item_id"
        case oemPartNumber = "oem_part_number"
        case createdAt = "created_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try Self.decodeUUID(c, key: .id)
        stockItemId = try Self.decodeUUID(c, key: .stockItemId)
        oemPartNumber = try c.decodeIfPresent(String.self, forKey: .oemPartNumber) ?? "—"
        description = try c.decodeIfPresent(String.self, forKey: .description)
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt)
    }

    func toModel() -> CompareItem {
        CompareItem(
            id: id,
            stockItemId: stockItemId,
            oemPartNumber: oemPartNumber,
            description: description,
            createdAt: createdAt
        )
    }

    private static func decodeUUID(_ c: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) throws -> UUID {
        if let id = try? c.decode(UUID.self, forKey: key) { return id }
        if let s = try c.decode(String.self, forKey: key), let id = UUID(uuidString: s) { return id }
        throw DecodingError.dataCorruptedError(forKey: key, in: c, debugDescription: "UUID required")
    }
}

private struct ProductReviewRow: Decodable {
    let id: UUID
    let stockItemId: UUID
    let rating: Int
    let body: String
    let status: ProductReviewStatus
    let createdAt: Date?
    let stockItems: WishlistStockEmbed?

    enum CodingKeys: String, CodingKey {
        case id, rating, body, status
        case stockItemId = "stock_item_id"
        case createdAt = "created_at"
        case stockItems = "stock_items"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try Self.decodeUUID(c, key: .id)
        stockItemId = try Self.decodeUUID(c, key: .stockItemId)
        if let i = try? c.decode(Int.self, forKey: .rating) {
            rating = i
        } else if let d = try? c.decode(Double.self, forKey: .rating) {
            rating = Int(d)
        } else {
            rating = try c.decode(Int.self, forKey: .rating)
        }
        body = try c.decodeIfPresent(String.self, forKey: .body) ?? ""
        status = try c.decodeIfPresent(ProductReviewStatus.self, forKey: .status) ?? .pending
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt)
        if let embed = try? c.decodeIfPresent(WishlistStockEmbed.self, forKey: .stockItems) {
            stockItems = embed
        } else if let arr = try? c.decodeIfPresent([WishlistStockEmbed].self, forKey: .stockItems) {
            stockItems = arr.first
        } else {
            stockItems = nil
        }
    }

    func toModel() -> ProductReview {
        ProductReview(
            id: id,
            stockItemId: stockItemId,
            oemPartNumber: stockItems?.oemPartNumber,
            description: stockItems?.description,
            rating: rating,
            body: body,
            status: status,
            createdAt: createdAt
        )
    }

    private static func decodeUUID(_ c: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) throws -> UUID {
        if let id = try? c.decode(UUID.self, forKey: key) { return id }
        if let s = try c.decode(String.self, forKey: key), let id = UUID(uuidString: s) { return id }
        throw DecodingError.dataCorruptedError(forKey: key, in: c, debugDescription: "UUID required")
    }
}

private struct ProductReviewStatsDTO: Decodable {
    let stockItemId: UUID
    let avgRating: FlexibleDecimal
    let reviewCount: Int

    enum CodingKeys: String, CodingKey {
        case stockItemId = "stock_item_id"
        case avgRating = "avg_rating"
        case reviewCount = "review_count"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .stockItemId) {
            stockItemId = id
        } else if let s = try c.decode(String.self, forKey: .stockItemId), let id = UUID(uuidString: s) {
            stockItemId = id
        } else {
            throw DecodingError.dataCorruptedError(
                forKey: .stockItemId,
                in: c,
                debugDescription: "stock_item_id required"
            )
        }
        avgRating = try c.decodeIfPresent(FlexibleDecimal.self, forKey: .avgRating) ?? FlexibleDecimal(0)
        if let i = try c.decodeIfPresent(Int.self, forKey: .reviewCount) {
            reviewCount = i
        } else if let d = try c.decodeIfPresent(Double.self, forKey: .reviewCount) {
            reviewCount = Int(d)
        } else if let s = try c.decodeIfPresent(String.self, forKey: .reviewCount), let i = Int(s) {
            reviewCount = i
        } else {
            reviewCount = 0
        }
    }

    func toModel() -> ProductReviewStats {
        ProductReviewStats(
            stockItemId: stockItemId,
            avgRating: avgRating.value,
            reviewCount: reviewCount
        )
    }
}

// MARK: - Catalog DTOs

private struct StockItemBrowseRow: Decodable {
    let id: UUID
    let oemPartNumber: String
    let description: String?
    let reorderPoint: Double?

    enum CodingKeys: String, CodingKey {
        case id, description
        case oemPartNumber = "oem_part_number"
        case reorderPoint = "reorder_point"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .id) {
            self.id = id
        } else if let s = try c.decode(String.self, forKey: .id), let id = UUID(uuidString: s) {
            self.id = id
        } else {
            throw DecodingError.dataCorruptedError(forKey: .id, in: c, debugDescription: "id required")
        }
        oemPartNumber = try c.decode(String.self, forKey: .oemPartNumber)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        if let d = try c.decodeIfPresent(Double.self, forKey: .reorderPoint) {
            reorderPoint = d
        } else if let s = try c.decodeIfPresent(String.self, forKey: .reorderPoint), let d = Double(s) {
            reorderPoint = d
        } else {
            reorderPoint = nil
        }
    }
}

private struct StockItemPdpRow: Decodable {
    let id: UUID
    let oemPartNumber: String
    let description: String?
    let reorderPoint: Double?
    let baseUomId: UUID?

    enum CodingKeys: String, CodingKey {
        case id, description
        case oemPartNumber = "oem_part_number"
        case reorderPoint = "reorder_point"
        case baseUomId = "base_uom_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .id) {
            self.id = id
        } else if let s = try c.decode(String.self, forKey: .id), let id = UUID(uuidString: s) {
            self.id = id
        } else {
            throw DecodingError.dataCorruptedError(forKey: .id, in: c, debugDescription: "id required")
        }
        oemPartNumber = try c.decode(String.self, forKey: .oemPartNumber)
        description = try c.decodeIfPresent(String.self, forKey: .description)
        if let d = try c.decodeIfPresent(Double.self, forKey: .reorderPoint) {
            reorderPoint = d
        } else if let s = try c.decodeIfPresent(String.self, forKey: .reorderPoint), let d = Double(s) {
            reorderPoint = d
        } else {
            reorderPoint = nil
        }
        if let id = try? c.decodeIfPresent(UUID.self, forKey: .baseUomId) {
            baseUomId = id
        } else if let s = try c.decodeIfPresent(String.self, forKey: .baseUomId) {
            baseUomId = UUID(uuidString: s)
        } else {
            baseUomId = nil
        }
    }
}

private struct FitmentMetaRow: Decodable {
    let chassisCode: String?
    let engineCode: String?
    let pncCode: String?
    let pncCategories: PncCategoryEmbed?

    enum CodingKeys: String, CodingKey {
        case chassisCode = "chassis_code"
        case engineCode = "engine_code"
        case pncCode = "pnc_code"
        case pncCategories = "pnc_categories"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        chassisCode = try c.decodeIfPresent(String.self, forKey: .chassisCode)
        engineCode = try c.decodeIfPresent(String.self, forKey: .engineCode)
        pncCode = try c.decodeIfPresent(String.self, forKey: .pncCode)
        if let embed = try? c.decodeIfPresent(PncCategoryEmbed.self, forKey: .pncCategories) {
            pncCategories = embed
        } else if let arr = try? c.decodeIfPresent([PncCategoryEmbed].self, forKey: .pncCategories) {
            pncCategories = arr.first
        } else {
            pncCategories = nil
        }
    }
}

private struct PncCategoryEmbed: Decodable {
    let categoryName: String?
    let subcategoryName: String?

    enum CodingKeys: String, CodingKey {
        case categoryName = "category_name"
        case subcategoryName = "subcategory_name"
    }
}

private struct FitmentCategoryRow: Decodable {
    let oemPartNumber: String
    let pncCategories: PncCategoryEmbed?

    enum CodingKeys: String, CodingKey {
        case oemPartNumber = "oem_part_number"
        case pncCategories = "pnc_categories"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        oemPartNumber = try c.decode(String.self, forKey: .oemPartNumber)
        if let embed = try? c.decodeIfPresent(PncCategoryEmbed.self, forKey: .pncCategories) {
            pncCategories = embed
        } else if let arr = try? c.decodeIfPresent([PncCategoryEmbed].self, forKey: .pncCategories) {
            pncCategories = arr.first
        } else {
            pncCategories = nil
        }
    }
}

private struct PncCodeRow: Decodable {
    let pncCode: String

    enum CodingKeys: String, CodingKey {
        case pncCode = "pnc_code"
    }
}

private struct VehicleMasterDbRow: Decodable {
    let id: String?
    let vinPrefix: String?
    let chassisCode: String
    let engineCode: String?
    let productionYear: Int?
    let modelVariant: String

    enum CodingKeys: String, CodingKey {
        case id
        case vinPrefix = "vin_prefix"
        case chassisCode = "chassis_code"
        case engineCode = "engine_code"
        case productionYear = "production_year"
        case modelVariant = "model_variant"
    }
}

private struct OemOnlyRow: Decodable {
    let oemPartNumber: String

    enum CodingKeys: String, CodingKey {
        case oemPartNumber = "oem_part_number"
    }
}

private struct OeNumberRow: Decodable {
    let oeNumber: String?

    enum CodingKeys: String, CodingKey {
        case oeNumber = "oe_number"
    }
}

private struct SupersededRow: Decodable {
    let supersededBy: String?

    enum CodingKeys: String, CodingKey {
        case supersededBy = "superseded_by"
    }
}

private struct DiagramPathRow: Decodable {
    let diagramPath: String?

    enum CodingKeys: String, CodingKey {
        case diagramPath = "diagram_path"
    }
}

private struct ProfileRow: Decodable {
    let id: UUID
    let fullName: String?

    enum CodingKeys: String, CodingKey {
        case id
        case fullName = "full_name"
    }

    func toModel() -> UserProfile {
        UserProfile(id: id, fullName: fullName)
    }
}

private struct CustomerProfileRow: Decodable {
    let id: UUID
    let displayName: String?
    let email: String?
    let phoneE164: String?
    let whatsappE164: String?
    let smsReceipts: Bool
    let emailReceipts: Bool
    let whatsappReceipts: Bool
    let marketingOptIn: Bool
    let lastPromoAt: String?

    enum CodingKeys: String, CodingKey {
        case id, email
        case displayName = "display_name"
        case phoneE164 = "phone_e164"
        case whatsappE164 = "whatsapp_e164"
        case smsReceipts = "sms_receipts"
        case emailReceipts = "email_receipts"
        case whatsappReceipts = "whatsapp_receipts"
        case marketingOptIn = "marketing_opt_in"
        case lastPromoAt = "last_promotional_message_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .id) {
            self.id = id
        } else if let s = try c.decode(String.self, forKey: .id), let id = UUID(uuidString: s) {
            self.id = id
        } else {
            throw DecodingError.dataCorruptedError(forKey: .id, in: c, debugDescription: "id required")
        }
        displayName = try c.decodeIfPresent(String.self, forKey: .displayName)
        email = try c.decodeIfPresent(String.self, forKey: .email)
        phoneE164 = try c.decodeIfPresent(String.self, forKey: .phoneE164)
        whatsappE164 = try c.decodeIfPresent(String.self, forKey: .whatsappE164)
        smsReceipts = try c.decodeIfPresent(Bool.self, forKey: .smsReceipts) ?? false
        emailReceipts = try c.decodeIfPresent(Bool.self, forKey: .emailReceipts) ?? false
        whatsappReceipts = try c.decodeIfPresent(Bool.self, forKey: .whatsappReceipts) ?? false
        marketingOptIn = try c.decodeIfPresent(Bool.self, forKey: .marketingOptIn) ?? false
        lastPromoAt = try c.decodeIfPresent(String.self, forKey: .lastPromoAt)
    }

    func toModel() -> CustomerProfile {
        CustomerProfile(
            id: id,
            displayName: displayName,
            email: email,
            phoneE164: phoneE164,
            whatsappE164: whatsappE164,
            smsReceipts: smsReceipts,
            emailReceipts: emailReceipts,
            whatsappReceipts: whatsappReceipts,
            marketingOptIn: marketingOptIn,
            lastPromoAt: lastPromoAt
        )
    }
}

private struct LoyaltyBalanceRow: Decodable {
    let customerId: UUID?
    let pointsBalance: Double
    let currency: String
    let liabilityPerPoint: Double
    let estimatedLiability: Double

    enum CodingKeys: String, CodingKey {
        case currency
        case customerId = "customer_id"
        case pointsBalance = "points_balance"
        case liabilityPerPoint = "liability_per_point"
        case estimatedLiability = "estimated_liability"
    }

    func toModel(fallbackCustomerId: UUID) -> LoyaltyBalance {
        LoyaltyBalance(
            customerId: customerId ?? fallbackCustomerId,
            pointsBalance: pointsBalance,
            currency: currency,
            liabilityPerPoint: liabilityPerPoint,
            estimatedLiability: estimatedLiability
        )
    }
}

private struct ItemKitRow: Decodable {
    let id: UUID
    let sellMode: String
    let stockItemId: UUID
    let stockItems: StockItemBriefEmbed?

    enum CodingKeys: String, CodingKey {
        case id
        case sellMode = "sell_mode"
        case stockItemId = "stock_item_id"
        case stockItems = "stock_items"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try Self.decodeUUID(c, key: .id)
        sellMode = try c.decodeIfPresent(String.self, forKey: .sellMode) ?? "bundle"
        stockItemId = try Self.decodeUUID(c, key: .stockItemId)
        if let embed = try? c.decodeIfPresent(StockItemBriefEmbed.self, forKey: .stockItems) {
            stockItems = embed
        } else if let arr = try? c.decodeIfPresent([StockItemBriefEmbed].self, forKey: .stockItems) {
            stockItems = arr.first
        } else {
            stockItems = nil
        }
    }

    private static func decodeUUID(_ c: KeyedDecodingContainer<CodingKeys>, key: CodingKeys) throws -> UUID {
        if let id = try? c.decode(UUID.self, forKey: key) { return id }
        if let s = try c.decode(String.self, forKey: key), let id = UUID(uuidString: s) { return id }
        throw DecodingError.dataCorruptedError(forKey: key, in: c, debugDescription: "UUID required")
    }
}

private struct KitComponentRow: Decodable {
    let kitId: UUID
    let qty: Double
    let stockItems: StockItemBriefEmbed?

    enum CodingKeys: String, CodingKey {
        case qty
        case kitId = "kit_id"
        case stockItems = "stock_items"
    }
}

private struct StockItemBriefEmbed: Decodable {
    let oemPartNumber: String?
    let description: String?

    enum CodingKeys: String, CodingKey {
        case description
        case oemPartNumber = "oem_part_number"
    }
}

private struct InvoiceLineRow: Decodable {
    let id: UUID
    let stockItemId: UUID
    let uomId: UUID
    let qty: FlexibleDecimal
    let stockItems: StockItemBriefEmbed?

    enum CodingKeys: String, CodingKey {
        case id, qty
        case stockItemId = "stock_item_id"
        case uomId = "uom_id"
        case stockItems = "stock_items"
    }

    func toModel() -> InvoiceLineSummary {
        InvoiceLineSummary(
            id: id,
            stockItemId: stockItemId,
            uomId: uomId,
            qty: qty.value,
            oemPartNumber: stockItems?.oemPartNumber,
            description: stockItems?.description
        )
    }
}

private enum JWTSubjectParser {
    static func userId(from token: String) -> UUID? {
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else { return nil }
        var payload = String(parts[1])
        let pad = 4 - payload.count % 4
        if pad < 4 { payload += String(repeating: "=", count: pad) }
        guard let data = Data(base64Encoded: payload.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let sub = obj["sub"] as? String,
              let id = UUID(uuidString: sub) else { return nil }
        return id
    }
}

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

private struct FitmentLabelRow: Decodable {
    let chassisCode: String?
    let engineCode: String?
    let pncCode: String?

    enum CodingKeys: String, CodingKey {
        case chassisCode = "chassis_code"
        case engineCode = "engine_code"
        case pncCode = "pnc_code"
    }
}

private struct StockLevelQtyRow: Decodable {
    let stockItemId: UUID
    let quantity: Double
    let warehouses: WarehouseFlagsRow

    enum CodingKeys: String, CodingKey {
        case quantity, warehouses
        case stockItemId = "stock_item_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .stockItemId) {
            stockItemId = id
        } else if let s = try c.decode(String.self, forKey: .stockItemId), let id = UUID(uuidString: s) {
            stockItemId = id
        } else {
            throw DecodingError.dataCorruptedError(forKey: .stockItemId, in: c, debugDescription: "stock_item_id")
        }
        if let d = try c.decodeIfPresent(Double.self, forKey: .quantity) {
            quantity = d
        } else if let s = try c.decodeIfPresent(String.self, forKey: .quantity), let d = Double(s) {
            quantity = d
        } else {
            quantity = 0
        }
        if let w = try? c.decode(WarehouseFlagsRow.self, forKey: .warehouses) {
            warehouses = w
        } else if let arr = try c.decode([WarehouseFlagsRow].self, forKey: .warehouses), let first = arr.first {
            warehouses = first
        } else {
            warehouses = WarehouseFlagsRow(isQuarantine: false, isActive: true)
        }
    }
}

private struct WarehouseFlagsRow: Decodable {
    let isQuarantine: Bool
    let isActive: Bool

    enum CodingKeys: String, CodingKey {
        case isQuarantine = "is_quarantine"
        case isActive = "is_active"
    }

    init(isQuarantine: Bool, isActive: Bool) {
        self.isQuarantine = isQuarantine
        self.isActive = isActive
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isQuarantine = try c.decodeIfPresent(Bool.self, forKey: .isQuarantine) ?? false
        isActive = try c.decodeIfPresent(Bool.self, forKey: .isActive) ?? true
    }
}

private struct PriceListHeadRow: Decodable {
    let id: UUID
    let currency: String

    enum CodingKeys: String, CodingKey {
        case id, currency
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .id) {
            self.id = id
        } else if let s = try c.decode(String.self, forKey: .id), let id = UUID(uuidString: s) {
            self.id = id
        } else {
            throw DecodingError.dataCorruptedError(forKey: .id, in: c, debugDescription: "id required")
        }
        currency = try c.decodeIfPresent(String.self, forKey: .currency) ?? "USD"
    }
}

private struct PriceListItemPriceRow: Decodable {
    let stockItemId: UUID
    let unitPrice: Double
    let coreCharge: Double

    enum CodingKeys: String, CodingKey {
        case stockItemId = "stock_item_id"
        case unitPrice = "unit_price"
        case coreCharge = "core_charge"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let id = try? c.decode(UUID.self, forKey: .stockItemId) {
            stockItemId = id
        } else if let s = try c.decode(String.self, forKey: .stockItemId), let id = UUID(uuidString: s) {
            stockItemId = id
        } else {
            throw DecodingError.dataCorruptedError(forKey: .stockItemId, in: c, debugDescription: "stock_item_id")
        }
        if let d = try c.decodeIfPresent(Double.self, forKey: .unitPrice) {
            unitPrice = d
        } else if let s = try c.decodeIfPresent(String.self, forKey: .unitPrice), let d = Double(s) {
            unitPrice = d
        } else {
            unitPrice = 0
        }
        if let d = try c.decodeIfPresent(Double.self, forKey: .coreCharge) {
            coreCharge = d
        } else if let s = try c.decodeIfPresent(String.self, forKey: .coreCharge), let d = Double(s) {
            coreCharge = d
        } else {
            coreCharge = 0
        }
    }
}

private struct CatalogPriceRow {
    let unitPrice: Double
    let coreCharge: Double
}

private enum CatalogSearchParser {
    static func parse(
        data: Data,
        fallbackMode: CatalogSearchMode,
        fallbackQuery: String,
        backend: String? = nil
    ) throws -> SearchCatalogResponse {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return SearchCatalogResponse(mode: fallbackMode, query: fallbackQuery, parts: [], backend: backend)
        }
        let mode = CatalogSearchMode(rawValue: (root["mode"] as? String)?.lowercased() ?? "") ?? fallbackMode
        let query = (root["query"] as? String) ?? fallbackQuery
        let results = root["results"] as? [Any] ?? []
        var parts: [CatalogPartHit] = []
        for row in results {
            collectPartHits(row, into: &parts)
        }
        var seen = Set<String>()
        let distinct = parts.filter { hit in
            let key = hit.oemPartNumber.uppercased()
            if seen.contains(key) { return false }
            seen.insert(key)
            return true
        }
        let resolvedBackend = (root["backend"] as? String) ?? backend
        let facets = parseFacetDistribution(root["facetDistribution"])
        return SearchCatalogResponse(
            mode: mode,
            query: query,
            parts: distinct,
            backend: resolvedBackend,
            facetDistribution: facets
        )
    }

    private static func parseFacetDistribution(_ element: Any?) -> [String: [String: Int]] {
        guard let root = element as? [String: Any] else { return [:] }
        var out: [String: [String: Int]] = [:]
        for (facet, valuesEl) in root {
            guard let values = valuesEl as? [String: Any] else { continue }
            var counts: [String: Int] = [:]
            for (label, countEl) in values {
                if let n = countEl as? Int {
                    counts[label] = n
                } else if let d = countEl as? Double {
                    counts[label] = Int(d)
                } else if let s = countEl as? String, let n = Int(s) {
                    counts[label] = n
                }
            }
            if !counts.isEmpty { out[facet] = counts }
        }
        return out
    }

    private static func collectPartHits(_ row: Any, into out: inout [CatalogPartHit]) {
        guard let o = row as? [String: Any] else { return }
        let type = (o["type"] as? String) ?? "part"
        switch type {
        case "part":
            if let hit = toPartHit(o) { out.append(hit) }
        case "vehicle", "pnc":
            let fitments = o["fitments"] as? [Any] ?? []
            for f in fitments {
                if let fo = f as? [String: Any], let hit = toPartHit(fo) {
                    out.append(hit)
                }
            }
        default:
            break
        }
    }

    private static func toPartHit(_ o: [String: Any]) -> CatalogPartHit? {
        let oem = ((o["oem_part_number"] as? String) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !oem.isEmpty else { return nil }
        return CatalogPartHit(
            oemPartNumber: oem,
            pncCode: o["pnc_code"] as? String,
            categoryName: o["category_name"] as? String,
            subcategoryName: o["subcategory_name"] as? String,
            chassisCode: o["chassis_code"] as? String,
            engineCode: o["engine_code"] as? String
        )
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
