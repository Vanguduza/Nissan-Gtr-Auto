import SwiftUI

/// Shared storefront dependency for feature tabs + GoTrue session.
@MainActor
final class StorefrontSession: ObservableObject {
    let api: any StorefrontApi
    let usesFake: Bool

    /// Customer JWT present (Live) or Fake (always treated as signed-in for gating).
    @Published private(set) var isSignedIn: Bool
    @Published private(set) var userEmail: String?

    private let liveApi: LiveStorefrontApi?
    private var goTrue: GoTrueAuthClient?

    /// Live mode without a customer JWT — main tabs stay behind `SignInScreen`.
    var requiresSignIn: Bool {
        !usesFake && !isSignedIn
    }

    init(api: (any StorefrontApi)? = nil) {
        let resolved = api ?? StorefrontApiFactory.make()
        self.api = resolved
        self.usesFake = resolved is FakeStorefrontApi
        self.liveApi = resolved as? LiveStorefrontApi

        if usesFake {
            self.isSignedIn = true
            self.userEmail = nil
            return
        }

        // Prefer scheme env JWT (one-shot), else restore UserDefaults scaffold store.
        if let envToken = AppEnv.accessToken {
            liveApi?.setAccessToken(envToken)
            self.isSignedIn = true
            self.userEmail = nil
        } else if let stored = AuthTokenStore.load() {
            liveApi?.setAccessToken(stored.accessToken)
            self.isSignedIn = true
            self.userEmail = stored.email
        } else {
            self.isSignedIn = false
            self.userEmail = nil
        }

        if AppEnv.isConfigured {
            self.goTrue = try? GoTrueAuthClient()
        }
    }

    func signIn(email: String, password: String) async throws {
        guard let liveApi else {
            // Fake: no network — mark signed-in for optional UI paths.
            isSignedIn = true
            userEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
            return
        }
        guard let goTrue else {
            throw StorefrontError.notConfigured
        }

        let session = try await goTrue.signIn(email: email, password: password)
        liveApi.setAccessToken(session.accessToken)
        AuthTokenStore.save(
            AuthTokenRecord(
                accessToken: session.accessToken,
                refreshToken: session.refreshToken,
                email: session.email
            )
        )
        userEmail = session.email
        isSignedIn = true
        await syncGuestCompareToServer()
    }

    /// Push guest UserDefaults OEMs into `add_customer_compare_item` after login (web parity).
    func syncGuestCompareToServer() async {
        let local = GuestCompareStore.readOems()
        guard !local.isEmpty else { return }
        for oem in local {
            _ = try? await api.addCompareItem(stockItemId: nil, oem: oem)
        }
        if let listed = try? await api.listCompareItems() {
            _ = GuestCompareStore.writeOems(listed.map(\.oemPartNumber))
        }
    }

    func signOut() {
        AuthTokenStore.clear()
        liveApi?.setAccessToken("")
        userEmail = nil
        if usesFake {
            isSignedIn = true
        } else {
            isSignedIn = false
        }
    }
}

enum FeatureTab: String, CaseIterable, Identifiable {
    case catalog
    case cart
    case orders
    case garage
    case wishlist
    case compare
    case reviews
    case pay
    case chat

    var id: String { rawValue }

    var title: String {
        switch self {
        case .catalog: return "Catalog"
        case .cart: return "Cart"
        case .orders: return "Orders"
        case .garage: return "Garage"
        case .wishlist: return "Wishlist"
        case .compare: return "Compare"
        case .reviews: return "Reviews"
        case .pay: return "Pay"
        case .chat: return "Chat"
        }
    }

    var systemImage: String {
        switch self {
        case .catalog: return "square.grid.2x2"
        case .cart: return "cart"
        case .orders: return "list.bullet.rectangle"
        case .garage: return "car"
        case .wishlist: return "heart"
        case .compare: return "rectangle.split.2x1"
        case .reviews: return "star"
        case .pay: return "creditcard"
        case .chat: return "bubble.left.and.bubble.right"
        }
    }
}
