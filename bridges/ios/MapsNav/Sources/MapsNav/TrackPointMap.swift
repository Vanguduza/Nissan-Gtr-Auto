import MapKit
import SwiftUI

/// Live-delivery last-point map — MapLibre SoR; MapKit deprecated fallback (B-MAP-1 / H5-iOS).
public struct TrackPointMap: View {
    public var point: MapLatLng?
    public var useMapLibre: Bool
    public var styleURL: URL

    @State private var mapLibreFailed = false

    public init(
        point: MapLatLng?,
        useMapLibre: Bool = true,
        styleURL: URL = defaultMapLibreStyleURL
    ) {
        self.point = point
        self.useMapLibre = useMapLibre
        self.styleURL = styleURL
    }

    private var showMapLibre: Bool { useMapLibre && !mapLibreFailed }

    public var body: some View {
        VStack(spacing: 0) {
            Group {
                if showMapLibre {
                    MapLibreTrackMap(
                        point: point,
                        styleURL: styleURL,
                        onInitFailed: { mapLibreFailed = true }
                    )
                } else if let point {
                    MapKitTrackMap(point: point)
                } else {
                    Color(uiColor: .secondarySystemBackground)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Text(trackMapCaption(showingMapLibre: showMapLibre))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
                .background(Color(uiColor: .secondarySystemBackground))
        }
    }
}

/// DEPRECATED MapKit single annotation — no trail.
struct MapKitTrackMap: View {
    var point: MapLatLng

    @State private var cameraPosition: MapCameraPosition

    init(point: MapLatLng) {
        self.point = point
        let coord = CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
        _cameraPosition = State(
            initialValue: .region(
                MKCoordinateRegion(
                    center: coord,
                    span: MKCoordinateSpan(latitudeDelta: 0.04, longitudeDelta: 0.04)
                )
            )
        )
    }

    var body: some View {
        Map(position: $cameraPosition) {
            Annotation("Driver", coordinate: CLLocationCoordinate2D(
                latitude: point.latitude,
                longitude: point.longitude
            )) {
                Image(systemName: "truck.box.fill")
                    .padding(8)
                    .background(Color.red, in: Circle())
                    .foregroundStyle(.white)
            }
        }
        .onChange(of: point.latitude) { _, _ in recenter() }
        .onChange(of: point.longitude) { _, _ in recenter() }
    }

    private func recenter() {
        let coord = CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
        cameraPosition = .region(
            MKCoordinateRegion(
                center: coord,
                span: MKCoordinateSpan(latitudeDelta: 0.04, longitudeDelta: 0.04)
            )
        )
    }
}
