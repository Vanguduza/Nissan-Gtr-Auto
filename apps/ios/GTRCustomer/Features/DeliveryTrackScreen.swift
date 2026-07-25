import MapKit
import SwiftUI

/// Customer active-delivery track — **last point + ETA only**.
///
/// Calls `get_delivery_track_point` (job JWT owner and/or share token).
/// Never selects `delivery_locations`, never draws a historical trail,
/// never uses WebView / HTML5 geolocation.
struct DeliveryTrackScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    let ref: DeliveryTrackRef

    @State private var point: DeliveryTrackPoint?
    @State private var status: String?
    @State private var busy = false
    @State private var cameraPosition: MapCameraPosition = .automatic

    private let pollNanos: UInt64 = 15_000_000_000

    var body: some View {
        List {
            Section {
                Text(
                    "Shows the driver’s last known location and ETA while the job is out for delivery. Full GPS history is never shared."
                )
                .font(.footnote)
                .foregroundStyle(.secondary)
            }

            if let point {
                Section("Live") {
                    LabeledContent("Status", value: statusLabel(point.status))
                    LabeledContent(
                        "ETA",
                        value: StorefrontFormat.etaLabel(
                            etaAt: point.etaAt,
                            etaSeconds: point.etaSeconds
                        ) ?? "Updating…"
                    )
                    LabeledContent(
                        "Last update",
                        value: StorefrontFormat.chatTime(point.recordedAt)
                    )
                    LabeledContent(
                        "Coordinates",
                        value: String(format: "%.5f, %.5f", point.lat, point.lng)
                    )
                }

                Section("Map") {
                    // Single annotation only — no polyline / trail overlays.
                    Map(position: $cameraPosition) {
                        Annotation("Driver", coordinate: CLLocationCoordinate2D(
                            latitude: point.lat,
                            longitude: point.lng
                        )) {
                            Image(systemName: "truck.box.fill")
                                .padding(8)
                                .background(.tint, in: Circle())
                                .foregroundStyle(.white)
                        }
                    }
                    .frame(height: 220)
                    .listRowInsets(EdgeInsets())
                    .onAppear { centerCamera(on: point) }
                }
            } else if busy {
                Section {
                    ProgressView("Loading live location…")
                }
            } else {
                Section {
                    Text(
                        status
                            ?? "Tracking is inactive, expired, or the delivery is not out for delivery yet. Last point is only available while a job is actively dispatched."
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }
            }

            if let status, point != nil {
                Section {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                Button(busy ? "Refreshing…" : "Refresh") {
                    Task { await refresh() }
                }
                .disabled(busy)
            }

            Section {
                Text("Privacy: last point + ETA only. No historical GPS trail.")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .navigationTitle("Live delivery")
        .task {
            await refresh()
            await pollLoop()
        }
        .refreshable { await refresh() }
        .onChange(of: point?.lat) { _, _ in
            if let point { centerCamera(on: point) }
        }
        .onChange(of: point?.lng) { _, _ in
            if let point { centerCamera(on: point) }
        }
    }

    private func statusLabel(_ raw: String) -> String {
        raw == "dispatched" ? "Out for delivery" : raw
    }

    private func centerCamera(on point: DeliveryTrackPoint) {
        let coord = CLLocationCoordinate2D(latitude: point.lat, longitude: point.lng)
        cameraPosition = .region(
            MKCoordinateRegion(
                center: coord,
                span: MKCoordinateSpan(latitudeDelta: 0.04, longitudeDelta: 0.04)
            )
        )
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            point = try await session.api.getDeliveryTrackPoint(ref)
            status = nil
        } catch {
            status = error.localizedDescription
            // Keep prior point if refresh fails mid-poll.
        }
    }

    private func pollLoop() async {
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: pollNanos)
            if Task.isCancelled { break }
            await refresh()
        }
    }
}

#Preview {
    NavigationStack {
        DeliveryTrackScreen(ref: .token("demo-track-token"))
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
