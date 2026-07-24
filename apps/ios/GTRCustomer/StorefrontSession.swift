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
