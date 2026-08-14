import CoreLocation
import MapLibre
import SwiftUI
import UIKit

/// Live-delivery last-point only — MapLibre SoR (B-MAP-1 / H5-iOS).
/// Never draws a trail / polyline. Display only.
public struct MapLibreTrackMap: UIViewRepresentable {
    public var point: MapLatLng?
    public var styleURL: URL
    public var onInitFailed: (() -> Void)?

    public init(
        point: MapLatLng?,
        styleURL: URL = defaultMapLibreStyleURL,
        onInitFailed: (() -> Void)? = nil
    ) {
        self.point = point
        self.styleURL = styleURL
        self.onInitFailed = onInitFailed
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(onInitFailed: onInitFailed)
    }

    public func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero, styleURL: styleURL)
        mapView.delegate = context.coordinator
        mapView.compassView.isHidden = true
        mapView.showsUserLocation = false
        context.coordinator.mapView = mapView
        context.coordinator.sync(point: point, on: mapView, animated: false)
        return mapView
    }

    public func updateUIView(_ mapView: MLNMapView, context: Context) {
        context.coordinator.onInitFailed = onInitFailed
        if mapView.styleURL != styleURL {
            mapView.styleURL = styleURL
        }
        context.coordinator.sync(point: point, on: mapView, animated: true)
    }

    public final class Coordinator: NSObject, MLNMapViewDelegate {
        var onInitFailed: (() -> Void)?
        weak var mapView: MLNMapView?
        private var pin: MLNPointAnnotation?

        init(onInitFailed: (() -> Void)?) {
            self.onInitFailed = onInitFailed
        }

        func sync(point: MapLatLng?, on mapView: MLNMapView, animated: Bool) {
            if let existing = pin {
                mapView.removeAnnotation(existing)
                pin = nil
            }
            guard let point else { return }
            let annotation = MLNPointAnnotation()
            annotation.coordinate = CLLocationCoordinate2D(
                latitude: point.latitude,
                longitude: point.longitude
            )
            annotation.title = "Driver"
            mapView.addAnnotation(annotation)
            pin = annotation
            mapView.setCenter(
                annotation.coordinate,
                zoomLevel: max(mapView.zoomLevel, 14),
                animated: animated
            )
        }

        public func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
            onInitFailed?()
        }
    }
}
