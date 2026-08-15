import Foundation

/// Minimal GoTrue client — password + OpenID `id_token` + PKCE exchange.
/// Same URLSession style as `PostgrestClient` (no supabase-swift).
///
/// - Password: `POST …/auth/v1/token?grant_type=password`
/// - Apple/Google native: `POST …/auth/v1/token?grant_type=id_token`
/// - OAuth redirect: authorize → `gtrcustomer://auth/callback` → PKCE token
public final class GoTrueAuthClient: @unchecked Sendable {
    public struct Session: Sendable, Equatable {
        public let accessToken: String
        public let refreshToken: String?
        public let email: String?
        public let expiresIn: Int?
    }

    public enum IdTokenProvider: String, Sendable {
        case apple
        case google
    }

    /// Preferred mobile auth callback — matches `docs/CUSTOMER_OAUTH_SETUP.md` + Android.
    public static let preferredRedirectURL = "gtrcustomer://auth/callback"
    public static let legacyRedirectURL = "gtr-customer://auth/callback"
    public static let preferredCallbackScheme = "gtrcustomer"

    private let baseURL: URL
    private let anonKey: String
    private let session: URLSession

    public init(
        supabaseURL: String,
        anonKey: String,
        session: URLSession = .shared
    ) throws {
        let trimmed = supabaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = URL(string: trimmed), !anonKey.isEmpty else {
            throw StorefrontError.notConfigured
        }
        self.baseURL = url
        self.anonKey = anonKey
        self.session = session
    }

    public convenience init() throws {
        try self.init(
            supabaseURL: AppEnv.supabaseURL,
            anonKey: AppEnv.supabaseAnonKey
        )
    }

    /// Email/password sign-in → access_token (+ refresh_token when returned).
    public func signIn(email: String, password: String) async throws -> Session {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedEmail.isEmpty, !password.isEmpty else {
            throw StorefrontError.message("Email and password are required.")
        }
        return try await postToken(
            grantType: "password",
            body: [
                "email": trimmedEmail,
                "password": password,
            ],
            fallbackEmail: trimmedEmail
        )
    }

    /// Native Apple / Google ID token → GoTrue session.
    /// `nonce` must be the **raw** nonce when a hashed nonce was sent to the provider.
    public func signInWithIdToken(
        provider: IdTokenProvider,
        idToken: String,
        nonce: String?
    ) async throws -> Session {
        let trimmed = idToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw StorefrontError.message("ID token is required.")
        }
        var body: [String: Any] = [
            "provider": provider.rawValue,
            "id_token": trimmed,
        ]
        if let nonce, !nonce.isEmpty {
            body["nonce"] = nonce
        }
        return try await postToken(grantType: "id_token", body: body, fallbackEmail: nil)
    }

    /// Build Google (or other) OAuth authorize URL with PKCE + mobile redirect.
    public func oauthAuthorizeURL(
        provider: IdTokenProvider,
        codeChallenge: String,
        redirectTo: String = GoTrueAuthClient.preferredRedirectURL
    ) throws -> URL {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("auth/v1/authorize"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [
            URLQueryItem(name: "provider", value: provider.rawValue),
            URLQueryItem(name: "redirect_to", value: redirectTo),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "s256"),
        ]
        guard let url = components.url else {
            throw StorefrontError.message("Invalid GoTrue authorize URL.")
        }
        return url
    }

    /// Exchange PKCE auth code from `gtrcustomer://auth/callback?code=…`.
    public func exchangePKCE(authCode: String, codeVerifier: String) async throws -> Session {
        let code = authCode.trimmingCharacters(in: .whitespacesAndNewlines)
        let verifier = codeVerifier.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty, !verifier.isEmpty else {
            throw StorefrontError.message("OAuth code and verifier are required.")
        }
        return try await postToken(
            grantType: "pkce",
            body: [
                "auth_code": code,
                "code_verifier": verifier,
            ],
            fallbackEmail: nil
        )
    }

    /// Parse deep-link callback: `?code=` (PKCE) or `#access_token=` (implicit fallback).
    public static func parseAuthCallback(_ url: URL) -> AuthCallbackPayload? {
        guard isAuthCallbackURL(url) else { return nil }
        let items = queryAndFragmentItems(url)
        if let code = items["code"], !code.isEmpty {
            return .pkce(code: code)
        }
        if let access = items["access_token"], !access.isEmpty {
            let refresh = items["refresh_token"]
            let email = items["email"]
            let expires = items["expires_in"].flatMap(Int.init)
            return .session(
                Session(
                    accessToken: access,
                    refreshToken: (refresh?.isEmpty == false) ? refresh : nil,
                    email: (email?.isEmpty == false) ? email : nil,
                    expiresIn: expires
                )
            )
        }
        if let err = items["error_description"] ?? items["error"] {
            return .error(err)
        }
        return nil
    }

    public static func isAuthCallbackURL(_ url: URL) -> Bool {
        let scheme = (url.scheme ?? "").lowercased()
        guard scheme == "gtrcustomer" || scheme == "gtr-customer" else { return false }
        let host = (url.host ?? "").lowercased()
        let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")).lowercased()
        // gtrcustomer://auth/callback  → host=auth, path=callback
        // gtrcustomer:///auth/callback → host empty, path=auth/callback
        if host == "auth" && (path == "callback" || path.isEmpty || path.hasPrefix("callback")) {
            return true
        }
        if host.isEmpty && (path == "auth/callback" || path.hasPrefix("auth/callback")) {
            return true
        }
        return false
    }

    // MARK: - Internals

    private func postToken(
        grantType: String,
        body: [String: Any],
        fallbackEmail: String?
    ) async throws -> Session {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("auth/v1/token"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "grant_type", value: grantType)]
        guard let url = components.url else {
            throw StorefrontError.message("Invalid GoTrue token URL.")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw StorefrontError.message("Invalid HTTP response.")
        }

        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw StorefrontError.message("GoTrue returned non-JSON (HTTP \(http.statusCode)).")
        }

        if !(200 ..< 300).contains(http.statusCode) {
            let desc =
                (obj["error_description"] as? String)
                ?? (obj["msg"] as? String)
                ?? (obj["error"] as? String)
                ?? "HTTP \(http.statusCode)"
            throw StorefrontError.message(desc)
        }

        guard let access = obj["access_token"] as? String, !access.isEmpty else {
            throw StorefrontError.message("GoTrue response missing access_token.")
        }

        let refresh = obj["refresh_token"] as? String
        var userEmail = fallbackEmail
        if let user = obj["user"] as? [String: Any],
           let fromUser = user["email"] as? String,
           !fromUser.isEmpty
        {
            userEmail = fromUser
        }

        return Session(
            accessToken: access,
            refreshToken: (refresh?.isEmpty == false) ? refresh : nil,
            email: userEmail,
            expiresIn: obj["expires_in"] as? Int
        )
    }

    private static func queryAndFragmentItems(_ url: URL) -> [String: String] {
        var out: [String: String] = [:]
        if let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems {
            for item in items {
                if let value = item.value { out[item.name] = value }
            }
        }
        if let fragment = url.fragment, !fragment.isEmpty {
            for pair in fragment.split(separator: "&") {
                let parts = pair.split(separator: "=", maxSplits: 1).map(String.init)
                guard parts.count == 2 else { continue }
                out[parts[0]] = parts[1].removingPercentEncoding ?? parts[1]
            }
        }
        return out
    }
}

public enum AuthCallbackPayload: Sendable {
    case pkce(code: String)
    case session(GoTrueAuthClient.Session)
    case error(String)
}

// MARK: - Edge auth-otp + password-reset (mirrors web)

/// Hosted signup / password recovery via Edge — public GoTrue `/signup` is blocked.
public enum AuthEdgeClient {
    public struct OtpRequestResult: Sendable {
        public let stub: Bool
        public let stubCode: String?
    }

    public struct OtpVerifyResult: Sendable {
        public let proofToken: String
        public let email: String?
    }

    public static func requestSignupOtp(
        client: PostgrestClient,
        email: String
    ) async throws -> OtpRequestResult {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { throw StorefrontError.message("Enter your email.") }
        let data = try await client.invokeFunction(
            "auth-otp",
            body: ["action": "request", "email": trimmed]
        )
        let obj = try jsonObject(data)
        if let err = stringField(obj, "error") { throw StorefrontError.message(err) }
        return OtpRequestResult(
            stub: (obj["stub"] as? Bool) == true,
            stubCode: stringField(obj, "stub_code")
        )
    }

    public static func verifySignupOtp(
        client: PostgrestClient,
        email: String,
        code: String
    ) async throws -> OtpVerifyResult {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let digits = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard digits.count == 6, digits.allSatisfy({ $0.isNumber }) else {
            throw StorefrontError.message("Enter the 6-digit code.")
        }
        let data = try await client.invokeFunction(
            "auth-otp",
            body: ["action": "verify", "email": trimmed, "code": digits]
        )
        let obj = try jsonObject(data)
        if let err = stringField(obj, "error") { throw StorefrontError.message(err) }
        guard (obj["verified"] as? Bool) == true else {
            throw StorefrontError.message(stringField(obj, "error") ?? "OTP verification failed")
        }
        guard let proof = stringField(obj, "proof_token"), !proof.isEmpty else {
            throw StorefrontError.message("OTP verify did not return proof_token")
        }
        return OtpVerifyResult(proofToken: proof, email: stringField(obj, "email") ?? trimmed)
    }

    public static func completeSignup(
        client: PostgrestClient,
        email: String,
        password: String,
        proofToken: String,
        fullName: String? = nil
    ) async throws -> GoTrueAuthClient.Session {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard password.count >= 8 else {
            throw StorefrontError.message("Password must be at least 8 characters.")
        }
        guard !proofToken.isEmpty else {
            throw StorefrontError.message("OTP proof missing — verify OTP again.")
        }
        var body: [String: Any] = [
            "action": "complete_signup",
            "email": trimmed,
            "password": password,
            "proof_token": proofToken,
        ]
        if let fullName, !fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            body["full_name"] = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let data = try await client.invokeFunction("auth-otp", body: body)
        return try sessionFromEdge(data, fallbackEmail: trimmed)
    }

    public static func requestPasswordReset(
        client: PostgrestClient,
        email: String
    ) async throws -> OtpRequestResult {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { throw StorefrontError.message("Enter your email.") }
        let data = try await client.invokeFunction(
            "request-password-reset",
            body: ["email": trimmed]
        )
        let obj = try jsonObject(data)
        if let err = stringField(obj, "error") { throw StorefrontError.message(err) }
        return OtpRequestResult(
            stub: (obj["stub"] as? Bool) == true,
            stubCode: stringField(obj, "stub_code")
        )
    }

    public static func verifyPasswordReset(
        client: PostgrestClient,
        email: String,
        code: String,
        newPassword: String
    ) async throws {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let digits = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard digits.count == 6, digits.allSatisfy({ $0.isNumber }) else {
            throw StorefrontError.message("Enter the 6-digit code.")
        }
        guard newPassword.count >= 8 else {
            throw StorefrontError.message("Password must be at least 8 characters.")
        }
        let data = try await client.invokeFunction(
            "verify-password-reset",
            body: [
                "email": trimmed,
                "code": digits,
                "new_password": newPassword,
            ]
        )
        let obj = try jsonObject(data)
        if let err = stringField(obj, "error") { throw StorefrontError.message(err) }
        if (obj["ok"] as? Bool) == false {
            throw StorefrontError.message(stringField(obj, "error") ?? "Could not reset password")
        }
    }

    private static func sessionFromEdge(
        _ data: Data,
        fallbackEmail: String?
    ) throws -> GoTrueAuthClient.Session {
        let obj = try jsonObject(data)
        if let err = stringField(obj, "error") { throw StorefrontError.message(err) }
        guard let access = stringField(obj, "access_token"), !access.isEmpty,
              let refresh = stringField(obj, "refresh_token"), !refresh.isEmpty
        else {
            throw StorefrontError.message("Signup did not return a session")
        }
        return GoTrueAuthClient.Session(
            accessToken: access,
            refreshToken: refresh,
            email: stringField(obj, "email") ?? fallbackEmail,
            expiresIn: obj["expires_in"] as? Int
        )
    }

    private static func jsonObject(_ data: Data) throws -> [String: Any] {
        guard let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw StorefrontError.message("Edge returned non-JSON")
        }
        return obj
    }

    private static func stringField(_ obj: [String: Any], _ key: String) -> String? {
        guard let s = obj[key] as? String else { return nil }
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
