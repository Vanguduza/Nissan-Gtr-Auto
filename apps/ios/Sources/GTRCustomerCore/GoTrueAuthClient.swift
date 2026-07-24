import Foundation

/// Minimal GoTrue password grant — same URLSession style as `PostgrestClient`.
///
/// `POST {SUPABASE_URL}/auth/v1/token?grant_type=password`
/// Headers: `apikey`, `Content-Type: application/json`
/// Body: `{ "email", "password" }`
public final class GoTrueAuthClient: @unchecked Sendable {
    public struct Session: Sendable, Equatable {
        public let accessToken: String
        public let refreshToken: String?
        public let email: String?
        public let expiresIn: Int?
    }

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

        var components = URLComponents(
            url: baseURL.appendingPathComponent("auth/v1/token"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "grant_type", value: "password")]
        guard let url = components.url else {
            throw StorefrontError.message("Invalid GoTrue token URL.")
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "email": trimmedEmail,
            "password": password,
        ])

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
        var userEmail = trimmedEmail
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
}
