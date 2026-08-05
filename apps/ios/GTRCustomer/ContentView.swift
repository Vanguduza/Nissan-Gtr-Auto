import SwiftUI

/// KMP 5-tab shell — Home / Shop / Wishlist / My Garage / Settings.
/// Cart + Account / Sign-in live in the top strip (GSF layout reference, GTR brand).
struct ContentView: View {
    @EnvironmentObject private var session: StorefrontSession
    @Binding var pendingPartsOem: String?
    @State private var selectedTab: ShopTab = .home
    @State private var overlay: ShellOverlay = .none
    @State private var catalogSeed: String?
    @State private var catalogSeedToken = UUID()
    @State private var cartBadge = 0
    @State private var showSignInSheet = false
    @State private var payInvoiceId: UUID?

    private enum ShopTab: Hashable {
        case home, shop, wishlist, garage, settings
    }

    private enum ShellOverlay: Equatable {
        case none, menu, cart, account, categories, pay(UUID?), orders
    }

    init(pendingPartsOem: Binding<String?>) {
        _pendingPartsOem = pendingPartsOem
        configureShopTabBar()
    }

    var body: some View {
        Group {
            if session.requiresSignIn {
                SignInScreen()
            } else {
                mainShell
            }
        }
        .preferredColorScheme(session.preferredColorScheme)
        .task {
            await refreshCartBadge()
            await session.refreshWishlist()
        }
        .onChange(of: overlay) { _, _ in
            Task { await refreshCartBadge() }
        }
        .onChange(of: selectedTab) { _, _ in
            Task { await refreshCartBadge() }
        }
        .onChange(of: session.isSignedIn) { _, signedIn in
            if signedIn { showSignInSheet = false }
        }
        .sheet(isPresented: $showSignInSheet) {
            NavigationStack {
                SignInScreen(allowsSkip: session.usesFake, onSkip: { showSignInSheet = false })
                    .environmentObject(session)
            }
        }
    }

    private var mainShell: some View {
        ZStack {
            VStack(spacing: 0) {
                if overlay == .none {
                    CustomerShellTopBar(
                        signedInEmail: session.userEmail,
                        cartBadgeCount: cartBadge,
                        onOpenMenu: { overlay = .menu },
                        onOpenAccount: { overlay = .account },
                        onOpenCart: { overlay = .cart },
                        onSignIn: {
                            if session.isSignedIn && session.userEmail != nil {
                                overlay = .account
                            } else {
                                showSignInSheet = true
                            }
                        }
                    )
                }

                TabView(selection: $selectedTab) {
                    NavigationStack {
                        CatalogScreen(
                            initialOem: $pendingPartsOem,
                            onCartChanged: { Task { await refreshCartBadge() } }
                        )
                    }
                    .tabItem { Label("Home", systemImage: "house.fill") }
                    .tag(ShopTab.home)

                    NavigationStack {
                        CatalogScreen(
                            initialOem: $pendingPartsOem,
                            categorySeed: $catalogSeed,
                            categorySeedToken: $catalogSeedToken,
                            onCartChanged: { Task { await refreshCartBadge() } }
                        )
                    }
                    .tabItem { Label("Shop", systemImage: "storefront.fill") }
                    .tag(ShopTab.shop)

                    NavigationStack {
                        WishlistScreen()
                    }
                    .tabItem { Label("Wishlist", systemImage: "heart.fill") }
                    .tag(ShopTab.wishlist)

                    NavigationStack {
                        GarageScreen()
                    }
                    .tabItem { Label("My Garage", systemImage: "car.fill") }
                    .tag(ShopTab.garage)

                    NavigationStack {
                        SettingsHubScreen(onOpenAccount: { overlay = .account })
                    }
                    .tabItem { Label("Settings", systemImage: "gearshape.fill") }
                    .tag(ShopTab.settings)
                }
                .tint(GTRColors.primary)
                .toolbarBackground(.visible, for: .tabBar)
                .toolbarBackground(Color.white, for: .tabBar)
                .safeAreaInset(edge: .bottom) {
                    if session.usesFake {
                        Text(fakeBannerText)
                            .font(GTRType.label(.caption2))
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .padding(8)
                            .background(GTRColors.mist)
                            .foregroundStyle(GTRColors.steel)
                    } else if let email = session.userEmail {
                        Text(email)
                            .font(GTRType.label(.caption2))
                            .frame(maxWidth: .infinity)
                            .padding(6)
                            .background(GTRColors.mist)
                            .foregroundStyle(GTRColors.silverDim)
                    }
                }
            }

            switch overlay {
            case .none:
                EmptyView()
            case .menu:
                HamburgerMenuOverlay { action in
                    switch action {
                    case .close:
                        overlay = .none
                    case .openAllCategories:
                        overlay = .categories
                    case .openCategory(let label):
                        catalogSeed = label
                        catalogSeedToken = UUID()
                        overlay = .none
                        selectedTab = .shop
                    }
                }
                .zIndex(2)
            case .categories:
                NavigationStack {
                    CategoriesGridScreen(
                        onBack: { overlay = .none },
                        onSelectCategory: { label in
                            catalogSeed = label
                            catalogSeedToken = UUID()
                            overlay = .none
                            selectedTab = .shop
                        }
                    )
                }
                .zIndex(2)
            case .cart:
                NavigationStack {
                    CartScreen(
                        onPay: { id in
                            payInvoiceId = id
                            overlay = .pay(id)
                        },
                        onManageOrders: { overlay = .orders }
                    )
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Back") {
                                Task { await refreshCartBadge() }
                                overlay = .none
                            }
                        }
                    }
                }
                .zIndex(2)
            case .pay(let invoiceId):
                NavigationStack {
                    PayIntentScreen(initialInvoiceId: invoiceId)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Back") { overlay = .cart }
                            }
                        }
                }
                .zIndex(2)
            case .orders:
                NavigationStack {
                    OrdersScreen()
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Back") { overlay = .cart }
                            }
                        }
                }
                .zIndex(2)
            case .account:
                NavigationStack {
                    AccountHubScreen(
                        onClose: { overlay = .none },
                        onOpenGarage: {
                            overlay = .none
                            selectedTab = .garage
                        },
                        onOpenProduct: { oem in
                            pendingPartsOem = oem
                            overlay = .none
                            selectedTab = .home
                        }
                    )
                }
                .zIndex(2)
            }
        }
    }

    private var fakeBannerText: String {
        if AppEnv.forceFake {
            return "STOREFRONT_FORCE_FAKE — FakeStorefrontApi"
        }
        if !AppEnv.isConfigured {
            return "SUPABASE unset — FakeStorefrontApi"
        }
        return "Using FakeStorefrontApi"
    }

    private func refreshCartBadge() async {
        do {
            let cart = try await session.api.loadOpenCart()
            cartBadge = cart?.lines.reduce(0) { partial, line in
                partial + max(1, NSDecimalNumber(decimal: line.qty).intValue)
            } ?? 0
        } catch {
            cartBadge = 0
        }
    }

    private func configureShopTabBar() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor.white
        appearance.shadowColor = UIColor(GTRColors.mist)
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
        UITabBar.appearance().layer.cornerRadius = 16
        UITabBar.appearance().layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        UITabBar.appearance().layer.masksToBounds = true
    }
}

#Preview {
    ContentView(pendingPartsOem: .constant(nil))
        .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
