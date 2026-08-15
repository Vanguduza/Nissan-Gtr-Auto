package co.zw.nissangtr.pos.till

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.ChassisShortcut
import co.zw.nissangtr.pos.api.FitmentRules
import co.zw.nissangtr.pos.api.TillFakeState
import co.zw.nissangtr.pos.api.TillItem
import co.zw.nissangtr.pos.api.TicketSnapshot
import co.zw.nissangtr.pos.api.TillStaffSession
import co.zw.nissangtr.pos.api.VehicleLatch
import co.zw.nissangtr.pos.lookup.FacetChipsRow
import co.zw.nissangtr.pos.lookup.FinderMode
import co.zw.nissangtr.pos.lookup.FinderModesRow
import co.zw.nissangtr.pos.lookup.SpareTile
import co.zw.nissangtr.pos.lookup.VehicleLatchChrome
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Sole till chrome (§15.1). Callers fill every expanded slot.
 * Weights: finder 0.58 · ticket 0.37 · rail 0.05.
 */
@Composable
fun TillScaffold(
    layoutMode: TillLayoutMode,
    session: TillStaffSession,
    latch: VehicleLatch?,
    finderMode: FinderMode,
    searchQuery: String,
    selectedCategory: String?,
    inStockOnly: Boolean,
    categories: List<String>,
    tiles: List<TillItem>,
    selectedOem: String?,
    ticket: TicketSnapshot,
    statusLabel: String,
    online: Boolean,
    clockText: String,
    onClearLatch: () -> Unit,
    onFinderMode: (FinderMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onInStockToggle: () -> Unit,
    onTileClick: (TillItem) -> Unit,
    onCustomer: () -> Unit,
    onOrders: () -> Unit,
    onPark: () -> Unit,
    onVoid: () -> Unit,
    onPay: () -> Unit,
    onScan: () -> Unit = {},
    onPrint: () -> Unit = {},
    onSync: () -> Unit = {},
    onPriceCheck: () -> Unit = {},
    onDrawer: () -> Unit = {},
    onSettings: () -> Unit = {},
    chassisShortcuts: List<ChassisShortcut> = emptyList(),
    onChassisChip: (ChassisShortcut) -> Unit = {},
    modifier: Modifier = Modifier,
    banner: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrColors.Steel),
    ) {
        TillHeader(
            session = session,
            latch = latch,
            clockText = clockText,
            onClearLatch = onClearLatch,
            onCustomer = onCustomer,
            onOrders = onOrders,
        )
        when (layoutMode) {
            TillLayoutMode.Expanded ->             ExpandedBody(
                finderMode = finderMode,
                searchQuery = searchQuery,
                selectedCategory = selectedCategory,
                inStockOnly = inStockOnly,
                categories = categories,
                tiles = tiles,
                selectedOem = selectedOem,
                latch = latch,
                ticket = ticket,
                banner = banner,
                chassisShortcuts = chassisShortcuts,
                onFinderMode = onFinderMode,
                onSearchChange = onSearchChange,
                onCategory = onCategory,
                onInStockToggle = onInStockToggle,
                onTileClick = onTileClick,
                onChassisChip = onChassisChip,
                onPark = onPark,
                onVoid = onVoid,
                onPay = onPay,
                onScan = onScan,
                onPrint = onPrint,
                onSync = onSync,
                onPriceCheck = onPriceCheck,
                onDrawer = onDrawer,
                onSettings = onSettings,
                modifier = Modifier.weight(1f),
            )
            TillLayoutMode.Compact -> CompactBody(
                finderMode = finderMode,
                searchQuery = searchQuery,
                selectedCategory = selectedCategory,
                inStockOnly = inStockOnly,
                categories = categories,
                tiles = tiles,
                selectedOem = selectedOem,
                latch = latch,
                ticket = ticket,
                banner = banner,
                chassisShortcuts = chassisShortcuts,
                onFinderMode = onFinderMode,
                onSearchChange = onSearchChange,
                onCategory = onCategory,
                onInStockToggle = onInStockToggle,
                onTileClick = onTileClick,
                onChassisChip = onChassisChip,
                onPark = onPark,
                onVoid = onVoid,
                onPay = onPay,
                modifier = Modifier.weight(1f),
            )
        }
        StatusBar(statusLabel = statusLabel, online = online)
    }
}

@Composable
private fun TillHeader(
    session: TillStaffSession,
    latch: VehicleLatch?,
    clockText: String,
    onClearLatch: () -> Unit,
    onCustomer: () -> Unit,
    onOrders: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp, max = 64.dp)
            .background(GtrColors.Steel)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // brand
        Column(modifier = Modifier.weight(0.22f)) {
            Text(
                text = "GT-R · NISSAN GTR AUTO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = GtrColors.Chalk,
            )
            Text(
                text = "${session.staffName} · Till · ${session.warehouseLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = GtrColors.SilverDim,
            )
        }
        // vehicleLatch (center)
        VehicleLatchChrome(
            latch = latch,
            onClear = onClearLatch,
            modifier = Modifier.weight(0.36f),
        )
        // customer / orders
        Row(
            modifier = Modifier.weight(0.28f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCustomer) {
                Text("Select customer", color = GtrColors.Silver)
            }
            TextButton(onClick = onOrders) {
                Text("Orders", color = GtrColors.Silver)
            }
        }
        // clock
        Text(
            text = clockText,
            style = MaterialTheme.typography.titleSmall,
            color = GtrColors.Chalk,
            modifier = Modifier.weight(0.14f),
        )
    }
}

@Composable
private fun ExpandedBody(
    finderMode: FinderMode,
    searchQuery: String,
    selectedCategory: String?,
    inStockOnly: Boolean,
    categories: List<String>,
    tiles: List<TillItem>,
    selectedOem: String?,
    latch: VehicleLatch?,
    ticket: TicketSnapshot,
    banner: String?,
    chassisShortcuts: List<ChassisShortcut>,
    onFinderMode: (FinderMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onInStockToggle: () -> Unit,
    onTileClick: (TillItem) -> Unit,
    onChassisChip: (ChassisShortcut) -> Unit,
    onPark: () -> Unit,
    onVoid: () -> Unit,
    onPay: () -> Unit,
    onScan: () -> Unit,
    onPrint: () -> Unit,
    onSync: () -> Unit = {},
    onPriceCheck: () -> Unit = {},
    onDrawer: () -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        FinderPane(
            finderMode = finderMode,
            searchQuery = searchQuery,
            selectedCategory = selectedCategory,
            inStockOnly = inStockOnly,
            categories = categories,
            tiles = tiles,
            selectedOem = selectedOem,
            latch = latch,
            columnCount = 4,
            banner = banner,
            chassisShortcuts = chassisShortcuts,
            onFinderMode = onFinderMode,
            onSearchChange = onSearchChange,
            onCategory = onCategory,
            onInStockToggle = onInStockToggle,
            onTileClick = onTileClick,
            onChassisChip = onChassisChip,
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .padding(8.dp),
        )
        TicketPane(
            ticket = ticket,
            onPark = onPark,
            onVoid = onVoid,
            onPay = onPay,
            modifier = Modifier
                .weight(0.37f)
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 4.dp),
        )
        UtilityRail(
            onSync = onSync,
            onScan = onScan,
            onPrint = onPrint,
            onDrawer = onDrawer,
            onInfo = onPriceCheck,
            onSettings = onSettings,
            modifier = Modifier
                .weight(0.05f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun CompactBody(
    finderMode: FinderMode,
    searchQuery: String,
    selectedCategory: String?,
    inStockOnly: Boolean,
    categories: List<String>,
    tiles: List<TillItem>,
    selectedOem: String?,
    latch: VehicleLatch?,
    ticket: TicketSnapshot,
    banner: String?,
    chassisShortcuts: List<ChassisShortcut>,
    onFinderMode: (FinderMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onInStockToggle: () -> Unit,
    onTileClick: (TillItem) -> Unit,
    onChassisChip: (ChassisShortcut) -> Unit,
    onPark: () -> Unit,
    onVoid: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinderPane(
            finderMode = finderMode,
            searchQuery = searchQuery,
            selectedCategory = selectedCategory,
            inStockOnly = inStockOnly,
            categories = categories,
            tiles = tiles,
            selectedOem = selectedOem,
            latch = latch,
            columnCount = 2,
            banner = banner,
            chassisShortcuts = chassisShortcuts,
            onFinderMode = onFinderMode,
            onSearchChange = onSearchChange,
            onCategory = onCategory,
            onInStockToggle = onInStockToggle,
            onTileClick = onTileClick,
            onChassisChip = onChassisChip,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
        )
        TicketPane(
            ticket = ticket,
            onPark = onPark,
            onVoid = onVoid,
            onPay = onPay,
            compactBar = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FinderPane(
    finderMode: FinderMode,
    searchQuery: String,
    selectedCategory: String?,
    inStockOnly: Boolean,
    categories: List<String>,
    tiles: List<TillItem>,
    selectedOem: String?,
    latch: VehicleLatch?,
    columnCount: Int,
    banner: String?,
    chassisShortcuts: List<ChassisShortcut>,
    onFinderMode: (FinderMode) -> Unit,
    onSearchChange: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onInStockToggle: () -> Unit,
    onTileClick: (TillItem) -> Unit,
    onChassisChip: (ChassisShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FinderModesRow(selected = finderMode, onSelect = onFinderMode)
        SearchField(query = searchQuery, onChange = onSearchChange)
        ChassisShortcutChips(
            chips = chassisShortcuts,
            selectedChassis = latch?.chassisCode,
            onSelect = onChassisChip,
        )
        banner?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color = GtrColors.Warning,
            )
        }
        FacetChipsRow(
            categories = categories,
            selectedCategory = selectedCategory,
            inStockOnly = inStockOnly,
            onCategory = onCategory,
            onInStockToggle = onInStockToggle,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(tiles, key = { it.oemPartNumber }) { item ->
                SpareTile(
                    item = item,
                    badge = FitmentRules.badge(item, latch),
                    selected = item.oemPartNumber == selectedOem,
                    onClick = { onTileClick(item) },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(GtrColors.SteelLift, GtrShapes.small)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(
                text = "OEM / OE / VIN / PNC + barcode",
                color = GtrColors.SilverDim,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = GtrColors.Chalk),
            cursorBrush = SolidColor(GtrColors.Primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusBar(
    statusLabel: String,
    online: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GtrColors.SteelLift)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (online) "Online" else "Offline",
            color = if (online) GtrColors.StockIn else GtrColors.Warning,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = statusLabel,
            color = GtrColors.SilverDim,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun TillScreen(
    state: TillUiState,
    onClearLatch: () -> Unit = {},
    onFinderMode: (FinderMode) -> Unit = {},
    onSearchChange: (String) -> Unit = {},
    onCategory: (String?) -> Unit = {},
    onInStockToggle: () -> Unit = {},
    onTileClick: (TillItem) -> Unit = {},
    onCustomer: () -> Unit = {},
    onOrders: () -> Unit = {},
    onPark: () -> Unit = {},
    onVoid: () -> Unit = {},
    onPay: () -> Unit = {},
    onScan: () -> Unit = {},
    onPrint: () -> Unit = {},
    onSync: () -> Unit = {},
    onPriceCheck: () -> Unit = {},
    onDrawer: () -> Unit = {},
    onSettings: () -> Unit = {},
    onChassisChip: (ChassisShortcut) -> Unit = {},
    clockText: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    modifier: Modifier = Modifier,
) {
    TillScaffold(
        layoutMode = state.layoutMode,
        session = state.fake.session,
        latch = state.latch,
        finderMode = state.finderMode,
        searchQuery = state.searchQuery,
        selectedCategory = state.selectedCategory,
        inStockOnly = state.inStockOnly,
        categories = listOf("Brakes", "Engine", "Body"),
        tiles = state.visibleTiles,
        selectedOem = state.selectedOem,
        ticket = state.fake.ticket,
        statusLabel = state.fake.statusLabel,
        online = state.fake.online,
        clockText = clockText,
        onClearLatch = onClearLatch,
        onFinderMode = onFinderMode,
        onSearchChange = onSearchChange,
        onCategory = onCategory,
        onInStockToggle = onInStockToggle,
        onTileClick = onTileClick,
        onCustomer = onCustomer,
        onOrders = onOrders,
        onPark = onPark,
        onVoid = onVoid,
        onPay = onPay,
        onScan = onScan,
        onPrint = onPrint,
        onSync = onSync,
        onPriceCheck = onPriceCheck,
        onDrawer = onDrawer,
        onSettings = onSettings,
        chassisShortcuts = state.chassisShortcuts,
        onChassisChip = onChassisChip,
        banner = state.banner,
        modifier = modifier,
    )
}

fun tillUiStateFromFake(
    fake: TillFakeState,
    layoutMode: TillLayoutMode,
): TillUiState = TillUiState(layoutMode = layoutMode, fake = fake)
