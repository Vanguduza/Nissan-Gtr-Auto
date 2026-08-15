import SwiftUI

/// Email/password + Google + Edge OTP signup + Edge password reset (no Apple).
struct SignInScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var mode: Mode = .signIn
    @State private var signUpStep: SignUpStep = .email
    @State private var forgotStep: ForgotStep = .email
    @State private var email = ""
    @State private var password = ""
    @State private var otpCode = ""
    @State private var proofToken: String?
    @State private var stubHint: String?
    @State private var busy = false
    @State private var errorText: String?
    @State private var infoText: String?
    var allowsSkip: Bool = false
    var onSkip: (() -> Void)?

    private enum Mode { case signIn, signUp, forgot }
    private enum SignUpStep { case email, otp, password }
    private enum ForgotStep { case email, code }

    var body: some View {
        ShopDefaultScreen(
            title: title,
            subtitle: nil,
            scrollable: true
        ) {
            Form {
                Section {
                    TextField("Email", text: $email)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .disabled(emailLocked)

                    if showPassword {
                        SecureField(
                            mode == .forgot ? "New password" : "Password",
                            text: $password
                        )
                        .textContentType(mode == .forgot ? .newPassword : .password)
                    }

                    if showOtp {
                        TextField("6-digit code", text: $otpCode)
                            .keyboardType(.numberPad)
                            .textContentType(.oneTimeCode)
                        if let stubHint {
                            Text("Stub code (local only): \(stubHint)")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                } footer: {
                    Text(footerCopy)
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
                    .disabled(!primaryEnabled || busy)
                    .listRowBackground(GTRColors.primary)
                    .foregroundStyle(GTRColors.primaryInk)

                    if mode == .signIn, !session.usesFake {
                        Button {
                            Task { await signInWithGoogle() }
                        } label: {
                            Text(busy ? "Signing in with Google…" : "Continue with Google")
                                .font(GTRType.label(.body))
                                .frame(maxWidth: .infinity)
                        }
                        .disabled(busy)

                        Button("Need an account? Sign up") {
                            resetWizard(to: .signUp)
                        }
                        .disabled(busy)

                        Button("Forgot password?") {
                            resetWizard(to: .forgot)
                        }
                        .disabled(busy)
                    } else if mode != .signIn {
                        Button("Back to sign in") {
                            resetWizard(to: .signIn)
                        }
                        .disabled(busy)
                    }

                    if allowsSkip, mode == .signIn, let onSkip {
                        Button("Continue without signing in", action: onSkip)
                            .disabled(busy)
                    }
                }

                if let infoText {
                    Section {
                        Text(infoText).font(.footnote)
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

    private var title: String {
        switch mode {
        case .signIn: return "Nissan GTR Auto"
        case .signUp: return "Create account"
        case .forgot: return "Reset password"
        }
    }

    private var footerCopy: String {
        switch mode {
        case .signIn:
            return "Email uses GoTrue password grant. Google uses Supabase OAuth. Sign up uses Edge OTP (hosted blocks public GoTrue signup)."
        case .signUp:
            return "We email a 6-digit code, then you set a password (min 8)."
        case .forgot:
            return "Reset uses the same Edge OTP path as the web /forgot-password page."
        }
    }

    private var emailLocked: Bool {
        switch mode {
        case .signIn: return busy
        case .signUp: return busy || signUpStep != .email
        case .forgot: return busy || forgotStep != .email
        }
    }

    private var showPassword: Bool {
        switch mode {
        case .signIn: return true
        case .signUp: return signUpStep == .password
        case .forgot: return forgotStep == .code
        }
    }

    private var showOtp: Bool {
        switch mode {
        case .signIn: return false
        case .signUp: return signUpStep == .otp
        case .forgot: return forgotStep == .code
        }
    }

    private var primaryLabel: String {
        switch mode {
        case .signIn: return "Sign in"
        case .signUp:
            switch signUpStep {
            case .email: return "Send verification code"
            case .otp: return "Verify code"
            case .password: return "Create account"
            }
        case .forgot:
            switch forgotStep {
            case .email: return "Send reset code"
            case .code: return "Set new password"
            }
        }
    }

    private var primaryEnabled: Bool {
        let e = !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        switch mode {
        case .signIn:
            return e && !password.isEmpty
        case .signUp:
            switch signUpStep {
            case .email: return e
            case .otp: return otpCode.filter(\.isNumber).count == 6
            case .password: return password.count >= 8
            }
        case .forgot:
            switch forgotStep {
            case .email: return e
            case .code: return otpCode.filter(\.isNumber).count == 6 && password.count >= 8
            }
        }
    }

    private func resetWizard(to newMode: Mode) {
        mode = newMode
        signUpStep = .email
        forgotStep = .email
        otpCode = ""
        password = newMode == .signIn ? password : ""
        proofToken = nil
        stubHint = nil
        errorText = nil
        infoText = nil
    }

    private func primaryAction() async {
        busy = true
        errorText = nil
        defer { busy = false }
        do {
            switch mode {
            case .signIn:
                try await session.signIn(email: email, password: password)
            case .signUp:
                switch signUpStep {
                case .email:
                    let res = try await session.requestSignupOtp(email: email)
                    stubHint = res.stubCode
                    signUpStep = .otp
                    infoText = res.stub
                        ? "Local stub OTP — enter the code shown below."
                        : "Code sent — check your email."
                case .otp:
                    let res = try await session.verifySignupOtp(email: email, code: otpCode)
                    proofToken = res.proofToken
                    password = ""
                    signUpStep = .password
                    infoText = "OTP verified — choose a password (min 8 characters)."
                case .password:
                    guard let proofToken else {
                        throw StorefrontError.message("Verify OTP before creating an account.")
                    }
                    try await session.completeSignup(
                        email: email,
                        password: password,
                        proofToken: proofToken
                    )
                }
            case .forgot:
                switch forgotStep {
                case .email:
                    let res = try await session.requestPasswordReset(email: email)
                    stubHint = res.stubCode
                    forgotStep = .code
                    password = ""
                    infoText = res.stub
                        ? "Local stub OTP — enter the code shown below."
                        : "If an account exists, a reset code was sent."
                case .code:
                    try await session.completePasswordReset(
                        email: email,
                        code: otpCode,
                        newPassword: password
                    )
                    resetWizard(to: .signIn)
                    infoText = "Password updated — sign in with your new password."
                }
            }
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
}

#Preview {
    SignInScreen()
        .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
