import SwiftUI

/// Phase 1 shell — full browse / search / PDP lands in iOS Phase 2 (see docs/plans/2026-07-27-mobile-storefront-parity.md).
struct CatalogScreen: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Catalog")
                .font(.title2.bold())
            Text("Storefront browse, four-way search, and PDP will mirror Android Phase 1 here.")
                .font(.body)
                .foregroundStyle(.secondary)
            Text("RPC: search_catalog · PostgREST stock_items + default price list (USD)")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .padding()
        .navigationTitle("Catalog")
    }
}

#Preview {
    NavigationStack {
        CatalogScreen()
    }
}
