package co.zw.nissangtr.customer.visual

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.rpc.StockState
import co.zw.nissangtr.customer.visual.vehicle.VehicleArtworkResolver
import co.zw.nissangtr.pos.design.icons.Cog
import co.zw.nissangtr.pos.design.icons.Disc3
import co.zw.nissangtr.pos.design.icons.Droplet
import co.zw.nissangtr.pos.design.icons.Gauge
import co.zw.nissangtr.pos.design.icons.House
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.icons.ShieldCheck
import co.zw.nissangtr.pos.design.icons.Sparkles
import co.zw.nissangtr.pos.design.icons.Zap
import co.zw.nissangtr.pos.design.icons.Clock
import co.zw.nissangtr.pos.design.primitives.posNeuRaised
import co.zw.nissangtr.pos.design.primitives.posNeuRaisedSmall
import co.zw.nissangtr.pos.design.theme.PosTheme

/*
 * The POS customer style: the counter's own components (packages/pos-design) on a phone.
 * Minimal, airy layouts on the light canvas; white surfaces lifted with soft neumorphic depth;
 * thin line icons; red only for brand accents and the part-card lining. The hero stays a dark
 * rounded card with the GT-R, as on the tablet. Every function mirrors a tablet component.
 */

internal val isPosStyle: Boolean
    @Composable get() = LocalGtrPalette.current.style == CustomerStyle.Pos

private val PosCardShape = RoundedCornerShape(16.dp)
private val PosControlShape = RoundedCornerShape(12.dp)

/** White (or dark-surface) panel lifted with the POS soft shadow. */
@Composable
internal fun Modifier.posSurface(shape: RoundedCornerShape = PosCardShape, radius: Dp = 16.dp): Modifier =
    this.posNeuRaised(cornerRadius = radius).clip(shape).background(PosTheme.palette.surfacePrimary)

@Composable
private fun PosLineIcon(icon: ImageVector, tint: Color, size: Dp = 22.dp, description: String? = null) {
    Image(
        imageVector = icon,
        contentDescription = description,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(size),
    )
}

@Composable
internal fun PosStyleTopBar(cartCount: Int, onMenu: () -> Unit, onCart: () -> Unit, modifier: Modifier) {
    val p = PosTheme.palette
    Row(
        modifier.fillMaxWidth().background(p.canvas).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).posNeuRaisedSmall(cornerRadius = 22.dp).clip(CircleShape)
                .background(p.surfacePrimary).clickable(role = Role.Button, onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Menu, "Menu", tint = p.textPrimary, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("Nissan GTR Auto", color = p.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Genuine parts, fast", color = p.textMuted, fontSize = 12.sp)
        }
        Box(
            Modifier.size(44.dp).posNeuRaisedSmall(cornerRadius = 22.dp).clip(CircleShape)
                .background(p.surfacePrimary).clickable(role = Role.Button, onClick = onCart),
            contentAlignment = Alignment.Center,
        ) {
            PosLineIcon(PosIcons.ShoppingCart, p.textPrimary, 20.dp, "Cart")
            if (cartCount > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp).clip(CircleShape).background(p.brandRed),
                    contentAlignment = Alignment.Center,
                ) { Text(cartCount.coerceAtMost(99).toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
internal fun PosStyleBottomNav(selected: PremiumTab, onSelect: (PremiumTab) -> Unit, modifier: Modifier) {
    val p = PosTheme.palette
    data class Tab(val tab: PremiumTab, val label: String, val icon: ImageVector?)
    val tabs = listOf(
        Tab(PremiumTab.Home, "Home", PosIcons.House),
        Tab(PremiumTab.Shop, "Shop", PosIcons.Package),
        Tab(PremiumTab.Garage, "Garage", PosIcons.Car),
        Tab(PremiumTab.Wishlist, "Saved", null),
        Tab(PremiumTab.Account, "Account", PosIcons.User),
    )
    Row(
        modifier.fillMaxWidth().background(p.surfacePrimary).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            val on = t.tab == selected
            val tint = if (on) Color.White else p.textMuted
            Column(
                Modifier.weight(1f).clip(PosControlShape).clickable(role = Role.Tab) { onSelect(t.tab) }.padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.height(30.dp).width(52.dp).clip(RoundedCornerShape(15.dp))
                        .background(if (on) p.brandRed else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (t.icon != null) PosLineIcon(t.icon, tint, 20.dp)
                    else Icon(if (on) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = tint, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(t.label, color = if (on) p.textPrimary else p.textMuted, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun PosStyleHero(onShopAll: () -> Unit, modifier: Modifier) {
    val p = PosTheme.palette
    Box(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).height(200.dp).clip(RoundedCornerShape(20.dp)).background(p.heroBackdrop),
    ) {
        Image(
            painter = painterResource(R.drawable.pos_hero_car),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.CenterEnd),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to p.heroBackdrop, 0.42f to p.heroBackdrop, 0.66f to p.heroBackdrop.copy(alpha = 0f)),
            ),
        )
        Column(Modifier.fillMaxHeight().padding(start = 20.dp, top = 22.dp, bottom = 16.dp, end = 20.dp)) {
            Text("Genuine Parts", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            Text("Real Performance", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            Spacer(Modifier.height(6.dp))
            Text("Keep your Nissan at its best.", color = Color.White.copy(alpha = .82f), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.brandRed))
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                    HeroBadge(PosIcons.Cog, "GENUINE")
                    HeroBadge(PosIcons.ShieldCheck, "TRUSTED")
                }
                Text(
                    "Shop all",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(p.brandRed)
                        .clickable(role = Role.Button, onClick = onShopAll).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HeroBadge(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PosLineIcon(icon, Color.White, 16.dp)
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
    }
}

@Composable
internal fun PosStyleVehicleCard(vehicle: SelectedFitmentVehicle?, onChange: () -> Unit, modifier: Modifier) {
    val p = PosTheme.palette
    val artwork = VehicleArtworkResolver.resolve(vehicle)
    Row(
        modifier.fillMaxWidth().posSurface().clickable(role = Role.Button, onClick = onChange).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(84.dp).height(60.dp).clip(PosControlShape).background(p.canvas),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                VehicleArtworkImage(
                    assetPath = artwork.assetPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    fallback = { PosLineIcon(PosIcons.Car, p.textMuted, 28.dp) },
                )
            } else {
                PosLineIcon(PosIcons.Car, p.textMuted, 28.dp)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("YOUR VEHICLE", color = p.textMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em)
            Text(
                vehicle?.model?.removePrefix("Nissan ") ?: "Choose your vehicle",
                color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            vehicle?.let {
                Text(
                    listOfNotNull(it.generation, it.engine).filter { s -> s.isNotBlank() }.joinToString(" · "),
                    color = p.textMuted, fontSize = 12.sp, maxLines = 1,
                )
            }
        }
        Box(
            Modifier.size(34.dp).posNeuRaisedSmall(cornerRadius = 17.dp).clip(CircleShape).background(p.surfacePrimary),
            contentAlignment = Alignment.Center,
        ) { PosLineIcon(PosIcons.ChevronRight, p.textPrimary, 18.dp, if (vehicle == null) "Select vehicle" else "Change vehicle") }
    }
}

private fun posIconFor(art: CategoryArt): ImageVector = when (art) {
    CategoryArt.Service -> PosIcons.Droplet
    CategoryArt.Engine -> PosIcons.Cog
    CategoryArt.Brakes -> PosIcons.Disc3
    CategoryArt.Suspension -> PosIcons.Wrench
    CategoryArt.Exhaust, CategoryArt.Body -> PosIcons.Car
    CategoryArt.Electrical -> PosIcons.Zap
    CategoryArt.Cooling, CategoryArt.Fuel -> PosIcons.Droplet
    CategoryArt.Lighting -> PosIcons.Sparkles
    CategoryArt.Transmission -> PosIcons.Gauge
}

@Composable
internal fun PosStyleCategoryRail(onCategory: (String) -> Unit, modifier: Modifier) {
    val p = PosTheme.palette
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(DefaultPremiumCategories, key = { it.label }) { c ->
            Column(
                Modifier.width(92.dp).height(96.dp).posSurface(PosControlShape, 12.dp)
                    .clickable(role = Role.Button) { onCategory(c.label) }.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PosLineIcon(posIconFor(c.art), p.textPrimary, 26.dp)
                Spacer(Modifier.height(8.dp))
                Text(c.label, color = p.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
internal fun PosStyleSectionHeader(title: String, action: String?, onAction: (() -> Unit)?, modifier: Modifier) {
    val p = PosTheme.palette
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = p.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = p.brandRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(PosControlShape).clickable(onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
internal fun PosStyleSupportRow(onFindPart: () -> Unit, onTrackOrder: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SupportTile(PosIcons.Search, "Find a part", "Search by VIN", onFindPart, Modifier.weight(1f))
        SupportTile(PosIcons.Clock, "Track an order", "Live delivery", onTrackOrder, Modifier.weight(1f))
    }
}

@Composable
private fun SupportTile(icon: ImageVector, title: String, body: String, onClick: () -> Unit, modifier: Modifier) {
    val p = PosTheme.palette
    Column(modifier.posSurface().clickable(role = Role.Button, onClick = onClick).padding(14.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(p.canvas), contentAlignment = Alignment.Center) {
            PosLineIcon(icon, p.brandRed, 18.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = p.textMuted, fontSize = 12.sp)
    }
}

/** The tablet PartCard: soft-raised white card, red lining, image well, price + stock, soft Add. */
@Composable
internal fun PosStylePartCard(
    item: CatalogListItem,
    liked: Boolean,
    imageUrl: String?,
    onOpen: () -> Unit,
    onLike: () -> Unit,
    onAddToCart: (() -> Unit)?,
    modifier: Modifier,
) {
    val p = PosTheme.palette
    Column(
        modifier.width(GtrPremiumDimens.ProductCardWidth + 10.dp)
            .posNeuRaised(cornerRadius = 16.dp)
            .clip(PosCardShape)
            .background(p.surfacePrimary)
            .border(1.5.dp, p.brandRed.copy(alpha = 0.7f), PosCardShape)
            .clickable(onClick = onOpen)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp).clip(PosControlShape).background(p.canvas), contentAlignment = Alignment.Center) {
            if (imageUrl != null) {
                co.zw.nissangtr.ui.shop.ShopRemoteImage(
                    url = imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                PosLineIcon(PosIcons.Package, p.textMuted, 28.dp)
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).posNeuRaisedSmall(cornerRadius = 15.dp)
                    .clip(CircleShape).background(p.surfacePrimary).clickable(role = Role.Button, onClick = onLike),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Save", tint = if (liked) p.brandRed else p.textMuted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(item.name, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        item.category?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, color = p.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.usd?.let { "US$ %.2f".format(it) } ?: "On request", color = p.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            val (label, color) = when (item.stock) {
                StockState.IN_STOCK -> "In stock" to p.success
                StockState.LOW -> "Low stock" to p.warning
                else -> "Back-order" to p.textMuted
            }
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (onAddToCart != null) {
            Spacer(Modifier.height(10.dp))
            PosSoftButton("Add", PosIcons.ShoppingCart, onAddToCart, Modifier.fillMaxWidth())
        }
    }
}

/** The tablet SoftButton: white, soft-raised, dark label. */
@Composable
internal fun PosSoftButton(label: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    val p = PosTheme.palette
    Row(
        modifier.height(40.dp).alpha(if (enabled) 1f else .45f).posNeuRaisedSmall(cornerRadius = 12.dp).clip(PosControlShape)
            .background(p.surfacePrimary).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PosLineIcon(icon, p.textPrimary, 16.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = p.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Generic POS panel used by cards across the app. */
@Composable
internal fun PosStyleSurfaceCard(modifier: Modifier, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val base = modifier.fillMaxWidth().posSurface()
    Box((if (onClick == null) base else base.clickable(onClick = onClick)).padding(16.dp)) { content() }
}

@Composable
internal fun PosStyleEmptyState(title: String, body: String, modifier: Modifier) {
    val p = PosTheme.palette
    Column(modifier.fillMaxWidth().posSurface().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(p.canvas), contentAlignment = Alignment.Center) {
            PosLineIcon(PosIcons.Package, p.textMuted, 24.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = p.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, color = p.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
    }
}

/** POS search field: soft-raised white field with a line search icon. */
@Composable
internal fun PosStyleSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    val p = PosTheme.palette
    Row(
        modifier.fillMaxWidth().height(52.dp).posSurface(PosControlShape, 12.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosLineIcon(PosIcons.Search, p.textMuted, 20.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = p.textMuted, fontSize = 15.sp)
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = p.textPrimary, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(p.brandRed),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** POS filter pill: soft-raised; the selected one is solid brand red. */
@Composable
internal fun PosStyleFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val p = PosTheme.palette
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.height(36.dp)
            .then(if (selected) Modifier else Modifier.posNeuRaisedSmall(cornerRadius = 18.dp))
            .clip(shape)
            .background(if (selected) p.brandRed else p.surfacePrimary)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else p.textPrimary, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}
