import Foundation

/// Currency codes — matches `public.currency_code` (`USD` | `ZIG`).
public enum StorefrontCurrency: String, Sendable, Codable, CaseIterable {
    case USD
    case ZIG
}

/// Matches `public.fulfillment_mode`.
public enum FulfillmentMode: String, Sendable, Codable, CaseIterable {
    case immediate
    case dispatch
}

/// ContiPay / Paynow method enums used by create-intent RPCs (default `ecocash`).
public enum ContipayMethod: String, Sendable, Codable {
    case ecocash
    case visa
    case zimswitch
}

public enum PaynowMethod: String, Sendable, Codable {
    case ecocash
    case visa
}

public struct CartSummary: Identifiable, Sendable, Equatable {
    public let id: UUID
    public var currency: StorefrontCurrency
    public var fulfillmentMode: FulfillmentMode
    public var exchangeRateApplied: Decimal
    public var lines: [CartLineSummary]

    public init(
        id: UUID,
        currency: StorefrontCurrency,
        fulfillmentMode: FulfillmentMode,
        exchangeRateApplied: Decimal,
        lines: [CartLineSummary] = []
    ) {
        self.id = id
        self.currency = currency
        self.fulfillmentMode = fulfillmentMode
        self.exchangeRateApplied = exchangeRateApplied
        self.lines = lines
    }
}

public struct CartLineSummary: Identifiable, Sendable, Equatable {
    public let id: UUID
    public var stockItemId: UUID
    public var oemPartNumber: String
    public var description: String?
    public var qty: Decimal
    public var unitPrice: Decimal
    public var currency: StorefrontCurrency

    public init(
        id: UUID,
        stockItemId: UUID,
        oemPartNumber: String,
        description: String? = nil,
        qty: Decimal,
        unitPrice: Decimal,
        currency: StorefrontCurrency
    ) {
        self.id = id
        self.stockItemId = stockItemId
        self.oemPartNumber = oemPartNumber
        self.description = description
        self.qty = qty
        self.unitPrice = unitPrice
        self.currency = currency
    }
}

/// Customer-safe order payload from `get_customer_order` (no assignee/GPS trail).
public struct CustomerOrder: Identifiable, Sendable, Equatable {
    public var id: UUID { invoiceId }
    public let invoiceId: UUID
    public var documentNumber: String?
    public var docType: String
    public var status: String
    public var fulfillmentMode: FulfillmentMode
    public var currency: StorefrontCurrency
    public var exchangeRateApplied: Decimal
    public var subtotal: Decimal
    public var total: Decimal
    public var amountPaid: Decimal
    public var amountOpen: Decimal
    public var cartId: UUID?
    public var postedAt: Date?
    public var pickListStatus: String?
    public var deliveryNoteStatus: String?
    /// When present (RPC forward-compat / Fake), opens last-point track for active job.
    public var activeDeliveryJobId: UUID?

    public init(
        invoiceId: UUID,
        documentNumber: String? = nil,
        docType: String = "invoice",
        status: String,
        fulfillmentMode: FulfillmentMode = .immediate,
        currency: StorefrontCurrency = .USD,
        exchangeRateApplied: Decimal = 1,
        subtotal: Decimal = 0,
        total: Decimal = 0,
        amountPaid: Decimal = 0,
        amountOpen: Decimal = 0,
        cartId: UUID? = nil,
        postedAt: Date? = nil,
        pickListStatus: String? = nil,
        deliveryNoteStatus: String? = nil,
        activeDeliveryJobId: UUID? = nil
    ) {
        self.invoiceId = invoiceId
        self.documentNumber = documentNumber
        self.docType = docType
        self.status = status
        self.fulfillmentMode = fulfillmentMode
        self.currency = currency
        self.exchangeRateApplied = exchangeRateApplied
        self.subtotal = subtotal
        self.total = total
        self.amountPaid = amountPaid
        self.amountOpen = amountOpen
        self.cartId = cartId
        self.postedAt = postedAt
        self.pickListStatus = pickListStatus
        self.deliveryNoteStatus = deliveryNoteStatus
        self.activeDeliveryJobId = activeDeliveryJobId
    }

    /// Whether order UI should offer live last-point track entry.
    public var offersLiveDeliveryTrack: Bool {
        if activeDeliveryJobId != nil { return true }
        return fulfillmentMode == .dispatch
    }
}

/// Privacy-safe last point from `get_delivery_track_point` — never a historical trail.
public struct DeliveryTrackPoint: Sendable, Equatable, Identifiable {
    public var id: UUID { deliveryJobId }
    public let deliveryJobId: UUID
    public let lat: Double
    public let lng: Double
    public let recordedAt: Date
    public let etaAt: Date?
    public let etaSeconds: Int?
    public let status: String

    public init(
        deliveryJobId: UUID,
        lat: Double,
        lng: Double,
        recordedAt: Date,
        etaAt: Date? = nil,
        etaSeconds: Int? = nil,
        status: String = "dispatched"
    ) {
        self.deliveryJobId = deliveryJobId
        self.lat = lat
        self.lng = lng
        self.recordedAt = recordedAt
        self.etaAt = etaAt
        self.etaSeconds = etaSeconds
        self.status = status
    }
}

/// How to call `get_delivery_track_point` — job id (owner JWT) and/or share token.
public enum DeliveryTrackRef: Sendable, Equatable, Hashable, Identifiable {
    case job(UUID)
    case token(String)

    public var id: String {
        switch self {
        case .job(let id): return "job:\(id.uuidString)"
        case .token(let t): return "token:\(t)"
        }
    }

    public var jobId: UUID? {
        if case .job(let id) = self { return id }
        return nil
    }

    public var token: String? {
        if case .token(let t) = self { return t }
        return nil
    }
}

public struct GarageVehicle: Identifiable, Sendable, Equatable {
    public let id: UUID
    public var make: String?
    public var model: String?
    public var generation: String?
    public var engine: String?
    public var vin: String?
    public var isPrimary: Bool

    public init(
        id: UUID,
        make: String? = nil,
        model: String? = nil,
        generation: String? = nil,
        engine: String? = nil,
        vin: String? = nil,
        isPrimary: Bool = false
    ) {
        self.id = id
        self.make = make
        self.model = model
        self.generation = generation
        self.engine = engine
        self.vin = vin
        self.isPrimary = isPrimary
    }

    public var label: String {
        let parts = [make, model, generation, engine].compactMap { $0 }.filter { !$0.isEmpty }
        if !parts.isEmpty { return parts.joined(separator: " · ") }
        if let vin, !vin.isEmpty { return "VIN \(vin)" }
        return "Saved vehicle"
    }
}

public struct GarageVehicleInput: Sendable {
    public var id: UUID?
    public var make: String?
    public var model: String?
    public var generation: String?
    public var engine: String?
    public var vin: String?
    public var isPrimary: Bool

    public init(
        id: UUID? = nil,
        make: String? = nil,
        model: String? = nil,
        generation: String? = nil,
        engine: String? = nil,
        vin: String? = nil,
        isPrimary: Bool = false
    ) {
        self.id = id
        self.make = make
        self.model = model
        self.generation = generation
        self.engine = engine
        self.vin = vin
        self.isPrimary = isPrimary
    }
}

public enum PaymentRail: String, Sendable, CaseIterable, Identifiable {
    case contipay
    case paynow

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .contipay: return "ContiPay"
        case .paynow: return "Paynow"
        }
    }
}

/// Result of create-intent — shows intent id; checkout URL is stub redirect only (no PSP crypto).
public struct PaymentIntentResult: Sendable, Equatable {
    public let intentId: UUID
    public let rail: PaymentRail
    public let checkoutURL: URL?
    public let stubMessage: String

    public init(
        intentId: UUID,
        rail: PaymentRail,
        checkoutURL: URL? = nil,
        stubMessage: String = "Stub redirect — open checkout URL when edge returns one; settlement is webhook-only."
    ) {
        self.intentId = intentId
        self.rail = rail
        self.checkoutURL = checkoutURL
        self.stubMessage = stubMessage
    }
}

public enum StorefrontError: Error, LocalizedError, Sendable, Equatable {
    case notConfigured
    case notAuthenticated
    case message(String)

    public var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Set SUPABASE_URL + SUPABASE_ANON_KEY"
        case .notAuthenticated:
            return "Sign in required — AuthZ RPCs need a customer user JWT (sign-in screen or SUPABASE_ACCESS_TOKEN)."
        case .message(let text):
            return text
        }
    }
}

// MARK: - Live chat (matches packages/shared chat types)

/// Matches `chat_thread_kind`.
public enum ChatThreadKind: String, Sendable, Codable, CaseIterable, Identifiable {
    case support
    case parts

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .support: return "Support"
        case .parts: return "Parts"
        }
    }
}

/// Matches `chat_thread_status`.
public enum ChatThreadStatus: String, Sendable, Codable {
    case open
    case assigned
    case closed
}

/// Matches `chat_sender_kind`.
public enum ChatSenderKind: String, Sendable, Codable {
    case customer
    case staff
    case system
}

public struct ChatThread: Identifiable, Sendable, Equatable {
    public let id: UUID
    public var customerUserId: UUID?
    public var kind: ChatThreadKind
    public var status: ChatThreadStatus
    public var subject: String?
    public var lastMessageAt: Date?
    public var createdAt: Date?

    public init(
        id: UUID,
        customerUserId: UUID? = nil,
        kind: ChatThreadKind = .support,
        status: ChatThreadStatus = .open,
        subject: String? = nil,
        lastMessageAt: Date? = nil,
        createdAt: Date? = nil
    ) {
        self.id = id
        self.customerUserId = customerUserId
        self.kind = kind
        self.status = status
        self.subject = subject
        self.lastMessageAt = lastMessageAt
        self.createdAt = createdAt
    }

    public var preview: String {
        let sub = subject?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !sub.isEmpty { return sub }
        return kind == .parts ? "Parts inquiry" : "Support"
    }
}

public struct ChatMessage: Identifiable, Sendable, Equatable {
    public let id: UUID
    public var threadId: UUID
    public var senderUserId: UUID?
    public var senderKind: ChatSenderKind
    public var body: String
    public var createdAt: Date

    public init(
        id: UUID,
        threadId: UUID,
        senderUserId: UUID? = nil,
        senderKind: ChatSenderKind,
        body: String,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.threadId = threadId
        self.senderUserId = senderUserId
        self.senderKind = senderKind
        self.body = body
        self.createdAt = createdAt
    }
}

public struct StartChatThreadInput: Sendable {
    public var kind: ChatThreadKind
    public var subject: String?
    public var body: String?

    public init(
        kind: ChatThreadKind = .support,
        subject: String? = nil,
        body: String? = nil
    ) {
        self.kind = kind
        self.subject = subject
        self.body = body
    }
}

// MARK: - Wishlist / Compare / Reviews (shop ops)

/// Soft cap matching `_customer_compare_max_items()`.
public let maxCompareItems = 8

public struct WishlistItem: Identifiable, Sendable, Equatable {
    public let id: UUID
    public var stockItemId: UUID
    public var oemPartNumber: String
    public var description: String?
    public var notifyWhenInStock: Bool
    public var createdAt: Date?

    public init(
        id: UUID,
        stockItemId: UUID,
        oemPartNumber: String,
        description: String? = nil,
        notifyWhenInStock: Bool = false,
        createdAt: Date? = nil
    ) {
        self.id = id
        self.stockItemId = stockItemId
        self.oemPartNumber = oemPartNumber
        self.description = description
        self.notifyWhenInStock = notifyWhenInStock
        self.createdAt = createdAt
    }

    public var label: String {
        if let description, !description.isEmpty {
            return "\(oemPartNumber) · \(description)"
        }
        return oemPartNumber
    }
}

public struct CompareItem: Identifiable, Sendable, Equatable {
    public let id: UUID
    public var stockItemId: UUID
    public var oemPartNumber: String
    public var description: String?
    public var createdAt: Date?

    public init(
        id: UUID,
        stockItemId: UUID,
        oemPartNumber: String,
        description: String? = nil,
        createdAt: Date? = nil
    ) {
        self.id = id
        self.stockItemId = stockItemId
        self.oemPartNumber = oemPartNumber
        self.description = description
        self.createdAt = createdAt
    }
}

public enum ProductReviewStatus: String, Sendable, Codable, CaseIterable {
    case pending
    case approved
    case rejected
}

public struct ProductReview: Identifiable, Sendable, Equatable {
    public let id: UUID
    public var stockItemId: UUID
    public var oemPartNumber: String?
    public var description: String?
    public var rating: Int
    public var body: String
    public var status: ProductReviewStatus
    public var createdAt: Date?

    public init(
        id: UUID,
        stockItemId: UUID,
        oemPartNumber: String? = nil,
        description: String? = nil,
        rating: Int,
        body: String = "",
        status: ProductReviewStatus = .pending,
        createdAt: Date? = nil
    ) {
        self.id = id
        self.stockItemId = stockItemId
        self.oemPartNumber = oemPartNumber
        self.description = description
        self.rating = rating
        self.body = body
        self.status = status
        self.createdAt = createdAt
    }

    public var label: String {
        let oem = oemPartNumber ?? "Part"
        if let description, !description.isEmpty {
            return "\(oem) · \(description)"
        }
        return oem
    }
}

public struct ProductReviewStats: Sendable, Equatable {
    public var stockItemId: UUID
    public var avgRating: Decimal
    public var reviewCount: Int

    public init(stockItemId: UUID, avgRating: Decimal, reviewCount: Int) {
        self.stockItemId = stockItemId
        self.avgRating = avgRating
        self.reviewCount = reviewCount
    }
}

public struct ReviewPhotoUpload: Sendable {
    public var data: Data
    public var fileExtension: String
    public var contentType: String

    public init(data: Data, fileExtension: String = "jpg", contentType: String = "image/jpeg") {
        self.data = data
        self.fileExtension = fileExtension
        self.contentType = contentType
    }
}
