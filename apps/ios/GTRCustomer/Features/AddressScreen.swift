import MapKit
import SwiftUI

/// Shipping addresses — list / upsert / delete + MapKit pick (lat/lng in line2 via AddressGeo).
struct AddressScreen: View {
    @EnvironmentObject private var session: StorefrontSession

    private enum Route {
        case list
        case edit
    }

    @State private var route: Route = .list
    @State private var addresses: [CustomerAddress] = []
    @State private var form = AddressFormState()
    @State private var busy = false
    @State private var message: String?
    @State private var error: String?
    @State private var showMapPick = false

    var body: some View {
        ShopDefaultScreen(
            title: route == .edit ? "Edit address" : "Addresses",
            subtitle: nil,
            onBack: route == .edit ? addressEditBack : nil,
            scrollable: false
        ) {
            Group {
                switch route {
                case .list:
                    listBody
                case .edit:
                    editBody
                }
            }
        }
        .task { await refresh() }
        .sheet(isPresented: $showMapPick) {
            NavigationStack {
                AddressMapPickSheet(
                    latitude: Binding(
                        get: { form.latitude },
                        set: { form.latitude = $0 }
                    ),
                    longitude: Binding(
                        get: { form.longitude },
                        set: { form.longitude = $0 }
                    )
                )
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showMapPick = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Use pin") { showMapPick = false }
                    }
                }
            }
            .shopTheme()
        }
    }

    private var addressEditBack: () -> Void {
        {
            route = .list
            form = AddressFormState()
            error = nil
        }
    }

    private var listBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Map pick stores lat/lng with the address (MapKit — Bridge-First, no WebView).")
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.silverDim)

                if let error {
                    Text(error)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.primary)
                }
                if let message {
                    Text(message)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }

                ShopSectionHeader(title: "Saved addresses")

                if addresses.isEmpty {
                    ShopHonestEmpty(
                        title: "No addresses yet",
                        bodyText: "No addresses."
                    )
                } else {
                    ForEach(addresses) { addr in
                        ShopListCard {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(addr.label.isEmpty ? "Address" : addr.label)
                                        .font(GTRType.displaySemi(.headline))
                                    if addr.isDefault {
                                        ShopStatusChip(text: "Default", tone: .success)
                                    }
                                    Spacer()
                                }
                                Text(addr.line1)
                                    .font(GTRType.body(.subheadline))
                                    .foregroundStyle(GTRColors.silverDim)
                                if let city = addr.city {
                                    Text([city, addr.province].compactMap { $0 }.joined(separator: ", "))
                                        .font(GTRType.body(.caption))
                                        .foregroundStyle(GTRColors.silverDim)
                                }
                                if let geo = addr.geoLatLng {
                                    Text(String(format: "%.5f, %.5f", geo.lat, geo.lng))
                                        .font(GTRType.label(.caption2))
                                        .foregroundStyle(GTRColors.accent)
                                        .monospaced()
                                }
                                HStack {
                                    Button("Edit") { openEdit(addr) }
                                        .disabled(busy)
                                    Button("Delete", role: .destructive) {
                                        Task { await delete(addr.id) }
                                    }
                                    .disabled(busy)
                                }
                                .font(GTRType.label(.caption))
                            }
                        }
                    }
                }

                ShopPrimaryButton(title: "Add address", enabled: !busy) {
                    openNew()
                }
                Button("Refresh") { Task { await refresh() } }
                    .disabled(busy)
                    .frame(maxWidth: .infinity)
            }
            .padding(16)
        }
        .background(GTRColors.chalk)
    }

    private var editBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.primary)
                }

                field("Label", text: $form.label)
                field("Street / area (line1) *", text: $form.line1)
                field("Line 2", text: $form.line2)
                field("City", text: $form.city)
                field("Province", text: $form.province)
                field("Postal code", text: $form.postalCode)
                field("Country", text: $form.country)

                Toggle("Default shipping address", isOn: $form.isDefault)
                    .font(GTRType.body(.subheadline))

                ShopSectionHeader(title: "Map pick")
                Text(
                    form.hasCoords
                        ? String(format: "Pin · %.5f, %.5f", form.latitude!, form.longitude!)
                        : "No pin yet — tap map to set delivery coordinates"
                )
                .font(GTRType.body(.caption))
                .foregroundStyle(GTRColors.silverDim)

                Button {
                    showMapPick = true
                } label: {
                    Label("Open MapKit picker", systemImage: "map")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.bordered)
                .tint(GTRColors.steel)

                if form.hasCoords {
                    Button("Clear pin") {
                        form.latitude = nil
                        form.longitude = nil
                    }
                    .font(GTRType.label(.caption))
                }

                ShopPrimaryButton(title: "Save address", enabled: !busy) {
                    Task { await save() }
                }
                Button("Cancel") {
                    route = .list
                    form = AddressFormState()
                    error = nil
                }
                .disabled(busy)
                .frame(maxWidth: .infinity)
            }
            .padding(16)
        }
        .background(GTRColors.chalk)
    }

    private func field(_ title: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(GTRType.label(.caption))
                .foregroundStyle(GTRColors.silverDim)
            TextField(title, text: text)
                .font(GTRType.body())
                .padding(10)
                .background(
                    RoundedRectangle(cornerRadius: GTRRadius.sharp)
                        .stroke(GTRColors.mist, lineWidth: 1)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
                )
        }
    }

    private func openNew() {
        form = AddressFormState(isDefault: addresses.isEmpty)
        route = .edit
        message = nil
        error = nil
    }

    private func openEdit(_ address: CustomerAddress) {
        let geo = address.geoLatLng
        form = AddressFormState(
            id: address.id,
            label: address.label,
            line1: address.line1,
            line2: AddressGeo.strip(address.line2) ?? "",
            city: address.city ?? "",
            province: address.province ?? "",
            postalCode: address.postalCode ?? "",
            country: address.country,
            isDefault: address.isDefault,
            latitude: geo?.lat,
            longitude: geo?.lng
        )
        route = .edit
        message = nil
        error = nil
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            addresses = try await session.api.listOwnAddresses()
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func save() async {
        let line1 = form.line1.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !line1.isEmpty else {
            error = "Street / area (line1) is required"
            return
        }
        busy = true
        defer { busy = false }
        do {
            let id = try await session.api.upsertCustomerAddress(
                CustomerAddressInput(
                    id: form.id,
                    label: form.label,
                    line1: line1,
                    line2: form.line2.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                    city: form.city.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                    province: form.province.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                    postalCode: form.postalCode.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                    country: form.country.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? "Zimbabwe"
                        : form.country,
                    isDefault: form.isDefault,
                    latitude: form.latitude,
                    longitude: form.longitude
                )
            )
            addresses = try await session.api.listOwnAddresses()
            route = .list
            form = AddressFormState()
            message = "Address saved"
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func delete(_ id: UUID) async {
        busy = true
        defer { busy = false }
        do {
            try await session.api.deleteCustomerAddress(id: id)
            addresses = try await session.api.listOwnAddresses()
            message = "delete_customer_address ok"
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }
}

private struct AddressFormState {
    var id: UUID?
    var label: String = ""
    var line1: String = ""
    var line2: String = ""
    var city: String = "Harare"
    var province: String = "Harare"
    var postalCode: String = ""
    var country: String = "Zimbabwe"
    var isDefault: Bool = false
    var latitude: Double?
    var longitude: Double?

    var hasCoords: Bool { latitude != nil && longitude != nil }
}

/// Full-screen MapKit pin pick — Harare default; tap map to move pin.
struct AddressMapPickSheet: View {
    @Binding var latitude: Double?
    @Binding var longitude: Double?

    @State private var position: MapCameraPosition
    @State private var pin: CLLocationCoordinate2D

    init(latitude: Binding<Double?>, longitude: Binding<Double?>) {
        _latitude = latitude
        _longitude = longitude
        let lat = latitude.wrappedValue ?? -17.8292
        let lng = longitude.wrappedValue ?? 31.0522
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
        VStack(spacing: 0) {
            MapReader { proxy in
                Map(position: $position) {
                    Marker("Delivery", coordinate: pin)
                        .tint(GTRColors.primary)
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
            .ignoresSafeArea(edges: .bottom)

            Text(String(format: "%.5f, %.5f — tap map to move pin", pin.latitude, pin.longitude))
                .font(GTRType.label(.caption))
                .foregroundStyle(GTRColors.steel)
                .frame(maxWidth: .infinity)
                .padding(12)
                .background(GTRColors.mist)
        }
        .navigationTitle("Pick on map")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            latitude = pin.latitude
            longitude = pin.longitude
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}

#Preview {
    NavigationStack {
        AddressScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
