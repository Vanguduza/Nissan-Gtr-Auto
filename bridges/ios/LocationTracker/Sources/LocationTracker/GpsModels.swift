import Foundation

/// Mirrors bridges/contracts/gps.ts — native Swift surface for GpsBridge.
/// Bridge emits coordinates only; callers map via `toDeliveryLocationIngest`
/// and post with RPC `ingest_delivery_location` (never from this module).

public enum LocationPermissionStatus: String, Sendable {
    case granted
    case denied
    case restricted
    case notDetermined = "not_determined"
    /// Android approximate-only; on iOS unused (maps to denied/notDetermined).
    case approximate
}

/// One native position fix from the device.
public struct GpsCoordinate: Sendable, Equatable {
    public var latitude: Double
    public var longitude: Double
    /// Horizontal accuracy in meters when the OS provides it.
    public var accuracyMeters: Double?
    /// ISO-8601 timestamp from the device (maps to p_recorded_at).
    public var capturedAt: String

    public init(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double? = nil,
        capturedAt: String
    ) {
        self.latitude = latitude
        self.longitude = longitude
        self.accuracyMeters = accuracyMeters
        self.capturedAt = capturedAt
    }
}

/// Payload shape for `ingest_delivery_location` after a bridge fix.
public struct DeliveryLocationIngest: Sendable, Equatable {
    public var deliveryJobId: String
    public var lat: Double
    public var lng: Double
    public var recordedAt: String?
    public var accuracyM: Double?

    public init(
        deliveryJobId: String,
        lat: Double,
        lng: Double,
        recordedAt: String? = nil,
        accuracyM: Double? = nil
    ) {
        self.deliveryJobId = deliveryJobId
        self.lat = lat
        self.lng = lng
        self.recordedAt = recordedAt
        self.accuracyM = accuracyM
    }
}

public func toDeliveryLocationIngest(
    deliveryJobId: String,
    coord: GpsCoordinate
) -> DeliveryLocationIngest {
    DeliveryLocationIngest(
        deliveryJobId: deliveryJobId,
        lat: coord.latitude,
        lng: coord.longitude,
        recordedAt: coord.capturedAt,
        accuracyM: coord.accuracyMeters
    )
}

public protocol GpsWatchHandle: AnyObject {
    func stop() async
}

/// Native GPS for delivery tracking.
/// Prefer `watchPosition` while a job is en route; throttle client-side (~5s)
/// before calling `ingest_delivery_location`. This bridge does not call Supabase.
public protocol GpsBridge: AnyObject {
    func getLocationPermissionStatus() async -> LocationPermissionStatus
    func requestLocationPermission() async -> LocationPermissionStatus
    func getCurrentPosition() async throws -> GpsCoordinate
    func watchPosition(
        onUpdate: @escaping @Sendable (GpsCoordinate) -> Void,
        onError: (@Sendable (String) -> Void)?
    ) async throws -> GpsWatchHandle
}
