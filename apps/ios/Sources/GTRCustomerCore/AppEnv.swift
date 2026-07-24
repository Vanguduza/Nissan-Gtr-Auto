import Foundation

/// Env placeholders — wire via scheme env or xcconfig. Never hardcode secrets.
public enum AppEnv {
    public static var supabaseURL: String {
        ProcessInfo.processInfo.environment["SUPABASE_URL"] ?? ""
    }

    public static var supabaseAnonKey: String {
        ProcessInfo.processInfo.environment["SUPABASE_ANON_KEY"] ?? ""
    }

    public static var isConfigured: Bool {
        !supabaseURL.isEmpty && !supabaseAnonKey.isEmpty
    }
}
