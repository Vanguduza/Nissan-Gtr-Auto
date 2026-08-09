import Foundation

/// Chassis codes in vehicle_master/fixtures without catalog_variants — map to published EPC.
/// Keep in sync with packages/shared catalog-chassis-alias.ts.
public enum CatalogChassisAlias {
    private static let aliases: [String: String] = [
        "D40": "D22",
        "T32": "T31",
    ]

    /// Ordered unique codes to try (requested first, then alias).
    public static func lookupCodes(_ chassis: String) -> [String] {
        let primary = chassis.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !primary.isEmpty else { return [] }
        var out = [primary]
        if let alias = aliases[primary]?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
           !alias.isEmpty, alias != primary
        {
            out.append(alias)
        }
        return out
    }
}
