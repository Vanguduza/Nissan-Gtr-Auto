// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "QRScanner",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "QRScanner", targets: ["QRScanner"]),
    ],
    targets: [
        .target(name: "QRScanner"),
    ]
)
