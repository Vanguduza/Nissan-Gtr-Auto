import SwiftUI

/// Pay initiate — ContiPay / Paynow / EcoCash; D-57 USD browse + ZiG at EcoCash with fxRateId.
struct PayScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    var initialInvoiceId: UUID? = nil
    @State private var orders: [CustomerOrder] = []
    @State private var selectedInvoiceId: UUID?
    @State private var rail: PaymentRail = .contipay
    @State private var ecocashMsisdn: String = ""
    @State private var useSavedEcocash = true
    @State private var zigRate: Decimal?
    @State private var fxRateId: String?
    @State private var result: PaymentIntentResult?
    @State private var status: String?
    @State private var busy = false

    var body: some View {
        ShopDefaultScreen(
            title: "Secure payment",
            subtitle: nil,
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

            Section("Amount due") {
                amountDueRows
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
                    .disabled(busy || selectedInvoiceId == nil || ecoCashBlocked)
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
            .task {
                if let initialInvoiceId {
                    selectedInvoiceId = initialInvoiceId
                }
                await refresh()
            }
            .refreshable { await refresh() }
        }
    }

    private var ecoCashBlocked: Bool {
        rail == .ecocash && checkoutDisplay() == nil
    }

    @ViewBuilder
    private var amountDueRows: some View {
        if let display = checkoutDisplay() {
            if display.payCurrency == .ZIG {
                let zig = MoneyDualRead.fromAmountMinor(display.payable.amountMinor, currency: .ZIG)
                Text(StorefrontFormat.money(zig, currency: .ZIG))
                    .font(GTRType.label(.body))
                if let usdMinor = selectedOpenUsdMinor() {
                    let usd = MoneyDualRead.fromAmountMinor(usdMinor, currency: .USD)
                    Text("Browse \(StorefrontFormat.money(usd, currency: .USD))")
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
                if let rate = zigRate {
                    Text("@ \(rate) ZiG per USD · fx \(display.fxRateId ?? "—")")
                        .font(GTRType.body(.caption2))
                        .foregroundStyle(GTRColors.silverDim)
                }
            } else {
                let usd = MoneyDualRead.fromAmountMinor(display.payable.amountMinor, currency: .USD)
                Text(StorefrontFormat.money(usd, currency: .USD))
                    .font(GTRType.label(.body))
                if let zm = display.indicativeZigMinor {
                    let zig = MoneyDualRead.fromAmountMinor(zm, currency: .ZIG)
                    Text("≈ \(StorefrontFormat.money(zig, currency: .ZIG)) indicative")
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }
        } else if rail == .ecocash {
            Text("Daily ZiG rate required for EcoCash. Try ContiPay/Paynow (USD) or refresh.")
                .font(GTRType.body(.footnote))
                .foregroundStyle(.red)
        } else {
            Text("Select an invoice to see the amount due.")
                .font(GTRType.body(.footnote))
                .foregroundStyle(GTRColors.silverDim)
        }
    }

    private func orderLabel(_ order: CustomerOrder) -> String {
        let doc = order.documentNumber ?? String(order.invoiceId.uuidString.prefix(8))
        // D-57 browse: open amount as USD dual-read
        return "\(doc) · \(StorefrontFormat.money(order.displayAmountOpen(), currency: .USD)) open"
    }

    private func selectedOrder() -> CustomerOrder? {
        guard let selectedInvoiceId else { return nil }
        return orders.first(where: { $0.invoiceId == selectedInvoiceId })
    }

    private func selectedOpenUsdMinor() -> Int64? {
        guard let order = selectedOrder() else { return nil }
        return try? MoneyDualRead.toAmountMinor(order.displayAmountOpen(), currency: .USD)
    }

    private func checkoutDisplay() -> CheckoutDisplay? {
        guard let usdMinor = selectedOpenUsdMinor() else { return nil }
        let method: CheckoutPayMethod
        switch rail {
        case .contipay: method = .contipay
        case .paynow: method = .paynow
        case .ecocash: method = .ecocash
        }
        return try? CheckoutDisplayBuilder.build(
            usdMinor: usdMinor,
            payMethod: method,
            zigRatePerUsd: zigRate,
            fxRateId: method == .ecocash ? fxRateId : nil
        )
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            let rate = try await session.api.fetchZigExchangeRate(asOf: nil)
            zigRate = rate > 0 ? rate : nil
            fxRateId = try await session.api.fetchZigExchangeRateId(asOf: nil)
            orders = try await session.api.listOrders()
            if selectedInvoiceId == nil {
                selectedInvoiceId = orders.first(where: { $0.displayAmountOpen() > 0 })?.invoiceId
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
                guard checkoutDisplay() != nil else {
                    status = "Daily ZiG rate required for EcoCash"
                    return
                }
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

typealias PayIntentScreen = PayScreen
