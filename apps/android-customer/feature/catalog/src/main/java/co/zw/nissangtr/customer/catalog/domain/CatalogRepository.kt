package co.zw.nissangtr.customer.catalog.domain

import co.zw.nissangtr.customer.rpc.CatalogBrowseResult
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.SearchCatalogResponse
import co.zw.nissangtr.customer.rpc.SearchMode

/**
 * Feature-scoped repository boundary for catalog/home.
 *
 * Narrows the ~39-method [co.zw.nissangtr.customer.rpc.RpcClient] god-interface down to
 * what this feature actually needs, mirroring the `data ↔ domain ↔ presentation` split
 * used by Android Clean-Architecture Compose samples (e.g. 3wiida/OmniCart's
 * `domain/repository` + `domain/usecase` layering — see [co.zw.nissangtr.customer.catalog.domain.usecase]
 * KDoc for the adopt-first/license note). [co.zw.nissangtr.customer.catalog.CatalogViewModel]
 * depends on this interface (via use cases), never on `RpcClient` directly. See
 * `data.CatalogRepositoryImpl` for the only class in this feature allowed to import `RpcClient`.
 */
interface CatalogRepository {
    suspend fun searchCatalog(mode: SearchMode, query: String): SearchCatalogResponse

    suspend fun browse(category: String? = null, limit: Int = 50): CatalogBrowseResult

    suspend fun loadProduct(oem: String): CatalogProduct

    suspend fun addToCart(oem: String, qty: Double): Pair<String, String>

    suspend fun addToWishlist(stockItemId: String, oem: String)

    suspend fun addToCompare(stockItemId: String, oem: String)

    /** Primary (or most-recently saved) My Garage vehicle — reuses `feature/garage`'s own RPCs. */
    suspend fun getPrimaryVehicle(): GarageVehicle?

    suspend fun getReviewStats(stockItemId: String?, oem: String?): co.zw.nissangtr.customer.rpc.ProductReviewStats?
}

/**
 * Home "Deals & Promotions" tile — UI model only, never fabricated data.
 *
 * TODO(@backend_agent): no public customer-facing "browse active deals" RPC exists yet.
 * `ai_promo_settings` / `ai_promo_runs` / `ai_promo_deliveries`
 * (`supabase/migrations/20260803150000_ai_autonomous_crm_stores_finance.sql`) are staff-only
 * AI/CRM outreach tables (push/SMS/WhatsApp campaigns to customers) — RLS-gated to
 * `admin/sales/finance` staff roles, not anon/customer readable, and not a storefront
 * "browse today's deals" feed. Do not point this type at them. Ship a real
 * `list_active_customer_deals`-style RPC + RLS-safe table (backend lane) before populating
 * this from [co.zw.nissangtr.customer.catalog.domain.usecase.GetActiveDealsUseCase].
 */
data class DealTile(
    val id: String,
    val title: String,
    val subtitle: String,
)
