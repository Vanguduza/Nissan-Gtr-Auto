package co.zw.nissangtr.customer.catalog

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import co.zw.nissangtr.customer.rpc.summaryLabel
import co.zw.nissangtr.customer.wishlist.WishlistStore
import co.zw.nissangtr.ui.shop.ShopBannerCarousel
import co.zw.nissangtr.ui.shop.ShopCategoryChipRow
import co.zw.nissangtr.ui.shop.ShopCircleIconButton
import co.zw.nissangtr.ui.shop.ShopExpandableDescription
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopMerchTitleRow
import co.zw.nissangtr.ui.shop.ShopProductCard
import co.zw.nissangtr.ui.shop.ShopRatingRow
import co.zw.nissangtr.ui.shop.ShopStickyCtaBar
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
    initialOem: String? = null,
    initialCategorySeed: String? = null,
    categorySeedSeq: Int = 0,
    showShellChrome: Boolean = false,
    viewModelKey: String = "catalog",
    modifier: Modifier = Modifier,
) {
    val viewModel: CatalogViewModel = viewModel(
        key = viewModelKey,
        factory = CatalogViewModel.factory(rpc),
    )
    val state by viewModel.state.collectAsState()
    val wishOems by wishlistStore.oemKeys.collectAsState()

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
                onManageVehicle = onManageVehicle,
                onSeeAllCategories = viewModel::openCategories,
                onSeeAllNewest = viewModel::openNewest,
                onSeeAllMostSale = viewModel::openNewest,
                onOpenProduct = viewModel::openProduct,
                onToggleWish = { item -> wishlistStore.toggle(item.stockItemId, item.oem) },
                onApplySearchFilter = { /* typeahead may pass category label — show empty dialog via InlineCatalogSearch host */ },
            )
            CatalogScreenRoute.Categories -> CategoriesGridScreen(
                onBack = viewModel::navigateHome,
            )
            CatalogScreenRoute.Newest -> NewestProductsScreen(
                products = state.browseItems,
                wishOems = wishOems,
                busy = state.busy,
                onBack = viewModel::navigateHome,
                onOpenProduct = viewModel::openProduct,
                onToggleWish = { item -> wishlistStore.toggle(item.stockItemId, item.oem) },
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
private fun KmpHome(
    state: CatalogUiState,
    rpc: RpcClient,
    wishOems: Set<String>,
    showShellChrome: Boolean,
    onBack: () -> Unit,
    onManageVehicle: () -> Unit,
    onSeeAllCategories: () -> Unit,
    onSeeAllMostSale: () -> Unit,
    onSeeAllNewest: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleWish: (CatalogListItem) -> Unit,
    onApplySearchFilter: (String) -> Unit,
) {
    var emptyCategory by remember { mutableStateOf<String?>(null) }
    emptyCategory?.let { title ->
        AlertDialog(
            onDismissRequest = { emptyCategory = null },
            title = { Text(title) },
            text = {
                Text("No items added yet. Stock for this category will appear here when catalog listings are published.")
            },
            confirmButton = {
                TextButton(onClick = { emptyCategory = null }) { Text("OK") }
            },
        )
    }
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
                    onApplyFilter = onApplySearchFilter,
                )
                if (state.primaryVehicle != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onManageVehicle) {
                        Text("Fitment: ${state.primaryVehicle.summaryLabel()} · My Garage")
                    }
                }
                state.activeCategory?.let { cat ->
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onCategory("") }) {
                        Text("Filtered: $cat · Clear")
                    }
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
                    onApplyFilter = onApplySearchFilter,
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
                onCategory = { emptyCategory = it },
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(16.dp))
            if (state.deals.isEmpty()) {
                ShopHonestEmpty(
                    title = "No flash deals",
                    body = "Deals appear here when a live promotions feed ships - never a fake countdown on hardcoded SKUs.",
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

            Spacer(Modifier.height(16.dp))
            ShopMerchTitleRow(
                title = "Newest products",
                actionLabel = "See all",
                onAction = onSeeAllNewest,
            )
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
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleWish: (CatalogListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShopCircleIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                contentDescription = "Back",
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Newest products", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Recently added parts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (products.isEmpty() && !busy) {
            ShopHonestEmpty(
                title = "No recent parts yet",
                body = "Newest products appear here from catalog browse when stock is indexed.",
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gridItems(products, key = { it.stockItemId }) { item ->
                    ShopProductCard(
                        title = item.oem,
                        subtitle = item.name,
                        priceLabel = item.usd?.let { "USD %.2f".format(it) } ?: "On request",
                        liked = wishOems.contains(item.oem.trim().uppercase()),
                        onLikeClick = { onToggleWish(item) },
                        onClick = { onOpenProduct(item.oem) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
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
) {
    val priceLabel = when {
        product.usd == null -> "Price on request"
        product.coreCharge > 0 ->
            "USD %.2f + %.2f core".format(product.usd, product.coreCharge)
        else -> "USD %.2f".format(product.usd)
    }

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
                Text(
                    product.oem,
                    style = MaterialTheme.typography.headlineMedium,
                    color = GtrColors.Steel,
                    modifier = Modifier.align(Alignment.Center),
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
                        rowItems(listOf(0, 1, 2, 3), key = { it }) { i ->
                            Box(
                                modifier = Modifier
                                    .size(65.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(if (i == 0) GtrColors.Steel else GtrColors.Mist),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${i + 1}",
                                    color = if (i == 0) GtrColors.Chalk else GtrColors.Steel,
                                    style = MaterialTheme.typography.labelMedium,
                                )
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
                ShopRatingRow(
                    avgRating = reviewStats?.avgRating ?: 0.0,
                    reviewCount = reviewStats?.reviewCount ?: 0,
                )
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
            ShopExpandableDescription(
                text = product.name,
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
            Text(
                "Reviews",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                when {
                    reviewStats == null || reviewStats.reviewCount == 0 ->
                        "No reviews yet for this part."
                    else ->
                        "%.1f average · %d review(s)".format(
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
                        "· $line",
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
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
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
