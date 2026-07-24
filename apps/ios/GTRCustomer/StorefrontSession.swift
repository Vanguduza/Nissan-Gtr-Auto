import SwiftUI

/// Shared storefront dependency for feature tabs.
@MainActor
final class StorefrontSession: ObservableObject {
    let api: any StorefrontApi
    let usesFake: Bool

    init(api: (any StorefrontApi)? = nil) {
        let resolved = api ?? StorefrontApiFactory.make()
        self.api = resolved
        self.usesFake = resolved is FakeStorefrontApi
    }
}

enum FeatureTab: String, CaseIterable, Identifiable {
    case cart
    case orders
    case garage
    case pay

    var id: String { rawValue }

    var title: String {
        switch self {
        case .cart: return "Cart"
        case .orders: return "Orders"
        case .garage: return "Garage"
        case .pay: return "Pay"
        }
    }

    var systemImage: String {
        switch self {
        case .cart: return "cart"
        case .orders: return "list.bullet.rectangle"
        case .garage: return "car"
        case .pay: return "creditcard"
        }
    }
}
