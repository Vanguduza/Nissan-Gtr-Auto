package co.zw.nissangtr.customer.visual

import android.graphics.BlurMaskFilter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.rpc.StockState
import co.zw.nissangtr.ui.shop.ShopRemoteImage

/*
 * Express style — the quick-commerce home designed in Figma ("GTR Customer — Home",
 * file g1TXU7YONLHNpZ5wfL129w): red-to-black header with the brand badge and account actions,
 * white domed category buttons with red glyphs, equal product cards with a round add button,
 * and a floating soft-UI tab bar. Colours come from [ExpressPalette] / [ExpressDarkPalette].
 */

val isExpressStyle: Boolean
    @Composable get() = LocalGtrPalette.current.style == CustomerStyle.Express

private val ExpressHeaderBrush = Brush.linearGradient(
    0f to Color(0xFF0A0C0E),
    0.45f to Color(0xFF2A0B12),
    0.8f to Color(0xFF8E0F22),
    1f to Color(0xFFC8102E),
    start = Offset(0f, 0f),
    end = Offset(900f, 1300f),
)

// ---------------------------------------------------------------------------------------------
// Soft-UI depth (brand neumorph tokens) — light canvas only; on dark it falls back to a shadow.
// ---------------------------------------------------------------------------------------------

private fun Modifier.expressNeuRaised(radius: Dp, distance: Dp = 5.dp, blur: Dp = 14.dp): Modifier = composed {
    val p = LocalGtrPalette.current
    val highlight = if (p.isDark) Color(0xFF283040) else Color.White
    val shade = if (p.isDark) Color(0xFF05070A) else Color(0xFFAEB6C2).copy(alpha = 0.55f)
    drawBehind {
        val d = distance.toPx(); val r = radius.toPx(); val b = blur.toPx()
        drawIntoCanvas { canvas ->
            fun layer(color: Color, o: Float) {
                val paint = Paint(); val fp = paint.asFrameworkPaint()
                fp.color = color.toArgb(); fp.maskFilter = BlurMaskFilter(b, BlurMaskFilter.Blur.NORMAL)
                canvas.drawRoundRect(o, o, size.width + o, size.height + o, r, r, paint)
            }
            layer(shade, d)
            layer(highlight, -d)
        }
    }
}

private fun Modifier.expressNeuPressed(radius: Dp): Modifier = composed {
    val p = LocalGtrPalette.current
    val shade = if (p.isDark) Color(0xFF05070A) else Color(0xFFAEB6C2)
    val highlight = if (p.isDark) Color(0xFF2E3646) else Color.White
    drawWithContent {
        drawContent()
        val r = radius.toPx(); val d = 3.dp.toPx()
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r))) }
        clipPath(clip) {
            drawIntoCanvas { canvas ->
                fun edge(color: Color, o: Float) {
                    val paint = Paint(); val fp = paint.asFrameworkPaint()
                    fp.color = color.toArgb(); fp.style = android.graphics.Paint.Style.STROKE
                    fp.strokeWidth = d * 2; fp.maskFilter = BlurMaskFilter(d * 1.8f, BlurMaskFilter.Blur.NORMAL)
                    canvas.drawRoundRect(o - d, o - d, size.width + o + d, size.height + o + d, r + d, r + d, paint)
                }
                edge(shade.copy(alpha = 0.6f), d)
                edge(highlight.copy(alpha = 0.9f), -d)
            }
        }
    }
}

/**
 * Soft raised depth for round controls on the red/black header: a dark drop to the lower right
 * and a faint light lift to the upper left, kept subtle so the gradient shows through.
 */
private fun Modifier.expressNeuOnHeader(distance: Dp = 4.dp, blur: Dp = 10.dp): Modifier = drawBehind {
    val d = distance.toPx(); val b = blur.toPx()
    drawIntoCanvas { canvas ->
        fun layer(color: Color, o: Float) {
            val paint = Paint(); val fp = paint.asFrameworkPaint()
            fp.color = color.toArgb(); fp.maskFilter = BlurMaskFilter(b, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(androidx.compose.ui.geometry.Offset(size.width / 2 + o, size.height / 2 + o), size.minDimension / 2, paint)
        }
        layer(Color.Black.copy(alpha = 0.6f), d)
        layer(Color.White.copy(alpha = 0.16f), -d)
    }
}

// ---------------------------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------------------------

/** One round category shortcut: [glyph] is an `express_glyph_*` vector drawable. */
data class ExpressCategory(val label: String, @DrawableRes val glyph: Int, val key: String)

/** Default category row, mapped to the catalogue's real category names. */
val ExpressDefaultCategories = listOf(
    ExpressCategory("Fits my car", R.drawable.express_glyph_car, ExpressCategoryKeys.MY_VEHICLE),
    ExpressCategory("Service", R.drawable.express_glyph_service, "Service Parts"),
    ExpressCategory("Brakes", R.drawable.express_glyph_brakes, "Braking"),
    ExpressCategory("Suspension", R.drawable.express_glyph_suspension, "Steering & Suspension"),
    ExpressCategory("Engine", R.drawable.express_glyph_engine, "Engine Parts"),
    ExpressCategory("Electrical", R.drawable.express_glyph_electrical, "Electrical"),
    ExpressCategory("All parts", R.drawable.express_glyph_all, ExpressCategoryKeys.ALL),
)

object ExpressCategoryKeys {
    const val MY_VEHICLE = "__my_vehicle__"
    const val ALL = "__all__"
}

/**
 * Brand header: badge + wishlist / account / settings, delivery chip,
 * search pill, the vehicle being shopped for, and the round category buttons.
 */
@Composable
fun ExpressHeader(
    vehicleLabel: String?,
    onWishlist: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onVehicle: () -> Unit,
    onCategory: (ExpressCategory) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<ExpressCategory> = ExpressDefaultCategories,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(ExpressHeaderBrush)
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 46.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Brand row
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.express_logo),
                contentDescription = "Nissan GTR Auto",
                modifier = Modifier.height(44.dp).width(58.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderAction(Icons.Outlined.FavoriteBorder, "Wishlist", onWishlist)
                HeaderAction(Icons.Outlined.PersonOutline, "My account", onAccount)
                HeaderAction(Icons.Outlined.Settings, "Settings", onSettings)
            }
        }
        // Delivery row
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFC8102E))
                    .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.ElectricBolt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delivery", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
        // Search
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .clickable(role = Role.Button, onClick = onSearch)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = Color(0xFF12151C), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Search parts", color = Color(0xFF12151C), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        // Vehicle being shopped for
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(role = Role.Button, onClick = onVehicle)
                .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.DirectionsCar, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            if (vehicleLabel != null) {
                Text("Shopping for ", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                Text(vehicleLabel, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Choose your vehicle", color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        // Category buttons
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(categories, key = { it.key }) { c -> ExpressCategoryButton(c, onClick = { onCategory(c) }) }
        }
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** White domed button with a solid red glyph and a label underneath (reference: car-icon set). */
@Composable
fun ExpressCategoryButton(category: ExpressCategory, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(72.dp).clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        val p = LocalGtrPalette.current
        // Light theme: graphite face matching the service-kit banner; dark theme keeps the chalk face.
        val face = if (p.isDark) {
            Brush.linearGradient(listOf(Color.White, Color(0xFFDCE1E8)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF2A303C), Color(0xFF12151C)))
        }
        Box(
            Modifier
                .size(64.dp)
                .expressNeuOnHeader()
                .clip(CircleShape)
                .background(face)
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.35f))),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(category.glyph), contentDescription = null, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            category.label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Content
// ---------------------------------------------------------------------------------------------

/** White content sheet that overlaps the header with rounded top corners. */
@Composable
fun ExpressSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .offset(y = (-26).dp)
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(GtrPremiumColors.Background)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Chalk panel with a spaced uppercase title, an item-count pill and a sideways product row. */
@Composable
fun ExpressSection(
    title: String,
    countLabel: String?,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(GtrPremiumColors.Surface)
            .padding(top = 14.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (countLabel != null && onSeeAll != null) {
                Row(
                    Modifier
                        .expressNeuRaised(16.dp, distance = 2.dp, blur = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GtrPremiumColors.SurfaceRaised)
                        .clickable(onClick = onSeeAll)
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(countLabel, color = GtrPremiumColors.TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = GtrPremiumColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Equal-size product card: photo well with a round add button, two-line name, price, fit line. */
@Composable
fun ExpressProductCard(
    item: CatalogListItem,
    imageUrl: String?,
    fitsLabel: String?,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(140.dp)
            .height(232.dp)
            .expressNeuRaised(18.dp, distance = 3.dp, blur = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GtrPremiumColors.SurfaceRaised)
            .clickable(onClick = onOpen)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(GtrPremiumColors.SurfaceSoft),
        ) {
            ShopRemoteImage(
                url = imageUrl,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
                placeholderLabel = "Photo coming soon",
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(36.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(role = Role.Button, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add ${item.name} to bag", tint = Color(0xFFC8102E), modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            color = GtrPremiumColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text(
            item.usd?.let { "$%.2f".format(it) } ?: "Price on request",
            color = GtrPremiumColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            listOfNotNull(fitsLabel?.let { "Fits $it" }, stockLabel(item.stock)).joinToString(" · "),
            color = when (item.stock) {
                StockState.LOW -> GtrPremiumColors.Warning
                else -> GtrPremiumColors.TextSecondary
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun stockLabel(s: StockState): String = when (s) {
    StockState.IN_STOCK -> "In stock"
    StockState.LOW -> "Low stock"
    StockState.BACKORDER -> "On order"
}

/** Dark banner with the GT-R photo fading in from the right. */
@Composable
fun ExpressPromoBanner(
    kicker: String,
    title: String,
    body: String,
    cta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(152.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF12151C), Color(0xFF1E2430))))
            .clickable(onClick = onClick),
    ) {
        Image(
            painterResource(R.drawable.pos_hero_car),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(214.dp),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(214.dp)
                .background(Brush.horizontalGradient(0f to Color(0xFF12151C), 0.55f to Color(0x0012151C))),
        )
        Column(Modifier.padding(start = 18.dp, top = 16.dp, end = 120.dp)) {
            Text(kicker, color = Color(0xFFE01234), fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(body, color = Color(0xFFC0C5CE), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(start = 14.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(cta, color = Color(0xFF12151C), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFF12151C), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Floating bottom bar
// ---------------------------------------------------------------------------------------------

data class ExpressTab(val key: String, val label: String, val icon: ImageVector, val badge: Int = 0)

/**
 * Floating soft-UI tab bar (Meetup-style placement): a raised pill inset from the edges; the
 * selected tab is pressed into it, in GTR red.
 */
@Composable
fun ExpressBottomBar(
    tabs: List<ExpressTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalGtrPalette.current
    val barColor = if (p.isDark) Color(0xFF1E2430) else Color(0xFFEEF1F5)
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(barColor.copy(alpha = 0f), barColor.copy(alpha = 0.92f))))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .expressNeuRaised(35.dp, distance = 6.dp, blur = 16.dp)
                .clip(RoundedCornerShape(35.dp))
                .background(barColor)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val on = t.key == selectedKey
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(29.dp))
                            .then(if (on) Modifier.background(barColor).expressNeuPressed(29.dp) else Modifier)
                            .clickable(role = Role.Tab) { onSelect(t.key) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(t.icon, contentDescription = null, tint = if (on) Color(0xFFC8102E) else GtrPremiumColors.TextPrimary, modifier = Modifier.size(24.dp))
                        Text(
                            t.label,
                            color = if (on) Color(0xFFC8102E) else GtrPremiumColors.TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    if (t.badge > 0) {
                        Text(
                            if (t.badge > 99) "99+" else t.badge.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(x = 14.dp, y = 2.dp)
                                .border(2.dp, barColor, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFC8102E))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
