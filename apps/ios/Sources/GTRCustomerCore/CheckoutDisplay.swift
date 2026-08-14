import Foundation

/// D-57 pay method — mirrors `@gtr/payments` PspMethod subset used by checkout display.
public enum CheckoutPayMethod: String, Sendable, Equatable {
    case contipay
    case paynow
    case ecocash
    case cash
}

/// Minor-unit money DTO for checkout payable (H4 / D-57).
public struct MoneyMinorDto: Sendable, Equatable {
    public var amountMinor: Int64
    public var currency: StorefrontCurrency
    public var fxRateId: String?

    public init(
        amountMinor: Int64,
        currency: StorefrontCurrency,
        fxRateId: String? = nil
    ) {
        self.amountMinor = amountMinor
        self.currency = currency
        self.fxRateId = fxRateId
    }
}

/// D-57 checkout display — mirrors `@gtr/payments` `buildCheckoutDisplay`.
///
/// Browse/cart stays USD; ZiG only at pay step with ops daily rate + optional
/// `fxRateId`. Never invents rates or payable amounts.
public struct CheckoutDisplay: Sendable, Equatable {
    public var browseCurrency: StorefrontCurrency
    public var payCurrency: StorefrontCurrency
    public var payable: MoneyMinorDto
    public var fxRateId: String?
    public var indicativeZigMinor: Int64?

    public init(
        browseCurrency: StorefrontCurrency = .USD,
        payCurrency: StorefrontCurrency,
        payable: MoneyMinorDto,
        fxRateId: String? = nil,
        indicativeZigMinor: Int64? = nil
    ) {
        self.browseCurrency = browseCurrency
        self.payCurrency = payCurrency
        self.payable = payable
        self.fxRateId = fxRateId
        self.indicativeZigMinor = indicativeZigMinor
    }
}

public enum CheckoutDisplayError: Error, Equatable, LocalizedError {
    case dailyZigRateRequired

    public var errorDescription: String? {
        switch self {
        case .dailyZigRateRequired:
            return "Daily ZiG rate required for EcoCash checkout"
        }
    }
}

public enum CheckoutDisplayBuilder {
    /// Build D-57 checkout display from USD minor + ops rate.
    /// - Throws: ``CheckoutDisplayError/dailyZigRateRequired`` when EcoCash lacks a positive rate.
    public static func build(
        usdMinor: Int64,
        payMethod: CheckoutPayMethod,
        zigRatePerUsd: Decimal? = nil,
        fxRateId: String? = nil
    ) throws -> CheckoutDisplay {
        let zigWallet = payMethod == .ecocash
        if zigWallet {
            guard let rate = zigRatePerUsd, rate > 0 else {
                throw CheckoutDisplayError.dailyZigRateRequired
            }
            let zigMinor = Self.roundMinor(Decimal(usdMinor) * rate)
            return CheckoutDisplay(
                browseCurrency: .USD,
                payCurrency: .ZIG,
                payable: MoneyMinorDto(
                    amountMinor: zigMinor,
                    currency: .ZIG,
                    fxRateId: fxRateId
                ),
                fxRateId: fxRateId
            )
        }
        let indicative: Int64?
        if let rate = zigRatePerUsd, rate > 0 {
            indicative = Self.roundMinor(Decimal(usdMinor) * rate)
        } else {
            indicative = nil
        }
        return CheckoutDisplay(
            browseCurrency: .USD,
            payCurrency: .USD,
            payable: MoneyMinorDto(
                amountMinor: usdMinor,
                currency: .USD,
                fxRateId: nil
            ),
            fxRateId: nil,
            indicativeZigMinor: indicative
        )
    }

    /// ZiG settlement from USD minor + ops rate (fail-closed when rate missing).
    public static func buildZigSettlement(
        usdMinor: Int64,
        zigRatePerUsd: Decimal?,
        fxRateId: String?
    ) throws -> CheckoutDisplay {
        try build(
            usdMinor: usdMinor,
            payMethod: .ecocash,
            zigRatePerUsd: zigRatePerUsd,
            fxRateId: fxRateId
        )
    }

    private static func roundMinor(_ value: Decimal) -> Int64 {
        let d = NSDecimalNumber(decimal: value).doubleValue
        return Int64((d >= 0 ? floor(d + 0.5) : ceil(d - 0.5)))
    }
}
