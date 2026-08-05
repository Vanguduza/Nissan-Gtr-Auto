import SwiftUI

/// Orders list + detail — detail uses `get_customer_order`.
/// Active dispatch offers last-point track via `get_delivery_track_point`.
struct OrdersScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var orders: [CustomerOrder] = []
    @State private var status: String?
    @State private var busy = false

    var body: some View {
        ShopDefaultScreen(title: "Orders", subtitle: "History · track", scrollable: false) {
            List {
            if orders.isEmpty {
                Text("No orders yet")
                    .font(GTRType.body(.subheadline))
                    .foregroundStyle(GTRColors.silverDim)
            }
            ForEach(orders) { order in
                NavigationLink {
                    OrderDetailScreen(invoiceId: order.invoiceId)
                } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(order.documentNumber ?? String(order.invoiceId.uuidString.prefix(8)) + "…")
                            .font(GTRType.displaySemi(.headline))
                        Text("\(order.status) · \(StorefrontFormat.money(order.total, currency: order.currency))")
                            .font(GTRType.body(.subheadline))
                            .foregroundStyle(GTRColors.silverDim)
                        HStack(spacing: 6) {
                            Text(StorefrontFormat.fulfillment(order.fulfillmentMode))
                                .font(GTRType.label(.caption))
                            if order.activeDeliveryJobId != nil {
                                Text("Live track")
                                    .font(GTRType.label(.caption2))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(GTRColors.primary.opacity(0.15), in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
                            }
                        }
                    }
                }
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
    @State private var trackToken = ""
    @State private var tokenNavActive = false

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

                if order.offersLiveDeliveryTrack {
                    Section {
                        GTRSectionLabel(text: "Live delivery")
                        Text(
                            "Last point + ETA while out for delivery. No historical GPS trail."
                        )
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)

                        if let jobId = order.activeDeliveryJobId {
                            NavigationLink {
                                DeliveryTrackScreen(ref: .job(jobId))
                            } label: {
                                Label("Track active delivery", systemImage: "location.fill")
                            }
                        }

                        TextField("Share track token", text: $trackToken)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(GTRType.body())
                            .monospaced()

                        Button("Track with token") {
                            let trimmed = trackToken.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard trimmed.count >= 8 else {
                                status = "Enter a valid track token (from your out-for-delivery message)."
                                return
                            }
                            status = nil
                            tokenNavActive = true
                        }
                    }
                }

                Section {
                    GTRSectionLabel(text: "Invoice id")
                    Text(order.invoiceId.uuidString)
                        .font(GTRType.label(.caption))
                        .monospaced()
                        .textSelection(.enabled)
                }
            } else if let status {
                Text(status)
                    .font(GTRType.body(.subheadline))
                    .foregroundStyle(GTRColors.silverDim)
            } else {
                ProgressView()
            }

            if let status, order != nil {
                Section {
                    Text(status)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(GTRColors.chalk)
        .navigationTitle("Order")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: $tokenNavActive) {
            DeliveryTrackScreen(
                ref: .token(trackToken.trimmingCharacters(in: .whitespacesAndNewlines))
            )
        }
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
