package co.zw.nissangtr.pos.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.pos.design.icons.BookOpen
import co.zw.nissangtr.pos.design.icons.FileText
import co.zw.nissangtr.pos.design.icons.House
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.icons.Settings
import co.zw.nissangtr.pos.design.icons.Undo2
import co.zw.nissangtr.pos.design.icons.Wallet
import co.zw.nissangtr.pos.design.primitives.posFocusRing
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosText

data class RailItem(val destination: PosDestination, val label: Int, val icon: ImageVector)

/** Canonical rail order (Blueprint §6.1). Adding `Reports` here is forbidden (D-001). */
val RailItems: List<RailItem> = listOf(
    RailItem(PosDestination.Home, R.string.pos_nav_home, PosIcons.House),
    RailItem(PosDestination.SearchSpares, R.string.pos_nav_search, PosIcons.Search),
    RailItem(PosDestination.QuickSale, R.string.pos_nav_quick_sale, PosIcons.ShoppingCart),
    RailItem(PosDestination.Customer, R.string.pos_nav_customer, PosIcons.User),
    RailItem(PosDestination.Orders, R.string.pos_nav_orders, PosIcons.FileText),
    RailItem(PosDestination.Returns, R.string.pos_nav_returns, PosIcons.Undo2),
    RailItem(PosDestination.EpcBrowse, R.string.pos_nav_epc, PosIcons.BookOpen),
    RailItem(PosDestination.Till, R.string.pos_nav_till, PosIcons.Wallet),
    RailItem(PosDestination.Settings, R.string.pos_nav_settings, PosIcons.Settings),
)

@Composable
fun PosRail(active: PosDestination, onSelect: (PosDestination) -> Unit, modifier: Modifier = Modifier) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    // §3.5: full rail at Expanded (144 dp); icon rail at Medium / Compact landscape.
    val iconOnly = PosTheme.geometry.railWidth < 120.dp
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.pos_brand_logo),
            contentDescription = stringResource(R.string.pos_brand_name),
            modifier = Modifier.size(if (iconOnly) 56.dp else 88.dp).clip(PosTheme.shape.md),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RailItems.forEach { item ->
                val selected = item.destination == active
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(PosTheme.shape.sm)
                        .background(if (selected) palette.navActiveFill else palette.navBackground)
                        .posFocusRing()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.destination) })
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (iconOnly) Arrangement.Center else Arrangement.spacedBy(8.dp),
                ) {
                    val tint = if (selected) palette.textOnBrand else palette.textMuted
                    PosIcon(item.icon, tint = tint, size = 20.dp, contentDescription = if (iconOnly) stringResource(item.label) else null)
                    if (!iconOnly) PosText(
                        text = stringResource(item.label),
                        style = type.labelAction.copy(fontSize = 12.5.sp, letterSpacing = 0.em, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (selected) palette.textOnBrand else palette.textMuted,
                        maxLines = 1,
                    )
                }
            }
        }
        if (!iconOnly) {
            // GT-R artwork takes whatever height the destinations leave, up to its natural size.
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
                Image(
                    painter = painterResource(R.drawable.pos_nav_car_locked),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                )
            }
            Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
                PosText(
                    text = stringResource(R.string.pos_brand_statement),
                    style = type.labelMeta.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.18.em, lineHeight = type.labelMeta.lineHeight * 1.3f),
                    color = palette.textOnBrand,
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(24.dp).height(3.dp).background(palette.brandRed))
            }
        }
    }
}

/** Compact portrait: the same eight destinations as a bottom navigation bar (Blueprint §9). */
@Composable
fun PosBottomNav(active: PosDestination, onSelect: (PosDestination) -> Unit, modifier: Modifier = Modifier) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RailItems.forEach { item ->
            val selected = item.destination == active
            Column(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(PosTheme.shape.sm)
                    .background(if (selected) palette.navActiveFill else palette.navBackground)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.destination) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (selected) palette.textOnBrand else palette.textMuted
                PosIcon(item.icon, tint = tint, size = 20.dp, contentDescription = stringResource(item.label))
                PosText(
                    stringResource(item.label).substringBefore(' '),
                    type.labelMeta.copy(fontSize = 10.sp),
                    tint,
                    maxLines = 1,
                )
            }
        }
    }
}
