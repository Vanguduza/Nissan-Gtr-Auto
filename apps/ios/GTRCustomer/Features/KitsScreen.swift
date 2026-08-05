import SwiftUI

struct KitsScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    var onOpenProduct: (String) -> Void
    @State private var kits: [KitListItem] = []
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ShopDefaultScreen(title: "Service kits", subtitle: "Bundles", scrollable: true) {
            Text("Active kits from inventory. Tap a component OEM to open the product page.")
                .font(GTRType.body(.caption))
                .foregroundStyle(GTRColors.silverDim)

            Button("Refresh kits") { Task { await refresh() } }
                .buttonStyle(.borderedProminent)
                .tint(GTRColors.primary)
                .disabled(busy)
                .frame(maxWidth: .infinity)

            if kits.isEmpty && !busy {
                ShopHonestEmpty(
                    title: "No active kits",
                    bodyText: "Service kits appear here when published in item_kits."
                )
            }

            ForEach(kits) { kit in
                ShopMerchTitleRow(title: kit.name, actionLabel: kit.sellMode)
                Button {
                    onOpenProduct(kit.oem)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(kit.oem)
                            .font(GTRType.body(.body))
                            .foregroundStyle(GTRColors.steel)
                        Text("\(kit.components.count) component(s) · tap to open PDP")
                            .font(GTRType.body(.caption))
                            .foregroundStyle(GTRColors.silverDim)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
                }
                .buttonStyle(.plain)
                ForEach(kit.components, id: \.oem) { comp in
                    Button {
                        onOpenProduct(comp.oem)
                    } label: {
                        Text("\(comp.oem) · \(comp.name) × \(Int(comp.qty))")
                    }
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.primary)
                }
            }

            if let error {
                Text(error)
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.primary)
            }
        }
        .task { await refresh() }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            kits = try await session.api.listActiveKits(limit: 50)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}
