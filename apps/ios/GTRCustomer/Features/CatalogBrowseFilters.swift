import Foundation

/// Client-side filter + sort on browse rows (Android / web PLP parity).
struct ShopFilterState: Equatable {
    var minPrice: Float = 0
    var maxPrice: Float = 500
    var category: String?
}

enum ShopSortOption: String, CaseIterable, Identifiable {
    case relevance, priceAsc, priceDesc, nameAsc

    var id: String { rawValue }

    var label: String {
        switch self {
        case .relevance: return "Relevance"
        case .priceAsc: return "Price: low to high"
        case .priceDesc: return "Price: high to low"
        case .nameAsc: return "Name: A–Z"
        }
    }
}

func applyCatalogFilterSort(
    items: [CatalogListItem],
    filter: ShopFilterState,
    sort: ShopSortOption
) -> [CatalogListItem] {
    var out = items
    if filter.minPrice > 0 || filter.maxPrice < 500 {
        out = out.filter { item in
            guard let usd = item.usd else { return false }
            let price = NSDecimalNumber(decimal: usd).doubleValue
            return price >= Double(filter.minPrice) && price <= Double(filter.maxPrice)
        }
    }
    if let cat = filter.category?.trimmingCharacters(in: .whitespacesAndNewlines), !cat.isEmpty {
        out = out.filter { CatalogCategoryFilter.matches(filter: cat, fields: $0.category) }
    }
    switch sort {
    case .relevance:
        return out
    case .priceAsc:
        return out.sorted {
            NSDecimalNumber(decimal: $0.usd ?? 999_999).doubleValue
                < NSDecimalNumber(decimal: $1.usd ?? 999_999).doubleValue
        }
    case .priceDesc:
        return out.sorted {
            NSDecimalNumber(decimal: $0.usd ?? 0).doubleValue
                > NSDecimalNumber(decimal: $1.usd ?? 0).doubleValue
        }
    case .nameAsc:
        return out.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }
}
