import SwiftUI

/// Compare tray — auth RPCs, or `GuestCompareStore` when signed out.
/// Attribute matrix subset: OEM + description side-by-side.
struct CompareScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var items: [CompareItem] = []
    @State private var oem = "15208-65F0C"
    @State private var status: String?
    @State private var busy = false
    @State private var usingGuestStore = false

    var body: some View {
        List {
            Section {
                Text(usingGuestStore
                     ? "Guest mode — OEMs stored on-device (UserDefaults)."
                     : "Synced via list_customer_compare_items / add / remove.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section("Compare list") {
                if items.isEmpty {
                    Text("No items to compare")
                        .foregroundStyle(.secondary)
                }
                ForEach(items) { item in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.oemPartNumber).font(.headline.monospaced())
                            Text(item.description ?? "—")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
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
                    matrixRow(label: "OEM", values: items.map(\.oemPartNumber))
                    matrixRow(
                        label: "Description",
                        values: items.map { $0.description?.isEmpty == false ? $0.description! : "—" }
                    )
                    matrixRow(
                        label: "Stock item",
                        values: items.map {
                            usingGuestStore ? "local" : String($0.stockItemId.uuidString.prefix(8)) + "…"
                        }
                    )
                }
            }

            Section("Add by OEM") {
                TextField("OEM part number", text: $oem)
                    .textInputAutocapitalization(.characters)
                    .font(.body.monospaced())
                Button("Add to compare") { Task { await add() } }
                    .disabled(busy || oem.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                              || items.count >= maxCompareItems)
                Text("Max \(maxCompareItems) items")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if let status {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Compare")
        .task { await refresh() }
        .refreshable { await refresh() }
    }

    @ViewBuilder
    private func matrixRow(label: String, values: [String]) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    ForEach(Array(values.enumerated()), id: \.offset) { _, value in
                        Text(value)
                            .font(.caption.monospaced())
                            .frame(width: 120, alignment: .leading)
                            .padding(8)
                            .background(.quaternary, in: RoundedRectangle(cornerRadius: 8))
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

    private func add() async {
        busy = true
        defer { busy = false }
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            if preferGuest {
                usingGuestStore = true
                _ = try GuestCompareStore.addOem(needle)
                items = GuestCompareStore.asCompareItems()
                status = "Added to GuestCompareStore"
            } else {
                _ = try await session.api.addCompareItem(stockItemId: nil, oem: needle)
                items = try await session.api.listCompareItems()
                _ = GuestCompareStore.writeOems(items.map(\.oemPartNumber))
                status = "Added via add_customer_compare_item"
            }
        } catch {
            status = error.localizedDescription
        }
    }

    private func remove(_ item: CompareItem) async {
        busy = true
        defer { busy = false }
        do {
            if preferGuest {
                _ = GuestCompareStore.removeOem(item.oemPartNumber)
                items = GuestCompareStore.asCompareItems()
                status = "Removed from GuestCompareStore"
            } else {
                try await session.api.removeCompareItem(
                    compareId: item.id,
                    stockItemId: nil,
                    oem: nil
                )
                items = try await session.api.listCompareItems()
                _ = GuestCompareStore.writeOems(items.map(\.oemPartNumber))
                status = "Removed via remove_customer_compare_item"
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
