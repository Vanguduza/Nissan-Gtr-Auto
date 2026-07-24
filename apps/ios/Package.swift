// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "GTRCustomer",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "GTRCustomerCore", targets: ["GTRCustomerCore"]),
    ],
    targets: [
        .target(
            name: "GTRCustomerCore",
            path: "Sources/GTRCustomerCore"
        ),
    ]
)
