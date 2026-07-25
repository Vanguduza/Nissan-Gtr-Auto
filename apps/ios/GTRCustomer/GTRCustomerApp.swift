import SwiftUI

@main
struct GTRCustomerApp: App {
    @StateObject private var session = StorefrontSession()
    @State private var pendingTrackRef: DeliveryTrackRef?

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(session)
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
                }
                .onOpenURL { url in
                    if let ref = Self.parseTrackURL(url) {
                        pendingTrackRef = ref
                    }
                }
        }
    }

    /// `gtr-customer://track?token=…` or `gtr-customer://track?job=<uuid>`
    static func parseTrackURL(_ url: URL) -> DeliveryTrackRef? {
        guard url.scheme == "gtr-customer" else { return nil }
        let host = (url.host ?? "").lowercased()
        let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")).lowercased()
        guard host == "track" || path == "track" || host.isEmpty && path.hasPrefix("track") else {
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
        // Path form: gtr-customer://track/<token>
        let segments = url.path.split(separator: "/").map(String.init)
        if let last = segments.last, last.count >= 8, last.lowercased() != "track" {
            return .token(last)
        }
        return nil
    }
}

extension DeliveryTrackRef: Identifiable {
    public var id: String {
        switch self {
        case .job(let id): return "job:\(id.uuidString)"
        case .token(let t): return "token:\(t)"
        }
    }
}
