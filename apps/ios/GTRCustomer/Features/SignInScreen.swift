import AuthenticationServices
import SwiftUI

private enum AuthMode: String, CaseIterable, Identifiable {
    case signIn
    case signUp
    case reset
    var id: String { rawValue }
    var title: String {
        switch self {
        case .signIn: return "Sign in"
        case .signUp: return "Create account"
        case .reset: return "Reset password"
        }
    }
}

/// Supabase Auth-native customer auth. Password login, signup OTP and recovery
/// enter through the hardened Auth Edge; Apple/Google stay native Supabase Auth.
struct SignInScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @Environment(\.colorScheme) private var colorScheme
    @State private var mode: AuthMode = .signIn
    @State private var email = ""
    @State private var password = ""
    @State private var code = ""
    @State private var verificationPending = false
    @State private var busy = false
    @State private var errorText: String?
    @State private var infoText: String?
    @State private var appleRawNonce: String = ""
    var allowsSkip: Bool = false
    var onSkip: (() -> Void)?

    var body: some View {
        ShopDefaultScreen(title: "Nissan GTR Auto", subtitle: nil, scrollable: false) {
            Form {
                Section {
                    Picker("Authentication", selection: $mode) {
                        Text("Sign in").tag(AuthMode.signIn)
                        Text("Create account").tag(AuthMode.signUp)
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: mode) { _, _ in resetTransientState() }

                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .disabled(busy || (mode == .signUp && verificationPending))

                    if (mode == .signUp && verificationPending || mode == .reset {
                        TextField(mode == .reset ? "Recovery code" : "Verification code", text: $code)
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode)
                            .onChange(of: code) { _, value in
                                code = String(value.filter(\.isNumber).prefix(10))
                            }
                    }

                    SecureField(mode == .reset ? "New password" : "Password", text: $password)
                        .textContentType(mode == .signIn ? .password : .newPassword)
                } footer: {
                    Text(footerText)
                }

                Section {
                    Button {
                        Task { await primaryAction() }
                    } label: {
                        if busy {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text(primaryLabel)
                                .font(GTRType.label(.body))
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(!canSubmit)
                    .listRowBackground(GTRColors.primary)
                    .foregroundStyle(GTRColors.primaryInk)

                    if mode == .signUp && verificationPending {
                        Button("Resend verification code") {
                            Task { await requestSignupCode() }
                        }
                        .disabled(busy)
                    }

                    if mode == .signIn {
                        Button("Forgot password?") {
                            guard !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                                errorText = "Enter your email first."
                                return
                            }
                            mode = .reset
                            Task { await requestResetCode() }
                        }
                        .disabled(busy)
                    } else if mode == .reset {
                        Button("Request another recovery code") {
                            Task { await requestResetCode() }
                        }
                        .disabled(busy)
                        Button("Back to sign in") {
                            mode = .signIn
                            resetTransientState()
                        }
                        .disabled(busy)
                    }

                    if mode != .reset && !session.usesFake {
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
                        Button("Continue without signing in", action: onSkip).disabled(busy)
                    }
                }

                if let infoText {
                    Section { Text(infoText).font(.footnote) }
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

    private var footerText: String {
        switch mode {
        case .signIn:
            return "Password authentication is performed by Supabase Auth through the hardened Nissan GTR Auto Auth Edge."
        case .signUp:
            return verificationPending
                ? "Enter the Supabase Auth code from your email, then create your account."
                : "Supabase Auth sends and verifies the registration code before your password is set."
        case .reset:
            return "Recovery is non-enumerating. Supabase Auth verifies the recovery code and authorizes the password change."
        }
    }

    private var primaryLabel: String {
        switch mode {
        case .signIn: return "Sign in"
        case .signUp: return verificationPending ? "Verify and create account" : "Send signup code"
        case .reset: return "Update password"
        }
    }

    private var canSubmit: Bool {
        guard !busy, !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        switch mode {
        case .signIn: return !password.isEmpty
        case .signUp:
            return verificationPending ? code.count >= 6 && password.count >= 8 : password.count >= 8
        case .reset: return code.count >= 6 && password.count >= 8
        }
    }

    private func resetTransientState() {
        code = ""
        verificationPending = false
        errorText = nil
        infoText = nil
    }

    private func primaryAction() async {
        switch mode {
        case .signIn: await signIn()
        case .signUp:
            if verificationPending { await verifyAndCreateAccount() }
            else { await requestSignupCode() }
        case .reset: await completeReset()
        }
    }

    private func requestSignupCode() async {
        busy = true
        errorText = nil
        infoText = nil
        defer { busy = false }
        do {
            try await session.requestSignupEmailCode(email: email)
            verificationPending = true
            code = ""
            infoText = "Supabase Auth sent a verification code to your email."
        } catch { errorText = error.localizedDescription }
    }

    private func verifyAndCreateAccount() async {
        busy = true
        errorText = nil
        infoText = nil
        defer { busy = false }
        do {
            try await session.completeSignup(email: email, code: code, password: password)
            password = ""
            code = ""
            verificationPending = false
            infoText = "Account created."
        } catch { errorText = error.localizedDescription }
    }

    private func requestResetCode() async {
        busy = true
        errorText = nil
        infoText = nil
        defer { busy = false }
        do {
            try await session.requestPasswordReset(email: email)
            verificationPending = true
            code = ""
            infoText = "If an account matches that email, Supabase Auth sent a recovery code."
        } catch { errorText = error.localizedDescription }
    }

    private func completeReset() async {
        busy = true
        errorText = nil
        infoText = nil
        defer { busy = false }
        do {
            try await session.completePasswordReset(email: email, code: code, newPassword: password)
            password = ""
            code = ""
            verificationPending = false
            infoText = "Password updated."
        } catch { errorText = error.localizedDescription }
    }

    private func signIn() async {
        busy = true
        errorText = nil
        infoText = nil
        defer { busy = false }
        do { try await session.signIn(email: email, password: password) }
        catch { errorText = error.localizedDescription }
    }

    private func signInWithGoogle() async {
        busy = true
        errorText = nil
        defer { busy = false }
        do { try await session.signInWithGoogle() }
        catch { errorText = error.localizedDescription }
    }

    private func handleApple(_ result: Result<ASAuthorization, Error>) async {
        busy = true
        errorText = nil
        defer { busy = false }
        do {
            switch result {
            case .failure(let error):
                let ns = error as NSError
                errorText = ns.domain == ASAuthorizationError.errorDomain && ns.code == ASAuthorizationError.canceled.rawValue
                    ? "Sign in with Apple cancelled"
                    : error.localizedDescription
            case .success(let authorization):
                guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
                    errorText = "Apple Sign-In returned an unexpected credential."
                    return
                }
                guard let tokenData = credential.identityToken,
                      let idToken = String(data: tokenData, encoding: .utf8),
                      !idToken.isEmpty else {
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
        } catch { errorText = error.localizedDescription }
    }
}

#Preview {
    SignInScreen()
        .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
