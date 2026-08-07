import AuthenticationServices
import SwiftUI

/// Email/password GoTrue sign-in + Sign in with Apple (+ Google OAuth PKCE).
/// Live mode gates tabs behind a session. Fake can skip when `allowsSkip`.
struct SignInScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @Environment(\.colorScheme) private var colorScheme
    @State private var email = ""
    @State private var password = ""
    @State private var busy = false
    @State private var errorText: String?
    /// Raw nonce for the in-flight Apple request (hashed value goes on the AS request).
    @State private var appleRawNonce: String = ""
    /// When true, Fake mode can dismiss without signing in.
    var allowsSkip: Bool = false
    var onSkip: (() -> Void)?

    var body: some View {
        ShopDefaultScreen(
            title: "Nissan GTR Auto",
            subtitle: nil,
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
                    Text("Email uses GoTrue password grant. Apple uses native ID token. Google uses Supabase OAuth (`gtrcustomer://auth/callback`). Customer AuthZ needs a `customers` row (`profile_id = auth.uid()`).")
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

                    if !session.usesFake {
                        SignInWithAppleButton(.signIn) { request in
                            let raw = AuthNonce.randomRaw()
                            appleRawNonce = raw
                            request.requestedScopes = [.fullName, .email]
                            request.nonce = AuthNonce.sha256Hex(raw)
                        } onCompletion: { result in
                            Task { await handleApple(result) }
                        }
                        .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                        .frame(height: 44)
                        .disabled(busy)
                        .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                        .listRowBackground(Color.clear)

                        Button {
                            Task { await signInWithGoogle() }
                        } label: {
                            Text(busy ? "Signing in with Google…" : "Continue with Google")
                                .font(GTRType.label(.body))
                                .frame(maxWidth: .infinity)
                        }
                        .disabled(busy)
                    }

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

    private func signInWithGoogle() async {
        busy = true
        errorText = nil
        defer { busy = false }
        do {
            try await session.signInWithGoogle()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func handleApple(_ result: Result<ASAuthorization, Error>) async {
        busy = true
        errorText = nil
        defer { busy = false }
        do {
            switch result {
            case .failure(let error):
                let ns = error as NSError
                if ns.domain == ASAuthorizationError.errorDomain,
                   ns.code == ASAuthorizationError.canceled.rawValue
                {
                    errorText = "Sign in with Apple cancelled"
                } else {
                    errorText = error.localizedDescription
                }
            case .success(let authorization):
                guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                    errorText = "Apple Sign-In returned an unexpected credential."
                    return
                }
                guard let tokenData = credential.identityToken,
                      let idToken = String(data: tokenData, encoding: .utf8),
                      !idToken.isEmpty
                else {
                    errorText = "Apple Sign-In returned no identity token."
                    return
                }
                let rawNonce = appleRawNonce
                guard !rawNonce.isEmpty else {
                    errorText = "Apple Sign-In nonce missing — try again."
                    return
                }
                try await session.signInWithApple(
                    idToken: idToken,
                    rawNonce: rawNonce,
                    email: credential.email
                )
            }
        } catch {
            errorText = error.localizedDescription
        }
    }
}

#Preview {
    SignInScreen()
        .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
