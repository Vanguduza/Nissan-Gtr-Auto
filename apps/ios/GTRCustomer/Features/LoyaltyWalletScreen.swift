import SwiftUI

struct LoyaltyWalletScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var balance: LoyaltyBalance?
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ShopDefaultScreen(title: "Loyalty wallet", subtitle: "Points balance", scrollable: true) {
            Text("Live balance via get_loyalty_balance.")
                .font(GTRType.body(.caption))
                .foregroundStyle(GTRColors.silverDim)

            if let balance {
                ShopMerchTitleRow(title: "Balance", actionLabel: nil)
                Text(String(format: "%.0f points", balance.pointsBalance))
                    .font(GTRType.display(.title))
                    .foregroundStyle(GTRColors.steel)
                Text("Currency: \(balance.currency)")
                    .font(GTRType.body(.body))
                Text(String(format: "Estimated liability: %@ %.2f", balance.currency, balance.estimatedLiability))
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.silverDim)
                Text(String(format: "Liability per point: %.4f", balance.liabilityPerPoint))
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.silverDim)
            } else if !busy && error == nil {
                ShopHonestEmpty(
                    title: "No loyalty account",
                    bodyText: "Points appear here when a loyalty account is linked to your customer profile."
                )
            }

            Button("Refresh") { Task { await refresh() } }
                .buttonStyle(.borderedProminent)
                .tint(GTRColors.primary)
                .disabled(busy)
                .frame(maxWidth: .infinity)

            if let error {
                Text(error)
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.primary)
            }
        }
        .task { await refresh() }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            guard let customer = try await session.api.loadOwnCustomer() else {
                balance = nil
                error = "No customer profile linked."
                return
            }
            balance = try await session.api.getLoyaltyBalance(customerId: customer.id)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}
