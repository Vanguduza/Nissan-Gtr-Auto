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
            if !AppEnv.isConfigured {
                Text("SUPABASE_URL / ANON_KEY unset — using FakeStorefrontApi")
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
}

#Preview {
    ContentView()
        .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
