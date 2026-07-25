import MapKit
import SwiftUI

/// Customer active-delivery track — **last point + ETA only**.
///
/// Calls `get_delivery_track_point` (job JWT owner and/or share token).
/// Never selects `delivery_locations`, never draws a historical trail,
/// never uses WebView / HTML5 geolocation. Polling stops when the RPC
/// returns empty after an active point (job terminal / token expired).
struct DeliveryTrackScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    let ref: DeliveryTrackRef

    @State private var point: DeliveryTrackPoint?
    @State private var status: String?
    @State private var busy = false
    @State private var polling = true
    @State private var sawActivePoint = false
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
                    .accessibilityLabel(
                        "Driver last location \(String(format: "%.5f", point.lat)), \(String(format: "%.5f", point.lng))"
                    )
                }
            } else if busy && !sawActivePoint {
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
                    Task { await refresh(fromPoll: false) }
                }
                .disabled(busy)
                if !polling, sawActivePoint, point == nil {
                    Button("Resume polling") {
                        polling = true
                        Task { await pollLoop() }
                    }
                }
            }

            Section {
                Text(
                    polling
                        ? "Privacy: last point + ETA only. Polling every 15s while active."
                        : "Privacy: tracking stopped — job ended or link inactive. No historical GPS trail."
                )
                .font(.caption2)
                .foregroundStyle(.tertiary)
            }
        }
        .navigationTitle("Live delivery")
        .task {
            await refresh(fromPoll: false)
            await pollLoop()
        }
        .refreshable { await refresh(fromPoll: false) }
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

    private func refresh(fromPoll: Bool) async {
        if !fromPoll { busy = true }
        defer { if !fromPoll { busy = false } }
        do {
            let next = try await session.api.getDeliveryTrackPoint(ref)
            if let next {
                point = next
                sawActivePoint = true
                status = nil
                if !polling {
                    polling = true
                }
            } else {
                point = nil
                if sawActivePoint {
                    // Job went terminal / token revoked — stop stalking.
                    polling = false
                    status =
                        "Delivery is no longer out for delivery (completed, failed, or link expired). Live location has been cleared."
                } else {
                    status = nil
                }
            }
        } catch {
            status = error.localizedDescription
            // Keep prior point if refresh fails mid-poll; do not invent a trail.
        }
    }

    private func pollLoop() async {
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: pollNanos)
            if Task.isCancelled { break }
            guard polling else { break }
            await refresh(fromPoll: true)
        }
    }
}

#Preview {
    NavigationStack {
        DeliveryTrackScreen(ref: .token("demo-track-token"))
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
