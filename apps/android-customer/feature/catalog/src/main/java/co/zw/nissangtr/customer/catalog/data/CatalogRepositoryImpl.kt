package co.zw.nissangtr.customer.catalog.data

import co.zw.nissangtr.customer.catalog.domain.CatalogRepository
import co.zw.nissangtr.customer.rpc.CatalogBrowseResult
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.HomeMerchRails
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.SearchCatalogResponse
import co.zw.nissangtr.customer.rpc.SearchMode
import co.zw.nissangtr.customer.rpc.VehicleMasterRow

/**
 * Live [CatalogRepository] — the only class in `feature/catalog` allowed to import
 * [RpcClient] directly. ViewModel → use case → repository → RpcClient, never
 * ViewModel → RpcClient (see `CatalogViewModel.factory`).
 */
class CatalogRepositoryImpl(
    private val rpc: RpcClient,
) : CatalogRepository {
    override suspend fun searchCatalog(mode: SearchMode, query: String): SearchCatalogResponse =
        rpc.searchCatalog(mode, query)

    override suspend fun browse(category: String?, limit: Int): CatalogBrowseResult =
        rpc.listCatalogBrowse(category, limit)

    override suspend fun listHomeRails(limit: Int): HomeMerchRails =
        rpc.listStorefrontHomeRails(limit)

    override suspend fun loadProduct(oem: String): CatalogProduct =
        rpc.loadCatalogProduct(oem)

    override suspend fun addToCart(oem: String, qty: Double): Pair<String, String> =
        rpc.addCustomerCartLineByOem(oem, qty)

    override suspend fun addToWishlist(stockItemId: String, oem: String) {
        rpc.addCustomerWishlistItem(stockItemId = stockItemId, oem = oem)
    }

    override suspend fun addToCompare(stockItemId: String, oem: String) {
        rpc.addCustomerCompareItem(stockItemId = stockItemId, oem = oem)
    }

    override suspend fun listVehicleMaster(): List<VehicleMasterRow> =
        rpc.listVehicleMaster()

    override suspend fun listCatalogForVehicle(
        chassisCode: String,
        engineCode: String?,
        limit: Int,
    ): CatalogBrowseResult =
        rpc.listCatalogForVehicle(chassisCode, engineCode, limit)

    override suspend fun getPrimaryVehicle(): GarageVehicle? {
        val vehicles = rpc.listGarageVehicles()
        return vehicles.firstOrNull { it.isPrimary } ?: vehicles.firstOrNull()
    }

    override suspend fun getReviewStats(
        stockItemId: String?,
        oem: String?,
    ): co.zw.nissangtr.customer.rpc.ProductReviewStats? =
        rpc.getProductReviewStats(stockItemId = stockItemId, oem = oem)
}
