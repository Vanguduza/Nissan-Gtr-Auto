import Foundation

/// Live storefront client — PostgREST RPC / select + edge pay-initiate + live chat.
///
/// RPC names match web `apps/web/lib/customer-storefront.ts`, `apps/web/lib/chat.ts`, and AuthZ migration.
/// Session: uses anon key as Bearer by default; AuthZ customer RPCs require a
/// **customer user JWT** (`customers.profile_id = auth.uid()`). Pass via
/// GoTrue sign-in → `setAccessToken(_:)`, restored `AuthTokenStore`, or
/// scheme env `SUPABASE_ACCESS_TOKEN`.
/// No ContiPay/Paynow secrets or HMAC in the app binary.
/// Chat updates: PostgREST poll from UI (no supabase-swift Realtime on this transport).
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
        public static let startChatThread = "start_chat_thread"
        public static let postChatMessage = "post_chat_message"
        public static let markChatThreadRead = "mark_chat_thread_read"
        public static let chatUnreadCount = "chat_unread_count"
    }

    public enum EdgeName {
        public static let contipayInitiate = "contipay-initiate"
        public static let paynowInitiate = "paynow-initiate"
    }

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

    // MARK: - Private

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
            deliveryNoteStatus: deliveryNoteStatus
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
