package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import co.zw.nissangtr.customer.visual.IllustratedCategoryRail
import co.zw.nissangtr.customer.visual.PerformanceHero
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumProductRail
import co.zw.nissangtr.customer.visual.PremiumSectionHeader
import co.zw.nissangtr.customer.visual.R
import co.zw.nissangtr.customer.visual.SupportCardsRow

/**
 * Exact preview-locked Home body.
 *
 * Shell top bar and bottom navigation are owned by app shell. This composable owns everything
 * between them. It deliberately has NO EPC UI and NO permanently-mounted vehicle selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumCatalogHome(
    state: CatalogUiState,
    wishOems: Set<String>,
    onShopAll: () -> Unit,
    onSeeAllCategories: () -> Unit,
    onSeeAllPopular: () -> Unit,
    onSeeAllNewest: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onToggleWish: (CatalogListItem) -> Unit,
    onAddToCart: (CatalogListItem) -> Unit,
    onCategoryBrowse: (String) -> Unit,
    onConfirmCascade: (String, String, String, String?) -> Unit,
    onConfirmVin: (String) -> Unit,
    onClearVehicle: () -> Unit,
    onTrackOrder: () -> Unit,
    modifier: Modifier = Modifier,
    startVehicleSheetOpen: Boolean = false,
) {
    var vehicleSheetOpen by remember { mutableStateOf(startVehicleSheetOpen) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Successful selection changes the selected fitment; only then close the selector.
    var selectionAtOpen by remember { mutableStateOf(state.selectedFitment) }
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

    val popular = state.browseItems.take(8)
    val newest = state.browseItems.drop(8).take(8).ifEmpty { state.browseItems.take(8) }
    val vehicleName = state.selectedFitment
        ?.model
        ?.removePrefix("Nissan ")
        ?.substringBefore(" T")
        ?.substringBefore(" R")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        PerformanceHero(
            vehicle = state.selectedFitment,
            onShopAll = onShopAll,
        )

        Spacer(Modifier.height(12.dp))
        CollapsedVehicleCard(
            vehicle = state.selectedFitment,
            onChange = {
                selectionAtOpen = state.selectedFitment
                vehicleSheetOpen = true
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(22.dp))
        PremiumSectionHeader(
            title = "Shop by category",
            action = "See all",
            onAction = onSeeAllCategories,
        )
        Spacer(Modifier.height(10.dp))
        IllustratedCategoryRail(onCategory = onCategoryBrowse)

        Spacer(Modifier.height(22.dp))
        SupportCardsRow(
            onFindPart = {
                selectionAtOpen = state.selectedFitment
                vehicleSheetOpen = true
            },
            onTrackOrder = onTrackOrder,
        )

        Spacer(Modifier.height(24.dp))
        if (popular.isEmpty()) {
            PremiumEmptyState(
                title = if (state.selectedFitment != null) {
                    "Nothing in stock for your vehicle"
                } else {
                    "No items currently in stock"
                },
                body = if (state.selectedFitment != null) {
                    "Try another category or change your selected vehicle."
                } else {
                    "Available parts will appear here when inventory is published."
                },
                art = if (state.selectedFitment != null) {
                    R.drawable.gtr_empty_state_no_items_for_vehicle
                } else null,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            PremiumProductRail(
                title = vehicleName?.let { "Popular for your $it" } ?: "Popular parts",
                items = popular,
                wishOems = wishOems,
                onSeeAll = onSeeAllPopular,
                onOpen = { onOpenProduct(it.oem) },
                onLike = onToggleWish,
                onAddToCart = onAddToCart,
            )
        }

        Spacer(Modifier.height(24.dp))
        if (newest.isNotEmpty()) {
            PremiumProductRail(
                title = "New in stock",
                items = newest,
                wishOems = wishOems,
                onSeeAll = onSeeAllNewest,
                onOpen = { onOpenProduct(it.oem) },
                onLike = onToggleWish,
                onAddToCart = onAddToCart,
            )
        }
        Spacer(Modifier.height(28.dp))
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                Text(
                    text = if (state.selectedFitment == null) "Choose your vehicle" else "Change vehicle",
                    style = MaterialTheme.typography.headlineSmall,
                    color = GtrPremiumColors.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your selection only filters parts currently in stock that fit this vehicle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GtrPremiumColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
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
