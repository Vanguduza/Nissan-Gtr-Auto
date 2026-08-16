package com.joker.coolmall.feature.main.view

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.zw.nissangtr.management.gtradapter.GtrStockItemOption
import co.zw.nissangtr.management.gtradapter.GtrWarehouseOption
import com.joker.coolmall.core.ui.component.button.AppButton
import com.joker.coolmall.feature.main.R
import com.joker.coolmall.feature.main.viewmodel.WarehouseReceiveViewModel

@Composable
internal fun WarehouseReceiveRoute(
    viewModel: WarehouseReceiveViewModel = hiltViewModel(),
) {
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedWarehouseId.collectAsStateWithLifecycle()
    val oemQuery by viewModel.oemQuery.collectAsStateWithLifecycle()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val qty by viewModel.qty.collectAsStateWithLifecycle()
    val unitCost by viewModel.unitCost.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    WarehouseReceiveScreen(
        warehouses = warehouses,
        selectedWarehouseId = selectedId,
        oemQuery = oemQuery,
        item = item,
        qty = qty,
        unitCost = unitCost,
        currency = currency,
        notes = notes,
        busy = busy,
        message = message,
        onOemChange = viewModel::updateOemQuery,
        onSearch = viewModel::searchOem,
        onSelectWarehouse = viewModel::selectWarehouse,
        onQtyChange = viewModel::updateQty,
        onUnitCostChange = viewModel::updateUnitCost,
        onCurrencyChange = viewModel::updateCurrency,
        onNotesChange = viewModel::updateNotes,
        onPost = viewModel::postReceipt,
    )
}

@Composable
internal fun WarehouseReceiveScreen(
    warehouses: List<GtrWarehouseOption>,
    selectedWarehouseId: String?,
    oemQuery: String,
    item: GtrStockItemOption?,
    qty: String,
    unitCost: String,
    currency: String,
    notes: String,
    busy: Boolean,
    message: String?,
    onOemChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectWarehouse: (String) -> Unit,
    onQtyChange: (String) -> Unit,
    onUnitCostChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onPost: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.warehouse_receive_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = oemQuery,
                onValueChange = onOemChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.warehouse_search_oem)) },
            )
            TextButton(onClick = onSearch, enabled = !busy) {
                Text(stringResource(R.string.warehouse_search))
            }
        }
        if (item != null) {
            Text(
                text = "${item.oemPartNumber} · ${item.description}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.warehouse_to_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            warehouses.forEach { wh ->
                FilterChip(
                    selected = wh.id == selectedWarehouseId,
                    onClick = { onSelectWarehouse(wh.id) },
                    label = { Text(wh.code) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = qty,
                onValueChange = onQtyChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.warehouse_qty)) },
            )
            OutlinedTextField(
                value = unitCost,
                onValueChange = onUnitCostChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.warehouse_unit_cost)) },
            )
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = currency == "USD",
                onClick = { onCurrencyChange("USD") },
                label = { Text("USD") },
            )
            FilterChip(
                selected = currency == "ZIG",
                onClick = { onCurrencyChange("ZIG") },
                label = { Text("ZIG") },
            )
        }
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = { Text(stringResource(R.string.warehouse_notes)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        AppButton(
            text = stringResource(R.string.warehouse_post_receipt),
            onClick = onPost,
            enabled = !busy,
            loading = busy,
        )
        if (message != null) {
            Text(
                text = message,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
