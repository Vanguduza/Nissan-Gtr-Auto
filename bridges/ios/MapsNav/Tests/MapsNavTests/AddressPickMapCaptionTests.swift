import XCTest
@testable import MapsNav

final class AddressPickMapCaptionTests: XCTestCase {
    func testCaptionMapLibreSoR() {
        XCTAssertEqual(addressPickMapCaption(showingMapLibre: true), "MapLibre SoR")
        XCTAssertEqual(trackMapCaption(showingMapLibre: true), "MapLibre SoR")
    }

    func testCaptionDeprecatedMapKitFallback() {
        XCTAssertEqual(
            addressPickMapCaption(showingMapLibre: false),
            "DEPRECATED MapKit fallback"
        )
        XCTAssertEqual(
            trackMapCaption(showingMapLibre: false),
            "DEPRECATED MapKit fallback"
        )
    }

    func testResolveUseMapLibreDefaultsTrue() {
        XCTAssertTrue(resolveUseMapLibre(processEnv: [:], infoPlistValue: nil))
        XCTAssertTrue(resolveUseMapLibre(processEnv: [:], infoPlistValue: ""))
        XCTAssertTrue(resolveUseMapLibre(processEnv: [:], infoPlistValue: "$(USE_MAPLIBRE)"))
    }

    func testResolveUseMapLibreFalseValues() {
        for raw in ["false", "0", "no", "off", "FALSE"] {
            XCTAssertFalse(
                resolveUseMapLibre(processEnv: ["USE_MAPLIBRE": raw], infoPlistValue: nil),
                "expected false for \(raw)"
            )
        }
    }

    func testResolveUseMapLibreProcessEnvWinsOverPlist() {
        XCTAssertFalse(
            resolveUseMapLibre(
                processEnv: ["USE_MAPLIBRE": "false"],
                infoPlistValue: "true"
            )
        )
        XCTAssertTrue(
            resolveUseMapLibre(
                processEnv: ["USE_MAPLIBRE": "true"],
                infoPlistValue: "false"
            )
        )
    }

    func testDefaultHarareCenter() {
        XCTAssertEqual(defaultAddressPickCenter.latitude, -17.8292, accuracy: 0.0001)
        XCTAssertEqual(defaultAddressPickCenter.longitude, 31.0522, accuracy: 0.0001)
    }
}
