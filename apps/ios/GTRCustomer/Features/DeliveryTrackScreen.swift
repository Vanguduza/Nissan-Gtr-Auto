import MapsNav
import SwiftUI

/// Customer active-delivery track — **last point + ETA only**.
///
/// Calls `get_delivery_track_point` (job JWT owner and/or share token).
/// Never selects `delivery_locations`, never draws a historical trail,
/// never uses WebView / HTML5 geolocation. Polling stops when the RPC
/// returns empty after an active point (job terminal / token expired).
///
/// Map render SoR: MapLibre (`TrackPointMap`); MapKit = deprecated fallback only.
struct DeliveryTrackScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    let ref: DeliveryTrackRef

    @State private var point: DeliveryTrackPoint?
    @State private var status: String?
    @State private var busy = false
    @State private var polling = true
    @State private var sawActivePoint = false

    private let pollNanos: UInt64 = 15_000_000_000

    private var styleURL: URL {
        let raw = AppEnv.mapLibreStyleURL.trimmingCharacters(in: .whitespacesAndNewlines)
        if let url = URL(string: raw), !raw.isEmpty {
            return url
        }
        return defaultMapLibreStyleURL
    }

    var body: some View {
        ShopDefaultScreen(title: "Live delivery", subtitle: nil, scrollable: false) {
            List {
            Section {
                Text(
                    "Shows the driver's last known location and ETA while the job is out for delivery. Full GPS history is never shared."
                )
                .font(GTRType.body(.footnote))
                .foregroundStyle(GTRColors.silverDim)
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
                    TrackPointMap(
                        point: MapLatLng(latitude: point.lat, longitude: point.lng),
                        useMapLibre: AppEnv.useMapLibre,
                        styleURL: styleURL
                    )
                    .frame(height: 220)
                    .listRowInsets(EdgeInsets())
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
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.silverDim)
                }
            }

            if let status, point != nil {
                Section {
                    Text(status)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
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
                Text(polling ? "Tracking…" : "Tracking stopped")
                .font(GTRType.label(.caption2))
                .foregroundStyle(GTRColors.silverDim)
            }
            }
            .scrollContentBackground(.hidden)
            .background(GTRColors.chalk)
            .navigationBarTitleDisplayMode(.inline)
            .task {
                await refresh(fromPoll: false)
                await pollLoop()
            }
            .refreshable { await refresh(fromPoll: false) }
        }
    }

    private func statusLabel(_ raw: String) -> String {
        raw == "dispatched" ? "Out for delivery" : raw
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
