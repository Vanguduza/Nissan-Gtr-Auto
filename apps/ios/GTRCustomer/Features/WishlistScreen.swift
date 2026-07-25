import SwiftUI

/// Wishlist — list / add / remove / notify / move-to-cart via AuthZ RPCs.
struct WishlistScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var items: [WishlistItem] = []
    @State private var oem = "15208-65F0C"
    @State private var status: String?
    @State private var busy = false

    var body: some View {
        List {
            Section("Saved") {
                if items.isEmpty {
                    Text("No wishlist items")
                        .foregroundStyle(.secondary)
                }
                ForEach(items) { item in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.oemPartNumber).font(.headline.monospaced())
                                if let description = item.description, !description.isEmpty {
                                    Text(description)
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Button(role: .destructive) {
                                Task { await remove(item) }
                            } label: {
                                Image(systemName: "trash")
                            }
                            .disabled(busy)
                        }
                        Toggle(
                            "Notify when back in stock",
                            isOn: Binding(
                                get: {
                                    items.first(where: { $0.id == item.id })?.notifyWhenInStock ?? false
                                },
                                set: { next in
                                    Task { await setNotify(item, notify: next) }
                                }
                            )
                        )
                        .disabled(busy)
                        Button("Move to cart") {
                            Task { await moveToCart(item) }
                        }
                        .disabled(busy)
                    }
                    .padding(.vertical, 4)
                }
            }

            Section("Add by OEM") {
                TextField("OEM part number", text: $oem)
                    .textInputAutocapitalization(.characters)
                    .font(.body.monospaced())
                Button("Add to wishlist") { Task { await add() } }
                    .disabled(busy || oem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }

            if let status {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Wishlist")
        .task { await refresh() }
        .refreshable { await refresh() }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            items = try await session.api.listWishlist()
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func add() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await session.api.addWishlistItem(
                stockItemId: nil,
                oem: oem.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            items = try await session.api.listWishlist()
            status = "Added via add_customer_wishlist_item"
        } catch {
            status = error.localizedDescription
        }
    }

    private func remove(_ item: WishlistItem) async {
        busy = true
        defer { busy = false }
        do {
            try await session.api.removeWishlistItem(
                wishlistId: item.id,
                stockItemId: nil,
                oem: nil
            )
            items = try await session.api.listWishlist()
            status = "Removed via remove_customer_wishlist_item"
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
                stockItemId: nil,
                oem: nil
            )
            items = try await session.api.listWishlist()
            status = "Updated via set_wishlist_notify_when_in_stock"
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
                stockItemId: nil,
                oem: nil,
                qty: 1,
                removeFromWishlist: true
            )
            items = try await session.api.listWishlist()
            status = "Moved via wishlist_move_to_cart → line \(lineId.uuidString.prefix(8))…"
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
