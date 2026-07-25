import Foundation

/// Formats money with explicit currency — never silent USD.
public enum StorefrontFormat {
    public static func money(_ amount: Decimal, currency: StorefrontCurrency) -> String {
        let n = NSDecimalNumber(decimal: amount)
        return "\(currency.rawValue) \(n.stringValue)"
    }

    public static func fulfillment(_ mode: FulfillmentMode) -> String {
        switch mode {
        case .immediate: return "Click & collect"
        case .dispatch: return "Nationwide dispatch"
        }
    }

    public static func chatTime(_ date: Date) -> String {
        date.formatted(
            .dateTime.month(.abbreviated).day().hour().minute()
        )
    }

    /// ETA label from `get_delivery_track_point` — prefers absolute `eta_at`, else seconds.
    public static func etaLabel(etaAt: Date?, etaSeconds: Int?) -> String? {
        if let etaAt {
            return etaAt.formatted(
                .dateTime.month(.abbreviated).day().hour().minute()
            )
        }
        guard let etaSeconds, etaSeconds >= 0 else { return nil }
        let mins = Int((Double(etaSeconds) / 60.0).rounded())
        if mins < 1 { return "Less than a minute" }
        if mins < 60 { return "About \(mins) min" }
        let h = mins / 60
        let m = mins % 60
        return m > 0 ? "About \(h) h \(m) min" : "About \(h) h"
    }
}
