import CoreLocation
import Foundation

/// iOS [GpsBridge] via CoreLocation.
///
/// Info.plist (host app) must declare:
/// - `NSLocationWhenInUseUsageDescription`
/// - `NSLocationAlwaysAndWhenInUseUsageDescription` (for background delivery)
/// Background Modes → Location updates (UIBackgroundModes: location)
///
/// Does not perform network I/O — emit [GpsCoordinate] only.
@MainActor
public final class CoreLocationGpsBridge: NSObject, GpsBridge {
    private let manager = CLLocationManager()
    private var permissionContinuation: CheckedContinuation<LocationPermissionStatus, Never>?
    private var oneShotContinuation: CheckedContinuation<GpsCoordinate, Error>?
    private var watchOnUpdate: (@Sendable (GpsCoordinate) -> Void)?
    private var watchOnError: (@Sendable (String) -> Void)?
    private var activeWatch: WatchHandle?

    public override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        // Delivery: allow pauses off so trail stays dense while moving slowly.
        manager.pausesLocationUpdatesAutomatically = false
        manager.activityType = .automotiveNavigation
    }

    public func getLocationPermissionStatus() async -> LocationPermissionStatus {
        Self.mapAuthorization(manager.authorizationStatus)
    }

    public func requestLocationPermission() async -> LocationPermissionStatus {
        let current = manager.authorizationStatus
        switch current {
        case .authorizedAlways, .authorizedWhenInUse:
            return Self.mapAuthorization(current)
        case .denied, .restricted:
            return Self.mapAuthorization(current)
        case .notDetermined:
            return await withCheckedContinuation { cont in
                permissionContinuation = cont
                manager.requestWhenInUseAuthorization()
            }
        @unknown default:
            return .notDetermined
        }
    }

    /// Request Always after When In Use is granted (separate UX step; required for
    /// reliable background delivery tracking).
    public func requestAlwaysAuthorization() {
        guard manager.authorizationStatus == .authorizedWhenInUse else { return }
        manager.requestAlwaysAuthorization()
    }

    public func getCurrentPosition() async throws -> GpsCoordinate {
        let status = manager.authorizationStatus
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            throw GpsBridgeError.permissionDenied
        }
        guard CLLocationManager.locationServicesEnabled() else {
            throw GpsBridgeError.locationDisabled
        }
        return try await withCheckedThrowingContinuation { cont in
            oneShotContinuation = cont
            manager.requestLocation()
        }
    }

    public func watchPosition(
        onUpdate: @escaping @Sendable (GpsCoordinate) -> Void,
        onError: (@Sendable (String) -> Void)?,
        options: GpsWatchOptions = GpsWatchOptions()
    ) async throws -> GpsWatchHandle {
        let status = manager.authorizationStatus
        guard status == .authorizedAlways || status == .authorizedWhenInUse else {
            throw GpsBridgeError.permissionDenied
        }
        guard CLLocationManager.locationServicesEnabled() else {
            throw GpsBridgeError.locationDisabled
        }

        await activeWatch?.stop()

        watchOnUpdate = onUpdate
        watchOnError = onError

        switch options.cadence {
        case .idle:
            manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
            manager.distanceFilter = options.minDistanceMeters ?? 25
        case .moving, .auto:
            manager.desiredAccuracy = kCLLocationAccuracyBest
            manager.distanceFilter = options.minDistanceMeters ?? kCLDistanceFilterNone
        }

        if status == .authorizedAlways {
            manager.allowsBackgroundLocationUpdates = true
            manager.showsBackgroundLocationIndicator = true
        }

        let handle = WatchHandle(bridge: self)
        activeWatch = handle
        manager.startUpdatingLocation()
        return handle
    }

    fileprivate func stopWatching() {
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        manager.showsBackgroundLocationIndicator = false
        watchOnUpdate = nil
        watchOnError = nil
        activeWatch = nil
    }

    private static func mapAuthorization(_ status: CLAuthorizationStatus) -> LocationPermissionStatus {
        switch status {
        case .authorizedAlways, .authorizedWhenInUse:
            return .granted
        case .denied:
            return .denied
        case .restricted:
            return .restricted
        case .notDetermined:
            return .notDetermined
        @unknown default:
            return .notDetermined
        }
    }
}

public enum GpsBridgeError: Error, LocalizedError, Sendable {
    case permissionDenied
    case locationDisabled
    case noFix

    public var errorDescription: String? {
        switch self {
        case .permissionDenied: return "Location permission not granted"
        case .locationDisabled: return "Device location is disabled"
        case .noFix: return "No current location available"
        }
    }
}

@MainActor
private final class WatchHandle: GpsWatchHandle {
    private weak var bridge: CoreLocationGpsBridge?
    private var stopped = false

    init(bridge: CoreLocationGpsBridge) {
        self.bridge = bridge
    }

    func stop() async {
        guard !stopped else { return }
        stopped = true
        bridge?.stopWatching()
        bridge = nil
    }
}

extension CoreLocationGpsBridge: CLLocationManagerDelegate {
    public nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            let status = Self.mapAuthorization(manager.authorizationStatus)
            if let cont = permissionContinuation {
                permissionContinuation = nil
                cont.resume(returning: status)
            }
        }
    }

    public nonisolated func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        guard let location = locations.last else { return }
        let coord = location.toGpsCoordinate()
        Task { @MainActor in
            if let oneShot = oneShotContinuation {
                oneShotContinuation = nil
                oneShot.resume(returning: coord)
                return
            }
            watchOnUpdate?(coord)
        }
    }

    public nonisolated func locationManager(
        _ manager: CLLocationManager,
        didFailWithError error: Error
    ) {
        let message = error.localizedDescription
        Task { @MainActor in
            if let oneShot = oneShotContinuation {
                oneShotContinuation = nil
                oneShot.resume(throwing: error)
                return
            }
            watchOnError?(message)
        }
    }
}

extension CLLocation {
    func toGpsCoordinate() -> GpsCoordinate {
        let accuracy: Double? = horizontalAccuracy >= 0 ? horizontalAccuracy : nil
        let capturedAt = ISO8601DateFormatter().string(from: timestamp)
        return GpsCoordinate(
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            accuracyMeters: accuracy,
            capturedAt: capturedAt
        )
    }
}
