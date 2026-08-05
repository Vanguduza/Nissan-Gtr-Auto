import SwiftUI

/// Home / Categories / Newest / PDP — inline typeahead stays on Home (no discarded search page).
struct CatalogScreen: View {
    @EnvironmentObject private var session: StorefrontSession
    @Binding var initialOem: String?
    @Binding var categorySeed: String?
    @Binding var categorySeedToken: UUID
    var onCartChanged: (() -> Void)? = nil

    private enum Route: Equatable {
        case home
        case categories
        case newest
        case categoryPlp
        case product
        case pdpReviews
    }

    @State private var route: Route = .home
    @State private var query = ""
    @State private var browseItems: [CatalogListItem] = []
    @State private var activeCategory: String?
    @State private var product: CatalogProduct?
    @State private var selectedGalleryKey = ""
    @State private var selectedGalleryIndex = 0
    @State private var filterState = ShopFilterState()
    @State private var sortOption = ShopSortOption.relevance
    @State private var facetChips: [(String, String)] = []
    @State private var searchBackend: String?
    @State private var qty = "1"
    @State private var busy = false
    @State private var status: String?
    @State private var error: String?
    @State private var suggestions: [SearchSuggestion] = []
    @State private var showSuggestions = false
    @State private var searchTask: Task<Void, Never>?
    @State private var routeBeforeProduct: Route = .home
    @State private var reviewStats: ProductReviewStats?

    private let homeBanners = [
        "Genuine Nissan parts · Harare counter & nationwide dispatch",
        "Click & Collect same day at the Harare counter",
        "ZiG settlement available at checkout",
    ]

    private let categories = [
        "Brakes", "Filters", "Engine", "Suspension", "Electrical", "Cooling", "Body", "Drivetrain",
    ]

    init(
        initialOem: Binding<String?>,
        categorySeed: Binding<String?> = .constant(nil),
        categorySeedToken: Binding<UUID> = .constant(UUID()),
        onCartChanged: (() -> Void)? = nil
    ) {
        _initialOem = initialOem
        _categorySeed = categorySeed
        _categorySeedToken = categorySeedToken
        self.onCartChanged = onCartChanged
    }

    var body: some View {
        Group {
            switch route {
            case .home:
                homeBody
            case .categories:
                categoriesBody
            case .newest:
                newestBody
            case .categoryPlp:
                categoryPlpBody
            case .product:
                if let product {
                    productBody(product)
                } else {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background(GTRColors.chalk)
                }
            case .pdpReviews:
                if let product {
                    PdpReviewsScreen(
                        oem: product.oem,
                        stockItemId: product.stockItemId,
                        onBack: { route = .product }
                    )
                }
            }
        }
        .background(GTRColors.chalk.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await refreshBrowse()
            await session.refreshWishlist()
        }
        .task(id: initialOem) {
            guard let oem = initialOem?.trimmingCharacters(in: .whitespacesAndNewlines), !oem.isEmpty else {
                return
            }
            await openProduct(oem)
            initialOem = nil
        }
        .task(id: categorySeedToken) {
            guard let seed = categorySeed?.trimmingCharacters(in: .whitespacesAndNewlines), !seed.isEmpty else {
                return
            }
            await applyCategoryFilter(seed)
            categorySeed = nil
        }
        .onChange(of: query) { _, newValue in
            scheduleSuggestions(for: newValue)
        }
    }

    // MARK: - Home

    private var homeBody: some View {
        ShopTabBody {
            VStack(alignment: .leading, spacing: 8) {
                inlineSearchField
                if showSuggestions && query.trimmingCharacters(in: .whitespacesAndNewlines).count >= 2 {
                    suggestionPopup
                }
                if let activeCategory {
                    Button("Filtered: \(activeCategory) · Clear") {
                        Task { await applyCategoryFilter(nil) }
                    }
                    .font(GTRType.label(.caption))
                    .foregroundStyle(GTRColors.primary)
                }
            }

            ShopBannerCarousel(banners: homeBanners)

            ShopMerchTitleRow(
                title: "Categories",
                actionLabel: "See all",
                onAction: { route = .categories }
            )
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 4) {
                    ForEach(categories, id: \.self) { cat in
                        ShopCategoryBox(label: cat) {
                            Task { await applyCategoryFilter(cat) }
                        }
                    }
                }
            }

            productRail(
                title: "Newest products",
                items: newestItems,
                action: { route = .newest }
            )

            productRail(
                title: "Most sale",
                items: popularItems,
                action: { route = .newest }
            )

            if let error {
                Text(error)
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.primary)
            }
            if let status {
                Text(status)
                    .font(GTRType.body(.footnote))
                    .foregroundStyle(GTRColors.silverDim)
            }
        }
    }

    private var inlineSearchField: some View {
        ShopSearchBar(placeholder: "OEM, VIN, model, or PNC", text: $query)
    }

    private var suggestionPopup: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let searchBackend {
                Text("Search via \(searchBackend == "meili" ? "Meili" : "catalog FTS")")
                    .font(GTRType.label(.caption2))
                    .foregroundStyle(GTRColors.silverDim)
                    .padding(.horizontal, 12)
                    .padding(.top, 6)
            }
            if !facetChips.isEmpty {
                Text("Facets")
                    .font(GTRType.label(.caption))
                    .foregroundStyle(GTRColors.silverDim)
                    .padding(.horizontal, 12)
                    .padding(.top, 4)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(Array(facetChips.enumerated()), id: \.offset) { _, chip in
                            Button(chip.1) {
                                Task { await selectSuggestion(SearchSuggestion(kind: .category, title: chip.1, filterQuery: chip.1)) }
                            }
                            .buttonStyle(.bordered)
                            .tint(GTRColors.primary)
                            .font(GTRType.label(.caption2))
                        }
                    }
                    .padding(.horizontal, 8)
                }
                Divider().overlay(GTRColors.mist)
            }
            if suggestions.isEmpty && !busy {
                Text("No matches")
                    .font(GTRType.body(.caption))
                    .foregroundStyle(GTRColors.silverDim)
                    .padding(12)
            }
            ForEach(SuggestKind.allCases, id: \.self) { kind in
                let rows = suggestions.filter { $0.kind == kind }
                if !rows.isEmpty {
                    Text(kind.label)
                        .font(GTRType.label(.caption))
                        .foregroundStyle(GTRColors.silverDim)
                        .padding(.horizontal, 12)
                        .padding(.top, 8)
                    ForEach(rows) { row in
                        Button {
                            Task { await selectSuggestion(row) }
                        } label: {
                            HStack {
                                Image(systemName: kind.systemImage)
                                    .foregroundStyle(GTRColors.primary)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(row.title)
                                        .font(GTRType.body(.callout))
                                        .foregroundStyle(GTRColors.steel)
                                    if let sub = row.subtitle {
                                        Text(sub)
                                            .font(GTRType.body(.caption2))
                                            .foregroundStyle(GTRColors.silverDim)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                        Divider().overlay(GTRColors.mist)
                    }
                }
            }
        }
        .background(
            RoundedRectangle(cornerRadius: GTRRadius.sharp)
                .stroke(GTRColors.mist, lineWidth: 1)
                .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
        )
        .frame(maxHeight: 280)
    }

    // MARK: - Categories / Newest

    private var categoriesBody: some View {
        ShopDefaultScreen(title: "Categories", subtitle: "Browse by type", onBack: { route = .home }) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(categories, id: \.self) { cat in
                    Button {
                        Task { await applyCategoryFilter(cat) }
                    } label: {
                        VStack(spacing: 12) {
                            RoundedRectangle(cornerRadius: GTRRadius.control)
                                .fill(GTRColors.mist)
                                .frame(height: 100)
                                .overlay {
                                    Image(systemName: categorySymbol(cat))
                                        .font(.system(size: 36))
                                        .foregroundStyle(GTRColors.steel)
                                }
                            Text(cat)
                                .font(GTRType.displaySemi(.headline))
                                .foregroundStyle(GTRColors.steel)
                        }
                        .padding(8)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.control))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
    }

    private var newestBody: some View {
        ShopDefaultScreen(
            title: "Newest products",
            subtitle: "Recently added parts",
            onBack: { route = .home },
            scrollable: true
        ) {
            if browseItems.isEmpty {
                ShopHonestEmpty(
                    title: "No recent parts yet",
                    bodyText: "Newest products appear here from catalog browse when stock is indexed."
                )
            } else {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    ForEach(browseItems) { item in
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
                            onTap: { Task { await openProduct(item.oem) } }
                        )
                    }
                }
                .padding(8)
            }
        }
    }

    @ViewBuilder
    private func productRail(
        title: String,
        items: [CatalogListItem],
        action: @escaping () -> Void
    ) -> some View {
        ShopMerchTitleRow(title: title, actionLabel: "See all", onAction: action)
        if items.isEmpty {
            ShopHonestEmpty(
                title: "No parts yet",
                bodyText: "Browse loads from stock_items when Live; Fake seeds Filters demo SKUs."
            )
        } else {
            ShopHorizontalRail(items: items) { item in
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
                    onTap: { Task { await openProduct(item.oem) } }
                )
            }
        }
    }

    private var newestItems: [CatalogListItem] {
        Array(browseItems.prefix(8))
    }

    private var popularItems: [CatalogListItem] {
        let tail = Array(browseItems.dropFirst(8).prefix(8))
        return tail.isEmpty ? newestItems : tail
    }

    private var categoryPlpBody: some View {
        CategoryPlpScreen(
            title: activeCategory ?? "Browse",
            products: browseItems,
            categoryLabels: browseCategoryLabels,
            busy: busy,
            filterState: $filterState,
            sortOption: $sortOption,
            onBack: { route = .home },
            onOpenProduct: { oem in Task { await openProduct(oem) } }
        )
    }

    private var browseCategoryLabels: [String] {
        let fromItems = browseItems.compactMap(\.category)
        let merged = Array(Set(categories + fromItems)).sorted()
        return merged.isEmpty ? categories : merged
    }

    // MARK: - PDP

    @ViewBuilder
    private func productBody(_ product: CatalogProduct) -> some View {
        let galleryUrls = product.imageUrls.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        let gallery = galleryKeys(for: product)
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ZStack(alignment: .top) {
                        RoundedRectangle(cornerRadius: 0)
                            .fill(GTRColors.mist)
                            .frame(height: 320)
                            .overlay {
                                ShopProductGalleryHero(
                                    imageUrls: galleryUrls,
                                    heroLabel: product.oem,
                                    selectedIndex: selectedGalleryIndex
                                )
                            }

                        HStack {
                            Button {
                                route = routeBeforeProduct
                                self.product = nil
                            } label: {
                                Image(systemName: "chevron.left")
                                    .font(.body.bold())
                                    .foregroundStyle(GTRColors.steel)
                                    .padding(10)
                                    .background(GTRColors.chalk.opacity(0.9), in: Circle())
                            }
                            Spacer()
                            Button {
                                Task {
                                    try? await session.toggleWishlist(
                                        oem: product.oem,
                                        stockItemId: product.stockItemId
                                    )
                                }
                            } label: {
                                Image(systemName: session.isLiked(oem: product.oem) ? "heart.fill" : "heart")
                                    .font(.body.bold())
                                    .foregroundStyle(GTRColors.primary)
                                    .padding(10)
                                    .background(GTRColors.chalk.opacity(0.9), in: Circle())
                            }
                            .disabled(busy)
                        }
                        .padding(16)

                        VStack {
                            Spacer()
                            ShopImageGalleryStrip(
                                items: gallery,
                                imageUrls: galleryUrls,
                                selected: selectedGalleryKey.isEmpty ? (galleryUrls.isEmpty ? gallery.first ?? product.oem : "img-0") : selectedGalleryKey,
                                onSelect: { key in
                                    selectedGalleryKey = key
                                    if key.hasPrefix("img-"), let idx = Int(key.dropFirst(4)) {
                                        selectedGalleryIndex = idx
                                    }
                                }
                            )
                            .padding(.horizontal, 16)
                            .padding(.bottom, 12)
                        }
                    }

                    VStack(alignment: .leading, spacing: 16) {
                        HStack {
                            Text(product.brand ?? "Nissan OEM")
                                .font(GTRType.body(.subheadline))
                                .foregroundStyle(GTRColors.silverDim)
                            Spacer()
                            if let reviewStats, reviewStats.reviewCount > 0 {
                                ShopRatingRow(
                                    ratingLabel: String(
                                        format: "%.1f · %d",
                                        reviewStats.avgRating,
                                        reviewStats.reviewCount
                                    )
                                )
                            } else {
                                ShopRatingRow(ratingLabel: "No reviews yet")
                            }
                            Button("Reviews") { route = .pdpReviews }
                                .font(GTRType.label(.caption))
                        }

                        Text(product.name)
                            .font(GTRType.display(.title2))
                            .foregroundStyle(GTRColors.steel)

                        HStack {
                            Text(product.usd.map { StorefrontFormat.money($0, currency: .USD) } ?? "Price on request")
                                .font(GTRType.displaySemi(.title2))
                                .foregroundStyle(GTRColors.accent)
                            Spacer()
                            ShopStatusChip(
                                text: product.stock.label,
                                tone: product.stock == .inStock ? .success : (product.stock == .low ? .warning : .danger)
                            )
                        }

                        if product.coreCharge > 0 {
                            Text("Core charge \(StorefrontFormat.money(product.coreCharge, currency: .USD)) — refunded on return of old unit")
                                .font(GTRType.body(.caption))
                                .foregroundStyle(GTRColors.silverDim)
                        }

                        ShopMerchTitleRow(title: "Product details", actionLabel: nil)
                        ShopExpandableDescription(text: product.descriptionText)

                        ShopMerchTitleRow(title: "Reviews", actionLabel: "See all")
                        Button {
                            route = .pdpReviews
                        } label: {
                            Text(
                                reviewStats.map {
                                    $0.reviewCount == 0
                                        ? "No reviews yet · Write the first"
                                        : String(format: "%.1f average · %d review(s) · Write review", $0.avgRating, $0.reviewCount)
                                } ?? "No reviews yet · Write the first"
                            )
                            .font(GTRType.body(.subheadline))
                            .foregroundStyle(GTRColors.silverDim)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)

                        if !product.fitmentLines.isEmpty {
                            ShopMerchTitleRow(title: "Fitment vs garage", actionLabel: nil)
                            ForEach(product.fitmentLines, id: \.self) { line in
                                Text(line)
                                    .font(GTRType.body(.subheadline))
                                    .foregroundStyle(GTRColors.steel)
                            }
                        }

                        ShopMerchTitleRow(title: "Qty", actionLabel: nil)
                        TextField("Qty", text: $qty)
                            .keyboardType(.decimalPad)
                            .font(GTRType.body())
                            .padding(10)
                            .background(
                                RoundedRectangle(cornerRadius: GTRRadius.sharp)
                                    .stroke(GTRColors.mist, lineWidth: 1)
                                    .background(Color.white, in: RoundedRectangle(cornerRadius: GTRRadius.sharp))
                            )
                            .disabled(busy)

                        if let error {
                            Text(error)
                                .font(GTRType.body(.footnote))
                                .foregroundStyle(GTRColors.primary)
                        }
                        if let status {
                            Text(status)
                                .font(GTRType.body(.footnote))
                                .foregroundStyle(GTRColors.silverDim)
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 88)
                }
            }
            ShopPdpBuyBar(
                priceLabel: product.usd.map { StorefrontFormat.money($0, currency: .USD) } ?? "POR",
                ctaTitle: "Add to cart",
                enabled: !busy,
                onCta: { Task { await addToCart() } }
            )
        }
        .background(GTRColors.chalk.ignoresSafeArea())
        .onAppear {
            selectedGalleryKey = product.imageUrls.isEmpty ? galleryKeys(for: product).first ?? product.oem : "img-0"
            selectedGalleryIndex = 0
        }
    }

    // MARK: - Actions

    private func galleryKeys(for product: CatalogProduct) -> [String] {
        var keys = [product.oem]
        keys.append(contentsOf: product.fitmentLines.prefix(4).map { String($0.prefix(8)) })
        while keys.count < 4 {
            keys.append("\(product.oem)-\(keys.count)")
        }
        return Array(keys.prefix(6))
    }

    private func categorySymbol(_ name: String) -> String {
        let n = name.lowercased()
        if n.contains("brake") { return "circle.dashed" }
        if n.contains("filter") { return "line.3.horizontal.decrease" }
        if n.contains("engine") { return "wrench.and.screwdriver" }
        if n.contains("suspension") { return "gearshape" }
        if n.contains("electric") { return "bolt.fill" }
        if n.contains("cool") { return "thermometer" }
        if n.contains("body") { return "car.fill" }
        return "wrench.and.screwdriver"
    }

    private func refreshBrowse(category: String? = nil) async {
        busy = true
        defer { busy = false }
        do {
            let result = try await session.api.listCatalogBrowse(category: category, limit: 50)
            browseItems = result.items
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func applyCategoryFilter(_ category: String?) async {
        activeCategory = category?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        filterState.category = activeCategory
        route = .categoryPlp
        await refreshBrowse(category: activeCategory)
    }

    private func openProduct(_ oem: String) async {
        busy = true
        defer { busy = false }
        do {
            routeBeforeProduct = route == .product ? .home : route
            product = try await session.api.loadCatalogProduct(oem: oem)
            reviewStats = try? await session.api.getProductReviewStats(
                stockItemId: product?.stockItemId,
                oem: oem
            )
            route = .product
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func addToCart() async {
        guard let product else { return }
        guard let q = Decimal(string: qty.trimmingCharacters(in: .whitespacesAndNewlines)), q > 0 else {
            error = "Qty must be > 0"
            return
        }
        busy = true
        defer { busy = false }
        do {
            let result = try await session.api.addCartLineByOem(oem: product.oem, qty: q)
            status = "Added · cart \(result.cartId.uuidString.prefix(8))… · line \(result.lineId.uuidString.prefix(8))…"
            error = nil
            onCartChanged?()
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func scheduleSuggestions(for raw: String) {
        searchTask?.cancel()
        let q = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard q.count >= 2 else {
            suggestions = []
            showSuggestions = false
            return
        }
        showSuggestions = true
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            let hits = await fetchSuggestions(query: q)
            guard !Task.isCancelled else { return }
            suggestions = hits
        }
    }

    private func selectSuggestion(_ row: SearchSuggestion) async {
        showSuggestions = false
        suggestions = []
        query = row.title
        if let oem = row.oem {
            await openProduct(oem)
        } else {
            await applyCategoryFilter(row.filterQuery ?? row.title)
        }
    }

    private func fetchSuggestions(query: String) async -> [SearchSuggestion] {
        var out: [String: SearchSuggestion] = [:]
        func put(_ s: SearchSuggestion) {
            let key = "\(s.kind.rawValue):\(s.title.uppercased()):\(s.oem ?? "")"
            if out[key] == nil { out[key] = s }
        }
        func absorb(_ hits: [CatalogPartHit], asParts: Bool) {
            for hit in hits.prefix(8) {
                if asParts {
                    put(SearchSuggestion(
                        kind: .part,
                        title: hit.oemPartNumber,
                        subtitle: [hit.categoryName, hit.pncCode].compactMap { $0 }.joined(separator: " · ").nilIfEmpty,
                        oem: hit.oemPartNumber
                    ))
                }
                if let cat = hit.categoryName?.trimmingCharacters(in: .whitespacesAndNewlines), !cat.isEmpty {
                    put(SearchSuggestion(kind: .category, title: cat, subtitle: hit.subcategoryName, filterQuery: cat))
                }
                if let pnc = hit.pncCode?.trimmingCharacters(in: .whitespacesAndNewlines), !pnc.isEmpty {
                    put(SearchSuggestion(kind: .category, title: pnc, subtitle: "PNC", filterQuery: pnc))
                }
                for model in [hit.chassisCode, hit.engineCode].compactMap({ $0 }) {
                    let m = model.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !m.isEmpty {
                        put(SearchSuggestion(kind: .model, title: m, subtitle: hit.oemPartNumber, filterQuery: m))
                    }
                }
            }
        }
        if let part = try? await session.api.searchCatalog(mode: .part, query: query) {
            absorb(part.parts, asParts: true)
        }
        if let model = try? await session.api.searchCatalog(mode: .model, query: query) {
            absorb(model.parts, asParts: false)
        }
        if let pnc = try? await session.api.searchCatalog(mode: .pnc, query: query) {
            absorb(pnc.parts, asParts: false)
        }
        let vinLike = query.count >= 11 && query.count <= 17 && query.allSatisfy(\.isLetterOrNumber)
        if vinLike, let vin = try? await session.api.searchCatalog(mode: .vin, query: query) {
            absorb(vin.parts, asParts: true)
        }
        return Array(out.values.prefix(24))
    }
}

// MARK: - Suggestion models

private enum SuggestKind: String, CaseIterable {
    case category, model, part
    var label: String {
        switch self {
        case .category: return "Category / type"
        case .model: return "Model"
        case .part: return "Part number"
        }
    }
    var systemImage: String {
        switch self {
        case .category: return "square.grid.2x2"
        case .model: return "car"
        case .part: return "number"
        }
    }
}

private struct SearchSuggestion: Identifiable {
    let kind: SuggestKind
    let title: String
    var subtitle: String? = nil
    var oem: String? = nil
    var filterQuery: String? = nil
    var id: String { "\(kind.rawValue)-\(title)-\(oem ?? "")" }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

private extension Character {
    var isLetterOrNumber: Bool { isLetter || isNumber }
}

#Preview {
    NavigationStack {
        CatalogScreen(initialOem: .constant(nil))
    }
    .environmentObject(StorefrontSession(api: FakeStorefrontApi()))
}
