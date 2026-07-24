import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var session: StorefrontSession

    var body: some View {
        TabView {
            ForEach(FeatureTab.allCases) { tab in
                NavigationStack {
                    tabRoot(tab)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Text(session.usesFake ? "Fake" : "Live")
                                    .font(.caption2)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(.quaternary, in: Capsule())
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
            }
        }
    }

    @ViewBuilder
    private func tabRoot(_ tab: FeatureTab) -> some View {
        switch tab {
        case .cart: CartScreen()
        case .orders: OrdersScreen()
        case .garage: GarageScreen()
        case .pay: PayScreen()
        }
    }

    private var fakeBannerText: String {
        if AppEnv.forceFake {
            return "STOREFRONT_FORCE_FAKE — using FakeStorefrontApi"
        }
        if !AppEnv.isConfigured {
            return "SUPABASE_URL / ANON_KEY unset — using FakeStorefrontApi"
        }
        return "Using FakeStorefrontApi"
    }
}

#Preview {
    ContentView()
        .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
