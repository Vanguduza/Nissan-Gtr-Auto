import SwiftUI

/// Pay initiate — ContiPay / Paynow create intent; shows intent id + stub redirect (no PSP crypto).
struct PayScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var orders: [CustomerOrder] = []
    @State private var selectedInvoiceId: UUID?
    @State private var rail: PaymentRail = .contipay
    @State private var ecocashMsisdn: String = ""
    @State private var useSavedEcocash = true
    @State private var result: PaymentIntentResult?
    @State private var status: String?
    @State private var busy = false

    var body: some View {
        ShopDefaultScreen(
            title: "Pay",
            subtitle: "ContiPay · Paynow · EcoCash",
            scrollable: false
        ) {
            List {
            Section("Invoice") {
                if orders.isEmpty {
                    Text("Checkout a cart first, or use the seeded demo order (Fake).")
                        .font(GTRType.body(.subheadline))
                        .foregroundStyle(GTRColors.silverDim)
                } else {
                    Picker("Order", selection: $selectedInvoiceId) {
                        Text("Select…").tag(UUID?.none)
                        ForEach(orders) { order in
                            Text(orderLabel(order)).tag(Optional(order.invoiceId))
                        }
                    }
                }
            }

            Section("Rail") {
                Picker("PSP", selection: $rail) {
                    ForEach(PaymentRail.allCases) { r in
                        Text(r.title).tag(r)
                    }
                }
                .pickerStyle(.segmented)
            }

            if rail == .ecocash {
                Section("EcoCash number") {
                    Toggle("This is my saved / profile EcoCash number", isOn: $useSavedEcocash)
                    TextField("07… or +263…", text: $ecocashMsisdn)
                        .keyboardType(.phonePad)
                        .textInputAutocapitalization(.never)
                    Text("PIN is approved on that EcoCash handset — may differ from this iPhone.")
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }

            Section {
                Button("Create payment intent") { Task { await pay() } }
                    .disabled(busy || selectedInvoiceId == nil)
            }

            if let result {
                Section("Intent") {
                    LabeledContent("Rail", value: result.rail.title)
                    Text(result.intentId.uuidString)
                        .font(GTRType.label(.caption))
                        .monospaced()
                        .textSelection(.enabled)
                    if let url = result.checkoutURL {
                        Text(url.absoluteString)
                            .font(GTRType.label(.caption2))
                            .monospaced()
                            .foregroundStyle(GTRColors.silverDim)
                            .textSelection(.enabled)
                    }
                    Text(result.stubMessage)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }

            Section("Notes") {
                Text("Settlement is webhook / service_role only. Clients never hold ContiPay or Paynow secrets.")
                    .font(GTRType.body(.footnote))
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

    private func orderLabel(_ order: CustomerOrder) -> String {
        let doc = order.documentNumber ?? String(order.invoiceId.uuidString.prefix(8))
        return "\(doc) · \(StorefrontFormat.money(order.amountOpen, currency: order.currency)) open"
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            orders = try await session.api.listOrders()
            if selectedInvoiceId == nil {
                selectedInvoiceId = orders.first(where: { $0.amountOpen > 0 })?.invoiceId
                    ?? orders.first?.invoiceId
            }
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func pay() async {
        guard let invoiceId = selectedInvoiceId else { return }
        busy = true
        defer { busy = false }
        do {
            switch rail {
            case .contipay:
                result = try await session.api.createContipayIntent(
                    invoiceId: invoiceId,
                    method: .ecocash
                )
                status = "create_customer_contipay_intent / contipay-initiate"
            case .paynow:
                result = try await session.api.createPaynowIntent(
                    invoiceId: invoiceId,
                    method: .ecocash
                )
                status = "create_customer_paynow_intent / paynow-initiate"
            case .ecocash:
                let msisdn = ecocashMsisdn.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !msisdn.isEmpty else {
                    status = "Enter EcoCash number (saved/profile or different wallet)"
                    return
                }
                result = try await session.api.createEcocashIntent(
                    invoiceId: invoiceId,
                    payerMsisdn: msisdn,
                    payerMode: useSavedEcocash ? "saved" : "other"
                )
                status = "create_customer_ecocash_intent / ecocash-initiate"
            }
        } catch {
            result = nil
            status = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        PayScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
