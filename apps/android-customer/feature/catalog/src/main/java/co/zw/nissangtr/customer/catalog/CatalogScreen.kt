package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.ProductReviewStats
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.descriptionText
import co.zw.nissangtr.customer.rpc.summaryLabel
import co.zw.nissangtr.customer.reviews.PdpReviewsScreen
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.customer.wishlist.WishlistStore
import co.zw.nissangtr.ui.shop.ShopBannerCarousel
import co.zw.nissangtr.ui.shop.ShopCategoryChipRow
import co.zw.nissangtr.ui.shop.ShopCircleIconButton
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopMerchTitleRow
import co.zw.nissangtr.ui.shop.ShopProductCard
import co.zw.nissangtr.ui.shop.ShopRatingRow
import co.zw.nissangtr.ui.shop.ShopStickyCtaBar
import co.zw.nissangtr.ui.shop.ShopProductGalleryHero
import co.zw.nissangtr.ui.shop.ShopRemoteImage
import co.zw.nissangtr.ui.theme.GtrColors

/**
 * Storefront catalog — Home / Categories / Newest / PDP.
 * Inline typeahead stays on Home; never opens a discarded SearchResults page.
 */
@Composable
fun CatalogScreen(
    rpc: RpcClient,
    wishlistStore: WishlistStore,
    onBack: () -> Unit,
    onOpenCart: () -> Unit = {},
    onCartChanged: () -> Unit = {},
    onManageVehicle: () -> Unit = {},
    onTrackOrder: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenEpcBrowse: () -> Unit = {},
    initialOem: String? = null,
    initialCategorySeed: String? = null,
    categorySeedSeq: Int = 0,
    showShellChrome: Boolean = false,
    landing: CatalogLanding = CatalogLanding.Home,
    viewModelKey: String = "catalog",
    camera: PodCameraBridge? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: CatalogViewModel = viewModel(
        key = viewModelKey,
        factory = CatalogViewModel.factory(rpc),
    )
    val state by viewModel.state.collectAsState()
    val wishOems by wishlistStore.oemKeys.collectAsState()
    var reviewsProduct by remember { mutableStateOf<CatalogProduct?>(null) }
    val displayItems = remember(state.browseItems, state.filterState, state.sortOption) {
        applyCatalogFilterSort(state.browseItems, state.filterState, state.sortOption)
    }
    val categoryLabels = remember { DefaultCatalogCategoryCards.map { it.label } }

    reviewsProduct?.let { product ->
        BackHandler { reviewsProduct = null }
        PdpReviewsScreen(
            rpc = rpc,
            camera = camera,
            oem = product.oem,
            stockItemId = product.stockItemId,
            onBack = { reviewsProduct = null },
            modifier = modifier,
        )
        return
    }

    BackHandler(enabled = state.route != CatalogScreenRoute.Home) {
        when (state.route) {
            CatalogScreenRoute.Product -> viewModel.navigateBackFromProduct()
            CatalogScreenRoute.Home -> Unit
            else -> viewModel.navigateHome()
        }
    }

    LaunchedEffect(initialOem) {
        val oem = initialOem?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        viewModel.openProduct(oem)
    }

    LaunchedEffect(categorySeedSeq) {
        if (categorySeedSeq == 0) return@LaunchedEffect
        val seed = initialCategorySeed?.trim()?.takeIf { it.isNotEmpty() }
        viewModel.applyCategoryFilter(seed)
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.route) {
            CatalogScreenRoute.Home -> when (landing) {
                CatalogLanding.Home -> PremiumCatalogHome(
                    state = state,
                    wishOems = wishOems,
                    onShopAll = viewModel::openNewest,
                    onSeeAllCategories = viewModel::openCategories,
                    onSeeAllPopular = viewModel::openNewest,
                    onSeeAllNewest = viewModel::openNewest,
                    onOpenProduct = viewModel::openProduct,
                    onToggleWish = { item -> wishlistStore.toggle(item.stockItemId, item.oem) },
                    onAddToCart = { item ->
                        viewModel.quickAddToCart(item) { onCartChanged() }
                    },
                    onCategoryBrowse = viewModel::openCategoryBrowse,
                    onConfirmCascade = viewModel::confirmCascadeVehicle,
                    onConfirmVin = viewModel::confirmVinVehicle,
                    onClearVehicle = viewModel::clearSelectedVehicle,
                    onTrackOrder = onTrackOrder,
                )
                CatalogLanding.Shop -> PremiumShopBrowse(
                    state = state,
                    wishOems = wishOems,
                    onOpenProduct = viewModel::openProduct,
                    onToggleWish = { item -> wishlistStore.toggle(item.stockItemId, item.oem) },
                    onAddToCart = { item ->
                        viewModel.quickAddToCart(item) { onCartChanged() }
                    },
                    onConfirmCascade = viewModel::confirmCascadeVehicle,
                    onConfirmVin = viewModel::confirmVinVehicle,
                    onClearVehicle = viewModel::clearSelectedVehicle,
                )
            }
            CatalogScreenRoute.Categories -> CategoriesGridScreen(
                onBack = viewModel::navigateHome,
                onCategoryClick = viewModel::openCategoryBrowse,
            )
            CatalogScreenRoute.CategoryBrowse -> CategoryPlpScreen(
                title = state.categoryBrowseTitle ?: state.activeCategory ?: "Parts",
                products = displayItems,
                wishOems = wishOems,
                busy = state.busy,
                filterState = state.filterState,
                sortOption = state.sortOption,
                categoryLabels = categoryLabels,
                onBack = viewModel::navigateHome,
                onOpenProduct = viewModel::openProduct,
                onToggleWish = { item -> wishlistStore.toggle(item.stockItemId, item.oem) },
                onApplyFilter = viewModel::applyBrowseFilter,
                onApplySort = viewModel::applyBrowseSort,
                onAddToCart = { item ->
                    viewModel.quickAddToCart(item) { onCartChanged() }
                },
            )
            CatalogScreenRoute.Newest -> NewestProductsScreen(
                products = displayItems,
                wishOems = wishOems,
                busy = state.busy,
                filterState = state.filterState,
                sortOption = state.sortOption,
                categoryLabels = categoryLabels,
                onBack = viewModel::navigateHome,
                onOpenProduct = viewModel::openProduct,
                onToggleWish = { item -> wishlistStore.toggle(item.stockItemId, item.oem) },
                onApplyFilter = viewModel::applyBrowseFilter,
                onApplySort = viewModel::applyBrowseSort,
                onAddToCart = { item ->
                    viewModel.quickAddToCart(item) { onCartChanged() }
                },
            )
            CatalogScreenRoute.Product -> state.product?.let { product ->
                PremiumCatalogPdp(
                    product = product,
                    selectedVehicle = state.selectedFitment,
                    qty = state.addQty,
                    busy = state.busy,
                    reviewStats = state.reviewStats,
                    liked = wishOems.contains(product.oem.trim().uppercase()),
                    onQtyChange = viewModel::onAddQtyChange,
                    onBack = viewModel::navigateBackFromProduct,
                    onAddToCart = {
                        viewModel.addToCart {
                            onCartChanged()
                            onOpenCart()
                        }
                    },
                    onToggleWishlist = {
                        wishlistStore.toggle(product.stockItemId, product.oem)
                    },
                    onOpenReviews = { reviewsProduct = product },
                )
            }
        }

        state.error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                    .padding(12.dp),
            )
        }
    }
}

/** Dedicated page of recently added products (browse order = newest-first when available). */
@Composable
fun NewestProductsScreen(
    products: List<CatalogListItem>,
    wishOems: Set<String>,
    busy: Boolean,
    filterState: co.zw.nissangtr.ui.shop.ShopFilterState,
    sortOption: co.zw.nissangtr.ui.shop.ShopSortOption,
    categoryLabels: List<String>,
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleWish: (CatalogListItem) -> Unit,
    onApplyFilter: (co.zw.nissangtr.ui.shop.ShopFilterState) -> Unit,
    onApplySort: (co.zw.nissangtr.ui.shop.ShopSortOption) -> Unit,
    onAddToCart: ((CatalogListItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    CategoryPlpScreen(
        title = "Newest products",
        products = products,
        wishOems = wishOems,
        busy = busy,
        filterState = filterState,
        sortOption = sortOption,
        categoryLabels = categoryLabels,
        onBack = onBack,
        onOpenProduct = onOpenProduct,
        onToggleWish = onToggleWish,
        onApplyFilter = onApplyFilter,
        onApplySort = onApplySort,
        onAddToCart = onAddToCart,
        modifier = modifier,
    )
}
