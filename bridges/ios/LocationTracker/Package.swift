// swift-tools-version: 5.9
import PackageDescription

/// Native GPS bridge (CoreLocation). Matches bridges/contracts/gps.ts GpsBridge.
/// No network / Supabase — emits GpsCoordinate only.
let package = Package(
    name: "LocationTracker",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "LocationTracker", targets: ["LocationTracker"]),
    ],
    targets: [
        .target(
            name: "LocationTracker",
            path: "Sources/LocationTracker"
        ),
        .testTarget(
            name: "LocationTrackerTests",
            dependencies: ["LocationTracker"],
            path: "Tests/LocationTrackerTests"
        ),
    ]
)
