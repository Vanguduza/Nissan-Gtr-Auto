import SwiftUI

/// Email/password GoTrue sign-in — Live mode gates tabs behind a session.
struct SignInScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var errorText: String?
    /// When true, Fake mode can dismiss without signing in.
    var allowsSkip: Bool = false
    var onSkip: (() -> Void)?

    var body: some View {
        ShopDefaultScreen(
            title: "Nissan GTR Auto",
            subtitle: session.usesFake ? "Sign in · Fake" : "Sign in · Live",
            scrollable: false
        ) {
            Form {
                Section {
                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                } footer: {
                    Text("Uses GoTrue password grant. Customer AuthZ needs a user with a `customers` row (`profile_id = auth.uid()`).")
                }

                Section {
                    Button {
                        Task { await signIn() }
                    } label: {
                        if busy {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Text("Sign in")
                                .font(GTRType.label(.body))
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(busy || email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.isEmpty)
                    .listRowBackground(GTRColors.primary)
                    .foregroundStyle(GTRColors.primaryInk)

                    if allowsSkip, let onSkip {
                        Button("Continue without signing in", action: onSkip)
                            .disabled(busy)
                    }
                }

                if let errorText {
                    Section {
                        Text(errorText)
                            .font(.footnote)
                            .foregroundStyle(GTRColors.primary)
                    }
                }
            }
            .scrollContentBackground(.hidden)
        }
    }

    private func signIn() async {
        busy = true
        errorText = nil
        defer { busy = false }
        do {
            try await session.signIn(email: email, password: password)
        } catch {
            errorText = error.localizedDescription
        }
    }
}

#Preview {
    SignInScreen()
        .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
