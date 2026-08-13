import Foundation

/// H4 / B-MONEY-1 dual-read helpers for iOS storefront.
///
/// Mirrors `@gtr/shared` `preferAmountMinor` / `displayMajorFromDual` /
/// `displayUnitPriceMajor` / `displayLineTotalMajor` / `sumPreferAmountMinor`
/// and Android `MoneyDualRead`. Prefer `*_minor` when present; fall back to
/// major NUMERIC. Never invents payable amounts — callers supply DB/API values only.
public enum MoneyDualRead {
    /// Minor units per major for USD / ZiG (both 2 decimals today).
    public static let minorPerMajor: Int = 100

    public enum MoneyError: Error, Equatable, LocalizedError {
        case nonFiniteMajor
        case bothAbsent

        public var errorDescription: String? {
            switch self {
            case .nonFiniteMajor:
                return "amountMajor must be finite"
            case .bothAbsent:
                return "preferAmountMinor: need amountMinor or amountMajor"
            }
        }
    }

    /// Convert a decimal major-unit amount to minor units.
    /// Rejects non-finite values; rounds half-away-from-zero to nearest minor.
    public static func toAmountMinor(
        _ amountMajor: Decimal,
        currency _: StorefrontCurrency = .USD
    ) throws -> Int64 {
        let d = NSDecimalNumber(decimal: amountMajor).doubleValue
        guard d.isFinite else { throw MoneyError.nonFiniteMajor }
        let scaled = d * Double(minorPerMajor)
        let rounded: Double
        if scaled >= 0 {
            rounded = floor(scaled + 0.5)
        } else {
            rounded = ceil(scaled - 0.5)
        }
        return Int64(rounded)
    }

    /// Convert minor units back to major for display / legacy bridge.
    public static func fromAmountMinor(
        _ amountMinor: Int64,
        currency _: StorefrontCurrency = .USD
    ) -> Decimal {
        Decimal(amountMinor) / Decimal(minorPerMajor)
    }

    /// Dual-read: prefer `amountMinor` when present; else convert `amountMajor`.
    public static func preferAmountMinor(
        amountMinor: Int64?,
        amountMajor: Decimal?,
        currency: StorefrontCurrency = .USD
    ) throws -> Int64 {
        if let amountMinor { return amountMinor }
        guard let amountMajor else { throw MoneyError.bothAbsent }
        return try toAmountMinor(amountMajor, currency: currency)
    }

    /// Display major from dual-read row (minor wins when present).
    public static func displayMajorFromDual(
        amountMinor: Int64?,
        amountMajor: Decimal?,
        currency: StorefrontCurrency = .USD
    ) throws -> Decimal {
        let minor = try preferAmountMinor(
            amountMinor: amountMinor,
            amountMajor: amountMajor,
            currency: currency
        )
        return fromAmountMinor(minor, currency: currency)
    }

    /// Display major for a dual-read unit price.
    public static func displayUnitPriceMajor(
        unitPrice: Decimal,
        unitPriceMinor: Int64?,
        currency: StorefrontCurrency = .USD
    ) throws -> Decimal {
        try displayMajorFromDual(
            amountMinor: unitPriceMinor,
            amountMajor: unitPrice,
            currency: currency
        )
    }

    /// Display major for a dual-read line total.
    public static func displayLineTotalMajor(
        lineTotal: Decimal,
        lineTotalMinor: Int64?,
        currency: StorefrontCurrency = .USD
    ) throws -> Decimal {
        try displayMajorFromDual(
            amountMinor: lineTotalMinor,
            amountMajor: lineTotal,
            currency: currency
        )
    }

    /// Sum dual-read money rows in minor units (H4 cart totals).
    /// Each row is `(amountMinor?, amountMajor?)`.
    public static func sumPreferAmountMinor(
        rows: [(amountMinor: Int64?, amountMajor: Decimal?)],
        currency: StorefrontCurrency = .USD
    ) throws -> Int64 {
        var total: Int64 = 0
        for row in rows {
            total += try preferAmountMinor(
                amountMinor: row.amountMinor,
                amountMajor: row.amountMajor,
                currency: currency
            )
        }
        return total
    }
}

// MARK: - Cart / order display extensions

public extension CartLineSummary {
    /// H4 dual-read: unit price display (minor wins when present).
    func displayUnitPrice() -> Decimal {
        (try? MoneyDualRead.displayUnitPriceMajor(
            unitPrice: unitPrice,
            unitPriceMinor: unitPriceMinor,
            currency: currency
        )) ?? unitPrice
    }

    /// H4 dual-read: line total display (minor wins when present).
    func displayLineTotal() -> Decimal {
        let major = lineTotal ?? (unitPrice * qty)
        return (try? MoneyDualRead.displayLineTotalMajor(
            lineTotal: major,
            lineTotalMinor: lineTotalMinor,
            currency: currency
        )) ?? major
    }
}

public extension CartSummary {
    /// H4 dual-read cart subtotal via minor units when present.
    func displaySubtotal() -> Decimal {
        let rows = lines.map {
            (
                amountMinor: $0.lineTotalMinor,
                amountMajor: $0.lineTotal ?? ($0.unitPrice * $0.qty)
            )
        }
        guard let sum = try? MoneyDualRead.sumPreferAmountMinor(rows: rows, currency: currency) else {
            return lines.reduce(Decimal.zero) { $0 + ($1.lineTotal ?? ($1.unitPrice * $1.qty)) }
        }
        return MoneyDualRead.fromAmountMinor(sum, currency: currency)
    }
}

public extension CustomerOrder {
    /// H4 dual-read: total (prefer `totalMinor` when API returns it).
    func displayTotal() -> Decimal {
        (try? MoneyDualRead.displayMajorFromDual(
            amountMinor: totalMinor,
            amountMajor: total,
            currency: currency
        )) ?? total
    }

    /// H4 dual-read: subtotal.
    func displaySubtotal() -> Decimal {
        (try? MoneyDualRead.displayMajorFromDual(
            amountMinor: subtotalMinor,
            amountMajor: subtotal,
            currency: currency
        )) ?? subtotal
    }

    /// H4 dual-read: amount paid.
    func displayAmountPaid() -> Decimal {
        (try? MoneyDualRead.displayMajorFromDual(
            amountMinor: amountPaidMinor,
            amountMajor: amountPaid,
            currency: currency
        )) ?? amountPaid
    }

    /// H4 dual-read: open balance (prefer explicit open minor, else total−paid dual-read).
    func displayAmountOpen() -> Decimal {
        if let amountOpenMinor {
            return MoneyDualRead.fromAmountMinor(amountOpenMinor, currency: currency)
        }
        if totalMinor != nil || amountPaidMinor != nil {
            let totalM =
                (try? MoneyDualRead.preferAmountMinor(
                    amountMinor: totalMinor,
                    amountMajor: total,
                    currency: currency
                )) ?? 0
            let paidM =
                (try? MoneyDualRead.preferAmountMinor(
                    amountMinor: amountPaidMinor,
                    amountMajor: amountPaid,
                    currency: currency
                )) ?? 0
            return MoneyDualRead.fromAmountMinor(totalM - paidM, currency: currency)
        }
        return amountOpen
    }
}
