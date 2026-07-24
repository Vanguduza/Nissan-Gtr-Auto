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
}
