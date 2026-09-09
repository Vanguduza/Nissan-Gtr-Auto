package co.zw.nissangtr.customer.catalog.domain

import co.zw.nissangtr.customer.rpc.CatalogBrowseResult
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.SearchCatalogResponse
import co.zw.nissangtr.customer.rpc.SearchMode
import co.zw.nissangtr.customer.rpc.VehicleMasterRow

/**
 * Feature-scoped repository boundary for catalog/home.
 *
 * Narrows the RpcClient surface to what this feature needs. ViewModel depends on
 * use cases over this interface, never on RpcClient directly.
 */
interface CatalogRepository {
    suspend fun searchCatalog(mode: SearchMode, query: String): SearchCatalogResponse

    suspend fun browse(category: String? = null, limit: Int = 50): CatalogBrowseResult

    suspend fun popular(limit: Int = 8): List<co.zw.nissangtr.customer.rpc.CatalogListItem>

    suspend fun loadProduct(oem: String): CatalogProduct

    suspend fun addToCart(oem: String, qty: Double): Pair<String, String>

    suspend fun addToWishlist(stockItemId: String, oem: String)

    suspend fun addToCompare(stockItemId: String, oem: String)

    /** Published catalog_v2 rows for cascading Select vehicle. */
    suspend fun listVehicleMaster(): List<VehicleMasterRow>

    /**
     * Saleable stock referenced against hosted EPC fitment for the canonical selected vehicle.
     * Technical EPC identifiers remain internal to the transport.
     */
    suspend fun listCatalogForVehicle(
        vehicleMasterId: String?,
        chassisCode: String,
        engineCode: String?,
        category: String? = null,
        limit: Int = 50,
    ): CatalogBrowseResult

    /** Primary (or most-recently saved) My Garage vehicle. */
    suspend fun getPrimaryVehicle(): GarageVehicle?

    suspend fun getReviewStats(stockItemId: String?, oem: String?): co.zw.nissangtr.customer.rpc.ProductReviewStats?
}
