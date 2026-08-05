// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ReviewCamera",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "ReviewCamera", targets: ["ReviewCamera"]),
    ],
    targets: [
        .target(name: "ReviewCamera", path: "Sources/ReviewCamera"),
    ]
)
