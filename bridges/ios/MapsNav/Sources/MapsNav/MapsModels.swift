import Foundation

/// Lat/lng pin for address pick / track display (parity with Android `MapLatLng`).
public struct MapLatLng: Equatable, Sendable {
    public var latitude: Double
    public var longitude: Double

    public init(latitude: Double, longitude: Double) {
        self.latitude = latitude
        self.longitude = longitude
    }
}

/// Public demo tiles — ops should prefer a self-hosted style via `MAPLIBRE_STYLE_URL`.
public let defaultMapLibreStyleURL = URL(string: "https://demotiles.maplibre.org/style.json")!

/// Harare CBD default when no pin yet (parity with Android address pick).
public let defaultAddressPickCenter = MapLatLng(latitude: -17.8292, longitude: 31.0522)

/// Caption for address-pick SoR honesty (unit-tested; parity with Android `addressPickMapCaption`).
public func addressPickMapCaption(showingMapLibre: Bool) -> String {
    showingMapLibre ? "MapLibre SoR" : "DEPRECATED MapKit fallback"
}

/// Caption for live-delivery track map SoR honesty.
public func trackMapCaption(showingMapLibre: Bool) -> String {
    showingMapLibre ? "MapLibre SoR" : "DEPRECATED MapKit fallback"
}

/// Resolve MapLibre SoR flag from env / Info.plist.
/// Default **true**. Set `USE_MAPLIBRE=false` (or `0` / `no` / `off`) only for deprecated MapKit tiles.
public func resolveUseMapLibre(
    processEnv: [String: String] = ProcessInfo.processInfo.environment,
    infoPlistValue: String? = Bundle.main.object(forInfoDictionaryKey: "USE_MAPLIBRE") as? String
) -> Bool {
    let raw = firstNonEmpty(processEnv["USE_MAPLIBRE"], infoPlistValue)?
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .lowercased()
    guard let raw, !raw.isEmpty, !raw.hasPrefix("$(") else {
        return true
    }
    return !["0", "false", "no", "off"].contains(raw)
}

private func firstNonEmpty(_ values: String?...) -> String? {
    for value in values {
        if let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return value
        }
    }
    return nil
}
