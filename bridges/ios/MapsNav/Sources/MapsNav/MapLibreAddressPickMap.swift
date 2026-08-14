import CoreLocation
import MapLibre
import SwiftUI
import UIKit

/// Customer address map pick — MapLibre render SoR (B-MAP-1 / H5-iOS).
/// Tap to set pin. Display only; no GPS ingest (use `LocationTracker` if needed).
public struct MapLibreAddressPickMap: UIViewRepresentable {
    public var selected: MapLatLng?
    public var onPick: (MapLatLng) -> Void
    public var defaultCenter: MapLatLng
    public var styleURL: URL
    public var onInitFailed: (() -> Void)?

    public init(
        selected: MapLatLng?,
        onPick: @escaping (MapLatLng) -> Void,
        defaultCenter: MapLatLng = defaultAddressPickCenter,
        styleURL: URL = defaultMapLibreStyleURL,
        onInitFailed: (() -> Void)? = nil
    ) {
        self.selected = selected
        self.onPick = onPick
        self.defaultCenter = defaultCenter
        self.styleURL = styleURL
        self.onInitFailed = onInitFailed
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(onPick: onPick, onInitFailed: onInitFailed)
    }

    public func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero, styleURL: styleURL)
        mapView.delegate = context.coordinator
        mapView.logoView.isHidden = false
        mapView.attributionButton.isHidden = false
        mapView.compassView.isHidden = true
        mapView.showsUserLocation = false

        let center = selected ?? defaultCenter
        mapView.setCenter(
            CLLocationCoordinate2D(latitude: center.latitude, longitude: center.longitude),
            zoomLevel: 14,
            animated: false
        )

        let tap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap(_:))
        )
        tap.numberOfTapsRequired = 1
        // Let double-tap zoom wait for our single-tap to fail (MapLibre default gestures).
        for recognizer in mapView.gestureRecognizers ?? [] {
            if let existing = recognizer as? UITapGestureRecognizer, existing.numberOfTapsRequired == 2 {
                existing.require(toFail: tap)
            }
        }
        mapView.addGestureRecognizer(tap)
        context.coordinator.mapView = mapView
        context.coordinator.syncPin(selected: selected, on: mapView)
        return mapView
    }

    public func updateUIView(_ mapView: MLNMapView, context: Context) {
        context.coordinator.onPick = onPick
        context.coordinator.onInitFailed = onInitFailed
        if mapView.styleURL != styleURL {
            mapView.styleURL = styleURL
        }
        context.coordinator.syncPin(selected: selected, on: mapView)
        if let selected {
            mapView.setCenter(
                CLLocationCoordinate2D(latitude: selected.latitude, longitude: selected.longitude),
                zoomLevel: max(mapView.zoomLevel, 14),
                animated: true
            )
        }
    }

    public final class Coordinator: NSObject, MLNMapViewDelegate {
        var onPick: (MapLatLng) -> Void
        var onInitFailed: (() -> Void)?
        weak var mapView: MLNMapView?
        private var pin: MLNPointAnnotation?

        init(onPick: @escaping (MapLatLng) -> Void, onInitFailed: (() -> Void)?) {
            self.onPick = onPick
            self.onInitFailed = onInitFailed
        }

        @objc func handleTap(_ gesture: UITapGestureRecognizer) {
            guard let mapView, gesture.state == .ended else { return }
            let point = gesture.location(in: mapView)
            let coord = mapView.convert(point, toCoordinateFrom: mapView)
            onPick(MapLatLng(latitude: coord.latitude, longitude: coord.longitude))
        }

        func syncPin(selected: MapLatLng?, on mapView: MLNMapView) {
            if let existing = pin {
                mapView.removeAnnotation(existing)
                pin = nil
            }
            guard let selected else { return }
            let annotation = MLNPointAnnotation()
            annotation.coordinate = CLLocationCoordinate2D(
                latitude: selected.latitude,
                longitude: selected.longitude
            )
            annotation.title = "Delivery"
            mapView.addAnnotation(annotation)
            pin = annotation
        }

        public func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
            onInitFailed?()
        }
    }
}
