import SwiftUI

/// Thin cart screen — create / add line / checkout RPCs via `StorefrontApi`.
struct CartScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var cart: CartSummary?
    @State private var status: String?
    @State private var busy = false
    @State private var lastInvoiceId: UUID?

    private let demoWarehouseId = UUID(uuidString: "00000000-0000-4000-8000-000000000001")!

    var body: some View {
        List {
            Section {
                if let cart {
                    LabeledContent("Cart", value: String(cart.id.uuidString.prefix(8)) + "…")
                    LabeledContent("Currency", value: cart.currency.rawValue)
                    LabeledContent("Fulfillment", value: StorefrontFormat.fulfillment(cart.fulfillmentMode))
                    ForEach(cart.lines) { line in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(line.oemPartNumber).font(.headline)
                            Text(line.description ?? "Part")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Text("Qty \(line.qty) · \(StorefrontFormat.money(line.unitPrice, currency: line.currency))")
                                .font(.caption)
                        }
                    }
                } else {
                    Text("No open cart")
                        .foregroundStyle(.secondary)
                }
            }

            Section("Actions") {
                Button("Create cart") { Task { await createCart() } }
                    .disabled(busy || cart != nil)
                Button("Add demo line") { Task { await addLine() } }
                    .disabled(busy || cart == nil)
                Button("Checkout") { Task { await checkout() } }
                    .disabled(busy || cart == nil || (cart?.lines.isEmpty ?? true))
            }

            if let lastInvoiceId {
                Section("Last checkout") {
                    Text("Invoice \(lastInvoiceId.uuidString)")
                        .font(.caption.monospaced())
                    Text("Pay from the Pay tab using this invoice id.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            if let status {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Cart")
        .task { await refresh() }
        .refreshable { await refresh() }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            cart = try await session.api.loadOpenCart()
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func createCart() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await session.api.createCart(
                warehouseId: demoWarehouseId,
                currency: .USD,
                fulfillmentMode: .immediate,
                exchangeRate: 1
            )
            cart = try await session.api.loadOpenCart()
            status = "Created via create_customer_cart"
        } catch {
            status = error.localizedDescription
        }
    }

    private func addLine() async {
        guard let cart else { return }
        busy = true
        defer { busy = false }
        do {
            _ = try await session.api.addCartLine(
                cartId: cart.id,
                stockItemId: UUID(),
                uomId: UUID(),
                qty: 1
            )
            self.cart = try await session.api.loadOpenCart()
            status = "Added via add_customer_cart_line"
        } catch {
            status = error.localizedDescription
        }
    }

    private func checkout() async {
        guard let cart else { return }
        busy = true
        defer { busy = false }
        do {
            let invoiceId = try await session.api.checkoutCart(cartId: cart.id)
            lastInvoiceId = invoiceId
            self.cart = try await session.api.loadOpenCart()
            status = "Checked out via checkout_customer_cart → \(invoiceId.uuidString)"
        } catch {
            status = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        CartScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
