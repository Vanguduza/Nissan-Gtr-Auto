import Foundation

/// Env placeholders — scheme env, then Info.plist (xcconfig). Never hardcode secrets.
public enum AppEnv {
    public static var supabaseURL: String {
        env("SUPABASE_URL")
    }

    public static var supabaseAnonKey: String {
        env("SUPABASE_ANON_KEY")
    }

    /// Debug-only bootstrap customer JWT for AuthZ RPCs (scheme env only — not Info.plist).
    /// Prefer Sign-in UI; when unset, Bearer starts as anon until GoTrue / AuthTokenStore supplies a session.
    public static var accessToken: String? {
        #if DEBUG
        let raw = ProcessInfo.processInfo.environment["SUPABASE_ACCESS_TOKEN"] ?? ""
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
        #else
        return nil
        #endif
    }

    /// Force Fake even when URL + anon are set (`STOREFRONT_FORCE_FAKE=1|true|yes`).
    public static var forceFake: Bool {
        let raw = env("STOREFRONT_FORCE_FAKE").lowercased()
        return ["1", "true", "yes", "on"].contains(raw)
    }

    public static var isConfigured: Bool {
        !supabaseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !supabaseAnonKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Live when configured and not force-faked.
    public static var prefersLive: Bool {
        isConfigured && !forceFake
    }

    public static let productionSupabaseHost = "bicyjghgdnzlnjqxzoud.supabase.co"

    /// The release target is pinned to the approved replacement project. Key values
    /// remain owner-supplied public client credentials, never server credentials.
    public static func validateReleaseConfiguration(url: String, anonKey: String, forceFake: Bool) throws {
        guard !forceFake, let parsed = URLComponents(string: url),
              parsed.scheme == "https", parsed.host == productionSupabaseHost,
              parsed.port == nil, parsed.user == nil, parsed.password == nil,
              parsed.query == nil, parsed.fragment == nil,
              parsed.path.isEmpty || parsed.path == "/"
        else { throw StorefrontError.notConfigured }
        let key = anonKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if key.hasPrefix("sb_publishable_") {
            guard key.range(of: #"^sb_publishable_[A-Za-z0-9_-]{20,}$"#, options: .regularExpression) != nil else {
                throw StorefrontError.notConfigured
            }
            return
        }
        let parts = key.split(separator: ".")
        guard parts.count == 3 else { throw StorefrontError.notConfigured }
        var payload = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
        guard let data = Data(base64Encoded: payload),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              json["role"] as? String == "anon",
              json["ref"] as? String == productionSupabaseHost.components(separatedBy: ".").first,
              let expiry = json["exp"] as? TimeInterval,
              expiry.isFinite, expiry > Date().timeIntervalSince1970
        else { throw StorefrontError.notConfigured }
    }

    /// Digits-only E.164 for `wa.me` CTA. Empty means the optional CTA is unavailable.
    public static var whatsappE164Digits: String {
        env("WHATSAPP_E164").filter(\.isNumber)
    }

    /// Scheme `ProcessInfo` first, then generated Info.plist keys from `Config/Shared.xcconfig`.
    private static func env(_ key: String) -> String {
        if let fromProcess = ProcessInfo.processInfo.environment[key]?
            .trimmingCharacters(in: .whitespacesAndNewlines),
            !fromProcess.isEmpty {
            return fromProcess
        }
        if let fromPlist = Bundle.main.object(forInfoDictionaryKey: key) as? String {
            let trimmed = fromPlist.trimmingCharacters(in: .whitespacesAndNewlines)
            // Unexpanded $(VAR) placeholders count as unset.
            if !trimmed.isEmpty, !trimmed.hasPrefix("$(") {
                return trimmed
            }
        }
        return ""
    }
}
