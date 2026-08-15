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

/// Parity with `apps/web/lib/vehicle-catalog.ts` VehicleCascade.
public enum VehicleCascade {
    /// Brand labels matched at the start of `model_variant` (longest first).
    private static let variantBrandPrefixes: [String] = [
        "Mercedes-Benz", "Land Rover", "Alfa Romeo", "Volkswagen", "Infiniti", "Datsun",
        "Nissan", "Toyota", "Lexus", "Honda", "Acura", "Mazda", "Mitsubishi", "Subaru",
        "Suzuki", "Daihatsu", "Isuzu", "Hino", "Hyundai", "Kia", "Genesis", "BMW", "Mini",
        "Smart", "Audi", "Skoda", "Seat", "Porsche", "Ford", "Lincoln", "Chevrolet",
        "Cadillac", "Buick", "GMC", "Jeep", "Dodge", "Chrysler", "Ram", "Volvo", "Jaguar",
        "Peugeot", "Citroen", "Renault", "Opel", "Fiat",
    ].sorted { $0.count > $1.count }

    /// VIN WMI → maker (longest prefix first). Includes regional Nissan WMIs (SJN/MNT/…).
    private static let vinWmiMakers: [(String, String)] = [
        ("JNK", "Infiniti"), ("5N3", "Infiniti"),
        ("SJN", "Nissan"), ("MNT", "Nissan"), ("MDH", "Nissan"), ("VSK", "Nissan"),
        ("ADN", "Nissan"), ("3N1", "Nissan"), ("5N1", "Nissan"), ("1N4", "Nissan"),
        ("1N6", "Nissan"), ("JN1", "Nissan"), ("JN", "Nissan"),
        ("JTD", "Toyota"), ("JT2", "Toyota"), ("JTE", "Toyota"), ("JTM", "Toyota"),
        ("4T1", "Toyota"), ("5TD", "Toyota"), ("2T1", "Toyota"), ("MR0", "Toyota"),
        ("JTJ", "Lexus"), ("JTH", "Lexus"), ("2T2", "Lexus"), ("58A", "Lexus"),
        ("JHM", "Honda"), ("1HG", "Honda"), ("2HG", "Honda"), ("3CZ", "Honda"), ("SHH", "Honda"),
        ("JH4", "Acura"), ("19U", "Acura"), ("2HN", "Acura"),
        ("JM1", "Mazda"), ("JM3", "Mazda"), ("1YV", "Mazda"), ("3MZ", "Mazda"),
        ("JA3", "Mitsubishi"), ("JA4", "Mitsubishi"), ("4A3", "Mitsubishi"), ("6MM", "Mitsubishi"),
        ("JF1", "Subaru"), ("JF2", "Subaru"), ("4S3", "Subaru"), ("4S4", "Subaru"),
        ("JS2", "Suzuki"), ("JS3", "Suzuki"), ("JSA", "Suzuki"), ("TSM", "Suzuki"),
        ("KMH", "Hyundai"), ("KM8", "Hyundai"), ("5NP", "Hyundai"), ("5NM", "Hyundai"),
        ("KNA", "Kia"), ("KND", "Kia"), ("5XY", "Kia"), ("3KP", "Kia"),
        ("WBA", "BMW"), ("WBS", "BMW"), ("WBY", "BMW"), ("4US", "BMW"), ("5UX", "BMW"),
        ("WDD", "Mercedes-Benz"), ("WDB", "Mercedes-Benz"), ("4JG", "Mercedes-Benz"),
        ("WAU", "Audi"), ("WA1", "Audi"),
        ("WVW", "Volkswagen"), ("WV1", "Volkswagen"), ("WV2", "Volkswagen"),
        ("3VW", "Volkswagen"), ("1VW", "Volkswagen"),
        ("1FA", "Ford"), ("1FT", "Ford"), ("1FM", "Ford"), ("WF0", "Ford"),
        ("SAL", "Land Rover"), ("SAJ", "Jaguar"),
    ].sorted { $0.0.count > $1.0.count }

    private static let nissanModelToken = try! NSRegularExpression(
        pattern: #"^(MICRA|QASHQAI\+?\d*|JUKE|NAVARA|X-?TRAIL|PULSAR|PATROL|ALTIMA|SENTRA|MAXIMA|LEAF|370Z|350Z|GT-?R|SKYLINE|ALMERA|TIIDA|TEANA|PATHFINDER|MURANO|NP300|HARDBODY|CARAVAN|SYLPHY|PRIMERA|NOTE|CUBE)\b"#,
        options: [.caseInsensitive]
    )

    public static func deriveMaker(_ row: VehicleMasterRow) -> String? {
        let variant = row.modelVariant.trimmingCharacters(in: .whitespaces)
        let upper = variant.uppercased()
        for brand in variantBrandPrefixes {
            if upper.hasPrefix(brand.uppercased()) { return brand }
        }
        let vp = (row.vinPrefix ?? "").trimmingCharacters(in: .whitespaces).uppercased()
        if !vp.isEmpty {
            for (prefix, maker) in vinWmiMakers {
                if vp.hasPrefix(prefix) { return maker }
            }
        }
        let range = NSRange(variant.startIndex..<variant.endIndex, in: variant)
        if nissanModelToken.firstMatch(in: variant, options: [], range: range) != nil {
            return "Nissan"
        }
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
