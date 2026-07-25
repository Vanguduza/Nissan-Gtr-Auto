import Foundation

/// Thin storefront AuthZ surface — mirrors `apps/web/lib/customer-storefront.ts`
/// and customer chat helpers in `apps/web/lib/chat.ts`.
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

    // MARK: Chat

    func listChatThreads() async throws -> [ChatThread]

    func listChatMessages(threadId: UUID) async throws -> [ChatMessage]

    func startChatThread(_ input: StartChatThreadInput) async throws -> UUID

    func postChatMessage(threadId: UUID, body: String) async throws -> UUID

    func markChatThreadRead(threadId: UUID) async throws

    func chatUnreadCount(threadId: UUID?) async throws -> Int
}

/// In-memory Fake for Simulator / Windows scaffold — no network.
@MainActor
public final class FakeStorefrontApi: StorefrontApi {
    private var cart: CartSummary?
    private var orders: [CustomerOrder] = []
    private var garage: [GarageVehicle] = []

    public init(seedDemo: Bool = true) {
        if seedDemo {
            let inv = UUID()
            orders = [
                CustomerOrder(
                    invoiceId: inv,
                    documentNumber: "INV-DEMO-001",
                    status: "posted",
                    fulfillmentMode: .immediate,
                    currency: .USD,
                    subtotal: 120,
                    total: 120,
                    amountPaid: 0,
                    amountOpen: 120
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
