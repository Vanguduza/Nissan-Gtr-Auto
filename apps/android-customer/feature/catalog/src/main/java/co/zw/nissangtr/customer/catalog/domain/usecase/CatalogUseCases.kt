package co.zw.nissangtr.customer.catalog.domain.usecase

import co.zw.nissangtr.customer.catalog.domain.CatalogRepository
import co.zw.nissangtr.customer.catalog.domain.DealTile
import co.zw.nissangtr.customer.rpc.CatalogBrowseResult
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.SearchCatalogResponse
import co.zw.nissangtr.customer.rpc.SearchMode
import co.zw.nissangtr.customer.rpc.VehicleMasterRow

/** One use case per customer catalog action. */
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
    suspend operator fun invoke(stockItemId: String, oem: String) = repository.addToWishlist(stockItemId, oem)
}

class AddToCompareUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(stockItemId: String, oem: String) = repository.addToCompare(stockItemId, oem)
}

class GetPrimaryVehicleUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(): GarageVehicle? = repository.getPrimaryVehicle()
}

class ListVehicleMasterUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(): List<VehicleMasterRow> = repository.listVehicleMaster()
}

class ListCatalogForVehicleUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(
        vehicleMasterId: String?,
        chassisCode: String,
        engineCode: String?,
        category: String? = null,
        limit: Int = 50,
    ): CatalogBrowseResult = repository.listCatalogForVehicle(
        vehicleMasterId = vehicleMasterId,
        chassisCode = chassisCode,
        engineCode = engineCode,
        category = category,
        limit = limit,
    )

    /**
     * Transitional source-compatible overload. Production repository resolves the canonical id
     * from the published master and fails closed if chassis/engine is ambiguous.
     */
    suspend operator fun invoke(
        chassisCode: String,
        engineCode: String?,
        limit: Int = 50,
    ): CatalogBrowseResult = repository.listCatalogForVehicle(
        vehicleMasterId = null,
        chassisCode = chassisCode,
        engineCode = engineCode,
        category = null,
        limit = limit,
    )
}

class GetReviewStatsUseCase(private val repository: CatalogRepository) {
    suspend operator fun invoke(stockItemId: String?, oem: String?) = repository.getReviewStats(stockItemId, oem)
}

/** Honest placeholder until an active-deals endpoint exists. */
class GetActiveDealsUseCase {
    suspend operator fun invoke(): List<DealTile> = emptyList()
}

class CatalogUseCases(
    val search: SearchCatalogUseCase,
    val browse: BrowseCatalogUseCase,
    val loadProduct: LoadCatalogProductUseCase,
    val addToCart: AddToCartUseCase,
    val addToWishlist: AddToWishlistUseCase,
    val addToCompare: AddToCompareUseCase,
    val getPrimaryVehicle: GetPrimaryVehicleUseCase,
    val listVehicleMaster: ListVehicleMasterUseCase,
    val listCatalogForVehicle: ListCatalogForVehicleUseCase,
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
            listVehicleMaster = ListVehicleMasterUseCase(repository),
            listCatalogForVehicle = ListCatalogForVehicleUseCase(repository),
            getActiveDeals = GetActiveDealsUseCase(),
            reviewStats = GetReviewStatsUseCase(repository),
        )
    }
}
