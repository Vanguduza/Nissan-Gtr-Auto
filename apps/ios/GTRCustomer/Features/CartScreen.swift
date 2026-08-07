import SwiftUI

/// Cart / checkout — KMP pattern: back · lines · delivery · payment · secure pay · orders.
struct CartScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    var onPay: ((UUID) -> Void)? = nil
    var onManageOrders: (() -> Void)? = nil
    @State private var cart: CartSummary?
    @State private var addresses: [CustomerAddress] = []
    @State private var selectedAddressId: UUID?
    @State private var status: String?
    @State private var busy = false
    @State private var lastInvoiceId: UUID?
    @State private var fulfillmentMode: FulfillmentMode = .immediate
    @State private var settleCurrency: StorefrontCurrency = .USD
    @State private var zigRate: Decimal = 1
    @State private var payRail: PaymentRail = .contipay

    var body: some View {
        ZStack(alignment: .bottom) {
            ShopTabBody {
                if let cart, !cart.lines.isEmpty {
                    ForEach(cart.lines) { line in
                        ShopCartLineRow(
                            oem: line.oemPartNumber,
                            title: line.description ?? "Part",
                            priceLabel: StorefrontFormat.money(line.unitPrice, currency: line.currency),
                            qty: "\(line.qty)",
                            onAddQty: { Task { await bumpQty(line) } }
                        )
                        Divider().overlay(GTRColors.mist)
                    }
                } else {
                    ShopHonestEmpty(
                        title: "Basket is empty",
                        bodyText: "No items."
                    )
                }

                deliveryAndPaySection

                if let onManageOrders {
                    Button("Order management · track & history", action: onManageOrders)
                        .font(GTRType.label(.body))
                        .padding(.top, 8)
                }

                if let status {
                    Text(status)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }
            .padding(.bottom, cartHasLines ? 120 : 0)

            if cartHasLines || lastInvoiceId != nil {
                ShopProceedButtonBox(
                    totalLabel: cartTotalLabel,
                    ctaTitle: lastInvoiceId != nil ? "Continue to secure payment" : "Place order & pay",
                    enabled: !busy && !dispatchNeedsAddress,
                    onCta: {
                        if let lastInvoiceId {
                            onPay?(lastInvoiceId)
                        } else {
                            Task { await checkout() }
                        }
                    }
                )
            }
        }
        .background(GTRColors.chalk.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .navigationTitle("Cart")
        .task { await refresh() }
        .refreshable { await refresh() }
    }

    private var cartHasLines: Bool {
        guard let cart else { return false }
        return !cart.lines.isEmpty
    }

    private var cartTotalLabel: String {
        guard let cart else { return "—" }
        let subtotal = cart.lines.reduce(Decimal.zero) { $0 + ($1.unitPrice * $1.qty) }
        return StorefrontFormat.money(subtotal, currency: cart.currency)
    }

    @ViewBuilder
    private var deliveryAndPaySection: some View {
        ShopMerchTitleRow(title: "Delivery", actionLabel: nil)
        Picker("Fulfillment", selection: $fulfillmentMode) {
            Text("Click & collect").tag(FulfillmentMode.immediate)
            Text("Nationwide delivery").tag(FulfillmentMode.dispatch)
        }
        .pickerStyle(.segmented)

        if fulfillmentMode == .dispatch {
            ShopMerchTitleRow(title: "Shipping address", actionLabel: nil)
            if addresses.isEmpty {
                ShopHonestEmpty(title: "Add an address", bodyText: "No shipping address.")
                NavigationLink("Manage addresses") { AddressScreen() }
            } else {
                ForEach(addresses) { addr in
                    ShopAddressPickerRow(
                        address: addr,
                        selected: selectedAddressId == addr.id,
                        onSelect: { selectedAddressId = addr.id }
                    )
                }
            }
        }

        ShopMerchTitleRow(title: "Settle currency", actionLabel: nil)
        Picker("Currency", selection: $settleCurrency) {
            Text("USD").tag(StorefrontCurrency.USD)
            Text("ZiG").tag(StorefrontCurrency.ZIG)
        }
        .pickerStyle(.segmented)

        ShopMerchTitleRow(title: "Payment method", actionLabel: nil)
        Picker("PSP", selection: $payRail) {
            ForEach(PaymentRail.allCases) { r in
                Text(r.title).tag(r)
            }
        }
        .pickerStyle(.segmented)
        Text("You will be redirected to a secure ContiPay / Paynow / EcoCash page after placing the order.")
            .font(GTRType.body(.caption))
            .foregroundStyle(GTRColors.silverDim)
    }

    private var dispatchNeedsAddress: Bool {
        fulfillmentMode == .dispatch && selectedAddressId == nil
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            zigRate = try await session.api.fetchZigExchangeRate(asOf: nil)
            cart = try await session.api.loadOpenCart()
            addresses = (try? await session.api.listOwnAddresses()) ?? []
            if selectedAddressId == nil {
                selectedAddressId = addresses.first(where: \.isDefault)?.id ?? addresses.first?.id
            }
            if let open = cart {
                fulfillmentMode = open.fulfillmentMode
                settleCurrency = open.currency
            }
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func bumpQty(_ line: CartLineSummary) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await session.api.addCartLineByOem(oem: line.oemPartNumber, qty: 1)
            cart = try await session.api.loadOpenCart()
            status = "Qty updated for \(line.oemPartNumber)"
        } catch {
            status = error.localizedDescription
        }
    }

    private func checkout() async {
        busy = true
        defer { busy = false }
        do {
            if fulfillmentMode == .dispatch, selectedAddressId == nil {
                status = "Select a delivery address for Nationwide dispatch"
                return
            }
            let rate: Decimal = settleCurrency == .ZIG
                ? (try await session.api.fetchZigExchangeRate(asOf: nil))
                : 1
            let open = try await session.api.ensureOpenCart(
                currency: settleCurrency,
                fulfillmentMode: fulfillmentMode,
                exchangeRate: rate
            )
            let invoiceId = try await session.api.checkoutCart(cartId: open.id)
            lastInvoiceId = invoiceId
            cart = nil
            status = "Order placed · opening secure payment…"
            onPay?(invoiceId)
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
