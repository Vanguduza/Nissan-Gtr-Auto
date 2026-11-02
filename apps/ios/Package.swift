// swift-tools-version: 5.9
import PackageDescription

// Live storefront uses URLSession → PostgREST (`/rest/v1/rpc/*`) so this package
// resolves on hosts without pulling supabase-swift (Windows scaffold / CI).
// Optional later (macOS): add `.package(url: "https://github.com/supabase/supabase-swift", from: "2.0.0")`
// and a thin adapter behind `StorefrontApi` — keep RPC names identical to web AuthZ.

let package = Package(
    name: "GTRCustomer",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [
        .library(name: "GTRCustomerCore", targets: ["GTRCustomerCore"]),
    ],
    targets: [
        .target(
            name: "GTRCustomerCore",
            path: "Sources/GTRCustomerCore",
            exclude: {
                #if os(Linux)
                // Device-provider UI/crypto are compiled by the macOS+iOS CI job.
                return ["GoogleOAuthBrowser.swift", "AuthNonce.swift"]
                #else
                return []
                #endif
            }()
        ),
        .testTarget(name: "GTRCustomerCoreTests", dependencies: ["GTRCustomerCore"], path: "Tests/GTRCustomerCoreTests"),
    ]
)
