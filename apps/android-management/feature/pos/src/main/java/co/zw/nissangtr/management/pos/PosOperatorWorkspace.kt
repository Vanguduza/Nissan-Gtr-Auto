package co.zw.nissangtr.management.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.management.rpc.CatalogPartHit
import co.zw.nissangtr.management.rpc.CatalogSearchMode
import co.zw.nissangtr.management.rpc.EpcModel
import co.zw.nissangtr.management.rpc.PopularPosSpare
import co.zw.nissangtr.management.rpc.PosInvoiceSummary
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopWarmTheme
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrLogo

/** Canonical operator-facing POS destinations locked by the 2026-09-07 kiosk design. */
internal enum class OperatorPosDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home("Home", Icons.Filled.Home),
    SearchSpares("Search Spares", Icons.Filled.Search),
    QuickSale("Quick Sale", Icons.Filled.PointOfSale),
    Customer("Customer", Icons.Filled.AccountCircle),
    Orders("Orders", Icons.Filled.ReceiptLong),
    Returns("Returns", Icons.Filled.CompareArrows),
    EpcBrowse("EPC Browse", Icons.Filled.Build),
    Settings("Settings", Icons.Filled.Settings),
}

private data class OperatorCategory(
    val label: String,
    val query: String,
    val icon: ImageVector,
)

private val operatorCategories = listOf(
    OperatorCategory("Engine & Drivetrain", "engine drivetrain", Icons.Filled.Build),
    OperatorCategory("Brakes", "brake", Icons.Filled.PointOfSale),
    OperatorCategory("Suspension", "suspension", Icons.Filled.DirectionsCar),
    OperatorCategory("Body & Exterior", "body exterior", Icons.Filled.DirectionsCar),
    OperatorCategory("Electrical", "electrical", Icons.Filled.Inventory2),
    OperatorCategory("Fluids & Chemicals", "oil fluid coolant", Icons.Filled.Inventory2),
    OperatorCategory("Accessories", "accessory", Icons.Filled.Star),
)

/**
 * Tablet-first operator shell. The right sale pane remains persistent on wide displays while
 * discovery/operations change in the center. All mutations stay in [PosViewModel]/RpcClient.
 */
@Composable
internal fun PosOperatorWorkspace(
    state: PosUiState,
    viewModel: PosViewModel,
    operatorLabel: String?,
    onOpenHub: (() -> Unit)?,
    onOpenStaffPortal: (() -> Unit)? = null,
    onOpenKioskSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ShopWarmTheme(darkTheme = false) {
        var destination by remember { mutableStateOf(OperatorPosDestination.Home) }
        var searchDraft by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }
        val recentSearches = remember { mutableStateListOf<String>() }

        fun submitSearch(query: String = searchDraft) {
            val q = query.trim()
            if (q.isEmpty()) return
            searchDraft = q
            viewModel.onSearchQueryChange(q)
            recentSearches.remove(q)
            recentSearches.add(0, q)
            while (recentSearches.size > 5) recentSearches.removeLast()
            viewModel.searchCatalog()
            destination = OperatorPosDestination.SearchSpares
        }

        LaunchedEffect(destination) {
            when (destination) {
                OperatorPosDestination.Orders -> {
                    if (!state.showQuotes) viewModel.toggleQuotes()
                    viewModel.refreshOperatorDiscovery()
                }
                OperatorPosDestination.Returns -> viewModel.refreshOperatorDiscovery()
                else -> Unit
            }
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 690.dp),
        ) {
            val wide = maxWidth >= 1050.dp
            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    OperatorNavigationRail(
                        selected = destination,
                        onSelect = { destination = it },
                        onOpenHub = onOpenHub,
                        modifier = Modifier
                            .width(168.dp)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OperatorSearchHeader(
                            searchDraft = searchDraft,
                            onSearchDraftChange = { searchDraft = it },
                            onSubmit = { submitSearch() },
                            onScan = viewModel::tillScanAddLine,
                            state = state,
                            viewModel = viewModel,
                            operatorLabel = operatorLabel,
                        )
                        OperatorDestinationContent(
                            destination = destination,
                            state = state,
                            viewModel = viewModel,
                            recentSearches = recentSearches,
                            onSearch = ::submitSearch,
                            onDestination = { destination = it },
                            onOpenStaffPortal = onOpenStaffPortal,
                            onOpenKioskSettings = onOpenKioskSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight()
                            .padding(top = 14.dp, end = 14.dp, bottom = 14.dp),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp,
                        shadowElevation = 2.dp,
                    ) {
                        RightCartPane(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            onCustomerClick = { destination = OperatorPosDestination.Customer },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CompactDestinationBar(
                        selected = destination,
                        onSelect = { destination = it },
                    )
                    OperatorSearchHeader(
                        searchDraft = searchDraft,
                        onSearchDraftChange = { searchDraft = it },
                        onSubmit = { submitSearch() },
                        onScan = viewModel::tillScanAddLine,
                        state = state,
                        viewModel = viewModel,
                        operatorLabel = operatorLabel,
                    )
                    OperatorDestinationContent(
                        destination = destination,
                        state = state,
                        viewModel = viewModel,
                        recentSearches = recentSearches,
                        onSearch = ::submitSearch,
                        onDestination = { destination = it },
                        onOpenStaffPortal = onOpenStaffPortal,
                        onOpenKioskSettings = onOpenKioskSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RightCartPane(
                        state = state,
                        viewModel = viewModel,
                        onCustomerClick = { destination = OperatorPosDestination.Customer },
                    )
                }
            }
        }
    }
}

@Composable
private fun OperatorNavigationRail(
    selected: OperatorPosDestination,
    onSelect: (OperatorPosDestination) -> Unit,
    onOpenHub: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(GtrColors.Steel)
            .padding(horizontal = 10.dp, vertical = 18.dp),
    ) {
        GtrLogo(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        OperatorPosDestination.entries.forEach { item ->
            val selectedItem = item == selected
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) },
                color = if (selectedItem) GtrColors.Primary else Color.Transparent,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(21.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        if (onOpenHub != null) {
            Text(
                "ALL MODULES",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenHub() }
                    .padding(vertical = 10.dp),
                color = GtrColors.Silver,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            "BUILT FOR A\nHIGHER STANDARD",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.width(34.dp).height(3.dp),
            color = GtrColors.Primary,
            shape = RoundedCornerShape(2.dp),
        ) {}
    }
}

@Composable
private fun CompactDestinationBar(
    selected: OperatorPosDestination,
    onSelect: (OperatorPosDestination) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(OperatorPosDestination.entries) { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelect(item) },
                label = { Text(item.label) },
                leadingIcon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun OperatorSearchHeader(
    searchDraft: String,
    onSearchDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onScan: () -> Unit,
    state: PosUiState,
    viewModel: PosViewModel,
    operatorLabel: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = searchDraft,
                onValueChange = onSearchDraftChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Search by part name, OEM number, VIN or vehicle model…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onScan, enabled = !state.busy) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan part")
                    }
                },
            )
            Button(onClick = onSubmit, enabled = !state.busy && searchDraft.isNotBlank()) {
                Text("Search")
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    operatorLabel?.substringBefore('@')?.ifBlank { "Operator" } ?: "Operator",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (state.isOffline) "POS Kiosk · Offline" else "POS Kiosk · Online",
                    color = if (state.isOffline) MaterialTheme.colorScheme.error else GtrColors.Accent,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VehicleCascadeDropdown(
                label = "Model",
                selected = state.selectedVehicleModelName,
                options = state.vehicleModels,
                optionLabel = { it.displayName },
                onSelect = viewModel::selectVehicleModel,
                enabled = !state.busy && !state.isOffline,
                modifier = Modifier.weight(1f),
            )
            VehicleCascadeDropdown(
                label = "Generation",
                selected = state.selectedVehicleGeneration,
                options = state.vehicleGenerations,
                optionLabel = { it.label },
                onSelect = viewModel::selectVehicleGeneration,
                enabled = !state.busy && state.selectedVehicleModelSlug.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            VehicleCascadeDropdown(
                label = "Engine",
                selected = state.selectedVehicleEngineCode,
                options = state.vehicleEngines,
                optionLabel = { it },
                onSelect = viewModel::selectVehicleEngine,
                enabled = !state.busy && state.selectedVehicleChassisCode.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            if (state.saleVehicle != null) {
                OutlinedButton(onClick = viewModel::clearVehicleSelection, enabled = !state.busy) {
                    Text("Clear")
                }
            }
        }
        if (state.saleVehicle == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CatalogSearchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.searchMode == mode,
                        onClick = { viewModel.onSearchModeChange(mode) },
                        label = { Text(modeLabel(mode)) },
                        enabled = !state.busy,
                    )
                }
            }
        } else {
            AssistChip(
                onClick = {},
                label = { Text("Fitment filter · ${state.saleVehicle.displayLabel}") },
                leadingIcon = { Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OperatorDestinationContent(
    destination: OperatorPosDestination,
    state: PosUiState,
    viewModel: PosViewModel,
    recentSearches: List<String>,
    onSearch: (String) -> Unit,
    onDestination: (OperatorPosDestination) -> Unit,
    onOpenStaffPortal: (() -> Unit)?,
    onOpenKioskSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.background,
    ) {
        when (destination) {
            OperatorPosDestination.Home -> OperatorHome(
                state = state,
                viewModel = viewModel,
                recentSearches = recentSearches,
                onSearch = onSearch,
                onDestination = onDestination,
            )
            OperatorPosDestination.SearchSpares -> OperatorSearchResults(state, viewModel, recentSearches, onSearch)
            OperatorPosDestination.QuickSale -> OperatorQuickSale(state, viewModel)
            OperatorPosDestination.Customer -> OperatorCustomer(state, viewModel)
            OperatorPosDestination.Orders -> OperatorOrders(state, viewModel)
            OperatorPosDestination.Returns -> OperatorReturns(state, viewModel)
            OperatorPosDestination.EpcBrowse -> if (state.isOffline && !state.epcCatalogAvailable) {
                ShopHonestEmpty(
                    title = "Offline EPC bundle not downloaded",
                    body = "Connect once and use POS Settings → Offline EPC catalog → Refresh full catalog. After that, EPC browse and vehicle filtering work without internet.",
                )
            } else {
                PosEpcBrowseScreen(
                    source = viewModel.epcCatalogSource(),
                    onBack = { onDestination(OperatorPosDestination.Home) },
                    onSelectOem = { oem ->
                        viewModel.addOemToCart(oem)
                        onDestination(OperatorPosDestination.QuickSale)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 560.dp, max = 720.dp),
                )
            }
            OperatorPosDestination.Settings -> OperatorSettings(
                state = state,
                viewModel = viewModel,
                onOpenStaffPortal = onOpenStaffPortal,
                onOpenKioskSettings = onOpenKioskSettings,
            )
        }
    }
}

@Composable
private fun OperatorHome(
    state: PosUiState,
    viewModel: PosViewModel,
    recentSearches: List<String>,
    onSearch: (String) -> Unit,
    onDestination: (OperatorPosDestination) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(210.dp),
            shape = MaterialTheme.shapes.large,
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF07090C), GtrColors.Steel, GtrColors.SteelLift),
                        ),
                    )
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Genuine Parts\nReal Performance",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Find the correct spare by OEM, VIN, vehicle model or EPC.",
                        color = GtrColors.Silver,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.saleVehicle?.let { vehicle ->
                        AssistChip(
                            onClick = {},
                            label = { Text(vehicle.displayLabel, color = Color.White) },
                            leadingIcon = { Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = GtrColors.Primary) },
                        )
                    } ?: Text(
                        "Select Model · Generation · Engine above to filter by exact fitment.",
                        color = GtrColors.Silver,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = GtrColors.Primary,
                    modifier = Modifier.size(118.dp),
                )
            }
        }

        Text("Browse spares", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().height(190.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gridItems(operatorCategories) { category ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clickable {
                            viewModel.onSearchModeChange(CatalogSearchMode.PNC)
                            onSearch(category.query)
                        },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(category.icon, contentDescription = null, tint = GtrColors.Steel)
                        Text(category.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Popular Spares", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "EPC Browse",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onDestination(OperatorPosDestination.EpcBrowse) },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (state.popularSpares.isEmpty()) {
            ShopHonestEmpty(
                title = "Popular spares will appear from sales history",
                body = "No posted-sales ranking is available yet. Search, scan, or use EPC Browse to find a spare.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 260.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(state.popularSpares, key = { it.stockItemId }) { spare ->
                    PopularSpareCard(spare = spare, busy = state.busy, onAdd = {
                        viewModel.addOemToCart(spare.oemPartNumber)
                    })
                }
            }
        }
        if (state.searchHits.isNotEmpty()) {
            Text("Search matches", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OperatorPartGrid(state = state, viewModel = viewModel)
        }
        RecentSearches(recentSearches, onSearch)
    }
}

@Composable
private fun HeroBadge(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = GtrColors.Primary, modifier = Modifier.size(14.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OperatorSearchResults(
    state: PosUiState,
    viewModel: PosViewModel,
    recentSearches: List<String>,
    onSearch: (String) -> Unit,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Search Spares", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "${state.searchHits.size} result(s) · ${modeLabel(state.searchMode)} search",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.searchHits.isEmpty()) {
            ShopHonestEmpty(
                title = "No matching spares yet",
                body = "Search by OEM, VIN, model or PNC. EPC Browse is available from the left rail.",
            )
        } else {
            OperatorPartGrid(state = state, viewModel = viewModel)
        }
        RecentSearches(recentSearches, onSearch)
    }
}

@Composable
private fun OperatorPartGrid(
    state: PosUiState,
    viewModel: PosViewModel,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        gridItems(state.searchHits, key = { it.oemPartNumber + (it.pncCode ?: "") }) { hit ->
            OperatorPartCard(hit = hit, busy = state.busy, onAdd = { viewModel.addPartFromCatalog(hit) })
        }
    }
}

@Composable
private fun OperatorPartCard(
    hit: CatalogPartHit,
    busy: Boolean,
    onAdd: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(66.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(
                    Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = GtrColors.SilverDim,
                    modifier = Modifier.padding(18.dp),
                )
            }
            Text(
                hit.description ?: hit.categoryName ?: "Genuine spare",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Text(hit.oemPartNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(hit.pncCode?.let { "PNC $it" }, hit.chassisCode, hit.engineCode)
                    .joinToString(" · ")
                    .ifBlank { "Catalog matched" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                hit.saleableQty?.let { "Stock ${formatOperatorQty(it)}" } ?: "Stock unavailable",
                color = if ((hit.saleableQty ?: 0.0) > 0) GtrColors.StockIn else GtrColors.StockBo,
                style = MaterialTheme.typography.labelMedium,
            )
            ShopPrimaryButton(label = "Add to sale", onClick = onAdd, enabled = !busy)
        }
    }
}

@Composable
private fun PopularSpareCard(
    spare: PopularPosSpare,
    busy: Boolean,
    onAdd: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Inventory2, contentDescription = null, tint = GtrColors.SilverDim)
            Text(
                spare.description ?: "Genuine spare",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(spare.oemPartNumber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    spare.unitPrice?.let { price ->
                        "Retail ${spare.currency?.rpcValue ?: ""} ${"%.2f".format(price)}".trim()
                    } ?: "Price on add",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Stock ${formatOperatorQty(spare.saleableQty)}",
                    color = if (spare.saleableQty > 0) GtrColors.StockIn else GtrColors.StockBo,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text("${formatOperatorQty(spare.unitsSold)} sold / 90d", style = MaterialTheme.typography.labelSmall)
            ShopPrimaryButton(label = "Add", onClick = onAdd, enabled = !busy)
        }
    }
}

@Composable
private fun RecentSearches(
    recentSearches: List<String>,
    onSearch: (String) -> Unit,
) {
    if (recentSearches.isEmpty()) return
    Text("Recent searches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recentSearches) { query ->
            AssistChip(onClick = { onSearch(query) }, label = { Text(query) })
        }
    }
}

@Composable
private fun OperatorQuickSale(state: PosUiState, viewModel: PosViewModel) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Quick Sale", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Set the sale context, then use the persistent Current Sale pane to take payment.")
        CartSetupSection(state, viewModel)
        CartActionTriggers(state, viewModel)
    }
}

@Composable
private fun OperatorCustomer(state: PosUiState, viewModel: PosViewModel) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Customer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Find and attach a registered or trade customer to the current sale.")
        CartSetupSection(state, viewModel)
    }
}

@Composable
private fun OperatorOrders(state: PosUiState, viewModel: PosViewModel) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Orders & Sales", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Find posted counter sales, create/send quotations, or resume a parked sale.")
        InvoiceSearchControls(state = state, viewModel = viewModel)
        if (state.recentInvoices.isEmpty()) {
            ShopHonestEmpty(
                title = "No posted sales found",
                body = "Search by invoice number or customer. Quotations remain available below.",
            )
        } else {
            state.recentInvoices.take(12).forEach { invoice ->
                InvoiceSummaryCard(invoice = invoice)
            }
        }
        HorizontalDivider()
        QuotesPanel(state, viewModel)
        HorizontalDivider()
        ParkedAndPairingSection(state, viewModel)
    }
}

@Composable
private fun OperatorReturns(state: PosUiState, viewModel: PosViewModel) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Returns & Refunds", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Find the original posted invoice. Refunds require live manager approval and post through the finance refund pipeline.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InvoiceSearchControls(state = state, viewModel = viewModel)
        if (state.recentInvoices.isEmpty()) {
            ShopHonestEmpty(
                title = "No eligible invoice found",
                body = "Search by receipt/invoice number or customer name.",
            )
        } else {
            state.recentInvoices.take(20).forEach { invoice ->
                InvoiceSummaryCard(
                    invoice = invoice,
                    actionLabel = "Refund",
                    actionEnabled = !state.busy && !state.isOffline,
                    onAction = { viewModel.requestRefund(invoice) },
                )
            }
        }
    }
}

@Composable
private fun InvoiceSearchControls(state: PosUiState, viewModel: PosViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.invoiceQuery,
            onValueChange = viewModel::onInvoiceQueryChange,
            label = { Text("Invoice / receipt / customer") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            enabled = !state.busy && !state.isOffline,
        )
        Button(
            onClick = viewModel::searchRecentInvoices,
            enabled = !state.busy && !state.isOffline,
        ) { Text("Find") }
    }
}

@Composable
private fun InvoiceSummaryCard(
    invoice: PosInvoiceSummary,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = GtrColors.Steel)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    invoice.documentNumber ?: invoice.id.take(12),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    listOfNotNull(invoice.customerName, invoice.postedAt).joinToString(" · ").ifBlank { "Posted sale" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                invoice.vehicleLabel?.let { vehicle ->
                    Text(
                        vehicle,
                        style = MaterialTheme.typography.labelSmall,
                        color = GtrColors.SteelLift,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "${invoice.currency.rpcValue} ${"%.2f".format(invoice.total)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun OperatorSettings(
    state: PosUiState,
    viewModel: PosViewModel,
    onOpenStaffPortal: (() -> Unit)?,
    onOpenKioskSettings: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("POS Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Staff", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Reauthenticate to open the staff management portal. Only modules permitted for the signed-in staff identity are displayed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ShopPrimaryButton(
                    label = "Open staff portal",
                    onClick = { onOpenStaffPortal?.invoke() },
                    enabled = onOpenStaffPortal != null,
                )
            }
        }
        if (onOpenKioskSettings != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kiosk & device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Lock Task, boot/home ownership, idle lock, Wi-Fi/Bluetooth system access, bridge diagnostics, reboot and audited maintenance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ShopPrimaryButton(label = "Open kiosk settings", onClick = onOpenKioskSettings)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offline EPC catalog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (state.epcCatalogAvailable) {
                        "Complete encrypted local catalog available · browse and vehicle-filter offline"
                    } else {
                        "Full EPC bundle has not been cached on this tablet yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.epcCatalogSyncedAtEpochMs?.let {
                    Text("Last local bundle: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))}", style = MaterialTheme.typography.labelSmall)
                }
                ShopSecondaryButton(
                    label = if (state.epcSyncBusy) "Syncing full catalog…" else "Refresh full offline EPC bundle",
                    onClick = viewModel::refreshOfflineEpcCatalog,
                    enabled = !state.epcSyncBusy && !state.isOffline,
                )
            }
        }
        OfflineStatusBanner(state, viewModel)
        PrinterSection(state, viewModel)
        CompanionSection(state, viewModel)
    }
}

@Composable
private fun <T> VehicleCascadeDropdown(
    label: String,
    selected: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selected.ifBlank { "Select" },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

private fun modeLabel(mode: CatalogSearchMode): String = when (mode) {
    CatalogSearchMode.PART -> "Part / OEM"
    CatalogSearchMode.VIN -> "VIN"
    CatalogSearchMode.MODEL -> "Vehicle model"
    CatalogSearchMode.PNC -> "PNC"
}

private fun formatOperatorQty(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.1f".format(qty)
