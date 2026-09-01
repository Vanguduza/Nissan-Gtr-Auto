package co.zw.nissangtr.customer.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.customer.catalog.VehicleSelectorSection
import co.zw.nissangtr.customer.rpc.GarageVehicle
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.visual.GenericNissanSilhouette
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumMessageBanner
import co.zw.nissangtr.customer.visual.PremiumMessageKind
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumStatusChip
import co.zw.nissangtr.customer.visual.PremiumStatusTone
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import co.zw.nissangtr.customer.visual.VehicleArtworkStorage
import co.zw.nissangtr.customer.visual.vehicle.VehicleArtworkResolver
import co.zw.nissangtr.ui.shop.ShopRemoteImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GarageViewModel = viewModel(factory = GarageViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()
    val formBusy = state.busy || state.catalogBusy
    var addOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background),
    ) {
        PremiumScreenHeader(
            title = "My Garage",
            subtitle = "Saved vehicles for faster fitment filtering",
            onBack = onBack,
        )

        if (state.vehicles.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                PremiumSurfaceCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GenericNissanSilhouette(Modifier.fillMaxWidth().height(100.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "No vehicles saved yet",
                            color = GtrPremiumColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Add your Nissan to keep fitment selection quick.",
                            color = GtrPremiumColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        PremiumPrimaryButton(
                            text = "Add a vehicle",
                            onClick = { addOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.vehicles, key = { it.id }) { vehicle ->
                    PremiumGarageVehicleCard(
                        vehicle = vehicle,
                        busy = formBusy,
                        onDelete = { viewModel.delete(vehicle.id) },
                    )
                }
                item {
                    PremiumPrimaryButton(
                        text = "Add another vehicle",
                        onClick = { addOpen = true },
                        enabled = !formBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.message?.let { message ->
                    item { PremiumMessageBanner(message, PremiumMessageKind.Success) }
                }
                state.error?.let { error ->
                    item { PremiumMessageBanner(error, PremiumMessageKind.Error) }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (addOpen) {
        ModalBottomSheet(
            onDismissRequest = { addOpen = false },
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
                    "Add a vehicle",
                    style = MaterialTheme.typography.headlineSmall,
                    color = GtrPremiumColors.TextPrimary,
                )
                Text(
                    "Choose from the same live vehicle catalog used for fitment.",
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                key(state.formEpoch) {
                    VehicleSelectorSection(
                        vehicleRows = state.vehicleRows,
                        confirmedVehicle = null,
                        busy = formBusy,
                        error = state.error,
                        onConfirmCascade = { make, model, generation, engine ->
                            viewModel.saveFromCascade(make, model, generation, engine)
                        },
                        onConfirmVin = viewModel::saveFromVin,
                        onClear = viewModel::clearFormError,
                        sectionTitle = "Vehicle details",
                        confirmLabel = "Save vehicle",
                        showClear = false,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.isPrimary,
                        onCheckedChange = viewModel::onPrimaryChange,
                        enabled = !formBusy,
                    )
                    Text(
                        "Make this my primary vehicle",
                        color = GtrPremiumColors.TextPrimary,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PremiumGarageVehicleCard(
    vehicle: GarageVehicle,
    busy: Boolean,
    onDelete: () -> Unit,
) {
    val fitment = SelectedFitmentVehicle(
        make = vehicle.make,
        model = vehicle.model.orEmpty(),
        generation = vehicle.generation.orEmpty(),
        engine = vehicle.engine,
        vin = vehicle.vin,
    )
    val art = VehicleArtworkResolver.resolve(fitment)
    val url = art?.let { VehicleArtworkStorage.publicUrl(it.assetPath) }

    PremiumSurfaceCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(112.dp)
                    .height(80.dp)
                    .background(GtrPremiumColors.SurfaceSoft, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (url != null) {
                    ShopRemoteImage(
                        url = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    GenericNissanSilhouette(Modifier.width(92.dp).height(50.dp))
                }
            }

            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        vehicle.model?.removePrefix("Nissan ")?.takeIf { it.isNotBlank() } ?: "Nissan vehicle",
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (vehicle.isPrimary) {
                        PremiumStatusChip("Primary", PremiumStatusTone.Premium)
                    }
                }
                Text(
                    listOfNotNull(vehicle.generation, vehicle.engine)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    color = GtrPremiumColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Delete vehicle",
                    tint = GtrPremiumColors.TextSecondary,
                )
            }
        }
    }
}
