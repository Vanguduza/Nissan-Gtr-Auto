import SwiftUI

struct VehicleSelectorSection: View {
    let vehicleRows: [VehicleMasterRow]
    var confirmedVehicle: SelectedFitmentVehicle?
    var busy: Bool
    var error: String?
    var sectionTitle: String = "Select vehicle"
    var confirmLabel: String = "Confirm vehicle"
    var showClear: Bool = true
    var onConfirmCascade: (String, String, String, String?) -> Void
    var onConfirmVin: (String) -> Void
    var onClear: () -> Void

    @State private var maker = ""
    @State private var model = ""
    @State private var generation = ""
    @State private var engine = ""
    @State private var vin = ""

    private var makers: [String] { VehicleCascade.makers(vehicleRows) }
    private var models: [String] {
        maker.isEmpty ? [] : VehicleCascade.models(vehicleRows, maker: maker)
    }
    private var generations: [String] {
        maker.isEmpty || model.isEmpty
            ? []
            : VehicleCascade.generations(vehicleRows, maker: maker, model: model)
    }
    private var engines: [String] {
        maker.isEmpty || model.isEmpty || generation.isEmpty
            ? []
            : VehicleCascade.engines(vehicleRows, maker: maker, model: model, generation: generation)
    }

    private var canCascade: Bool {
        !maker.isEmpty && !model.isEmpty && !generation.isEmpty
            && (engines.isEmpty || !engine.isEmpty)
    }

    private var canVin: Bool {
        let n = vin.trimmingCharacters(in: .whitespacesAndNewlines).count
        return (11...17).contains(n)
    }

    private var showingConfirmed: Bool {
        guard let c = confirmedVehicle else { return false }
        return maker == (c.make ?? "")
            && model == c.model
            && generation == c.generation
            && engine == (c.engine ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(sectionTitle)
                .font(GTRType.displaySemi(.subheadline))
                .foregroundStyle(GTRColors.steel)

            HStack(spacing: 8) {
                cascadePicker("Maker", selection: $maker, options: makers, enabled: !makers.isEmpty && !busy) {
                    model = ""; generation = ""; engine = ""
                }
                cascadePicker("Model", selection: $model, options: models, enabled: !maker.isEmpty && !busy) {
                    generation = ""; engine = ""
                }
            }
            HStack(spacing: 8) {
                cascadePicker("Generation", selection: $generation, options: generations, enabled: !model.isEmpty && !busy) {
                    engine = ""
                }
                cascadePicker(
                    "Engine",
                    selection: $engine,
                    options: engines,
                    enabled: !generation.isEmpty && !engines.isEmpty && !busy
                )
            }

            TextField("VIN / chassis", text: $vin)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .disabled(busy)
                .onChange(of: vin) { _, newValue in
                    let filtered = String(newValue.uppercased().filter { $0.isLetter || $0.isNumber }.prefix(17))
                    if filtered != newValue { vin = filtered }
                }
                .padding(10)
                .background(Color.white)
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(GTRColors.mist))
                .foregroundStyle(showingConfirmed ? GTRColors.steel.opacity(0.45) : GTRColors.steel)

            HStack(spacing: 8) {
                Button {
                    if canVin {
                        onConfirmVin(vin.trimmingCharacters(in: .whitespacesAndNewlines))
                    } else if canCascade {
                        onConfirmCascade(maker, model, generation, engine.isEmpty ? nil : engine)
                    }
                } label: {
                    Text(busy ? "Working…" : confirmLabel)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(GTRColors.primary)
                .disabled(busy || !(canVin || canCascade))

                if showClear {
                    Button("Clear") {
                        maker = ""; model = ""; generation = ""; engine = ""; vin = ""
                        onClear()
                    }
                    .disabled(busy)
                }
            }

            if let error {
                Text(error)
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.primary)
            }
        }
        .onChange(of: confirmedVehicle) { _, v in
            guard let v else { return }
            maker = v.make ?? ""
            model = v.model
            generation = v.generation
            engine = v.engine ?? ""
            vin = v.vin ?? ""
        }
        .onAppear {
            if let v = confirmedVehicle {
                maker = v.make ?? ""
                model = v.model
                generation = v.generation
                engine = v.engine ?? ""
                vin = v.vin ?? ""
            }
        }
    }

    @ViewBuilder
    private func cascadePicker(
        _ title: String,
        selection: Binding<String>,
        options: [String],
        enabled: Bool,
        onChange: (() -> Void)? = nil
    ) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(GTRType.label(.caption2))
                .foregroundStyle(GTRColors.silverDim)
            Picker(title, selection: selection) {
                Text(title).tag("")
                ForEach(options, id: \.self) { opt in
                    Text(opt).tag(opt)
                }
            }
            .pickerStyle(.menu)
            .disabled(!enabled)
            .foregroundStyle(showingConfirmed ? GTRColors.steel.opacity(0.45) : GTRColors.steel)
            .onChange(of: selection.wrappedValue) { _, _ in
                onChange?()
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(Color.white)
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(GTRColors.mist))
        .opacity(enabled || showingConfirmed ? 1 : 0.55)
    }
}