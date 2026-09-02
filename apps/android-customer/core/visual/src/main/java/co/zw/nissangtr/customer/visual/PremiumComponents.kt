package co.zw.nissangtr.customer.visual

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.rpc.StockState
import co.zw.nissangtr.customer.visual.vehicle.VehicleArtworkResolver
import co.zw.nissangtr.ui.shop.ShopRemoteImage

enum class PremiumTab { Home, Shop, Garage, Wishlist, Account }

@Composable
fun PremiumTopBar(
    cartCount: Int,
    onMenu: () -> Unit,
    onCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GtrPremiumDimens.TopBarHeight)
            .background(GtrPremiumColors.Background)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Menu, "Menu", tint = GtrPremiumColors.TextPrimary)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "NISSAN GTR AUTO",
                color = GtrPremiumColors.TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "PARTS • PERFORMANCE • PRECISION",
                color = GtrPremiumColors.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        IconButton(onClick = onCart, modifier = Modifier.size(48.dp)) {
            BadgedBox(
                badge = {
                    if (cartCount > 0) {
                        Badge(containerColor = GtrPremiumColors.Red) {
                            Text(cartCount.coerceAtMost(99).toString(), color = Color.White)
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.ShoppingCart, "Cart", tint = GtrPremiumColors.TextPrimary)
            }
        }
    }
}

@Composable
fun PremiumBottomNav(
    selected: PremiumTab,
    onSelect: (PremiumTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        Triple(PremiumTab.Home, "Home", Icons.Filled.Home),
        Triple(PremiumTab.Shop, "Shop", Icons.Filled.Storefront),
        Triple(PremiumTab.Garage, "Garage", Icons.Filled.DirectionsCar),
        Triple(PremiumTab.Wishlist, "Wishlist", Icons.Filled.Favorite),
        Triple(PremiumTab.Account, "Account", Icons.Filled.AccountCircle),
    )
    NavigationBar(
        modifier = modifier.height(GtrPremiumDimens.BottomBarHeight),
        containerColor = GtrPremiumColors.Surface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GtrPremiumColors.Red,
                    selectedTextColor = GtrPremiumColors.Red,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = GtrPremiumColors.TextSecondary,
                    unselectedTextColor = GtrPremiumColors.TextSecondary,
                ),
            )
        }
    }
}

@Composable
fun PerformanceHero(
    vehicle: SelectedFitmentVehicle?,
    onShopAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GtrPremiumDimens.HeroHeight)
            .background(GtrPremiumColors.Background)
    ) {
        // subtle red technical accent
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = GtrPremiumColors.Red.copy(alpha = 0.17f),
                radius = size.minDimension * .42f,
                center = androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .46f),
            )
            drawLine(
                color = GtrPremiumColors.Border,
                start = androidx.compose.ui.geometry.Offset(size.width * .50f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width * .78f, size.height),
                strokeWidth = 2f,
            )
        }

        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.gtr_hero_workshop_r35),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(.76f)
                .height(GtrPremiumDimens.HeroHeight),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to GtrPremiumColors.Background,
                        0.42f to GtrPremiumColors.Background.copy(alpha = .93f),
                        0.68f to GtrPremiumColors.Background.copy(alpha = .36f),
                        1.00f to Color.Transparent,
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(188.dp)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "PARTS THAT",
                color = GtrPremiumColors.TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "PERFORM",
                color = GtrPremiumColors.RedBright,
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Genuine. Aftermarket. Performance.",
                color = GtrPremiumColors.TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onShopAll,
                colors = ButtonDefaults.buttonColors(containerColor = GtrPremiumColors.Red),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Text("Shop All Parts", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun CollapsedVehicleCard(
    vehicle: SelectedFitmentVehicle?,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = VehicleArtworkResolver.resolve(vehicle)
    val imageUrl = artwork?.let { VehicleArtworkStorage.publicUrl(it.assetPath) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(GtrPremiumDimens.VehicleCardHeight),
        colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.SurfaceRaised),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GtrPremiumColors.Border),
    ) {
        Row(
            Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(88.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GtrPremiumColors.SurfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null) {
                    ShopRemoteImage(
                        url = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    GenericNissanSilhouette(Modifier.width(72.dp).height(40.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (vehicle == null) "YOUR VEHICLE" else "YOUR VEHICLE",
                    color = GtrPremiumColors.TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                Text(
                    vehicle?.model?.removePrefix("Nissan ") ?: "Choose your vehicle",
                    color = GtrPremiumColors.TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                vehicle?.let {
                    Text(
                        listOfNotNull(it.generation, it.engine).filter { s -> s.isNotBlank() }.joinToString(" • "),
                        color = GtrPremiumColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            Text(
                if (vehicle == null) "Select ›" else "Change ›",
                color = GtrPremiumColors.RedBright,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onChange)
                    .padding(10.dp),
            )
        }
    }
}

/**
 * Category rail glyphs from Pictogrammers Material Design Icons (Apache 2.0).
 * See `NOTICE` and `ic_mdi_*.xml`. Pack PNGs (`gtr_categorie_*`) stay on disk
 * for the copy-pack validator and are not drawn here.
 */
enum class CategoryArt(val drawable: Int) {
    Service(R.drawable.ic_mdi_oil),
    Engine(R.drawable.ic_mdi_engine),
    Brakes(R.drawable.ic_mdi_car_brake_abs),
    Suspension(R.drawable.ic_mdi_steering),
    Exhaust(R.drawable.ic_mdi_car),
    Electrical(R.drawable.ic_mdi_lightning_bolt),
    Body(R.drawable.ic_mdi_car),
    Cooling(R.drawable.ic_mdi_car_coolant_level),
    Lighting(R.drawable.ic_mdi_car_light_dimmed),
    Transmission(R.drawable.ic_mdi_car_shift_pattern),
    Fuel(R.drawable.ic_mdi_gas_station),
}

data class PremiumCategory(
    val label: String,
    val art: CategoryArt,
)

val DefaultPremiumCategories = listOf(
    PremiumCategory("Service Parts", CategoryArt.Service),
    PremiumCategory("Braking", CategoryArt.Brakes),
    PremiumCategory("Steering & Suspension", CategoryArt.Suspension),
    PremiumCategory("Engine Parts", CategoryArt.Engine),
    PremiumCategory("Transmission", CategoryArt.Transmission),
    PremiumCategory("Electrical", CategoryArt.Electrical),
    PremiumCategory("Lighting", CategoryArt.Lighting),
    PremiumCategory("Body & Exhaust", CategoryArt.Body),
    PremiumCategory("Cooling & Heating", CategoryArt.Cooling),
    PremiumCategory("Fuel System", CategoryArt.Fuel),
)

@Composable
fun CategoryGlyph(
    art: CategoryArt,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = GtrPremiumColors.TextPrimary,
) {
    Icon(
        painter = painterResource(art.drawable),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
fun IllustratedCategoryRail(
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(DefaultPremiumCategories, key = { it.label }) { category ->
            Column(
                modifier = Modifier
                    .width(GtrPremiumDimens.CategoryWidth)
                    .height(GtrPremiumDimens.CategoryHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onCategory(category.label) }
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(GtrPremiumDimens.CategoryArt)
                        .clip(CircleShape)
                        .background(GtrPremiumColors.Red.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CategoryGlyph(category.art)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    category.label,
                    color = GtrPremiumColors.TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PremiumSectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = GtrPremiumColors.TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (action != null && onAction != null) {
            Text(
                action,
                color = GtrPremiumColors.RedBright,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onAction).padding(8.dp),
            )
        }
    }
}

@Composable
fun SupportCardsRow(
    onFindPart: () -> Unit,
    onTrackOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SupportCard(
            title = "NEED HELP FINDING A PART?",
            action = "Search VIN",
            background = GtrPremiumColors.PaperWarm,
            onClick = onFindPart,
            modifier = Modifier.weight(1f),
        )
        SupportCard(
            title = "FAST & RELIABLE DELIVERY",
            action = "Track Order",
            background = GtrPremiumColors.PaperCool,
            onClick = onTrackOrder,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SupportCard(
    title: String,
    action: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(GtrPremiumDimens.SupportCardHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            color = GtrPremiumColors.Background,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "$action ›",
            color = GtrPremiumColors.RedDark,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
fun PremiumProductCard(
    item: CatalogListItem,
    liked: Boolean,
    imageUrl: String?,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onAddToCart: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .width(GtrPremiumDimens.ProductCardWidth)
            .height(GtrPremiumDimens.ProductCardHeight)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = GtrPremiumColors.SurfaceRaised),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GtrPremiumColors.Border),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(GtrPremiumDimens.ProductImageHeight)
                    .background(GtrPremiumColors.SurfaceSoft)
            ) {
                ShopRemoteImage(
                    url = imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit,
                    placeholderLabel = "Product photo unavailable",
                )
                IconButton(
                    onClick = onLike,
                    modifier = Modifier.align(Alignment.TopEnd).size(40.dp),
                ) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Wishlist",
                        tint = if (liked) GtrPremiumColors.RedBright else GtrPremiumColors.TextPrimary,
                    )
                }
            }
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                    item.name,
                    color = GtrPremiumColors.TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.category?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = GtrPremiumColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    item.usd?.let { "USD %.2f".format(it) } ?: "On request",
                    color = GtrPremiumColors.TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (onAddToCart != null) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onAddToCart,
                        colors = ButtonDefaults.buttonColors(containerColor = GtrPremiumColors.Red),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text("Add to Cart", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumProductRail(
    title: String,
    items: List<CatalogListItem>,
    wishOems: Set<String>,
    imageUrlFor: (CatalogListItem) -> String? = { ProductImageStorage.publicUrl(it.imagePath) },
    onSeeAll: (() -> Unit)? = null,
    onOpen: (CatalogListItem) -> Unit,
    onLike: (CatalogListItem) -> Unit,
    onAddToCart: ((CatalogListItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        PremiumSectionHeader(
            title = title,
            action = onSeeAll?.let { "See all" },
            onAction = onSeeAll,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.stockItemId }) { item ->
                PremiumProductCard(
                    item = item,
                    liked = wishOems.contains(item.oem.trim().uppercase()),
                    imageUrl = imageUrlFor(item),
                    onOpen = { onOpen(item) },
                    onLike = { onLike(item) },
                    onAddToCart = onAddToCart?.let { add -> { add(item) } },
                )
            }
        }
    }
}

@Composable
fun PremiumEmptyState(
    title: String,
    body: String,
    art: Int? = null,
    artTint: Color? = null,
    artSize: Dp = 120.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GtrPremiumColors.SurfaceRaised)
            .border(1.dp, GtrPremiumColors.Border, RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        art?.let {
            if (artTint != null) {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(artSize),
                    tint = artTint,
                )
            } else {
                androidx.compose.foundation.Image(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(artSize),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(title, color = GtrPremiumColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            color = GtrPremiumColors.TextSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun GenericNissanSilhouette(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * .08f, h * .68f)
            lineTo(w * .18f, h * .48f)
            lineTo(w * .34f, h * .37f)
            lineTo(w * .60f, h * .35f)
            lineTo(w * .78f, h * .50f)
            lineTo(w * .92f, h * .56f)
            lineTo(w * .95f, h * .72f)
            lineTo(w * .08f, h * .72f)
            close()
        }
        drawPath(p, color = GtrPremiumColors.TextSecondary.copy(alpha = .38f))
        drawCircle(GtrPremiumColors.TextPrimary.copy(alpha = .7f), h * .12f,
            androidx.compose.ui.geometry.Offset(w * .28f, h * .72f))
        drawCircle(GtrPremiumColors.TextPrimary.copy(alpha = .7f), h * .12f,
            androidx.compose.ui.geometry.Offset(w * .76f, h * .72f))
        drawLine(
            GtrPremiumColors.RedBright.copy(alpha = .8f),
            androidx.compose.ui.geometry.Offset(w * .18f, h * .48f),
            androidx.compose.ui.geometry.Offset(w * .78f, h * .50f),
            strokeWidth = 2.5f,
        )
    }
}
