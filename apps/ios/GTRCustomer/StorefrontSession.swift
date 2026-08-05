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

/// Shared storefront dependency for feature tabs + GoTrue session + wish-set + prefs.
@MainActor
final class StorefrontSession: ObservableObject {
    let api: any StorefrontApi
    let usesFake: Bool

    @Published private(set) var isSignedIn: Bool
    @Published private(set) var userEmail: String?
    /// Uppercased OEM keys currently wished — sticky hearts across Home/Shop/PDP/Wishlist.
    @Published private(set) var wishOems: Set<String> = []
    @Published private(set) var wishlistItems: [WishlistItem] = []

    @Published var themeMode: AppThemeMode {
        didSet { UserDefaults.standard.set(themeMode.rawValue, forKey: Self.themeKey) }
    }
    /// Local preference only — no FCM wiring yet.
    @Published var receivePush: Bool {
        didSet { UserDefaults.standard.set(receivePush, forKey: Self.pushKey) }
    }

    private let liveApi: LiveStorefrontApi?
    private var goTrue: GoTrueAuthClient?

    private static let themeKey = "gtr.themeMode"
    private static let pushKey = "gtr.receivePush"

    var requiresSignIn: Bool {
        !usesFake && !isSignedIn
    }

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

        if AppEnv.isConfigured {
            self.goTrue = try? GoTrueAuthClient()
        }
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
        await refreshWishlist()
    }

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
        wishOems = []
        wishlistItems = []
        if usesFake {
            isSignedIn = true
        } else {
            isSignedIn = false
        }
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
