import SwiftUI

/// Faulty returns — quarantine CN via post_customer_return_credit_note.
struct ReturnsCreditScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var invoices: [CustomerOrder] = []
    @State private var selectedInvoiceId: UUID?
    @State private var lines: [InvoiceLineSummary] = []
    @State private var selectedLineIds: Set<UUID> = []
    @State private var busy = false
    @State private var message: String?
    @State private var error: String?

    var body: some View {
        ShopDefaultScreen(title: "Returns", subtitle: nil, scrollable: true) {
            ShopMerchTitleRow(title: "Select invoice", actionLabel: nil)
            if invoices.isEmpty && !busy {
                ShopHonestEmpty(
                    title: "No invoices yet",
                    bodyText: "No invoices."
                )
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(invoices) { inv in
                        Button {
                            Task { await selectInvoice(inv.invoiceId) }
                        } label: {
                            Text("\(inv.documentNumber ?? inv.invoiceId.uuidString.prefix(8).description) · \(inv.currency.rawValue)")
                                .font(GTRType.label(.caption))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    selectedInvoiceId == inv.invoiceId
                                        ? GTRColors.primary.opacity(0.15)
                                        : GTRColors.mist,
                                    in: Capsule()
                                )
                        }
                        .buttonStyle(.plain)
                        .disabled(busy)
                    }
                }
            }

            if !lines.isEmpty {
                ShopMerchTitleRow(title: "Lines to return", actionLabel: nil)
                ForEach(lines) { line in
                    let selected = selectedLineIds.contains(line.id)
                    Button {
                        if selected { selectedLineIds.remove(line.id) }
                        else { selectedLineIds.insert(line.id) }
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(line.oemPartNumber ?? line.stockItemId.uuidString.prefix(8).description)
                                .font(GTRType.body(.body))
                                .foregroundStyle(GTRColors.steel)
                            Text("\(line.description ?? "Qty \(line.qty)")\(selected ? " · Selected" : "")")
                                .font(GTRType.body(.caption))
                                .foregroundStyle(GTRColors.silverDim)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
                        .overlay(
                            RoundedRectangle(cornerRadius: GTRRadius.sharp)
                                .stroke(selected ? GTRColors.primary : GTRColors.mist, lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
                Button("Request return credit note") { Task { await submitReturn() } }
                    .buttonStyle(.borderedProminent)
                    .tint(GTRColors.primary)
                    .disabled(busy || selectedLineIds.isEmpty)
                    .frame(maxWidth: .infinity)
            }

            if let message {
                Text(message)
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.primary)
            }
            if let error {
                Text(error)
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.primary)
            }
        }
        .task { await loadInvoices() }
    }

    private func loadInvoices() async {
        busy = true
        defer { busy = false }
        do {
            invoices = try await session.api.listOrders()
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func selectInvoice(_ id: UUID) async {
        selectedInvoiceId = id
        selectedLineIds = []
        busy = true
        defer { busy = false }
        do {
            lines = try await session.api.listInvoiceLines(invoiceId: id)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func submitReturn() async {
        guard let invoiceId = selectedInvoiceId else { return }
        let payload = lines.filter { selectedLineIds.contains($0.id) }.map {
            ReturnCreditNoteLine(stockItemId: $0.stockItemId, uomId: $0.uomId, qty: $0.qty)
        }
        guard !payload.isEmpty else { return }
        busy = true
        defer { busy = false }
        do {
            let cnId = try await session.api.postCustomerReturnCreditNote(invoiceId: invoiceId, lines: payload)
            message = "Return credit note \(cnId.uuidString.prefix(8))… — stock routed to quarantine."
            selectedLineIds = []
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}
