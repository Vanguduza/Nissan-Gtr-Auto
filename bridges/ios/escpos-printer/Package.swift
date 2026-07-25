// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "EscPosPrinter",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "EscPosPrinter", targets: ["EscPosPrinter"]),
    ],
    targets: [
        .target(name: "EscPosPrinter"),
    ]
)
