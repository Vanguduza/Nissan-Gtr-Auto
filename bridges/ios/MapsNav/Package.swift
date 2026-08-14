// swift-tools-version: 5.9
import PackageDescription

/// Customer / delivery map **display** helpers (B-MAP-1 / H5-iOS).
/// MapLibre Native = render SoR; MapKit = deprecated fallback only.
/// No GPS ingest — use LocationTracker for device location (Bridge-First).
let package = Package(
    name: "MapsNav",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "MapsNav", targets: ["MapsNav"]),
    ],
    dependencies: [
        .package(
            url: "https://github.com/maplibre/maplibre-gl-native-distribution",
            from: "6.28.0"
        ),
    ],
    targets: [
        .target(
            name: "MapsNav",
            dependencies: [
                .product(name: "MapLibre", package: "maplibre-gl-native-distribution"),
            ],
            path: "Sources/MapsNav"
        ),
        .testTarget(
            name: "MapsNavTests",
            dependencies: ["MapsNav"],
            path: "Tests/MapsNavTests"
        ),
    ]
)
