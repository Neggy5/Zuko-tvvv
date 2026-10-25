// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "ZukoTViOS",
    platforms: [
        .iOS(.v17)
    ],
    products: [
        .executable(
            name: "ZukoTViOSApp",
            targets: ["ZukoTViOSApp"]
        )
    ],
    targets: [
        .executableTarget(
            name: "ZukoTViOSApp",
            path: "Sources/ZukoTViOSApp"
        )
    ]
)
