import SwiftUI

/// Scaffold shell only — no catalog/cart/checkout screens until customer API exists.
struct ContentView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("Nissan GTR Auto")
                .font(.largeTitle.weight(.semibold))
            Text("Customer app scaffold")
                .foregroundStyle(.secondary)
            Text(AppEnv.isConfigured ? "Supabase env present" : "Set SUPABASE_URL + SUPABASE_ANON_KEY")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
