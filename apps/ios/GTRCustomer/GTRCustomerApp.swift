import SwiftUI

@main
struct GTRCustomerApp: App {
    @StateObject private var session = StorefrontSession()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(session)
        }
    }
}
