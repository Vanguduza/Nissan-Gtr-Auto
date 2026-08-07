import SwiftUI

/// My Account hub — Reviews live on PDP; Settings is its own tab.
struct AccountHubScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    var onClose: (() -> Void)? = nil
    var onOpenGarage: (() -> Void)? = nil
    var onOpenProduct: ((String) -> Void)? = nil
    var onSignIn: (() -> Void)? = nil

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                HStack {
                    Text("My Account")
                        .font(GTRType.displaySemi(.title2))
                        .foregroundStyle(GTRColors.steel)
                    Spacer()
                    if let onClose {
                        Button("Close", action: onClose)
                            .font(GTRType.label(.subheadline))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)

                ShopProfileAvatar(email: session.userEmail, usesFake: session.usesFake, large: true)

                VStack(spacing: 0) {
                    profileLink("Edit profile", "person.crop.circle") { EditProfileScreen() }
                    profileLink("My orders", "list.bullet.rectangle") { OrdersScreen() }
                    profileLink("Returns", "arrow.uturn.backward") { ReturnsCreditScreen() }
                    profileLink("Loyalty wallet", "star.fill") { LoyaltyWalletScreen() }
                    if let onOpenProduct {
                        NavigationLink {
                            KitsScreen(onOpenProduct: onOpenProduct)
                        } label: {
                            ShopProfileItemBox(title: "Service kits", systemImage: "wrench.and.screwdriver", onTap: {})
                        }
                        .buttonStyle(.plain)
                    } else {
                        profileLink("Service kits", "wrench.and.screwdriver") {
                            KitsScreen(onOpenProduct: { _ in })
                        }
                    }
                    profileLink("Manage address", "mappin.and.ellipse") { AddressScreen() }
                    profileLink("Payment methods", "creditcard") { PayScreen() }
                    if let onOpenGarage {
                        Button {
                            onOpenGarage()
                        } label: {
                            ShopProfileItemBox(title: "My garage", systemImage: "car.fill", onTap: {})
                        }
                        .buttonStyle(.plain)
                    } else {
                        profileLink("My garage", "car.fill") { GarageScreen() }
                    }
                    profileLink("Compare", "rectangle.split.2x1") { CompareScreen() }
                    profileLink("Track delivery", "shippingbox") {
                        DeliveryTrackScreen(ref: .token("demo-track-token"))
                    }
                    profileLink("Live chat", "bubble.left.and.bubble.right") { ChatScreen() }
                    honestRow(
                        title: "Notifications",
                        body: "No push backend yet — honest empty."
                    )
                    honestRow(
                        title: "My coupons",
                        body: "No coupon SoR — not a fake timer.",
                        isLast: true
                    )
                }
                .clipShape(RoundedRectangle(cornerRadius: GTRRadius.control))
                .padding(.horizontal, 12)

                if session.userEmail == nil, let onSignIn {
                    Button("Sign in", action: onSignIn)
                        .font(GTRType.label(.body))
                        .frame(maxWidth: .infinity)
                        .padding(.top, 8)
                }
                Button("Sign out", role: .destructive) { session.signOut() }
                    .font(GTRType.label(.body))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)

                Text("Bridge-First camera · ContiPay / Paynow / EcoCash only · No ZIMRA")
                    .font(GTRType.label(.caption2))
                    .foregroundStyle(GTRColors.silverDim)
                    .padding(16)
            }
        }
        .background(GTRColors.chalk.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
    }

    private func profileLink<Dest: View>(
        _ title: String,
        _ systemImage: String,
        @ViewBuilder destination: () -> Dest
    ) -> some View {
        NavigationLink {
            destination()
        } label: {
            ShopProfileItemBox(title: title, systemImage: systemImage, onTap: {})
        }
        .buttonStyle(.plain)
    }

    private func honestRow(title: String, body: String, isLast: Bool = false) -> some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(GTRType.body(.body))
                    .foregroundStyle(GTRColors.steel)
                Text(body)
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.silverDim)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            if !isLast {
                Divider().overlay(GTRColors.mist)
            }
        }
    }
}
