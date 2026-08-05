import SwiftUI

@main
struct GTRCustomerApp: App {
    @StateObject private var session = StorefrontSession()
    @State private var pendingTrackRef: DeliveryTrackRef?
    @State private var pendingPartsOem: String?
    @State private var showSplash = true

    var body: some Scene {
        WindowGroup {
            ZStack {
                if showSplash {
                    ShopSplash {
                        withAnimation(.easeOut(duration: 0.35)) {
                            showSplash = false
                        }
                    }
                    .transition(.opacity)
                } else {
                    ContentView(pendingPartsOem: $pendingPartsOem)
                        .environmentObject(session)
                        .shopTheme()
                        .preferredColorScheme(session.preferredColorScheme)
                        .transition(.opacity)
                }
            }
            .sheet(item: $pendingTrackRef) { ref in
                NavigationStack {
                    DeliveryTrackScreen(ref: ref)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Close") { pendingTrackRef = nil }
                            }
                        }
                }
                .environmentObject(session)
                .shopTheme()
            }
            .onOpenURL { url in
                if let ref = Self.parseTrackURL(url) {
                    pendingTrackRef = ref
                }
                if let oem = Self.parsePartsOEM(url) {
                    pendingPartsOem = oem
                }
            }
        }
    }

    /// Accepts `gtrcustomer://` (Android parity) and legacy `gtr-customer://`.
    static func isCustomerScheme(_ scheme: String?) -> Bool {
        guard let scheme else { return false }
        let s = scheme.lowercased()
        return s == "gtrcustomer" || s == "gtr-customer"
    }

    /// `gtrcustomer://track/{token}` · `gtr-customer://track?token=…` · `?job=`
    static func parseTrackURL(_ url: URL) -> DeliveryTrackRef? {
        guard isCustomerScheme(url.scheme) else { return nil }
        let host = (url.host ?? "").lowercased()
        let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")).lowercased()
        guard host == "track" || path == "track" || path.hasPrefix("track/") ||
            (host.isEmpty && path.hasPrefix("track"))
        else {
            return nil
        }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        if let token = items.first(where: { $0.name == "token" })?.value?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           token.count >= 8 {
            return .token(token)
        }
        if let job = items.first(where: { $0.name == "job" })?.value,
           let id = UUID(uuidString: job) {
            return .job(id)
        }
        let segments = url.path.split(separator: "/").map(String.init)
        if let last = segments.last, last.count >= 8, last.lowercased() != "track" {
            return .token(last)
        }
        return nil
    }

    /// `gtrcustomer://parts/{oem}` or https `…/parts/{oem}`
    static func parsePartsOEM(_ url: URL) -> String? {
        if isCustomerScheme(url.scheme) {
            let host = (url.host ?? "").lowercased()
            if host == "parts" {
                let seg = url.path.split(separator: "/").map(String.init).first
                    ?? url.lastPathComponent
                let oem = seg.trimmingCharacters(in: .whitespacesAndNewlines)
                return oem.isEmpty || oem.lowercased() == "parts" ? nil : oem
            }
        }
        let segments = url.path.split(separator: "/").map(String.init)
        if segments.count >= 2,
           segments[segments.count - 2].lowercased() == "parts" {
            let oem = segments.last!.trimmingCharacters(in: .whitespacesAndNewlines)
            return oem.isEmpty ? nil : oem
        }
        return nil
    }
}
