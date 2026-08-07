import SwiftUI

/// Category PLP — live browse with filter/sort sheets (ShopKit parity).
struct CategoryPlpScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    let title: String
    let products: [CatalogListItem]
    let categoryLabels: [String]
    let busy: Bool
    @Binding var filterState: ShopFilterState
    @Binding var sortOption: ShopSortOption
    var onBack: () -> Void
    var onOpenProduct: (String) -> Void

    @State private var showFilter = false
    @State private var showSort = false

    private var filtered: [CatalogListItem] {
        applyCatalogFilterSort(items: products, filter: filterState, sort: sortOption)
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(GTRColors.primary)
                }
                VStack(alignment: .leading) {
                    Text(title)
                        .font(GTRType.displaySemi(.title2))
                        .foregroundStyle(GTRColors.steel)
                    Text("\(filtered.count) part(s)")
                        .font(GTRType.body(.caption))
                        .foregroundStyle(GTRColors.silverDim)
                }
                Spacer()
                if busy { ProgressView() }
            }
            .padding(16)

            ShopFilterSortBar(
                sortLabel: sortOption.label,
                onFilter: { showFilter = true },
                onSort: { showSort = true }
            )
            .padding(.horizontal, 16)

            if filtered.isEmpty && !busy {
                ShopHonestEmpty(
                    title: "No parts in this category",
                    bodyText: "No items."
                )
                .padding(24)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        ForEach(filtered) { item in
                            ShopProductCard(
                                title: item.oem,
                                subtitle: item.name,
                                priceLabel: item.usd.map { StorefrontFormat.money($0, currency: .USD) } ?? "POR",
                                stockLabel: item.stock.label,
                                ratingLabel: "—",
                                liked: session.isLiked(oem: item.oem),
                                onLike: {
                                    Task {
                                        try? await session.toggleWishlist(
                                            oem: item.oem,
                                            stockItemId: item.stockItemId
                                        )
                                    }
                                },
                                onTap: { onOpenProduct(item.oem) }
                            )
                        }
                    }
                    .padding(8)
                }
            }
        }
        .background(GTRColors.chalk.ignoresSafeArea())
        .sheet(isPresented: $showFilter) {
            ShopFilterSheet(
                state: filterState,
                categories: categoryLabels,
                onApply: { filterState = $0; showFilter = false },
                onCancel: { showFilter = false }
            )
        }
        .sheet(isPresented: $showSort) {
            ShopSortSheet(
                selected: sortOption,
                onSelect: { sortOption = $0; showSort = false }
            )
        }
    }
}
