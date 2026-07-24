import Foundation

/// Re-export path for the app target; same placeholders as GTRCustomerCore.
enum AppEnv {
    static var supabaseURL: String {
        ProcessInfo.processInfo.environment["SUPABASE_URL"] ?? ""
    }

    static var supabaseAnonKey: String {
        ProcessInfo.processInfo.environment["SUPABASE_ANON_KEY"] ?? ""
    }

    static var isConfigured: Bool {
        !supabaseURL.isEmpty && !supabaseAnonKey.isEmpty
    }
}
