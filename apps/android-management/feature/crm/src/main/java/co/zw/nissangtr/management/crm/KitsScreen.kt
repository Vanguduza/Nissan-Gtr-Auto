package co.zw.nissangtr.management.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopListCard
import co.zw.nissangtr.ui.shop.ShopPrimaryButton
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopStaffPanel
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.ShopStatusChip
import co.zw.nissangtr.ui.theme.GtrColors

@Composable
fun KitsScreen(
    rpc: RpcClient,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KitsViewModel = viewModel(factory = KitsViewModel.factory(rpc)),
) {
    val state by viewModel.state.collectAsState()

    ShopStaffScreen(
        title = "Kits",
        subtitle = "BOM create · list",
        modifier = modifier,
        onBack = onBack,
    ) {
        ShopStaffPanel(title = "Create kit") {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.oem,
                onValueChange = viewModel::onOemChange,
                label = { Text("Kit OEM (manual, e.g. KIT-…)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            Text("Optional chassis", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.chassisCode.isEmpty(),
                    onClick = { viewModel.onChassisChange("") },
                    label = { Text("None") },
                    enabled = !state.busy,
                )
                state.chassisOptions.take(6).forEach { opt ->
                    FilterChip(
                        selected = state.chassisCode == opt.chassisCode,
                        onClick = { viewModel.onChassisChange(opt.chassisCode) },
                        label = { Text(opt.chassisCode) },
                        enabled = !state.busy,
                    )
                }
            }

            Text("Components (≥2)", style = MaterialTheme.typography.titleSmall)
            state.componentSlots.forEachIndexed { idx, slotId ->
                FilterChip(
                    selected = state.activeSlot == idx,
                    onClick = { viewModel.onActiveSlot(idx) },
                    label = {
                        Text(
                            if (slotId.isBlank()) "Slot ${idx + 1}: pick…"
                            else "Slot ${idx + 1}: ${slotId.take(8)}…",
                        )
                    },
                    enabled = !state.busy,
                )
            }
            ShopSecondaryButton(
                label = "Add component slot",
                onClick = viewModel::addComponentSlot,
                enabled = !state.busy,
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                label = { Text("Search catalog OEM / title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy,
            )
            ShopSecondaryButton(
                label = "Search parts",
                onClick = viewModel::searchComponents,
                enabled = !state.busy,
            )
            state.searchHits.forEach { hit ->
                ShopListCard(
                    title = hit.oemPartNumber,
                    subtitle = hit.description ?: hit.id.take(8),
                    onClick = { viewModel.pickComponent(hit) },
                )
            }
            ShopPrimaryButton(
                label = "Create kit",
                onClick = viewModel::create,
                enabled = !state.busy,
            )
        }

        ShopStaffPanel(title = "Existing kits") {
            ShopSecondaryButton(label = "Refresh", onClick = viewModel::refresh, enabled = !state.busy)
            if (state.kits.isEmpty()) {
                ShopHonestEmpty(title = "No kits", body = "Create one above")
            }
            state.kits.forEach { kit ->
                ShopListCard(
                    title = "${kit.oem} · ${kit.title}",
                    subtitle = "${kit.components.size} parts · ${kit.sellMode}" +
                        (kit.chassisCodes.takeIf { it.isNotEmpty() }?.let { " · ${it.joinToString()}" } ?: ""),
                    onClick = { viewModel.toggleActive(kit) },
                    badges = {
                        ShopStatusChip(
                            label = if (kit.isActive) "ACTIVE" else "OFF",
                            background = if (kit.isActive) GtrColors.Accent else GtrColors.Danger,
                        )
                    },
                )
            }
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
