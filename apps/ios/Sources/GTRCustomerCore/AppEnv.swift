import Foundation

/// Env placeholders — wire via scheme env or xcconfig. Never hardcode secrets.
public enum AppEnv {
    public static var supabaseURL: String {
        ProcessInfo.processInfo.environment["SUPABASE_URL"] ?? ""
    }

    public static var supabaseAnonKey: String {
        ProcessInfo.processInfo.environment["SUPABASE_ANON_KEY"] ?? ""
    }

    /// Optional customer JWT for AuthZ RPCs. When unset, Bearer = anon key
    /// (RLS/AuthZ customer RPCs will fail until sign-in supplies a real session).
    public static var accessToken: String? {
        let raw = ProcessInfo.processInfo.environment["SUPABASE_ACCESS_TOKEN"] ?? ""
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    /// Force Fake even when URL + anon are set (`STOREFRONT_FORCE_FAKE=1|true|yes`).
    public static var forceFake: Bool {
        let raw = (ProcessInfo.processInfo.environment["STOREFRONT_FORCE_FAKE"] ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
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
}
