import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var session: StorefrontSession

    var body: some View {
        Group {
            if session.requiresSignIn {
                SignInScreen()
            } else {
                mainTabs
            }
        }
    }

    private var mainTabs: some View {
        TabView {
            ForEach(FeatureTab.allCases) { tab in
                NavigationStack {
                    tabRoot(tab)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                HStack(spacing: 8) {
                                    Text(session.usesFake ? "Fake" : "Live")
                                        .font(.caption2)
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(.quaternary, in: Capsule())
                                    if !session.usesFake {
                                        Button("Sign out") {
                                            session.signOut()
                                        }
                                        .font(.caption)
                                    }
                                }
                            }
                        }
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.systemImage)
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if session.usesFake {
                Text(fakeBannerText)
                    .font(.caption2)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(8)
                    .background(.ultraThinMaterial)
            } else if let email = session.userEmail {
                Text(email)
                    .font(.caption2)
                    .frame(maxWidth: .infinity)
                    .padding(6)
                    .background(.ultraThinMaterial)
            }
        }
    }

    @ViewBuilder
    private func tabRoot(_ tab: FeatureTab) -> some View {
        switch tab {
        case .catalog: CatalogScreen()
        case .cart: CartScreen()
        case .orders: OrdersScreen()
        case .garage: GarageScreen()
        case .wishlist: WishlistScreen()
        case .compare: CompareScreen()
        case .reviews: ReviewsScreen()
        case .pay: PayScreen()
        case .chat: ChatScreen()
        }
    }

    private var fakeBannerText: String {
        if AppEnv.forceFake {
            return "STOREFRONT_FORCE_FAKE — using FakeStorefrontApi (sign-in optional / skipped)"
        }
        if !AppEnv.isConfigured {
            return "SUPABASE_URL / ANON_KEY unset — using FakeStorefrontApi (sign-in skipped)"
        }
        return "Using FakeStorefrontApi (sign-in skipped)"
    }
}

#Preview {
    ContentView()
        .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
