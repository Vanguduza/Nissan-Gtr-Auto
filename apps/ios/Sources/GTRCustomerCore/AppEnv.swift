import Foundation

/// Env placeholders — scheme env, then Info.plist (xcconfig). Never hardcode secrets.
public enum AppEnv {
    public static var supabaseURL: String {
        env("SUPABASE_URL")
    }

    public static var supabaseAnonKey: String {
        env("SUPABASE_ANON_KEY")
    }

    /// Optional bootstrap customer JWT for AuthZ RPCs (scheme env only — not Info.plist).
    /// Prefer Sign-in UI; when unset, Bearer starts as anon until GoTrue / AuthTokenStore supplies a session.
    public static var accessToken: String? {
        let raw = ProcessInfo.processInfo.environment["SUPABASE_ACCESS_TOKEN"] ?? ""
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
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
