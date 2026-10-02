package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.visual.ExpressCategoryKeys
import co.zw.nissangtr.customer.visual.ExpressHeader
import co.zw.nissangtr.customer.visual.ExpressProductCard
import co.zw.nissangtr.customer.visual.ExpressPromoBanner
import co.zw.nissangtr.customer.visual.ExpressSection
import co.zw.nissangtr.customer.visual.ExpressSheet
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.ProductImageStorage
import co.zw.nissangtr.customer.visual.R

/** Shell destinations the Express header and banner open (owned by the app shell). */
data class ExpressHomeActions(
    val onWishlist: () -> Unit,
    val onAccount: () -> Unit,
    val onSettings: () -> Unit,
    val onDeliveryAddress: () -> Unit,
    val onSearch: () -> Unit,
    val onServiceKits: () -> Unit,
    /** Delivery address shown in the header; a prompt when none is saved. */
    val deliverToLabel: String = "Choose address",
)

/**
 * Express home (Figma "GTR Customer — Home"): brand header with the delivery address, search and
 * vehicle, round category buttons, then product panels and the service-kit banner on a sheet.
 * Data is the same in-stock, vehicle-scoped browse list as the illustrated home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressCatalogHome(
    state: CatalogUiState,
    actions: ExpressHomeActions,
    onSeeAllCategories: () -> Unit,
    onSeeAllPopular: () -> Unit,
    onSeeAllNewest: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onAddToCart: (CatalogListItem) -> Unit,
    onCategoryBrowse: (String) -> Unit,
    onConfirmCascade: (String, String, String, String?) -> Unit,
    onConfirmVin: (String) -> Unit,
    onClearVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var vehicleSheetOpen by remember { mutableStateOf(false) }
    var selectionAtOpen by remember { mutableStateOf(state.selectedFitment) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(state.selectedFitment, vehicleSheetOpen) {
        if (vehicleSheetOpen && state.selectedFitment != null &&
            state.selectedFitment != selectionAtOpen && state.vehicleError == null
        ) {
            vehicleSheetOpen = false
        }
    }
    fun openVehicle() {
        selectionAtOpen = state.selectedFitment
        vehicleSheetOpen = true
    }

    val popular = state.browseItems.take(8)
    val newest = state.browseItems.drop(8).take(8)
    val vehicleLabel = state.selectedFitment?.headerLabel()
    val fits = state.selectedFitment?.shortName()

    Column(
        modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        ExpressHeader(
            vehicleLabel = vehicleLabel,
            deliverToLabel = actions.deliverToLabel,
            onWishlist = actions.onWishlist,
            onAccount = actions.onAccount,
            onSettings = actions.onSettings,
            onDeliveryAddress = actions.onDeliveryAddress,
            onSearch = actions.onSearch,
            onVehicle = ::openVehicle,
            onCategory = { c ->
                when (c.key) {
                    ExpressCategoryKeys.MY_VEHICLE -> if (state.selectedFitment == null) openVehicle() else onSeeAllPopular()
                    ExpressCategoryKeys.ALL -> onSeeAllCategories()
                    else -> onCategoryBrowse(c.key)
                }
            },
        )
        ExpressSheet {
            if (popular.isEmpty()) {
                PremiumEmptyState(
                    title = if (state.selectedFitment != null) "Nothing in stock for your vehicle" else "No items currently in stock",
                    body = if (state.selectedFitment != null) {
                        "Try another category or change your selected vehicle."
                    } else {
                        "Available parts will appear here when inventory is published."
                    },
                    art = if (state.selectedFitment != null) R.drawable.gtr_empty_state_no_items_for_vehicle else null,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                ExpressSection(
                    title = fits?.let { "Trending for your $it" } ?: "Popular parts",
                    countLabel = itemsLabel(state.browseItems.size),
                    onSeeAll = onSeeAllPopular,
                ) {
                    popular.forEach { item ->
                        ExpressProductCard(
                            item = item,
                            imageUrl = ProductImageStorage.publicUrl(item.imagePath),
                            fitsLabel = fits,
                            onOpen = { onOpenProduct(item.oem) },
                            onAdd = { onAddToCart(item) },
                        )
                    }
                }
            }
            ExpressPromoBanner(
                kicker = "SERVICE KITS",
                title = fits?.let { "Kits for your $it" } ?: "Service kits",
                body = "Filters, plugs and fluids, bundled",
                cta = "Shop kits",
                onClick = actions.onServiceKits,
            )
            if (newest.isNotEmpty()) {
                ExpressSection(
                    title = "New in stock",
                    countLabel = itemsLabel(newest.size),
                    onSeeAll = onSeeAllNewest,
                ) {
                    newest.forEach { item ->
                        ExpressProductCard(
                            item = item,
                            imageUrl = ProductImageStorage.publicUrl(item.imagePath),
                            fitsLabel = fits,
                            onOpen = { onOpenProduct(item.oem) },
                            onAdd = { onAddToCart(item) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (vehicleSheetOpen) {
        VehiclePickerSheet(
            state = state,
            sheetState = sheetState,
            onDismiss = { vehicleSheetOpen = false },
            onConfirmCascade = onConfirmCascade,
            onConfirmVin = onConfirmVin,
            onClearVehicle = onClearVehicle,
        )
    }
}

private fun itemsLabel(n: Int) = if (n == 1) "1 item" else "$n items"

/** "Nissan GT-R R35 · VR38DETT" style label for the header chip. */
private fun SelectedFitmentVehicle.headerLabel(): String =
    listOfNotNull(
        listOfNotNull(make?.takeIf { !model.startsWith(it, ignoreCase = true) }, model, generation.takeIf { it.isNotBlank() && !model.contains(it, ignoreCase = true) })
            .joinToString(" "),
        engine?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

/** Short model name for "Fits …" lines, e.g. "GT-R". */
private fun SelectedFitmentVehicle.shortName(): String =
    model.removePrefix("Nissan ").trim().ifEmpty { model }
