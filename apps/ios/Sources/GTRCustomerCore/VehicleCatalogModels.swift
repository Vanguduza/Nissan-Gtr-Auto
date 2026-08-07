import Foundation

/// Live `vehicle_master` row — cascade source for maker → model → generation → engine.
public struct VehicleMasterRow: Identifiable, Sendable, Equatable {
    public var id: String?
    public var vinPrefix: String?
    public var chassisCode: String
    public var engineCode: String?
    public var productionYear: Int?
    public var modelVariant: String

    public var stableId: String {
        id ?? "\(modelVariant)|\(chassisCode)|\(engineCode ?? "")|\(vinPrefix ?? "")"
    }

    public init(
        id: String? = nil,
        vinPrefix: String? = nil,
        chassisCode: String,
        engineCode: String? = nil,
        productionYear: Int? = nil,
        modelVariant: String
    ) {
        self.id = id
        self.vinPrefix = vinPrefix
        self.chassisCode = chassisCode
        self.engineCode = engineCode
        self.productionYear = productionYear
        self.modelVariant = modelVariant
    }
}

/// Session fitment after Select vehicle confirm.
public struct SelectedFitmentVehicle: Sendable, Equatable {
    public var make: String?
    public var model: String
    public var generation: String
    public var engine: String?
    public var vin: String?
    public var vinPrefix: String?

    public init(
        make: String?,
        model: String,
        generation: String,
        engine: String?,
        vin: String? = nil,
        vinPrefix: String? = nil
    ) {
        self.make = make
        self.model = model
        self.generation = generation
        self.engine = engine
        self.vin = vin
        self.vinPrefix = vinPrefix
    }

    /// Compact bar — model · chassis · engine (no maker / Nissan tag).
    public var compactLabel: String {
        let parts = [model, generation, engine ?? ""].map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        if !parts.isEmpty { return parts.joined(separator: " · ") }
        if let vin, !vin.isEmpty { return "VIN \(vin)" }
        return ""
    }
}

public enum VehicleCascade {
    public static func deriveMaker(_ row: VehicleMasterRow) -> String? {
        let variant = row.modelVariant.trimmingCharacters(in: .whitespaces)
        if variant.uppercased().hasPrefix("DATSUN") { return "Datsun" }
        if variant.lowercased().hasPrefix("nissan") { return "Nissan" }
        if variant.lowercased().hasPrefix("infiniti") { return "Infiniti" }
        let vp = (row.vinPrefix ?? "").trimmingCharacters(in: .whitespaces).uppercased()
        if vp.hasPrefix("JNK") { return "Infiniti" }
        if vp.hasPrefix("JN") { return "Nissan" }
        return nil
    }

    public static func makers(_ rows: [VehicleMasterRow]) -> [String] {
        Array(Set(rows.compactMap { deriveMaker($0) })).sorted()
    }

    public static func models(_ rows: [VehicleMasterRow], maker: String) -> [String] {
        Array(Set(rows.filter { deriveMaker($0) == maker }.map {
            $0.modelVariant.trimmingCharacters(in: .whitespaces)
        }.filter { !$0.isEmpty })).sorted()
    }

    public static func generations(_ rows: [VehicleMasterRow], maker: String, model: String) -> [String] {
        Array(Set(rows.filter {
            deriveMaker($0) == maker && $0.modelVariant.trimmingCharacters(in: .whitespaces) == model
        }.map { $0.chassisCode.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty })).sorted()
    }

    public static func engines(
        _ rows: [VehicleMasterRow],
        maker: String,
        model: String,
        generation: String
    ) -> [String] {
        Array(Set(rows.filter {
            deriveMaker($0) == maker
                && $0.modelVariant.trimmingCharacters(in: .whitespaces) == model
                && $0.chassisCode.trimmingCharacters(in: .whitespaces) == generation
        }.compactMap {
            $0.engineCode?.trimmingCharacters(in: .whitespaces)
        }.filter { !$0.isEmpty })).sorted()
    }

    public static func resolveVin(_ rows: [VehicleMasterRow], vinRaw: String) -> SelectedFitmentVehicle? {
        let vin = vinRaw.trimmingCharacters(in: .whitespaces).uppercased()
        guard vin.count >= 11 else { return nil }
        let needle = String(vin.prefix(11))
        guard let match = rows.first(where: { row in
            let vp = (row.vinPrefix ?? "").trimmingCharacters(in: .whitespaces).uppercased()
            guard !vp.isEmpty else { return false }
            return needle.hasPrefix(vp) || vp.hasPrefix(String(needle.prefix(vp.count)))
        }) else { return nil }
        return SelectedFitmentVehicle(
            make: deriveMaker(match),
            model: match.modelVariant.trimmingCharacters(in: .whitespaces),
            generation: match.chassisCode.trimmingCharacters(in: .whitespaces),
            engine: match.engineCode?.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            vin: vin,
            vinPrefix: match.vinPrefix
        )
    }

    public static func fromCascade(
        maker: String,
        model: String,
        generation: String,
        engine: String?,
        rows: [VehicleMasterRow]
    ) -> SelectedFitmentVehicle? {
        guard let row = rows.first(where: {
            deriveMaker($0) == maker
                && $0.modelVariant.trimmingCharacters(in: .whitespaces) == model
                && $0.chassisCode.trimmingCharacters(in: .whitespaces) == generation
                && (engine == nil || engine!.isEmpty || $0.engineCode?.trimmingCharacters(in: .whitespaces) == engine)
        }) else { return nil }
        return SelectedFitmentVehicle(
            make: maker,
            model: model,
            generation: generation,
            engine: (engine?.nilIfEmpty) ?? row.engineCode?.trimmingCharacters(in: .whitespaces).nilIfEmpty,
            vinPrefix: row.vinPrefix
        )
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespaces)
        return t.isEmpty ? nil : t
    }
}
