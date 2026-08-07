import SwiftUI

/// Wishlist tab — KMP 2-column ProductBox grid; hearts driven by shared StorefrontSession wish-set.
struct WishlistScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var oem = "15208-65F0C"
    @State private var selectedCategory: String?
    @State private var status: String?
    @State private var busy = false

    private let chipCategories = ["All", "Brakes", "Filters", "Engine", "Electrical"]

    private var items: [WishlistItem] { session.wishlistItems }

    var body: some View {
        ShopTabBody {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(chipCategories, id: \.self) { cat in
                        let selected = (cat == "All" && selectedCategory == nil) || selectedCategory == cat
                        Button(cat) {
                            selectedCategory = cat == "All" ? nil : cat
                        }
                        .font(GTRType.label(.caption))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(
                            selected ? GTRColors.primary.opacity(0.15) : GTRColors.mist,
                            in: Capsule()
                        )
                        .foregroundStyle(selected ? GTRColors.primary : GTRColors.steel)
                    }
                }
            }

            if filteredItems.isEmpty {
                ShopHonestEmpty(
                    title: "Wishlist is empty",
                    bodyText: "No saved items."
                )
            } else {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    ForEach(filteredItems) { item in
                        VStack(alignment: .leading, spacing: 6) {
                            ShopProductCard(
                                title: item.oemPartNumber,
                                subtitle: item.description,
                                priceLabel: "Saved",
                                liked: true,
                                onLike: { Task { await remove(item) } },
                                onTap: {}
                            )
                            Toggle(
                                "Notify in stock",
                                isOn: Binding(
                                    get: {
                                        session.wishlistItems.first(where: { $0.id == item.id })?.notifyWhenInStock ?? false
                                    },
                                    set: { next in
                                        Task { await setNotify(item, notify: next) }
                                    }
                                )
                            )
                            .font(GTRType.label(.caption2))
                            .disabled(busy)
                            Button("Move to cart") {
                                Task { await moveToCart(item) }
                            }
                            .font(GTRType.label(.caption))
                            .disabled(busy)
                        }
                    }
                }
            }

            ShopMerchTitleRow(title: "Add by OEM", actionLabel: nil)
            TextField("OEM part number", text: $oem)
                .textInputAutocapitalization(.characters)
                .font(GTRType.body())
                .monospaced()
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: GTRRadius.sharp)
                        .stroke(GTRColors.mist, lineWidth: 1)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
                )
            ShopPrimaryButton(
                title: "Add to wishlist",
                enabled: !busy && !oem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ) {
                Task { await add() }
            }

            if let status {
                Text(status)
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.silverDim)
            }
        }
        .navigationTitle("Wishlist")
        .navigationBarTitleDisplayMode(.inline)
        .task { await refresh() }
        .refreshable { await refresh() }
    }

    private var filteredItems: [WishlistItem] {
        guard let selectedCategory else { return items }
        return items.filter { item in
            (item.description ?? item.oemPartNumber).localizedCaseInsensitiveContains(selectedCategory)
        }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        await session.refreshWishlist()
        status = nil
    }

    private func add() async {
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else { return }
        busy = true
        defer { busy = false }
        do {
            if session.isLiked(oem: needle) {
                status = "Already on wishlist"
            } else {
                _ = try await session.api.addWishlistItem(stockItemId: nil, oem: needle)
                await session.refreshWishlist()
                status = "Added \(needle)"
            }
        } catch {
            status = error.localizedDescription
        }
    }

    private func remove(_ item: WishlistItem) async {
        busy = true
        defer { busy = false }
        do {
            try await session.toggleWishlist(oem: item.oemPartNumber, stockItemId: item.stockItemId)
            status = "Removed"
        } catch {
            status = error.localizedDescription
        }
    }

    private func setNotify(_ item: WishlistItem, notify: Bool) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await session.api.setWishlistNotifyWhenInStock(
                notify: notify,
                wishlistId: item.id,
                stockItemId: item.stockItemId,
                oem: item.oemPartNumber
            )
            await session.refreshWishlist()
        } catch {
            status = error.localizedDescription
        }
    }

    private func moveToCart(_ item: WishlistItem) async {
        busy = true
        defer { busy = false }
        do {
            let lineId = try await session.api.wishlistMoveToCart(
                wishlistId: item.id,
                stockItemId: item.stockItemId,
                oem: item.oemPartNumber,
                qty: 1,
                removeFromWishlist: true
            )
            await session.refreshWishlist()
            status = "Moved to cart · line \(lineId.uuidString.prefix(8))…"
        } catch {
            status = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        WishlistScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
