import Foundation

/// Guest compare tray — UserDefaults OEM list (web `compare-selection` localStorage parity).
/// Auth users sync via `list_customer_compare_items` / add / remove RPCs.
@MainActor
public enum GuestCompareStore {
    private static let key = "gtr.compare.oems"

    public static func readOems() -> [String] {
        let raw = UserDefaults.standard.stringArray(forKey: key) ?? []
        return normalize(raw)
    }

    @discardableResult
    public static func writeOems(_ oems: [String]) -> [String] {
        let next = normalize(oems).prefix(maxCompareItems).map { $0 }
        UserDefaults.standard.set(Array(next), forKey: key)
        return Array(next)
    }

    public static func addOem(_ oem: String) throws -> [String] {
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else {
            throw StorefrontError.message("OEM required.")
        }
        var list = readOems()
        if list.contains(where: { $0.caseInsensitiveCompare(needle) == .orderedSame }) {
            return list
        }
        guard list.count < maxCompareItems else {
            throw StorefrontError.message("Compare holds up to \(maxCompareItems) SKUs. Remove one first.")
        }
        list.insert(needle, at: 0)
        return writeOems(list)
    }

    public static func removeOem(_ oem: String) -> [String] {
        let needle = oem.trimmingCharacters(in: .whitespacesAndNewlines)
        let next = readOems().filter { $0.caseInsensitiveCompare(needle) != .orderedSame }
        return writeOems(next)
    }

    public static func clear() {
        UserDefaults.standard.removeObject(forKey: key)
    }

    /// Map guest OEMs into `CompareItem` rows (synthetic ids) for matrix UI.
    public static func asCompareItems() -> [CompareItem] {
        readOems().enumerated().map { idx, oem in
            CompareItem(
                id: UUID(uuidString: String(format: "00000000-0000-4000-8000-%012d", idx + 1))
                    ?? UUID(),
                stockItemId: UUID(),
                oemPartNumber: oem,
                description: nil,
                createdAt: nil
            )
        }
    }

    private static func normalize(_ oems: [String]) -> [String] {
        var seen = Set<String>()
        var out: [String] = []
        for raw in oems {
            let oem = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !oem.isEmpty else { continue }
            let key = oem.lowercased()
            guard !seen.contains(key) else { continue }
            seen.insert(key)
            out.append(oem)
        }
        return out
    }
}
