import Foundation

/// Persisted GoTrue session for scaffold restore-on-launch.
/// UserDefaults is fine for local/dev; swap to Keychain before production.
public struct AuthTokenRecord: Sendable, Equatable {
    public let accessToken: String
    public let refreshToken: String?
    public let email: String?

    public init(accessToken: String, refreshToken: String? = nil, email: String? = nil) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.email = email
    }
}

public enum AuthTokenStore {
    private static let accessKey = "gtr.customer.auth.access_token"
    private static let refreshKey = "gtr.customer.auth.refresh_token"
    private static let emailKey = "gtr.customer.auth.email"

    public static func load(defaults: UserDefaults = .standard) -> AuthTokenRecord? {
        guard let access = defaults.string(forKey: accessKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
            !access.isEmpty
        else {
            return nil
        }
        let refresh = defaults.string(forKey: refreshKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let email = defaults.string(forKey: emailKey)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return AuthTokenRecord(
            accessToken: access,
            refreshToken: (refresh?.isEmpty == false) ? refresh : nil,
            email: (email?.isEmpty == false) ? email : nil
        )
    }

    public static func save(_ record: AuthTokenRecord, defaults: UserDefaults = .standard) {
        defaults.set(record.accessToken, forKey: accessKey)
        if let refresh = record.refreshToken, !refresh.isEmpty {
            defaults.set(refresh, forKey: refreshKey)
        } else {
            defaults.removeObject(forKey: refreshKey)
        }
        if let email = record.email, !email.isEmpty {
            defaults.set(email, forKey: emailKey)
        } else {
            defaults.removeObject(forKey: emailKey)
        }
    }

    public static func clear(defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: accessKey)
        defaults.removeObject(forKey: refreshKey)
        defaults.removeObject(forKey: emailKey)
    }
}
