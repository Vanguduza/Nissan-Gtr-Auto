import SwiftUI

/// Compare tray — auth RPCs, or `GuestCompareStore` when signed out.
/// Customer product names/descriptions stay visible; technical identities stay internal.
struct CompareScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var items: [CompareItem] = []
    @State private var oem = ""
    @State private var matchingProducts: [CatalogListItem] = []
    @State private var status: String?
    @State private var busy = false
    @State private var usingGuestStore = false

    var body: some View {
        ShopDefaultScreen(title: "Compare", subtitle: nil, scrollable: false) {
            List {
            Section {
                Text(usingGuestStore
                     ? "Saved on this device."
                     : "Synced to your account.")
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.silverDim)
            }

            Section("Compare list") {
                if items.isEmpty {
                    Text("No items to compare")
                        .font(GTRType.body(.subheadline))
                        .foregroundStyle(GTRColors.silverDim)
                }
                ForEach(items) { item in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(CustomerCatalogPresentation.productName(item.description, identifier: item.oemPartNumber))
                                .font(GTRType.displaySemi(.headline))
                                .monospaced()
                            Text("Saved product")
                                .font(GTRType.body(.subheadline))
                                .foregroundStyle(GTRColors.silverDim)
                        }
                        Spacer()
                        Button(role: .destructive) {
                            Task { await remove(item) }
                        } label: {
                            Image(systemName: "trash")
                        }
                        .disabled(busy)
                    }
                }
            }

            if items.count >= 2 {
                Section("Attribute matrix") {
                    matrixRow(label: "Product", values: items.map { CustomerCatalogPresentation.productName($0.description, identifier: $0.oemPartNumber) })
                    matrixRow(
                        label: "Description",
                        values: items.map { CustomerCatalogPresentation.productName($0.description, identifier: $0.oemPartNumber) }
                    )

                }
            }

            Section("Find a product") {
                TextField("Search products", text: $oem)
                    .textInputAutocapitalization(.characters)
                    .font(GTRType.body())
                    .monospaced()
                Button("Find products") { Task { await searchProducts() } }
                    .disabled(busy || oem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                              || items.count >= maxCompareItems)
                ForEach(matchingProducts) { product in
                    Button(CustomerCatalogPresentation.productName(product.name, identifier: product.oem)) {
                        Task { await add(product) }
                    }.disabled(busy || items.count >= maxCompareItems)
                }
                Text("Max \(maxCompareItems) items")
                    .font(GTRType.label(.caption))
                    .foregroundStyle(GTRColors.silverDim)
            }

            if let status {
                Section {
                    Text(status)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }
            }
            .scrollContentBackground(.hidden)
            .background(GTRColors.chalk)
            .navigationBarTitleDisplayMode(.inline)
            .task { await refresh() }
            .refreshable { await refresh() }
        }
    }

    @ViewBuilder
    private func matrixRow(label: String, values: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(GTRType.label(.caption))
                .foregroundStyle(GTRColors.silverDim)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    ForEach(Array(values.enumerated()), id: \.offset) { _, value in
                        Text(value)
                            .font(GTRType.label(.caption))
                            .monospaced()
                            .frame(width: 120, alignment: .leading)
                            .padding(8)
                            .background(GTRColors.mist, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
                    }
                }
            }
        }
    }

    private var preferGuest: Bool {
        !session.isSignedIn
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            if preferGuest {
                usingGuestStore = true
                items = GuestCompareStore.asCompareItems()
                for index in items.indices {
                    if let product = try? await session.api.loadCatalogProduct(oem: items[index].oemPartNumber) {
                        items[index].description = product.name
                    }
                }
            } else {
                usingGuestStore = false
                items = try await session.api.listCompareItems()
                // Mirror OEMs for guest badge parity after login.
                _ = GuestCompareStore.writeOems(items.map(\.oemPartNumber))
            }
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func searchProducts() async {
        busy = true
        defer { busy = false }
        do {
            matchingProducts = try await session.api.searchProducts(query: oem, limit: 20)
            status = matchingProducts.isEmpty ? "No matching products." : nil
        } catch { status = "Products could not be loaded. Please try again." }
    }

    private func add(_ product: CatalogListItem) async {
        busy = true
        defer { busy = false }
        do {
            if preferGuest {
                usingGuestStore = true
                _ = try GuestCompareStore.addOem(product.oem)
                await refresh()
            } else {
                _ = try await session.api.addCompareItem(stockItemId: product.stockItemId, oem: nil)
                items = try await session.api.listCompareItems()
                _ = GuestCompareStore.writeOems(items.map(\.oemPartNumber))
            }
            status = "Added to compare."
        } catch { status = "This product could not be added. Please try again." }
    }

    private func remove(_ item: CompareItem) async {
        busy = true
        defer { busy = false }
        do {
            if preferGuest {
                _ = GuestCompareStore.removeOem(item.oemPartNumber)
                items = GuestCompareStore.asCompareItems()
                for index in items.indices {
                    if let product = try? await session.api.loadCatalogProduct(oem: items[index].oemPartNumber) {
                        items[index].description = product.name
                    }
                }
                status = "Removed."
            } else {
                try await session.api.removeCompareItem(
                    compareId: item.id,
                    stockItemId: nil,
                    oem: nil
                )
                items = try await session.api.listCompareItems()
                _ = GuestCompareStore.writeOems(items.map(\.oemPartNumber))
                status = "Removed."
            }
        } catch {
            status = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        CompareScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
