import Foundation

/// Thin storefront AuthZ surface — mirrors `apps/web/lib/customer-storefront.ts`,
/// wishlist / compare / reviews helpers, and customer chat in `apps/web/lib/chat.ts`.
///
/// Live RPCs (authenticated customer; `customers.profile_id = auth.uid()`):
/// - `create_customer_cart` — p_warehouse_id, p_currency, p_fulfillment_mode, p_exchange_rate
/// - `add_customer_cart_line` — p_cart_id, p_stock_item_id, p_uom_id, p_qty
/// - `checkout_customer_cart` — p_cart_id → invoice UUID
/// - `get_customer_order` — p_invoice_id → JSONB summary
/// - `upsert_customer_garage_vehicle` / `delete_customer_garage_vehicle`
/// - `create_customer_contipay_intent` / `create_customer_paynow_intent`
///   (prefer edge `contipay-initiate` / `paynow-initiate`; RPC fallback — no client PSP crypto)
/// - Chat: `start_chat_thread`, `post_chat_message`, `mark_chat_thread_read`, `chat_unread_count`
///   (+ RLS select on `chat_threads` / `chat_messages`)
/// - Delivery track: `get_delivery_track_point` — last point + ETA only (never trail /
///   never `SELECT` on `delivery_locations`)
/// - Wishlist: `add_customer_wishlist_item` / `remove_customer_wishlist_item` /
///   `set_wishlist_notify_when_in_stock` / `wishlist_move_to_cart`
/// - Compare: `list_customer_compare_items` / `add_customer_compare_item` /
///   `remove_customer_compare_item` (guests: `GuestCompareStore`)
/// - Reviews: `submit_customer_product_review` / `get_product_review_stats` /
///   `add_customer_product_review_photo` (+ Storage `review-photos`)
@MainActor
public protocol StorefrontApi: AnyObject {
    func createCart(
        warehouseId: UUID,
        currency: StorefrontCurrency,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Decimal
    ) async throws -> UUID

    func addCartLine(
        cartId: UUID,
        stockItemId: UUID,
        uomId: UUID,
        qty: Decimal
    ) async throws -> UUID

    func checkoutCart(cartId: UUID) async throws -> UUID

    func loadOpenCart() async throws -> CartSummary?

    /// Official daily ZiG rate (ZiG per 1 USD) — mirrors web `fetchZigExchangeRate`.
    func fetchZigExchangeRate(asOf: String?) async throws -> Decimal

    /// Active MAIN warehouse (or first non-quarantine) — mirrors web `resolveMainWarehouseId`.
    func resolveMainWarehouseId() async throws -> UUID

    /// Open cart or create — mirrors web `ensureOpenCart`.
    func ensureOpenCart(
        currency: StorefrontCurrency,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Decimal
    ) async throws -> CartSummary

    func listOrders() async throws -> [CustomerOrder]

    func getOrder(invoiceId: UUID) async throws -> CustomerOrder

    func listGarage() async throws -> [GarageVehicle]

    func upsertGarage(_ input: GarageVehicleInput) async throws -> UUID

    func deleteGarage(id: UUID) async throws

    func createContipayIntent(
        invoiceId: UUID,
        method: ContipayMethod
    ) async throws -> PaymentIntentResult

    func createPaynowIntent(
        invoiceId: UUID,
        method: PaynowMethod
    ) async throws -> PaymentIntentResult

    /// EcoCash direct C2B — payerMsisdn required unless using profile/saved via nil + mode.
    func createEcocashIntent(
        invoiceId: UUID,
        payerMsisdn: String,
        payerMode: String
    ) async throws -> PaymentIntentResult

    // MARK: Chat

    func listChatThreads() async throws -> [ChatThread]

    func listChatMessages(threadId: UUID) async throws -> [ChatMessage]

    func startChatThread(_ input: StartChatThreadInput) async throws -> UUID

    func postChatMessage(threadId: UUID, body: String) async throws -> UUID

    func markChatThreadRead(threadId: UUID) async throws

    func chatUnreadCount(threadId: UUID?) async throws -> Int

    // MARK: Delivery track (privacy-safe last point)

    /// Calls `get_delivery_track_point` with job id (owner JWT) and/or share token.
    /// Returns at most one last point for an active `dispatched` job; `nil` when
    /// inactive / unauthorized / no ping yet. Never a historical trail.
    func getDeliveryTrackPoint(_ ref: DeliveryTrackRef) async throws -> DeliveryTrackPoint?

    // MARK: Wishlist

    func listWishlist() async throws -> [WishlistItem]

    func addWishlistItem(stockItemId: UUID?, oem: String?) async throws -> UUID

    func removeWishlistItem(wishlistId: UUID?, stockItemId: UUID?, oem: String?) async throws

    func setWishlistNotifyWhenInStock(
        notify: Bool,
        wishlistId: UUID?,
        stockItemId: UUID?,
        oem: String?
    ) async throws -> UUID

    /// Ensures open cart then `wishlist_move_to_cart` (or Fake compose).
    func wishlistMoveToCart(
        wishlistId: UUID?,
        stockItemId: UUID?,
        oem: String?,
        qty: Decimal,
        removeFromWishlist: Bool
    ) async throws -> UUID

    // MARK: Compare (auth)

    func listCompareItems() async throws -> [CompareItem]

    func addCompareItem(stockItemId: UUID?, oem: String?) async throws -> UUID

    func removeCompareItem(compareId: UUID?, stockItemId: UUID?, oem: String?) async throws

    // MARK: Reviews

    func listOwnReviews() async throws -> [ProductReview]

    func listApprovedReviews(oem: String) async throws -> [ProductReview]

    func getProductReviewStats(stockItemId: UUID?, oem: String?) async throws -> ProductReviewStats?

    func submitProductReview(
        rating: Int,
        body: String,
        stockItemId: UUID?,
        oem: String?
    ) async throws -> UUID

    /// Uploads to Storage bucket `review-photos` then `add_customer_product_review_photo`.
    func uploadReviewPhoto(
        reviewId: UUID,
        photo: ReviewPhotoUpload,
        sortOrder: Int
    ) async throws -> UUID

    // MARK: Catalog

    /// Four-way `search_catalog` (part / vin / model / pnc).
    func searchCatalog(mode: CatalogSearchMode, query: String) async throws -> SearchCatalogResponse

    /// Meili Edge proxy first (`catalog-search-meili` + JWT); FTS fallback server-side.
    func searchCatalogMeili(
        mode: CatalogSearchMode,
        query: String,
        limit: Int,
        facets: [String]?
    ) async throws -> SearchCatalogResponse

    /// Browse PLP — stock_items + default USD price + saleable qty.
    func listCatalogBrowse(category: String?, limit: Int) async throws -> CatalogBrowseResult

    /// Megazip hierarchy browse RPCs.
    func listCatalogMakers() async throws -> [EpcMaker]
    func listCatalogModels(makerSlug: String) async throws -> [EpcModel]
    func listCatalogVariants(makerSlug: String, modelSlug: String) async throws -> [EpcVariant]
    func listCatalogSections(makerSlug: String, modelSlug: String, variantSlug: String) async throws -> [EpcSection]
    func getCatalogDiagram(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String
    ) async throws -> EpcDiagramResponse

    /// Live `vehicle_master` rows for cascading Select vehicle.
    func listVehicleMaster() async throws -> [VehicleMasterRow]

    /// Parts for chassis (+ optional engine) via `part_fitment` → `stock_items`.
    func listCatalogForVehicle(chassisCode: String, engineCode: String?, limit: Int) async throws -> CatalogBrowseResult

    /// PDP load — stock_items + price + saleable qty + fitment labels.
    func loadCatalogProduct(oem: String) async throws -> CatalogProduct

    /// Ensures open cart then `add_customer_cart_line` for OEM.
    @discardableResult
    func addCartLineByOem(oem: String, qty: Decimal) async throws -> (cartId: UUID, lineId: UUID)

    // MARK: Addresses

    /// Live: SELECT own `customer_addresses` via RLS (default first).
    func listOwnAddresses() async throws -> [CustomerAddress]

    /// `upsert_customer_address` — embeds optional map pick into line2 via `AddressGeo`.
    func upsertCustomerAddress(_ input: CustomerAddressInput) async throws -> UUID

    /// `delete_customer_address`
    func deleteCustomerAddress(id: UUID) async throws

    // MARK: Profile

    func loadOwnProfile() async throws -> UserProfile?

    func loadOwnCustomer() async throws -> CustomerProfile?

    func updateOwnFullName(_ fullName: String) async throws

    func updateOwnCustomerContact(_ patch: CustomerContactPatch) async throws

    func setOwnMarketingOptIn(_ optIn: Bool) async throws

    // MARK: Loyalty / returns / kits

    func getLoyaltyBalance(customerId: UUID) async throws -> LoyaltyBalance

    func postCustomerReturnCreditNote(
        invoiceId: UUID,
        lines: [ReturnCreditNoteLine]
    ) async throws -> UUID

    func listActiveKits(limit: Int) async throws -> [KitListItem]

    func listInvoiceLines(invoiceId: UUID) async throws -> [InvoiceLineSummary]
}

/// In-memory Fake for Simulator / Windows scaffold — no network.
@MainActor
public final class FakeStorefrontApi: StorefrontApi {
    private var cart: CartSummary?
    private var orders: [CustomerOrder] = []
    private var garage: [GarageVehicle] = []
    private var threads: [ChatThread] = []
    private var messages: [ChatMessage] = []
    private var unreadByThread: [UUID: Int] = [:]
    private var wishlist: [WishlistItem] = []
    private var compare: [CompareItem] = []
    private var reviews: [ProductReview] = []
    private var reviewPhotoCounts: [UUID: Int] = [:]
    private var addresses: [CustomerAddress] = []
    /// Fake last-point only — never a trail. Demo token: `demo-track-token`.
    private var fakeFullName = "Demo Customer"
    private var fakeCustomer = CustomerProfile(
        id: UUID(uuidString: "00000000-0000-4000-8000-000000000001")!,
        displayName: "Demo Customer",
        email: "demo@nissangtrauto.co.zw",
        phoneE164: "+263770000000",
        whatsappE164: "+263770000000",
        whatsappReceipts: true,
        marketingOptIn: false
    )
    private var demoTrackJobId: UUID?
    private var demoTrackPoint: DeliveryTrackPoint?
    private let catalogProducts: [String: CatalogProduct]

    private static let seedOilFilterId = UUID(uuidString: "00000000-0000-4000-8000-0000000000a1")!
    private static let seedAirFilterId = UUID(uuidString: "00000000-0000-4000-8000-0000000000a2")!
    private static let seedUomId = UUID(uuidString: "00000000-0000-4000-8000-0000000000u1")!
    private static let seedWarehouseId = UUID(uuidString: "00000000-0000-4000-8000-0000000000w1")!

    public init(seedDemo: Bool = true) {
        catalogProducts = Self.seedCatalogProducts()
        if seedDemo {
            let inv = UUID()
            let jobId = UUID()
            demoTrackJobId = jobId
            demoTrackPoint = DeliveryTrackPoint(
                deliveryJobId: jobId,
                lat: -17.8292,
                lng: 31.0522,
                recordedAt: Date().addingTimeInterval(-90),
                etaAt: Date().addingTimeInterval(25 * 60),
                etaSeconds: 25 * 60,
                status: "dispatched"
            )
            orders = [
                CustomerOrder(
                    invoiceId: inv,
                    documentNumber: "INV-DEMO-001",
                    status: "posted",
                    fulfillmentMode: .dispatch,
                    currency: .USD,
                    subtotal: 120,
                    total: 120,
                    amountPaid: 0,
                    amountOpen: 120,
                    deliveryNoteStatus: "submitted",
                    activeDeliveryJobId: jobId
                ),
            ]
            garage = [
                GarageVehicle(
                    id: UUID(),
                    make: "Nissan",
                    model: "GT-R",
                    generation: "R35",
                    engine: "VR38DETT",
                    isPrimary: true
                ),
            ]
            let now = Date()
            addresses = [
                CustomerAddress(
                    id: UUID(),
                    label: "Home",
                    line1: "15 Samora Machel Ave",
                    line2: AddressGeo.embed(line2: nil, latitude: -17.8292, longitude: 31.0522),
                    city: "Harare",
                    province: "Harare",
                    country: "Zimbabwe",
                    isDefault: true,
                    createdAt: now,
                    updatedAt: now
                ),
            ]
            let threadId = UUID()
            threads = [
                ChatThread(
                    id: threadId,
                    kind: .support,
                    status: .open,
                    subject: "Demo counter check",
                    lastMessageAt: now,
                    createdAt: now
                ),
            ]
            messages = [
                ChatMessage(
                    id: UUID(),
                    threadId: threadId,
                    senderKind: .customer,
                    body: "Hi — need fitment help for a VR38 oil filter.",
                    createdAt: now.addingTimeInterval(-120)
                ),
                ChatMessage(
                    id: UUID(),
                    threadId: threadId,
                    senderKind: .staff,
                    body: "Sure — send the OEM or VIN and we’ll confirm.",
                    createdAt: now
                ),
            ]
            unreadByThread[threadId] = 1

            let oilFilterId = UUID(uuidString: "00000000-0000-4000-8000-0000000000a1")!
            let airFilterId = UUID(uuidString: "00000000-0000-4000-8000-0000000000a2")!
            wishlist = [
                WishlistItem(
                    id: UUID(),
                    stockItemId: oilFilterId,
                    oemPartNumber: "15208-65F0C",
                    description: "Oil filter (demo)",
                    notifyWhenInStock: false,
                    createdAt: now
                ),
                WishlistItem(
                    id: UUID(),
                    stockItemId: airFilterId,
                    oemPartNumber: "16546-EB70A",
                    description: "Air cleaner element (demo)",
                    notifyWhenInStock: true,
                    createdAt: now.addingTimeInterval(-3600)
                ),
            ]
            compare = [
                CompareItem(
                    id: UUID(),
                    stockItemId: oilFilterId,
                    oemPartNumber: "15208-65F0C",
                    description: "Oil filter (demo)",
                    createdAt: now
                ),
            ]
            reviews = [
                ProductReview(
                    id: UUID(),
                    stockItemId: oilFilterId,
                    oemPartNumber: "15208-65F0C",
                    description: "Oil filter (demo)",
                    rating: 5,
                    body: "Fits my Navara — approved demo review.",
                    status: .approved,
                    createdAt: now.addingTimeInterval(-86400)
                ),
                ProductReview(
                    id: UUID(),
                    stockItemId: airFilterId,
                    oemPartNumber: "16546-EB70A",
                    description: "Air cleaner element (demo)",
                    rating: 4,
                    body: "Pending moderation demo.",
                    status: .pending,
                    createdAt: now
                ),
            ]
        }
    }

    public func createCart(
        warehouseId _: UUID,
        currency: StorefrontCurrency,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Decimal
    ) async throws -> UUID {
        let id = UUID()
        cart = CartSummary(
            id: id,
            currency: currency,
            fulfillmentMode: fulfillmentMode,
            exchangeRateApplied: exchangeRate,
            lines: []
        )
        return id
    }

    public func addCartLine(
        cartId: UUID,
        stockItemId: UUID,
        uomId _: UUID,
        qty: Decimal
    ) async throws -> UUID {
        guard var open = cart, open.id == cartId else {
            throw StorefrontError.message("Open cart not found.")
        }
        let lineId = UUID()
        open.lines.append(
            CartLineSummary(
                id: lineId,
                stockItemId: stockItemId,
                oemPartNumber: "OEM-\(stockItemId.uuidString.prefix(8))",
                description: "Demo line",
                qty: qty,
                unitPrice: 42.5,
                currency: open.currency
            )
        )
        cart = open
        return lineId
    }

    public func checkoutCart(cartId: UUID) async throws -> UUID {
        guard let open = cart, open.id == cartId else {
            throw StorefrontError.message("Open cart not found.")
        }
        let invoiceId = UUID()
        let subtotal = open.lines.reduce(Decimal(0)) { $0 + ($1.unitPrice * $1.qty) }
        orders.insert(
            CustomerOrder(
                invoiceId: invoiceId,
                documentNumber: "INV-\(invoiceId.uuidString.prefix(8))",
                status: "posted",
                fulfillmentMode: open.fulfillmentMode,
                currency: open.currency,
                exchangeRateApplied: open.exchangeRateApplied,
                subtotal: subtotal,
                total: subtotal,
                amountPaid: 0,
                amountOpen: subtotal,
                cartId: cartId,
                postedAt: Date()
            ),
            at: 0
        )
        cart = nil
        return invoiceId
    }

    public func loadOpenCart() async throws -> CartSummary? {
        cart
    }

    public func fetchZigExchangeRate(asOf _: String?) async throws -> Decimal {
        26.5
    }

    public func resolveMainWarehouseId() async throws -> UUID {
        UUID(uuidString: "00000000-0000-4000-8000-000000000001")!
    }

    public func ensureOpenCart(
        currency: StorefrontCurrency,
        fulfillmentMode: FulfillmentMode,
        exchangeRate: Decimal
    ) async throws -> CartSummary {
        if let open = cart { return open }
        _ = try await createCart(
            warehouseId: try await resolveMainWarehouseId(),
            currency: currency,
            fulfillmentMode: fulfillmentMode,
            exchangeRate: exchangeRate
        )
        guard let open = cart else { throw StorefrontError.message("ensureOpenCart failed") }
        return open
    }

    public func listOrders() async throws -> [CustomerOrder] {
        orders
    }

    public func getOrder(invoiceId: UUID) async throws -> CustomerOrder {
        guard let order = orders.first(where: { $0.invoiceId == invoiceId }) else {
            throw StorefrontError.message("Order not found.")
        }
        return order
    }

    public func listGarage() async throws -> [GarageVehicle] {
        garage
    }

    public func upsertGarage(_ input: GarageVehicleInput) async throws -> UUID {
        if let id = input.id, let idx = garage.firstIndex(where: { $0.id == id }) {
            garage[idx] = GarageVehicle(
                id: id,
                make: input.make,
                model: input.model,
                generation: input.generation,
                engine: input.engine,
                vin: input.vin,
                isPrimary: input.isPrimary
            )
            return id
        }
        let id = UUID()
        garage.insert(
            GarageVehicle(
                id: id,
                make: input.make,
                model: input.model,
                generation: input.generation,
                engine: input.engine,
                vin: input.vin,
                isPrimary: input.isPrimary
            ),
            at: 0
        )
        return id
    }

    public func deleteGarage(id: UUID) async throws {
        garage.removeAll { $0.id == id }
    }

    public func createContipayIntent(
        invoiceId: UUID,
        method _: ContipayMethod
    ) async throws -> PaymentIntentResult {
        try stubIntent(invoiceId: invoiceId, rail: .contipay)
    }

    public func createPaynowIntent(
        invoiceId: UUID,
        method _: PaynowMethod
    ) async throws -> PaymentIntentResult {
        try stubIntent(invoiceId: invoiceId, rail: .paynow)
    }

    public func createEcocashIntent(
        invoiceId: UUID,
        payerMsisdn _: String,
        payerMode _: String
    ) async throws -> PaymentIntentResult {
        try stubIntent(invoiceId: invoiceId, rail: .ecocash)
    }

    // MARK: Chat (Fake)

    public func listChatThreads() async throws -> [ChatThread] {
        threads.sorted { ($0.lastMessageAt ?? .distantPast) > ($1.lastMessageAt ?? .distantPast) }
    }

    public func listChatMessages(threadId: UUID) async throws -> [ChatMessage] {
        messages
            .filter { $0.threadId == threadId }
            .sorted { $0.createdAt < $1.createdAt }
    }

    public func startChatThread(_ input: StartChatThreadInput) async throws -> UUID {
        let id = UUID()
        let now = Date()
        let subject = input.subject?.trimmingCharacters(in: .whitespacesAndNewlines)
        threads.insert(
            ChatThread(
                id: id,
                kind: input.kind,
                status: .open,
                subject: (subject?.isEmpty == false) ? subject : nil,
                lastMessageAt: now,
                createdAt: now
            ),
            at: 0
        )
        if let body = input.body?.trimmingCharacters(in: .whitespacesAndNewlines), !body.isEmpty {
            messages.append(
                ChatMessage(
                    id: UUID(),
                    threadId: id,
                    senderKind: .customer,
                    body: body,
                    createdAt: now
                )
            )
        }
        unreadByThread[id] = 0
        return id
    }

    public func postChatMessage(threadId: UUID, body: String) async throws -> UUID {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw StorefrontError.message("Message body required.")
        }
        guard let idx = threads.firstIndex(where: { $0.id == threadId }) else {
            throw StorefrontError.message("Thread not found.")
        }
        guard threads[idx].status != .closed else {
            throw StorefrontError.message("Thread closed.")
        }
        let id = UUID()
        let now = Date()
        messages.append(
            ChatMessage(
                id: id,
                threadId: threadId,
                senderKind: .customer,
                body: trimmed,
                createdAt: now
            )
        )
        threads[idx].lastMessageAt = now
        return id
    }

    public func markChatThreadRead(threadId: UUID) async throws {
        unreadByThread[threadId] = 0
    }

    public func chatUnreadCount(threadId: UUID?) async throws -> Int {
        if let threadId {
            return unreadByThread[threadId] ?? 0
        }
        return unreadByThread.values.reduce(0, +)
    }

    // MARK: Delivery track (Fake)

    public func getDeliveryTrackPoint(_ ref: DeliveryTrackRef) async throws -> DeliveryTrackPoint? {
        switch ref {
        case .job(let id):
            guard id == demoTrackJobId else { return nil }
            return nudgeDemoTrackPoint()
        case .token(let raw):
            let token = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard token.count >= 8 else {
                throw StorefrontError.message("Invalid track token.")
            }
            guard token == "demo-track-token" else { return nil }
            return nudgeDemoTrackPoint()
        }
    }

    /// Single last-point only — nudges coords so MapKit preview looks live (never a trail).
    private func nudgeDemoTrackPoint() -> DeliveryTrackPoint? {
        guard var point = demoTrackPoint else { return nil }
        let delta = 0.00018
        point = DeliveryTrackPoint(
            deliveryJobId: point.deliveryJobId,
            lat: point.lat + delta,
            lng: point.lng + delta * 0.6,
            recordedAt: Date(),
            etaAt: point.etaAt.map { $0.addingTimeInterval(-15) } ?? Date().addingTimeInterval(20 * 60),
            etaSeconds: max(60, (point.etaSeconds ?? 25 * 60) - 15),
            status: "dispatched"
        )
        demoTrackPoint = point
        return point
    }

    // MARK: Wishlist (Fake)

    public func listWishlist() async throws -> [WishlistItem] {
        wishlist.sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
    }

    public func addWishlistItem(stockItemId: UUID?, oem: String?) async throws -> UUID {
        let resolvedOem = oem?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard stockItemId != nil || (resolvedOem?.isEmpty == false) else {
            throw StorefrontError.message("stock_item_id or oem_part_number required")
        }
        if let stockItemId, let existing = wishlist.first(where: { $0.stockItemId == stockItemId }) {
            return existing.id
        }
        if let resolvedOem, let existing = wishlist.first(where: {
            $0.oemPartNumber.caseInsensitiveCompare(resolvedOem) == .orderedSame
        }) {
            return existing.id
        }
        let id = UUID()
        let itemId = stockItemId ?? UUID()
        wishlist.insert(
            WishlistItem(
                id: id,
                stockItemId: itemId,
                oemPartNumber: (resolvedOem?.isEmpty == false) ? resolvedOem! : "OEM-\(itemId.uuidString.prefix(8))",
                description: "Demo wishlist part",
                notifyWhenInStock: false,
                createdAt: Date()
            ),
            at: 0
        )
        return id
    }

    public func removeWishlistItem(wishlistId: UUID?, stockItemId: UUID?, oem: String?) async throws {
        if let wishlistId {
            let before = wishlist.count
            wishlist.removeAll { $0.id == wishlistId }
            if wishlist.count == before {
                throw StorefrontError.message("wishlist item not found")
            }
            return
        }
        if let stockItemId {
            let before = wishlist.count
            wishlist.removeAll { $0.stockItemId == stockItemId }
            if wishlist.count == before {
                throw StorefrontError.message("wishlist item not found")
            }
            return
        }
        if let oem {
            let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
            let before = wishlist.count
            wishlist.removeAll { $0.oemPartNumber.caseInsensitiveCompare(needle) == .orderedSame }
            if wishlist.count == before {
                throw StorefrontError.message("wishlist item not found")
            }
            return
        }
        throw StorefrontError.message("wishlist id, stock item, or OEM required")
    }

    public func setWishlistNotifyWhenInStock(
        notify: Bool,
        wishlistId: UUID?,
        stockItemId: UUID?,
        oem: String?
    ) async throws -> UUID {
        if let wishlistId, let idx = wishlist.firstIndex(where: { $0.id == wishlistId }) {
            wishlist[idx].notifyWhenInStock = notify
            return wishlist[idx].id
        }
        if let stockItemId, let idx = wishlist.firstIndex(where: { $0.stockItemId == stockItemId }) {
            wishlist[idx].notifyWhenInStock = notify
            return wishlist[idx].id
        }
        if let oem {
            let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
            if let idx = wishlist.firstIndex(where: {
                $0.oemPartNumber.caseInsensitiveCompare(needle) == .orderedSame
            }) {
                wishlist[idx].notifyWhenInStock = notify
                return wishlist[idx].id
            }
        }
        throw StorefrontError.message("wishlist item not found")
    }

    public func wishlistMoveToCart(
        wishlistId: UUID?,
        stockItemId: UUID?,
        oem: String?,
        qty: Decimal,
        removeFromWishlist: Bool
    ) async throws -> UUID {
        guard qty > 0 else { throw StorefrontError.message("qty must be > 0") }
        let item: WishlistItem
        if let wishlistId, let found = wishlist.first(where: { $0.id == wishlistId }) {
            item = found
        } else if let stockItemId, let found = wishlist.first(where: { $0.stockItemId == stockItemId }) {
            item = found
        } else if let oem {
            let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let found = wishlist.first(where: {
                $0.oemPartNumber.caseInsensitiveCompare(needle) == .orderedSame
            }) else {
                throw StorefrontError.message("wishlist item not found")
            }
            item = found
        } else {
            throw StorefrontError.message("wishlist item not found")
        }

        let cartId: UUID
        if let open = cart {
            cartId = open.id
        } else {
            cartId = try await createCart(
                warehouseId: UUID(uuidString: "00000000-0000-4000-8000-000000000001")!,
                currency: .USD,
                fulfillmentMode: .immediate,
                exchangeRate: 1
            )
        }
        let lineId = try await addCartLine(
            cartId: cartId,
            stockItemId: item.stockItemId,
            uomId: UUID(),
            qty: qty
        )
        if removeFromWishlist {
            wishlist.removeAll { $0.id == item.id }
        }
        return lineId
    }

    // MARK: Compare (Fake)

    public func listCompareItems() async throws -> [CompareItem] {
        compare.sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
    }

    public func addCompareItem(stockItemId: UUID?, oem: String?) async throws -> UUID {
        let resolvedOem = oem?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard stockItemId != nil || (resolvedOem?.isEmpty == false) else {
            throw StorefrontError.message("stock_item_id or oem_part_number required")
        }
        if let stockItemId, let existing = compare.first(where: { $0.stockItemId == stockItemId }) {
            return existing.id
        }
        if let resolvedOem, let existing = compare.first(where: {
            $0.oemPartNumber.caseInsensitiveCompare(resolvedOem) == .orderedSame
        }) {
            return existing.id
        }
        guard compare.count < maxCompareItems else {
            throw StorefrontError.message("compare list is full (max \(maxCompareItems) items)")
        }
        let id = UUID()
        let itemId = stockItemId ?? UUID()
        compare.insert(
            CompareItem(
                id: id,
                stockItemId: itemId,
                oemPartNumber: (resolvedOem?.isEmpty == false) ? resolvedOem! : "OEM-\(itemId.uuidString.prefix(8))",
                description: "Demo compare part",
                createdAt: Date()
            ),
            at: 0
        )
        return id
    }

    public func removeCompareItem(compareId: UUID?, stockItemId: UUID?, oem: String?) async throws {
        if let compareId {
            let before = compare.count
            compare.removeAll { $0.id == compareId }
            if compare.count == before {
                throw StorefrontError.message("compare item not found")
            }
            return
        }
        if let stockItemId {
            let before = compare.count
            compare.removeAll { $0.stockItemId == stockItemId }
            if compare.count == before {
                throw StorefrontError.message("compare item not found")
            }
            return
        }
        if let oem {
            let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
            let before = compare.count
            compare.removeAll { $0.oemPartNumber.caseInsensitiveCompare(needle) == .orderedSame }
            if compare.count == before {
                throw StorefrontError.message("compare item not found")
            }
            return
        }
        throw StorefrontError.message("compare id, stock item, or OEM required")
    }

    // MARK: Reviews (Fake)

    public func listOwnReviews() async throws -> [ProductReview] {
        reviews.sorted { ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast) }
    }

    public func listApprovedReviews(oem: String) async throws -> [ProductReview] {
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else { return [] }
        return reviews.filter {
            $0.status == .approved
                && ($0.oemPartNumber?.caseInsensitiveCompare(needle) == .orderedSame)
        }
    }

    public func getProductReviewStats(stockItemId: UUID?, oem: String?) async throws -> ProductReviewStats? {
        let approved: [ProductReview]
        if let stockItemId {
            approved = reviews.filter { $0.stockItemId == stockItemId && $0.status == .approved }
        } else if let oem {
            let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
            approved = reviews.filter {
                $0.status == .approved
                    && ($0.oemPartNumber?.caseInsensitiveCompare(needle) == .orderedSame)
            }
        } else {
            throw StorefrontError.message("stock_item_id or oem_part_number required")
        }
        guard let first = approved.first ?? reviews.first(where: { item in
            if let stockItemId { return item.stockItemId == stockItemId }
            if let oem {
                return item.oemPartNumber?.caseInsensitiveCompare(
                    oem.trimmingCharacters(in: .whitespacesAndNewlines)
                ) == .orderedSame
            }
            return false
        }) else {
            return ProductReviewStats(stockItemId: stockItemId ?? UUID(), avgRating: 0, reviewCount: 0)
        }
        let count = approved.count
        let avg: Decimal
        if count == 0 {
            avg = 0
        } else {
            let sum = approved.reduce(0) { $0 + $1.rating }
            avg = Decimal(sum) / Decimal(count)
        }
        return ProductReviewStats(stockItemId: first.stockItemId, avgRating: avg, reviewCount: count)
    }

    public func submitProductReview(
        rating: Int,
        body: String,
        stockItemId: UUID?,
        oem: String?
    ) async throws -> UUID {
        guard (1 ... 5).contains(rating) else {
            throw StorefrontError.message("rating must be 1..5")
        }
        let resolvedOem = oem?.trimmingCharacters(in: .whitespacesAndNewlines)
        guard stockItemId != nil || (resolvedOem?.isEmpty == false) else {
            throw StorefrontError.message("stock_item_id or oem_part_number required")
        }
        if let idx = reviews.firstIndex(where: { r in
            if let stockItemId { return r.stockItemId == stockItemId }
            if let resolvedOem {
                return r.oemPartNumber?.caseInsensitiveCompare(resolvedOem) == .orderedSame
            }
            return false
        }) {
            guard reviews[idx].status != .approved else {
                throw StorefrontError.message("cannot replace an approved review; contact support")
            }
            reviews[idx].rating = rating
            reviews[idx].body = body
            reviews[idx].status = .pending
            reviews[idx].createdAt = Date()
            return reviews[idx].id
        }
        let id = UUID()
        let itemId = stockItemId ?? UUID()
        reviews.insert(
            ProductReview(
                id: id,
                stockItemId: itemId,
                oemPartNumber: (resolvedOem?.isEmpty == false) ? resolvedOem : "OEM-\(itemId.uuidString.prefix(8))",
                description: "Demo review part",
                rating: rating,
                body: body,
                status: .pending,
                createdAt: Date()
            ),
            at: 0
        )
        return id
    }

    public func uploadReviewPhoto(
        reviewId: UUID,
        photo: ReviewPhotoUpload,
        sortOrder _: Int
    ) async throws -> UUID {
        guard let review = reviews.first(where: { $0.id == reviewId }) else {
            throw StorefrontError.message("pending review not found for customer")
        }
        guard review.status == .pending else {
            throw StorefrontError.message("pending review not found for customer")
        }
        guard !photo.data.isEmpty else {
            throw StorefrontError.message("photo data required")
        }
        let count = reviewPhotoCounts[reviewId] ?? 0
        guard count < 5 else {
            throw StorefrontError.message("max 5 photos per review")
        }
        reviewPhotoCounts[reviewId] = count + 1
        return UUID()
    }

    // MARK: Catalog (Fake)

    public func searchCatalog(mode: CatalogSearchMode, query: String) async throws -> SearchCatalogResponse {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { throw StorefrontError.message("search query required") }
        let needle = q.uppercased()
        let hits = catalogProducts.values.filter { p in
            switch mode {
            case .part:
                return p.oem.uppercased().contains(needle) || p.name.uppercased().contains(needle)
            case .vin:
                return needle.hasPrefix("JN") || p.oem.contains("15208")
            case .model:
                return p.name.uppercased().contains(needle) || needle.contains("NAVARA")
            case .pnc:
                return p.category?.uppercased().contains(needle) == true || needle.contains("FILTER")
            }
        }
        .map { CatalogPartHit(oemPartNumber: $0.oem, categoryName: $0.category) }
        return SearchCatalogResponse(mode: mode, query: q, parts: hits, backend: "fts")
    }

    public func searchCatalogMeili(
        mode: CatalogSearchMode,
        query: String,
        limit: Int,
        facets: [String]?
    ) async throws -> SearchCatalogResponse {
        let res = try await searchCatalog(mode: mode, query: query)
        return SearchCatalogResponse(
            mode: res.mode,
            query: res.query,
            parts: Array(res.parts.prefix(min(max(limit, 1), 50))),
            backend: "meili",
            facetDistribution: res.parts.compactMap(\.categoryName).reduce(into: [:]) { acc, cat in
                acc["category_name", default: [:]][cat, default: 0] += 1
            }
        )
    }

    public func listCatalogBrowse(category: String?, limit: Int) async throws -> CatalogBrowseResult {
        let cap = min(max(limit, 1), 100)
        let cat = category?.trimmingCharacters(in: .whitespacesAndNewlines)
        let items = catalogProducts.values
            .filter { item in
                guard let cat, !cat.isEmpty else { return true }
                return CatalogCategoryFilter.matches(filter: cat, fields: item.category)
            }
            .prefix(cap)
            .map {
                CatalogListItem(
                    stockItemId: $0.stockItemId,
                    oem: $0.oem,
                    name: $0.name,
                    stock: $0.stock,
                    usd: $0.usd,
                    category: $0.category
                )
            }
        return CatalogBrowseResult(items: Array(items), categories: ["Filters", "Brakes", "Engine"])
    }

    public func listCatalogMakers() async throws -> [EpcMaker] {
        [EpcMaker(slug: "nissan", name: "Nissan", modelCount: 2)]
    }

    public func listCatalogModels(makerSlug: String) async throws -> [EpcModel] {
        guard makerSlug == "nissan" else { return [] }
        return [
            EpcModel(slug: "x-trail", displayName: "X-Trail", sortKey: "x-trail"),
            EpcModel(slug: "navara", displayName: "Navara", sortKey: "navara"),
        ]
    }

    public func listCatalogVariants(makerSlug: String, modelSlug: String) async throws -> [EpcVariant] {
        switch modelSlug {
        case "x-trail":
            return [EpcVariant(slug: "t31-mr20", chassisCode: "T31", engineCode: "MR20", yearLabel: "2007–2013")]
        case "navara":
            return [EpcVariant(slug: "d40-yd25", chassisCode: "D40", engineCode: "YD25", yearLabel: "2005–2015")]
        default:
            return []
        }
    }

    public func listCatalogSections(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String
    ) async throws -> [EpcSection] {
        [
            EpcSection(slug: "section-filters", name: "Filters", sortOrder: 10),
            EpcSection(slug: "section-engine", name: "Engine", sortOrder: 20),
        ]
    }

    public func getCatalogDiagram(
        makerSlug: String,
        modelSlug: String,
        variantSlug: String,
        sectionSlug: String
    ) async throws -> EpcDiagramResponse {
        EpcDiagramResponse(
            diagramSlug: "demo",
            diagramTitle: sectionSlug,
            hotspots: [
                EpcHotspot(
                    oem: "15208-9N00A",
                    pncCode: "15208",
                    bboxX: 0.1,
                    bboxY: 0.1,
                    bboxWidth: 0.2,
                    bboxHeight: 0.2
                ),
            ],
            parts: [
                EpcDiagramPart(
                    oemPartNumber: "15208-9N00A",
                    pncCode: "15208",
                    categoryName: "Filters",
                    stockItemId: "fake-stock",
                    stockDescription: "Oil filter (demo)"
                ),
            ]
        )
    }

    public func listVehicleMaster() async throws -> [VehicleMasterRow] {
        [
            VehicleMasterRow(
                id: "vm-navara-d40",
                vinPrefix: "MNTCCND40",
                chassisCode: "D40",
                engineCode: "YD25",
                productionYear: 2010,
                modelVariant: "NAVARA"
            ),
            VehicleMasterRow(
                id: "vm-xtrail-t31",
                vinPrefix: "JN1T31XX",
                chassisCode: "T31",
                engineCode: "QR25DE",
                productionYear: 2010,
                modelVariant: "X-TRAIL"
            ),
            VehicleMasterRow(
                id: "vm-gtr-r35",
                vinPrefix: "JN1AR5EF",
                chassisCode: "R35",
                engineCode: "VR38DETT",
                productionYear: 2012,
                modelVariant: "GT-R"
            ),
            VehicleMasterRow(
                id: "vm-almera-n16",
                vinPrefix: "JN1N16",
                chassisCode: "N16",
                engineCode: "QG18DE",
                productionYear: 2002,
                modelVariant: "ALMERA"
            ),
        ]
    }

    public func listCatalogForVehicle(
        chassisCode: String,
        engineCode: String?,
        limit: Int
    ) async throws -> CatalogBrowseResult {
        let chassis = chassisCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !chassis.isEmpty else { throw StorefrontError.message("chassis required") }
        let engine = engineCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        let cap = min(max(limit, 1), 100)
        let items = catalogProducts.values.filter { p in
            let lines = p.fitmentLines.joined(separator: " ").uppercased()
            guard lines.contains(chassis) else { return false }
            if let engine, !engine.isEmpty {
                return lines.contains(engine) || engine.count < 3
            }
            return true
        }
        .prefix(cap)
        .map {
            CatalogListItem(
                stockItemId: $0.stockItemId,
                oem: $0.oem,
                name: $0.name,
                stock: $0.stock,
                usd: $0.usd,
                category: $0.category
            )
        }
        return CatalogBrowseResult(items: Array(items), categories: [])
    }

    public func loadCatalogProduct(oem: String) async throws -> CatalogProduct {
        let key = oem.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if let hit = catalogProducts[key] { return hit }
        if let hit = catalogProducts.values.first(where: {
            $0.oem.caseInsensitiveCompare(oem) == .orderedSame
        }) {
            return hit
        }
        throw StorefrontError.message("Part not found: \(oem)")
    }

    public func addCartLineByOem(oem: String, qty: Decimal) async throws -> (cartId: UUID, lineId: UUID) {
        guard qty > 0 else { throw StorefrontError.message("qty must be > 0") }
        let product = try await loadCatalogProduct(oem: oem)
        let cartId: UUID
        if let open = cart {
            cartId = open.id
        } else {
            cartId = try await createCart(
                warehouseId: Self.seedWarehouseId,
                currency: .USD,
                fulfillmentMode: .immediate,
                exchangeRate: 1
            )
        }
        let lineId = try await addCartLine(
            cartId: cartId,
            stockItemId: product.stockItemId,
            uomId: product.baseUomId,
            qty: qty
        )
        return (cartId, lineId)
    }

    // MARK: Addresses (Fake)

    public func listOwnAddresses() async throws -> [CustomerAddress] {
        addresses.sorted {
            if $0.isDefault != $1.isDefault { return $0.isDefault && !$1.isDefault }
            return ($0.createdAt ?? .distantPast) > ($1.createdAt ?? .distantPast)
        }
    }

    public func upsertCustomerAddress(_ input: CustomerAddressInput) async throws -> UUID {
        let line1 = input.line1.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !line1.isEmpty else {
            throw StorefrontError.message("line1 required for upsert_customer_address")
        }
        let line2 = AddressGeo.embed(
            line2: input.line2,
            latitude: input.latitude,
            longitude: input.longitude
        )
        let id = input.id ?? UUID()
        if input.isDefault {
            for i in addresses.indices {
                addresses[i].isDefault = false
            }
        }
        let now = Date()
        let existing = addresses.first(where: { $0.id == id })
        let row = CustomerAddress(
            id: id,
            label: input.label,
            line1: line1,
            line2: line2,
            city: input.city,
            province: input.province,
            postalCode: input.postalCode,
            country: input.country.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? "Zimbabwe"
                : input.country,
            isDefault: input.isDefault,
            createdAt: existing?.createdAt ?? now,
            updatedAt: now
        )
        if let idx = addresses.firstIndex(where: { $0.id == id }) {
            addresses[idx] = row
        } else {
            addresses.insert(row, at: 0)
        }
        return id
    }

    public func deleteCustomerAddress(id: UUID) async throws {
        let before = addresses.count
        addresses.removeAll { $0.id == id }
        if addresses.count == before {
            throw StorefrontError.message("address not found for delete_customer_address")
        }
    }

    // MARK: Profile (Fake)

    public func loadOwnProfile() async throws -> UserProfile? {
        UserProfile(id: UUID(uuidString: "00000000-0000-4000-8000-0000000000f1")!, fullName: fakeFullName)
    }

    public func loadOwnCustomer() async throws -> CustomerProfile? {
        fakeCustomer
    }

    public func updateOwnFullName(_ fullName: String) async throws {
        let trimmed = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { fakeFullName = trimmed }
    }

    public func updateOwnCustomerContact(_ patch: CustomerContactPatch) async throws {
        fakeCustomer = CustomerProfile(
            id: fakeCustomer.id,
            displayName: patch.displayName ?? fakeCustomer.displayName,
            email: patch.email ?? fakeCustomer.email,
            phoneE164: patch.phoneE164 ?? fakeCustomer.phoneE164,
            whatsappE164: patch.whatsappE164 ?? fakeCustomer.whatsappE164,
            smsReceipts: patch.smsReceipts ?? fakeCustomer.smsReceipts,
            emailReceipts: patch.emailReceipts ?? fakeCustomer.emailReceipts,
            whatsappReceipts: patch.whatsappReceipts ?? fakeCustomer.whatsappReceipts,
            marketingOptIn: fakeCustomer.marketingOptIn,
            lastPromoAt: fakeCustomer.lastPromoAt
        )
    }

    public func setOwnMarketingOptIn(_ optIn: Bool) async throws {
        fakeCustomer = CustomerProfile(
            id: fakeCustomer.id,
            displayName: fakeCustomer.displayName,
            email: fakeCustomer.email,
            phoneE164: fakeCustomer.phoneE164,
            whatsappE164: fakeCustomer.whatsappE164,
            smsReceipts: fakeCustomer.smsReceipts,
            emailReceipts: fakeCustomer.emailReceipts,
            whatsappReceipts: fakeCustomer.whatsappReceipts,
            marketingOptIn: optIn,
            lastPromoAt: fakeCustomer.lastPromoAt
        )
    }

    // MARK: Loyalty / returns / kits (Fake)

    public func getLoyaltyBalance(customerId: UUID) async throws -> LoyaltyBalance {
        LoyaltyBalance(
            customerId: customerId,
            pointsBalance: 120,
            currency: "USD",
            liabilityPerPoint: 0.01,
            estimatedLiability: 1.2
        )
    }

    public func postCustomerReturnCreditNote(
        invoiceId: UUID,
        lines: [ReturnCreditNoteLine]
    ) async throws -> UUID {
        guard !lines.isEmpty else { throw StorefrontError.message("return lines required") }
        guard orders.contains(where: { $0.invoiceId == invoiceId }) else {
            throw StorefrontError.message("invoice not found or not owned")
        }
        return UUID()
    }

    public func listActiveKits(limit: Int) async throws -> [KitListItem] {
        let cap = min(max(limit, 1), 50)
        return [
            KitListItem(
                kitId: UUID(uuidString: "00000000-0000-4000-8000-0000000000k1")!,
                stockItemId: Self.seedOilFilterId,
                oem: "15208-65F0C",
                name: "Oil filter service kit (demo)",
                sellMode: "bundle",
                components: [
                    KitComponent(oem: "15208-65F0C", name: "Oil filter", qty: 1),
                    KitComponent(oem: "11026-JA00A", name: "Drain plug washer", qty: 1),
                ]
            ),
        ].prefix(cap).map { $0 }
    }

    public func listInvoiceLines(invoiceId: UUID) async throws -> [InvoiceLineSummary] {
        guard orders.contains(where: { $0.invoiceId == invoiceId }) else { return [] }
        return [
            InvoiceLineSummary(
                id: UUID(uuidString: "00000000-0000-4000-8000-0000000000l1")!,
                stockItemId: Self.seedOilFilterId,
                uomId: Self.seedUomId,
                qty: 1,
                oemPartNumber: "15208-65F0C",
                description: "Oil filter (demo)"
            ),
            InvoiceLineSummary(
                id: UUID(uuidString: "00000000-0000-4000-8000-0000000000l2")!,
                stockItemId: Self.seedAirFilterId,
                uomId: Self.seedUomId,
                qty: 1,
                oemPartNumber: "16546-EB70A",
                description: "Air cleaner element (demo)"
            ),
        ]
    }

    private static func seedCatalogProducts() -> [String: CatalogProduct] {
        let products = [
            CatalogProduct(
                stockItemId: seedOilFilterId,
                baseUomId: seedUomId,
                oem: "15208-65F0C",
                name: "Oil filter (demo)",
                brand: "Nissan",
                category: "Filters",
                usd: 12.50,
                stock: .inStock,
                coreCharge: 0,
                fitmentLines: ["Navara D40 · YD25DDTi · 2005"]
            ),
            CatalogProduct(
                stockItemId: seedAirFilterId,
                baseUomId: seedUomId,
                oem: "16546-EB70A",
                name: "Air cleaner element (demo)",
                brand: "Nissan",
                category: "Filters",
                usd: 28.00,
                stock: .low,
                coreCharge: 0,
                fitmentLines: ["Navara D40 · YD25DDTi"]
            ),
        ]
        return Dictionary(uniqueKeysWithValues: products.map { ($0.oem.uppercased(), $0) })
    }

    private func stubIntent(invoiceId: UUID, rail: PaymentRail) throws -> PaymentIntentResult {
        guard orders.contains(where: { $0.invoiceId == invoiceId }) else {
            throw StorefrontError.message("Order not found.")
        }
        let intentId = UUID()
        let url = URL(
            string: "gtr-customer://checkout/return?invoice=\(invoiceId.uuidString)&psp=\(rail.rawValue)&stub=1&intent_id=\(intentId.uuidString)"
        )
        return PaymentIntentResult(
            intentId: intentId,
            rail: rail,
            checkoutURL: url,
            stubMessage: "Fake \(rail.title) intent — no PSP crypto. Redirect URL is a stub deep link; settlement stays webhook/service_role only."
        )
    }
}

/// Factory — **prefer Live** when URL + anon present; Fake when env missing / force-fake /
/// Live init failure.
///
/// Live uses URLSession PostgREST (`POST …/rest/v1/rpc/{name}`) so Windows scaffolds
/// need no supabase-swift resolve. On macOS you may later swap the transport for SPM
/// supabase-swift without changing `StorefrontApi` call sites.
@MainActor
public enum StorefrontApiFactory {
    public static func make() -> any StorefrontApi {
        guard AppEnv.prefersLive else {
            return FakeStorefrontApi()
        }
        do {
            return try LiveStorefrontApi()
        } catch {
            return FakeStorefrontApi()
        }
    }
}
