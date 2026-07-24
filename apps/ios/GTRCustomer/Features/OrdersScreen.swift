import SwiftUI

/// Orders list + detail — detail uses `get_customer_order`.
struct OrdersScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var orders: [CustomerOrder] = []
    @State private var status: String?
    @State private var busy = false

    var body: some View {
        List {
            if orders.isEmpty {
                Text("No orders yet")
                    .foregroundStyle(.secondary)
            }
            ForEach(orders) { order in
                NavigationLink {
                    OrderDetailScreen(invoiceId: order.invoiceId)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(order.documentNumber ?? order.invoiceId.uuidString.prefix(8) + "…")
                            .font(.headline)
                        Text("\(order.status) · \(StorefrontFormat.money(order.total, currency: order.currency))")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text(StorefrontFormat.fulfillment(order.fulfillmentMode))
                            .font(.caption)
                    }
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
        .navigationTitle("Orders")
        .task { await refresh() }
        .refreshable { await refresh() }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            orders = try await session.api.listOrders()
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }
}

struct OrderDetailScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    let invoiceId: UUID
    @State private var order: CustomerOrder?
    @State private var status: String?

    var body: some View {
        List {
            if let order {
                LabeledContent("Document", value: order.documentNumber ?? "—")
                LabeledContent("Status", value: order.status)
                LabeledContent("Fulfillment", value: StorefrontFormat.fulfillment(order.fulfillmentMode))
                LabeledContent("Currency", value: order.currency.rawValue)
                LabeledContent("FX rate", value: "\(order.exchangeRateApplied)")
                LabeledContent("Subtotal", value: StorefrontFormat.money(order.subtotal, currency: order.currency))
                LabeledContent("Total", value: StorefrontFormat.money(order.total, currency: order.currency))
                LabeledContent("Paid", value: StorefrontFormat.money(order.amountPaid, currency: order.currency))
                LabeledContent("Open", value: StorefrontFormat.money(order.amountOpen, currency: order.currency))
                if let pick = order.pickListStatus {
                    LabeledContent("Pick list", value: pick)
                }
                if let dn = order.deliveryNoteStatus {
                    LabeledContent("Delivery note", value: dn)
                }
                Section("Invoice id") {
                    Text(order.invoiceId.uuidString)
                        .font(.caption.monospaced())
                        .textSelection(.enabled)
                }
            } else if let status {
                Text(status).foregroundStyle(.secondary)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Order")
        .task { await load() }
    }

    private func load() async {
        do {
            order = try await session.api.getOrder(invoiceId: invoiceId)
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        OrdersScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
