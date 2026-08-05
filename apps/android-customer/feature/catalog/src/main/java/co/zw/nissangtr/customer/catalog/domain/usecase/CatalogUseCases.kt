package co.zw.nissangtr.customer.catalog.domain.usecase

import co.zw.nissangtr.customer.catalog.domain.CatalogRepository
import co.zw.nissangtr.customer.catalog.domain.DealTile
import co.zw.nissangtr.customer.rpc.CatalogBrowseResult
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.SearchCatalogResponse
import co.zw.nissangtr.customer.rpc.SearchMode

/**
 * One use case per user action, sitting between [co.zw.nissangtr.customer.catalog.CatalogViewModel]
 * and [CatalogRepository].
 *
 * **Adopt-first note (structural pattern only, no code/assets copied):** this
 * data ↔ domain(model/repository/usecase) ↔ presentation layering mirrors
 * [3wiida/OmniCart](https://github.com/3wiida/OmniCart) — a real, verified Jetpack
 * Compose Kotlin e-commerce sample (`app/src/main/java/com/mahmoudibrahem/omnicart/{data,domain,presentation}`,
 * with `domain/repository` + `domain/usecase` subpackages) confirmed via the GitHub API
 * in this pass. **License: none** — the repo has no `LICENSE` file (`license: null` on
 * the GitHub repos API), so it is all-rights-reserved by default, *more* restrictive than
 * AGPL/GPL. Per the adopt-first policy this means **no code, resources, or assets were
 * copied or forked** — only the generic, non-copyrightable architectural shape (Clean
 * Architecture + MVVM: repository interface in `domain`, implementation in `data`,
 * one-class-per-use-case) was applied, the same generic pattern documented by Google's
 * own Apache-2.0 "Now in Android" sample and already the basis for this repo's Jetsnack
 * (Apache-2.0) customer-shell adoption (`docs/plans/2026-08-03-mobile-ui-oss-discovery.md`).
 */
class SearchCatalogUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(mode: SearchMode, query: String): SearchCatalogResponse {
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Enter a search query" }
        return repository.searchCatalog(mode, trimmed)
    }
}

class BrowseCatalogUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(category: String? = null, limit: Int = 50): CatalogBrowseResult =
        repository.browse(category, limit)
}

class LoadCatalogProductUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(oem: String): CatalogProduct = repository.loadProduct(oem)
}

class AddToCartUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(oem: String, qty: Double): Pair<String, String> {
        require(qty > 0) { "Qty must be > 0" }
        return repository.addToCart(oem, qty)
    }
}

class AddToWishlistUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(stockItemId: String, oem: String) =
        repository.addToWishlist(stockItemId, oem)
}

class AddToCompareUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(stockItemId: String, oem: String) =
        repository.addToCompare(stockItemId, oem)
}

class GetPrimaryVehicleUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(): GarageVehicle? = repository.getPrimaryVehicle()
}

class GetReviewStatsUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(stockItemId: String?, oem: String?) =
        repository.getReviewStats(stockItemId, oem)
}

/**
 * Always returns empty today — see [DealTile] TODO. Deliberately its own use case
 * (not a [CatalogRepository] method backed by a live RPC call) so a not-yet-shipped
 * backend endpoint can never surface as a runtime Postgres "function does not exist"
 * error on the storefront home screen; the UI shows an honest "coming soon" placeholder.
 */
class GetActiveDealsUseCase {
    suspend operator fun invoke(): List<DealTile> = emptyList()
}

/** Aggregates catalog/home use cases behind a single ViewModel constructor parameter. */
class CatalogUseCases(
    val search: SearchCatalogUseCase,
    val browse: BrowseCatalogUseCase,
    val loadProduct: LoadCatalogProductUseCase,
    val addToCart: AddToCartUseCase,
    val addToWishlist: AddToWishlistUseCase,
    val addToCompare: AddToCompareUseCase,
    val getPrimaryVehicle: GetPrimaryVehicleUseCase,
    val getActiveDeals: GetActiveDealsUseCase,
    val reviewStats: GetReviewStatsUseCase,
) {
    companion object {
        fun from(repository: CatalogRepository): CatalogUseCases = CatalogUseCases(
            search = SearchCatalogUseCase(repository),
            browse = BrowseCatalogUseCase(repository),
            loadProduct = LoadCatalogProductUseCase(repository),
            addToCart = AddToCartUseCase(repository),
            addToWishlist = AddToWishlistUseCase(repository),
            addToCompare = AddToCompareUseCase(repository),
            getPrimaryVehicle = GetPrimaryVehicleUseCase(repository),
            getActiveDeals = GetActiveDealsUseCase(),
            reviewStats = GetReviewStatsUseCase(repository),
        )
    }
}
