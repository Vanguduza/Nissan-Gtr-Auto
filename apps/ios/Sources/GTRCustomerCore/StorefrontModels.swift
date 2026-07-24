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

/// Customer-safe order payload from `get_customer_order` (no assignee/GPS).
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
        deliveryNoteStatus: String? = nil
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
