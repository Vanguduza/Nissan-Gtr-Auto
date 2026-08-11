import Foundation

/// Merchandising / shop category filter ↔ EPC `category_name`
/// (parity with web `@gtr/shared` `catalog-category-filter`).
///
/// FILTERS → CATEGORY lists curated parents/leaves — never raw Megazip
/// `pnc_categories.category_name` assembly dumps.
public enum CatalogCategoryFilter {
    public struct MerchSub: Sendable {
        public let slug: String
        public let label: String
        public let stems: [String]
    }

    public struct MerchParent: Sendable {
        public let slug: String
        public let label: String
        public let subcategories: [MerchSub]
    }

    public struct FacetOption: Sendable {
        public let slug: String
        public let label: String
    }

    public struct ResolveResult: Sendable {
        public let parent: MerchParent
        public let sub: MerchSub?
    }

    public static let taxonomy: [MerchParent] = [
        MerchParent(slug: "brakes", label: "Brakes", subcategories: [
            MerchSub(slug: "brake-pads", label: "Brake pads", stems: ["pad", "lining"]),
            MerchSub(slug: "brake-discs", label: "Brake discs", stems: ["disc", "rotor"]),
            MerchSub(slug: "calipers", label: "Calipers", stems: ["caliper"]),
            MerchSub(slug: "brake-hoses", label: "Brake hoses & lines", stems: ["hose", "piping", "brake line", "brake tube"]),
            MerchSub(slug: "brake-fluid", label: "Brake fluid", stems: ["brake fluid"]),
        ]),
        MerchParent(slug: "filters", label: "Filters", subcategories: [
            MerchSub(slug: "oil-filters", label: "Oil filters", stems: ["oil filter"]),
            MerchSub(slug: "air-filters", label: "Air filters", stems: ["air filter", "air cleaner", "cleaner"]),
            MerchSub(slug: "cabin-filters", label: "Cabin filters", stems: ["cabin", "pollen", "cabin filter"]),
            MerchSub(slug: "fuel-filters", label: "Fuel filters", stems: ["fuel filter"]),
        ]),
        MerchParent(slug: "engine", label: "Engine", subcategories: [
            MerchSub(slug: "gaskets", label: "Gaskets", stems: ["gasket", "seal"]),
            MerchSub(slug: "timing", label: "Timing belts & chains", stems: ["timing", "cam belt", "timing chain"]),
            MerchSub(slug: "pulleys", label: "Pulleys", stems: ["pulley", "idler"]),
            MerchSub(slug: "engine-sensors", label: "Engine sensors", stems: ["sensor", "o2", "oxygen", "knock"]),
            MerchSub(slug: "spark-plugs", label: "Spark plugs", stems: ["spark", "glow plug"]),
        ]),
        MerchParent(slug: "suspension", label: "Suspension", subcategories: [
            MerchSub(slug: "shock-absorbers", label: "Shock absorbers", stems: ["shock", "strut", "damper", "absorber"]),
            MerchSub(slug: "coil-springs", label: "Coil springs", stems: ["coil spring", "spring"]),
            MerchSub(slug: "control-arms", label: "Control arms", stems: ["control arm", "wishbone", "lateral link", "trailing arm"]),
            MerchSub(slug: "ball-joints", label: "Ball joints", stems: ["ball joint", "ball-joint"]),
            MerchSub(slug: "tie-rod-ends", label: "Tie rod ends", stems: ["tie rod", "tie-rod", "outer socket", "inner socket"]),
            MerchSub(slug: "bushings", label: "Bushings", stems: ["bushing", "arm bush"]),
        ]),
        MerchParent(slug: "electrical", label: "Electrical", subcategories: [
            MerchSub(slug: "batteries", label: "Batteries", stems: ["battery"]),
            MerchSub(slug: "alternators", label: "Alternators", stems: ["alternator"]),
            MerchSub(slug: "starters", label: "Starters", stems: ["starter"]),
            MerchSub(slug: "ignition", label: "Ignition", stems: ["ignition", "coil", "distributor"]),
            MerchSub(slug: "wiring", label: "Wiring", stems: ["wiring", "harness"]),
        ]),
        MerchParent(slug: "cooling", label: "Cooling", subcategories: [
            MerchSub(slug: "radiators", label: "Radiators", stems: ["radiator"]),
            MerchSub(slug: "water-pumps", label: "Water pumps", stems: ["water pump"]),
            MerchSub(slug: "thermostats", label: "Thermostats", stems: ["thermostat"]),
            MerchSub(slug: "cooling-hoses", label: "Cooling hoses", stems: ["radiator hose", "coolant hose", "heater hose"]),
            MerchSub(slug: "heater", label: "Heater & A/C", stems: ["heater", "evaporator", "condenser", "a/c", "ac "]),
        ]),
        MerchParent(slug: "body", label: "Body", subcategories: [
            MerchSub(slug: "body-panels", label: "Body panels", stems: ["fender", "bonnet", "hood", "door panel", "quarter"]),
            MerchSub(slug: "bumpers", label: "Bumpers", stems: ["bumper"]),
            MerchSub(slug: "mirrors", label: "Mirrors", stems: ["mirror"]),
            MerchSub(slug: "exhaust", label: "Exhaust", stems: ["exhaust", "muffler", "silencer", "catalytic"]),
        ]),
        MerchParent(slug: "transmission", label: "Drivetrain", subcategories: [
            MerchSub(slug: "clutch", label: "Clutch kits", stems: ["clutch"]),
            MerchSub(slug: "flywheels", label: "Flywheels", stems: ["flywheel"]),
            MerchSub(slug: "gearbox-mounts", label: "Gearbox mounts", stems: ["mount", "transmission mount"]),
            MerchSub(slug: "driveshaft", label: "Driveshaft & CV", stems: ["driveshaft", "drive shaft", "cv joint", "axle"]),
            MerchSub(slug: "transfer", label: "Transfer & differential", stems: ["transfer", "differential", "diff "]),
        ]),
    ]

    private static let aliases: [String: [String]] = [
        "brakes": ["brake"],
        "braking": ["brake"],
        "filters": ["filter", "cleaner"],
        "engine": ["engine"],
        "engine parts": ["engine"],
        "cooling": ["cool", "radiator", "thermostat"],
        "cooling & heating": ["cool", "radiator", "heater", "heating"],
        "suspension": ["suspension", "strut"],
        "steering & suspension": ["steering", "suspension", "strut"],
        "electrical": ["electric", "wiring"],
        "body": ["body", "bumper"],
        "body & exhaust": ["body", "exhaust", "bumper"],
        "transmission": ["transmission", "clutch", "transfer"],
        "drivetrain": ["transmission", "drivetrain", "transfer", "power train"],
        "fuel system": ["fuel"],
        "lighting": ["lamp", "light", "headlamp"],
        "service parts": ["filter", "oil", "spark", "service"],
    ]

    private static let parentSynonyms: [String: String] = [
        "braking": "brakes",
        "drivetrain": "transmission",
        "engine parts": "engine",
        "cooling & heating": "cooling",
        "steering & suspension": "suspension",
        "body & exhaust": "body",
    ]

    public static func stripEpcVehicleSuffix(_ name: String) -> String {
        let s = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return s }
        if let match = s.range(of: #"^(.+?)\s+for\s+\d"#, options: [.regularExpression, .caseInsensitive]) {
            let prefix = String(s[match])
            if let m = prefix.range(of: #"^(.+?)\s+for\s+\d"#, options: [.regularExpression, .caseInsensitive]),
               let regex = try? NSRegularExpression(pattern: #"^(.+?)\s+for\s+\d"#, options: .caseInsensitive),
               let result = regex.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)),
               let r = Range(result.range(at: 1), in: s) {
                return String(s[r]).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        if let regex = try? NSRegularExpression(
            pattern: #"^(.+?)\s+for\s+(?:the\s+)?(?:\d{4}|nissan|toyota|honda|suzuki|subaru|mitsubishi|lexus)\b"#,
            options: .caseInsensitive
        ),
           let result = regex.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)),
           let r = Range(result.range(at: 1), in: s) {
            return String(s[r]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let regex = try? NSRegularExpression(pattern: #"^(.+?)\s+FOR\s+"#),
           let result = regex.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)),
           let r = Range(result.range(at: 1), in: s) {
            let stem = String(s[r])
            if stem.contains(where: { $0.isUppercase }) {
                return stem.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return s
    }

    public static func resolve(_ filter: String) -> ResolveResult? {
        let raw = filter.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty else { return nil }
        let key = normKey(raw)
        let slug = slugKey(raw)

        for parent in taxonomy {
            for sub in parent.subcategories {
                if sub.slug == slug || normKey(sub.label) == key || slugKey(sub.label) == slug {
                    return ResolveResult(parent: parent, sub: sub)
                }
            }
        }
        for parent in taxonomy {
            if parent.slug == slug || normKey(parent.label) == key || slugKey(parent.label) == slug {
                return ResolveResult(parent: parent, sub: nil)
            }
        }
        if let mapped = parentSynonyms[key] ?? parentSynonyms[slug],
           let parent = taxonomy.first(where: { $0.slug == mapped }) {
            return ResolveResult(parent: parent, sub: nil)
        }
        return nil
    }

    public static func facetOptions(activeFilter: String?, activeSubfilter: String? = nil) -> [FacetOption] {
        let parentFilter = activeFilter?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if parentFilter.isEmpty, (activeSubfilter?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "").isEmpty {
            return taxonomy.map { FacetOption(slug: $0.slug, label: $0.label) }
        }
        let resolved = resolve(parentFilter.isEmpty ? (activeSubfilter ?? "") : parentFilter)
            ?? activeSubfilter.flatMap { resolve($0) }
        guard let resolved else { return [] }
        return resolved.parent.subcategories.map { FacetOption(slug: $0.slug, label: $0.label) }
    }

    public static func facetLabels(activeFilter: String?, activeSubfilter: String? = nil) -> [String] {
        facetOptions(activeFilter: activeFilter, activeSubfilter: activeSubfilter).map(\.label)
    }

    public static func needles(for filter: String) -> [String] {
        let raw = filter.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !raw.isEmpty else { return [] }
        var out = Set<String>()
        if let resolved = resolve(filter), let sub = resolved.sub {
            collectNeedles(normKey(sub.label), extra: sub.stems, into: &out)
            collectNeedles(sub.slug.replacingOccurrences(of: "-", with: " "), extra: [], into: &out)
        } else if let resolved = resolve(filter) {
            collectNeedles(normKey(resolved.parent.label), extra: [], into: &out)
            collectNeedles(resolved.parent.slug, extra: [], into: &out)
            aliases[resolved.parent.slug]?.forEach { out.insert($0) }
            aliases[raw]?.forEach { out.insert($0) }
        } else {
            collectNeedles(raw, extra: [], into: &out)
        }
        return out.sorted { $0.count > $1.count }
    }

    public static func matches(filter: String, fields: String?...) -> Bool {
        let ns = needles(for: filter)
        guard !ns.isEmpty else { return false }
        let haystacks = fields.compactMap { field -> String? in
            guard let trimmed = field?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty
            else { return nil }
            let stripped = stripEpcVehicleSuffix(trimmed)
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()
            return stripped.isEmpty ? nil : stripped
        }
        guard !haystacks.isEmpty else { return false }
        for hay in haystacks {
            for needle in ns where hay == needle || hay.contains(needle) {
                return true
            }
        }
        return false
    }

    private static func collectNeedles(_ raw: String, extra: [String], into out: inout Set<String>) {
        out.insert(raw)
        if let s = stem(raw) { out.insert(s) }
        aliases[raw]?.forEach { out.insert($0) }
        let parts = raw.split { !$0.isLetter && !$0.isNumber }.map(String.init)
        for part in parts where part.count >= 3 {
            out.insert(part)
            if let s = stem(part) { out.insert(s) }
            aliases[part]?.forEach { out.insert($0) }
        }
        for e in extra {
            let n = e.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            guard !n.isEmpty else { continue }
            out.insert(n)
            if let s = stem(n) { out.insert(s) }
        }
    }

    private static func normKey(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    }

    private static func slugKey(_ raw: String) -> String {
        raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "&", with: " and ")
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
    }

    private static func stem(_ raw: String) -> String? {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard t.count >= 4 else { return nil }
        let stemmed: String
        if t.hasSuffix("ies"), t.count > 4 {
            stemmed = String(t.dropLast(3)) + "y"
        } else if t.hasSuffix("ses"), t.count > 4 {
            stemmed = String(t.dropLast(2))
        } else if t.hasSuffix("s"), !t.hasSuffix("ss") {
            stemmed = String(t.dropLast())
        } else {
            stemmed = t
        }
        return stemmed.count >= 3 ? stemmed : nil
    }
}
