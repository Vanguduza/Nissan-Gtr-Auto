import Foundation

/// Merchandising / shop category filter ↔ EPC `category_name`
/// (parity with web `@gtr/shared` `categoryMatchesFilter`).
public enum CatalogCategoryFilter {
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

    public static func needles(for filter: String) -> [String] {
        let raw = filter.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !raw.isEmpty else { return [] }
        var out = Set<String>()
        out.insert(raw)
        if let s = stem(raw) { out.insert(s) }
        if let aliased = aliases[raw] { aliased.forEach { out.insert($0) } }
        let parts = raw.split { !$0.isLetter && !$0.isNumber }.map(String.init)
        for part in parts where part.count >= 3 {
            out.insert(part)
            if let s = stem(part) { out.insert(s) }
            if let aliased = aliases[part] { aliased.forEach { out.insert($0) } }
        }
        return out.sorted { $0.count > $1.count }
    }

    public static func matches(filter: String, fields: String?...) -> Bool {
        let ns = needles(for: filter)
        guard !ns.isEmpty else { return false }
        let haystacks = fields.compactMap {
            $0?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        }.filter { !$0.isEmpty }
        guard !haystacks.isEmpty else { return false }
        for hay in haystacks {
            for needle in ns where hay == needle || hay.contains(needle) {
                return true
            }
        }
        return false
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
