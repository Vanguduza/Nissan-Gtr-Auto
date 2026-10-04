package co.zw.nissangtr.pos.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import co.zw.nissangtr.pos.design.icons.ChevronLeft
import co.zw.nissangtr.pos.design.icons.Clock
import co.zw.nissangtr.pos.design.icons.Cog
import co.zw.nissangtr.pos.design.icons.Disc3
import co.zw.nissangtr.pos.design.icons.Droplet
import co.zw.nissangtr.pos.design.icons.EllipsisVertical
import co.zw.nissangtr.pos.design.icons.Gauge
import co.zw.nissangtr.pos.design.icons.Pin
import co.zw.nissangtr.pos.design.icons.PinOff
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.icons.ShieldCheck
import co.zw.nissangtr.pos.design.icons.Sparkles
import co.zw.nissangtr.pos.design.icons.Trash2
import co.zw.nissangtr.pos.design.icons.Zap
import co.zw.nissangtr.pos.design.layout.PosAdaptiveMath
import co.zw.nissangtr.pos.design.primitives.posFocusRing
import co.zw.nissangtr.pos.design.primitives.posNeuRaised
import co.zw.nissangtr.pos.design.primitives.posNeuRaisedSmall
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.Category
import co.zw.nissangtr.pos.domain.model.PinKind
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.PopularRowItem
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.common.formatQty
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Hero (§6.3)

@Composable
fun PosHero(height: Dp, modifier: Modifier = Modifier, identityStrip: Boolean = false) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(PosTheme.shape.lg)
            .background(palette.heroBackdrop),
    ) {
        // Text-free crop of the locked hero artwork; copy is live text, never baked into the image.
        Image(
            painter = painterResource(R.drawable.pos_hero_car),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.48f).align(Alignment.CenterEnd),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to palette.heroBackdrop,
                        0.5f to palette.heroBackdrop,
                        0.62f to palette.heroBackdrop.copy(alpha = 0f),
                    ),
                ),
        )
        if (identityStrip) {
            // Compact (§3.5): the hero becomes an identity strip — title only, no badges.
            Column(Modifier.padding(start = 20.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                PosText(stringResource(R.string.pos_hero_title), type.heading2.copy(fontWeight = FontWeight.Bold), palette.textOnBrand)
            }
        } else {
        Column(Modifier.padding(start = 36.dp, top = 32.dp, bottom = 24.dp).fillMaxHeight()) {
            PosText(stringResource(R.string.pos_hero_title), type.displayHero, palette.textOnBrand)
            Spacer(Modifier.height(10.dp))
            PosText(stringResource(R.string.pos_hero_sub), type.bodyPrimary.copy(fontWeight = FontWeight.Normal), palette.textOnBrand.copy(alpha = 0.85f))
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(48.dp).height(3.dp).background(palette.brandRed))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                HeroBadge(PosIcons.Cog, stringResource(R.string.pos_hero_badge_genuine))
                HeroBadge(PosIcons.ShieldCheck, stringResource(R.string.pos_hero_badge_quality))
                HeroBadge(PosIcons.Gauge, stringResource(R.string.pos_hero_badge_performance))
            }
        }
        }
    }
}

@Composable
private fun HeroBadge(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PosIcon(icon, tint = PosTheme.palette.textOnBrand, size = 22.dp)
        PosText(
            label,
            PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
            PosTheme.palette.textOnBrand,
        )
    }
}

// ---------------------------------------------------------------- Categories (§6.4)

data class CategoryTile(val category: Category, val label: Int, val icon: ImageVector)

/** Tiles seed real catalogue searches — never canned product results. */
val CategoryTiles: List<CategoryTile> = listOf(
    CategoryTile(Category("engine", "Engine & Drivetrain", "engine"), R.string.pos_cat_engine, PosIcons.Cog),
    CategoryTile(Category("brakes", "Brakes", "brake"), R.string.pos_cat_brakes, PosIcons.Disc3),
    CategoryTile(Category("suspension", "Suspension", "suspension"), R.string.pos_cat_suspension, PosIcons.Wrench),
    CategoryTile(Category("body", "Body & Exterior", "body"), R.string.pos_cat_body, PosIcons.Car),
    CategoryTile(Category("electrical", "Electrical", "electrical"), R.string.pos_cat_electrical, PosIcons.Zap),
    CategoryTile(Category("fluids", "Fluids & Chemicals", "fluid"), R.string.pos_cat_fluids, PosIcons.Droplet),
    CategoryTile(Category("accessories", "Accessories", "accessories"), R.string.pos_cat_accessories, PosIcons.Sparkles),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosCategoryRow(onOpen: (Category) -> Unit, onPin: (Category) -> Unit, modifier: Modifier = Modifier) {
    val palette = PosTheme.palette
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Count derived from width (§3.3); seven at 1280 dp is an output, not a cap.
        val grid = PosAdaptiveMath.deriveLazyRow(maxWidth, minItemWidth = 96.dp, maxItemWidth = 150.dp, gap = 12.dp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(CategoryTiles, key = { it.category.id }) { tile ->
                Column(
                    modifier = Modifier
                        .width(grid.itemWidth)
                        .height(96.dp)
                        .posNeuRaised()
                        .clip(PosTheme.shape.md)
                        .background(palette.surfacePrimary)
                        .posFocusRing(PosTheme.shape.md)
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { onOpen(tile.category) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPin(tile.category)
                            },
                        )
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    PosIcon(tile.icon, tint = palette.textPrimary, size = 30.dp)
                    Spacer(Modifier.height(8.dp))
                    PosText(
                        stringResource(tile.label),
                        PosTheme.type.labelMeta.copy(fontWeight = FontWeight.Medium),
                        palette.textPrimary,
                        maxLines = 2,
                        align = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Popular Items (§7, D1)

@Composable
fun PosPopularRow(
    items: List<PopularRowItem>,
    isPinned: (PopularPin) -> Boolean,
    addEnabled: Boolean,
    onAdd: (CatalogPart) -> Unit,
    onFind: (PopularPin) -> Unit,
    onPin: (PopularPin) -> Unit,
    onUnpin: (PopularPin) -> Unit,
    onHide: (CatalogPart) -> Unit,
    onActivate: (PopularPin) -> Unit,
    modifier: Modifier = Modifier,
    /** Stock of a part at every branch. */
    onStock: (CatalogPart) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth()) {
        SectionHead(title = stringResource(R.string.pos_popular_title)) {
            ScrollButton(PosIcons.ChevronLeft, stringResource(R.string.pos_scroll_left)) {
                scope.launch { listState.animateScrollToItem((listState.firstVisibleItemIndex - 3).coerceAtLeast(0)) }
            }
            ScrollButton(PosIcons.ChevronRight, stringResource(R.string.pos_scroll_right)) {
                scope.launch { listState.animateScrollToItem(listState.firstVisibleItemIndex + 3) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val grid = PosAdaptiveMath.deriveLazyRow(maxWidth, minItemWidth = 160.dp, maxItemWidth = 220.dp, gap = 12.dp)
            if (items.isEmpty()) {
                EmptyCard(stringResource(R.string.pos_popular_empty))
            } else {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    items(items, key = { it.stableKey }) { item ->
                        when (item) {
                            is PopularRowItem.BestSeller -> {
                                val pin = PopularPin.forPart(item.part)
                                PartCard(
                                    part = item.part,
                                    width = grid.itemWidth,
                                    pinnedBadge = false,
                                    addEnabled = addEnabled,
                                    onAdd = { onAdd(item.part) },
                                    menu = listOf(
                                        CardAction(if (isPinned(pin)) PosIcons.PinOff else PosIcons.Pin, if (isPinned(pin)) R.string.pos_unpin else R.string.pos_pin) {
                                            if (isPinned(pin)) onUnpin(pin) else onPin(pin)
                                        },
                                        CardAction(PosIcons.Trash2, R.string.pos_remove_popular) { onHide(item.part) },
                                    ) + listOfNotNull(
                                        item.part.stockItemId?.let { CardAction(PosIcons.Package, R.string.pos_stock_title) { onStock(item.part) } },
                                    ),
                                )
                            }
                            is PopularRowItem.Pinned -> if (item.pin.kind == PinKind.PART) {
                                PartCard(
                                    part = CatalogPart(
                                        stockItemId = null,
                                        oemPartNumber = item.pin.oemPartNumber ?: item.pin.searchQuery,
                                        name = item.pin.label,
                                        price = null,
                                        saleableQty = null,
                                        imageUrl = item.pin.imageUrl,
                                    ),
                                    width = grid.itemWidth,
                                    pinnedBadge = true,
                                    addEnabled = true,
                                    findInstead = true,
                                    onAdd = { onFind(item.pin) },
                                    menu = listOf(CardAction(PosIcons.Trash2, R.string.pos_remove_popular) { onUnpin(item.pin) }),
                                )
                            } else {
                                PinCard(item.pin, grid.itemWidth, onOpen = { onActivate(item.pin) }, onRemove = { onUnpin(item.pin) })
                            }
                        }
                    }
                }
            }
        }
    }
}

data class CardAction(val icon: ImageVector, val label: Int, val onClick: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PartCard(
    part: CatalogPart,
    width: Dp,
    pinnedBadge: Boolean,
    addEnabled: Boolean,
    onAdd: () -> Unit,
    menu: List<CardAction>,
    modifier: Modifier = Modifier,
    findInstead: Boolean = false,
) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .width(width)
            .posNeuRaised()
            .clip(PosTheme.shape.md)
            .background(palette.surfacePrimary)
            // Brand-red outer lining (owner request) — gives the soft card an edge.
            .border(1.5.dp, palette.brandRed.copy(alpha = 0.7f), PosTheme.shape.md)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    if (menu.isNotEmpty()) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                },
            )
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(64.dp).clip(PosTheme.shape.sm).background(palette.canvas),
                contentAlignment = Alignment.Center,
            ) {
                PosIcon(PosIcons.Package, tint = palette.textMuted, size = 28.dp)
            }
            if (pinnedBadge) PinTag(stringResource(R.string.pos_pinned), Modifier.align(Alignment.TopStart).padding(6.dp))
            if (menu.isNotEmpty()) {
                CardMenuButton(stringResource(R.string.pos_more_actions, part.name), Modifier.align(Alignment.TopEnd)) { menuOpen = !menuOpen }
                if (menuOpen) CardMenu(menu) { menuOpen = false }
            }
        }
        Spacer(Modifier.height(8.dp))
        PosText(part.name, type.labelAction.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        PosText(part.oemPartNumber, type.monoReference, palette.textMuted, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosText(
                when {
                    part.price != null -> formatMoney(part.price!!)
                    findInstead -> ""
                    else -> stringResource(R.string.pos_needs_price)
                },
                type.numericPrice,
                palette.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            part.saleableQty?.let { qty ->
                PosText(
                    if (qty > 0) stringResource(R.string.pos_in_stock, formatQty(qty)) else stringResource(R.string.pos_out_of_stock),
                    type.labelMeta,
                    if (qty > 0) palette.success else palette.error,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SoftButton(
            label = stringResource(if (findInstead) R.string.pos_find else R.string.pos_add),
            icon = if (findInstead) PosIcons.Search else PosIcons.ShoppingCart,
            enabled = addEnabled && (findInstead || part.canAdd),
            onClick = onAdd,
        )
    }
}

@Composable
private fun PinCard(pin: PopularPin, width: Dp, onOpen: () -> Unit, onRemove: () -> Unit) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(width)
            .posNeuRaised()
            .clip(PosTheme.shape.md)
            .background(palette.surfacePrimary)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(64.dp).clip(PosTheme.shape.sm).background(palette.canvas),
                contentAlignment = Alignment.Center,
            ) {
                PosIcon(if (pin.kind == PinKind.MODEL) PosIcons.Car else PosIcons.Tag, tint = palette.textMuted, size = 30.dp)
            }
            PinTag(
                stringResource(
                    when (pin.kind) {
                        PinKind.MODEL -> R.string.pos_pin_kind_vehicle
                        PinKind.CATEGORY -> R.string.pos_pin_kind_category
                        else -> R.string.pos_pin_kind_subcategory
                    },
                ),
                Modifier.align(Alignment.TopStart).padding(6.dp),
            )
            CardMenuButton(stringResource(R.string.pos_more_actions, pin.label), Modifier.align(Alignment.TopEnd)) { menuOpen = !menuOpen }
            if (menuOpen) CardMenu(listOf(CardAction(PosIcons.Trash2, R.string.pos_remove_popular, onRemove))) { menuOpen = false }
        }
        Spacer(Modifier.height(10.dp))
        PosText(pin.label, type.labelAction.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        PosText(pin.subtitle ?: "", type.monoReference, palette.textMuted, maxLines = 1)
        Spacer(Modifier.height(22.dp))
        SoftButton(
            label = stringResource(if (pin.kind == PinKind.MODEL) R.string.pos_shop_vehicle else R.string.pos_show_parts),
            icon = null,
            enabled = true,
            onClick = onOpen,
        )
    }
}

@Composable
private fun PinTag(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(PosTheme.shape.pill)
            .background(PosTheme.palette.navBackground)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PosIcon(PosIcons.Pin, tint = PosTheme.palette.textOnBrand, size = 10.dp)
        PosText(text, PosTheme.type.labelMeta, PosTheme.palette.textOnBrand, maxLines = 1)
    }
}

@Composable
private fun CardMenuButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(32.dp)
            .clip(PosTheme.shape.sm)
            .posFocusRing()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PosIcon(PosIcons.EllipsisVertical, tint = PosTheme.palette.textSecondary, size = 18.dp, contentDescription = label)
    }
}

@Composable
private fun CardMenu(actions: List<CardAction>, onDismiss: () -> Unit) {
    val offset = with(LocalDensity.current) { IntOffset(0, 34.dp.roundToPx()) }
    Popup(alignment = Alignment.TopEnd, offset = offset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .width(220.dp)
                .posNeuRaised()
                .clip(PosTheme.shape.md)
                .background(PosTheme.palette.surfaceElevated)
                .padding(vertical = 6.dp),
        ) {
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            onDismiss()
                            action.onClick()
                        }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PosIcon(action.icon, tint = PosTheme.palette.textSecondary, size = 16.dp)
                    PosText(stringResource(action.label), PosTheme.type.bodyPrimary, PosTheme.palette.textPrimary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SoftButton(label: String, icon: ImageVector?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PosTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .posNeuRaisedSmall()
            .clip(PosTheme.shape.sm)
            .background(palette.surfacePrimary)
            .posFocusRing()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PosIcon(icon, tint = palette.textPrimary, size = 16.dp)
            Spacer(Modifier.width(8.dp))
        }
        PosText(label, PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary, maxLines = 1)
    }
}

@Composable
private fun ScrollButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(PosTheme.shape.sm)
            .posFocusRing()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PosIcon(icon, tint = PosTheme.palette.textPrimary, size = 18.dp, contentDescription = label)
    }
}

@Composable
fun SectionHead(title: String, modifier: Modifier = Modifier, tools: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PosText(title, PosTheme.type.heading2.copy(fontWeight = FontWeight.Bold), PosTheme.palette.textPrimary, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { tools() }
    }
}

@Composable
fun EmptyCard(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(PosTheme.shape.md)
            .background(PosTheme.palette.surfacePrimary)
            .padding(20.dp),
    ) {
        PosText(text, PosTheme.type.bodyPrimary, PosTheme.palette.textMuted)
    }
}

// ---------------------------------------------------------------- Recent searches (SHELL-13)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosRecentSearches(recent: List<String>, onOpen: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    if (recent.isEmpty()) return
    val palette = PosTheme.palette
    Column(modifier.fillMaxWidth()) {
        SectionHead(stringResource(R.string.pos_recent_title)) {
            PosText(
                stringResource(R.string.pos_clear_all),
                PosTheme.type.labelAction,
                palette.brandRed,
                modifier = Modifier.clip(PosTheme.shape.sm).clickable(role = Role.Button, onClick = onClear).padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { q ->
                Row(
                    Modifier
                        .posNeuRaisedSmall(cornerRadius = 18.dp)
                        .clip(PosTheme.shape.pill)
                        .background(palette.surfacePrimary)
                        .clickable(role = Role.Button) { onOpen(q) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PosIcon(PosIcons.Clock, tint = palette.textMuted, size = 14.dp)
                    PosText(q, PosTheme.type.bodyPrimary, palette.textPrimary, maxLines = 1)
                }
            }
        }
    }
}
