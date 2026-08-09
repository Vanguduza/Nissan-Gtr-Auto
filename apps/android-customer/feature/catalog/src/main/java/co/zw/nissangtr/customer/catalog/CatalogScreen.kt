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
    onOpenEpcBrowse: () -> Unit = {},
    initialOem: String? = null,
    initialCategorySeed: String? = null,
    categorySeedSeq: Int = 0,
    showShellChrome: Boolean = false,
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
            CatalogScreenRoute.Home -> KmpHome(
                state = state,
                rpc = rpc,
                wishOems = wishOems,
                showShellChrome = showShellChrome,
                onBack = onBack,
                onSeeAllCategories = viewModel::openCategories,
                onSeeAllNewest = viewModel::openNewest,
                onSeeAllMostSale = viewModel::openNewest,
                onOpenProduct = viewModel::openProduct,
                onToggleWish = { item -> wishlistStore.toggle(item.stockItemId, item.oem) },
                onCategoryBrowse = viewModel::openCategoryBrowse,
                onConfirmCascade = viewModel::confirmCascadeVehicle,
                onConfirmVin = viewModel::confirmVinVehicle,
                onClearVehicle = viewModel::clearSelectedVehicle,
                onOpenEpcBrowse = onOpenEpcBrowse,
            )
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
            )
            CatalogScreenRoute.Product -> state.product?.let { product ->
                KmpPdp(
                    product = product,
                    primaryVehicle = state.primaryVehicle,
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

private val homeBanners = listOf(
    "Genuine Nissan parts · Harare counter & nationwide dispatch",
    "Click & Collect same day at the Harare counter",
    "ZiG settlement available at checkout",
)

@Composable
private fun CompactFitmentBar(label: String) {
    Text(
        text = "Shopping for · $label",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun KmpHome(
    state: CatalogUiState,
    rpc: RpcClient,
    wishOems: Set<String>,
    showShellChrome: Boolean,
    onBack: () -> Unit,
    onSeeAllCategories: () -> Unit,
    onSeeAllMostSale: () -> Unit,
    onSeeAllNewest: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleWish: (CatalogListItem) -> Unit,
    onCategoryBrowse: (String) -> Unit,
    onConfirmCascade: (String, String, String, String?) -> Unit,
    onConfirmVin: (String) -> Unit,
    onClearVehicle: () -> Unit,
    onOpenEpcBrowse: () -> Unit = {},
) {
    val newest = state.browseItems.take(8)
    val mostSale = state.browseItems.drop(8).take(8).ifEmpty { state.browseItems.take(8) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (showShellChrome) {
                InlineCatalogSearch(
                    rpc = rpc,
                    onOpenProduct = onOpenProduct,
                    onApplyFilter = onCategoryBrowse,
                )
                CompactFitmentBar(label = state.fitmentBarLabel())
                Spacer(Modifier.height(8.dp))
                VehicleSelectorSection(
                    vehicleRows = state.vehicleRows,
                    confirmedVehicle = state.selectedFitment,
                    busy = state.vehicleBusy,
                    error = state.vehicleError,
                    onConfirmCascade = onConfirmCascade,
                    onConfirmVin = onConfirmVin,
                    onClear = onClearVehicle,
                )
                TextButton(onClick = onOpenEpcBrowse) {
                    Text("Browse EPC diagrams")
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onBack) { Text("Home") }
                }
                InlineCatalogSearch(
                    rpc = rpc,
                    onOpenProduct = onOpenProduct,
                    onApplyFilter = onCategoryBrowse,
                )
                CompactFitmentBar(label = state.fitmentBarLabel())
                Spacer(Modifier.height(8.dp))
                VehicleSelectorSection(
                    vehicleRows = state.vehicleRows,
                    confirmedVehicle = state.selectedFitment,
                    busy = state.vehicleBusy,
                    error = state.vehicleError,
                    onConfirmCascade = onConfirmCascade,
                    onConfirmVin = onConfirmVin,
                    onClear = onClearVehicle,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Text(
                "Special for you",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )
            ShopBannerCarousel(banners = homeBanners)
            Spacer(Modifier.height(16.dp))

            ShopMerchTitleRow(
                title = "Category",
                actionLabel = "See all",
                onAction = onSeeAllCategories,
            )
            ShopCategoryChipRow(
                categories = DefaultCatalogCategoryCards.map { it.label },
                onCategory = onCategoryBrowse,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(16.dp))
            if (state.deals.isEmpty()) {
                ShopHonestEmpty(
                    title = "No flash deals",
                    body = "No deals.",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                ShopMerchTitleRow(title = "Flash deals", actionLabel = null)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    rowItems(state.deals, key = { it.id }) { deal ->
                        ShopProductCard(
                            title = deal.title,
                            subtitle = deal.subtitle,
                            priceLabel = "Deal",
                            liked = false,
                            onLikeClick = {},
                            onClick = {},
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            ShopMerchTitleRow(
                title = "Most sale",
                actionLabel = "See all",
                onAction = onSeeAllMostSale,
            )
            if (mostSale.isEmpty()) {
                ShopHonestEmpty(
                    title = "No stock yet",
                    body = "Catalog SoR has no saleable stock items. Reload inventory, then refresh.",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    rowItems(mostSale, key = { it.stockItemId }) { item ->
                        ShopProductCard(
                            title = item.oem,
                            subtitle = item.name,
                            priceLabel = item.usd?.let { "USD %.2f".format(it) } ?: "On request",
                            liked = wishOems.contains(item.oem.trim().uppercase()),
                            onLikeClick = { onToggleWish(item) },
                            onClick = { onOpenProduct(item.oem) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            ShopMerchTitleRow(
                title = "Newest products",
                actionLabel = "See all",
                onAction = onSeeAllNewest,
            )
            if (newest.isEmpty()) {
                ShopHonestEmpty(
                    title = "No recent parts",
                    body = "No stock rows yet — empty catalog, not a filter bug.",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    rowItems(newest, key = { "n-${it.stockItemId}" }) { item ->
                        ShopProductCard(
                            title = item.oem,
                            subtitle = item.name,
                            priceLabel = item.usd?.let { "USD %.2f".format(it) } ?: "On request",
                            liked = wishOems.contains(item.oem.trim().uppercase()),
                            onLikeClick = { onToggleWish(item) },
                            onClick = { onOpenProduct(item.oem) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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
        modifier = modifier,
    )
}

@Composable
private fun KmpPdp(
    product: CatalogProduct,
    primaryVehicle: GarageVehicle?,
    qty: String,
    busy: Boolean,
    reviewStats: ProductReviewStats?,
    liked: Boolean,
    onQtyChange: (String) -> Unit,
    onBack: () -> Unit,
    onAddToCart: () -> Unit,
    onToggleWishlist: () -> Unit,
    onOpenReviews: () -> Unit,
) {
    val priceLabel = when {
        product.usd == null -> "Price on request"
        product.coreCharge > 0 ->
            "USD %.2f + %.2f core".format(product.usd, product.coreCharge)
        else -> "USD %.2f".format(product.usd)
    }
    val galleryUrls = product.imageUrls.filter { it.isNotBlank() }.distinct()
    var selectedThumb by remember(product.oem) { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 88.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(GtrColors.Mist),
            ) {
                ShopProductGalleryHero(
                    imageUrls = galleryUrls,
                    heroLabel = product.oem,
                    modifier = Modifier.fillMaxSize(),
                    selectedIndex = selectedThumb,
                )
                Box(modifier = Modifier.padding(16.dp).align(Alignment.TopStart)) {
                    ShopCircleIconButton(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onBack,
                        contentDescription = "Back",
                    )
                }
                Box(modifier = Modifier.padding(16.dp).align(Alignment.TopEnd)) {
                    ShopCircleIconButton(
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        onClick = onToggleWishlist,
                        contentDescription = "Wishlist",
                    )
                }
                if (galleryUrls.size > 1) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems(galleryUrls.indices.toList(), key = { it }) { i ->
                                Box(
                                    modifier = Modifier
                                        .size(65.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { selectedThumb = i },
                                ) {
                                    ShopRemoteImage(
                                        url = galleryUrls[i],
                                        contentDescription = "Image ${i + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        placeholderLabel = "${i + 1}",
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(product.stock.label(), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShopRatingRow(
                        avgRating = reviewStats?.avgRating ?: 0.0,
                        reviewCount = reviewStats?.reviewCount ?: 0,
                    )
                    TextButton(onClick = onOpenReviews) { Text("Reviews") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                product.oem,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                product.name,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Product details",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            StableExpandableDescription(
                text = product.descriptionText(),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (product.coreCharge > 0) {
                Text(
                    "Part USD %.2f".format(product.usd ?: 0.0),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Core deposit USD %.2f (separate cart line - refundable on core return)".format(
                        product.coreCharge,
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(16.dp), color = GtrColors.Mist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Reviews", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onOpenReviews) { Text("See all / write") }
            }
            Text(
                when {
                    reviewStats == null || reviewStats.reviewCount == 0 ->
                        "No reviews yet"
                    else ->
                        "%.1f average · %d review(s). Tap above to read or submit.".format(
                            reviewStats.avgRating,
                            reviewStats.reviewCount,
                        )
                },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = GtrColors.Mist)
            Text(
                "Fitment",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                fitmentVersusGarage(product, primaryVehicle),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (product.fitmentLines.isNotEmpty()) {
                product.fitmentLines.forEach { line ->
                    Text(
                        " · $line",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = qty,
                onValueChange = onQtyChange,
                label = { Text("Qty") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                enabled = !busy,
                shape = MaterialTheme.shapes.small,
            )
            Spacer(Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            ShopStickyCtaBar(
                priceLabel = priceLabel,
                ctaLabel = if (busy) "Adding…" else "Add to cart",
                onCta = onAddToCart,
                enabled = !busy,
            )
        }
    }
}

/**
 * Stable Read More — no layout-measure loop. Collapsed text is truncated once;
 * expand/collapse only toggles maxLines (no finalText rewrite that flashes).
 */
@Composable
private fun StableExpandableDescription(
    text: String,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 2,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    val needsToggle = text.length > 120 || text.lines().size > collapsedLines
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (needsToggle) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show Less" else "Read More")
            }
        }
    }
}

private fun fitmentVersusGarage(product: CatalogProduct, vehicle: GarageVehicle?): String {
    if (vehicle == null) {
        return "Add a vehicle in My Garage for sticky fitment."
    }
    val garage = vehicle.summaryLabel()
    if (product.fitmentLines.isEmpty()) {
        return "Garage: $garage - no published fitment lines for this OEM yet."
    }
    val tokens = listOfNotNull(vehicle.model, vehicle.generation, vehicle.engine)
        .map { it.trim() }
        .filter { it.length >= 2 }
    val matches = tokens.isNotEmpty() && product.fitmentLines.any { line ->
        tokens.any { tok -> line.contains(tok, ignoreCase = true) }
    }
    return if (matches) {
        "Fits your garage vehicle: $garage"
    } else {
        "May not fit your garage vehicle ($garage). Check OEM fitment below."
    }
}
