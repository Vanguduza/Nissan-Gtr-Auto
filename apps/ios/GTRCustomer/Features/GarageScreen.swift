import SwiftUI

/// My Garage — upsert / delete via customer AuthZ RPCs.
struct GarageScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var vehicles: [GarageVehicle] = []
    @State private var make = "Nissan"
    @State private var model = ""
    @State private var vin = ""
    @State private var status: String?
    @State private var busy = false

    var body: some View {
        ShopDefaultScreen(title: "My Garage", subtitle: "VIN · vehicles", scrollable: false) {
            List {
            Section("Saved") {
                if vehicles.isEmpty {
                    Text("No vehicles")
                        .font(GTRType.body(.subheadline))
                        .foregroundStyle(GTRColors.silverDim)
                }
                ForEach(vehicles) { v in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(v.label).font(GTRType.displaySemi(.headline))
                            if v.isPrimary {
                                Text("Primary")
                                    .font(GTRType.label(.caption))
                                    .foregroundStyle(GTRColors.silverDim)
                            }
                            if let vin = v.vin, !vin.isEmpty {
                                Text(vin)
                                    .font(GTRType.label(.caption))
                                    .monospaced()
                            }
                        }
                        Spacer()
                        Button(role: .destructive) {
                            Task { await remove(v.id) }
                        } label: {
                            Image(systemName: "trash")
                        }
                        .disabled(busy)
                    }
                }
            }

            Section("Add vehicle") {
                TextField("Make", text: $make)
                TextField("Model", text: $model)
                TextField("VIN (optional)", text: $vin)
                    .textInputAutocapitalization(.characters)
                Button("Save") { Task { await save() } }
                    .disabled(busy || (make.isEmpty && model.isEmpty && vin.isEmpty))
            }

            if let status {
                Section {
                    Text(status)
                        .font(GTRType.body(.footnote))
                        .foregroundStyle(GTRColors.silverDim)
                }
            }
            }
            .scrollContentBackground(.hidden)
            .background(GTRColors.chalk)
            .navigationBarTitleDisplayMode(.inline)
            .task { await refresh() }
            .refreshable { await refresh() }
        }
    }

    private func refresh() async {
        busy = true
        defer { busy = false }
        do {
            vehicles = try await session.api.listGarage()
            status = nil
        } catch {
            status = error.localizedDescription
        }
    }

    private func save() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await session.api.upsertGarage(
                GarageVehicleInput(
                    make: make.isEmpty ? nil : make,
                    model: model.isEmpty ? nil : model,
                    vin: vin.isEmpty ? nil : vin,
                    isPrimary: vehicles.isEmpty
                )
            )
            model = ""
            vin = ""
            vehicles = try await session.api.listGarage()
            status = "Saved via upsert_customer_garage_vehicle"
        } catch {
            status = error.localizedDescription
        }
    }

    private func remove(_ id: UUID) async {
        busy = true
        defer { busy = false }
        do {
            try await session.api.deleteGarage(id: id)
            vehicles = try await session.api.listGarage()
            status = "Deleted via delete_customer_garage_vehicle"
        } catch {
            status = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        GarageScreen()
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
