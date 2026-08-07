import SwiftUI

/// My Garage — cascading maker → model → generation → engine via shared
/// `VehicleSelectorSection` / `VehicleCascade` over live `vehicle_master`.
struct GarageScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @State private var vehicles: [GarageVehicle] = []
    @State private var vehicleRows: [VehicleMasterRow] = []
    @State private var isPrimary = false
    @State private var status: String?
    @State private var vehicleError: String?
    @State private var busy = false
    @State private var catalogBusy = false
    @State private var formEpoch = 0

    private var formBusy: Bool { busy || catalogBusy }

    var body: some View {
        ShopDefaultScreen(title: "My Garage", subtitle: nil, scrollable: false) {
            List {
                Section("Add from catalog") {
                    VehicleSelectorSection(
                        vehicleRows: vehicleRows,
                        confirmedVehicle: nil,
                        busy: formBusy,
                        error: vehicleError,
                        sectionTitle: "Vehicle",
                        confirmLabel: "Save vehicle",
                        onConfirmCascade: { maker, model, generation, engine in
                            Task { await saveCascade(maker, model, generation, engine) }
                        },
                        onConfirmVin: { vin in
                            Task { await saveVin(vin) }
                        },
                        onClear: {
                            vehicleError = nil
                            status = nil
                        }
                    )
                    .id(formEpoch)
                    Toggle("Primary / sticky fitment", isOn: $isPrimary)
                        .disabled(formBusy)
                }

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
                            .disabled(formBusy)
                        }
                    }
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
            .task {
                await refresh()
                await loadVehicleCatalog()
            }
            .refreshable {
                await refresh()
                await loadVehicleCatalog()
            }
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

    private func loadVehicleCatalog() async {
        catalogBusy = true
        defer { catalogBusy = false }
        do {
            vehicleRows = try await session.api.listVehicleMaster()
            vehicleError = nil
        } catch {
            vehicleError = error.localizedDescription
        }
    }

    private func saveCascade(
        _ maker: String,
        _ model: String,
        _ generation: String,
        _ engine: String?
    ) async {
        guard let selected = VehicleCascade.fromCascade(
            maker: maker,
            model: model,
            generation: generation,
            engine: engine,
            rows: vehicleRows
        ) else {
            vehicleError = "Selection not found."
            return
        }
        await upsertSelected(selected)
    }

    private func saveVin(_ vin: String) async {
        guard let selected = VehicleCascade.resolveVin(vehicleRows, vinRaw: vin) else {
            vehicleError = "VIN not found."
            return
        }
        await upsertSelected(selected)
    }

    private func upsertSelected(_ selected: SelectedFitmentVehicle) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await session.api.upsertGarage(
                GarageVehicleInput(
                    make: selected.make,
                    model: selected.model,
                    generation: selected.generation,
                    engine: selected.engine,
                    vin: selected.vin,
                    isPrimary: isPrimary
                )
            )
            isPrimary = false
            formEpoch += 1
            vehicles = try await session.api.listGarage()
            vehicleError = nil
            status = "Vehicle saved"
        } catch {
            vehicleError = error.localizedDescription
        }
    }

    private func remove(_ id: UUID) async {
        busy = true
        defer { busy = false }
        do {
            try await session.api.deleteGarage(id: id)
            vehicles = try await session.api.listGarage()
            status = "Vehicle removed"
            vehicleError = nil
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
