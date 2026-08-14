import MapKit
import SwiftUI

/// Customer address map pick — Bridge-First MapsNav (B-MAP-1 / H5-iOS).
///
/// **MapLibre** is the render SoR (default). MapKit is a **deprecated fallback**
/// only when `useMapLibre` is false or MapLibre fails to load a style.
public struct AddressPickMap: View {
    @Binding public var latitude: Double?
    @Binding public var longitude: Double?

    public var useMapLibre: Bool
    public var styleURL: URL
    public var defaultCenter: MapLatLng

    @State private var mapLibreFailed = false

    public init(
        latitude: Binding<Double?>,
        longitude: Binding<Double?>,
        useMapLibre: Bool = true,
        styleURL: URL = defaultMapLibreStyleURL,
        defaultCenter: MapLatLng = defaultAddressPickCenter
    ) {
        _latitude = latitude
        _longitude = longitude
        self.useMapLibre = useMapLibre
        self.styleURL = styleURL
        self.defaultCenter = defaultCenter
    }

    private var selected: MapLatLng? {
        guard let latitude, let longitude else { return nil }
        return MapLatLng(latitude: latitude, longitude: longitude)
    }

    private var showMapLibre: Bool { useMapLibre && !mapLibreFailed }

    public var body: some View {
        VStack(spacing: 0) {
            Group {
                if showMapLibre {
                    MapLibreAddressPickMap(
                        selected: selected,
                        onPick: { pick in
                            latitude = pick.latitude
                            longitude = pick.longitude
                        },
                        defaultCenter: defaultCenter,
                        styleURL: styleURL,
                        onInitFailed: { mapLibreFailed = true }
                    )
                } else {
                    MapKitAddressPickMap(
                        latitude: $latitude,
                        longitude: $longitude,
                        defaultCenter: defaultCenter
                    )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            Text(addressPickMapCaption(showingMapLibre: showMapLibre))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 6)
                .background(Color(uiColor: .secondarySystemBackground))
        }
    }
}

/// DEPRECATED MapKit pin pick — Harare default; tap map to move pin.
struct MapKitAddressPickMap: View {
    @Binding var latitude: Double?
    @Binding var longitude: Double?
    var defaultCenter: MapLatLng

    @State private var position: MapCameraPosition
    @State private var pin: CLLocationCoordinate2D

    init(
        latitude: Binding<Double?>,
        longitude: Binding<Double?>,
        defaultCenter: MapLatLng
    ) {
        _latitude = latitude
        _longitude = longitude
        self.defaultCenter = defaultCenter
        let lat = latitude.wrappedValue ?? defaultCenter.latitude
        let lng = longitude.wrappedValue ?? defaultCenter.longitude
        let coord = CLLocationCoordinate2D(latitude: lat, longitude: lng)
        _pin = State(initialValue: coord)
        _position = State(
            initialValue: .region(
                MKCoordinateRegion(
                    center: coord,
                    span: MKCoordinateSpan(latitudeDelta: 0.04, longitudeDelta: 0.04)
                )
            )
        )
    }

    var body: some View {
        MapReader { proxy in
            Map(position: $position) {
                Marker("Delivery", coordinate: pin)
            }
            .mapStyle(.standard)
            .onTapGesture { screenPoint in
                if let coord = proxy.convert(screenPoint, from: .local) {
                    pin = coord
                    latitude = coord.latitude
                    longitude = coord.longitude
                }
            }
        }
        .onAppear {
            latitude = pin.latitude
            longitude = pin.longitude
        }
    }
}
