import XCTest
@testable import LocationTracker

final class GpsModelsTests: XCTestCase {
    func testToDeliveryLocationIngestMapsContractFields() {
        let coord = GpsCoordinate(
            latitude: -17.8292,
            longitude: 31.0522,
            accuracyMeters: 12.5,
            capturedAt: "2026-07-24T20:00:00Z"
        )
        let ingest = toDeliveryLocationIngest(
            deliveryJobId: "11111111-1111-1111-1111-111111111111",
            coord: coord
        )
        XCTAssertEqual(ingest.deliveryJobId, "11111111-1111-1111-1111-111111111111")
        XCTAssertEqual(ingest.lat, -17.8292)
        XCTAssertEqual(ingest.lng, 31.0522)
        XCTAssertEqual(ingest.recordedAt, "2026-07-24T20:00:00Z")
        XCTAssertEqual(ingest.accuracyM, 12.5)
    }

    func testToDeliveryLocationIngestOmitsNilAccuracy() {
        let coord = GpsCoordinate(
            latitude: 0,
            longitude: 0,
            accuracyMeters: nil,
            capturedAt: "2026-07-24T20:00:00Z"
        )
        let ingest = toDeliveryLocationIngest(deliveryJobId: "job", coord: coord)
        XCTAssertNil(ingest.accuracyM)
    }
}
