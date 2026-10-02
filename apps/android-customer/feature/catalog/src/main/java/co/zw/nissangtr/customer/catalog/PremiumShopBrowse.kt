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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.visual.CollapsedVehicleCard
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumProductCard
import co.zw.nissangtr.customer.visual.ProductImageStorage
import co.zw.nissangtr.customer.visual.R

enum class CatalogLanding { Home, Shop }

/**
 * Commerce-first Shop tab. It deliberately searches only [state.browseItems], which are already
 * scoped by the existing selected-fitment backend call. This prevents search from escaping the
 * chosen vehicle context and avoids customer-facing part-number search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumShopBrowse(
    state: CatalogUiState,
    wishOems: Set<String>,
    onOpenProduct: (String) -> Unit,
    onToggleWish: (CatalogListItem) -> Unit,
    onAddToCart: (CatalogListItem) -> Unit,
    onConfirmCascade: (String, String, String, String?) -> Unit,
    onConfirmVin: (String) -> Unit,
    onClearVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var vehicleSheetOpen by remember { mutableStateOf(false) }
    var selectionAtOpen by remember { mutableStateOf(state.selectedFitment) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.selectedFitment, vehicleSheetOpen) {
        if (
            vehicleSheetOpen &&
            state.selectedFitment != null &&
            state.selectedFitment != selectionAtOpen &&
            state.vehicleError == null
        ) {
            vehicleSheetOpen = false
        }
    }

    val categories = remember(state.browseItems) {
        state.browseItems.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .take(12)
    }
    val filtered = remember(state.browseItems, query, category) {
        val q = query.trim()
        state.browseItems.filter { item ->
            val categoryOk = category == null || item.category.equals(category, ignoreCase = true)
            val queryOk = q.isBlank() ||
                item.name.contains(q, ignoreCase = true) ||
                item.category?.contains(q, ignoreCase = true) == true
            categoryOk && queryOk
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Shop",
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
            )
            CollapsedVehicleCard(
                vehicle = state.selectedFitment,
                onChange = {
                    selectionAtOpen = state.selectedFitment
                    vehicleSheetOpen = true
                },
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                placeholder = { Text("Search in-stock parts") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = category == null,
                        onClick = { category = null },
                        label = { Text("All") },
                    )
                }
                rowItems(categories) { label ->
                    FilterChip(
                        selected = category == label,
                        onClick = { category = label },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (filtered.isEmpty() && !state.busy) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                PremiumEmptyState(
                    title = if (state.selectedFitment != null) {
                        "Nothing in stock for your vehicle"
                    } else {
                        "No matching items in stock"
                    },
                    body = "Try another search, category or vehicle.",
                    art = R.drawable.gtr_empty_state_no_search_results,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.stockItemId }) { item ->
                    PremiumProductCard(
                        item = item,
                        liked = wishOems.contains(item.oem.trim().uppercase()),
                        imageUrl = ProductImageStorage.publicUrl(item.imagePath),
                        onOpen = { onOpenProduct(item.oem) },
                        onLike = { onToggleWish(item) },
                        onAddToCart = { onAddToCart(item) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (vehicleSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { vehicleSheetOpen = false },
            sheetState = sheetState,
            containerColor = GtrPremiumColors.Surface,
            contentColor = GtrPremiumColors.TextPrimary,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Text(
                    if (state.selectedFitment == null) "Choose your vehicle" else "Change vehicle",
                    color = GtrPremiumColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "Only in-stock parts that fit your selection will remain in the shop.",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                VehicleSelectorSection(
                    vehicleRows = state.vehicleRows,
                    confirmedVehicle = state.selectedFitment,
                    busy = state.vehicleBusy,
                    error = state.vehicleError,
                    onConfirmCascade = onConfirmCascade,
                    onConfirmVin = onConfirmVin,
                    onClear = onClearVehicle,
                    sectionTitle = "Vehicle details",
                    confirmLabel = "Use this vehicle",
                    showClear = false,
                )
            }
        }
    }
}
