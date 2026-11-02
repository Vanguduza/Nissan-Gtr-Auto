import Foundation
#if canImport(Security)
import Security
#endif

/// Credentials are stored together so refresh-token rotation is atomic.
public struct AuthTokenRecord: Codable, Sendable, Equatable {
    public let accessToken: String
    public let refreshToken: String?
    public let email: String?
    public let expiresAt: Date?

    public init(accessToken: String, refreshToken: String? = nil, email: String? = nil, expiresAt: Date? = nil) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.email = email
        self.expiresAt = expiresAt ?? Self.jwtExpiry(accessToken)
    }

    /// JWT payload parsing is only a refresh hint, never identity verification.
    public static func jwtExpiry(_ token: String) -> Date? {
        let parts = token.split(separator: ".")
        guard parts.count == 3 else { return nil }
        var payload = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
        guard let data = Data(base64Encoded: payload),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let expiry = json["exp"] as? TimeInterval, expiry.isFinite
        else { return nil }
        return Date(timeIntervalSince1970: expiry)
    }

    public func needsRefresh(at now: Date = Date(), leeway: TimeInterval = 60) -> Bool {
        guard let expiresAt else { return true }
        return expiresAt <= now.addingTimeInterval(leeway)
    }
}

public enum AuthStorageError: Error, Equatable {
    case unavailable(Int32)
    case invalidRecord
    case unsupportedPlatform
}

/// Injectable storage lets lifecycle tests exercise failures without a device keychain.
public protocol AuthCredentialStorage: Sendable {
    func read() throws -> Data?
    func write(_ data: Data) throws
    func delete() throws
}

public struct KeychainAuthCredentialStorage: AuthCredentialStorage {
    private let service: String
    private let account: String

    public init(service: String = "co.zw.nissangtr.customer.auth", account: String = "supabase-session-v1") {
        self.service = service
        self.account = account
    }

    #if canImport(Security)
    private var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: account,
         kSecAttrSynchronizable as String: false]
    }

    public func read() throws -> Data? {
        var query = query
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var value: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &value)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw AuthStorageError.unavailable(status) }
        guard let data = value as? Data else { throw AuthStorageError.invalidRecord }
        return data
    }

    public func write(_ data: Data) throws {
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        ]
        var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var item = query
            attributes.forEach { item[$0.key] = $0.value }
            status = SecItemAdd(item as CFDictionary, nil)
            if status == errSecDuplicateItem {
                status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
            }
        }
        guard status == errSecSuccess else { throw AuthStorageError.unavailable(status) }
    }

    public func delete() throws {
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw AuthStorageError.unavailable(status)
        }
    }
    #else
    // A non-Apple host must never silently replace the device keychain with a file.
    public func read() throws -> Data? { throw AuthStorageError.unsupportedPlatform }
    public func write(_ data: Data) throws { throw AuthStorageError.unsupportedPlatform }
    public func delete() throws { throw AuthStorageError.unsupportedPlatform }
    #endif
}

public protocol AuthSessionPersistence {
    func load() throws -> AuthTokenRecord?
    func save(_ record: AuthTokenRecord) throws
    func clear() throws
}

public struct SecureAuthSessionPersistence: AuthSessionPersistence {
    private let storage: any AuthCredentialStorage
    private let defaults: UserDefaults
    private static let legacyKeys = ["gtr.customer.auth.access_token", "gtr.customer.auth.refresh_token", "gtr.customer.auth.email"]
    private static let deletionPendingKey = "gtr.customer.auth.keychain_deletion_pending"

    public init(storage: any AuthCredentialStorage = KeychainAuthCredentialStorage(), defaults: UserDefaults = .standard) {
        self.storage = storage
        self.defaults = defaults
    }

    public func load() throws -> AuthTokenRecord? {
        if defaults.bool(forKey: Self.deletionPendingKey) {
            try storage.delete()
            defaults.removeObject(forKey: Self.deletionPendingKey)
            removeLegacy()
            return nil
        }
        if let data = try storage.read() {
            guard let record = try? JSONDecoder().decode(AuthTokenRecord.self, from: data), !record.accessToken.isEmpty else {
                throw AuthStorageError.invalidRecord
            }
            removeLegacy()
            return record
        }
        guard let access = defaults.string(forKey: Self.legacyKeys[0])?.trimmingCharacters(in: .whitespacesAndNewlines),
              !access.isEmpty else {
            removeLegacy()
            return nil
        }
        let record = AuthTokenRecord(
            accessToken: access,
            refreshToken: defaults.string(forKey: Self.legacyKeys[1]),
            email: defaults.string(forKey: Self.legacyKeys[2])
        )
        // Delete old plaintext only after Keychain has durably accepted the record.
        try save(record)
        return record
    }

    public func save(_ record: AuthTokenRecord) throws {
        guard !record.accessToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw AuthStorageError.invalidRecord
        }
        try storage.write(JSONEncoder().encode(record))
        defaults.removeObject(forKey: Self.deletionPendingKey)
        removeLegacy()
    }

    public func clear() throws {
        // Clear legacy data even if the protected device keychain is temporarily locked.
        removeLegacy()
        defaults.set(true, forKey: Self.deletionPendingKey)
        try storage.delete()
        defaults.removeObject(forKey: Self.deletionPendingKey)
    }

    private func removeLegacy() { Self.legacyKeys.forEach { defaults.removeObject(forKey: $0) } }
}

public enum AuthTokenStore {
    public static func load() throws -> AuthTokenRecord? { try SecureAuthSessionPersistence().load() }
    public static func save(_ record: AuthTokenRecord) throws { try SecureAuthSessionPersistence().save(record) }
    public static func clear() throws { try SecureAuthSessionPersistence().clear() }
}
