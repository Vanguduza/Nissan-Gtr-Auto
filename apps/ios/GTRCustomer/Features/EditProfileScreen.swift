import SwiftUI

/// Edit profile — mirrors web profile-form via PostgREST + storefront RPCs.
struct EditProfileScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var company = ""
    @State private var email = ""
    @State private var phone = ""
    @State private var preferred: PreferredReceiptChannel = .whatsapp
    @State private var marketingOptIn = false
    @State private var customerId: UUID?
    @State private var lastPromoAt: String?
    @State private var busy = false
    @State private var message: String?
    @State private var error: String?

    var body: some View {
        ShopDefaultScreen(
            title: "Edit profile",
            subtitle: nil,
            scrollable: true
        ) {
            if busy && firstName.isEmpty && email.isEmpty {
                ProgressView().padding()
            }

            ShopMerchTitleRow(title: "Personal information", actionLabel: nil)
            TextField("First name", text: $firstName)
                .textFieldStyle(.roundedBorder)
                .disabled(busy)
            TextField("Last name", text: $lastName)
                .textFieldStyle(.roundedBorder)
                .disabled(busy)
            TextField("Company / display name", text: $company)
                .textFieldStyle(.roundedBorder)
                .disabled(busy)

            ShopMerchTitleRow(title: "Contact details", actionLabel: nil)
            TextField("Email", text: $email)
                .textInputAutocapitalization(.never)
                .keyboardType(.emailAddress)
                .textFieldStyle(.roundedBorder)
                .disabled(busy)
            TextField("Mobile / WhatsApp", text: $phone)
                .keyboardType(.phonePad)
                .textFieldStyle(.roundedBorder)
                .disabled(busy)

            Text("Preferred receipt channel")
                .font(GTRType.label(.caption))
                .foregroundStyle(GTRColors.silverDim)
            HStack {
                ForEach(PreferredReceiptChannel.allCases, id: \.self) { channel in
                    Button(channel.label) { preferred = channel }
                        .buttonStyle(.bordered)
                        .tint(preferred == channel ? GTRColors.primary : GTRColors.steel)
                        .disabled(busy)
                }
            }

            if customerId != nil {
                ShopMerchTitleRow(title: "Marketing", actionLabel: nil)
                Toggle("Promotional messages", isOn: $marketingOptIn)
                    .disabled(busy)
                if let lastPromoAt {
                    Text("Last promo: \(lastPromoAt)")
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }

            Button(busy ? "Saving…" : "Save details") { Task { await save() } }
                .buttonStyle(.borderedProminent)
                .tint(GTRColors.primary)
                .disabled(busy)
                .frame(maxWidth: .infinity)

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
        .task { await refresh() }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            let profile = try await session.api.loadOwnProfile()
            let customer = try await session.api.loadOwnCustomer()
            let names = splitName(profile?.fullName)
            firstName = names.0
            lastName = names.1
            company = customer?.displayName ?? ""
            email = customer?.email ?? session.userEmail ?? ""
            phone = customer?.phoneE164 ?? customer?.whatsappE164 ?? ""
            preferred = preferredFromCustomer(customer)
            marketingOptIn = customer?.marketingOptIn ?? false
            customerId = customer?.id
            lastPromoAt = customer?.lastPromoAt
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func save() async {
        busy = true
        defer { busy = false }
        do {
            let fullName = [firstName, lastName]
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .joined(separator: " ")
            try await session.api.updateOwnFullName(fullName)
            var notes = ["Name saved to profiles."]
            if customerId != nil {
                try await session.api.updateOwnCustomerContact(
                    CustomerContactPatch(
                        displayName: company.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
                            ?? fullName.nilIfEmpty ?? "Customer",
                        email: email.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                        phoneE164: phone.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                        whatsappE164: phone.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                        smsReceipts: preferred == .sms,
                        emailReceipts: preferred == .email,
                        whatsappReceipts: preferred == .whatsapp
                    )
                )
                notes.append("Contact + receipt prefs updated.")
                try await session.api.setOwnMarketingOptIn(marketingOptIn)
                notes.append(marketingOptIn ? "Marketing opt-in enabled." : "Marketing opt-in disabled.")
            } else {
                notes.append("No linked customer row — contact fields display-only until linked.")
            }
            await refresh()
            message = notes.joined(separator: " ")
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func splitName(_ full: String?) -> (String, String) {
        let parts = full?.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: \.isWhitespace)
            .map(String.init) ?? []
        if parts.isEmpty { return ("", "") }
        if parts.count == 1 { return (parts[0], "") }
        return (parts[0], parts.dropFirst().joined(separator: " "))
    }

    private func preferredFromCustomer(_ c: CustomerProfile?) -> PreferredReceiptChannel {
        guard let c else { return .whatsapp }
        if c.whatsappReceipts { return .whatsapp }
        if c.smsReceipts { return .sms }
        if c.emailReceipts { return .email }
        return .whatsapp
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
