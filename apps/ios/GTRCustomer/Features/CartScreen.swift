import SwiftUI

/// Cart / checkout — KMP CartScreen density (line cards, sticky proceed) + GTR fulfillment.
struct CartScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var cart: CartSummary?
    @State private var addresses: [CustomerAddress] = []
    @State private var selectedAddressId: UUID?
    @State private var status: String?
    @State private var busy = false
    @State private var lastInvoiceId: UUID?
    @State private var fulfillmentMode: FulfillmentMode = .immediate
    @State private var settleCurrency: StorefrontCurrency = .USD
    @State private var zigRate: Decimal = 1
    @State private var showCheckoutOptions = false

    var body: some View {
        ZStack(alignment: .bottom) {
            ShopTabBody {
                if let cart, !cart.lines.isEmpty {
                    Text("\(cart.lines.count) line(s) · \(cart.currency.rawValue)")
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)

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
                        bodyText: "Browse Home and add parts. No fake coupons or countdown timers."
                    )
                }

                if cart == nil {
                    ShopPrimaryButton(title: "Start cart", enabled: !busy) {
                        Task { await ensureCart() }
                    }
                }

                if showCheckoutOptions {
                    checkoutOptionsSection
                }

                if let lastInvoiceId {
                    ShopMerchTitleRow(title: "Last checkout", actionLabel: nil)
                    Text("Invoice \(lastInvoiceId.uuidString)")
                        .font(GTRType.label(.caption))
                        .monospaced()
                    Text("Pay from Profile → Payment methods (ContiPay / Paynow / EcoCash).")
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }

                if let status {
                    Text(status)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }
            .padding(.bottom, cartHasLines ? 120 : 0)

            if cartHasLines {
                ShopProceedButtonBox(
                    totalLabel: cartTotalLabel,
                    ctaTitle: showCheckoutOptions ? "Confirm checkout" : "Proceed to checkout",
                    enabled: !busy && !dispatchNeedsAddress,
                    onCta: {
                        if showCheckoutOptions {
                            Task { await checkout() }
                        } else {
                            showCheckoutOptions = true
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
    private var checkoutOptionsSection: some View {
        ShopMerchTitleRow(title: "How do you want it?", actionLabel: nil)
        Picker("Fulfillment", selection: $fulfillmentMode) {
            Text("Click & collect").tag(FulfillmentMode.immediate)
            Text("Nationwide dispatch").tag(FulfillmentMode.dispatch)
        }
        .pickerStyle(.inline)
        .labelsHidden()
        Text(
            fulfillmentMode == .immediate
                ? "Pick up at Harare counter when ready"
                : "Courier to your selected address"
        )
        .font(GTRType.body(.caption))
        .foregroundStyle(GTRColors.silverDim)

        ShopMerchTitleRow(title: "Settle in", actionLabel: nil)
        Picker("Currency", selection: $settleCurrency) {
            Text("USD").tag(StorefrontCurrency.USD)
            Text("ZiG").tag(StorefrontCurrency.ZIG)
        }
        .pickerStyle(.segmented)
        if settleCurrency == .ZIG {
            Text("Official rate · \(zigRate) ZiG / USD")
                .font(GTRType.body(.caption))
                .foregroundStyle(GTRColors.silverDim)
        }

        if fulfillmentMode == .dispatch {
            ShopMerchTitleRow(title: "Delivery address", actionLabel: nil)
            if addresses.isEmpty {
                ShopHonestEmpty(
                    title: "No addresses",
                    bodyText: "Add one under Profile → Manage address (MapKit pick)."
                )
                NavigationLink("Manage addresses") {
                    AddressScreen()
                }
            } else {
                ForEach(addresses) { addr in
                    ShopAddressPickerRow(
                        address: addr,
                        selected: selectedAddressId == addr.id,
                        onSelect: { selectedAddressId = addr.id }
                    )
                }
                NavigationLink("Manage addresses") {
                    AddressScreen()
                }
            }
        }

        Button("Refresh cart") { Task { await refresh() } }
            .font(GTRType.label(.caption))
            .disabled(busy)
    }

    private var dispatchNeedsAddress: Bool {
        showCheckoutOptions && fulfillmentMode == .dispatch && selectedAddressId == nil
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

    private func ensureCart() async {
        busy = true
        defer { busy = false }
        do {
            let rate: Decimal = settleCurrency == .ZIG
                ? (try await session.api.fetchZigExchangeRate(asOf: nil))
                : 1
            zigRate = rate
            cart = try await session.api.ensureOpenCart(
                currency: settleCurrency,
                fulfillmentMode: fulfillmentMode,
                exchangeRate: rate
            )
            status = "Cart ready · \(StorefrontFormat.fulfillment(fulfillmentMode))"
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
        guard let cart else { return }
        if fulfillmentMode == .dispatch, selectedAddressId == nil {
            status = "Select a delivery address for Nationwide dispatch"
            return
        }
        busy = true
        defer { busy = false }
        do {
            let invoiceId = try await session.api.checkoutCart(cartId: cart.id)
            lastInvoiceId = invoiceId
            self.cart = nil
            showCheckoutOptions = false
            let addrNote: String
            if fulfillmentMode == .dispatch, let id = selectedAddressId {
                addrNote = " · address \(id.uuidString.prefix(8))…"
            } else {
                addrNote = ""
            }
            status = "Checked out → \(invoiceId.uuidString.prefix(8))…\(addrNote). Pay from Profile."
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
