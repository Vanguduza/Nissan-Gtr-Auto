package co.zw.nissangtr.customer.catalog.domain.usecase

import co.zw.nissangtr.customer.catalog.domain.CatalogRepository
import co.zw.nissangtr.customer.rpc.CatalogBrowseResult
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.HomeMerchRails
import co.zw.nissangtr.customer.rpc.SearchCatalogResponse
import co.zw.nissangtr.customer.rpc.SearchMode
import co.zw.nissangtr.customer.rpc.VehicleMasterRow

/**
 * One use case per user action, sitting between [co.zw.nissangtr.customer.catalog.CatalogViewModel]
 * and [CatalogRepository].
 *
 * **Adopt-first note (structural pattern only, no code/assets copied):** this
 * data ↔ domain(model/repository/usecase) ↔ presentation layering mirrors
 * [3wiida/OmniCart](https://github.com/3wiida/OmniCart) — a real, verified Jetpack
 * Compose Kotlin e-commerce sample. **License: none** on that repo — only the generic
 * Clean Architecture + MVVM shape was applied (see `docs/plans/2026-08-03-mobile-ui-oss-discovery.md`).
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

class ListHomeRailsUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(limit: Int = 12): HomeMerchRails =
        repository.listHomeRails(limit)
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

class ListVehicleMasterUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(): List<VehicleMasterRow> = repository.listVehicleMaster()
}

class ListCatalogForVehicleUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(chassisCode: String, engineCode: String?, limit: Int = 50): CatalogBrowseResult =
        repository.listCatalogForVehicle(chassisCode, engineCode, limit)
}

class GetReviewStatsUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(stockItemId: String?, oem: String?) =
        repository.getReviewStats(stockItemId, oem)
}

/** Aggregates catalog/home use cases behind a single ViewModel constructor parameter. */
class CatalogUseCases(
    val search: SearchCatalogUseCase,
    val browse: BrowseCatalogUseCase,
    val listHomeRails: ListHomeRailsUseCase,
    val loadProduct: LoadCatalogProductUseCase,
    val addToCart: AddToCartUseCase,
    val addToWishlist: AddToWishlistUseCase,
    val addToCompare: AddToCompareUseCase,
    val getPrimaryVehicle: GetPrimaryVehicleUseCase,
    val listVehicleMaster: ListVehicleMasterUseCase,
    val listCatalogForVehicle: ListCatalogForVehicleUseCase,
    val reviewStats: GetReviewStatsUseCase,
) {
    companion object {
        fun from(repository: CatalogRepository): CatalogUseCases = CatalogUseCases(
            search = SearchCatalogUseCase(repository),
            browse = BrowseCatalogUseCase(repository),
            listHomeRails = ListHomeRailsUseCase(repository),
            loadProduct = LoadCatalogProductUseCase(repository),
            addToCart = AddToCartUseCase(repository),
            addToWishlist = AddToWishlistUseCase(repository),
            addToCompare = AddToCompareUseCase(repository),
            getPrimaryVehicle = GetPrimaryVehicleUseCase(repository),
            listVehicleMaster = ListVehicleMasterUseCase(repository),
            listCatalogForVehicle = ListCatalogForVehicleUseCase(repository),
            reviewStats = GetReviewStatsUseCase(repository),
        )
    }
}
