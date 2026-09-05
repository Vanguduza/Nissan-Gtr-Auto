import Foundation
import SwiftUI

enum AppThemeMode: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: return "System"
        case .light: return "Light"
        case .dark: return "Dark"
        }
    }
}

/// Shared storefront dependency for feature tabs + Supabase Auth session + wish-set + prefs.
@MainActor
final class StorefrontSession: ObservableObject {
    let api: any StorefrontApi
    let usesFake: Bool

    @Published private(set) var isSignedIn: Bool
    @Published private(set) var userEmail: String?
    @Published private(set) var wishOems: Set<String> = []
    @Published private(set) var wishlistItems: [WishlistItem] = []

    @Published var themeMode: AppThemeMode {
        didSet { UserDefaults.standard.set(themeMode.rawValue, forKey: Self.themeKey) }
    }
    @Published var receivePush: Bool {
        didSet { UserDefaults.standard.set(receivePush, forKey: Self.pushKey) }
    }

    private let liveApi: LiveStorefrontApi?
    private var goTrue: GoTrueAuthClient?

    private static let themeKey = "gtr.themeMode"
    private static let pushKey = "gtr.receivePush"

    var requiresSignIn: Bool { !usesFake && !isSignedIn }

    var preferredColorScheme: ColorScheme? {
        switch themeMode {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    init(api: (any StorefrontApi)? = nil) {
        let resolved = api ?? StorefrontApiFactory.make()
        self.api = resolved
        self.usesFake = resolved is FakeStorefrontApi
        self.liveApi = resolved as? LiveStorefrontApi

        let themeRaw = UserDefaults.standard.string(forKey: Self.themeKey) ?? AppThemeMode.system.rawValue
        self.themeMode = AppThemeMode(rawValue: themeRaw) ?? .system
        self.receivePush = UserDefaults.standard.bool(forKey: Self.pushKey)

        if usesFake {
            self.isSignedIn = true
            self.userEmail = nil
            return
        }

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

        if AppEnv.isConfigured { self.goTrue = try? GoTrueAuthClient() }
    }

    func isLiked(oem: String) -> Bool {
        wishOems.contains(oem.trimmingCharacters(in: .whitespacesAndNewlines).uppercased())
    }

    func refreshWishlist() async {
        do {
            let list = try await api.listWishlist()
            wishlistItems = list
            wishOems = Set(list.map { $0.oemPartNumber.uppercased() })
        } catch {
            // Keep prior set on transient failure.
        }
    }

    func toggleWishlist(oem: String, stockItemId: UUID?) async throws {
        let key = oem.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if wishOems.contains(key) {
            let existing = wishlistItems.first {
                $0.oemPartNumber.uppercased() == key || $0.stockItemId == stockItemId
            }
            try await api.removeWishlistItem(
                wishlistId: existing?.id,
                stockItemId: stockItemId ?? existing?.stockItemId,
                oem: oem
            )
        } else {
            _ = try await api.addWishlistItem(stockItemId: stockItemId, oem: oem)
        }
        await refreshWishlist()
    }

    func signIn(email: String, password: String) async throws {
        guard let liveApi else {
            isSignedIn = true
            userEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
            await refreshWishlist()
            return
        }
        guard let goTrue else { throw StorefrontError.notConfigured }
        let authSession = try await goTrue.signIn(email: email, password: password)
        try await applyGoTrueSession(authSession, liveApi: liveApi)
    }

    // MARK: - Supabase Auth Edge signup/recovery

    func requestSignupEmailCode(email: String) async throws {
        guard !usesFake else { return }
        guard let goTrue else { throw StorefrontError.notConfigured }
        try await goTrue.requestSignupEmailCode(email: email)
    }

    func completeSignup(email: String, code: String, password: String) async throws {
        guard let liveApi else {
            isSignedIn = true
            userEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
            return
        }
        guard let goTrue else { throw StorefrontError.notConfigured }
        let verification = try await goTrue.verifySignupEmailCode(email: email, code: code)
        guard verification.emailVerified, verification.signupReady else {
            throw StorefrontError.message("Email verification is incomplete.")
        }
        let authSession = try await goTrue.completeSignup(email: email, password: password)
        try await applyGoTrueSession(authSession, liveApi: liveApi)
    }

    func requestPasswordReset(email: String) async throws {
        guard !usesFake else { return }
        guard let goTrue else { throw StorefrontError.notConfigured }
        try await goTrue.requestPasswordReset(email: email)
    }

    func completePasswordReset(email: String, code: String, newPassword: String) async throws {
        guard let liveApi else {
            isSignedIn = true
            userEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
            return
        }
        guard let goTrue else { throw StorefrontError.notConfigured }
        let authSession = try await goTrue.completePasswordReset(
            email: email,
            code: code,
            newPassword: newPassword
        )
        try await applyGoTrueSession(authSession, liveApi: liveApi)
    }

    /// Sign in with Apple → Supabase Auth `grant_type=id_token`.
    func signInWithApple(idToken: String, rawNonce: String, email: String?) async throws {
        guard let liveApi else {
            isSignedIn = true
            userEmail = email
            await refreshWishlist()
            return
        }
        guard let goTrue else { throw StorefrontError.notConfigured }
        let authSession = try await goTrue.signInWithIdToken(
            provider: .apple,
            idToken: idToken,
            nonce: rawNonce
        )
        try await applyGoTrueSession(authSession, liveApi: liveApi, emailOverride: email)
    }

    /// Google via ASWebAuthenticationSession → Supabase OAuth PKCE.
    func signInWithGoogle() async throws {
        guard let liveApi else {
            isSignedIn = true
            userEmail = userEmail ?? "google@local"
            await refreshWishlist()
            return
        }
        guard let goTrue else { throw StorefrontError.notConfigured }
        let authSession = try await GoogleOAuthBrowser.signIn(using: goTrue)
        try await applyGoTrueSession(authSession, liveApi: liveApi)
    }

    @discardableResult
    func handleAuthCallbackURL(_ url: URL) async -> Bool {
        guard let payload = GoTrueAuthClient.parseAuthCallback(url) else { return false }
        guard let liveApi, let goTrue else { return true }
        do {
            switch payload {
            case .error(let message):
                throw StorefrontError.message(message)
            case .session(let authSession):
                try await applyGoTrueSession(authSession, liveApi: liveApi)
            case .pkce(let code):
                _ = code
                _ = goTrue
                return true
            }
        } catch {
            // Leave signed-out; in-app flows display their own errors.
        }
        return true
    }

    private func applyGoTrueSession(
        _ authSession: GoTrueAuthClient.Session,
        liveApi: LiveStorefrontApi,
        emailOverride: String? = nil
    ) async throws {
        liveApi.setAccessToken(authSession.accessToken)
        let email = emailOverride ?? authSession.email
        AuthTokenStore.save(
            AuthTokenRecord(
                accessToken: authSession.accessToken,
                refreshToken: authSession.refreshToken,
                email: email
            )
        )
        userEmail = email
        isSignedIn = true
        _ = try? await liveApi.ensureOwnCustomerIfNeeded()
        await syncGuestCompareToServer()
        await refreshWishlist()
    }

    func syncGuestCompareToServer() async {
        let local = GuestCompareStore.readOems()
        guard !local.isEmpty else { return }
        for oem in local { _ = try? await api.addCompareItem(stockItemId: nil, oem: oem) }
        if let listed = try? await api.listCompareItems() {
            _ = GuestCompareStore.writeOems(listed.map(\.oemPartNumber))
        }
    }

    func signOut() {
        AuthTokenStore.clear()
        liveApi?.setAccessToken("")
        userEmail = nil
        wishOems = []
        wishlistItems = []
        isSignedIn = usesFake
    }
}

enum FeatureTab: String, CaseIterable, Identifiable {
    case shop
    case cart
    case account

    var id: String { rawValue }
    var title: String {
        switch self {
        case .shop: return "Shop"
        case .cart: return "Cart"
        case .account: return "Account"
        }
    }
    var systemImage: String {
        switch self {
        case .shop: return "magnifyingglass"
        case .cart: return "cart"
        case .account: return "person.crop.circle"
        }
    }
}
